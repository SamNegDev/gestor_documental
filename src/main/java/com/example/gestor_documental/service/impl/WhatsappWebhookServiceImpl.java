package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.AvisoIncidencia;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.TipoTramiteRepository;
import com.example.gestor_documental.repository.WhatsappAdjuntoRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.service.AvisoAdminService;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.WhatsappMediaService;
import com.example.gestor_documental.service.WhatsappOutboundService;
import com.example.gestor_documental.service.WhatsappWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class WhatsappWebhookServiceImpl implements WhatsappWebhookService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsappWebhookEventoRepository eventoRepository;
    private final ClienteRepository clienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final HitoExpedienteRepository hitoExpedienteRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final AvisoIncidenciaRepository avisoIncidenciaRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final SolicitudRepository solicitudRepository;
    private final TipoTramiteRepository tipoTramiteRepository;
    private final WhatsappAdjuntoRepository adjuntoRepository;
    private final AvisoAdminService avisoAdminService;
    private final HistorialCambioService historialCambioService;
    private final ConfiguracionSeguimientoService configuracionSeguimientoService;
    private final WhatsappOutboundService whatsappOutboundService;
    private final WhatsappMediaService whatsappMediaService;

    private static final DateTimeFormatter FECHA_ESTADO_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final long VENTANA_NUEVO_LOTE_DOCUMENTOS_SEGUNDOS = 180;
    private static final long ESPERA_CIERRE_LOTE_DOCUMENTOS_SEGUNDOS = 75;
    private static final int DIGITOS_TELEFONO_COINCIDENCIA = 9;
    private static final long HORAS_VALIDEZ_CONTEXTO_WHATSAPP = 24;

    private final ConcurrentMap<String, LoteDocumentosWhatsapp> lotesDocumentosPorContexto = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cierreLotesDocumentosExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "whatsapp-cierre-lotes-documentos");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${app.whatsapp.webhook.verify-token:}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    @Value("${app.public-url:}")
    private String publicUrl;

    @Override
    @Transactional
    public void procesar(String payload, String signature) {
        if (!firmaValida(payload, signature)) {
            throw new IllegalArgumentException("Firma de WhatsApp no valida.");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode value = root.path("entry").path(0).path("changes").path(0).path("value");
            JsonNode message = value.path("messages").path(0);
            if (message.isMissingNode() || message.isNull()) {
                return;
            }
            String messageId = text(message.path("id"));
            if (StringUtils.hasText(messageId) && eventoRepository.existsByMessageId(messageId)) {
                return;
            }

            WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
            evento.setPayload(payload);
            evento.setMessageId(messageId);
            evento.setTelefono(normalizarTelefono(text(message.path("from"))));
            evento.setTipo(text(message.path("type")));
            evento.setTexto(extraerTexto(message));
            evento.setAccionCodigo(extraerAccionCodigo(message));
            evento.setNombrePerfil(text(value.path("contacts").path(0).path("profile").path("name")));
            asociarClienteYExpediente(evento);
            boolean media = esMedia(message);
            if (media) {
                aplicarContextoExpedientePrevio(evento);
                aplicarContextoSolicitudPrevio(evento);
                if (evento.getExpediente() != null && !StringUtils.hasText(evento.getAccionCodigo())) {
                    evento.setAccionCodigo("gestapp_contexto_expediente");
                } else if (evento.getSolicitud() != null && !StringUtils.hasText(evento.getAccionCodigo())) {
                    evento.setAccionCodigo("gestapp_contexto_solicitud");
                }
                evento.setEstado(EstadoWhatsappEvento.REVISADO);
                evento.setFechaRevision(LocalDateTime.now());
            } else if (!procesarMenuPrioritario(evento) && !procesarAccion(evento) && !procesarMensajeCliente(evento) && !procesarRespuestaEstadoTramite(evento) && !procesarRespuestaNuevaSolicitud(evento)) {
                if (!procesarMenuEspontaneo(evento)) {
                    marcarRevisado(evento);
                }
            }
            evento.setProcesado(true);
            evento = eventoRepository.save(evento);
            if (media) {
                iniciarLoteDocumentos(evento);
                whatsappMediaService.descargarYGuardar(evento, message);
                registrarActividadLoteDocumentos(evento);
            }
        } catch (Exception ex) {
            WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
            evento.setPayload(payload != null ? payload : "");
            evento.setProcesado(false);
            evento.setErrorProcesado(ex.getMessage());
            eventoRepository.save(evento);
            throw new IllegalArgumentException("No se pudo procesar el webhook de WhatsApp.", ex);
        }
    }

    @Override
    public boolean verificarToken(String token) {
        return StringUtils.hasText(verifyToken) && verifyToken.equals(token);
    }

    @Override
    public boolean firmaValida(String payload, String signature) {
        if (!StringUtils.hasText(appSecret)) {
            return true;
        }
        if (!StringUtils.hasText(signature) || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    @PreDestroy
    public void detenerCierreLotesDocumentosExecutor() {
        cierreLotesDocumentosExecutor.shutdownNow();
    }

    private void asociarClienteYExpediente(WhatsappWebhookEvento evento) {
        if (!StringUtils.hasText(evento.getTelefono())) {
            return;
        }
        resolverClientePorTelefono(evento.getTelefono()).ifPresent(evento::setCliente);
    }

    private Optional<Cliente> resolverClientePorTelefono(String telefono) {
        List<Cliente> candidatos = clienteRepository.findAll().stream()
                .filter(item -> coincideTelefono(telefono, item.getTelefono()))
                .toList();
        return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
    }

    private boolean coincideTelefono(String origen, String telefonoCliente) {
        String origenNormalizado = normalizarTelefono(origen);
        String cliente = normalizarTelefono(telefonoCliente);
        if (!StringUtils.hasText(origenNormalizado)
                || !StringUtils.hasText(cliente)
                || origenNormalizado.length() < DIGITOS_TELEFONO_COINCIDENCIA
                || cliente.length() < DIGITOS_TELEFONO_COINCIDENCIA) {
            return false;
        }
        return ultimosDigitosTelefono(origenNormalizado).equals(ultimosDigitosTelefono(cliente));
    }

    private String ultimosDigitosTelefono(String telefono) {
        return telefono.substring(telefono.length() - DIGITOS_TELEFONO_COINCIDENCIA);
    }

    private void aplicarContextoExpedientePrevio(WhatsappWebhookEvento evento) {
        if (evento.getExpediente() != null || !StringUtils.hasText(evento.getTelefono())) {
            return;
        }
        eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono())
                .filter(this::eventoMantieneContextoExpediente)
                .filter(this::contextoWhatsappReciente)
                .filter(anterior -> contextoCompatibleConCliente(evento, anterior.getExpediente().getCliente()))
                .ifPresent(anterior -> {
                    evento.setExpediente(anterior.getExpediente());
                    if (evento.getCliente() == null) {
                        evento.setCliente(anterior.getExpediente().getCliente());
                    }
                });
    }

    private void aplicarContextoSolicitudPrevio(WhatsappWebhookEvento evento) {
        if (evento.getSolicitud() != null || evento.getExpediente() != null || !StringUtils.hasText(evento.getTelefono())) {
            return;
        }
        eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono())
                .filter(this::eventoMantieneContextoSolicitud)
                .filter(this::contextoWhatsappReciente)
                .filter(anterior -> contextoCompatibleConCliente(evento, anterior.getSolicitud().getCliente()))
                .ifPresent(anterior -> {
                    evento.setSolicitud(anterior.getSolicitud());
                    if (evento.getCliente() == null) {
                        evento.setCliente(anterior.getSolicitud().getCliente());
                    }
                });
    }

    private boolean contextoWhatsappReciente(WhatsappWebhookEvento evento) {
        return evento != null
                && evento.getFechaRecepcion() != null
                && !evento.getFechaRecepcion().isBefore(LocalDateTime.now().minusHours(HORAS_VALIDEZ_CONTEXTO_WHATSAPP));
    }

    private boolean contextoCompatibleConCliente(WhatsappWebhookEvento evento, Cliente clienteContexto) {
        if (evento.getCliente() == null || clienteContexto == null) {
            return true;
        }
        return Objects.equals(evento.getCliente().getId(), clienteContexto.getId());
    }

    private boolean eventoMantieneContextoExpediente(WhatsappWebhookEvento evento) {
        return evento != null
                && evento.getExpediente() != null
                && ("gestapp_contexto_expediente".equals(evento.getAccionCodigo())
                    || "gestapp_mensaje_cliente".equals(evento.getAccionCodigo())
                    || accionAdmiteContextoExpediente(evento.getAccionCodigo()));
    }

    private boolean eventoMantieneContextoSolicitud(WhatsappWebhookEvento evento) {
        return evento != null
                && evento.getSolicitud() != null
                && ("gestapp_contexto_solicitud".equals(evento.getAccionCodigo())
                    || "gestapp_mensaje_cliente".equals(evento.getAccionCodigo())
                    || accionAdmiteContextoSolicitud(evento.getAccionCodigo()));
    }

    private boolean accionAdmiteContextoExpediente(String accion) {
        if (!StringUtils.hasText(accion)) {
            return false;
        }
        if (accion.startsWith("gestapp_inc_")) {
            return true;
        }
        return List.of(
                "gestapp_contactar",
                "gestapp_enviar_mensaje",
                "gestapp_estado_actual",
                "gestapp_subir_mas_documentacion",
                "gestapp_ver_pendientes",
                "gestapp_recordar_manana",
                "gestapp_enviar_documentacion",
                "gestapp_ver_expediente"
        ).contains(accion);
    }

    private boolean accionAdmiteContextoSolicitud(String accion) {
        return List.of(
                "gestapp_solicitud_aportar_documentos",
                "gestapp_solicitud_estado",
                "gestapp_solicitud_mensaje",
                "gestapp_contactar_solicitud"
        ).contains(accion);
    }

    private boolean procesarAccion(WhatsappWebhookEvento evento) {
        if (!StringUtils.hasText(evento.getAccionCodigo())) {
            return false;
        }
        String accion = evento.getAccionCodigo();
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            return true;
        }
        if ("gestapp_recibir_informacion".equals(accion)) {
            enviarInformacionPendienteCliente(evento);
            return true;
        }
        if (accion.startsWith("gestapp_inc_")) {
            procesarAccionIncidencia(evento, accion);
            return true;
        }
        if ("gestapp_estado_tramite".equals(accion) || "gestapp_menu_expedientes".equals(accion)) {
            solicitarMatriculaEstadoTramite(evento);
            return true;
        }
        if ("gestapp_menu_pendiente".equals(accion)) {
            enviarDocumentacionPendiente(evento);
            return true;
        }
        if ("gestapp_solicitud_aportar_documentos".equals(accion)) {
            aplicarContextoSolicitudPrevio(evento);
            enviarEnlaceUltimaSolicitud(evento, "Puedes aportar documentacion desde la solicitud:");
            return true;
        }
        if ("gestapp_solicitud_estado".equals(accion)) {
            aplicarContextoSolicitudPrevio(evento);
            enviarEstadoUltimaSolicitud(evento);
            return true;
        }
        if ("gestapp_nueva_solicitud".equals(accion)) {
            iniciarNuevaSolicitud(evento);
            return true;
        }
        if (accion.startsWith("gestapp_solicitud_tipo_")) {
            solicitarMatriculaNuevaSolicitud(evento);
            return true;
        }
        if ("gestapp_menu_principal".equals(accion)) {
            evento.setExpediente(null);
            evento.setSolicitud(null);
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_salir".equals(accion)) {
            evento.setExpediente(null);
            evento.setSolicitud(null);
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "👍 Perfecto, lo dejamos aqui.\n\nCuando necesites cualquier cosa, escribe *menu* y seguimos.");
            marcarRevisado(evento);
            return true;
        }
        if (accionAdmiteContextoExpediente(accion)) {
            aplicarContextoExpedientePrevio(evento);
        }
        if ("gestapp_cambiar_expediente".equals(accion) || "gestapp_consultar_otro".equals(accion)) {
            solicitarMatriculaEstadoTramite(evento);
            return true;
        }
        if ("gestapp_contactar_general".equals(accion)) {
            confirmarContactoGeneral(evento);
            return true;
        }
        if ("gestapp_mensaje_general".equals(accion)) {
            solicitarMensajeClienteGeneral(evento);
            return true;
        }
        if ("gestapp_contactar_solicitud".equals(accion)) {
            aplicarContextoSolicitudPrevio(evento);
            confirmarContactoSolicitud(evento);
            return true;
        }
        if ("gestapp_contactar".equals(accion)) {
            confirmarContactoExpediente(evento);
            return true;
        }
        if ("gestapp_enviar_mensaje".equals(accion)) {
            solicitarMensajeClienteExpediente(evento);
            return true;
        }
        if ("gestapp_solicitud_mensaje".equals(accion)) {
            aplicarContextoSolicitudPrevio(evento);
            solicitarMensajeClienteSolicitud(evento);
            return true;
        }
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes continuar desde el portal de GestApp:");
            return true;
        }
        if ("gestapp_estado_actual".equals(accion)) {
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), mensajeEstadoTramite(evento.getExpediente()));
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_subir_mas_documentacion".equals(accion)) {
            enviarEnlacePortalSinHistorial(evento, "Puedes subir la documentacion desde el portal:");
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo de nuevo las opciones del expediente.");
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_ver_pendientes".equals(accion)) {
            enviarDocumentacionPendiente(evento);
            return true;
        }
        if ("gestapp_recordar_manana".equals(accion)) {
            posponerSeguimiento(evento);
            return true;
        }
        if ("gestapp_enviar_documentacion".equals(accion)) {
            enviarEnlacePortal(evento, "Puedes subir la documentacion desde el portal:");
            return true;
        }
        if ("gestapp_ver_expediente".equals(accion)) {
            enviarEnlacePortal(evento, "Puedes consultar el expediente desde el portal:");
            return true;
        }
        return false;
    }

    private void enviarInformacionPendienteCliente(WhatsappWebhookEvento evento) {
        List<Incidencia> incidencias = incidenciaRepository.findSeguimientoPendienteByCliente(
                evento.getCliente().getId(),
                LocalDateTime.now());
        if (incidencias.isEmpty()) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "Ahora mismo no veo incidencias pendientes de notificar asociadas a este telefono.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return;
        }

        incidencias.forEach(this::registrarAvisoWhatsappRecibido);
        if (incidencias.size() == 1) {
            Incidencia incidencia = incidencias.get(0);
            fijarContextoIncidencia(evento, incidencia);
            whatsappOutboundService.enviarMenuIncidencia(evento.getTelefono(), incidencia, detalleIncidenciaMenu(incidencia));
        } else {
            List<Incidencia> opciones = incidencias.stream().limit(3).toList();
            whatsappOutboundService.enviarSelectorIncidencias(
                    evento.getTelefono(),
                    resumenIncidenciasPendientes(incidencias),
                    opciones);
        }
        marcarRevisado(evento);
    }

    private void procesarAccionIncidencia(WhatsappWebhookEvento evento, String accion) {
        Optional<Incidencia> incidencia = incidenciaDesdeAccion(evento, accion);
        if (incidencia.isEmpty()) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No he podido localizar esa incidencia. Escribe menu para empezar de nuevo.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return;
        }

        Incidencia seleccionada = incidencia.get();
        fijarContextoIncidencia(evento, seleccionada);
        if (accion.startsWith("gestapp_inc_recordar_manana_")) {
            programarRecordatorioIncidencia(evento, seleccionada, 1);
            return;
        }
        if (accion.startsWith("gestapp_inc_recordar_7_")) {
            programarRecordatorioIncidencia(evento, seleccionada, 7);
            return;
        }
        if (accion.startsWith("gestapp_inc_recordar_15_")) {
            programarRecordatorioIncidencia(evento, seleccionada, 15);
            return;
        }
        if (accion.startsWith("gestapp_inc_subir_")) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "Envia ahora por aqui las fotos o documentos de " + etiquetaExpediente(seleccionada) + ". Los adjuntare a ese expediente.");
            marcarRevisado(evento);
            return;
        }
        if (accion.startsWith("gestapp_inc_responder_")) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "Escribe ahora la respuesta para " + etiquetaExpediente(seleccionada) + ". La dejare anotada para la gestoria.");
            marcarRevisado(evento);
            return;
        }
        if (accion.startsWith("gestapp_inc_recordar_")) {
            whatsappOutboundService.enviarMenuRecordatorioIncidencia(evento.getTelefono(), seleccionada);
            marcarRevisado(evento);
            return;
        }

        whatsappOutboundService.enviarMenuIncidencia(evento.getTelefono(), seleccionada, detalleIncidenciaMenu(seleccionada));
        marcarRevisado(evento);
    }

    private Optional<Incidencia> incidenciaDesdeAccion(WhatsappWebhookEvento evento, String accion) {
        return incidenciaIdDesdeAccion(accion)
                .flatMap(incidenciaRepository::findById)
                .filter(incidencia -> incidencia.getExpediente() != null)
                .filter(incidencia -> evento.getCliente() != null
                        && incidencia.getExpediente().getCliente() != null
                        && evento.getCliente().getId().equals(incidencia.getExpediente().getCliente().getId()));
    }

    private Optional<Long> incidenciaIdDesdeAccion(String accion) {
        if (!StringUtils.hasText(accion)) {
            return Optional.empty();
        }
        List<String> prefixes = List.of(
                "gestapp_inc_recordar_manana_",
                "gestapp_inc_recordar_15_",
                "gestapp_inc_recordar_7_",
                "gestapp_inc_recordar_",
                "gestapp_inc_responder_",
                "gestapp_inc_subir_",
                "gestapp_inc_"
        );
        for (String prefix : prefixes) {
            if (accion.startsWith(prefix)) {
                try {
                    return Optional.of(Long.parseLong(accion.substring(prefix.length())));
                } catch (NumberFormatException exception) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private void fijarContextoIncidencia(WhatsappWebhookEvento evento, Incidencia incidencia) {
        evento.setExpediente(incidencia.getExpediente());
        evento.setSolicitud(null);
    }

    private void programarRecordatorioIncidencia(WhatsappWebhookEvento evento, Incidencia incidencia, int dias) {
        LocalDateTime proximoAviso = LocalDateTime.now().plusDays(dias).with(LocalTime.of(9, 0));
        incidencia.setProximoAviso(proximoAviso);
        incidenciaRepository.save(incidencia);
        Usuario usuario = usuarioSeguimiento(incidencia);
        if (usuario != null) {
            historialCambioService.registrarCambioExpediente(incidencia.getExpediente(), usuario, "WHATSAPP RECORDATORIO",
                    "El cliente pidio recordatorio por WhatsApp hasta " + proximoAviso + ".");
        }
        whatsappOutboundService.enviarTexto(evento.getTelefono(), "Perfecto, te lo recordaremos en "
                + (dias == 1 ? "manana" : dias + " dias") + ".");
        whatsappOutboundService.enviarMenuIncidencia(evento.getTelefono(), incidencia, "Te dejo de nuevo las opciones de " + etiquetaExpediente(incidencia) + ".");
        marcarRevisado(evento);
    }

    private void registrarAvisoWhatsappRecibido(Incidencia incidencia) {
        ConfiguracionSeguimiento config = configuracionSeguimientoService.obtener();
        int numero = Math.min(incidencia.getContadorAvisos() + 1, config.getMaxAvisos());
        LocalDateTime ahora = LocalDateTime.now();
        incidencia.setContadorAvisos(numero);
        incidencia.setFechaUltimoAviso(ahora);
        incidencia.setProximoAviso(siguienteVencimiento(ahora, numero, config));
        incidencia.setSeguimientoArchivado(false);
        incidencia.setFechaArchivoSeguimiento(null);
        incidencia.setSeguimientoArchivadoPor(null);
        incidenciaRepository.save(incidencia);

        Usuario usuario = usuarioSeguimiento(incidencia);
        if (usuario == null) {
            return;
        }
        String mensaje = detalleIncidenciaMenu(incidencia);
        AvisoIncidencia aviso = new AvisoIncidencia();
        aviso.setIncidencia(incidencia);
        aviso.setNumeroAviso(numero);
        aviso.setEnviadoPor(usuario);
        aviso.setMensaje(mensaje);
        aviso.setDestinatario(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getTelefono() : null);
        aviso.setAsunto("WhatsApp info " + numero);
        aviso.setCanal("WHATSAPP");
        aviso.setEstadoEnvio("RECIBIDO");
        avisoIncidenciaRepository.save(aviso);
        historialCambioService.registrarCambioExpediente(incidencia.getExpediente(), usuario, "AVISO INCIDENCIA",
                "Aviso " + numero + " por WHATSAPP mostrado al cliente tras pulsar Recibir info.");
    }

    private LocalDateTime siguienteVencimiento(LocalDateTime fecha, int numeroAviso, ConfiguracionSeguimiento config) {
        int dias = switch (numeroAviso) {
            case 1 -> config.getDiasAviso1();
            case 2 -> config.getDiasAviso2();
            case 3 -> config.getDiasAviso3();
            case 4 -> config.getDiasAviso4();
            case 5 -> config.getDiasAviso5();
            default -> 0;
        };
        return numeroAviso >= config.getMaxAvisos() || dias <= 0 ? null : fecha.plusDays(dias);
    }

    private Usuario usuarioSeguimiento(Incidencia incidencia) {
        if (incidencia.getExpediente() != null && incidencia.getExpediente().getModificadoPor() != null) {
            return incidencia.getExpediente().getModificadoPor();
        }
        if (incidencia.getCreadoPor() != null) {
            return incidencia.getCreadoPor();
        }
        return incidencia.getExpediente() != null ? incidencia.getExpediente().getCreadoPor() : null;
    }

    private String resumenIncidenciasPendientes(List<Incidencia> incidencias) {
        StringBuilder builder = new StringBuilder("Tienes ")
                .append(incidencias.size())
                .append(incidencias.size() == 1 ? " asunto pendiente:" : " asuntos pendientes:")
                .append("\n");
        int maximo = Math.min(incidencias.size(), 8);
        for (int index = 0; index < maximo; index++) {
            Incidencia incidencia = incidencias.get(index);
            builder.append("\n")
                    .append(index + 1)
                    .append(". ")
                    .append(etiquetaExpediente(incidencia))
                    .append(" - ")
                    .append(tipoIncidenciaWhatsapp(incidencia));
        }
        if (incidencias.size() > maximo) {
            builder.append("\n... y ").append(incidencias.size() - maximo).append(" mas.");
        }
        builder.append("\n\nElige una incidencia para ver sus opciones.");
        return limitarTextoWhatsapp(builder.toString(), 950);
    }

    private String detalleIncidenciaMenu(Incidencia incidencia) {
        return limitarTextoWhatsapp(
                "Expediente " + etiquetaExpediente(incidencia)
                        + "\n" + tipoIncidenciaWhatsapp(incidencia) + ": " + detalleIncidenciaWhatsapp(incidencia)
                        + "\n\nQue quieres hacer?",
                950);
    }

    private String etiquetaExpediente(Incidencia incidencia) {
        if (incidencia.getExpediente() == null) {
            return "EXP-" + incidencia.getId();
        }
        if (StringUtils.hasText(incidencia.getExpediente().getMatricula())) {
            return normalizarMatricula(incidencia.getExpediente().getMatricula());
        }
        return "EXP-" + incidencia.getExpediente().getId();
    }

    private void solicitarMensajeClienteExpediente(WhatsappWebhookEvento evento) {
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes escribirnos desde el portal:");
            return;
        }
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Escribe ahora el mensaje que quieres dejar a la gestoria para este expediente.");
        marcarRevisado(evento);
    }

    private void solicitarMensajeClienteSolicitud(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Escribe ahora el mensaje que quieres dejar a la gestoria sobre tu solicitud.");
        marcarRevisado(evento);
    }

    private void solicitarMensajeClienteGeneral(WhatsappWebhookEvento evento) {
        evento.setExpediente(null);
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Escribe ahora el mensaje que quieres dejar a la gestoria.");
        marcarRevisado(evento);
    }

    private boolean procesarMensajeCliente(WhatsappWebhookEvento evento) {
        if (!"text".equals(evento.getTipo()) || evento.getCliente() == null || !StringUtils.hasText(evento.getTexto())) {
            return false;
        }
        Optional<WhatsappWebhookEvento> anterior = eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono());
        if (anterior.isEmpty()) {
            return false;
        }
        String accionAnterior = anterior.get().getAccionCodigo();
        if (!"gestapp_enviar_mensaje".equals(accionAnterior)
                && !"gestapp_solicitud_mensaje".equals(accionAnterior)
                && !"gestapp_mensaje_general".equals(accionAnterior)
                && !(StringUtils.hasText(accionAnterior) && accionAnterior.startsWith("gestapp_inc_responder_"))) {
            return false;
        }
        evento.setAccionCodigo("gestapp_mensaje_cliente");
        Optional<Incidencia> incidenciaAnterior = incidenciaDesdeAccion(evento, accionAnterior);
        if (evento.getExpediente() == null && anterior.get().getExpediente() != null) {
            evento.setExpediente(anterior.get().getExpediente());
        } else if (evento.getExpediente() == null && incidenciaAnterior.isPresent()) {
            evento.setExpediente(incidenciaAnterior.get().getExpediente());
        }
        if (evento.getSolicitud() == null && anterior.get().getSolicitud() != null) {
            evento.setSolicitud(anterior.get().getSolicitud());
        }
        whatsappOutboundService.enviarTexto(evento.getTelefono(), "Mensaje recibido. Lo dejamos anotado para la gestoria.");
        if (incidenciaAnterior.isPresent()) {
            whatsappOutboundService.enviarMenuIncidencia(evento.getTelefono(), incidenciaAnterior.get(), "Te dejo de nuevo las opciones de " + etiquetaExpediente(incidenciaAnterior.get()) + ".");
        } else if (evento.getExpediente() != null) {
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo de nuevo las opciones del expediente.");
        } else if (evento.getSolicitud() != null) {
            whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), evento.getSolicitud(), "Te dejo de nuevo las opciones de la solicitud.");
        } else {
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        }
        return true;
    }

    private void solicitarMatriculaEstadoTramite(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto. Indícame la matrícula del vehículo para consultar el estado del trámite.");
        marcarRevisado(evento);
    }

    private boolean procesarRespuestaEstadoTramite(WhatsappWebhookEvento evento) {
        if (!"text".equals(evento.getTipo()) || evento.getCliente() == null || !StringUtils.hasText(evento.getTexto())) {
            return false;
        }
        Optional<WhatsappWebhookEvento> anterior = eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono());
        if (anterior.isEmpty() || !esAccionEstadoTramite(anterior.get().getAccionCodigo())) {
            return false;
        }
        String matricula = normalizarMatricula(evento.getTexto());
        if (!StringUtils.hasText(matricula) || matricula.length() < 4 || matricula.length() > 10) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No he podido reconocer la matrícula. Envíamela sin espacios ni guiones, por ejemplo 1234ABC.");
            marcarRevisado(evento);
            return true;
        }
        List<Expediente> expedientes = expedienteRepository.findByClienteIdAndMatriculaNormalizada(evento.getCliente().getId(), matricula);
        if (expedientes.isEmpty()) {
            List<Solicitud> solicitudes = solicitudRepository.findByClienteIdAndMatriculaNormalizada(evento.getCliente().getId(), matricula);
            if (!solicitudes.isEmpty()) {
                Solicitud solicitud = solicitudes.get(0);
                evento.setSolicitud(solicitud);
                evento.setAccionCodigo("gestapp_contexto_solicitud");
                whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud,
                        "Estado de tu solicitud: " + estadoSolicitudTexto(solicitud) + ".");
                marcarRevisado(evento);
                historialCambioService.registrarCambioSolicitud(solicitud, solicitud.getModificadoPor(), "WHATSAPP ESTADO",
                        "Se envio al cliente el estado de la solicitud por WhatsApp tras consultar la matricula " + matricula + ".");
                return true;
            }
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No encuentro un expediente ni una solicitud para la matrícula " + matricula + " asociado a este teléfono. Revisa la matrícula o solicita contacto con la gestoría.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return true;
        }
        Expediente expediente = expedientes.get(0);
        evento.setExpediente(expediente);
        evento.setAccionCodigo("gestapp_contexto_expediente");
        whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), expediente, mensajeEstadoTramite(expediente));
        marcarRevisado(evento);
        historialCambioService.registrarCambioExpediente(expediente, expediente.getModificadoPor(), "WHATSAPP ESTADO",
                "Se envio al cliente el estado del tramite por WhatsApp tras consultar la matricula " + matricula + ".");
        return true;
    }

    private void iniciarNuevaSolicitud(WhatsappWebhookEvento evento) {
        List<TipoTramite> tipos = tipoTramiteRepository.findAll().stream()
                .filter(TipoTramite::isActivo)
                .toList();
        whatsappOutboundService.enviarMenuTiposSolicitud(evento.getTelefono(), tipos);
        marcarRevisado(evento);
    }

    private void solicitarMatriculaNuevaSolicitud(WhatsappWebhookEvento evento) {
        String tipo = tipoSeleccionado(evento.getAccionCodigo())
                .flatMap(tipoTramiteRepository::findById)
                .map(this::descripcionTipoTramite)
                .orElse("el tramite seleccionado");
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto. Ahora dime la matricula del vehiculo para crear la solicitud de " + tipo + ".");
        marcarRevisado(evento);
    }

    private boolean procesarRespuestaNuevaSolicitud(WhatsappWebhookEvento evento) {
        if (!"text".equals(evento.getTipo()) || evento.getCliente() == null || !StringUtils.hasText(evento.getTexto())) {
            return false;
        }
        Optional<WhatsappWebhookEvento> anterior = eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono());
        if (anterior.isEmpty() || !StringUtils.hasText(anterior.get().getAccionCodigo())
                || !anterior.get().getAccionCodigo().startsWith("gestapp_solicitud_tipo_")) {
            return false;
        }
        Optional<Long> tipoId = tipoSeleccionado(anterior.get().getAccionCodigo());
        if (tipoId.isEmpty()) {
            return false;
        }
        TipoTramite tipoTramite = tipoTramiteRepository.findById(tipoId.get()).orElse(null);
        if (tipoTramite == null || !tipoTramite.isActivo()) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(), "No he podido encontrar ese tipo de tramite. Escribe menu y vuelve a intentarlo.");
            marcarRevisado(evento);
            return true;
        }
        String matricula = normalizarMatricula(evento.getTexto());
        if (!StringUtils.hasText(matricula) || matricula.length() < 4 || matricula.length() > 10) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No he podido reconocer la matricula. Enviamela sin espacios ni guiones, por ejemplo 1234ABC.");
            marcarRevisado(evento);
            return true;
        }
        Solicitud solicitud = new Solicitud();
        solicitud.setCliente(evento.getCliente());
        solicitud.setTipoTramite(tipoTramite);
        solicitud.setMatricula(matricula);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        solicitud.setObservaciones("Solicitud creada desde WhatsApp.");
        solicitud = solicitudRepository.save(solicitud);
        evento.setSolicitud(solicitud);
        evento.setAccionCodigo("gestapp_contexto_solicitud");
        historialCambioService.registrarCambioSolicitud(solicitud, null, "WHATSAPP SOLICITUD",
                "Solicitud creada desde WhatsApp para la matricula " + matricula + ".");
        whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud,
                "✅ *Solicitud SOL-" + solicitud.getId() + " creada con exito.*");
        marcarRevisado(evento);
        return true;
    }

    private Optional<Long> tipoSeleccionado(String accionCodigo) {
        if (!StringUtils.hasText(accionCodigo) || !accionCodigo.startsWith("gestapp_solicitud_tipo_")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(accionCodigo.substring("gestapp_solicitud_tipo_".length())));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String descripcionTipoTramite(TipoTramite tipoTramite) {
        if (tipoTramite == null) {
            return "tramite";
        }
        if (StringUtils.hasText(tipoTramite.getDescripcion())) {
            return tipoTramite.getDescripcion();
        }
        return tipoTramite.getNombre() != null ? tipoTramite.getNombre().name().replace('_', ' ') : "tramite";
    }

    private boolean esAccionEstadoTramite(String accionCodigo) {
        return "gestapp_estado_tramite".equals(accionCodigo)
                || "gestapp_menu_expedientes".equals(accionCodigo)
                || "gestapp_cambiar_expediente".equals(accionCodigo)
                || "gestapp_consultar_otro".equals(accionCodigo);
    }

    private String mensajeEstadoTramite(Expediente expediente) {
        String matricula = StringUtils.hasText(expediente.getMatricula()) ? normalizarMatricula(expediente.getMatricula()) : "Sin matricula";
        String tramite = expediente.getTipoTramite() != null && StringUtils.hasText(expediente.getTipoTramite().getDescripcion())
                ? expediente.getTipoTramite().getDescripcion()
                : "Tramite";
        String fecha = expediente.getFechaUltimaModificacion() != null
                ? "\n\n🕒 *Ultima actualizacion:* " + expediente.getFechaUltimaModificacion().format(FECHA_ESTADO_FORMATTER)
                : "";
        return "📄 *Estado del tramite*\n\n"
                + "🚗 *Matricula:* " + matricula + "\n"
                + "🧾 *Tramite:* " + tramite + "\n"
                + "📍 *Fase actual:* " + faseActualWhatsapp(expediente)
                + detallePendienteWhatsapp(expediente)
                + "\n\n✅ *Siguiente paso:* " + siguientePasoWhatsapp(expediente)
                + fecha;
    }

    private String detallePendienteWhatsapp(Expediente expediente) {
        StringBuilder builder = new StringBuilder();

        List<Incidencia> incidencias = incidenciaRepository.findByExpedienteIdAndResueltaFalse(expediente.getId()).stream()
                .sorted(Comparator.comparing(
                        (Incidencia incidencia) -> incidencia.getFechaCreacion() != null ? incidencia.getFechaCreacion() : LocalDateTime.MIN)
                        .reversed())
                .limit(3)
                .toList();
        if (!incidencias.isEmpty()) {
            builder.append("\n\n⚠️ *Pendiente de resolver:*");
            incidencias.forEach(incidencia -> builder
                    .append("\n- *").append(tipoIncidenciaWhatsapp(incidencia)).append("*")
                    .append(": ").append(detalleIncidenciaWhatsapp(incidencia)));
        }

        List<String> pendientes = requisitosPendientesWhatsapp(expediente);
        if (!pendientes.isEmpty()) {
            builder.append("\n\n📎 *Documentacion solicitada:*");
            pendientes.forEach(item -> builder.append("\n- ").append(item));
        } else if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL && incidencias.isEmpty()) {
            builder.append("\n\n⚠️ *Pendiente de resolver:*\n- Responder a la informacion adicional solicitada por la gestoria.");
        }

        return builder.toString();
    }

    private List<String> requisitosPendientesWhatsapp(Expediente expediente) {
        if (expediente.getEstadoExpediente() != EstadoExpediente.PENDIENTE_DOCUMENTACION
                && expediente.getEstadoExpediente() != EstadoExpediente.INCIDENCIA
                && expediente.getEstadoExpediente() != EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return List.of();
        }
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .map(this::descripcionRequisito)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
    }

    private String tipoIncidenciaWhatsapp(Incidencia incidencia) {
        if (incidencia.getTipoIncidencia() != null) {
            if (StringUtils.hasText(incidencia.getTipoIncidencia().getDescripcion())) {
                return incidencia.getTipoIncidencia().getDescripcion().trim();
            }
            TipoIncidenciaEnum tipo = incidencia.getTipoIncidencia().getNombre();
            if (tipo != null) {
                return tipo.name().replace('_', ' ');
            }
        }
        return "Incidencia";
    }

    private String detalleIncidenciaWhatsapp(Incidencia incidencia) {
        if (StringUtils.hasText(incidencia.getObservaciones())) {
            return limitarTextoWhatsapp(incidencia.getObservaciones().trim(), 350);
        }
        TipoIncidenciaEnum tipo = incidencia.getTipoIncidencia() != null ? incidencia.getTipoIncidencia().getNombre() : null;
        if (tipo == TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION && incidencia.getExpediente() != null) {
            List<String> pendientes = requisitosPendientesWhatsapp(incidencia.getExpediente());
            if (!pendientes.isEmpty()) {
                return "Aportar " + String.join(", ", pendientes);
            }
        }
        if (tipo == TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL) {
            return "Responder a la informacion solicitada.";
        }
        return "Revisar el detalle con la gestoria.";
    }

    private String limitarTextoWhatsapp(String texto, int maximo) {
        if (texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, Math.max(0, maximo - 3)).trim() + "...";
    }

    private String faseActualWhatsapp(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            return "Finalizado";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.RECHAZADO) {
            return "Rechazado";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA
                || expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return "Incidencias";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            return "Pendiente de documentacion";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
            return "Solicitada informacion adicional";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
            return "Informacion adicional recibida";
        }
        if (documentacionPendiente(expediente)) {
            return "Comprobacion de documentacion";
        }
        if (!hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION)) {
            return "Pendiente de subir a programa de gestion";
        }
        if (!modelo620Presentado(expediente)) {
            return "Tramite subido, a la espera de pasar el impuesto 620";
        }
        if (expediente.getEstadoExpediente() != EstadoExpediente.ENVIADO_DGT
                && !hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.ENVIADO_DGT)) {
            return "Impuesto 620 presentado, pendiente de enviar a DGT";
        }
        return "Tramite enviado a DGT, pendiente de cierre";
    }

    private String siguientePasoWhatsapp(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            return "El expediente ya esta cerrado.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.RECHAZADO) {
            return "Contactar con la gestoria si necesitas mas informacion.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            return "Aportar la documentacion pendiente.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
            return "Responder a la informacion solicitada.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA
                || expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return "Resolver la incidencia abierta.";
        }
        if (documentacionPendiente(expediente)) {
            return "La gestoria esta comprobando la documentacion.";
        }
        if (!hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION)) {
            return "Subir el tramite al programa de gestion.";
        }
        if (!modelo620Presentado(expediente)) {
            return "Presentar el impuesto 620.";
        }
        if (expediente.getEstadoExpediente() != EstadoExpediente.ENVIADO_DGT
                && !hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.ENVIADO_DGT)) {
            return "Enviar el tramite a DGT.";
        }
        return "Finalizar el expediente cuando DGT lo confirme.";
    }

    private boolean documentacionPendiente(Expediente expediente) {
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO);
    }

    private boolean modelo620Presentado(Expediente expediente) {
        return hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.MODELO_620_PRESENTADO);
    }

    private boolean procesarMenuEspontaneo(WhatsappWebhookEvento evento) {
        if (!esSolicitudMenu(evento.getTexto())) {
            return false;
        }
        evento.setExpediente(null);
        evento.setSolicitud(null);
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            return true;
        }
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        return true;
    }

    private boolean procesarMenuPrioritario(WhatsappWebhookEvento evento) {
        if (!"text".equals(evento.getTipo()) || !esSolicitudMenu(evento.getTexto())) {
            return false;
        }
        procesarMenuEspontaneo(evento);
        return true;
    }

    private void posponerSeguimiento(WhatsappWebhookEvento evento) {
        Optional<Incidencia> incidencia = incidenciaRepository.findByExpedienteIdAndResueltaFalse(evento.getExpediente().getId()).stream()
                .filter(item -> !item.isSeguimientoArchivado())
                .max(Comparator.comparing(item -> item.getFechaCreacion() != null ? item.getFechaCreacion() : LocalDateTime.MIN));
        if (incidencia.isEmpty()) {
            return;
        }
        LocalDateTime proximoAviso = LocalDateTime.now().plusDays(1).with(LocalTime.of(9, 0));
        incidencia.get().setProximoAviso(proximoAviso);
        incidenciaRepository.save(incidencia.get());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        historialCambioService.registrarCambioExpediente(evento.getExpediente(), evento.getExpediente().getModificadoPor(), "WHATSAPP ACCION",
                "El cliente pidio recordatorio por WhatsApp. Seguimiento pospuesto hasta " + proximoAviso + ".");
    }

    private void iniciarLoteDocumentos(WhatsappWebhookEvento evento) {
        String clave = claveLoteDocumentos(evento);
        if (!StringUtils.hasText(clave)) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        AtomicBoolean nuevoLote = new AtomicBoolean(false);
        AtomicReference<LoteDocumentosWhatsapp> loteActual = new AtomicReference<>();
        lotesDocumentosPorContexto.compute(clave, (key, lote) -> {
            boolean reiniciar = lote == null
                    || lote.getUltimaActividad().isBefore(ahora.minusSeconds(VENTANA_NUEVO_LOTE_DOCUMENTOS_SEGUNDOS));
            LoteDocumentosWhatsapp actualizado = reiniciar
                    ? nuevoLoteDocumentos(evento, ahora)
                    : lote.conActividad(ahora);
            nuevoLote.set(reiniciar);
            loteActual.set(actualizado);
            return actualizado;
        });
        if (nuevoLote.get()) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "Comenzando registro de documentos recibidos para " + loteActual.get().getDescripcionContexto()
                            + ". Te aviso con el total cuando termine.");
        }
    }

    private LoteDocumentosWhatsapp nuevoLoteDocumentos(WhatsappWebhookEvento evento, LocalDateTime fecha) {
        Long expedienteId = evento.getExpediente() != null ? evento.getExpediente().getId() : null;
        Long solicitudId = evento.getSolicitud() != null ? evento.getSolicitud().getId() : null;
        Long clienteId = evento.getCliente() != null ? evento.getCliente().getId() : null;
        return new LoteDocumentosWhatsapp(
                evento.getTelefono(),
                descripcionContextoDocumentos(evento),
                expedienteId,
                solicitudId,
                clienteId,
                fecha,
                1,
                fecha);
    }

    private void registrarActividadLoteDocumentos(WhatsappWebhookEvento evento) {
        String clave = claveLoteDocumentos(evento);
        if (!StringUtils.hasText(clave)) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        AtomicReference<LoteDocumentosWhatsapp> loteActual = new AtomicReference<>();
        lotesDocumentosPorContexto.compute(clave, (key, lote) -> {
            LoteDocumentosWhatsapp base = lote != null
                    ? lote
                    : nuevoLoteDocumentos(evento, ahora);
            LoteDocumentosWhatsapp actualizado = base.conActividad(ahora);
            loteActual.set(actualizado);
            return actualizado;
        });
        programarCierreLoteDocumentos(clave, loteActual.get().getVersion());
    }

    private void programarCierreLoteDocumentos(String clave, int versionEsperada) {
        cierreLotesDocumentosExecutor.schedule(
                () -> cerrarLoteDocumentosSiEstable(clave, versionEsperada),
                ESPERA_CIERRE_LOTE_DOCUMENTOS_SEGUNDOS,
                TimeUnit.SECONDS);
    }

    private void cerrarLoteDocumentosSiEstable(String clave, int versionEsperada) {
        LoteDocumentosWhatsapp lote = lotesDocumentosPorContexto.get(clave);
        if (lote == null || lote.getVersion() != versionEsperada) {
            return;
        }
        if (lotesDocumentosPorContexto.remove(clave, lote)) {
            long total = adjuntoRepository.countMediaRegistradosEnContextoDesde(
                    lote.getTelefono(),
                    lote.getExpedienteId(),
                    lote.getSolicitudId(),
                    EstadoWhatsappAdjunto.CLASIFICADO,
                    lote.getInicio());
            whatsappOutboundService.enviarTexto(lote.getTelefono(), mensajeCierreLoteDocumentos(lote, total));
            crearAvisoAdminLoteDocumentos(lote, total);
        }
    }

    private void crearAvisoAdminLoteDocumentos(LoteDocumentosWhatsapp lote, long total) {
        if (total <= 0) {
            return;
        }
        Expediente expediente = lote.getExpedienteId() != null
                ? expedienteRepository.findById(lote.getExpedienteId()).orElse(null)
                : null;
        Cliente cliente = lote.getClienteId() != null
                ? clienteRepository.findById(lote.getClienteId()).orElse(null)
                : null;
        String detalle = "Se " + (total == 1 ? "ha" : "han") + " incorporado " + total + " "
                + (total == 1 ? "documento" : "documentos")
                + " por WhatsApp en " + lote.getDescripcionContexto() + ".";
        avisoAdminService.crear(
                "WHATSAPP_DOCUMENTOS_LOTE",
                "Documentos WhatsApp incorporados",
                detalle,
                "WhatsApp",
                expediente,
                cliente);
    }

    private String mensajeCierreLoteDocumentos(LoteDocumentosWhatsapp lote, long total) {
        if (total <= 0) {
            return "He recibido la documentacion para " + lote.getDescripcionContexto()
                    + ", pero no he podido registrar documentos nuevos. La gestoria lo revisara.";
        }
        return "Se " + (total == 1 ? "ha" : "han") + " registrado " + total + " "
                + (total == 1 ? "documento" : "documentos")
                + " en " + lote.getDescripcionContexto() + ".";
    }

    private String claveLoteDocumentos(WhatsappWebhookEvento evento) {
        if (evento == null || !StringUtils.hasText(evento.getTelefono())) {
            return null;
        }
        if (evento.getSolicitud() != null) {
            return evento.getTelefono() + "|solicitud:" + evento.getSolicitud().getId();
        }
        if (evento.getExpediente() != null) {
            return evento.getTelefono() + "|expediente:" + evento.getExpediente().getId();
        }
        return null;
    }

    private String descripcionContextoDocumentos(WhatsappWebhookEvento evento) {
        if (evento.getSolicitud() != null) {
            StringBuilder descripcion = new StringBuilder("la solicitud SOL-")
                    .append(evento.getSolicitud().getId());
            if (StringUtils.hasText(evento.getSolicitud().getMatricula())) {
                descripcion.append(" (matricula ")
                        .append(evento.getSolicitud().getMatricula().trim().toUpperCase(Locale.ROOT))
                        .append(")");
            }
            return descripcion.toString();
        }
        if (evento.getExpediente() != null) {
            if (StringUtils.hasText(evento.getExpediente().getMatricula())) {
                return "el expediente " + evento.getExpediente().getMatricula().trim().toUpperCase(Locale.ROOT);
            }
            return "el expediente " + evento.getExpediente().getId();
        }
        return "el tramite";
    }

    private static final class LoteDocumentosWhatsapp {
        private final String telefono;
        private final String descripcionContexto;
        private final Long expedienteId;
        private final Long solicitudId;
        private final Long clienteId;
        private final LocalDateTime inicio;
        private final int version;
        private final LocalDateTime ultimaActividad;

        private LoteDocumentosWhatsapp(String telefono,
                                       String descripcionContexto,
                                       Long expedienteId,
                                       Long solicitudId,
                                       Long clienteId,
                                       LocalDateTime inicio,
                                       int version,
                                       LocalDateTime ultimaActividad) {
            this.telefono = telefono;
            this.descripcionContexto = descripcionContexto;
            this.expedienteId = expedienteId;
            this.solicitudId = solicitudId;
            this.clienteId = clienteId;
            this.inicio = inicio;
            this.version = version;
            this.ultimaActividad = ultimaActividad;
        }

        private LoteDocumentosWhatsapp conActividad(LocalDateTime fecha) {
            return new LoteDocumentosWhatsapp(
                    telefono,
                    descripcionContexto,
                    expedienteId,
                    solicitudId,
                    clienteId,
                    inicio,
                    version + 1,
                    fecha);
        }

        private String getTelefono() {
            return telefono;
        }

        private String getDescripcionContexto() {
            return descripcionContexto;
        }

        private Long getExpedienteId() {
            return expedienteId;
        }

        private Long getSolicitudId() {
            return solicitudId;
        }

        private Long getClienteId() {
            return clienteId;
        }

        private LocalDateTime getInicio() {
            return inicio;
        }

        private int getVersion() {
            return version;
        }

        private LocalDateTime getUltimaActividad() {
            return ultimaActividad;
        }
    }

    private void confirmarContactoGeneral(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "✅ Perfecto, paso el aviso a la gestoria para que contacten contigo.");
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
    }

    private void confirmarContactoSolicitud(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "✅ Perfecto, paso el aviso a la gestoria para que revisen tu solicitud.");
        Solicitud solicitud = evento.getCliente() != null ? solicitudActual(evento) : null;
        if (solicitud != null) {
            evento.setSolicitud(solicitud);
            evento.setAccionCodigo("gestapp_contexto_solicitud");
            whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud, "Te dejo las opciones de la solicitud.");
        } else {
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        }
    }

    private void confirmarContactoExpediente(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "✅ Perfecto, paso el aviso a la gestoria para que contacten contigo.");
        if (evento.getExpediente() != null) {
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo las opciones del expediente.");
        } else {
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        }
    }

    private void enviarEnlacePortal(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/expedientes/" + evento.getExpediente().getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        historialCambioService.registrarCambioExpediente(evento.getExpediente(), evento.getExpediente().getModificadoPor(), "WHATSAPP ACCION",
                "Se envio al cliente un enlace al portal tras pulsar un boton de WhatsApp.");
        whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo las opciones del expediente.");
    }

    private void enviarEnlacePortalSinHistorial(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/expedientes/" + evento.getExpediente().getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
    }

    private void enviarEnlacePortalCliente(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/cliente/expedientes";
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private void enviarEnlaceNuevaSolicitud(WhatsappWebhookEvento evento) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/cliente/solicitudes/nuevo";
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Puedes iniciar una nueva solicitud desde el portal:\n" + enlace);
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        marcarRevisado(evento);
    }

    private void enviarEstadoUltimaSolicitud(WhatsappWebhookEvento evento) {
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            marcarRevisado(evento);
            return;
        }
        Solicitud solicitud = solicitudActual(evento);
        if (solicitud == null) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(), "No veo solicitudes asociadas a este telefono.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return;
        }
        evento.setSolicitud(solicitud);
        evento.setAccionCodigo("gestapp_contexto_solicitud");
        whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud,
                "Estado: " + estadoSolicitudTexto(solicitud) + ".");
        marcarRevisado(evento);
    }

    private void enviarEnlaceUltimaSolicitud(WhatsappWebhookEvento evento, String introduccion) {
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            marcarRevisado(evento);
            return;
        }
        Solicitud solicitud = solicitudActual(evento);
        if (solicitud == null) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(), "No veo solicitudes asociadas a este telefono.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return;
        }
        evento.setSolicitud(solicitud);
        evento.setAccionCodigo("gestapp_contexto_solicitud");
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/solicitudes/" + solicitud.getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud, "Te dejo las opciones de la solicitud.");
        marcarRevisado(evento);
    }

    private String estadoSolicitudTexto(Solicitud solicitud) {
        if (solicitud.getEstadoSolicitud() == null) {
            return "pendiente de revision";
        }
        return solicitud.getEstadoSolicitud().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private Solicitud solicitudActual(WhatsappWebhookEvento evento) {
        if (evento.getSolicitud() != null) {
            return evento.getSolicitud();
        }
        return evento.getCliente() != null
                ? solicitudRepository.findByClienteIdOrderByFechaReferenciaDesc(evento.getCliente().getId()).stream().findFirst().orElse(null)
                : null;
    }

    private void enviarDocumentacionPendiente(WhatsappWebhookEvento evento) {
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes revisar la documentacion pendiente desde el portal:");
            return;
        }
        List<String> pendientes = requisitoRepository.findByExpedienteIdOrderByIdAsc(evento.getExpediente().getId()).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .map(this::descripcionRequisito)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
        String detalle = pendientes.isEmpty()
                ? "No veo documentacion marcada como pendiente en tu ultimo expediente. Puedes revisarlo en el portal:"
                : "Documentacion pendiente en tu ultimo expediente:\n- " + String.join("\n- ", pendientes) + "\n\nPuedes aportarla desde el portal:";
        enviarEnlacePortalSinHistorial(evento, detalle);
        whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo las opciones del expediente.");
        marcarRevisado(evento);
    }

    private String descripcionRequisito(RequisitoDocumentalExpediente requisito) {
        if (StringUtils.hasText(requisito.getDescripcion())) {
            return requisito.getDescripcion().trim();
        }
        if (requisito.getTipoDocumento() != null) {
            return requisito.getTipoDocumento().name().replace('_', ' ');
        }
        return null;
    }

    private void enviarIdentificacionNecesaria(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Hola, soy GestApp. No he podido identificar este telefono. Responde con tu nombre y la gestoria revisara la asociacion.");
    }

    private void marcarRevisado(WhatsappWebhookEvento evento) {
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private boolean esSolicitudMenu(String texto) {
        String limpio = normalizarTexto(texto);
        if (!StringUtils.hasText(limpio)) {
            return false;
        }
        if (pareceMatricula(limpio)) {
            return false;
        }
        if (contieneIntencionMenu(limpio)) {
            return true;
        }
        return List.of("hola", "buenas", "buenos dias", "buenas tardes", "menu", "menú", "ayuda", "inicio", "gestapp")
                .contains(limpio);
    }

    private boolean contieneIntencionMenu(String texto) {
        return texto.contains("menu")
                || texto.contains("ayuda")
                || texto.contains("opciones")
                || texto.contains("empezar")
                || texto.contains("inicio")
                || texto.contains("volver")
                || texto.contains("gestapp")
                || texto.contains("hacer una consulta")
                || texto.contains("nueva consulta")
                || texto.contains("quiero consultar")
                || texto.contains("necesito consultar")
                || texto.contains("necesito ayuda")
                || texto.contains("abrir menu")
                || texto.contains("ver menu")
                || texto.contains("mostrar menu");
    }

    private boolean pareceMatricula(String texto) {
        String normalizada = normalizarMatricula(texto);
        return StringUtils.hasText(normalizada)
                && normalizada.length() >= 5
                && normalizada.length() <= 8
                && normalizada.matches(".*\\d.*")
                && normalizada.matches(".*[A-Z].*");
    }

    private String normalizarTexto(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }
        String sinAcentos = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizarMatricula(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sinAcentos = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String extraerTexto(JsonNode message) {
        String tipo = text(message.path("type"));
        if ("text".equals(tipo)) {
            return text(message.path("text").path("body"));
        }
        if ("button".equals(tipo)) {
            return text(message.path("button").path("text"));
        }
        if ("interactive".equals(tipo)) {
            String button = text(message.path("interactive").path("button_reply").path("title"));
            return StringUtils.hasText(button) ? button : text(message.path("interactive").path("list_reply").path("title"));
        }
        if ("image".equals(tipo)) {
            String caption = text(message.path("image").path("caption"));
            return StringUtils.hasText(caption) ? caption : "Imagen recibida por WhatsApp";
        }
        if ("document".equals(tipo)) {
            String caption = text(message.path("document").path("caption"));
            String filename = text(message.path("document").path("filename"));
            if (StringUtils.hasText(caption)) {
                return caption;
            }
            return StringUtils.hasText(filename) ? filename : "Documento recibido por WhatsApp";
        }
        return null;
    }

    private boolean esMedia(JsonNode message) {
        String tipo = text(message.path("type"));
        return "image".equals(tipo) || "document".equals(tipo);
    }

    private String extraerAccionCodigo(JsonNode message) {
        if (!"interactive".equals(text(message.path("type")))) {
            return null;
        }
        String button = text(message.path("interactive").path("button_reply").path("id"));
        return StringUtils.hasText(button) ? button : text(message.path("interactive").path("list_reply").path("id"));
    }

    private String text(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText(null) : null;
    }

    private String normalizarTelefono(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.startsWith("00")) normalized = normalized.substring(2);
        if (normalized.startsWith("34") && normalized.length() == 11) normalized = normalized.substring(2);
        return normalized;
    }
}
