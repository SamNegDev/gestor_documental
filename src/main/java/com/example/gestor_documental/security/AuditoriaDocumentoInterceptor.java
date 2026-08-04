package com.example.gestor_documental.security;

import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoContext;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.service.AuditoriaDocumentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuditoriaDocumentoInterceptor implements HandlerInterceptor {

    private static final String ATTR_CONTEXTO = AuditoriaDocumentoInterceptor.class.getName() + ".contexto";
    private static final String ATTR_ACCION = AuditoriaDocumentoInterceptor.class.getName() + ".accion";
    private static final String ATTR_DETALLE = AuditoriaDocumentoInterceptor.class.getName() + ".detalle";
    private static final Pattern HISTORIAL_EXPORT = Pattern.compile(
            "/api/(?:cliente/)?expedientes/(\\d+)/historial/exportar");
    private static final Pattern USUARIO_ID = Pattern.compile("/api/admin/usuarios/(\\d+)");
    private static final Pattern ADMINISTRADOR_CLIENTE = Pattern.compile(
            "/api/admin/clientes/(\\d+)/administradores(?:/(\\d+))?");

    private final DocumentoRepository documentoRepository;
    private final CurrentUserService currentUserService;
    private final AuditoriaDocumentoService auditoriaDocumentoService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        EventoInterceptado evento = resolverEvento(request);
        if (evento == null) {
            return true;
        }

        Usuario usuario = obtenerUsuarioActual();
        AuditoriaDocumentoContext contexto;
        if (evento.documentoId() != null) {
            contexto = documentoRepository.findByIdConRelaciones(evento.documentoId())
                    .map(documento -> auditoriaDocumentoService.crearContexto(documento, usuario, request))
                    .orElseGet(() -> auditoriaDocumentoService.crearContextoIntento(
                            evento.documentoId(), usuario, request));
        } else {
            contexto = auditoriaDocumentoService.crearContextoEvento(
                    evento.recursoTipo(),
                    evento.recursoId(),
                    evento.recursoNombre(),
                    evento.expedienteId(),
                    evento.solicitudId(),
                    evento.clienteId(),
                    usuario,
                    request);
        }
        request.setAttribute(ATTR_CONTEXTO, contexto);
        request.setAttribute(ATTR_ACCION, evento.accion());
        if (evento.detalle() != null) {
            request.setAttribute(ATTR_DETALLE, evento.detalle());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Object contexto = request.getAttribute(ATTR_CONTEXTO);
        Object accion = request.getAttribute(ATTR_ACCION);
        if (!(contexto instanceof AuditoriaDocumentoContext auditoriaContexto)
                || !(accion instanceof AccionAuditoriaDocumento accionAuditoria)) {
            return;
        }

        ResultadoAuditoriaDocumento resultado;
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED
                || response.getStatus() == HttpServletResponse.SC_FORBIDDEN) {
            resultado = ResultadoAuditoriaDocumento.DENEGADO;
        } else if (exception != null || response.getStatus() >= 400) {
            resultado = ResultadoAuditoriaDocumento.ERROR;
        } else {
            resultado = ResultadoAuditoriaDocumento.CORRECTO;
        }

        String detalle = (String) request.getAttribute(ATTR_DETALLE);
        if (exception != null) {
            detalle = unirDetalle(detalle, exception.getClass().getSimpleName());
        } else if (resultado != ResultadoAuditoriaDocumento.CORRECTO) {
            detalle = unirDetalle(detalle, "HTTP " + response.getStatus());
        }
        auditoriaDocumentoService.registrar(auditoriaContexto, accionAuditoria, resultado, detalle);
    }

    public static void anotarDetalle(HttpServletRequest request, String detalle) {
        if (request == null || detalle == null || detalle.isBlank()) {
            return;
        }
        String limpio = detalle.replace('\r', ' ').replace('\n', ' ').trim();
        if (limpio.length() > 1000) {
            limpio = limpio.substring(0, 1000);
        }
        Object actual = request.getAttribute(ATTR_DETALLE);
        String combinado = actual instanceof String texto && !texto.isBlank()
                ? texto + "; " + limpio
                : limpio;
        request.setAttribute(ATTR_DETALLE,
                combinado.length() <= 1000 ? combinado : combinado.substring(0, 1000));
    }
    private Usuario obtenerUsuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return currentUserService.requireUser(authentication);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private EventoInterceptado resolverEvento(HttpServletRequest request) {
        String metodo = request.getMethod();
        String ruta = request.getRequestURI();
        if ("GET".equals(metodo) && ruta.matches("/documentos/ver/\\d+")) {
            return eventoDocumento(ruta, AccionAuditoriaDocumento.VISUALIZAR, null);
        }
        if ("GET".equals(metodo) && ruta.matches("/documentos/descargar/\\d+")) {
            return eventoDocumento(ruta, AccionAuditoriaDocumento.DESCARGAR, null);
        }
        if ("DELETE".equals(metodo) && ruta.matches("/api/documentos/\\d+")) {
            return eventoDocumento(ruta, AccionAuditoriaDocumento.ELIMINAR, null);
        }
        if ("PATCH".equals(metodo) && ruta.matches("/api/documentos/\\d+/paginas")) {
            String[] segmentos = ruta.split("/");
            return eventoDocumento(
                    Long.parseLong(segmentos[3]),
                    AccionAuditoriaDocumento.ELIMINAR_PAGINAS,
                    detallePaginas(request.getParameter("rangoPaginas")));
        }

        Matcher historial = HISTORIAL_EXPORT.matcher(ruta);
        if ("GET".equals(metodo) && historial.matches()) {
            Long expedienteId = Long.parseLong(historial.group(1));
            return new EventoInterceptado(
                    null, "EXPEDIENTE", expedienteId, "EXP-" + expedienteId,
                    expedienteId, null, null,
                    AccionAuditoriaDocumento.EXPORTAR_HISTORIAL,
                    detalleFiltrosHistorial(request));
        }
        if ("POST".equals(metodo)
                && "/api/admin/ia/extraccion-ga/revisiones/exportar".equals(ruta)) {
            return new EventoInterceptado(
                    null, "EXPORTACION_GA", null, "Lote FORMATO_GA", null, null, null,
                    AccionAuditoriaDocumento.EXPORTAR_GA, "Exportacion de revisiones preparadas");
        }

        EventoInterceptado usuario = eventoUsuario(metodo, ruta);
        if (usuario != null) {
            return usuario;
        }
        return eventoAdministradorCliente(metodo, ruta);
    }

    private EventoInterceptado eventoUsuario(String metodo, String ruta) {
        AccionAuditoriaDocumento accion;
        Long usuarioId = null;
        if ("POST".equals(metodo) && "/api/admin/usuarios".equals(ruta)) {
            accion = AccionAuditoriaDocumento.USUARIO_CREAR;
        } else {
            Matcher matcher = USUARIO_ID.matcher(ruta);
            if (!matcher.matches()) return null;
            usuarioId = Long.parseLong(matcher.group(1));
            if ("PUT".equals(metodo)) accion = AccionAuditoriaDocumento.USUARIO_ACTUALIZAR;
            else if ("DELETE".equals(metodo)) accion = AccionAuditoriaDocumento.USUARIO_ELIMINAR;
            else return null;
        }
        return new EventoInterceptado(
                null, "USUARIO", usuarioId,
                usuarioId != null ? "Usuario " + usuarioId : "Nuevo usuario",
                null, null, null, accion, null);
    }

    private EventoInterceptado eventoAdministradorCliente(String metodo, String ruta) {
        Matcher matcher = ADMINISTRADOR_CLIENTE.matcher(ruta);
        if (!matcher.matches()) return null;
        Long clienteId = Long.parseLong(matcher.group(1));
        Long interesadoId = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : null;
        AccionAuditoriaDocumento accion;
        if ("POST".equals(metodo) && interesadoId == null) {
            accion = AccionAuditoriaDocumento.ADMINISTRADOR_CLIENTE_CREAR;
        } else if ("PUT".equals(metodo) && interesadoId != null) {
            accion = AccionAuditoriaDocumento.ADMINISTRADOR_CLIENTE_ACTUALIZAR;
        } else if ("DELETE".equals(metodo) && interesadoId != null) {
            accion = AccionAuditoriaDocumento.ADMINISTRADOR_CLIENTE_DESVINCULAR;
        } else {
            return null;
        }
        return new EventoInterceptado(
                null, "ADMINISTRADOR_CLIENTE", interesadoId,
                interesadoId != null ? "Interesado " + interesadoId : "Nuevo administrador",
                null, null, clienteId, accion, "Cliente " + clienteId);
    }

    private EventoInterceptado eventoDocumento(
            String ruta,
            AccionAuditoriaDocumento accion,
            String detalle
    ) {
        String[] segmentos = ruta.split("/");
        return eventoDocumento(Long.parseLong(segmentos[segmentos.length - 1]), accion, detalle);
    }

    private EventoInterceptado eventoDocumento(
            Long documentoId,
            AccionAuditoriaDocumento accion,
            String detalle
    ) {
        return new EventoInterceptado(
                documentoId, "DOCUMENTO", documentoId, null,
                null, null, null, accion, detalle);
    }

    private String detallePaginas(String rangoPaginas) {
        if (rangoPaginas == null || rangoPaginas.isBlank()) {
            return null;
        }
        String limpio = rangoPaginas.replace('\r', ' ').replace('\n', ' ').trim();
        return "Rango solicitado: " + (limpio.length() <= 100 ? limpio : limpio.substring(0, 100));
    }

    private String detalleFiltrosHistorial(HttpServletRequest request) {
        StringJoiner detalle = new StringJoiner("; ");
        agregarParametro(detalle, "Categoria", request.getParameter("categoria"));
        agregarParametro(detalle, "Desde", request.getParameter("desde"));
        agregarParametro(detalle, "Hasta", request.getParameter("hasta"));
        return detalle.length() > 0 ? detalle.toString() : "Sin filtros";
    }

    private void agregarParametro(StringJoiner detalle, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) {
            String limpio = valor.replace('\r', ' ').replace('\n', ' ').trim();
            detalle.add(etiqueta + ": " + (limpio.length() <= 100 ? limpio : limpio.substring(0, 100)));
        }
    }

    private String unirDetalle(String actual, String adicional) {
        return actual == null || actual.isBlank() ? adicional : actual + "; " + adicional;
    }

    private record EventoInterceptado(
            Long documentoId,
            String recursoTipo,
            Long recursoId,
            String recursoNombre,
            Long expedienteId,
            Long solicitudId,
            Long clienteId,
            AccionAuditoriaDocumento accion,
            String detalle
    ) {
    }
}
