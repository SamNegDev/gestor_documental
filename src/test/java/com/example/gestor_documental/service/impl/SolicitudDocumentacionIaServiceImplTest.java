package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.LecturaIaSolicitudClienteResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.GestionPersonaCatalogoRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.validation.DniNieValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudDocumentacionIaServiceImplTest {

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    @Mock
    private DocumentoRolesLecturaRepository rolesLecturaRepository;
    @Mock
    private GestionPersonaCatalogoRepository gestionPersonaCatalogoRepository;
    @Mock
    private HistorialCambioRepository historialCambioRepository;
    @Mock
    private DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    @Mock
    private DocumentoRolesLecturaService documentoRolesLecturaService;
    @Mock
    private HistorialCambioService historialCambioService;

    private SolicitudDocumentacionIaServiceImpl service;
    private Usuario usuarioCliente;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setApiKey("test-key");
        service = new SolicitudDocumentacionIaServiceImpl(
                solicitudRepository,
                documentoRepository,
                identidadLecturaRepository,
                rolesLecturaRepository,
                gestionPersonaCatalogoRepository,
                historialCambioRepository,
                documentoIdentidadLecturaService,
                documentoRolesLecturaService,
                historialCambioService,
                new DniNieValidator(),
                openAiProperties
        );
        cliente = new Cliente();
        cliente.setId(7L);
        usuarioCliente = new Usuario();
        usuarioCliente.setRolUsuario(RolUsuario.CLIENTE);
        usuarioCliente.setCliente(cliente);
    }

    @Test
    void permiteLecturaClienteConDniYPermisoFichaSinContrato() {
        Solicitud solicitud = solicitudCliente(30L);
        when(solicitudRepository.findById(30L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(30L)).thenReturn(List.of(
                documento(1L, TipoDocumento.DNI),
                documento(2L, TipoDocumento.PERMISO_CIRCULACION),
                documento(3L, TipoDocumento.FICHA_TECNICA)
        ));
        when(historialCambioRepository.countBySolicitudIdAndAccion(30L, "IA DOCUMENTACION CLIENTE")).thenReturn(0L);

        LecturaIaSolicitudClienteResponse response = service.obtenerLecturaCliente(30L, usuarioCliente);

        assertThat(response.documentacionSuficiente()).isTrue();
        assertThat(response.puedeSolicitar()).isTrue();
        assertThat(response.bloqueosDocumentales()).isEmpty();
        assertThat(response.documentosIdentidad()).isEqualTo(1);
        assertThat(response.documentosVehiculo()).isEqualTo(2);
        assertThat(response.documentosRoles()).isZero();
    }

    @Test
    void permiteLecturaClienteConDniEInformeDgt() {
        Solicitud solicitud = solicitudCliente(31L);
        when(solicitudRepository.findById(31L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(31L)).thenReturn(List.of(
                documento(4L, TipoDocumento.DNI),
                documento(5L, TipoDocumento.INFORME_DGT)
        ));
        when(historialCambioRepository.countBySolicitudIdAndAccion(31L, "IA DOCUMENTACION CLIENTE")).thenReturn(0L);

        LecturaIaSolicitudClienteResponse response = service.obtenerLecturaCliente(31L, usuarioCliente);

        assertThat(response.documentacionSuficiente()).isTrue();
        assertThat(response.puedeSolicitar()).isTrue();
        assertThat(response.documentosVehiculo()).isEqualTo(1);
    }

    private Solicitud solicitudCliente(Long id) {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(id);
        solicitud.setCliente(cliente);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        return solicitud;
    }

    private Documento documento(Long id, TipoDocumento tipo) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setTipoDocumento(tipo);
        return documento;
    }
}
