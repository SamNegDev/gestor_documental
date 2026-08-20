package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.CorreoEntranteProcesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.CorreoEntranteProcesadoRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.TipoTramiteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.service.ExpedienteCompletoProcesamientoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.PdfSplitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorreoEntranteSolicitudServiceTest {

    @Mock CorreoEntranteProcesadoRepository procesadoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock TipoTramiteRepository tipoTramiteRepository;
    @Mock SolicitudRepository solicitudRepository;
    @Mock ExpedienteCompletoProcesamientoService expedienteCompletoProcesamientoService;
    @Mock PdfSplitService pdfSplitService;
    @Mock HistorialCambioService historialCambioService;

    @InjectMocks CorreoEntranteSolicitudService service;

    private final Cliente cliente = new Cliente();
    private final Usuario admin = new Usuario();
    private final TipoTramite traspaso = new TipoTramite(TipoTramiteEnum.TRASPASO, "Traspaso");

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(service, "defaultTipoTramite", TipoTramiteEnum.TRASPASO);
        ReflectionTestUtils.setField(service, "adminEmail", "");
        when(clienteRepository.findByEmailIgnoreCase("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findFirstByRolUsuarioAndActivoTrueOrderByIdAsc(any())).thenReturn(Optional.of(admin));
        when(tipoTramiteRepository.findByNombre(TipoTramiteEnum.TRASPASO)).thenReturn(Optional.of(traspaso));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(invocation -> {
            Solicitud solicitud = invocation.getArgument(0);
            solicitud.setId(77L);
            return solicitud;
        });
        when(procesadoRepository.findByMessageId(any())).thenReturn(Optional.empty());
    }

    @Test
    void creaLaSolicitudDesdeMetadatosYEncolaElPdfSinEsperarOcr() {
        service.crearSolicitudDesdeAdjuntos(
                "mensaje-1",
                "Documentacion para 1234 BCD",
                "Cliente <cliente@example.com>",
                List.of(adjunto("documentacion.pdf"))
        );

        ArgumentCaptor<Solicitud> solicitudCaptor = ArgumentCaptor.forClass(Solicitud.class);
        verify(solicitudRepository).save(solicitudCaptor.capture());
        Solicitud guardada = solicitudCaptor.getValue();
        assertThat(guardada.getMatricula()).isEqualTo("1234BCD");
        assertThat(guardada.getEstadoSolicitud()).isEqualTo(EstadoSolicitud.PENDIENTE_REVISION);
        assertThat(guardada.getTipoTramite()).isSameAs(traspaso);

        ArgumentCaptor<MultipartFile> archivoCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(expedienteCompletoProcesamientoService)
                .iniciarSolicitud(eq(77L), archivoCaptor.capture(), eq(false), eq(admin));
        assertThat(archivoCaptor.getValue().getOriginalFilename()).isEqualTo("documentacion.pdf");
        assertThat(archivoCaptor.getValue().getSize()).isPositive();

        ArgumentCaptor<CorreoEntranteProcesado> correoCaptor = ArgumentCaptor.forClass(CorreoEntranteProcesado.class);
        verify(procesadoRepository).save(correoCaptor.capture());
        assertThat(correoCaptor.getValue().getEstado()).isEqualTo("PROCESADO");
        assertThat(correoCaptor.getValue().getSolicitudId()).isEqualTo(77L);
        assertThat(correoCaptor.getValue().getMatricula()).isEqualTo("1234BCD");
        assertThat(correoCaptor.getValue().getDetalle()).contains("en cola");
    }

    @Test
    void creaYEncolaAunqueLaMatriculaNoEsteEnAsuntoNiNombre() {
        service.crearSolicitudDesdeAdjuntos(
                "mensaje-2",
                "Nueva documentacion",
                "cliente@example.com",
                List.of(adjunto("expediente_completo.pdf"))
        );

        ArgumentCaptor<Solicitud> solicitudCaptor = ArgumentCaptor.forClass(Solicitud.class);
        verify(solicitudRepository).save(solicitudCaptor.capture());
        assertThat(solicitudCaptor.getValue().getMatricula()).isNull();
        verify(expedienteCompletoProcesamientoService)
                .iniciarSolicitud(eq(77L), any(MultipartFile.class), eq(false), eq(admin));

        ArgumentCaptor<CorreoEntranteProcesado> correoCaptor = ArgumentCaptor.forClass(CorreoEntranteProcesado.class);
        verify(procesadoRepository).save(correoCaptor.capture());
        assertThat(correoCaptor.getValue().getEstado()).isEqualTo("PROCESADO");
        assertThat(correoCaptor.getValue().getDetalle()).contains("pendiente de lectura documental");
    }

    @Test
    void detectaLaMatriculaEnCualquieraDeLosNombresAntesDeUnificar() {
        service.crearSolicitudDesdeAdjuntos(
                "mensaje-3",
                "Documentacion adjunta",
                "cliente@example.com",
                List.of(adjunto("contrato.pdf"), adjunto("permiso_9876XYZ.pdf"))
        );

        ArgumentCaptor<Solicitud> solicitudCaptor = ArgumentCaptor.forClass(Solicitud.class);
        verify(solicitudRepository).save(solicitudCaptor.capture());
        assertThat(solicitudCaptor.getValue().getMatricula()).isEqualTo("9876XYZ");
        verify(pdfSplitService).unirDocumentos(any());
    }

    private CorreoEntranteSolicitudService.AdjuntoPdf adjunto(String nombre) {
        return new CorreoEntranteSolicitudService.AdjuntoPdf(
                nombre,
                "contenido-pdf".getBytes(StandardCharsets.UTF_8)
        );
    }
}
