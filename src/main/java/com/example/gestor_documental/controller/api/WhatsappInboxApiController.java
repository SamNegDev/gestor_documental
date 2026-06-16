package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.whatsapp.WhatsappEventoAsociarRequest;
import com.example.gestor_documental.dto.whatsapp.WhatsappEventoResponse;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.security.CurrentUserService;
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
    private final ClienteRepository clienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final CurrentUserService currentUserService;

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
        requireAdmin(authentication);
        WhatsappWebhookEvento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento de WhatsApp no encontrado."));
        if (request == null || request.clienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un cliente.");
        }
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado."));
        evento.setCliente(cliente);
        if (request.expedienteId() != null) {
            Expediente expediente = expedienteRepository.findById(request.expedienteId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado."));
            if (expediente.getCliente() == null || !cliente.getId().equals(expediente.getCliente().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El expediente no pertenece al cliente seleccionado.");
            }
            evento.setExpediente(expediente);
        } else {
            evento.setExpediente(null);
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
                .procesado(evento.isProcesado())
                .errorProcesado(evento.getErrorProcesado())
                .fechaRecepcion(evento.getFechaRecepcion() != null ? evento.getFechaRecepcion().format(FORMAT) : null)
                .clienteId(evento.getCliente() != null ? evento.getCliente().getId() : null)
                .cliente(evento.getCliente() != null ? evento.getCliente().getNombre() : null)
                .expedienteId(evento.getExpediente() != null ? evento.getExpediente().getId() : null)
                .matricula(evento.getExpediente() != null ? evento.getExpediente().getMatricula() : null)
                .build();
    }

    private String estadoPermitido(String estado) {
        if ("ASOCIADOS".equals(estado) || "NO_ASOCIADOS".equals(estado) || "ERRORES".equals(estado)) {
            return estado;
        }
        return "TODOS";
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
}
