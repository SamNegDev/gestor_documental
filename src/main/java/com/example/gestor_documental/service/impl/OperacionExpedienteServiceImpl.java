package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoOperacionExpediente;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.service.OperacionExpedienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperacionExpedienteServiceImpl implements OperacionExpedienteService {

    private final OperacionExpedienteRepository operacionRepository;

    @Override
    @Transactional
    public List<OperacionExpediente> sincronizarYListar(Expediente expediente) {
        if (esBatecom(expediente)) {
            asegurar(expediente, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE, 1, "Entrega del titular inicial a la compraventa.");
            asegurar(expediente, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM, 2, "Venta final de la compraventa al cliente.");
        } else {
            asegurar(expediente, TipoOperacionExpediente.TRASPASO_DIRECTO, 1, "Operacion unica del expediente.");
        }

        return operacionRepository.findByExpedienteIdOrderByOrdenAsc(expediente.getId());
    }

    private boolean esBatecom(Expediente expediente) {
        return expediente.getTipoTramite() != null
                && expediente.getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM;
    }

    private void asegurar(Expediente expediente, TipoOperacionExpediente tipo, int orden, String descripcion) {
        operacionRepository.findByExpedienteIdAndTipo(expediente.getId(), tipo).orElseGet(() -> {
            OperacionExpediente operacion = new OperacionExpediente();
            operacion.setExpediente(expediente);
            operacion.setTipo(tipo);
            operacion.setOrden(orden);
            operacion.setDescripcion(descripcion);
            operacion.setEstado(orden == 1 ? EstadoOperacionExpediente.EN_CURSO : EstadoOperacionExpediente.PENDIENTE);
            return operacionRepository.save(operacion);
        });
    }
}
