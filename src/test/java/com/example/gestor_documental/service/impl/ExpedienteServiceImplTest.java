package com.example.gestor_documental.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.enums.TipoTramiteEnum;
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
                new ExpedienteTipoTramitePolicyService());
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
