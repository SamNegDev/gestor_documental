package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.service.CorreoService;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumenDiarioTramitesService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final HistorialCambioRepository historialCambioRepository;
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
        ZoneId zoneId = ZoneId.of(zone);
        LocalDate hoy = LocalDate.now(zoneId);
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = LocalDateTime.now(zoneId).withNano(0);
        List<HistorialCambio> cambios = historialCambioRepository.findCambiosExpedienteClienteEntre(clienteId, desde, hasta);

        if (!StringUtils.hasText(cliente.getEmail())) {
            return new ResultadoResumenDiario(0, cambios.size(), List.of("El cliente no tiene email configurado."));
        }
        if (cambios.isEmpty() && !incluirClienteSinCambios) {
            return new ResultadoResumenDiario(0, 0, List.of("El cliente no tiene cambios registrados hoy."));
        }

        String asunto = "Resumen diario de tramites - " + hoy.format(FECHA);
        String mensaje = construirMensaje(cliente, hoy, desde, hasta, cambios);
        CorreoService.ResultadoCorreo resultado = enviar(cliente.getEmail(), asunto, mensaje, copiasOcultas(cliente.getEmail()));
        if (resultado.exito()) {
            return new ResultadoResumenDiario(1, cambios.size(), List.of());
        }
        return new ResultadoResumenDiario(0, cambios.size(), List.of("No se pudo enviar a " + cliente.getEmail() + ": " + resultado.error()));
    }

    private ResultadoResumenDiario enviarResumenDiario(boolean incluirClientesSinCambios) {
        ZoneId zoneId = ZoneId.of(zone);
        LocalDate hoy = LocalDate.now(zoneId);
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = LocalDateTime.now(zoneId).withNano(0);
        List<HistorialCambio> cambios = historialCambioRepository.findCambiosExpedienteEntre(desde, hasta);
        Map<Long, List<HistorialCambio>> cambiosPorCliente = cambiosPorCliente(cambios);

        List<Cliente> clientes = incluirClientesSinCambios
                ? clienteRepository.findAll().stream().filter(cliente -> StringUtils.hasText(cliente.getEmail())).toList()
                : cambiosPorCliente.values().stream()
                        .map(items -> items.get(0).getExpediente().getCliente())
                        .filter(cliente -> cliente != null && StringUtils.hasText(cliente.getEmail()))
                        .toList();
        if (clientes.isEmpty()) {
            return new ResultadoResumenDiario(0, cambios.size(), List.of("No hay clientes destinatarios para el resumen diario."));
        }

        String asunto = "Resumen diario de tramites - " + hoy.format(FECHA);
        List<String> avisos = new ArrayList<>();
        int enviados = 0;
        for (Cliente cliente : clientes) {
            List<HistorialCambio> cambiosCliente = cambiosPorCliente.getOrDefault(cliente.getId(), List.of());
            if (cambiosCliente.isEmpty() && !incluirClientesSinCambios) {
                continue;
            }
            String mensaje = construirMensaje(cliente, hoy, desde, hasta, cambiosCliente);
            CorreoService.ResultadoCorreo resultado = enviar(cliente.getEmail(), asunto, mensaje, copiasOcultas(cliente.getEmail()));
            if (resultado.exito()) {
                enviados++;
            } else {
                avisos.add("No se pudo enviar a " + cliente.getEmail() + ": " + resultado.error());
            }
        }
        return new ResultadoResumenDiario(enviados, cambios.size(), avisos);
    }

    private CorreoService.ResultadoCorreo enviar(String destinatario, String asunto, String mensaje, List<String> copiaOculta) {
        CorreoService.ResultadoCorreo resultado = correoService.enviar(destinatario, asunto, mensaje, copiaOculta);
        if (!resultado.exito()) {
            log.warn("No se pudo enviar el resumen diario de tramites a {}: {}", destinatario, resultado.error());
        } else if (resultado.simulado()) {
            log.info("Resumen diario de tramites simulado para {}.", destinatario);
        } else {
            log.info("Resumen diario de tramites enviado a {}.", destinatario);
        }
        return resultado;
    }

    private String construirMensaje(Cliente cliente, LocalDate fecha, LocalDateTime desde, LocalDateTime hasta, List<HistorialCambio> cambios) {
        Map<Long, List<HistorialCambio>> porExpediente = new LinkedHashMap<>();
        cambios.forEach(cambio -> porExpediente
                .computeIfAbsent(cambio.getExpediente().getId(), ignored -> new ArrayList<>())
                .add(cambio));

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Hola");
        if (StringUtils.hasText(cliente.getNombre())) {
            mensaje.append(" ").append(cliente.getNombre());
        }
        mensaje.append(",\n\n");
        mensaje.append("Resumen diario de tramites\n");
        mensaje.append("Fecha: ").append(fecha.format(FECHA)).append("\n");
        mensaje.append("Periodo: ").append(desde.format(HORA)).append(" - ").append(hasta.format(HORA)).append("\n\n");
        mensaje.append("Expedientes actualizados: ").append(porExpediente.size()).append("\n");
        mensaje.append("Cambios registrados: ").append(cambios.size()).append("\n\n");

        if (cambios.isEmpty()) {
            mensaje.append("Hoy no se han registrado cambios en tus tramites.\n\n");
            mensaje.append("Gestoria CN");
            return mensaje.toString().trim();
        }

        porExpediente.forEach((expedienteId, items) -> {
            Expediente expediente = items.get(0).getExpediente();
            mensaje.append("EXP-").append(expedienteId);
            if (StringUtils.hasText(expediente.getMatricula())) {
                mensaje.append(" - ").append(expediente.getMatricula());
            }
            if (expediente.getTipoTramite() != null && expediente.getTipoTramite().getDescripcion() != null) {
                mensaje.append(" - ").append(expediente.getTipoTramite().getDescripcion());
            }
            mensaje.append("\n");
            mensaje.append("Estado actual: ")
                    .append(expediente.getEstadoExpediente() != null ? humanizar(expediente.getEstadoExpediente().name()) : "Sin estado")
                    .append("\n");
            items.forEach(cambio -> mensaje
                    .append(" - ")
                    .append(cambio.getFechaCambio() != null ? cambio.getFechaCambio().format(HORA) : "--:--")
                    .append(" - ")
                    .append(cambio.getAccion())
                    .append(usuario(cambio.getUsuario()))
                    .append(": ")
                    .append(limpiar(cambio.getDescripcion()))
                    .append("\n"));
            mensaje.append("\n");
        });

        mensaje.append("Gestoria CN");
        return mensaje.toString().trim();
    }

    private Map<Long, List<HistorialCambio>> cambiosPorCliente(List<HistorialCambio> cambios) {
        Map<Long, List<HistorialCambio>> resultado = new LinkedHashMap<>();
        cambios.stream()
                .filter(cambio -> cambio.getExpediente() != null
                        && cambio.getExpediente().getCliente() != null
                        && cambio.getExpediente().getCliente().getId() != null)
                .forEach(cambio -> resultado
                        .computeIfAbsent(cambio.getExpediente().getCliente().getId(), ignored -> new ArrayList<>())
                        .add(cambio));
        return resultado;
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

    private String usuario(Usuario usuario) {
        if (usuario == null) {
            return "";
        }
        String nombre = ((usuario.getNombre() != null ? usuario.getNombre() : "") + " "
                + (usuario.getApellidos() != null ? usuario.getApellidos() : "")).trim();
        return StringUtils.hasText(nombre) ? " (" + nombre + ")" : "";
    }

    private String limpiar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "Sin descripcion";
        }
        return valor.replaceAll("\\s+", " ").trim();
    }

    private String humanizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "";
        }
        String texto = valor.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    public record ResultadoResumenDiario(int clientesEnviados, int cambiosIncluidos, List<String> avisos) {
    }
}
