package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.whatsapp.WhatsappAdjuntoClasificarRequest;
import com.example.gestor_documental.dto.whatsapp.WhatsappAdjuntoResponse;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.whatsapp.WhatsappEventoAsociarRequest;
import com.example.gestor_documental.dto.whatsapp.WhatsappEventoResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.WhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.repository.WhatsappAdjuntoRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.HistorialCambioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsappInboxApiController {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final WhatsappWebhookEventoRepository eventoRepository;
    private final WhatsappAdjuntoRepository adjuntoRepository;
    private final DocumentoRepository documentoRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final CurrentUserService currentUserService;
    private final HistorialCambioService historialCambioService;

    @GetMapping("/eventos")
    public PagedResponse<WhatsappEventoResponse> listar(@RequestParam(defaultValue = "TODOS") String estado,
            @RequestParam(required = false) String telefono,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanio,
            Authentication authentication) {
        requireAdmin(authentication);
        String filtroEstado = estadoPermitido(estado);
        String filtroTelefono = StringUtils.hasText(telefono) ? "%" + normalizarTelefono(telefono) + "%" : null;
        return PagedResponse.of(eventoRepository.buscarBandeja(filtroEstado, filtroTelefono,
                PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(tamanio, 100)), Sort.by(Sort.Direction.DESC, "fechaRecepcion")))
                .map(this::map));
    }

    @PostMapping("/eventos/{id}/asociar")
    @Transactional
    public WhatsappEventoResponse asociar(@PathVariable Long id, @RequestBody WhatsappEventoAsociarRequest request,
            Authentication authentication) {
        Usuario admin = requireAdmin(authentication);
        WhatsappWebhookEvento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento de WhatsApp no encontrado."));
        if (request == null || (request.clienteId() == null && request.expedienteId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un cliente o un expediente.");
        }
        if (request.expedienteId() != null) {
            Expediente expediente = expedienteRepository.findById(request.expedienteId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado."));
            if (expediente.getCliente() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El expediente no tiene cliente asociado.");
            }
            if (request.clienteId() != null && !request.clienteId().equals(expediente.getCliente().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El cliente seleccionado no coincide con el cliente real del expediente.");
            }
            evento.setCliente(expediente.getCliente());
            evento.setExpediente(expediente);
            asociarAdjuntosDelEvento(evento, expediente.getCliente(), expediente);
            historialCambioService.registrarCambioExpediente(expediente, admin, "WHATSAPP ASOCIADO",
                    "Mensaje de WhatsApp asociado al expediente desde el telefono " + nullSafe(evento.getTelefono()) + ".");
        } else {
            Cliente cliente = clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado."));
            evento.setCliente(cliente);
            evento.setExpediente(null);
            asociarAdjuntosDelEvento(evento, cliente, null);
        }
        return map(eventoRepository.save(evento));
    }

    @GetMapping("/adjuntos")
    public PagedResponse<WhatsappAdjuntoResponse> listarAdjuntos(@RequestParam(defaultValue = "PENDIENTE_CLASIFICAR") String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio,
            Authentication authentication) {
        requireAdmin(authentication);
        EstadoWhatsappAdjunto filtro = estadoAdjunto(estado);
        return PagedResponse.of(adjuntoRepository.buscarBandeja(filtro,
                PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(tamanio, 100)), Sort.by(Sort.Direction.DESC, "fechaRecepcion")))
                .map(this::mapAdjunto));
    }

    @PostMapping("/adjuntos/{id}/clasificar")
    @Transactional
    public WhatsappAdjuntoResponse clasificarAdjunto(@PathVariable Long id,
            @RequestBody WhatsappAdjuntoClasificarRequest request,
            Authentication authentication) {
        Usuario admin = requireAdmin(authentication);
        WhatsappAdjunto adjunto = adjuntoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto de WhatsApp no encontrado."));
        if (!StringUtils.hasText(adjunto.getNombreArchivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El adjunto no tiene archivo descargado. Revisa el error de descarga antes de clasificarlo.");
        }
        if (request == null || request.expedienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona el expediente destino.");
        }
        Expediente expediente = expedienteRepository.findById(request.expedienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado."));
        TipoDocumento tipoDocumento = request.tipoDocumento() != null ? request.tipoDocumento() : TipoDocumento.OTROS;

        Documento documento = new Documento();
        documento.setExpediente(expediente);
        documento.setCliente(expediente.getCliente());
        documento.setTipoDocumento(tipoDocumento);
        documento.setNombreArchivo(adjunto.getNombreArchivo());
        documento.setNombreArchivoOriginal(StringUtils.hasText(adjunto.getNombreArchivoOriginal())
                ? adjunto.getNombreArchivoOriginal()
                : "Documento recibido por WhatsApp");
        documento.setDescripcionArchivo("Recibido por WhatsApp desde " + nullSafe(adjunto.getTelefono()) + ".");
        documento.setSubidoPor(usuarioCliente(expediente.getCliente()));
        documentoRepository.save(documento);
        marcarRequisitoAportado(expediente, tipoDocumento, documento);

        adjunto.setCliente(expediente.getCliente());
        adjunto.setExpediente(expediente);
        adjunto.setEstado(EstadoWhatsappAdjunto.CLASIFICADO);
        adjuntoRepository.save(adjunto);

        if (adjunto.getEvento() != null) {
            WhatsappWebhookEvento evento = adjunto.getEvento();
            evento.setCliente(expediente.getCliente());
            evento.setExpediente(expediente);
            eventoRepository.save(evento);
        }
        historialCambioService.registrarCambioExpediente(expediente, admin, "WHATSAPP DOCUMENTO",
                "Adjunto de WhatsApp clasificado como " + tipoDocumento.name().replace('_', ' ')
                        + ": " + documento.getNombreArchivoOriginal() + ".");
        return mapAdjunto(adjunto);
    }

    @PostMapping("/adjuntos/{id}/descartar")
    @Transactional
    public WhatsappAdjuntoResponse descartarAdjunto(@PathVariable Long id, Authentication authentication) {
        requireAdmin(authentication);
        WhatsappAdjunto adjunto = adjuntoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto de WhatsApp no encontrado."));
        adjunto.setEstado(EstadoWhatsappAdjunto.DESCARTADO);
        return mapAdjunto(adjuntoRepository.save(adjunto));
    }

    @PostMapping("/eventos/{id}/revisar")
    @Transactional
    public WhatsappEventoResponse revisar(@PathVariable Long id, Authentication authentication) {
        Usuario admin = requireAdmin(authentication);
        WhatsappWebhookEvento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento de WhatsApp no encontrado."));
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(java.time.LocalDateTime.now());
        evento.setRevisadoPor(admin);
        if (evento.getExpediente() != null) {
            historialCambioService.registrarCambioExpediente(evento.getExpediente(), admin, "WHATSAPP REVISADO",
                    "Mensaje de WhatsApp revisado: " + limitar(evento.getTexto()));
        }
        return map(eventoRepository.save(evento));
    }

    @PostMapping("/eventos/{id}/archivar")
    @Transactional
    public WhatsappEventoResponse archivar(@PathVariable Long id, Authentication authentication) {
        Usuario admin = requireAdmin(authentication);
        WhatsappWebhookEvento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento de WhatsApp no encontrado."));
        evento.setEstado(EstadoWhatsappEvento.ARCHIVADO);
        evento.setFechaRevision(java.time.LocalDateTime.now());
        evento.setRevisadoPor(admin);
        if (evento.getExpediente() != null) {
            historialCambioService.registrarCambioExpediente(evento.getExpediente(), admin, "WHATSAPP ARCHIVADO",
                    "Mensaje de WhatsApp archivado: " + limitar(evento.getTexto()));
        }
        return map(eventoRepository.save(evento));
    }

    private WhatsappEventoResponse map(WhatsappWebhookEvento evento) {
        return WhatsappEventoResponse.builder()
                .id(evento.getId())
                .messageId(evento.getMessageId())
                .telefono(evento.getTelefono())
                .nombrePerfil(evento.getNombrePerfil())
                .tipo(evento.getTipo())
                .texto(evento.getTexto())
                .accionCodigo(evento.getAccionCodigo())
                .procesado(evento.isProcesado())
                .estado(evento.getEstado() != null ? evento.getEstado().name() : null)
                .errorProcesado(evento.getErrorProcesado())
                .fechaRecepcion(evento.getFechaRecepcion() != null ? evento.getFechaRecepcion().format(FORMAT) : null)
                .fechaRevision(evento.getFechaRevision() != null ? evento.getFechaRevision().format(FORMAT) : null)
                .revisadoPor(evento.getRevisadoPor() != null ? nombreUsuario(evento.getRevisadoPor()) : null)
                .clienteId(evento.getCliente() != null ? evento.getCliente().getId() : null)
                .cliente(evento.getCliente() != null ? evento.getCliente().getNombre() : null)
                .expedienteId(evento.getExpediente() != null ? evento.getExpediente().getId() : null)
                .matricula(evento.getExpediente() != null ? evento.getExpediente().getMatricula() : null)
                .build();
    }

    private WhatsappAdjuntoResponse mapAdjunto(WhatsappAdjunto adjunto) {
        return WhatsappAdjuntoResponse.builder()
                .id(adjunto.getId())
                .telefono(adjunto.getTelefono())
                .tipo(adjunto.getTipo())
                .mimeType(adjunto.getMimeType())
                .nombreArchivoOriginal(adjunto.getNombreArchivoOriginal())
                .tamanioBytes(adjunto.getTamanioBytes())
                .estado(adjunto.getEstado() != null ? adjunto.getEstado().name() : null)
                .errorDescarga(adjunto.getErrorDescarga())
                .fechaRecepcion(adjunto.getFechaRecepcion() != null ? adjunto.getFechaRecepcion().format(FORMAT) : null)
                .clienteId(adjunto.getCliente() != null ? adjunto.getCliente().getId() : null)
                .cliente(adjunto.getCliente() != null ? adjunto.getCliente().getNombre() : null)
                .expedienteId(adjunto.getExpediente() != null ? adjunto.getExpediente().getId() : null)
                .matricula(adjunto.getExpediente() != null ? adjunto.getExpediente().getMatricula() : null)
                .eventoId(adjunto.getEvento() != null ? adjunto.getEvento().getId() : null)
                .build();
    }

    private void asociarAdjuntosDelEvento(WhatsappWebhookEvento evento, Cliente cliente, Expediente expediente) {
        if (evento == null || evento.getId() == null) {
            return;
        }
        adjuntoRepository.findByEventoId(evento.getId()).forEach(adjunto -> {
            adjunto.setCliente(cliente);
            adjunto.setExpediente(expediente);
            adjuntoRepository.save(adjunto);
        });
    }

    private void marcarRequisitoAportado(Expediente expediente, TipoDocumento tipoDocumento, Documento documento) {
        requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .filter(requisito -> requisito.getTipoDocumento() == tipoDocumento)
                .findFirst()
                .ifPresent(requisito -> {
                    requisito.setDocumento(documento);
                    requisito.setEstado(EstadoRequisitoDocumental.APORTADO);
                    requisitoRepository.save(requisito);
                });
    }

    private Usuario usuarioCliente(Cliente cliente) {
        if (cliente == null || cliente.getId() == null) {
            return null;
        }
        return usuarioRepository.findFirstByClienteIdAndRolUsuarioAndActivoTrueOrderByIdAsc(cliente.getId(), RolUsuario.CLIENTE)
                .orElse(null);
    }

    private String estadoPermitido(String estado) {
        if ("PENDIENTES".equals(estado) || "REVISADOS".equals(estado) || "ARCHIVADOS".equals(estado)
                || "ASOCIADOS".equals(estado) || "NO_ASOCIADOS".equals(estado) || "ERRORES".equals(estado)) {
            return estado;
        }
        return "TODOS";
    }

    private EstadoWhatsappAdjunto estadoAdjunto(String estado) {
        if (!StringUtils.hasText(estado) || "TODOS".equals(estado)) {
            return null;
        }
        try {
            return EstadoWhatsappAdjunto.valueOf(estado);
        } catch (IllegalArgumentException exception) {
            return EstadoWhatsappAdjunto.PENDIENTE_CLASIFICAR;
        }
    }

    private String normalizarTelefono(String value) {
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.startsWith("00")) normalized = normalized.substring(2);
        if (normalized.startsWith("34") && normalized.length() == 11) normalized = normalized.substring(2);
        return normalized;
    }

    private Usuario requireAdmin(Authentication auth) {
        return currentUserService.requireAdmin(auth);
    }

    private String limitar(String valor) {
        if (!StringUtils.hasText(valor)) return "Sin texto visible.";
        String limpio = valor.trim().replaceAll("\\s+", " ");
        return limpio.length() <= 180 ? limpio : limpio.substring(0, 177) + "...";
    }

    private String nullSafe(String valor) {
        return StringUtils.hasText(valor) ? valor : "sin telefono";
    }

    private String nombreUsuario(Usuario usuario) {
        String nombre = ((usuario.getNombre() != null ? usuario.getNombre() : "") + " "
                + (usuario.getApellidos() != null ? usuario.getApellidos() : "")).trim();
        return !nombre.isBlank() ? nombre : usuario.getEmail();
    }
}
