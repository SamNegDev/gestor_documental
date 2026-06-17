package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.CorreoEntranteProcesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.CorreoEntranteProcesadoRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.TipoTramiteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.OcrPdfService;
import com.example.gestor_documental.util.TextNormalizer;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CorreoEntranteSolicitudService {

    private static final Logger log = LoggerFactory.getLogger(CorreoEntranteSolicitudService.class);
    private static final Pattern MATRICULA_MODERNA = Pattern.compile("\\b(\\d{4})\\s*[- ]?\\s*([BCDFGHJKLMNPRSTVWXYZ]{3})\\b", Pattern.CASE_INSENSITIVE);

    private final CorreoEntranteProcesadoRepository procesadoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final TipoTramiteRepository tipoTramiteRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocumentoService documentoService;
    private final OcrPdfService ocrPdfService;
    private final HistorialCambioService historialCambioService;

    @Value("${app.mail.inbound.enabled:false}")
    private boolean enabled;
    @Value("${app.mail.inbound.host:}")
    private String host;
    @Value("${app.mail.inbound.port:993}")
    private int port;
    @Value("${app.mail.inbound.username:${spring.mail.username:}}")
    private String username;
    @Value("${app.mail.inbound.password:${spring.mail.password:}}")
    private String password;
    @Value("${app.mail.inbound.folder:INBOX}")
    private String folderName;
    @Value("${app.mail.inbound.protocol:imaps}")
    private String protocol;
    @Value("${app.mail.inbound.default-cliente-id:}")
    private String defaultClienteId;
    @Value("${app.mail.inbound.admin-email:${app.initial-admin.email:}}")
    private String adminEmail;
    @Value("${app.mail.inbound.default-tipo-tramite:TRASPASO}")
    private TipoTramiteEnum defaultTipoTramite;
    @Value("${app.mail.inbound.max-messages:10}")
    private int maxMessages;

    @Scheduled(fixedDelayString = "${app.mail.inbound.poll-delay-ms:300000}", initialDelayString = "${app.mail.inbound.initial-delay-ms:60000}")
    public void procesarBuzon() {
        if (!enabled) {
            return;
        }
        if (isBlank(host) || isBlank(username) || isBlank(password)) {
            log.warn("Buzon entrante no configurado: host, usuario o password vacios.");
            return;
        }

        Properties properties = new Properties();
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", host);
        properties.put("mail." + protocol + ".port", String.valueOf(port));
        properties.put("mail." + protocol + ".ssl.enable", "true");

        try {
            Session session = Session.getInstance(properties);
            try (Store store = session.getStore(protocol)) {
                store.connect(host, port, username, password);
                Folder folder = store.getFolder(folderName);
                folder.open(Folder.READ_WRITE);
                try {
                    Message[] mensajes = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                    for (int i = 0; i < Math.min(mensajes.length, maxMessages); i++) {
                        try {
                            procesarMensaje(mensajes[i]);
                        } catch (Exception exception) {
                            registrarErrorMensaje(mensajes[i], exception.getMessage());
                            mensajes[i].setFlag(Flags.Flag.SEEN, true);
                            log.warn("Correo entrante ignorado por error de procesamiento: {}", exception.getMessage(), exception);
                        }
                    }
                } finally {
                    folder.close(false);
                }
            }
        } catch (Exception exception) {
            log.warn("No se pudo procesar el buzon entrante: {}", exception.getMessage(), exception);
        }
    }

    @Transactional
    protected void procesarMensaje(Message mensaje) throws MessagingException, IOException {
        String messageId = resolverMessageId(mensaje);
        if (procesadoRepository.existsByMessageId(messageId)) {
            mensaje.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        String asunto = mensaje.getSubject();
        String remitente = resolverRemitente(mensaje);
        List<AdjuntoPdf> adjuntos = extraerAdjuntosPdf(mensaje);
        if (adjuntos.isEmpty()) {
            registrarProcesado(messageId, asunto, remitente, null, null, "IGNORADO", "No contiene adjuntos PDF.");
            mensaje.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        Usuario admin = resolverAdmin();
        Cliente cliente = resolverCliente(remitente)
                .orElseThrow(() -> new IllegalStateException("No se encontro cliente para el remitente ni cliente por defecto configurado."));
        TipoTramite tipoTramite = tipoTramiteRepository.findByNombre(defaultTipoTramite)
                .orElseThrow(() -> new IllegalStateException("No existe el tipo de tramite configurado: " + defaultTipoTramite));

        AdjuntoPdf adjunto = adjuntos.get(0);
        MultipartFile archivo = new BytesMultipartFile(adjunto.nombre(), adjunto.contenido(), "application/pdf");
        String matricula = detectarMatricula(asunto + " " + adjunto.nombre() + " " + ocrPdfService.extraerTextoCompleto(archivo))
                .orElseThrow(() -> new IllegalStateException("No se pudo detectar matricula en el correo " + messageId));

        Solicitud solicitud = new Solicitud();
        solicitud.setCliente(cliente);
        solicitud.setCreadoPor(admin);
        solicitud.setTipoTramite(tipoTramite);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        solicitud.setMatricula(TextNormalizer.upperOrNull(matricula));
        solicitud.setObservaciones(TextNormalizer.upperOrNull("Creada automaticamente desde correo entrante: " + safe(asunto)));
        Solicitud guardada = solicitudRepository.save(solicitud);

        historialCambioService.registrarCambioSolicitud(
                guardada,
                admin,
                "CORREO ENTRANTE",
                "Solicitud creada automaticamente desde el buzon de correo."
        );
        documentoService.guardarParaSolicitud(guardada.getId(), archivo, TipoDocumento.EXPEDIENTE_COMPLETO, admin);
        registrarProcesado(messageId, asunto, remitente, matricula, guardada.getId(), "PROCESADO", "Solicitud creada desde PDF adjunto.");

        mensaje.setFlag(Flags.Flag.SEEN, true);
    }

    private Optional<Cliente> resolverCliente(String remitente) {
        String email = extraerEmail(remitente);
        if (!isBlank(email)) {
            Optional<Cliente> cliente = clienteRepository.findByEmailIgnoreCase(email);
            if (cliente.isPresent()) {
                return cliente;
            }
        }
        Long clienteId = parseLong(defaultClienteId);
        return clienteId != null ? clienteRepository.findById(clienteId) : Optional.empty();
    }

    private Usuario resolverAdmin() {
        if (!isBlank(adminEmail)) {
            return usuarioRepository.findByEmail(adminEmail.trim().toLowerCase(Locale.ROOT))
                    .orElseGet(this::primerAdminActivo);
        }
        return primerAdminActivo();
    }

    private Usuario primerAdminActivo() {
        return usuarioRepository.findFirstByRolUsuarioAndActivoTrueOrderByIdAsc(RolUsuario.ADMIN)
                .orElseThrow(() -> new IllegalStateException("No hay administrador activo para crear solicitudes automaticas."));
    }

    private Optional<String> detectarMatricula(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher matcher = MATRICULA_MODERNA.matcher(TextNormalizer.upperOrNull(texto));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) + matcher.group(2));
    }

    private List<AdjuntoPdf> extraerAdjuntosPdf(Part part) throws MessagingException, IOException {
        List<AdjuntoPdf> adjuntos = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                adjuntos.addAll(extraerAdjuntosPdf(bodyPart));
            }
            return adjuntos;
        }

        String nombre = part.getFileName();
        if (nombre != null && nombre.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try (InputStream inputStream = part.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                inputStream.transferTo(output);
                adjuntos.add(new AdjuntoPdf(nombre, output.toByteArray()));
            }
        }
        return adjuntos;
    }

    private void registrarProcesado(String messageId, String asunto, String remitente, String matricula, Long solicitudId, String estado, String detalle) {
        if (procesadoRepository.existsByMessageId(messageId)) {
            return;
        }
        CorreoEntranteProcesado procesado = new CorreoEntranteProcesado();
        procesado.setMessageId(messageId);
        procesado.setAsunto(limitar(asunto, 500));
        procesado.setRemitente(limitar(remitente, 250));
        procesado.setMatricula(matricula);
        procesado.setSolicitudId(solicitudId);
        procesado.setEstado(estado);
        procesado.setDetalle(limitar(detalle, 500));
        procesadoRepository.save(procesado);
    }

    private void registrarErrorMensaje(Message mensaje, String detalle) {
        try {
            String messageId = resolverMessageId(mensaje);
            if (!procesadoRepository.existsByMessageId(messageId)) {
                registrarProcesado(messageId, mensaje.getSubject(), resolverRemitente(mensaje), null, null, "ERROR", detalle);
            }
        } catch (Exception exception) {
            log.warn("No se pudo registrar el error del correo entrante: {}", exception.getMessage());
        }
    }

    private String resolverMessageId(Message mensaje) throws MessagingException {
        String[] header = mensaje.getHeader("Message-ID");
        if (header != null && header.length > 0 && !isBlank(header[0])) {
            return limitar(header[0], 255);
        }
        return limitar(mensaje.getFolder().getFullName() + "-" + mensaje.getMessageNumber(), 255);
    }

    private String resolverRemitente(Message mensaje) throws MessagingException {
        Address[] from = mensaje.getFrom();
        if (from == null || from.length == 0) {
            return "";
        }
        return from[0].toString();
    }

    private String extraerEmail(String remitente) {
        if (isBlank(remitente)) {
            return "";
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(remitente);
            if (addresses.length > 0 && addresses[0].getAddress() != null) {
                return addresses[0].getAddress().trim().toLowerCase(Locale.ROOT);
            }
        } catch (MessagingException ignored) {
        }
        return remitente.trim().toLowerCase(Locale.ROOT);
    }

    private String limitar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    private String safe(String valor) {
        return valor != null ? valor : "";
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }

    private Long parseLong(String valor) {
        if (isBlank(valor)) {
            return null;
        }
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record AdjuntoPdf(String nombre, byte[] contenido) {
    }

    private record BytesMultipartFile(String originalFilename, byte[] bytes, String contentType) implements MultipartFile {
        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes != null ? bytes.length : 0;
        }

        @Override
        public byte[] getBytes() {
            return bytes != null ? bytes : new byte[0];
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(getBytes());
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), getBytes());
        }
    }
}
