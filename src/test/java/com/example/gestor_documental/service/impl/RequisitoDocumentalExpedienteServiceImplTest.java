package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequisitoDocumentalExpedienteServiceImplTest {

    @Test
    void retrocederAntesDelTramiteDevuelveElComprobanteDgtAFasePosterior() {
        RequisitoDocumentalExpedienteRepository requisitoRepository = mock(RequisitoDocumentalExpedienteRepository.class);
        HitoExpedienteRepository hitoRepository = mock(HitoExpedienteRepository.class);
        OperacionExpedienteRepository operacionRepository = mock(OperacionExpedienteRepository.class);
        RequisitoDocumentalExpedienteServiceImpl service = new RequisitoDocumentalExpedienteServiceImpl(
                requisitoRepository, null, null, null, null, null, null, null,
                hitoRepository, operacionRepository, null, null, null
        );
        Expediente expediente = new Expediente();
        expediente.setId(378L);
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
        OperacionExpediente operacion = new OperacionExpediente();
        operacion.setId(402L);
        operacion.setExpediente(expediente);
        operacion.setTipo(TipoOperacionExpediente.TRASPASO_DIRECTO);
        RequisitoDocumentalExpediente comprobante = new RequisitoDocumentalExpediente();
        comprobante.setExpediente(expediente);
        comprobante.setOperacion(operacion);
        comprobante.setTipoDocumento(TipoDocumento.COMPROBANTE_DGT);
        comprobante.setDescripcion("Comprobante DGT o huella del tramite");
        comprobante.setOrigen(OrigenRequisitoDocumental.REGLA);
        comprobante.setEstado(EstadoRequisitoDocumental.REQUERIDO);
        when(operacionRepository.findByExpedienteIdAndTipo(378L, TipoOperacionExpediente.TRASPASO_DIRECTO))
                .thenReturn(Optional.of(operacion));
        when(hitoRepository.existsByExpedienteIdAndCodigo(378L, CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION))
                .thenReturn(false);
        when(requisitoRepository.findByExpedienteIdAndTipoDocumento(378L, TipoDocumento.MODELO_620))
                .thenReturn(List.of());
        when(requisitoRepository.findByExpedienteIdAndTipoDocumento(378L, TipoDocumento.COMPROBANTE_DGT))
                .thenReturn(List.of(comprobante));

        ReflectionTestUtils.invokeMethod(service, "generarRequisitosOperacion",
                expediente,
                TipoOperacionExpediente.TRASPASO_DIRECTO,
                CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION,
                null,
                "Modelo 620",
                "Comprobante DGT o huella del tramite",
                null);

        assertThat(comprobante.getEstado()).isEqualTo(EstadoRequisitoDocumental.POSTERIOR);
        verify(requisitoRepository).save(comprobante);
    }
}