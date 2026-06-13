package com.example.gestor_documental.config;

import com.example.gestor_documental.service.VehiculoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehiculoMigrationRunner implements ApplicationRunner {

    private final VehiculoService vehiculoService;

    @Override
    public void run(ApplicationArguments args) {
        int actualizados = vehiculoService.migrarExpedientesExistentes();
        if (actualizados > 0) {
            log.info("Migrados {} expedientes a la entidad Vehiculo", actualizados);
        }
    }
}
