package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.service.CorreoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM");
    private static final String LOGO_CID = "gestoria-cn-logo";
    private static final CorreoService.ImagenInline LOGO_INLINE = new CorreoService.ImagenInline(
            LOGO_CID,
            "static/assets/logos/casado-negrin-logo.png",
            "image/png",
            "casado-negrin-logo.png"
    );

    private final HistorialCambioRepository historialCambioRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final ClienteRepository clienteRepository;
    private final CorreoService correoService;

    @Value("${app.daily-summary.enabled:false}")
    private boolean enabled;

    @Value("${app.daily-summary.bcc-recipients:samuel.negrin@gestoriacn.com}")
    private String bccRecipients;

    @Value("${app.daily-summary.zone:Atlantic/Canary}")
    private String zone;

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

        if (!StringUtils.hasText(cliente.getEmail())) {
            return new ResultadoResumenDiario(0, totalElementos(finalizaciones, incidencias), List.of("El cliente no tiene email configurado."));
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

    private ResultadoResumenDiario enviarResumenDiario(boolean incluirClientesSinCambios) {
        RangoDia rango = rangoDia();
        List<HistorialCambio> finalizaciones = historialCambioRepository.findFinalizacionesExpedienteEntre(rango.desde(), rango.hasta());
        List<Incidencia> incidencias = incidenciaRepository.findActivasResumen();
        Map<Long, List<HistorialCambio>> finalizacionesPorCliente = finalizacionesPorCliente(finalizaciones);
        Map<Long, List<Incidencia>> incidenciasPorCliente = incidenciasPorCliente(incidencias);

        List<Cliente> clientes = clientesDestinatarios(incluirClientesSinCambios, finalizacionesPorCliente, incidenciasPorCliente);
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
        mensaje.append("\nIncidencias activas: ").append(resumen.incidencias().size()).append("\n");
        resumen.incidencias().forEach(incidencia ->
                mensaje.append(" - ").append(tituloExpediente(incidencia.getExpediente()))
                        .append(": ").append(tipoIncidencia(incidencia))
                        .append(StringUtils.hasText(incidencia.getObservaciones()) ? " - " + limpiar(incidencia.getObservaciones()) : "")
                        .append("\n"));
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
                bloqueIncidencias(resumen.incidencias()),
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

    private String bloqueIncidencias(List<Incidencia> incidencias) {
        if (incidencias.isEmpty()) {
            return bloqueVacio("Incidencias activas", "No hay tramites con incidencia activa.");
        }
        StringBuilder filas = new StringBuilder();
        for (Incidencia incidencia : incidencias) {
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
                    escapeHtml("Abierta desde " + fecha(incidencia.getFechaCreacion())),
                    StringUtils.hasText(incidencia.getObservaciones()) ? " - " + escapeHtml(limpiar(incidencia.getObservaciones())) : ""
            ));
        }
        return bloqueTabla("Incidencias activas", filas.toString());
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

    private RangoDia rangoDia() {
        ZoneId zoneId = ZoneId.of(zone);
        LocalDate hoy = LocalDate.now(zoneId);
        return new RangoDia(hoy, hoy.atStartOfDay(), LocalDateTime.now(zoneId).withNano(0));
    }

    private int totalElementos(List<HistorialCambio> finalizaciones, List<Incidencia> incidencias) {
        return expedientesFinalizados(finalizaciones).size() + incidencias.size();
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

    private String fecha(LocalDateTime value) {
        return value != null ? value.format(FECHA_CORTA) : "sin fecha";
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

    public record ResultadoResumenDiario(int clientesEnviados, int cambiosIncluidos, List<String> avisos) {
    }
}
