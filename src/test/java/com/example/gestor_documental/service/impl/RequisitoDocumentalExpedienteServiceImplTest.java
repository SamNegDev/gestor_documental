package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
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
import static org.mockito.ArgumentMatchers.any;

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

    @Test
    void reconciliaDocumentoMaestroAunqueElExpedienteEstePendienteDeDocumentacion() {
        RequisitoDocumentalExpedienteRepository requisitoRepository = mock(RequisitoDocumentalExpedienteRepository.class);
        DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
        DocumentoIdentidadLecturaRepository lecturaRepository = mock(DocumentoIdentidadLecturaRepository.class);
        com.example.gestor_documental.service.ExpedienteService expedienteService =
                mock(com.example.gestor_documental.service.ExpedienteService.class);
        RequisitoDocumentalExpedienteServiceImpl service = new RequisitoDocumentalExpedienteServiceImpl(
                requisitoRepository, expedienteService, null, null, documentoRepository, lecturaRepository, null, null,
                null, null, null, null, null
        );
        Cliente cliente = new Cliente();
        cliente.setId(4L);
        cliente.setNif("B38436556");
        Expediente expediente = new Expediente();
        expediente.setId(588L);
        expediente.setCliente(cliente);
        expediente.setEstadoExpediente(EstadoExpediente.PENDIENTE_DOCUMENTACION);
        Interesado empresa = new Interesado("B38436556", "CANARIOALEMANA DE AUTOMOVILES SL");
        empresa.setId(555L);
        RequisitoDocumentalExpediente requisito = new RequisitoDocumentalExpediente();
        requisito.setExpediente(expediente);
        requisito.setInteresado(empresa);
        requisito.setRolInteresado(RolInteresado.VENDEDOR);
        requisito.setTipoDocumento(TipoDocumento.CIF);
        requisito.setEstado(EstadoRequisitoDocumental.REQUERIDO);
        Documento documentoMaestro = new Documento();
        documentoMaestro.setId(7000L);
        documentoMaestro.setCliente(cliente);
        documentoMaestro.setTipoDocumento(TipoDocumento.CIF);
        when(requisitoRepository.findByExpedienteIdOrderByIdAsc(588L)).thenReturn(List.of(requisito));
        when(documentoRepository.findByClienteIdOrderByFechaSubidaDesc(any(), any()))
                .thenReturn(List.of(documentoMaestro));
        when(lecturaRepository.findByDocumentoId(7000L)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "reconciliarConDocumentos", expediente, List.of(), null);

        assertThat(requisito.getEstado()).isEqualTo(EstadoRequisitoDocumental.APORTADO);
        assertThat(requisito.getDocumento()).isEqualTo(documentoMaestro);
        verify(expedienteService).reanudarTrasDocumentacion(588L, null);
    }

    @Test
    void normalizaIdentificacionesPendientesBatecomComoComunesALasDosOperaciones() {
        RequisitoDocumentalExpedienteRepository requisitoRepository = mock(RequisitoDocumentalExpedienteRepository.class);
        RequisitoDocumentalExpedienteServiceImpl service = new RequisitoDocumentalExpedienteServiceImpl(
                requisitoRepository, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
        Expediente expediente = new Expediente();
        expediente.setId(586L);
        OperacionExpediente operacion = new OperacionExpediente();
        operacion.setId(100L);
        RequisitoDocumentalExpediente requisito = new RequisitoDocumentalExpediente();
        requisito.setOrigen(OrigenRequisitoDocumental.REGLA);
        requisito.setEstado(EstadoRequisitoDocumental.REQUERIDO);
        requisito.setTipoDocumento(TipoDocumento.CIF);
        requisito.setOperacion(operacion);
        when(requisitoRepository.findByExpedienteIdOrderByIdAsc(586L)).thenReturn(List.of(requisito));

        ReflectionTestUtils.invokeMethod(service, "normalizarIdentificacionesPendientesBatecom", expediente);

        assertThat(requisito.getOperacion()).isNull();
        verify(requisitoRepository).save(requisito);
    }

    @Test
    void unRepresentanteDetectadoSeAsociaTambienComoHabitual() {
        RequisitoDocumentalExpedienteRepository requisitoRepository = mock(RequisitoDocumentalExpedienteRepository.class);
        ClienteInteresadoRepository clienteInteresadoRepository = mock(ClienteInteresadoRepository.class);
        RequisitoDocumentalExpedienteServiceImpl service = new RequisitoDocumentalExpedienteServiceImpl(
                requisitoRepository, null, null, null, null, null, clienteInteresadoRepository, null,
                null, null, null, null, null
        );
        Cliente cliente = new Cliente();
        cliente.setId(4L);
        Expediente expediente = new Expediente();
        expediente.setCliente(cliente);
        Interesado representante = new Interesado("12345678Z", "ANTONIO ARMAS");
        representante.setId(637L);
        when(clienteInteresadoRepository.findByClienteIdAndInteresadoId(4L, 637L)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "asociarRepresentanteACliente", expediente, representante);

        org.mockito.ArgumentCaptor<com.example.gestor_documental.model.ClienteInteresado> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.gestor_documental.model.ClienteInteresado.class);
        verify(clienteInteresadoRepository).save(captor.capture());
        assertThat(captor.getValue().getRepresentanteLegal()).isTrue();
        assertThat(captor.getValue().getHabitual()).isTrue();
    }
}
