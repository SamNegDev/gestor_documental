package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.ExpedienteClienteResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cliente/expedientes")
@RequiredArgsConstructor
public class ClienteExpedienteApiController {

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final ExpedienteService expedienteService;
    private final MensajeService mensajeService;
    private final UsuarioService usuarioService;

    @GetMapping("/{id}")
    public ExpedienteClienteResponse obtenerDetalleCliente(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        ExpedienteDetailResponse detalle = expedienteDetalleApiService.obtenerDetalle(id, usuarioLogueado);

        return ExpedienteClienteResponse.builder()
                .id(detalle.getId())
                .referencia(detalle.getReferencia())
                .matricula(detalle.getMatricula())
                .tipoTramiteDescripcion(detalle.getTipoTramiteDescripcion())
                .estado(detalle.getEstado())
                .faseActual(detalle.getFaseActual())
                .fechaInicio(detalle.getFechaInicio())
                .solicitudId(detalle.getSolicitudId())
                .siguienteMensaje(mensajeEstado(detalle))
                .cliente(detalle.getCliente())
                .documentos(detalle.getDocumentos().stream()
                        .filter(documento -> documento.isSubido())
                        .toList())
                .requisitosDocumentales(detalle.getRequisitosDocumentales().stream()
                        .filter(requisito -> "REQUERIDO".equals(requisito.getEstado()))
                        .toList())
                .incidencias(detalle.getIncidencias())
                .mensajes(detalle.getMensajes())
                .historial(detalle.getHistorial())
                .build();
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Void> añadirMensajeCliente(
            @PathVariable Long id,
            @RequestParam(required = false) String contenido,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication authentication
    ) {
        String contenidoFinal = contenido != null ? contenido : body != null ? body.get("contenido") : null;
        mensajeService.añadirAExpediente(id, contenidoFinal, usuarioService.buscarPorEmail(authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/informacion-adicional/respuesta")
    public ResponseEntity<Void> responderInformacionAdicional(
            @PathVariable Long id,
            @RequestParam(required = false) String contenido,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication authentication
    ) {
        Usuario cliente = usuarioService.buscarPorEmail(authentication.getName());
        Expediente expediente = expedienteService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, cliente)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }
        if (expediente.getEstadoExpediente() != EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
            throw new OperacionInvalidaException("El expediente no tiene informacion adicional pendiente de respuesta.");
        }

        String contenidoFinal = contenido != null ? contenido : body != null ? body.get("contenido") : null;
        mensajeService.a\u00f1adirAExpediente(id, contenidoFinal, cliente);
        expedienteService.marcarInformacionAdicionalRecibida(id, cliente);
        return ResponseEntity.noContent().build();
    }

    private String mensajeEstado(ExpedienteDetailResponse detalle) {
        if ("FINALIZADO".equals(detalle.getEstado())) {
            return "El expediente esta finalizado.";
        }
        if ("SOLICITADA_INFORMACION_ADICIONAL".equals(detalle.getEstado())) {
            return "Necesitamos que respondas a la informacion solicitada para continuar.";
        }
        if ("INFORMACION_ADICIONAL_RECIBIDA".equals(detalle.getEstado())) {
            return "Hemos recibido tu respuesta y esta pendiente de revision.";
        }
        if ("PENDIENTE_DOCUMENTACION".equals(detalle.getEstado())) {
            return "Necesitamos que aportes la documentacion solicitada para continuar.";
        }
        if ("INCIDENCIA".equals(detalle.getEstado())) {
            return "Necesitamos que revises una incidencia pendiente.";
        }
        if ("REVISANDO_INCIDENCIAS".equals(detalle.getEstado())) {
            return "Hemos recibido la documentacion aportada y esta pendiente de revision.";
        }
        if ("ENVIADO_DGT".equals(detalle.getEstado())) {
            return "El expediente ya ha sido enviado a DGT y esta pendiente de resolucion.";
        }
        if (detalle.getSiguientePaso() != null && "DOCUMENTACION".equals(detalle.getSiguientePaso().getTipo())) {
            return "Estamos pendientes de completar la documentacion necesaria.";
        }
        return "Estamos tramitando el expediente.";
    }
}
