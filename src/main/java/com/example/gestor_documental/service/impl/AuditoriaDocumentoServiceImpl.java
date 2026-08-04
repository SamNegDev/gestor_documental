package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoContext;
import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoResponse;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.model.AuditoriaDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AuditoriaDocumentoRepository;
import com.example.gestor_documental.service.AuditoriaDocumentoService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaDocumentoServiceImpl implements AuditoriaDocumentoService {

    private final AuditoriaDocumentoRepository auditoriaDocumentoRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public AuditoriaDocumentoContext crearContexto(Documento documento, Usuario usuario, HttpServletRequest request) {
        Long expedienteId = documento.getExpediente() != null ? documento.getExpediente().getId() : null;
        Long solicitudId = documento.getSolicitud() != null ? documento.getSolicitud().getId() : null;
        Cliente cliente = documento.getCliente();
        if (cliente == null && documento.getExpediente() != null) {
            cliente = documento.getExpediente().getCliente();
        }
        if (cliente == null && documento.getSolicitud() != null) {
            cliente = documento.getSolicitud().getCliente();
        }
        return contexto(
                documento.getId(),
                limitar(documento.getNombreArchivoOriginal(), 200),
                documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : null,
                expedienteId,
                solicitudId,
                cliente != null ? cliente.getId() : null,
                usuario,
                request,
                "DOCUMENTO",
                documento.getId(),
                limitar(documento.getNombreArchivoOriginal(), 200));
    }

    @Override
    public AuditoriaDocumentoContext crearContextoIntento(Long documentoId, Usuario usuario, HttpServletRequest request) {
        return contexto(
                documentoId, null, null, null, null, null, usuario, request,
                "DOCUMENTO", documentoId, null);
    }

    @Override
    public AuditoriaDocumentoContext crearContextoEvento(
            String recursoTipo,
            Long recursoId,
            String recursoNombre,
            Long expedienteId,
            Long solicitudId,
            Long clienteId,
            Usuario usuario,
            HttpServletRequest request
    ) {
        return contexto(
                null, null, null, expedienteId, solicitudId, clienteId, usuario, request,
                limitar(recursoTipo, 40), recursoId, limitar(recursoNombre, 200));
    }

    private AuditoriaDocumentoContext contexto(
            Long documentoId,
            String documentoNombre,
            String documentoTipo,
            Long expedienteId,
            Long solicitudId,
            Long clienteId,
            Usuario usuario,
            HttpServletRequest request,
            String recursoTipo,
            Long recursoId,
            String recursoNombre
    ) {
        return new AuditoriaDocumentoContext(
                documentoId,
                documentoNombre,
                documentoTipo,
                expedienteId,
                solicitudId,
                clienteId,
                usuario != null ? usuario.getId() : null,
                usuario != null ? limitar(usuario.getEmail(), 150) : null,
                usuario != null && usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null,
                request != null ? limitar(request.getRemoteAddr(), 64) : null,
                request != null ? limitar(request.getHeader("User-Agent"), 500) : null,
                recursoTipo,
                recursoId,
                recursoNombre,
                request != null ? limitar(request.getMethod(), 10) : null,
                request != null ? limitar(request.getRequestURI(), 300) : null);
    }

    @Override
    public void registrar(AuditoriaDocumentoContext contexto, AccionAuditoriaDocumento accion,
                          ResultadoAuditoriaDocumento resultado, String detalle) {
        if (contexto == null || accion == null || resultado == null) {
            return;
        }
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status ->
                    auditoriaDocumentoRepository.save(crearEvento(contexto, accion, resultado, detalle)));
        } catch (RuntimeException exception) {
            log.warn("No se pudo guardar la auditoria para recurso {}:{} y accion {}",
                    contexto.recursoTipo(), contexto.recursoId(), accion, exception);
        }
    }

    @Override
    public PagedResponse<AuditoriaDocumentoResponse> listar(
            AccionAuditoriaDocumento accion,
            ResultadoAuditoriaDocumento resultado,
            String recursoTipo,
            Long recursoId,
            Long clienteId,
            Long expedienteId,
            Long documentoId,
            Long usuarioId,
            LocalDateTime desde,
            LocalDateTime hasta,
            int pagina,
            int tamanio
    ) {
        Specification<AuditoriaDocumento> filtros = (root, query, cb) -> cb.conjunction();
        if (accion != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("accion"), accion));
        if (resultado != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("resultado"), resultado));
        if (recursoTipo != null && !recursoTipo.isBlank()) {
            String tipo = recursoTipo.trim().toUpperCase(java.util.Locale.ROOT);
            filtros = filtros.and((root, query, cb) -> cb.equal(root.get("recursoTipo"), tipo));
        }
        if (recursoId != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("recursoId"), recursoId));
        if (clienteId != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        if (expedienteId != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("expedienteId"), expedienteId));
        if (documentoId != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("documentoId"), documentoId));
        if (usuarioId != null) filtros = filtros.and((root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId));
        if (desde != null) filtros = filtros.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaEvento"), desde));
        if (hasta != null) filtros = filtros.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaEvento"), hasta));

        PageRequest pageable = PageRequest.of(
                Math.max(0, pagina),
                Math.max(1, Math.min(tamanio, 100)),
                Sort.by(Sort.Order.desc("fechaEvento"), Sort.Order.desc("id")));
        Page<AuditoriaDocumentoResponse> resultadoPagina = auditoriaDocumentoRepository.findAll(filtros, pageable)
                .map(this::mapear);
        return PagedResponse.of(resultadoPagina);
    }

    private AuditoriaDocumento crearEvento(
            AuditoriaDocumentoContext contexto,
            AccionAuditoriaDocumento accion,
            ResultadoAuditoriaDocumento resultado,
            String detalle
    ) {
        AuditoriaDocumento evento = new AuditoriaDocumento();
        evento.setAccion(accion);
        evento.setResultado(resultado);
        evento.setRecursoTipo(contexto.recursoTipo());
        evento.setRecursoId(contexto.recursoId());
        evento.setRecursoNombre(contexto.recursoNombre());
        evento.setDocumentoId(contexto.documentoId());
        evento.setDocumentoNombre(contexto.documentoNombre());
        evento.setDocumentoTipo(contexto.documentoTipo());
        evento.setExpedienteId(contexto.expedienteId());
        evento.setSolicitudId(contexto.solicitudId());
        evento.setClienteId(contexto.clienteId());
        evento.setUsuarioId(contexto.usuarioId());
        evento.setUsuarioEmail(contexto.usuarioEmail());
        evento.setUsuarioRol(contexto.usuarioRol());
        evento.setDireccionIp(contexto.direccionIp());
        evento.setAgenteUsuario(contexto.agenteUsuario());
        evento.setMetodoHttp(contexto.metodoHttp());
        evento.setRuta(contexto.ruta());
        evento.setDetalle(limitar(detalle, 1000));
        return evento;
    }

    private AuditoriaDocumentoResponse mapear(AuditoriaDocumento evento) {
        return AuditoriaDocumentoResponse.builder()
                .id(evento.getId())
                .fechaEvento(evento.getFechaEvento() != null ? evento.getFechaEvento().toString() : null)
                .accion(evento.getAccion().name())
                .resultado(evento.getResultado().name())
                .recursoTipo(evento.getRecursoTipo())
                .recursoId(evento.getRecursoId())
                .recursoNombre(evento.getRecursoNombre())
                .documentoId(evento.getDocumentoId())
                .documentoNombre(evento.getDocumentoNombre())
                .documentoTipo(evento.getDocumentoTipo())
                .expedienteId(evento.getExpedienteId())
                .solicitudId(evento.getSolicitudId())
                .clienteId(evento.getClienteId())
                .usuarioId(evento.getUsuarioId())
                .usuarioEmail(evento.getUsuarioEmail())
                .usuarioRol(evento.getUsuarioRol())
                .direccionIp(evento.getDireccionIp())
                .agenteUsuario(evento.getAgenteUsuario())
                .metodoHttp(evento.getMetodoHttp())
                .ruta(evento.getRuta())
                .detalle(evento.getDetalle())
                .build();
    }

    private static String limitar(String valor, int longitudMaxima) {
        if (valor == null) return null;
        String limpio = valor.replace('\r', ' ').replace('\n', ' ').trim();
        return limpio.length() <= longitudMaxima ? limpio : limpio.substring(0, longitudMaxima);
    }
}
