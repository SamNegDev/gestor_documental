package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvisosClienteProgramadosService {
    private final ClienteRepository clienteRepository;
    private final ResumenFinalizadosDiarioService finalizadosService;
    private final ResumenDiarioTramitesService incidenciasService;
    private final ConfiguracionSeguimientoService configuracionSeguimientoService;

    @Value("${app.client-notifications.enabled:false}")
    private boolean enabled;
    @Value("${app.client-notifications.zone:Atlantic/Canary}")
    private String zone;
    @Value("${app.client-notifications.max-per-run:5}")
    private int maxPerRun;
    @Value("${app.client-notifications.max-per-day:20}")
    private int maxPerDay;

    @Scheduled(cron = "${app.client-notifications.cron:0 * * * * *}", zone = "${app.client-notifications.zone:Atlantic/Canary}")
    public void procesar() {
        if (!enabled) {
            return;
        }

        ConfiguracionSeguimiento config = configuracionSeguimientoService.obtener();
        ZoneId zona = ZoneId.of(zone);
        LocalDate hoy = LocalDate.now(zona);
        LocalTime ahora = LocalTime.now(zona).withSecond(59).withNano(0);
        if (!permiteEnvioAutomatico(config, hoy, ahora)) {
            return;
        }

        long procesadosHoy = clienteRepository.countByUltimoAvisoIncidencias(hoy)
                + clienteRepository.countByUltimoAvisoFinalizados(hoy);
        int limite = (int) Math.min(
                Math.max(0L, (long) maxPerDay - procesadosHoy),
                Math.min(Math.max(1, maxPerRun), Math.max(1, config.getTamanioLote()))
        );
        if (limite <= 0) {
            log.warn("Limite diario de avisos automaticos alcanzado: {} intentos el {}.", procesadosHoy, hoy);
            return;
        }

        int procesados = procesarIncidencias(hoy, ahora, limite);
        if (procesados < limite) {
            procesarFinalizados(hoy, ahora, limite - procesados);
        }
    }

    private int procesarIncidencias(LocalDate hoy, LocalTime ahora, int limite) {
        List<Cliente> pendientes = clienteRepository.findPendientesAvisoIncidencias(
                hoy, ahora, PageRequest.of(0, limite));
        int procesados = 0;
        for (Cliente cliente : pendientes) {
            if (clienteRepository.reservarAvisoIncidencias(cliente.getId(), hoy) != 1) {
                continue;
            }
            procesados++;
            try {
                var resultado = incidenciasService.enviarListadoIncidenciasAutomaticoCliente(cliente.getId());
                log.info("Aviso automatico de incidencias procesado para cliente {}: {} envios.",
                        cliente.getId(), resultado.clientesEnviados());
            } catch (RuntimeException ex) {
                log.error("Error en aviso automatico de incidencias del cliente {}. No se reintentara automaticamente hoy.",
                        cliente.getId(), ex);
            }
        }
        return procesados;
    }

    private int procesarFinalizados(LocalDate hoy, LocalTime ahora, int limite) {
        List<Cliente> pendientes = clienteRepository.findPendientesAvisoFinalizados(
                hoy, ahora, PageRequest.of(0, limite));
        int procesados = 0;
        for (Cliente cliente : pendientes) {
            if (clienteRepository.reservarAvisoFinalizados(cliente.getId(), hoy) != 1) {
                continue;
            }
            procesados++;
            try {
                var resultado = finalizadosService.enviarClienteDelDia(cliente.getId());
                log.info("Aviso automatico de finalizados procesado para cliente {}: {} envios.",
                        cliente.getId(), resultado.correos());
            } catch (RuntimeException ex) {
                log.error("Error en aviso automatico de finalizados del cliente {}. No se reintentara automaticamente hoy.",
                        cliente.getId(), ex);
            }
        }
        return procesados;
    }

    private boolean permiteEnvioAutomatico(ConfiguracionSeguimiento config, LocalDate hoy, LocalTime ahora) {
        if (!config.isAutomatizacionActiva() || config.isModoSupervisado()) {
            return false;
        }
        if (!"EMAIL".equalsIgnoreCase(config.getCanalAutomatico())) {
            log.warn("Avisos automaticos omitidos: el canal configurado no es EMAIL.");
            return false;
        }
        if ("LABORABLES".equalsIgnoreCase(config.getDiasEnvio())
                && (hoy.getDayOfWeek() == DayOfWeek.SATURDAY || hoy.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return false;
        }
        return ahora.getHour() >= config.getHoraEnvio();
    }
}
