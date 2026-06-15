package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaPreviewResponse;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaResponse;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.repository.TipoIncidenciaRepository;
import com.example.gestor_documental.service.*;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidenciaServiceImpl implements IncidenciaService {

    private final IncidenciaRepository incidenciaRepository;
    private final HitoExpedienteRepository hitoExpedienteRepository;
    private final AvisoIncidenciaRepository avisoIncidenciaRepository;
    private final TipoIncidenciaRepository tipoIncidenciaRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;
    private final TipoIncidenciaService tipoIncidenciaService;
    private final HistorialCambioService historialCambioService;
    private final MensajeService mensajeService;
    private final CorreoService correoService;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;
    @Value("${app.public-url:http://127.0.0.1:5173}")
    private String publicUrl;

    @Override
    public List<Incidencia> listarPorExpediente(Long expedienteId) {
        return incidenciaRepository.findByExpedienteId(expedienteId);
    }

    @Override
    public List<Incidencia> listarPorSolicitud(Long solicitudId) {
        return incidenciaRepository.findBySolicitudId(solicitudId);
    }

    /**
     * Abre una incidencia y fuerza la detención virtual del expediente.
     * Efecto secundario (Automático): Degrada el estado del Expediente general 
     * a 'INCIDENCIA' paralizando su avance.
     */
    @Override
    @Transactional
    public Incidencia crearIncidenciaExpediente(Long expedienteId, Long tipoIncidenciaId, String observaciones, Usuario admin) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
                
        if (!expedienteService.tienePermisoExpediente(expediente, admin) || admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede crear incidencias en expedientes.");
        }

        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("No se puede abrir una incidencia en un expediente finalizado");
        }

        TipoIncidencia tipo = tipoIncidenciaService.buscarPorId(tipoIncidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de incidencia no encontrado"));

        String observacionesNormalizadas = TextNormalizer.upperOrNull(observaciones);
        Incidencia incidencia = new Incidencia(tipo, expediente, observacionesNormalizadas, admin);
        incidenciaRepository.save(incidencia);

        // Cambiamos el estado a INCIDENCIA automáticamente
        expedienteService.cambiarEstado(expedienteId, estadoParaTipo(tipo), admin);
        
        historialCambioService.registrarCambioExpediente(
                expediente, 
                admin, 
                "INCIDENCIA", 
                "Nueva incidencia: " + tipo.getNombre().name() + " - " + observacionesNormalizadas
        );

        return incidencia;
    }

    @Override
    @Transactional
    public Incidencia prepararNotificacionExpediente(Long expedienteId, Usuario admin) {
        validarAdmin(admin);
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        if (!expedienteService.tienePermisoExpediente(expediente, admin)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO
                || expediente.getEstadoExpediente() == EstadoExpediente.RECHAZADO) {
            throw new OperacionInvalidaException("El expediente ya no admite notificaciones al cliente");
        }

        List<Incidencia> activas = incidenciaRepository.findByExpedienteIdAndResueltaFalse(expedienteId);
        if (!activas.isEmpty()) {
            return activas.stream()
                    .filter(incidencia -> !incidencia.isSeguimientoArchivado())
                    .findFirst()
                    .orElse(activas.get(0));
        }

        TipoIncidenciaEnum tipoNombre = expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL
                ? TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL
                : TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION;
        TipoIncidencia tipo = tipoIncidenciaRepository.findByNombre(tipoNombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de incidencia no encontrado"));
        String observaciones = tipoNombre == TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL
                ? "INFORMACION PENDIENTE DE RESPUESTA DEL CLIENTE"
                : "DOCUMENTACION PENDIENTE DE APORTACION DEL CLIENTE";

        Incidencia incidencia = new Incidencia(tipo, expediente, observaciones, admin);
        incidenciaRepository.save(incidencia);
        historialCambioService.registrarCambioExpediente(
                expediente,
                admin,
                "AVISO PENDIENTE",
                "Se preparo la notificacion al cliente para una peticion ya pendiente."
        );
        return incidencia;
    }

    /**
     * Idéntico a Expediente, pero aplicable a Solicitudes.
     * Efecto secundario (Automático): Cambia el estado directo de la Solicitud 
     * a 'PENDIENTE_DOCUMENTACION' para requerir intervención del usuario.
     */
    @Override
    @Transactional
    public Incidencia crearIncidenciaSolicitud(Long solicitudId, Long tipoIncidenciaId, String observaciones, Usuario admin) {
        Solicitud solicitud = solicitudService.buscarPorId(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        if (!solicitudService.tienePermisoSolicitud(solicitud, admin) || admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede crear incidencias en solicitudes.");
        }

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA || solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede abrir incidencia en solicitud convertida o rechazada");
        }

        TipoIncidencia tipo = tipoIncidenciaService.buscarPorId(tipoIncidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de incidencia no encontrado"));

        String observacionesNormalizadas = TextNormalizer.upperOrNull(observaciones);
        Incidencia incidencia = new Incidencia(tipo, solicitud, observacionesNormalizadas, admin);
        incidenciaRepository.save(incidencia);

        solicitudService.cambiarEstadoSolicitud(solicitudId, EstadoSolicitud.PENDIENTE_DOCUMENTACION, admin);

        historialCambioService.registrarCambioSolicitud(
                solicitud, 
                admin, 
                "INCIDENCIA", 
                "Nueva incidencia: " + tipo.getNombre().name() + " - " + observacionesNormalizadas
        );

        return incidencia;
    }

    /**
     * Acción invocada típicamente por el Cliente desde el portal para notificar que 
     * ha subido los datos faltantes que provocaron una incidencia.
     * Efecto secundario (Automático): Si el Expediente estaba detenido en 'INCIDENCIA', 
     * avanza su estado a 'REVISANDO_INCIDENCIAS' enviando la pelota al tejado del Administrador.
     */
    @Override
    @Transactional
    public void solicitarRevisionExpediente(Long expedienteId, Usuario cliente) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
                
        if (!expedienteService.tienePermisoExpediente(expediente, cliente)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }

        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA) {
            // El propio administrador lo marca EN_TRAMITE manualmente si necesita o aqui?
            // Aquí lo está mandando el cliente tras subir documentación, marcamos como revisión de incidencias.
            expediente.setEstadoExpediente(EstadoExpediente.REVISANDO_INCIDENCIAS);
            expedienteService.guardar(expediente);
        }
    }

    /**
     * Reflejo de su contraparte en Expedientes.
     * Efecto secundario (Automático): Escala la Solicitud desde 'PENDIENTE_DOCUMENTACION' 
     * hasta 'REVISANDO_INCIDENCIAS' para alertar al Administrador.
     */
    @Override
    @Transactional
    public void solicitarRevisionSolicitud(Long solicitudId, Usuario cliente) {
        Solicitud solicitud = solicitudService.buscarPorId(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        if (!solicitudService.tienePermisoSolicitud(solicitud, cliente)) {
            throw new AccesoDenegadoException("No tienes permiso sobre esta solicitud");
        }

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_DOCUMENTACION) {
            solicitud.setEstadoSolicitud(EstadoSolicitud.REVISANDO_INCIDENCIAS);
            solicitudService.guardar(solicitud);
        }
    }

    @Override
    @Transactional
    public void resolverIncidencia(Long incidenciaId, Usuario admin) {
        if (admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede resolver incidencias manualmente.");
        }

        Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));

        if (incidencia.isResuelta()) {
            throw new OperacionInvalidaException("La incidencia ya está resuelta.");
        }

        incidencia.setResuelta(true);
        incidencia.setProximoAviso(null);
        incidencia.setSeguimientoArchivado(false);
        incidencia.setFechaResolucion(LocalDateTime.now());
        incidencia.setResueltoPor(admin);
        incidenciaRepository.save(incidencia);
        
        if (incidencia.getExpediente() != null) {
            if (incidenciaRepository.findByExpedienteIdAndResueltaFalse(incidencia.getExpediente().getId()).isEmpty()) {
                incidencia.getExpediente().setEstadoExpediente(estadoTrasResolverIncidencias(incidencia.getExpediente()));
                expedienteService.guardar(incidencia.getExpediente());
            }
            historialCambioService.registrarCambioExpediente(
                    incidencia.getExpediente(), 
                    admin, 
                    "INCIDENCIA RESUELTA", 
                    "Se resolvió la incidencia: " + incidencia.getTipoIncidencia().getNombre().name()
            );
        } else if (incidencia.getSolicitud() != null) {
            historialCambioService.registrarCambioSolicitud(
                    incidencia.getSolicitud(), 
                    admin, 
                    "INCIDENCIA RESUELTA", 
                    "Se resolvió la incidencia: " + incidencia.getTipoIncidencia().getNombre().name()
            );
        }
    }

    @Override
    @Transactional
    public void reclamarIncidencia(Long incidenciaId, String observaciones, Usuario admin) {
        if (admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede reclamar incidencias.");
        }

        Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));

        if (incidencia.isResuelta()) {
            throw new OperacionInvalidaException("La incidencia ya esta resuelta.");
        }
        if (incidencia.getExpediente() == null) {
            throw new OperacionInvalidaException("Esta incidencia no pertenece a un expediente.");
        }

        String nuevaObservacion = TextNormalizer.upperOrNull(observaciones) != null
                ? TextNormalizer.upperOrNull(observaciones)
                : "La documentacion aportada no resuelve la incidencia.";
        incidencia.setObservaciones((incidencia.getObservaciones() != null ? incidencia.getObservaciones() + "\n\n" : "")
                + "Reclamacion admin: " + nuevaObservacion);
        incidenciaRepository.save(incidencia);

        expedienteService.cambiarEstado(
                incidencia.getExpediente().getId(),
                estadoParaTipo(incidencia.getTipoIncidencia()),
                admin
        );
        historialCambioService.registrarCambioExpediente(
                incidencia.getExpediente(),
                admin,
                "INCIDENCIA RECLAMADA",
                nuevaObservacion
        );
    }

    @Override
    @Transactional
    public void responderIncidenciaExpediente(Long incidenciaId, String respuesta, Usuario cliente) {
        if (respuesta == null || respuesta.isBlank()) {
            throw new IllegalArgumentException("La respuesta no puede estar vacia");
        }

        Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        if (incidencia.isResuelta()) {
            throw new OperacionInvalidaException("La incidencia ya esta resuelta.");
        }
        Expediente expediente = incidencia.getExpediente();
        if (expediente == null) {
            throw new OperacionInvalidaException("Esta incidencia no pertenece a un expediente.");
        }
        if (!expedienteService.tienePermisoExpediente(expediente, cliente)) {
            throw new AccesoDenegadoException("No tienes permiso para responder a esta incidencia");
        }

        mensajeService.añadirAExpediente(expediente.getId(), respuesta, cliente);
        incidencia.setProximoAviso(null);
        incidencia.setSeguimientoArchivado(false);
        incidenciaRepository.save(incidencia);
        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA) {
            expediente.setEstadoExpediente(EstadoExpediente.REVISANDO_INCIDENCIAS);
            expedienteService.guardar(expediente);
        }
        historialCambioService.registrarCambioExpediente(
                expediente,
                cliente,
                "RESPUESTA INCIDENCIA",
                "El cliente respondio a la solicitud de informacion."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public NotificacionIncidenciaPreviewResponse previsualizarNotificacion(Long incidenciaId, Usuario admin) {
        validarAdmin(admin);
        Incidencia incidencia = incidenciaRepository.findById(incidenciaId).orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        validarSeguimiento(incidencia);
        int numero = incidencia.getContadorAvisos() + 1;
        return new NotificacionIncidenciaPreviewResponse(
                incidencia.getId(),
                correoCliente(incidencia),
                asuntoPorDefecto(incidencia, numero),
                mensajePorDefecto(incidencia, numero),
                numero,
                mailEnabled
        );
    }

    @Override
    @Transactional
    public NotificacionIncidenciaResponse notificarCliente(Long incidenciaId, String asunto, String mensaje, Usuario admin) {
        validarAdmin(admin);
        Incidencia incidencia = incidenciaRepository.findById(incidenciaId).orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        validarSeguimiento(incidencia);
        int numero = incidencia.getContadorAvisos() + 1;
        if (numero > 5) throw new OperacionInvalidaException("Se ha alcanzado el maximo de avisos. Archiva el seguimiento.");
        String asuntoFinal = asunto != null && !asunto.isBlank() ? asunto.trim() : asuntoPorDefecto(incidencia, numero);
        String texto = mensaje != null && !mensaje.isBlank() ? mensaje.trim() : mensajePorDefecto(incidencia, numero);
        String destinatario = correoCliente(incidencia);
        CorreoService.ResultadoCorreo resultado = correoService.enviar(destinatario, asuntoFinal, texto);
        AvisoIncidencia aviso = new AvisoIncidencia();
        aviso.setIncidencia(incidencia); aviso.setNumeroAviso(numero); aviso.setEnviadoPor(admin); aviso.setMensaje(texto);
        aviso.setDestinatario(destinatario); aviso.setAsunto(asuntoFinal);
        aviso.setEstadoEnvio(resultado.exito() ? resultado.simulado() ? "SIMULADO" : "ENVIADO" : "ERROR");
        aviso.setErrorEnvio(resultado.error());
        avisoIncidenciaRepository.save(aviso);
        if (!resultado.exito()) return new NotificacionIncidenciaResponse(false, false, resultado.error());
        LocalDateTime ahora = LocalDateTime.now(); incidencia.setContadorAvisos(numero); incidencia.setFechaUltimoAviso(ahora); incidencia.setProximoAviso(siguienteVencimiento(ahora, numero)); incidencia.setSeguimientoArchivado(false); incidencia.setFechaArchivoSeguimiento(null); incidencia.setSeguimientoArchivadoPor(null); incidenciaRepository.save(incidencia);
        mensajeService.añadirAExpediente(incidencia.getExpediente().getId(), texto, admin);
        historialCambioService.registrarCambioExpediente(incidencia.getExpediente(), admin, "AVISO INCIDENCIA", "Aviso " + numero + (resultado.simulado() ? " simulado." : " enviado al cliente."));
        return new NotificacionIncidenciaResponse(true, resultado.simulado(), resultado.simulado() ? "Envio simulado correctamente." : "Correo enviado correctamente.");
    }

    @Override @Transactional
    public void archivarSeguimiento(Long incidenciaId, Usuario admin) {
        validarAdmin(admin); Incidencia incidencia = incidenciaRepository.findById(incidenciaId).orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        if (incidencia.isResuelta()) throw new OperacionInvalidaException("La incidencia ya esta resuelta");
        incidencia.setSeguimientoArchivado(true); incidencia.setFechaArchivoSeguimiento(LocalDateTime.now()); incidencia.setSeguimientoArchivadoPor(admin); incidencia.setProximoAviso(null); incidenciaRepository.save(incidencia);
    }

    @Override @Transactional
    public void reactivarSeguimiento(Long incidenciaId, Usuario admin) {
        validarAdmin(admin); Incidencia incidencia = incidenciaRepository.findById(incidenciaId).orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        incidencia.setSeguimientoArchivado(false); incidencia.setFechaArchivoSeguimiento(null); incidencia.setSeguimientoArchivadoPor(null); incidencia.setProximoAviso(LocalDateTime.now()); incidenciaRepository.save(incidencia);
    }

    private LocalDateTime siguienteVencimiento(LocalDateTime fecha, int numeroAviso) {
        return switch (numeroAviso) { case 1, 2, 3 -> fecha.plusWeeks(1); case 4 -> fecha.plusMonths(1); case 5 -> fecha.plusMonths(2); default -> null; };
    }

    private void validarAdmin(Usuario usuario) {
        if (usuario == null || usuario.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) throw new AccesoDenegadoException("Solo el administrador puede gestionar avisos");
    }

    private void validarSeguimiento(Incidencia incidencia) {
        if (incidencia.isResuelta() || incidencia.getExpediente() == null
                || incidencia.getExpediente().getEstadoExpediente() == EstadoExpediente.FINALIZADO
                || incidencia.getExpediente().getEstadoExpediente() == EstadoExpediente.RECHAZADO) {
            throw new OperacionInvalidaException("La incidencia ya no admite seguimiento");
        }
    }

    private String correoCliente(Incidencia incidencia) {
        if (incidencia.getExpediente().getCliente() == null
                || incidencia.getExpediente().getCliente().getEmail() == null
                || incidencia.getExpediente().getCliente().getEmail().isBlank()) {
            throw new OperacionInvalidaException("El cliente no tiene un correo configurado.");
        }
        return incidencia.getExpediente().getCliente().getEmail();
    }

    private String asuntoPorDefecto(Incidencia incidencia, int numeroAviso) {
        String matricula = incidencia.getExpediente().getMatricula() != null ? incidencia.getExpediente().getMatricula() : "EXPEDIENTE " + incidencia.getExpediente().getId();
        return (numeroAviso == 1 ? "Informacion pendiente" : "Recordatorio de informacion pendiente") + " - " + matricula;
    }

    private String mensajePorDefecto(Incidencia incidencia, int numeroAviso) {
        String matricula = incidencia.getExpediente().getMatricula() != null ? incidencia.getExpediente().getMatricula() : "EXPEDIENTE " + incidencia.getExpediente().getId();
        String tipo = incidencia.getTipoIncidencia() != null && incidencia.getTipoIncidencia().getNombre() != null
                ? incidencia.getTipoIncidencia().getNombre().name().replace('_', ' ')
                : "INFORMACION PENDIENTE";
        String detalle = incidencia.getObservaciones() != null && !incidencia.getObservaciones().isBlank() ? incidencia.getObservaciones() : "Consulta el detalle en el portal.";
        String base = publicUrl != null ? publicUrl.replaceAll("/$", "") : "";
        return "Hola,\n\n"
                + (numeroAviso == 1 ? "Necesitamos tu respuesta para continuar con el tramite." : "Te recordamos que seguimos pendientes de tu respuesta para continuar con el tramite.")
                + "\n\nExpediente: " + matricula
                + "\nMotivo: " + tipo
                + "\nDetalle: " + detalle
                + "\n\nAccede al portal: " + base + "/expedientes/" + incidencia.getExpediente().getId()
                + "\n\nGracias,\nGestoria CN";
    }

    private EstadoExpediente estadoParaTipo(TipoIncidencia tipo) {
        return tipo != null && tipo.getNombre() == TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION
                ? EstadoExpediente.PENDIENTE_DOCUMENTACION
                : EstadoExpediente.INCIDENCIA;
    }

    private EstadoExpediente estadoTrasResolverIncidencias(Expediente expediente) {
        boolean enviadoDgt = hitoExpedienteRepository.existsByExpedienteIdAndCodigo(
                expediente.getId(),
                CodigoHitoExpediente.ENVIADO_DGT
        ) || hitoExpedienteRepository.existsByExpedienteIdAndCodigo(
                expediente.getId(),
                CodigoHitoExpediente.COM_ENVIADO_DGT
        );
        return enviadoDgt ? EstadoExpediente.ENVIADO_DGT : EstadoExpediente.EN_TRAMITE;
    }
}
