package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.AvisoIncidencia;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.enums.PreferenciaCanalCliente;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import com.example.gestor_documental.service.CorreoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.util.MensajeAutomaticoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumenDiarioTramitesService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA_HORA_CORTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final String LOGO_CID = "gestoria-cn-logo";
    private static final CorreoService.ImagenInline LOGO_INLINE = new CorreoService.ImagenInline(
            LOGO_CID,
            "static/assets/logos/casado-negrin-logo.png",
            "image/png",
            "casado-negrin-logo.png"
    );

    private static final CorreoService.ImagenInline LOGO_SIMBOLO_INLINE = new CorreoService.ImagenInline(
            LOGO_CID,
            "static/assets/logos/casado-negrin-symbol.jpg",
            "image/jpeg",
            "casado-negrin-symbol.jpg"
    );
    private final HistorialCambioRepository historialCambioRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final ClienteRepository clienteRepository;
    private final AvisoIncidenciaRepository avisoIncidenciaRepository;
    private final CorreoService correoService;
    private final ConfiguracionSeguimientoService configuracionSeguimientoService;
    private final HistorialCambioService historialCambioService;

    @Value("${app.daily-summary.enabled:false}")
    private boolean enabled;

    @Value("${app.daily-summary.bcc-recipients:samuel.negrin@gestoriacn.com}")
    private String bccRecipients;

    @Value("${app.daily-summary.zone:Atlantic/Canary}")
    private String zone;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.public-url:}")
    private String appPublicUrl;

    @Scheduled(cron = "${app.daily-summary.cron:0 0 17 * * *}", zone = "${app.daily-summary.zone:Atlantic/Canary}")
    public void enviarResumenDiarioProgramado() {
        if (!enabled) {
            return;
        }
        enviarResumenDiario(false);
    }

    public ResultadoResumenDiario enviarResumenDiarioManual(boolean incluirClientesSinCambios) {
        return enviarResumenDiario(incluirClientesSinCambios);
    }

    public ResultadoResumenDiario enviarResumenDiarioManualCliente(Long clienteId, boolean incluirClienteSinCambios) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
        RangoDia rango = rangoDia();
        List<HistorialCambio> finalizaciones = historialCambioRepository.findFinalizacionesExpedienteClienteEntre(clienteId, rango.desde(), rango.hasta());
        List<Incidencia> incidencias = incidenciaRepository.findActivasResumenByCliente(clienteId);

        if (!permiteAvisoPorEmail(cliente)) {
            return new ResultadoResumenDiario(0, totalElementos(finalizaciones, incidencias), List.of(motivoClienteSinAvisoEmail(cliente)));
        }
        if (finalizaciones.isEmpty() && incidencias.isEmpty() && !incluirClienteSinCambios) {
            return new ResultadoResumenDiario(0, 0, List.of("El cliente no tiene tramites finalizados ni incidencias activas."));
        }

        ResumenCliente resumen = new ResumenCliente(cliente, finalizaciones, incidencias);
        CorreoService.ResultadoCorreo resultado = enviarResumen(cliente, rango, resumen);
        if (resultado.exito()) {
            return new ResultadoResumenDiario(1, totalElementos(finalizaciones, incidencias), List.of());
        }
        return new ResultadoResumenDiario(0, totalElementos(finalizaciones, incidencias), List.of("No se pudo enviar a " + cliente.getEmail() + ": " + resultado.error()));
    }

    public ResultadoResumenDiario enviarListadoIncidenciasManual(Long clienteId, Usuario admin) {
        RangoDia rango = rangoDia();
        List<Incidencia> incidencias = clienteId != null
                ? incidenciaRepository.findActivasResumenByCliente(clienteId)
                : incidenciaRepository.findActivasResumen();
        return enviarListadoIncidencias(rango, incidencias, admin, "No hay incidencias activas con cliente y email configurado.", "Listado diario de incidencias");
    }

    public ResultadoResumenDiario enviarListadoIncidenciasPendientesNotificar(Long clienteId, Usuario admin) {
        RangoDia rango = rangoDia();
        LocalDateTime limitePrimerAviso = LocalDateTime.now(zona())
                .minusDays(configuracionSeguimientoService.obtener().getDiasPrimerAviso());
        List<Incidencia> incidencias = clienteId != null
                ? incidenciaRepository.findPendientesPrimerAvisoByCliente(clienteId, limitePrimerAviso)
                : incidenciaRepository.findPendientesPrimerAviso(limitePrimerAviso);
        return enviarListadoIncidencias(rango, incidencias, admin, "No hay avisos pendientes de notificar con cliente y email configurado.", "Aviso conjunto de incidencias");
    }

    @Transactional
    public PrevisualizacionAvisoConjunto previsualizarListadoIncidenciasSeleccionadas(List<Long> incidenciaIds) {
        return crearAvisoSeleccionado(cargarYValidarSeleccion(incidenciaIds));
    }

    @Transactional
    public ResultadoResumenDiario enviarListadoIncidenciasSeleccionadas(List<Long> incidenciaIds, Usuario admin) {
        List<Incidencia> incidencias = cargarYValidarSeleccion(incidenciaIds);
        PrevisualizacionAvisoConjunto aviso = crearAvisoSeleccionado(incidencias);
        CorreoService.ResultadoCorreo resultado = correoService.enviarHtml(aviso.destinatario(), aviso.asunto(),
                aviso.htmlEnvio(), aviso.texto(), copiasOcultas(aviso.destinatario()), LOGO_SIMBOLO_INLINE);
        if (!resultado.exito()) {
            return new ResultadoResumenDiario(0, incidencias.size(), List.of("No se pudo enviar a " + aviso.destinatario() + ": " + resultado.error()));
        }
        registrarSeguimientoIncidencias(incidencias, admin, resultado.simulado(), aviso.texto(), aviso.asunto());
        return new ResultadoResumenDiario(1, incidencias.size(), List.of());
    }

    private List<Incidencia> cargarYValidarSeleccion(List<Long> incidenciaIds) {
        if (incidenciaIds == null || incidenciaIds.isEmpty()) throw new OperacionInvalidaException("Selecciona al menos una incidencia.");
        List<Long> ids = incidenciaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new OperacionInvalidaException("Selecciona al menos una incidencia.");
        List<Incidencia> incidencias = incidenciaRepository.findActivasResumenByIds(ids);
        if (incidencias.size() != ids.size()) throw new OperacionInvalidaException("Alguna incidencia seleccionada ya no esta activa o no existe.");
        ConfiguracionSeguimiento config = configuracionSeguimientoService.obtener();
        LocalDateTime ahora = LocalDateTime.now(zona());
        LocalDateTime limitePrimerAviso = ahora.minusDays(config.getDiasPrimerAviso());
        for (Incidencia incidencia : incidencias) {
            if (incidencia.getContadorAvisos() >= config.getMaxAvisos()) throw new OperacionInvalidaException("Alguna incidencia seleccionada ya alcanzo el maximo de avisos.");
            if (incidencia.getContadorAvisos() > 0 && (incidencia.getProximoAviso() == null || incidencia.getProximoAviso().isAfter(ahora))) {
                throw new OperacionInvalidaException("Alguna incidencia seleccionada aun no tiene el recordatorio vencido.");
            }
            if (incidencia.getContadorAvisos() == 0 && (incidencia.getFechaCreacion() == null || incidencia.getFechaCreacion().isAfter(limitePrimerAviso))) {
                throw new OperacionInvalidaException("Alguna incidencia seleccionada aun no ha cumplido el plazo del primer aviso.");
            }
        }
        List<Long> clientes = incidencias.stream().map(i -> i.getExpediente() == null ? null : i.getExpediente().getCliente())
                .map(c -> c == null ? null : c.getId()).distinct().toList();
        if (clientes.size() != 1 || clientes.get(0) == null) throw new OperacionInvalidaException("Todas las incidencias seleccionadas deben pertenecer al mismo cliente.");
        validarClienteAvisoEmail(incidencias.get(0).getExpediente().getCliente());
        return incidencias;
    }

    private PrevisualizacionAvisoConjunto crearAvisoSeleccionado(List<Incidencia> incidencias) {
        Cliente cliente = incidencias.get(0).getExpediente().getCliente();
        Map<Long, List<Incidencia>> grupos = incidencias.stream().collect(java.util.stream.Collectors.groupingBy(
                i -> i.getExpediente().getId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        String asunto = "Acción requerida en tus trámites (" + incidencias.size() + (incidencias.size() == 1 ? " pendiente)" : " pendientes)");
        String texto = textoAvisoSeleccionado(cliente, grupos);
        String htmlEnvio = htmlAvisoSeleccionado(cliente, grupos);
        return new PrevisualizacionAvisoConjunto(cliente.getEmail(), asunto, texto,
                htmlEnvio.replace("cid:" + LOGO_CID, "/assets/logos/casado-negrin-symbol.jpg"),
                htmlEnvio, incidencias.size(), grupos.size(), mailEnabled);
    }
    private ResultadoResumenDiario enviarListadoIncidencias(RangoDia rango, List<Incidencia> incidencias, Usuario admin,
                                                            String mensajeSinDestinatarios, String asuntoAviso) {
        Map<Long, List<Incidencia>> incidenciasPorCliente = incidenciasPorCliente(incidencias);
        List<Cliente> clientes = clientesDestinatarios(false, Map.of(), incidenciasPorCliente).stream()
                .filter(this::permiteAvisoPorEmail)
                .toList();
        if (clientes.isEmpty()) {
            return new ResultadoResumenDiario(0, incidencias.size(), List.of(mensajeSinDestinatarios));
        }

        List<String> avisos = new ArrayList<>();
        int enviados = 0;
        for (Cliente cliente : clientes) {
            List<Incidencia> incidenciasCliente = incidenciasPorCliente.getOrDefault(cliente.getId(), List.of());
            if (incidenciasCliente.isEmpty()) {
                continue;
            }
            ResumenCliente resumen = new ResumenCliente(cliente, List.of(), incidenciasCliente);
            CorreoService.ResultadoCorreo resultado = enviarResumen(cliente, rango, resumen);
            if (resultado.exito()) {
                enviados++;
                registrarSeguimientoIncidencias(incidenciasCliente, admin, resultado.simulado(), construirTexto(resumen, rango), asuntoAviso);
            } else {
                avisos.add("No se pudo enviar a " + cliente.getEmail() + ": " + resultado.error());
            }
        }
        return new ResultadoResumenDiario(enviados, incidencias.size(), avisos);
    }

    private ResultadoResumenDiario enviarResumenDiario(boolean incluirClientesSinCambios) {
        RangoDia rango = rangoDia();
        List<HistorialCambio> finalizaciones = historialCambioRepository.findFinalizacionesExpedienteEntre(rango.desde(), rango.hasta());
        List<Incidencia> incidencias = incidenciaRepository.findActivasResumen();
        Map<Long, List<HistorialCambio>> finalizacionesPorCliente = finalizacionesPorCliente(finalizaciones);
        Map<Long, List<Incidencia>> incidenciasPorCliente = incidenciasPorCliente(incidencias);

        List<Cliente> clientes = clientesDestinatarios(incluirClientesSinCambios, finalizacionesPorCliente, incidenciasPorCliente).stream()
                .filter(this::permiteAvisoPorEmail)
                .toList();
        if (clientes.isEmpty()) {
            return new ResultadoResumenDiario(0, totalElementos(finalizaciones, incidencias), List.of("No hay clientes destinatarios para el resumen diario."));
        }

        List<String> avisos = new ArrayList<>();
        int enviados = 0;
        for (Cliente cliente : clientes) {
            List<HistorialCambio> finalizacionesCliente = finalizacionesPorCliente.getOrDefault(cliente.getId(), List.of());
            List<Incidencia> incidenciasCliente = incidenciasPorCliente.getOrDefault(cliente.getId(), List.of());
            if (finalizacionesCliente.isEmpty() && incidenciasCliente.isEmpty() && !incluirClientesSinCambios) {
                continue;
            }
            CorreoService.ResultadoCorreo resultado = enviarResumen(cliente, rango, new ResumenCliente(cliente, finalizacionesCliente, incidenciasCliente));
            if (resultado.exito()) {
                enviados++;
            } else {
                avisos.add("No se pudo enviar a " + cliente.getEmail() + ": " + resultado.error());
            }
        }
        return new ResultadoResumenDiario(enviados, totalElementos(finalizaciones, incidencias), avisos);
    }

    private CorreoService.ResultadoCorreo enviarResumen(Cliente cliente, RangoDia rango, ResumenCliente resumen) {
        String asunto = "Resumen de tramites - " + rango.fecha().format(FECHA);
        String texto = construirTexto(resumen, rango);
        String html = construirHtml(resumen, rango);
        CorreoService.ResultadoCorreo resultado = correoService.enviarHtml(cliente.getEmail(), asunto, html, texto, copiasOcultas(cliente.getEmail()), LOGO_INLINE);
        if (!resultado.exito()) {
            log.warn("No se pudo enviar el resumen diario de tramites a {}: {}", cliente.getEmail(), resultado.error());
        } else if (resultado.simulado()) {
            log.info("Resumen diario de tramites simulado para {}.", cliente.getEmail());
        } else {
            log.info("Resumen diario de tramites enviado a {}.", cliente.getEmail());
        }
        return resultado;
    }

    private String textoAvisoSeleccionado(Cliente cliente, Map<Long, List<Incidencia>> grupos) {
        String salto = System.lineSeparator();
        StringBuilder texto = new StringBuilder("Hola");
        if (StringUtils.hasText(cliente.getNombre())) texto.append(" ").append(cliente.getNombre());
        texto.append(",").append(salto).append(salto)
                .append("Necesitamos tu ayuda para continuar con ")
                .append(grupos.size() == 1 ? "tu trámite." : grupos.size() + " trámites.")
                .append(salto).append(salto);
        grupos.values().forEach(grupo -> {
            Expediente e = grupo.get(0).getExpediente();
            texto.append("Expediente ").append(e.getId()).append(" - ").append(matriculaAviso(e))
                    .append(" - ").append(tipoTramite(e)).append(salto);
            grupo.forEach(i -> {
                texto.append(" - ").append(tituloIncidencia(i))
                        .append(" [").append(estadoAvisoIncidencia(i)).append("]").append(salto);
                String aclaracion = aclaracionIncidencia(i);
                if (StringUtils.hasText(aclaracion)) texto.append("   ").append(aclaracion).append(salto);
            });
            texto.append(salto);
        });
        if (StringUtils.hasText(appPublicUrl)) {
            texto.append("Revisar: ").append(appPublicUrl.replaceAll("/+$", "")).append("/tareas")
                    .append(salto).append(salto);
        }
        return texto.append("Si ya lo has enviado, puedes ignorar este aviso.").append(salto).append(salto)
                .append("Gestoria Casado Negrin").toString();
    }

    private String htmlAvisoSeleccionado(Cliente cliente, Map<Long, List<Incidencia>> grupos) {
        StringBuilder filas = new StringBuilder();
        grupos.values().forEach(grupo -> {
            Expediente e = grupo.get(0).getExpediente();
            StringBuilder pendientes = new StringBuilder();
            grupo.forEach(i -> {
                String aclaracion = aclaracionIncidencia(i);
                boolean recordatorio = i.getContadorAvisos() > 0;
                pendientes.append("""
                        <tr><td style="padding:11px 13px;border:1px solid #e4d390;background:#fffaf0;">
                          <p style="margin:0 0 6px;color:%s;font-size:11px;line-height:15px;font-weight:bold;letter-spacing:.06em;text-transform:uppercase;">%s</p>
                          <p style="margin:0;color:#172033;font-size:17px;line-height:22px;font-weight:bold;">%s</p>
                          %s
                        </td></tr>
                        <tr><td height="8" style="height:8px;line-height:8px;font-size:0;">&nbsp;</td></tr>
                        """.formatted(
                        recordatorio ? "#9a5b00" : "#1685bd",
                        escapeHtml(estadoAvisoIncidencia(i)),
                        escapeHtml(tituloIncidencia(i)),
                        StringUtils.hasText(aclaracion)
                                ? "<p style=\"margin:5px 0 0;color:#667085;font-size:13px;line-height:19px;font-weight:normal;\">" + escapeHtml(aclaracion) + "</p>"
                                : ""));
            });
            filas.append("""
                    <tr><td style="padding:22px 0;border-bottom:1px solid #dfe5ec;">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
                        <td valign="top" width="38%%" style="padding-right:22px;">
                          <p style="margin:0 0 7px;color:#1685bd;font-size:15px;font-weight:bold;">Expediente %d</p>
                          <p style="margin:0 0 4px;color:#667085;font-size:13px;">Matricula</p>
                          <p style="margin:0 0 5px;color:#172033;font-size:25px;font-weight:bold;">%s</p>
                          <p style="margin:0;color:#475467;font-size:14px;">%s</p>
                        </td>
                        <td valign="top" style="padding-left:22px;border-left:1px solid #dfe5ec;">
                          <p style="margin:0 0 9px;color:#172033;font-size:14px;font-weight:bold;">Documentación o acciones pendientes</p>
                          <table role="presentation" width="100%%">%s</table>
                        </td>
                      </tr></table>
                    </td></tr>
                    """.formatted(e.getId(), escapeHtml(matriculaAviso(e)), escapeHtml(tipoTramite(e)), pendientes));
        });
        String url = StringUtils.hasText(appPublicUrl) ? appPublicUrl.replaceAll("/+$", "") + "/tareas" : "";
        String boton = StringUtils.hasText(url) ? """
                <tr><td align="center" style="padding:26px 0 10px;">
                  <a href="%s" style="display:inline-block;background:#f8c91c;color:#172033;text-decoration:none;font-size:16px;font-weight:bold;padding:15px 28px;border-radius:5px;">Revisar y aportar documentación</a>
                </td></tr>
                """.formatted(escapeHtml(url)) : "";
        int total = grupos.values().stream().mapToInt(List::size).sum();
        String fecha = LocalDate.now(zona()).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new java.util.Locale("es", "ES")));
        return """
                <!doctype html><html lang="es">
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" bgcolor="#f4f6f8"><tr><td align="center" style="padding:24px 10px;">
                  <table role="presentation" width="640" cellspacing="0" cellpadding="0" bgcolor="#ffffff" style="width:640px;max-width:100%%;background:#fff;border:1px solid #d9e0ea;">
                    <tr><td bgcolor="#172033" style="padding:20px 28px;"><table role="presentation" cellspacing="0" cellpadding="0"><tr><td><img src="cid:%s" width="52" alt="" style="display:block;width:52px;height:52px;border:0;"></td><td style="padding-left:14px;color:#ffffff;font-size:20px;line-height:24px;font-weight:bold;">Gestoría<br>Casado Negrín</td></tr></table></td></tr>
                    <tr><td style="padding:30px 28px 8px;"><h1 style="margin:0 0 8px;color:#172033;font-size:27px;line-height:34px;">Acción requerida en tus trámites</h1><p style="margin:0;color:#1685bd;font-size:14px;font-weight:bold;">%s</p></td></tr>
                    <tr><td style="padding:12px 28px 4px;"><p style="margin:0 0 10px;color:#172033;font-size:17px;font-weight:bold;">Hola%s,</p><p style="margin:0;color:#475467;font-size:15px;line-height:23px;">Necesitamos tu ayuda para continuar con %s.</p></td></tr>
                    <tr><td style="padding:18px 28px 0;"><table role="presentation" width="100%%" bgcolor="#eef7fc" style="background:#eef7fc;border:1px solid #cce4f1;"><tr><td align="center" style="padding:13px;color:#1685bd;font-size:17px;font-weight:bold;">%s &middot; %s</td></tr></table></td></tr>
                    <tr><td style="padding:4px 28px 0;"><table role="presentation" width="100%%">%s</table>%s</td></tr>
                    <tr><td style="padding:8px 28px 24px;color:#475467;font-size:14px;">Si ya lo has enviado, puedes ignorar este aviso.</td></tr>
                    <tr><td style="padding:20px 28px 24px;border-top:2px solid #1685bd;color:#667085;font-size:13px;"><strong style="color:#172033;">Gestoria Casado Negrin</strong><br>Gestión documental y trámites de vehículos. Estamos aquí para ayudarte.</td></tr>
                  </table>
                </td></tr></table></body></html>
                """.formatted(LOGO_CID, fecha, saludoNombre(cliente),
                grupos.size() == 1 ? "tu trámite" : grupos.size() + " trámites",
                total + (total == 1 ? " incidencia" : " incidencias"), grupos.size() + (grupos.size() == 1 ? " expediente" : " expedientes"), filas, boton);
    }

    private String matriculaAviso(Expediente e) {
        return StringUtils.hasText(e.getMatricula()) ? e.getMatricula() : "Sin matricula";
    }

    private String tituloIncidencia(Incidencia incidencia) {
        if (incidencia == null || incidencia.getTipoIncidencia() == null || incidencia.getTipoIncidencia().getNombre() == null) {
            return "Incidencia";
        }
        return switch (incidencia.getTipoIncidencia().getNombre()) {
            case RODAJE -> "Rodaje";
            case RESERVA -> "Reserva de dominio";
            case EMBARGO -> "Embargo";
            case NOTIFICADO -> "Vehículo notificado";
            case DENEGATORIA -> "Denegatoria";
            case RECHAZADO_DGT -> "Rechazado por la DGT";
            case PENDIENTE_DOCUMENTACION -> "Pendiente de documentación";
            case SOLICITADA_INFORMACION_ADICIONAL -> "Información adicional solicitada";
        };
    }

    private String aclaracionIncidencia(Incidencia incidencia) {
        if (incidencia == null) return "";
        String observacion = observacionIncidenciaVisible(incidencia);
        if (StringUtils.hasText(observacion)) return limpiar(observacion);
        return aclaracionPredeterminadaIncidencia(incidencia);
    }

    private String aclaracionPredeterminadaIncidencia(Incidencia incidencia) {
        if (incidencia.getTipoIncidencia() == null || incidencia.getTipoIncidencia().getNombre() == null) return "";
        return switch (incidencia.getTipoIncidencia().getNombre()) {
            case RODAJE -> "Falta el pago del impuesto de rodaje o de otro impuesto local.";
            case RESERVA -> "El vehículo tiene una reserva de dominio que impide la transmisión.";
            case EMBARGO -> "El vehículo tiene un embargo. Es necesario aceptación de embargo o hacer el levantamiento.";
            case NOTIFICADO -> "El vehículo consta como notificado en tráfico, es necesario finalizar la transmisión previa para poder realizar el traspaso";
            case DENEGATORIA -> "El vehículo tiene anotada una denegatoria en la DGT.";
            case RECHAZADO_DGT -> "La DGT ha rechazado el trámite presentado";
            case PENDIENTE_DOCUMENTACION -> "Falta documentación necesaria para continuar el trámite.";
            case SOLICITADA_INFORMACION_ADICIONAL -> "Necesitamos una respuesta o aclaración adicional para continuar.";
        };
    }

    private String observacionIncidenciaVisible(Incidencia incidencia) {
        if (incidencia == null || MensajeAutomaticoUtils.esObservacionTecnicaIncidencia(incidencia.getObservaciones())) {
            return null;
        }
        return normalizarTextoCliente(incidencia.getObservaciones());
    }

    private String normalizarTextoCliente(String valor) {
        String texto = limpiar(valor);
        boolean tieneMayusculas = texto.chars().anyMatch(Character::isUpperCase);
        boolean tieneMinusculas = texto.chars().anyMatch(Character::isLowerCase);
        if (!tieneMayusculas || tieneMinusculas) return texto;

        String minusculas = texto.toLowerCase(new java.util.Locale("es", "ES"));
        StringBuilder resultado = new StringBuilder(minusculas.length());
        boolean capitalizar = true;
        for (int i = 0; i < minusculas.length(); i++) {
            char caracter = minusculas.charAt(i);
            if (capitalizar && Character.isLetter(caracter)) {
                resultado.append(Character.toUpperCase(caracter));
                capitalizar = false;
            } else {
                resultado.append(caracter);
            }
            if (caracter == '.' || caracter == '!' || caracter == '?') capitalizar = true;
        }
        String normalizado = resultado.toString();
        for (String sigla : List.of("DGT", "DNI", "NIE", "ITV", "PDF", "IVA", "ITP", "BATECOM")) {
            normalizado = normalizado.replaceAll("(?i)\\b" + sigla + "\\b", sigla);
        }
        return normalizado;
    }

    private String estadoAvisoIncidencia(Incidencia incidencia) {
        String fecha = incidencia != null && incidencia.getFechaCreacion() != null
                ? incidencia.getFechaCreacion().toLocalDate().format(
                        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new java.util.Locale("es", "ES")))
                : "fecha no disponible";
        return incidencia != null && incidencia.getContadorAvisos() > 0
                ? "Recordatorio · Pendiente desde " + fecha
                : "Nueva incidencia · Detectada el " + fecha;
    }
    private String construirTexto(ResumenCliente resumen, RangoDia rango) {
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Hola");
        if (StringUtils.hasText(resumen.cliente().getNombre())) {
            mensaje.append(" ").append(resumen.cliente().getNombre());
        }
        mensaje.append(",\n\n");
        mensaje.append("Resumen de tramites - ").append(rango.fecha().format(FECHA)).append("\n\n");
        mensaje.append("Finalizados hoy: ").append(expedientesFinalizados(resumen.finalizaciones()).size()).append("\n");
        expedientesFinalizados(resumen.finalizaciones()).forEach(expediente ->
                mensaje.append(" - ").append(tituloExpediente(expediente)).append("\n"));
        appendIncidenciasTexto(mensaje, resumen.incidencias(), rango);
        if (resumen.finalizaciones().isEmpty() && resumen.incidencias().isEmpty()) {
            mensaje.append("\nNo hay tramites finalizados ni incidencias activas en este momento.\n");
        }
        mensaje.append("\nGestoria CN");
        return mensaje.toString().trim();
    }

    private String construirHtml(ResumenCliente resumen, RangoDia rango) {
        List<Expediente> finalizados = expedientesFinalizados(resumen.finalizaciones());
        return """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:0;background-color:#f4f6f8;color:#172033;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#f4f6f8" style="background-color:#f4f6f8;">
                    <tr>
                      <td align="center" style="padding:24px 10px;">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" border="0" bgcolor="#ffffff" style="width:640px;background-color:#ffffff;border:1px solid #d9e0ea;">
                          <tr>
                            <td bgcolor="#172033" style="background-color:#172033;color:#ffffff;padding:24px 28px;">
                              <p style="margin:0 0 8px 0;font-size:12px;line-height:16px;text-transform:uppercase;color:#c4ccd8;font-weight:bold;">Resumen de tramites</p>
                              <h1 style="margin:0 0 8px 0;font-size:25px;line-height:31px;font-weight:bold;color:#ffffff;">%s</h1>
                              <p style="margin:0;font-size:14px;line-height:20px;color:#d7dde6;">%s &middot; Generado a las %s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 28px 10px 28px;">
                              <p style="margin:0 0 18px 0;font-size:15px;line-height:23px;color:#344054;">Hola%s, este es el estado resumido de tus tramites.</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                <tr>
                                  <td width="50%%" valign="top" style="padding:0 7px 12px 0;">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#f0faf3" style="background-color:#f0faf3;border:1px solid #d9eadf;">
                                      <tr>
                                        <td style="padding:15px;">
                                          <p style="margin:0 0 6px 0;font-size:12px;line-height:16px;color:#2d7a46;font-weight:bold;text-transform:uppercase;">Finalizados hoy</p>
                                          <p style="margin:0;font-size:32px;line-height:36px;color:#12351f;font-weight:bold;">%d</p>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                  <td width="50%%" valign="top" style="padding:0 0 12px 7px;">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#fff7eb" style="background-color:#fff7eb;border:1px solid #f2d5aa;">
                                      <tr>
                                        <td style="padding:15px;">
                                          <p style="margin:0 0 6px 0;font-size:12px;line-height:16px;color:#9a5b00;font-weight:bold;text-transform:uppercase;">Con incidencia</p>
                                          <p style="margin:0;font-size:32px;line-height:36px;color:#573400;font-weight:bold;">%d</p>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          %s
                          %s
                          <tr>
                            <td style="padding:10px 28px 26px 28px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="border-top:1px solid #e7ebf0;">
                                <tr>
                                  <td style="padding-top:18px;">
                                    <img src="cid:%s" width="180" alt="Gestoria CN" style="display:block;width:180px;height:auto;border:0;outline:none;text-decoration:none;margin:0 0 10px 0;">
                                    <p style="margin:0;font-size:13px;line-height:19px;color:#667085;">Gestoria Casado Negrin<br>Gestion documental y tramites de vehiculos</p>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(resumen.cliente().getNombre()),
                rango.fecha().format(FECHA),
                rango.hasta().format(HORA),
                saludoNombre(resumen.cliente()),
                finalizados.size(),
                resumen.incidencias().size(),
                bloqueFinalizados(finalizados),
                bloquesIncidencias(resumen.incidencias(), rango),
                LOGO_CID
        );
    }

    private String bloqueFinalizados(List<Expediente> finalizados) {
        if (finalizados.isEmpty()) {
            return bloqueVacio("Tramites finalizados", "No se han finalizado tramites hoy.");
        }
        StringBuilder filas = new StringBuilder();
        for (Expediente expediente : finalizados) {
            filas.append("""
                    <tr>
                      <td style="padding:12px 0;border-bottom:1px solid #edf1f5;">
                        <p style="margin:0 0 3px 0;color:#172033;font-size:15px;line-height:21px;font-weight:bold;">%s</p>
                        <p style="margin:0;color:#667085;font-size:13px;line-height:19px;">%s</p>
                      </td>
                    </tr>
                    """.formatted(escapeHtml(tituloExpediente(expediente)), escapeHtml(tipoTramite(expediente))));
        }
        return bloqueTabla("Tramites finalizados", filas.toString());
    }

    private void appendIncidenciasTexto(StringBuilder mensaje, List<Incidencia> incidencias, RangoDia rango) {
        mensaje.append("\nIncidencias activas: ").append(incidencias.size()).append("\n");
        if (incidencias.isEmpty()) {
            return;
        }
        List<Incidencia> nuevasHoy = incidenciasNuevasHoy(incidencias, rango);
        List<Incidencia> pendientesPrimerAviso = incidencias.stream()
                .filter(incidencia -> incidencia.getContadorAvisos() == 0)
                .filter(incidencia -> !nuevasHoy.contains(incidencia))
                .toList();
        List<Incidencia> recordatorios = incidencias.stream()
                .filter(incidencia -> incidencia.getContadorAvisos() > 0)
                .toList();
        appendGrupoIncidenciasTexto(mensaje, "Incidencias nuevas obtenidas hoy", nuevasHoy, rango);
        appendGrupoIncidenciasTexto(mensaje, "Incidencias pendientes de primer aviso", pendientesPrimerAviso, rango);
        appendGrupoIncidenciasTexto(mensaje, "Recordatorios pendientes", recordatorios, rango);
    }

    private void appendGrupoIncidenciasTexto(StringBuilder mensaje, String titulo, List<Incidencia> incidencias, RangoDia rango) {
        if (incidencias.isEmpty()) {
            return;
        }
        mensaje.append("\n").append(titulo).append(": ").append(incidencias.size()).append("\n");
        incidencias.forEach(incidencia -> {
            String observacion = observacionIncidenciaVisible(incidencia);
            mensaje.append(" - ").append(tituloExpediente(incidencia.getExpediente()))
                    .append(": ").append(tipoIncidencia(incidencia))
                    .append(" (").append(detalleSeguimiento(incidencia, rango)).append(")")
                    .append(StringUtils.hasText(observacion) ? " - " + limpiar(observacion) : "")
                    .append("\n");
        });
    }

    private String bloquesIncidencias(List<Incidencia> incidencias, RangoDia rango) {
        if (incidencias.isEmpty()) {
            return bloqueVacio("Incidencias activas", "No hay tramites con incidencia activa.");
        }
        StringBuilder bloques = new StringBuilder();
        List<Incidencia> nuevasHoy = incidenciasNuevasHoy(incidencias, rango);
        List<Incidencia> pendientesPrimerAviso = incidencias.stream()
                .filter(incidencia -> incidencia.getContadorAvisos() == 0)
                .filter(incidencia -> !nuevasHoy.contains(incidencia))
                .toList();
        List<Incidencia> recordatorios = incidencias.stream()
                .filter(incidencia -> incidencia.getContadorAvisos() > 0)
                .toList();
        if (!nuevasHoy.isEmpty()) {
            bloques.append(bloqueIncidencias("Incidencias nuevas obtenidas hoy", nuevasHoy, rango));
        }
        if (!pendientesPrimerAviso.isEmpty()) {
            bloques.append(bloqueIncidencias("Incidencias pendientes de primer aviso", pendientesPrimerAviso, rango));
        }
        if (!recordatorios.isEmpty()) {
            bloques.append(bloqueIncidencias("Recordatorios pendientes", recordatorios, rango));
        }
        return bloques.toString();
    }

    private String bloqueIncidencias(String titulo, List<Incidencia> incidencias, RangoDia rango) {
        StringBuilder filas = new StringBuilder();
        for (Incidencia incidencia : incidencias) {
            String observacion = observacionIncidenciaVisible(incidencia);
            filas.append("""
                    <tr>
                      <td style="padding:12px 0;border-bottom:1px solid #edf1f5;">
                        <p style="margin:0 0 3px 0;color:#172033;font-size:15px;line-height:21px;font-weight:bold;">%s</p>
                        <p style="margin:0 0 3px 0;color:#9a5b00;font-size:13px;line-height:19px;font-weight:bold;">%s</p>
                        <p style="margin:0;color:#667085;font-size:13px;line-height:19px;">%s%s</p>
                      </td>
                    </tr>
                    """.formatted(
                    escapeHtml(tituloExpediente(incidencia.getExpediente())),
                    escapeHtml(tipoIncidencia(incidencia)),
                    escapeHtml(detalleSeguimiento(incidencia, rango)),
                    StringUtils.hasText(observacion) ? " - " + escapeHtml(limpiar(observacion)) : ""
            ));
        }
        return bloqueTabla(titulo, filas.toString());
    }

    private List<Incidencia> incidenciasNuevasHoy(List<Incidencia> incidencias, RangoDia rango) {
        return incidencias.stream()
                .filter(incidencia -> incidencia.getContadorAvisos() == 0)
                .filter(incidencia -> estaEnRango(incidencia.getFechaCreacion(), rango))
                .toList();
    }

    private boolean estaEnRango(LocalDateTime fecha, RangoDia rango) {
        return fecha != null && !fecha.isBefore(rango.desde()) && !fecha.isAfter(rango.hasta());
    }

    private String detalleSeguimiento(Incidencia incidencia, RangoDia rango) {
        if (incidencia.getContadorAvisos() > 0) {
            String ultimoAviso = incidencia.getFechaUltimoAviso() != null
                    ? "ultimo aviso " + fechaHora(incidencia.getFechaUltimoAviso()) + ", " + tiempoDesde(incidencia.getFechaUltimoAviso(), rango.hasta())
                    : "ultimo aviso sin fecha registrada";
            return "Recordatorio: " + ultimoAviso + ". Avisos enviados: " + incidencia.getContadorAvisos();
        }
        if (estaEnRango(incidencia.getFechaCreacion(), rango)) {
            return "Nueva incidencia obtenida hoy a las " + (incidencia.getFechaCreacion() != null ? incidencia.getFechaCreacion().format(HORA) : "hora no registrada");
        }
        return "Pendiente de primer aviso desde " + fechaHora(incidencia.getFechaCreacion());
    }

    private String tiempoDesde(LocalDateTime fecha, LocalDateTime referencia) {
        if (fecha == null || referencia == null) {
            return "sin referencia temporal";
        }
        if (fecha.isAfter(referencia)) {
            return "programado para mas adelante";
        }
        long dias = ChronoUnit.DAYS.between(fecha, referencia);
        if (dias > 0) {
            return "hace " + dias + " " + (dias == 1 ? "dia" : "dias");
        }
        long horas = ChronoUnit.HOURS.between(fecha, referencia);
        if (horas > 0) {
            return "hace " + horas + " " + (horas == 1 ? "hora" : "horas");
        }
        long minutos = ChronoUnit.MINUTES.between(fecha, referencia);
        if (minutos > 0) {
            return "hace " + minutos + " " + (minutos == 1 ? "minuto" : "minutos");
        }
        return "hace menos de 1 minuto";
    }

    private String bloqueTabla(String titulo, String filas) {
        return """
                <tr>
                  <td style="padding:12px 28px;">
                    <h2 style="font-size:18px;line-height:24px;margin:0 0 6px 0;color:#172033;">%s</h2>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">%s</table>
                  </td>
                </tr>
                """.formatted(escapeHtml(titulo), filas);
    }

    private String bloqueVacio(String titulo, String texto) {
        return """
                <tr>
                  <td style="padding:12px 28px;">
                    <h2 style="font-size:18px;line-height:24px;margin:0 0 10px 0;color:#172033;">%s</h2>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#fafbfc" style="background-color:#fafbfc;border:1px solid #d6dde7;">
                      <tr>
                        <td style="padding:14px;color:#667085;font-size:14px;line-height:20px;">%s</td>
                      </tr>
                    </table>
                  </td>
                </tr>
                """.formatted(escapeHtml(titulo), escapeHtml(texto));
    }

    private List<Expediente> expedientesFinalizados(List<HistorialCambio> finalizaciones) {
        Map<Long, Expediente> expedientes = new LinkedHashMap<>();
        finalizaciones.stream()
                .filter(cambio -> cambio.getExpediente() != null && cambio.getExpediente().getId() != null)
                .forEach(cambio -> expedientes.putIfAbsent(cambio.getExpediente().getId(), cambio.getExpediente()));
        return List.copyOf(expedientes.values());
    }

    private Map<Long, List<HistorialCambio>> finalizacionesPorCliente(List<HistorialCambio> finalizaciones) {
        Map<Long, List<HistorialCambio>> resultado = new LinkedHashMap<>();
        finalizaciones.stream()
                .filter(cambio -> cambio.getExpediente() != null
                        && cambio.getExpediente().getCliente() != null
                        && cambio.getExpediente().getCliente().getId() != null)
                .forEach(cambio -> resultado
                        .computeIfAbsent(cambio.getExpediente().getCliente().getId(), ignored -> new ArrayList<>())
                        .add(cambio));
        return resultado;
    }

    private Map<Long, List<Incidencia>> incidenciasPorCliente(List<Incidencia> incidencias) {
        Map<Long, List<Incidencia>> resultado = new LinkedHashMap<>();
        incidencias.stream()
                .filter(incidencia -> incidencia.getExpediente() != null
                        && incidencia.getExpediente().getCliente() != null
                        && incidencia.getExpediente().getCliente().getId() != null)
                .forEach(incidencia -> resultado
                        .computeIfAbsent(incidencia.getExpediente().getCliente().getId(), ignored -> new ArrayList<>())
                        .add(incidencia));
        return resultado;
    }

    private List<Cliente> clientesDestinatarios(boolean incluirClientesSinCambios,
                                                Map<Long, List<HistorialCambio>> finalizacionesPorCliente,
                                                Map<Long, List<Incidencia>> incidenciasPorCliente) {
        if (incluirClientesSinCambios) {
            return clienteRepository.findAll().stream().filter(cliente -> StringUtils.hasText(cliente.getEmail())).toList();
        }
        LinkedHashSet<Cliente> clientes = new LinkedHashSet<>();
        finalizacionesPorCliente.values().stream()
                .filter(items -> !items.isEmpty())
                .map(items -> items.get(0).getExpediente().getCliente())
                .filter(cliente -> cliente != null && StringUtils.hasText(cliente.getEmail()))
                .forEach(clientes::add);
        incidenciasPorCliente.values().stream()
                .filter(items -> !items.isEmpty())
                .map(items -> items.get(0).getExpediente().getCliente())
                .filter(cliente -> cliente != null && StringUtils.hasText(cliente.getEmail()))
                .forEach(clientes::add);
        return List.copyOf(clientes);
    }

    private void validarClienteAvisoEmail(Cliente cliente) {
        if (cliente == null) {
            throw new OperacionInvalidaException("Las incidencias seleccionadas no tienen cliente asociado.");
        }
        if (!StringUtils.hasText(cliente.getEmail())) {
            throw new OperacionInvalidaException("El cliente no tiene un correo configurado.");
        }
        PreferenciaCanalCliente preferencia = cliente.getPreferenciaCanal() != null
                ? cliente.getPreferenciaCanal()
                : PreferenciaCanalCliente.AMBOS;
        if (preferencia == PreferenciaCanalCliente.SIN_AVISOS) {
            throw new OperacionInvalidaException("El cliente tiene desactivados los avisos.");
        }
        if (preferencia == PreferenciaCanalCliente.WHATSAPP) {
            throw new OperacionInvalidaException("El cliente solo admite avisos por WhatsApp.");
        }
    }

    private boolean permiteAvisoPorEmail(Cliente cliente) {
        if (cliente == null || !StringUtils.hasText(cliente.getEmail())) {
            return false;
        }
        PreferenciaCanalCliente preferencia = cliente.getPreferenciaCanal() != null
                ? cliente.getPreferenciaCanal()
                : PreferenciaCanalCliente.AMBOS;
        return preferencia == PreferenciaCanalCliente.EMAIL || preferencia == PreferenciaCanalCliente.AMBOS;
    }
    private String motivoClienteSinAvisoEmail(Cliente cliente) {
        if (cliente == null || !StringUtils.hasText(cliente.getEmail())) {
            return "El cliente no tiene email configurado.";
        }
        PreferenciaCanalCliente preferencia = cliente.getPreferenciaCanal() != null
                ? cliente.getPreferenciaCanal()
                : PreferenciaCanalCliente.AMBOS;
        if (preferencia == PreferenciaCanalCliente.SIN_AVISOS) {
            return "El cliente tiene desactivados los avisos.";
        }
        if (preferencia == PreferenciaCanalCliente.WHATSAPP) {
            return "El cliente solo admite avisos por WhatsApp.";
        }
        return "El cliente no admite avisos por correo.";
    }

    private ZoneId zona() {
        return StringUtils.hasText(zone) ? ZoneId.of(zone) : ZoneId.systemDefault();
    }

    private RangoDia rangoDia() {
        ZoneId zoneId = zona();
        LocalDate hoy = LocalDate.now(zoneId);
        return new RangoDia(hoy, hoy.atStartOfDay(), LocalDateTime.now(zoneId).withNano(0));
    }

    private int totalElementos(List<HistorialCambio> finalizaciones, List<Incidencia> incidencias) {
        return expedientesFinalizados(finalizaciones).size() + incidencias.size();
    }

    private void registrarSeguimientoIncidencias(List<Incidencia> incidencias, Usuario admin, boolean simulado, String texto, String asuntoAviso) {
        ConfiguracionSeguimiento config = configuracionSeguimientoService.obtener();
        LocalDateTime ahora = LocalDateTime.now();
        for (Incidencia incidencia : incidencias) {
            if (incidencia.getExpediente() == null || incidencia.getContadorAvisos() >= config.getMaxAvisos()) {
                continue;
            }
            int numero = incidencia.getContadorAvisos() + 1;
            AvisoIncidencia aviso = new AvisoIncidencia();
            aviso.setIncidencia(incidencia);
            aviso.setNumeroAviso(numero);
            aviso.setEnviadoPor(admin);
            aviso.setMensaje(texto);
            aviso.setDestinatario(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getEmail() : null);
            aviso.setAsunto(asuntoAviso);
            aviso.setCanal("EMAIL_RESUMEN");
            aviso.setEstadoEnvio(simulado ? "SIMULADO" : "ENVIADO");
            avisoIncidenciaRepository.save(aviso);

            incidencia.setContadorAvisos(numero);
            incidencia.setFechaUltimoAviso(ahora);
            incidencia.setProximoAviso(siguienteVencimiento(ahora, numero, config));
            incidencia.setSeguimientoArchivado(false);
            incidencia.setFechaArchivoSeguimiento(null);
            incidencia.setSeguimientoArchivadoPor(null);
            incidenciaRepository.save(incidencia);
            historialCambioService.registrarCambioExpediente(incidencia.getExpediente(), admin, "LISTADO INCIDENCIAS",
                    "Incidencia incluida en el listado diario enviado al cliente" + (simulado ? " (simulado)." : "."));
        }
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

    private List<String> copiasOcultas(String destinatario) {
        String principal = destinatario != null ? destinatario.trim() : "";
        return parseCorreos(bccRecipients).stream()
                .filter(correo -> !correo.equalsIgnoreCase(principal))
                .toList();
    }

    private List<String> parseCorreos(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("[,;]")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String tituloExpediente(Expediente expediente) {
        if (expediente == null) {
            return "Expediente sin referencia";
        }
        StringBuilder titulo = new StringBuilder("EXP-").append(expediente.getId());
        if (StringUtils.hasText(expediente.getMatricula())) {
            titulo.append(" - ").append(expediente.getMatricula());
        }
        return titulo.toString();
    }

    private String tipoTramite(Expediente expediente) {
        if (expediente == null || expediente.getTipoTramite() == null) {
            return "Tramite";
        }
        return StringUtils.hasText(expediente.getTipoTramite().getDescripcion())
                ? expediente.getTipoTramite().getDescripcion()
                : expediente.getTipoTramite().getNombre().name();
    }

    private String tipoIncidencia(Incidencia incidencia) {
        if (incidencia == null || incidencia.getTipoIncidencia() == null) {
            return "Incidencia";
        }
        return StringUtils.hasText(incidencia.getTipoIncidencia().getDescripcion())
                ? incidencia.getTipoIncidencia().getDescripcion()
                : incidencia.getTipoIncidencia().getNombre().name();
    }

    private String saludoNombre(Cliente cliente) {
        return StringUtils.hasText(cliente.getNombre()) ? " " + escapeHtml(cliente.getNombre()) : "";
    }

    private String fechaHora(LocalDateTime value) {
        return value != null ? value.format(FECHA_HORA_CORTA) : "sin fecha";
    }

    private String limpiar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "Sin descripcion";
        }
        return valor.replaceAll("\\s+", " ").trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record RangoDia(LocalDate fecha, LocalDateTime desde, LocalDateTime hasta) {
    }

    private record ResumenCliente(Cliente cliente, List<HistorialCambio> finalizaciones, List<Incidencia> incidencias) {
    }

    public record PrevisualizacionAvisoConjunto(String destinatario, String asunto, String texto, String html,
                                                String htmlEnvio, int incidencias, int expedientes, boolean envioReal) {
    }

    public record ResultadoResumenDiario(int clientesEnviados, int cambiosIncluidos, List<String> avisos) {
    }
}
