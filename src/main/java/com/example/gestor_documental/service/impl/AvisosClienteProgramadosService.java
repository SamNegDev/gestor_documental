package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvisosClienteProgramadosService {
    private final ClienteRepository clienteRepository;
    private final ResumenFinalizadosDiarioService finalizadosService;
    private final ResumenDiarioTramitesService incidenciasService;

    @Value("${app.client-notifications.enabled:false}")
    private boolean enabled;
    @Value("${app.client-notifications.zone:Atlantic/Canary}")
    private String zone;

    @Scheduled(cron = "${app.client-notifications.cron:0 * * * * *}", zone = "${app.client-notifications.zone:Atlantic/Canary}")
    public void procesar() {
        if (!enabled) return;
        LocalDate hoy = LocalDate.now(ZoneId.of(zone));
        LocalTime ahora = LocalTime.now(ZoneId.of(zone)).withSecond(59).withNano(0);
        for (Cliente cliente : clienteRepository.findPendientesAvisoIncidencias(hoy, ahora)) {
            try {
                var resultado = incidenciasService.enviarListadoIncidenciasAutomaticoCliente(cliente.getId());
                if (resultado.cambiosIncluidos() == 0 || resultado.clientesEnviados() > 0) {
                    cliente.setUltimoAvisoIncidencias(hoy);
                    clienteRepository.save(cliente);
                }
                log.info("Aviso automatico de incidencias procesado para cliente {}: {} envios.", cliente.getId(), resultado.clientesEnviados());
            } catch (RuntimeException ex) {
                log.error("Error en aviso automatico de incidencias del cliente {}.", cliente.getId(), ex);
            }
        }
        for (Cliente cliente : clienteRepository.findPendientesAvisoFinalizados(hoy, ahora)) {
            try {
                var resultado = finalizadosService.enviarClienteDelDia(cliente.getId());
                if (resultado.expedientes() == 0 || resultado.correos() > 0) {
                    cliente.setUltimoAvisoFinalizados(hoy);
                    clienteRepository.save(cliente);
                }
                log.info("Aviso automatico de finalizados procesado para cliente {}: {} envios.", cliente.getId(), resultado.correos());
            } catch (RuntimeException ex) {
                log.error("Error en aviso automatico de finalizados del cliente {}.", cliente.getId(), ex);
            }
        }
    }
}