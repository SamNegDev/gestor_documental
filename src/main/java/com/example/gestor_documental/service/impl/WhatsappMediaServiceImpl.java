package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.WhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.repository.WhatsappAdjuntoRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.OcrPdfService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
import com.example.gestor_documental.service.WhatsappMediaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WhatsappMediaServiceImpl implements WhatsappMediaService {
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsappAdjuntoRepository adjuntoRepository;
    private final DocumentoRepository documentoRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioService historialCambioService;
    private final OcrPdfService ocrPdfService;
    private final DocumentoService documentoService;
    private final SolicitudDocumentacionIaService solicitudDocumentacionIaService;

    @Value("${app.whatsapp.access-token:}")
    private String accessToken;

    @Value("${app.whatsapp.graph-api-version:v23.0}")
    private String graphApiVersion;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public boolean descargarYGuardar(WhatsappWebhookEvento evento, JsonNode message) {
        String tipo = text(message.path("type"));
        JsonNode media = mediaNode(message, tipo);
        String mediaId = text(media.path("id"));
        if (!StringUtils.hasText(mediaId) || adjuntoRepository.existsByMediaId(mediaId)) {
            return false;
        }

        WhatsappAdjunto adjunto = new WhatsappAdjunto();
        adjunto.setEvento(evento);
        adjunto.setCliente(evento.getCliente());
        adjunto.setExpediente(evento.getExpediente());
        adjunto.setSolicitud(evento.getSolicitud());
        adjunto.setTelefono(evento.getTelefono());
        adjunto.setTipo(tipo);
        adjunto.setMediaId(mediaId);
        adjunto.setMimeType(text(media.path("mime_type")));
        adjunto.setNombreArchivoOriginal(nombreOriginal(media, mediaId, tipo));

        if (!StringUtils.hasText(accessToken)) {
            adjunto.setErrorDescarga("No hay token de WhatsApp configurado para descargar adjuntos.");
            adjuntoRepository.save(adjunto);
            return false;
        }

        try {
            String metadataResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.facebook.com")
                            .pathSegment(version(), mediaId)
                            .build())
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .retrieve()
                    .body(String.class);
            JsonNode metadata = parseJson(metadataResponse);

            if (metadata != null) {
                adjunto.setMimeType(textOrFallback(metadata.path("mime_type"), adjunto.getMimeType()));
                adjunto.setSha256(text(metadata.path("sha256")));
                if (metadata.path("file_size").canConvertToLong()) {
                    adjunto.setTamanioBytes(metadata.path("file_size").asLong());
                }
            }
            String url = metadata != null ? text(metadata.path("url")) : null;
            if (!StringUtils.hasText(url)) {
                adjunto.setErrorDescarga("Meta no devolvio URL de descarga para el adjunto.");
                adjuntoRepository.save(adjunto);
                return false;
            }

            byte[] contenido = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .retrieve()
                    .body(byte[].class);
            if (contenido == null || contenido.length == 0) {
                adjunto.setErrorDescarga("El adjunto descargado esta vacio.");
                adjuntoRepository.save(adjunto);
                return false;
            }

            String nombre = guardarArchivo(contenido, adjunto.getNombreArchivoOriginal());
            adjunto.setNombreArchivo(nombre);
            adjunto.setTamanioBytes((long) contenido.length);
            adjuntarAutomaticamenteSiTieneContexto(adjunto);
            adjuntoRepository.save(adjunto);
            return adjunto.getEstado() == EstadoWhatsappAdjunto.CLASIFICADO;
        } catch (RestClientException | IllegalArgumentException | IOException exception) {
            adjunto.setErrorDescarga(exception.getMessage());
            adjuntoRepository.save(adjunto);
            return false;
        }
    }

    private void adjuntarAutomaticamenteSiTieneContexto(WhatsappAdjunto adjunto) {
        Expediente expediente = adjunto.getExpediente();
        Solicitud solicitud = adjunto.getSolicitud();
        if ((expediente == null && solicitud == null) || !StringUtils.hasText(adjunto.getNombreArchivo())) {
            return;
        }
        if (expediente == null && solicitud != null && esPdf(adjunto)) {
            adjuntarPdfCompletoASolicitud(adjunto, solicitud);
            return;
        }
        TipoDocumento tipo = tipoPorNombre(adjunto.getNombreArchivoOriginal())
                .or(() -> tipoPorOcr(adjunto))
                .or(() -> expediente != null ? tipoPorRequisitoUnico(expediente) : Optional.empty())
                .orElse(TipoDocumento.OTROS);
        Documento documento = new Documento();
        documento.setExpediente(expediente);
        documento.setSolicitud(solicitud);
        documento.setCliente(expediente != null ? expediente.getCliente() : solicitud.getCliente());
        documento.setTipoDocumento(tipo);
        documento.setNombreArchivo(adjunto.getNombreArchivo());
        documento.setNombreArchivoOriginal(StringUtils.hasText(adjunto.getNombreArchivoOriginal())
                ? adjunto.getNombreArchivoOriginal()
                : "Documento recibido por WhatsApp");
        documento.setDescripcionArchivo("Recibido y clasificado automaticamente desde WhatsApp.");
        documento.setSubidoPor(usuarioCliente(expediente, solicitud));
        documentoRepository.save(documento);

        if (expediente != null && tipo != TipoDocumento.OTROS) {
            requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                    .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                    .filter(requisito -> tipoCubreRequisito(tipo, requisito.getTipoDocumento()))
                    .filter(requisito -> !esDocumentoIdentidad(requisito.getTipoDocumento()))
                    .findFirst()
                    .ifPresent(requisito -> {
                        requisito.setDocumento(documento);
                        requisito.setEstado(EstadoRequisitoDocumental.APORTADO);
                        requisitoRepository.save(requisito);
                    });
        }

        adjunto.setEstado(EstadoWhatsappAdjunto.CLASIFICADO);
        registrarAdjuntoAutomatico(expediente, solicitud, tipo, documento);
    }

    private void adjuntarPdfCompletoASolicitud(WhatsappAdjunto adjunto, Solicitud solicitud) {
        Path archivo = rutaAdjunto(adjunto);
        if (archivo == null) {
            return;
        }
        Usuario usuario = Optional.ofNullable(usuarioCliente(null, solicitud))
                .or(() -> primerAdminActivo())
                .orElse(null);
        documentoService.guardarParaSolicitud(solicitud.getId(), new PathMultipartFile(
                archivo,
                adjunto.getNombreArchivoOriginal(),
                adjunto.getMimeType()), TipoDocumento.EXPEDIENTE_COMPLETO, usuario);
        adjunto.setEstado(EstadoWhatsappAdjunto.CLASIFICADO);
        historialCambioService.registrarCambioSolicitud(solicitud, usuario, "WHATSAPP DOCUMENTO",
                "Adjunto PDF de WhatsApp incorporado como expediente completo: "
                        + (StringUtils.hasText(adjunto.getNombreArchivoOriginal())
                        ? adjunto.getNombreArchivoOriginal()
                        : "Documento recibido por WhatsApp")
                        + ".");
        intentarLecturaIaSolicitud(solicitud);
    }

    private void intentarLecturaIaSolicitud(Solicitud solicitud) {
        Usuario admin = primerAdminActivo().orElse(null);
        if (admin == null) {
            return;
        }
        try {
            solicitudDocumentacionIaService.procesarDocumentacion(solicitud.getId(), admin);
        } catch (RuntimeException ignored) {
            // La lectura IA es una ayuda posterior al OCR; el documento debe quedar igualmente disponible para revision manual.
        }
    }

    private void registrarAdjuntoAutomatico(Expediente expediente, Solicitud solicitud, TipoDocumento tipo, Documento documento) {
        String tipoTexto = tipo.name().replace('_', ' ');
        if (expediente != null) {
            historialCambioService.registrarCambioExpediente(expediente, null, "WHATSAPP DOCUMENTO",
                    "Adjunto de WhatsApp incorporado como " + tipoTexto + ": " + documento.getNombreArchivoOriginal() + ".");
            return;
        }
        historialCambioService.registrarCambioSolicitud(solicitud, null, "WHATSAPP DOCUMENTO",
                "Adjunto de WhatsApp incorporado como " + tipoTexto + ": " + documento.getNombreArchivoOriginal() + ".");
    }

    private Optional<TipoDocumento> tipoPorRequisitoUnico(Expediente expediente) {
        List<TipoDocumento> tipos = requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .map(RequisitoDocumentalExpediente::getTipoDocumento)
                .filter(tipo -> tipo != null)
                .distinct()
                .toList();
        return tipos.size() == 1 ? Optional.of(tipos.get(0)) : Optional.empty();
    }

    private Optional<TipoDocumento> tipoPorOcr(WhatsappAdjunto adjunto) {
        Path archivo = rutaAdjunto(adjunto);
        if (archivo == null) {
            return Optional.empty();
        }
        try {
            return ocrPdfService.detectarTipoDocumento(new PathMultipartFile(
                    archivo,
                    adjunto.getNombreArchivoOriginal(),
                    adjunto.getMimeType()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Path rutaAdjunto(WhatsappAdjunto adjunto) {
        if (!StringUtils.hasText(adjunto.getNombreArchivo())) {
            return null;
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path archivo = base.resolve(adjunto.getNombreArchivo()).normalize();
        if (!archivo.startsWith(base) || !Files.exists(archivo)) {
            return null;
        }
        return archivo;
    }

    private boolean esPdf(WhatsappAdjunto adjunto) {
        String mimeType = adjunto.getMimeType();
        String nombre = adjunto.getNombreArchivoOriginal();
        return "application/pdf".equalsIgnoreCase(mimeType)
                || (StringUtils.hasText(nombre) && nombre.toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    private Optional<TipoDocumento> tipoPorNombre(String nombre) {
        if (!StringUtils.hasText(nombre)) {
            return Optional.empty();
        }
        String limpio = nombre.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", " ");
        if (limpio.contains("informe") && limpio.contains("dgt")) {
            return Optional.of(TipoDocumento.INFORME_DGT);
        }
        Map<String, TipoDocumento> reglas = Map.ofEntries(
                Map.entry("dni", TipoDocumento.DNI),
                Map.entry("cif", TipoDocumento.CIF),
                Map.entry("contrato", TipoDocumento.CONTRATO_COMPRAVENTA),
                Map.entry("compraventa", TipoDocumento.CONTRATO_COMPRAVENTA),
                Map.entry("permiso", TipoDocumento.PERMISO_CIRCULACION),
                Map.entry("circulacion", TipoDocumento.PERMISO_CIRCULACION),
                Map.entry("ficha", TipoDocumento.FICHA_TECNICA),
                Map.entry("tecnica", TipoDocumento.FICHA_TECNICA),
                Map.entry("mandato", TipoDocumento.MANDATO),
                Map.entry("factura", TipoDocumento.FACTURA),
                Map.entry("620", TipoDocumento.MODELO_620),
                Map.entry("dgt", TipoDocumento.COMPROBANTE_DGT)
        );
        List<TipoDocumento> detectados = reglas.entrySet().stream()
                .filter(entry -> limpio.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .limit(2)
                .toList();
        return detectados.size() == 1 ? Optional.of(detectados.get(0)) : Optional.empty();
    }

    private boolean tipoCubreRequisito(TipoDocumento documento, TipoDocumento requisito) {
        if (documento == null || requisito == null) {
            return false;
        }
        if (documento == requisito) {
            return true;
        }
        if (requisito == TipoDocumento.MANDATO) {
            return documento == TipoDocumento.MANDATO_REPRESENTACION;
        }
        if (requisito == TipoDocumento.CONTRATO_COMPRAVENTA) {
            return documento == TipoDocumento.FACTURA;
        }
        return (requisito == TipoDocumento.PERMISO_CIRCULACION || requisito == TipoDocumento.FICHA_TECNICA)
                && documento == TipoDocumento.INFORME_DGT;
    }

    private boolean esDocumentoIdentidad(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.DNI || tipoDocumento == TipoDocumento.CIF;
    }

    private Usuario usuarioCliente(Expediente expediente, Solicitud solicitud) {
        Long clienteId = expediente != null && expediente.getCliente() != null
                ? expediente.getCliente().getId()
                : solicitud != null && solicitud.getCliente() != null ? solicitud.getCliente().getId() : null;
        if (clienteId == null) {
            return null;
        }
        return usuarioRepository.findFirstByClienteIdAndRolUsuarioAndActivoTrueOrderByIdAsc(clienteId, RolUsuario.CLIENTE)
                .orElse(null);
    }

    private Optional<Usuario> primerAdminActivo() {
        return usuarioRepository.findFirstByRolUsuarioAndActivoTrueOrderByIdAsc(RolUsuario.ADMIN);
    }

    private record PathMultipartFile(Path path, String originalFilename, String contentType) implements MultipartFile {
        @Override
        public String getName() {
            return originalFilename != null ? originalFilename : path.getFileName().toString();
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename != null ? originalFilename : path.getFileName().toString();
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException exception) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException exception) {
                return 0;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonNode mediaNode(JsonNode message, String tipo) {
        if (!StringUtils.hasText(tipo)) {
            return MissingNode.getInstance();
        }
        return message.path(tipo);
    }

    private String nombreOriginal(JsonNode media, String mediaId, String tipo) {
        String filename = text(media.path("filename"));
        if (StringUtils.hasText(filename)) {
            return sanitizar(filename);
        }
        String mimeType = text(media.path("mime_type"));
        return "WHATSAPP_" + mediaId + extensionPara(tipo, mimeType);
    }

    private String guardarArchivo(byte[] contenido, String nombreOriginal) throws IOException {
        LocalDate hoy = LocalDate.now();
        Path carpetaBase = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path carpetaWhatsapp = carpetaBase.resolve(Paths.get("whatsapp", String.valueOf(hoy.getYear()), "%02d".formatted(hoy.getMonthValue()))).normalize();
        if (!carpetaWhatsapp.startsWith(carpetaBase)) {
            throw new IllegalArgumentException("Ruta de archivo no permitida");
        }
        Files.createDirectories(carpetaWhatsapp);
        String nombre = UUID.randomUUID() + "_" + sanitizar(nombreOriginal);
        Path destino = carpetaWhatsapp.resolve(nombre).normalize();
        if (!destino.startsWith(carpetaWhatsapp)) {
            throw new IllegalArgumentException("Ruta de archivo no permitida");
        }
        Files.write(destino, contenido);
        return carpetaBase.relativize(destino).toString().replace('\\', '/');
    }

    private String sanitizar(String nombre) {
        String limpio = Paths.get(nombre != null ? nombre : "adjunto").getFileName().toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (!StringUtils.hasText(limpio) || ".".equals(limpio) || "..".equals(limpio)) {
            return "adjunto.bin";
        }
        return limpio.length() > 180 ? limpio.substring(0, 180) : limpio;
    }

    private String extensionPara(String tipo, String mimeType) {
        Map<String, String> porMime = Map.of(
                "application/pdf", ".pdf",
                "image/jpeg", ".jpg",
                "image/png", ".png",
                "image/webp", ".webp"
        );
        if (StringUtils.hasText(mimeType) && porMime.containsKey(mimeType)) {
            return porMime.get(mimeType);
        }
        if ("image".equals(tipo)) {
            return ".jpg";
        }
        if ("document".equals(tipo)) {
            return ".pdf";
        }
        return ".bin";
    }

    private String version() {
        String value = StringUtils.hasText(graphApiVersion) ? graphApiVersion.trim() : "v23.0";
        return value.startsWith("v") ? value : "v" + value;
    }

    private String text(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText(null) : null;
    }

    private String textOrFallback(JsonNode node, String fallback) {
        String value = text(node);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private JsonNode parseJson(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new RestClientException("Meta devolvio una respuesta no interpretable.");
        }
    }
}
