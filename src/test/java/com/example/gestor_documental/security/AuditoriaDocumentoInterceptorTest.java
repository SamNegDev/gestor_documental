package com.example.gestor_documental.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoContext;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.service.AuditoriaDocumentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditoriaDocumentoInterceptorTest {

    @Mock DocumentoRepository documentoRepository;
    @Mock CurrentUserService currentUserService;
    @Mock AuditoriaDocumentoService auditoriaDocumentoService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    private AuditoriaDocumentoInterceptor interceptor;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        interceptor = new AuditoriaDocumentoInterceptor(
                documentoRepository, currentUserService, auditoriaDocumentoService);
        Map<String, Object> atributos = new HashMap<>();
        doAnswer(invocacion -> {
            atributos.put(invocacion.getArgument(0), invocacion.getArgument(1));
            return null;
        }).when(request).setAttribute(anyString(), any());
        when(request.getAttribute(anyString())).thenAnswer(invocacion -> atributos.get(invocacion.getArgument(0)));
        usuario = new Usuario("Ada", "Lovelace", "ada@example.test", "secret", RolUsuario.ADMIN, true);
        Authentication authentication = new UsernamePasswordAuthenticationToken("ada@example.test", "secret", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraUnaDescargaCorrectaFueraDelHistorialFuncional() throws Exception {
        Documento documento = new Documento();
        documento.setId(44L);
        AuditoriaDocumentoContext contexto = contexto(44L);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/documentos/descargar/44");
        when(documentoRepository.findByIdConRelaciones(44L)).thenReturn(Optional.of(documento));
        when(auditoriaDocumentoService.crearContexto(documento, usuario, request)).thenReturn(contexto);
        when(response.getStatus()).thenReturn(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditoriaDocumentoService).registrar(
                contexto,
                AccionAuditoriaDocumento.DESCARGAR,
                ResultadoAuditoriaDocumento.CORRECTO,
                null);
    }

    @Test
    void conservaComoDenegadoUnIntentoDeEliminacion() throws Exception {
        AuditoriaDocumentoContext contexto = contexto(72L);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/documentos/72");
        when(documentoRepository.findByIdConRelaciones(72L)).thenReturn(Optional.empty());
        when(auditoriaDocumentoService.crearContextoIntento(72L, usuario, request)).thenReturn(contexto);
        when(response.getStatus()).thenReturn(403);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditoriaDocumentoService).registrar(
                contexto,
                AccionAuditoriaDocumento.ELIMINAR,
                ResultadoAuditoriaDocumento.DENEGADO,
                "HTTP 403");
    }

    @Test
    void registraElRangoSoloEnLaAuditoriaInterna() throws Exception {
        Documento documento = new Documento();
        documento.setId(91L);
        AuditoriaDocumentoContext contexto = contexto(91L);
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getRequestURI()).thenReturn("/api/documentos/91/paginas");
        when(request.getParameter("rangoPaginas")).thenReturn("2-4");
        when(documentoRepository.findByIdConRelaciones(91L)).thenReturn(Optional.of(documento));
        when(auditoriaDocumentoService.crearContexto(documento, usuario, request)).thenReturn(contexto);
        when(response.getStatus()).thenReturn(204);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditoriaDocumentoService).registrar(
                eq(contexto),
                eq(AccionAuditoriaDocumento.ELIMINAR_PAGINAS),
                eq(ResultadoAuditoriaDocumento.CORRECTO),
                eq("Rango solicitado: 2-4"));
    }

    @Test
    void auditaUnaExportacionDeHistorialConSusFiltros() throws Exception {
        AuditoriaDocumentoContext contexto = contextoEvento("EXPEDIENTE", 12L, 12L, null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/expedientes/12/historial/exportar");
        when(request.getParameter("categoria")).thenReturn("ESTADO");
        when(auditoriaDocumentoService.crearContextoEvento(
                "EXPEDIENTE", 12L, "EXP-12", 12L, null, null, usuario, request)).thenReturn(contexto);
        when(response.getStatus()).thenReturn(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditoriaDocumentoService).registrar(
                contexto,
                AccionAuditoriaDocumento.EXPORTAR_HISTORIAL,
                ResultadoAuditoriaDocumento.CORRECTO,
                "Categoria: ESTADO");
    }

    @Test
    void auditaUnCambioDeUsuarioComoEventoSensible() throws Exception {
        AuditoriaDocumentoContext contexto = contextoEvento("USUARIO", 27L, null, null);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/api/admin/usuarios/27");
        when(auditoriaDocumentoService.crearContextoEvento(
                "USUARIO", 27L, "Usuario 27", null, null, null, usuario, request)).thenReturn(contexto);
        when(response.getStatus()).thenReturn(204);

        interceptor.preHandle(request, response, new Object());
        AuditoriaDocumentoInterceptor.anotarDetalle(request, "Rol: CLIENTE -> ADMIN");
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditoriaDocumentoService).registrar(
                contexto,
                AccionAuditoriaDocumento.USUARIO_ACTUALIZAR,
                ResultadoAuditoriaDocumento.CORRECTO,
                "Rol: CLIENTE -> ADMIN");
    }

    private AuditoriaDocumentoContext contextoEvento(
            String recursoTipo,
            Long recursoId,
            Long expedienteId,
            Long clienteId
    ) {
        return new AuditoriaDocumentoContext(
                null, null, null, expedienteId, null, clienteId,
                1L, "ada@example.test", "ADMIN", "127.0.0.1", "test",
                recursoTipo, recursoId, null, "GET", "/ruta");
    }
    private AuditoriaDocumentoContext contexto(Long documentoId) {
        return new AuditoriaDocumentoContext(
                documentoId, "documento.pdf", "OTRO", 9L, null, 3L,
                1L, "ada@example.test", "ADMIN", "127.0.0.1", "test");
    }
}
