package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
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
import com.example.gestor_documental.service.PdfSplitService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
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
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final PdfSplitService pdfSplitService;
    private final HistorialCambioService historialCambioService;
    private final SolicitudDocumentacionIaService solicitudDocumentacionIaService;
    private final RestClient restClient = RestClient.create();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    @Value("${app.mail.inbound.enabled:false}")
    private boolean enabled;
    @Value("${app.mail.inbound.provider:${app.mail.provider:imap}}")
    private String inboundProvider;
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
    @Value("${app.mail.graph.tenant-id:}")
    private String graphTenantId;
    @Value("${app.mail.graph.client-id:}")
    private String graphClientId;
    @Value("${app.mail.graph.client-secret:}")
    private String graphClientSecret;
    @Value("${app.mail.inbound.graph.mailbox:${app.mail.graph.sender:${app.mail.from:}}}")
    private String graphMailbox;

    @Scheduled(fixedDelayString = "${app.mail.inbound.poll-delay-ms:300000}", initialDelayString = "${app.mail.inbound.initial-delay-ms:60000}")
    public void procesarBuzon() {
        intentarProcesarBuzon();
    }

    public boolean intentarProcesarBuzon() {
        if (!enabled) {
            return false;
        }
        if (!processing.compareAndSet(false, true)) {
            log.info("Comprobacion de buzon entrante omitida porque ya hay una en curso.");
            return false;
        }
        try {
            if ("graph".equalsIgnoreCase(inboundProvider)) {
                procesarBuzonGraph();
            } else {
                procesarBuzonImap();
            }
            return true;
        } finally {
            processing.set(false);
        }
    }

    private void procesarBuzonImap() {
        if (isBlank(host) || isBlank(username) || isBlank(password)) {
            log.warn("Buzon entrante IMAP no configurado: host, usuario o password vacios.");
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
                            procesarMensajeImap(mensajes[i]);
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
            log.warn("No se pudo procesar el buzon entrante IMAP: {}", exception.getMessage(), exception);
        }
    }

    private void procesarBuzonGraph() {
        if (isBlank(graphMailbox) || isBlank(graphTenantId) || isBlank(graphClientId) || isBlank(graphClientSecret)) {
            log.warn("Buzon entrante Graph no configurado: mailbox, tenant, client id o client secret vacios.");
            return;
        }
        try {
            String token = obtenerTokenGraph();
            String folder = "INBOX".equalsIgnoreCase(folderName) ? "inbox" : folderName;
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.microsoft.com")
                            .pathSegment("v1.0", "users", graphMailbox.trim(), "mailFolders", folder, "messages")
                            .queryParam("$top", Math.max(1, maxMessages))
                            .queryParam("$filter", "isRead eq false")
                            .queryParam("$select", "id,internetMessageId,subject,from,hasAttachments")
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);

            Object value = response != null ? response.get("value") : null;
            if (!(value instanceof List<?> mensajes)) {
                return;
            }
            for (Object item : mensajes) {
                if (item instanceof Map<?, ?> mensaje) {
                    procesarMensajeGraph(token, mensaje);
                }
            }
        } catch (RestClientResponseException exception) {
            log.warn("Microsoft Graph rechazo la lectura del buzon entrante ({}): {}", exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            log.warn("No se pudo procesar el buzon entrante Graph: {}", exception.getMessage(), exception);
        }
    }

    private void procesarMensajeImap(Message mensaje) throws MessagingException, IOException {
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

        crearSolicitudDesdeAdjuntos(messageId, asunto, remitente, adjuntos);
        mensaje.setFlag(Flags.Flag.SEEN, true);
    }

    private void procesarMensajeGraph(String token, Map<?, ?> mensaje) {
        String graphId = valor(mensaje.get("id"));
        String messageId = !isBlank(valor(mensaje.get("internetMessageId"))) ? valor(mensaje.get("internetMessageId")) : graphId;
        if (isBlank(graphId) || isBlank(messageId)) {
            return;
        }
        if (procesadoRepository.existsByMessageId(limitar(messageId, 255))) {
            marcarLeidoGraph(token, graphId);
            return;
        }

        String asunto = valor(mensaje.get("subject"));
        String remitente = remitenteGraph(mensaje.get("from"));
        try {
            List<AdjuntoPdf> adjuntos = extraerAdjuntosPdfGraph(token, graphId);
            if (adjuntos.isEmpty()) {
                registrarProcesado(messageId, asunto, remitente, null, null, "IGNORADO", "No contiene adjuntos PDF.");
                marcarLeidoGraph(token, graphId);
                return;
            }
            crearSolicitudDesdeAdjuntos(messageId, asunto, remitente, adjuntos);
            marcarLeidoGraph(token, graphId);
        } catch (Exception exception) {
            registrarProcesado(messageId, asunto, remitente, null, null, "ERROR", exception.getMessage());
            marcarLeidoGraph(token, graphId);
            log.warn("Correo entrante Graph ignorado por error de procesamiento: {}", exception.getMessage(), exception);
        }
    }

    private void crearSolicitudDesdeAdjuntos(String messageId, String asunto, String remitente, List<AdjuntoPdf> adjuntos) {
        Cliente cliente = resolverCliente(remitente)
                .orElseThrow(() -> new IllegalStateException("No se encontro cliente para el remitente ni cliente por defecto configurado."));
        Usuario admin = resolverAdmin();
        AdjuntoPdf adjunto = prepararExpedienteCompleto(adjuntos);

        MultipartFile archivo = new BytesMultipartFile(adjunto.nombre(), adjunto.contenido(), "application/pdf");
        String textoDeteccion = asunto + " " + adjunto.nombre() + " " + ocrPdfService.extraerTextoCompleto(archivo);
        String matricula = detectarMatricula(textoDeteccion)
                .orElseThrow(() -> new IllegalStateException("No se pudo detectar matricula en el correo " + messageId));
        TipoTramiteEnum tramiteDetectado = detectarTipoTramite(textoDeteccion);
        TipoTramite tipoTramite = tipoTramiteRepository.findByNombre(tramiteDetectado)
                .orElseThrow(() -> new IllegalStateException("No existe el tipo de tramite configurado: " + tramiteDetectado));

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
        intentarLecturaIaSolicitud(guardada, admin);
        registrarProcesado(messageId, asunto, remitente, matricula, guardada.getId(), "PROCESADO",
                adjuntos.size() > 1
                        ? "Solicitud creada desde " + adjuntos.size() + " PDFs adjuntos unificados."
                        : "Solicitud creada desde PDF adjunto.");
    }

    private AdjuntoPdf prepararExpedienteCompleto(List<AdjuntoPdf> adjuntos) {
        if (adjuntos == null || adjuntos.isEmpty()) {
            throw new IllegalStateException("No contiene adjuntos PDF.");
        }
        if (adjuntos.size() == 1) {
            return adjuntos.get(0);
        }
        List<byte[]> documentos = new ArrayList<>();
        for (AdjuntoPdf adjunto : adjuntos) {
            documentos.add(adjunto.contenido());
        }
        return new AdjuntoPdf(nombreExpedienteCompleto(adjuntos), pdfSplitService.unirDocumentos(documentos));
    }

    private String nombreExpedienteCompleto(List<AdjuntoPdf> adjuntos) {
        String base = adjuntos.stream()
                .map(AdjuntoPdf::nombre)
                .filter(nombre -> !isBlank(nombre))
                .findFirst()
                .orElse("expediente_completo.pdf");
        String nombre = base.replaceAll("(?i)\\.pdf$", "");
        return nombre + "_unificado.pdf";
    }

    private void intentarLecturaIaSolicitud(Solicitud solicitud, Usuario admin) {
        try {
            SolicitudDocumentacionIaResponse response = solicitudDocumentacionIaService.procesarDocumentacion(solicitud.getId(), admin);
            if (response.isDatosAplicados() || response.isYaEstabaCorrecta()) {
                log.info("Solicitud {} lectura IA correo completada: {}", solicitud.getId(), response.getMensaje());
            } else {
                log.info("Solicitud {} lectura IA correo pendiente: {} Detalles: {}",
                        solicitud.getId(),
                        response.getMensaje(),
                        response.getDetalles() != null ? String.join(" | ", response.getDetalles()) : "");
            }
        } catch (RuntimeException exception) {
            log.info("Solicitud {} creada desde correo, pero la lectura IA queda pendiente: {}",
                    solicitud.getId(), exception.getMessage());
        }
    }

    private TipoTramiteEnum detectarTipoTramite(String texto) {
        String normalizado = TextNormalizer.upperOrNull(texto);
        if (normalizado == null) {
            return defaultTipoTramite;
        }
        String compacto = normalizado.replaceAll("[^A-Z0-9]", "");
        if (compacto.contains("BATECOM") || compacto.contains("BATECOMP")) {
            return TipoTramiteEnum.BATECOM;
        }
        return defaultTipoTramite;
    }

    private List<AdjuntoPdf> extraerAdjuntosPdfGraph(String token, String messageId) {
        Map<?, ?> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.microsoft.com")
                        .pathSegment("v1.0", "users", graphMailbox.trim(), "messages", messageId, "attachments")
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(Map.class);
        Object value = response != null ? response.get("value") : null;
        if (!(value instanceof List<?> attachments)) {
            return List.of();
        }
        List<AdjuntoPdf> adjuntos = new ArrayList<>();
        for (Object item : attachments) {
            if (!(item instanceof Map<?, ?> attachment)) {
                continue;
            }
            String nombre = valor(attachment.get("name"));
            String contentType = valor(attachment.get("contentType"));
            String contentBytes = valor(attachment.get("contentBytes"));
            boolean pdf = nombre.toLowerCase(Locale.ROOT).endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType);
            if (pdf && !isBlank(contentBytes)) {
                adjuntos.add(new AdjuntoPdf(nombre, Base64.getDecoder().decode(contentBytes)));
            }
        }
        return adjuntos;
    }

    private void marcarLeidoGraph(String token, String messageId) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.microsoft.com")
                            .pathSegment("v1.0", "users", graphMailbox.trim(), "messages", messageId)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("isRead", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.warn("No se pudo marcar como leido el correo entrante Graph {}: {}", messageId, exception.getMessage());
        }
    }

    private String obtenerTokenGraph() {
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("client_id", graphClientId.trim());
        form.add("client_secret", graphClientSecret.trim());
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");

        Map<?, ?> response = restClient.post()
                .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", graphTenantId.trim())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        Object token = response != null ? response.get("access_token") : null;
        if (token == null || token.toString().isBlank()) {
            throw new IllegalStateException("Microsoft Graph no devolvio access_token.");
        }
        return token.toString();
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
        String id = limitar(messageId, 255);
        if (procesadoRepository.existsByMessageId(id)) {
            return;
        }
        CorreoEntranteProcesado procesado = new CorreoEntranteProcesado();
        procesado.setMessageId(id);
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

    private String remitenteGraph(Object from) {
        if (!(from instanceof Map<?, ?> fromMap)) {
            return "";
        }
        Object emailAddress = fromMap.get("emailAddress");
        if (!(emailAddress instanceof Map<?, ?> emailMap)) {
            return "";
        }
        String address = valor(emailMap.get("address"));
        String name = valor(emailMap.get("name"));
        return !isBlank(address) && !isBlank(name) ? name + " <" + address + ">" : address;
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

    private String valor(Object value) {
        return value != null ? value.toString() : "";
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
