package com.example.gestor_documental.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
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
    @Mock Authentication authentication;
    @InjectMocks TareaApiController controller;

    @Test
    void usuarioClienteSinClienteActivoNoRecibeTareasGlobales() {
        Usuario usuario = new Usuario("Cliente", "Sin contexto", "cliente@test.local", "secret", RolUsuario.CLIENTE, true);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);

        var resultado = controller.listar(null, null, null, null, 0, 25, authentication);

        assertThat(resultado.getContenido()).isEmpty();
        assertThat(resultado.getTotalElementos()).isZero();
        verifyNoInteractions(expedienteRepository, solicitudRepository, documentoRepository, incidenciaRepository,
                mensajeRepository, requisitoRepository, whatsappAdjuntoRepository, whatsappWebhookEventoRepository,
                configuracionSeguimientoService, justificanteFinalService, tipoTramitePolicyService);
    }
}
