package com.example.gestor_documental.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.JustificanteProvisionalRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.WhatsappAdjuntoRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import com.example.gestor_documental.service.ExpedienteTipoTramitePolicyService;
import com.example.gestor_documental.service.impl.ExpedienteJustificanteFinalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
class TareaApiControllerTest {
    @Mock ExpedienteRepository expedienteRepository;
    @Mock SolicitudRepository solicitudRepository;
    @Mock DocumentoRepository documentoRepository;
    @Mock IncidenciaRepository incidenciaRepository;
    @Mock MensajeRepository mensajeRepository;
    @Mock RequisitoDocumentalExpedienteRepository requisitoRepository;
    @Mock WhatsappAdjuntoRepository whatsappAdjuntoRepository;
    @Mock WhatsappWebhookEventoRepository whatsappWebhookEventoRepository;
    @Mock CurrentUserService currentUserService;
    @Mock ConfiguracionSeguimientoService configuracionSeguimientoService;
    @Mock ExpedienteJustificanteFinalService justificanteFinalService;
    @Mock ExpedienteTipoTramitePolicyService tipoTramitePolicyService;
    @Mock JustificanteProvisionalRepository justificanteProvisionalRepository;
    @Mock Authentication authentication;
    @InjectMocks TareaApiController controller;

    @Test
    void agrupaLasTareasPorTrabajoYNoPorCanal() {
        assertThat(TareaApiController.perteneceGrupo("WHATSAPP_PENDIENTE_REVISION", "REVISION")).isTrue();
        assertThat(TareaApiController.perteneceGrupo("SOLICITUD_PENDIENTE_REVISION", "REVISION")).isTrue();
        assertThat(TareaApiController.perteneceGrupo("INCIDENCIA_PENDIENTE_NOTIFICAR", "AVISAR")).isTrue();
        assertThat(TareaApiController.perteneceGrupo("DOCUMENTACION_PENDIENTE_CLIENTE", "COMPLETAR")).isTrue();
        assertThat(TareaApiController.perteneceGrupo("EXPEDIENTE_ESTANCADO", "SEGUIMIENTO")).isTrue();
        assertThat(TareaApiController.perteneceGrupo("WHATSAPP_PENDIENTE_REVISION", "SEGUIMIENTO")).isFalse();
    }
    @Test
    void usuarioClienteSinClienteActivoNoRecibeTareasGlobales() {
        Usuario usuario = new Usuario("Cliente", "Sin contexto", "cliente@test.local", "secret", RolUsuario.CLIENTE, true);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);

        var resultado = controller.listar(null, null, null, null, null, 0, 25, authentication);

        assertThat(resultado.getContenido()).isEmpty();
        assertThat(resultado.getTotalElementos()).isZero();
        verifyNoInteractions(expedienteRepository, solicitudRepository, documentoRepository, incidenciaRepository,
                mensajeRepository, requisitoRepository, whatsappAdjuntoRepository, whatsappWebhookEventoRepository,
                configuracionSeguimientoService, justificanteFinalService, tipoTramitePolicyService,
                justificanteProvisionalRepository);
    }

    @Test
    void cargaSolicitudYClienteAlConsultarJustificantesPendientes() throws NoSuchMethodException {
        EntityGraph entityGraph = JustificanteProvisionalRepository.class
                .getMethod("findByEstadoInOrderBySolicitadoEnAsc", java.util.List.class)
                .getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(Arrays.asList(entityGraph.attributePaths()))
                .contains("solicitud", "solicitud.cliente");
    }
}
