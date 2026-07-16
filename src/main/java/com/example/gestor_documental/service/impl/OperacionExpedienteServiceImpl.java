package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoOperacionExpediente;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HitoExpediente;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.service.OperacionExpedienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperacionExpedienteServiceImpl implements OperacionExpedienteService {

    private static final Set<CodigoHitoExpediente> HITOS_DIRECTOS = EnumSet.of(
            CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION,
            CodigoHitoExpediente.MODELO_620_PRESENTADO,
            CodigoHitoExpediente.ENVIADO_DGT
    );
    private static final Set<CodigoHitoExpediente> HITOS_BATECOM = EnumSet.of(
            CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION,
            CodigoHitoExpediente.BATE_MODELO_620_PRESENTADO,
            CodigoHitoExpediente.BATE_FINALIZADO,
            CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION,
            CodigoHitoExpediente.COM_MODELO_620_PRESENTADO,
            CodigoHitoExpediente.COM_ENVIADO_DGT,
            CodigoHitoExpediente.COM_FINALIZADO
    );

    private final OperacionExpedienteRepository operacionRepository;
    private final DocumentoRepository documentoRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final HitoExpedienteRepository hitoRepository;

    @Override
    @Transactional
    public List<OperacionExpediente> sincronizarYListar(Expediente expediente) {
        boolean batecom = esBatecom(expediente);
        if (batecom) {
            asegurar(expediente, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE, 1, "Entrega del titular inicial a la compraventa.");
            asegurar(expediente, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM, 2, "Venta final de la compraventa al cliente.");
        } else {
            asegurar(expediente, TipoOperacionExpediente.TRASPASO_DIRECTO, 1, "Operacion unica del expediente.");
        }

        List<OperacionExpediente> operaciones = operacionRepository.findByExpedienteIdOrderByOrdenAsc(expediente.getId());
        List<OperacionExpediente> obsoletas = operaciones.stream()
                .filter(operacion -> batecom
                        ? operacion.getTipo() == TipoOperacionExpediente.TRASPASO_DIRECTO
                        : operacion.getTipo() != TipoOperacionExpediente.TRASPASO_DIRECTO)
                .toList();
        if (obsoletas.isEmpty()) {
            return operaciones;
        }

        limpiarFlujoAnterior(expediente.getId(), batecom, obsoletas);
        return operacionRepository.findByExpedienteIdOrderByOrdenAsc(expediente.getId());
    }

    private void limpiarFlujoAnterior(Long expedienteId, boolean batecom, List<OperacionExpediente> obsoletas) {
        Set<Long> idsObsoletos = obsoletas.stream()
                .map(OperacionExpediente::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<Documento> documentos = documentoRepository.findByExpedienteId(expedienteId).stream()
                .filter(documento -> documento.getOperacion() != null
                        && idsObsoletos.contains(documento.getOperacion().getId()))
                .toList();
        documentos.forEach(documento -> documento.setOperacion(null));
        documentoRepository.saveAll(documentos);

        List<RequisitoDocumentalExpediente> requisitos = requisitoRepository
                .findByExpedienteIdOrderByIdAsc(expedienteId);
        List<RequisitoDocumentalExpediente> reglasObsoletas = requisitos.stream()
                .filter(requisito -> requisito.getOrigen() == OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> operacionObsoleta(requisito, idsObsoletos)
                        || (batecom && requisito.getOperacion() == null))
                .toList();
        requisitoRepository.deleteAll(reglasObsoletas);

        List<RequisitoDocumentalExpediente> requisitosConservados = requisitos.stream()
                .filter(requisito -> requisito.getOrigen() != OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> operacionObsoleta(requisito, idsObsoletos))
                .toList();
        requisitosConservados.forEach(requisito -> requisito.setOperacion(null));
        requisitoRepository.saveAll(requisitosConservados);

        Set<CodigoHitoExpediente> hitosObsoletos = batecom ? HITOS_DIRECTOS : HITOS_BATECOM;
        List<HitoExpediente> hitos = hitoRepository.findByExpedienteId(expedienteId).stream()
                .filter(hito -> hitosObsoletos.contains(hito.getCodigo()))
                .toList();
        hitoRepository.deleteAll(hitos);
        operacionRepository.deleteAll(obsoletas);
    }

    private boolean operacionObsoleta(RequisitoDocumentalExpediente requisito, Set<Long> idsObsoletos) {
        return requisito.getOperacion() != null
                && idsObsoletos.contains(requisito.getOperacion().getId());
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
