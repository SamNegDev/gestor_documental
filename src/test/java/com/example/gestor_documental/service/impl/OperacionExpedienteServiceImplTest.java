package com.example.gestor_documental.service.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HitoExpediente;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperacionExpedienteServiceImplTest {

    @Mock private OperacionExpedienteRepository operacionRepository;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private RequisitoDocumentalExpedienteRepository requisitoRepository;
    @Mock private HitoExpedienteRepository hitoRepository;

    private OperacionExpedienteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OperacionExpedienteServiceImpl(
                operacionRepository,
                documentoRepository,
                requisitoRepository,
                hitoRepository);
    }

    @Test
    void eliminaOperacionDirectaSobranteAlVolverABatecom() {
        Expediente expediente = expediente(TipoTramiteEnum.BATECOM);
        OperacionExpediente bate = operacion(10L, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE);
        OperacionExpediente com = operacion(11L, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM);
        OperacionExpediente directa = operacion(12L, TipoOperacionExpediente.TRASPASO_DIRECTO);
        RequisitoDocumentalExpediente reglaDirecta = requisito(OrigenRequisitoDocumental.REGLA, null);
        RequisitoDocumentalExpediente reglaBate = requisito(OrigenRequisitoDocumental.REGLA, bate);

        when(operacionRepository.findByExpedienteIdAndTipo(1L, bate.getTipo())).thenReturn(Optional.of(bate));
        when(operacionRepository.findByExpedienteIdAndTipo(1L, com.getTipo())).thenReturn(Optional.of(com));
        when(operacionRepository.findByExpedienteIdOrderByOrdenAsc(1L))
                .thenReturn(List.of(bate, directa, com), List.of(bate, com));
        when(documentoRepository.findByExpedienteId(1L)).thenReturn(List.of());
        when(requisitoRepository.findByExpedienteIdOrderByIdAsc(1L))
                .thenReturn(List.of(reglaDirecta, reglaBate));
        when(hitoRepository.findByExpedienteId(1L)).thenReturn(List.of());

        service.sincronizarYListar(expediente);

        verify(requisitoRepository).deleteAll(List.of(reglaDirecta));
        verify(operacionRepository).deleteAll(List.of(directa));
    }

    @Test
    void conservaArchivosYRequisitosManualesAlCambiarAOperacionDirecta() {
        Expediente expediente = expediente(TipoTramiteEnum.TRASPASO);
        OperacionExpediente directa = operacion(20L, TipoOperacionExpediente.TRASPASO_DIRECTO);
        OperacionExpediente bate = operacion(21L, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE);
        Documento documento = new Documento();
        documento.setOperacion(bate);
        RequisitoDocumentalExpediente regla = requisito(OrigenRequisitoDocumental.REGLA, bate);
        RequisitoDocumentalExpediente manual = requisito(OrigenRequisitoDocumental.MANUAL, bate);
        HitoExpediente hito = new HitoExpediente();
        hito.setCodigo(CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION);

        when(operacionRepository.findByExpedienteIdAndTipo(1L, directa.getTipo())).thenReturn(Optional.of(directa));
        when(operacionRepository.findByExpedienteIdOrderByOrdenAsc(1L))
                .thenReturn(List.of(directa, bate), List.of(directa));
        when(documentoRepository.findByExpedienteId(1L)).thenReturn(List.of(documento));
        when(requisitoRepository.findByExpedienteIdOrderByIdAsc(1L)).thenReturn(List.of(regla, manual));
        when(hitoRepository.findByExpedienteId(1L)).thenReturn(List.of(hito));

        service.sincronizarYListar(expediente);

        assertNull(documento.getOperacion());
        assertNull(manual.getOperacion());
        verify(documentoRepository).saveAll(List.of(documento));
        verify(requisitoRepository).deleteAll(List.of(regla));
        verify(requisitoRepository).saveAll(List.of(manual));
        verify(hitoRepository).deleteAll(List.of(hito));
        verify(operacionRepository).deleteAll(List.of(bate));
    }

    private Expediente expediente(TipoTramiteEnum tipo) {
        TipoTramite tipoTramite = new TipoTramite();
        tipoTramite.setNombre(tipo);
        Expediente expediente = new Expediente();
        expediente.setId(1L);
        expediente.setTipoTramite(tipoTramite);
        return expediente;
    }

    private OperacionExpediente operacion(Long id, TipoOperacionExpediente tipo) {
        OperacionExpediente operacion = new OperacionExpediente();
        operacion.setId(id);
        operacion.setTipo(tipo);
        return operacion;
    }

    private RequisitoDocumentalExpediente requisito(
            OrigenRequisitoDocumental origen,
            OperacionExpediente operacion
    ) {
        RequisitoDocumentalExpediente requisito = new RequisitoDocumentalExpediente();
        requisito.setOrigen(origen);
        requisito.setOperacion(operacion);
        return requisito;
    }
}
