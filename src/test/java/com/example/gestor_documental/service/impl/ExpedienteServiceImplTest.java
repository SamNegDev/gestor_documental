package com.example.gestor_documental.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.TipoIncidencia;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.service.AvisoAdminService;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteTipoTramitePolicyService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.VehiculoService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpedienteServiceImplTest {

    @Mock private ExpedienteRepository expedienteRepository;
    @Mock private InteresadoService interesadoService;
    @Mock private ExpedienteInteresadoRepository expedienteInteresadoRepository;
    @Mock private ClienteService clienteService;
    @Mock private TipoTramiteService tipoTramiteService;
    @Mock private IncidenciaRepository incidenciaRepository;
    @Mock private RequisitoDocumentalExpedienteRepository requisitoRepository;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private HistorialCambioService historialCambioService;
    @Mock private VehiculoService vehiculoService;
    @Mock private AvisoAdminService avisoAdminService;

    private ExpedienteServiceImpl service;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        service = new ExpedienteServiceImpl(
                expedienteRepository,
                interesadoService,
                expedienteInteresadoRepository,
                clienteService,
                tipoTramiteService,
                incidenciaRepository,
                requisitoRepository,
                documentoRepository,
                historialCambioService,
                vehiculoService,
                new ExpedienteTipoTramitePolicyService(),
                avisoAdminService);
        admin = new Usuario("Admin", "Test", "admin@test.local", "secret", RolUsuario.ADMIN, true);
    }

    @Test
    void noRegistraDocumentacionCompletadaSiSiguePendientePorIncidenciaDocumental() {
        Expediente expediente = expediente(EstadoExpediente.PENDIENTE_DOCUMENTACION, TipoTramiteEnum.TRASPASO);
        TipoIncidencia tipo = new TipoIncidencia(TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION, "Documentacion", true);
        Incidencia incidencia = new Incidencia();
        incidencia.setTipoIncidencia(tipo);

        when(expedienteRepository.findById(1L)).thenReturn(Optional.of(expediente));
        when(requisitoRepository.findByExpedienteIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(incidenciaRepository.findByExpedienteIdAndResueltaFalse(1L)).thenReturn(List.of(incidencia));

        service.reanudarTrasDocumentacion(1L, admin);

        verify(expedienteRepository, never()).save(expediente);
        verify(historialCambioService, never()).registrarCambioExpediente(
                expediente,
                admin,
                "DOCUMENTACION COMPLETADA",
                "Se completaron los requisitos documentales y el expediente retoma su tramitacion.");
    }

    @Test
    void modelo620NoRequeridoNoBloqueaLaReanudacion() {
        Expediente expediente = expediente(EstadoExpediente.PENDIENTE_DOCUMENTACION, TipoTramiteEnum.HERENCIA);
        expediente.setEstadoPrevioPausa(EstadoExpediente.EN_TRAMITE);
        RequisitoDocumentalExpediente requisito = new RequisitoDocumentalExpediente();
        requisito.setExpediente(expediente);
        requisito.setTipoDocumento(TipoDocumento.MODELO_620);
        requisito.setDescripcion("Modelo 620");
        requisito.setEstado(EstadoRequisitoDocumental.REQUERIDO);

        when(expedienteRepository.findById(1L)).thenReturn(Optional.of(expediente));
        when(requisitoRepository.findByExpedienteIdOrderByIdAsc(1L)).thenReturn(List.of(requisito));
        when(incidenciaRepository.findByExpedienteIdAndResueltaFalse(1L)).thenReturn(List.of());

        service.reanudarTrasDocumentacion(1L, admin);

        verify(historialCambioService).registrarCambioExpediente(
                expediente,
                admin,
                "DOCUMENTACION COMPLETADA",
                "Se completaron los requisitos documentales y el expediente retoma su tramitacion.");
    }

    @Test
    void alFinalizarOrigenAdjuntaHuellaComoPermisoYGeneraAviso() {
        Cliente clienteDependiente = new Cliente();
        clienteDependiente.setId(9L);
        Expediente origen = expediente(EstadoExpediente.EN_TRAMITE, TipoTramiteEnum.TRASPASO);
        origen.setId(10L);
        Expediente dependiente = expediente(EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO, TipoTramiteEnum.TRASPASO);
        dependiente.setId(20L);
        dependiente.setCliente(clienteDependiente);
        dependiente.setEstadoPrevioPausa(EstadoExpediente.ENVIADO_DGT);
        Documento huellaFinal = new Documento();
        huellaFinal.setTipoDocumento(TipoDocumento.HUELLA_TRAMITE);
        huellaFinal.setNombreArchivo("huella-final.pdf");
        huellaFinal.setNombreArchivoOriginal("huella-final-original.pdf");

        when(expedienteRepository.findById(10L)).thenReturn(Optional.of(origen));
        when(incidenciaRepository.findByExpedienteIdAndResueltaFalse(10L)).thenReturn(List.of());
        when(documentoRepository.findByExpedienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(
                10L,
                List.of(TipoDocumento.COMPROBANTE_DGT, TipoDocumento.HUELLA_TRAMITE)
        )).thenReturn(List.of(huellaFinal));
        when(expedienteRepository.findByExpedienteVinculadoOrigenIdAndEstadoExpediente(
                10L,
                EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO
        )).thenReturn(List.of(dependiente));

        service.cambiarEstado(10L, EstadoExpediente.FINALIZADO, admin);

        ArgumentCaptor<Documento> documentoCaptor = ArgumentCaptor.forClass(Documento.class);
        verify(documentoRepository).save(documentoCaptor.capture());
        Documento documentoVinculado = documentoCaptor.getValue();
        assertEquals(TipoDocumento.PERMISO_CIRCULACION, documentoVinculado.getTipoDocumento());
        assertEquals(dependiente, documentoVinculado.getExpediente());
        assertEquals(clienteDependiente, documentoVinculado.getCliente());
        assertEquals("Permiso de circulacion incorporado desde la huella final de EXP-10", documentoVinculado.getDescripcionArchivo());
        assertEquals(EstadoExpediente.ENVIADO_DGT, dependiente.getEstadoExpediente());

        verify(avisoAdminService).crear(
                "TRAMITE_VINCULADO_LISTO",
                "Tramite vinculado listo",
                "EXP-20 ya puede continuar porque EXP-10 finalizo. La huella anterior se adjunto como permiso de circulacion.",
                "TRAMITE_VINCULADO",
                dependiente,
                clienteDependiente);
    }

    @Test
    void cancelaExpedienteConIncidenciasActivasYAvisaSiTieneDependiente() {
        Expediente origen = expediente(EstadoExpediente.INCIDENCIA, TipoTramiteEnum.TRASPASO);
        origen.setId(10L);
        Expediente dependiente = expediente(EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO, TipoTramiteEnum.TRASPASO);
        dependiente.setId(20L);

        when(expedienteRepository.findById(10L)).thenReturn(Optional.of(origen));
        when(expedienteRepository.findByExpedienteVinculadoOrigenIdAndEstadoExpediente(
                10L, EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO)).thenReturn(List.of(dependiente));

        service.cambiarEstado(10L, EstadoExpediente.CANCELADO, admin);

        assertEquals(EstadoExpediente.CANCELADO, origen.getEstadoExpediente());
        assertEquals(EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO, dependiente.getEstadoExpediente());
        verify(avisoAdminService).crear(
                "TRAMITE_VINCULADO_ORIGEN_CANCELADO",
                "Tramite vinculado con origen cancelado",
                "EXP-20 sigue en espera, pero su expediente previo EXP-10 fue cancelado por el cliente. Revisa si debe desvincularse o cancelarse.",
                "TRAMITE_VINCULADO",
                dependiente,
                null);
    }

    @Test
    void noPermiteReabrirUnExpedienteCancelado() {
        Expediente expediente = expediente(EstadoExpediente.CANCELADO, TipoTramiteEnum.TRASPASO);
        when(expedienteRepository.findById(1L)).thenReturn(Optional.of(expediente));

        assertThrows(OperacionInvalidaException.class,
                () -> service.cambiarEstado(1L, EstadoExpediente.EN_TRAMITE, admin));

        verify(expedienteRepository, never()).save(expediente);
    }

    private Expediente expediente(EstadoExpediente estado, TipoTramiteEnum tramiteEnum) {
        Expediente expediente = new Expediente();
        expediente.setId(1L);
        expediente.setEstadoExpediente(estado);
        TipoTramite tramite = new TipoTramite();
        tramite.setNombre(tramiteEnum);
        expediente.setTipoTramite(tramite);
        return expediente;
    }
}
