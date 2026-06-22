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

    @Value("${app.public-url:http://127.0.0.1:5173}")
    private String publicUrl;

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
        CorreoService.ResultadoCorreo resultado = correoService.enviarHtml(cliente.getEmail(), asunto, html, texto, copiasOcultas(cliente.getEmail()));
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
        String logoUrl = logoUrl();
        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#f4f6f8;color:#172033;font-family:Arial,Helvetica,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;">Resumen de tramites: finalizados e incidencias activas.</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f6f8;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:680px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e3e8ef;">
                          <tr>
                            <td style="background:#172033;color:#ffffff;padding:26px 30px;">
                              <div style="font-size:13px;text-transform:uppercase;letter-spacing:.08em;color:#aeb9c8;">Resumen de tramites</div>
                              <h1 style="margin:8px 0 6px;font-size:26px;line-height:1.2;">%s</h1>
                              <div style="font-size:14px;color:#d7dde6;">%s &middot; Generado a las %s</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 30px 8px;">
                              <p style="margin:0 0 18px;font-size:15px;line-height:1.6;color:#344054;">Hola%s, este es el estado resumido de tus tramites.</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td width="50%%" style="padding:0 8px 12px 0;">
                                    <div style="border:1px solid #d9eadf;background:#f0faf3;border-radius:12px;padding:16px;">
                                      <div style="font-size:12px;text-transform:uppercase;color:#2d7a46;font-weight:bold;">Finalizados hoy</div>
                                      <div style="font-size:32px;font-weight:bold;color:#12351f;margin-top:4px;">%d</div>
                                    </div>
                                  </td>
                                  <td width="50%%" style="padding:0 0 12px 8px;">
                                    <div style="border:1px solid #f2d5aa;background:#fff7eb;border-radius:12px;padding:16px;">
                                      <div style="font-size:12px;text-transform:uppercase;color:#9a5b00;font-weight:bold;">Con incidencia</div>
                                      <div style="font-size:32px;font-weight:bold;color:#573400;margin-top:4px;">%d</div>
                                    </div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          %s
                          %s
                          <tr>
                            <td style="padding:8px 30px 28px;">
                              <div style="border-top:1px solid #e7ebf0;padding-top:18px;">
                                <img src="%s" alt="Gestoria CN" style="display:block;max-width:180px;height:auto;margin-bottom:10px;">
                                <div style="font-size:13px;color:#667085;line-height:1.5;">Gestoria Casado Negrin<br>Gestion documental y tramites de vehiculos</div>
                              </div>
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
                escapeHtml(logoUrl)
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
                        <strong style="display:block;color:#172033;font-size:15px;">%s</strong>
                        <span style="color:#667085;font-size:13px;">%s</span>
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
                        <strong style="display:block;color:#172033;font-size:15px;">%s</strong>
                        <span style="display:block;color:#9a5b00;font-size:13px;font-weight:bold;">%s</span>
                        <span style="display:block;color:#667085;font-size:13px;">%s%s</span>
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
                  <td style="padding:12px 30px;">
                    <h2 style="font-size:18px;margin:0 0 6px;color:#172033;">%s</h2>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">%s</table>
                  </td>
                </tr>
                """.formatted(escapeHtml(titulo), filas);
    }

    private String bloqueVacio(String titulo, String texto) {
        return """
                <tr>
                  <td style="padding:12px 30px;">
                    <h2 style="font-size:18px;margin:0 0 10px;color:#172033;">%s</h2>
                    <div style="border:1px dashed #d6dde7;border-radius:12px;padding:14px;color:#667085;font-size:14px;background:#fafbfc;">%s</div>
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

    private String logoUrl() {
        String base = StringUtils.hasText(publicUrl) ? publicUrl.trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/assets/logos/casado-negrin-logo.png";
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
