package com.example.gestor_documental.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoResponse;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.AuditoriaDocumentoService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminAuditoriaDocumentoApiControllerTest {

    @Mock AuditoriaDocumentoService auditoriaDocumentoService;
    @Mock CurrentUserService currentUserService;
    @Mock Authentication authentication;
    @InjectMocks AdminAuditoriaDocumentoApiController controller;

    @Test
    void exigeAdministradorYDelegaLaConsultaPaginada() {
        Usuario admin = new Usuario("Ada", "Lovelace", "ada@example.test", "secret", RolUsuario.ADMIN, true);
        PagedResponse<AuditoriaDocumentoResponse> pagina = PagedResponse.<AuditoriaDocumentoResponse>builder()
                .contenido(List.of()).pagina(0).tamanio(25).totalElementos(0).totalPaginas(0).build();
        when(currentUserService.requireAdmin(authentication)).thenReturn(admin);
        when(auditoriaDocumentoService.listar(
                AccionAuditoriaDocumento.EXPORTAR_HISTORIAL,
                ResultadoAuditoriaDocumento.CORRECTO,
                "EXPEDIENTE", 12L, 3L, 12L, null, 1L,
                null, null, 0, 25)).thenReturn(pagina);

        PagedResponse<AuditoriaDocumentoResponse> resultado = controller.listar(
                AccionAuditoriaDocumento.EXPORTAR_HISTORIAL,
                ResultadoAuditoriaDocumento.CORRECTO,
                "EXPEDIENTE", 12L, 3L, 12L, null, 1L,
                null, null, 0, 25, authentication);

        assertThat(resultado).isSameAs(pagina);
        verify(currentUserService).requireAdmin(authentication);
    }

    @Test
    void publicaCatalogosDeEventosSoloParaAdministradores() {
        Usuario admin = new Usuario("Ada", "Lovelace", "ada@example.test", "secret", RolUsuario.ADMIN, true);
        when(currentUserService.requireAdmin(authentication)).thenReturn(admin);

        Map<String, List<String>> catalogos = controller.catalogos(authentication);

        assertThat(catalogos.get("acciones")).contains("DESCARGAR", "EXPORTAR_HISTORIAL", "USUARIO_ACTUALIZAR");
        assertThat(catalogos.get("recursos")).contains("DOCUMENTO", "EXPEDIENTE", "USUARIO");
        verify(currentUserService).requireAdmin(authentication);
    }
}
