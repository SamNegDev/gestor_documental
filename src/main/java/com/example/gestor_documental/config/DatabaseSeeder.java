package com.example.gestor_documental.config;

import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.TipoIncidencia;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.repository.TipoIncidenciaRepository;
import com.example.gestor_documental.repository.TipoTramiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TipoIncidenciaRepository tipoIncidenciaRepository;
    private final TipoTramiteRepository tipoTramiteRepository;

    @Override
    public void run(String... args) {
        if (tipoIncidenciaRepository.count() == 0) {
            List<TipoIncidencia> incidencias = Arrays.asList(
                    new TipoIncidencia(TipoIncidenciaEnum.RODAJE,
                            "Falta pago del impuesto de rodaje u otros impuestos locales.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.RESERVA,
                            "El vehiculo posee una reserva de dominio activa que impide la transmision.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.EMBARGO,
                            "El vehiculo consta con embargo activo. Requiere notificacion expresa o levantamiento.",
                            true),
                    new TipoIncidencia(TipoIncidenciaEnum.NOTIFICADO,
                            "El vehiculo consta ya como notificada su venta. Requiere aportar datos del notificador.",
                            true),
                    new TipoIncidencia(TipoIncidenciaEnum.DENEGATORIA,
                            "Existe una denegatoria o impedimento administrativo que bloquea la tramitacion.", true),
                    new TipoIncidencia(TipoIncidenciaEnum.RECHAZADO_DGT,
                            "Tramite rechazado explicitamente por la DGT por inconsistencia de datos.", true));
            tipoIncidenciaRepository.saveAll(incidencias);
            System.out.println("Base de datos inicializada: Se insertaron " + incidencias.size()
                    + " registros base para TipoIncidencia.");
        }
        if (tipoTramiteRepository.count() == 0) {
            List<TipoTramite> tiposTramite = Arrays.asList(
                    new TipoTramite(TipoTramiteEnum.TRASPASO, "Cambio de titularidad"),
                    new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM"),
                    new TipoTramite(TipoTramiteEnum.ALTA, "Alta de vehiculo"),
                    new TipoTramite(TipoTramiteEnum.BAJA, "Baja de vehiculo"),
                    new TipoTramite(TipoTramiteEnum.DUPLICADO, "Duplicado de tarjeta ITV"),
                    new TipoTramite(TipoTramiteEnum.MATRICULACION, "Matriculacion de vehiculo"),
                    new TipoTramite(TipoTramiteEnum.NOTIFICACION_VENTA, "Notificacion de venta"),
                    new TipoTramite(TipoTramiteEnum.HERENCIA, "Herencia"),
                    new TipoTramite(TipoTramiteEnum.CUESTIONES_VARIAS, "Cuestiones varias"));
            tipoTramiteRepository.saveAll(tiposTramite);
            System.out.println("Base de datos inicializada: Se insertaron " + tiposTramite.size()
                    + " registros base para TipoTramite.");
        }
        asegurarTipoTramite(TipoTramiteEnum.BATECOM, "BATECOM");
        asegurarTipoTramite(TipoTramiteEnum.NOTIFICACION_VENTA, "Notificacion de venta");
        asegurarTipoTramite(TipoTramiteEnum.HERENCIA, "Herencia");
        asegurarTipoTramite(TipoTramiteEnum.CUESTIONES_VARIAS, "Cuestiones varias");
        if (tipoIncidenciaRepository.findByNombre(TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION).isEmpty()) {
            tipoIncidenciaRepository.save(new TipoIncidencia(TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION,
                    "Falta documentacion necesaria u obligatoria para el tramite.", true));
            System.out.println("Se inserto el nuevo TipoIncidencia: PENDIENTE_DOCUMENTACION");
        }
        if (tipoIncidenciaRepository.findByNombre(TipoIncidenciaEnum.DENEGATORIA).isEmpty()) {
            tipoIncidenciaRepository.save(new TipoIncidencia(TipoIncidenciaEnum.DENEGATORIA,
                    "Existe una denegatoria o impedimento administrativo que bloquea la tramitacion.", true));
            System.out.println("Se inserto el nuevo TipoIncidencia: DENEGATORIA");
        }
        if (tipoIncidenciaRepository.findByNombre(TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL).isEmpty()) {
            tipoIncidenciaRepository.save(new TipoIncidencia(TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL,
                    "Se necesita una respuesta o aclaracion adicional del cliente.", true));
            System.out.println("Se inserto el nuevo TipoIncidencia: SOLICITADA_INFORMACION_ADICIONAL");
        }
    }

    private void asegurarTipoTramite(TipoTramiteEnum nombre, String descripcion) {
        if (tipoTramiteRepository.findByNombre(nombre).isEmpty()) {
            tipoTramiteRepository.save(new TipoTramite(nombre, descripcion));
            System.out.println("Se inserto el nuevo TipoTramite: " + nombre);
        }
    }
}
