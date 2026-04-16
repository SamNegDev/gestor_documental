package com.example.gestor_documental.config;

import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.model.TipoIncidencia;
import com.example.gestor_documental.repository.TipoIncidenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TipoIncidenciaRepository tipoIncidenciaRepository;

    @Override
    public void run(String... args) throws Exception {
        if (tipoIncidenciaRepository.count() == 0) {
            List<TipoIncidencia> incidencias = Arrays.asList(
                    new TipoIncidencia(TipoIncidenciaEnum.RODAJE, "Falta pago de del impuesto de rodaje u otros impuestos locales.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.RESERVA, "El vehículo posee una reserva de dominio activa que impide la transmisión.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.EMBARGO, "El vehículo consta con embargo activo. Requiere notificación expresa o levantamiento.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.NOTIFICADO, "El vehículo consta ya como notificada su venta. Requiere aportar datos del notificador.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.RECHAZADO_DGT, "Trámite rechazado explícitamente por la DGT por inconsistencia de datos.", true)
            );
            tipoIncidenciaRepository.saveAll(incidencias);
            System.out.println("Base de datos inicializada: Se insertaron " + incidencias.size() + " registros base para TipoIncidencia.");
        }
    }
}
