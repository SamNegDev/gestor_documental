package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.LecturaIaSolicitudClienteResponse;
import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import com.example.gestor_documental.model.GestionPersonaCatalogo;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.GestionPersonaCatalogoRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import com.example.gestor_documental.util.NombrePersonaNormalizer;
import com.example.gestor_documental.util.TextNormalizer;
import com.example.gestor_documental.validation.DniNieValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitudDocumentacionIaServiceImpl implements SolicitudDocumentacionIaService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudDocumentacionIaServiceImpl.class);
    private static final double CONFIANZA_MINIMA_IDENTIDAD = 0.80;
    private static final double CONFIANZA_MINIMA_ROLES = 0.90;
    private static final int USOS_CLIENTE_SIN_LIMITE = 0;
    private static final int USOS_RESTANTES_SIN_LIMITE = Integer.MAX_VALUE;
    private static final String ACCION_IA_CLIENTE_SOLICITUD = "IA DOCUMENTACION CLIENTE";

    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final DocumentoRolesLecturaRepository rolesLecturaRepository;
    private final DocumentoVehiculoLecturaRepository vehiculoLecturaRepository;
    private final GestionPersonaCatalogoRepository gestionPersonaCatalogoRepository;
    private final HistorialCambioRepository historialCambioRepository;
    private final DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    private final DocumentoRolesLecturaService documentoRolesLecturaService;
    private final DocumentoVehiculoLecturaService documentoVehiculoLecturaService;
    private final HistorialCambioService historialCambioService;
    private final DniNieValidator dniNieValidator;
    private final OpenAiProperties openAiProperties;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario admin) {
        validarAdmin(admin);
        return procesarDocumentacion(solicitudId, admin, false, false);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario admin, boolean forzarRelectura) {
        validarAdmin(admin);
        return procesarDocumentacion(solicitudId, admin, false, forzarRelectura);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SolicitudDocumentacionIaResponse procesarDocumentacionInterna(Long solicitudId, Usuario usuario) {
        return procesarDocumentacion(solicitudId, usuario, true, false);
    }

    @Override
    @Transactional(readOnly = true)
    public LecturaIaSolicitudClienteResponse obtenerLecturaCliente(Long solicitudId, Usuario cliente) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        validarClienteSolicitud(solicitud, cliente);
        return construirEstadoLecturaCliente(solicitud, null);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SolicitudDocumentacionIaResponse procesarDocumentacionCliente(Long solicitudId, Usuario cliente) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        validarClienteSolicitud(solicitud, cliente);
        LecturaIaSolicitudClienteResponse estado = construirEstadoLecturaCliente(solicitud, null);
        if (!estado.puedeSolicitar()) {
            throw new OperacionInvalidaException(estado.mensaje());
        }

        SolicitudDocumentacionIaResponse response = procesarDocumentacion(solicitudId, cliente, true, false);
        historialCambioService.registrarCambioSolicitud(
                solicitud,
                cliente,
                ACCION_IA_CLIENTE_SOLICITUD,
                "El cliente solicito lectura IA de documentacion.");
        return response;
    }

    private SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario usuario, boolean automatica, boolean forzarRelectura) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (automatica) {
            validarPermisoSolicitud(solicitud, usuario);
        }
        validarSolicitudAbierta(solicitud);

        List<Documento> documentos = documentoRepository.findBySolicitudId(solicitudId).stream()
                .filter(documento -> documento.getId() != null)
                .sorted(Comparator.comparing(Documento::getFechaSubida, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        List<Documento> documentosIdentidad = documentos.stream()
                .filter(documento -> esIdentidad(documento.getTipoDocumento()))
                .toList();
        List<Documento> documentosRoles = documentos.stream()
                .filter(documento -> esDocumentoRoles(documento.getTipoDocumento()))
                .toList();
        List<Documento> documentosVehiculo = documentos.stream()
                .filter(documento -> esDocumentoVehiculo(documento.getTipoDocumento()))
                .toList();

        if (documentosIdentidad.isEmpty() && documentosRoles.isEmpty() && documentosVehiculo.isEmpty()) {
            throw new OperacionInvalidaException("No hay DNI/CIF, factura/contrato ni documentacion de vehiculo en la solicitud.");
        }

        List<String> detalles = new ArrayList<>();
        Contadores contadores = new Contadores();
        boolean permitirRelectura = usuario != null && usuario.getRolUsuario() == RolUsuario.ADMIN;
        boolean forzarLecturasExistentes = forzarRelectura && permitirRelectura;
        if (forzarLecturasExistentes) {
            detalles.add("Se forzo la relectura de DNI/CIF, roles y vehiculo ya leidos previamente.");
        }
        leerIdentidades(documentosIdentidad, usuario, contadores, detalles, permitirRelectura, forzarLecturasExistentes);
        leerVehiculo(documentosVehiculo, usuario, contadores, detalles, permitirRelectura, forzarLecturasExistentes);
        leerRoles(documentosRoles, usuario, contadores, detalles, permitirRelectura, forzarLecturasExistentes);

        Map<String, IdentidadSolicitud> identidades = mejoresIdentidades(documentosIdentidad);
        DocumentoVehiculoLectura lecturaVehiculo = mejorLecturaVehiculo(documentosVehiculo);
        boolean vehiculoActualizado = aplicarVehiculoSiProcede(solicitud, lecturaVehiculo, detalles);
        if (esBatecom(solicitud)) {
            return procesarBatecom(solicitud, documentosIdentidad, documentosRoles, identidades, contadores, detalles, usuario, vehiculoActualizado);
        }

        DocumentoRolesLectura lecturaRoles = mejorLecturaRoles(documentosRoles);
        if (lecturaRoles == null) {
            detalles.add(documentosRoles.isEmpty()
                    ? "Falta factura o contrato para determinar comprador y vendedor."
                    : "La factura/contrato aun no tiene comprador y vendedor con confianza suficiente.");
            if (vehiculoActualizado) {
                guardarVehiculoActualizado(solicitud, usuario, "Se actualizaron datos de vehiculo desde permiso/ficha/informe DGT.");
                detalles.add("Datos de vehiculo actualizados en la solicitud.");
                return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, true,
                        "Datos de vehiculo actualizados; falta revision para comprador y vendedor.", detalles);
            }
            return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, false, false, true,
                    "Lecturas realizadas, pero falta revision antes de actualizar datos.", detalles);
        }

        PersonaSolicitud vendedor = personaDesdeLectura(lecturaRoles, true, identidades);
        PersonaSolicitud comprador = personaDesdeLectura(lecturaRoles, false, identidades);
        validarPersona(vendedor, "vendedor");
        validarPersona(comprador, "comprador");
        if (vendedor.identificador().equals(comprador.identificador())) {
            throw new OperacionInvalidaException("Comprador y vendedor tienen el mismo DNI/CIF.");
        }
        List<String> faltasCorroboracion = faltasCorroboracionIdentidad(solicitud, vendedor, comprador, identidades);
        if (!faltasCorroboracion.isEmpty()) {
            detalles.addAll(faltasCorroboracion);
            if (vehiculoActualizado) {
                guardarVehiculoActualizado(solicitud, usuario, "Se actualizaron datos de vehiculo desde permiso/ficha/informe DGT.");
                detalles.add("Datos de vehiculo actualizados en la solicitud.");
                return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, true,
                        "Datos de vehiculo actualizados; falta validar identidad antes de actualizar comprador y vendedor.", detalles);
            }
            return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, false, false, true,
                    "Lecturas realizadas, pero falta validar identidad antes de actualizar datos.", detalles);
        }
        detalles.addAll(avisosNombreIdentidad(lecturaRoles, identidades));

        vehiculoActualizado |= aplicarVehiculoSiProcede(solicitud, lecturaRoles, detalles);
        boolean yaCorrecta = solicitudYaCoincide(solicitud, vendedor, comprador);
        if (yaCorrecta) {
            marcarIdentidadesUsadas(identidades, vendedor, comprador);
            detalles.add("La solicitud ya tenia comprador y vendedor coherentes con la lectura valida.");
            if (vehiculoActualizado) {
                solicitud.setFechaUltimaModificacion(LocalDateTime.now());
                solicitud.setModificadoPor(usuario);
                solicitudRepository.save(solicitud);
                historialCambioService.registrarCambioSolicitud(
                        solicitud,
                        usuario,
                        "IA DOCUMENTACION",
                        "Se actualizaron datos de vehiculo u operacion desde factura/contrato.");
                detalles.add("Datos de vehiculo u operacion actualizados en la solicitud.");
                return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, false,
                        "Datos de vehiculo u operacion actualizados desde la documentacion.", detalles);
            }
            return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, false, true, false,
                    "Sin cambios: la documentacion ya estaba procesada correctamente.", detalles);
        }

        aplicarPersona(solicitud, RolInteresado.VENDEDOR, vendedor);
        aplicarPersona(solicitud, RolInteresado.COMPRADOR, comprador);
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(usuario);
        solicitudRepository.save(solicitud);
        marcarIdentidadesUsadas(identidades, vendedor, comprador);

        historialCambioService.registrarCambioSolicitud(
                solicitud,
                usuario,
                "IA DOCUMENTACION",
                "Se actualizaron comprador y vendedor desde DNI/CIF y factura/contrato.");
        detalles.add("Datos de comprador y vendedor actualizados en la solicitud.");
        return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, false,
                "Datos actualizados desde la documentacion.", detalles);
    }

    private SolicitudDocumentacionIaResponse procesarBatecom(
            Solicitud solicitud,
            List<Documento> documentosIdentidad,
            List<Documento> documentosRoles,
            Map<String, IdentidadSolicitud> identidades,
            Contadores contadores,
            List<String> detalles,
            Usuario admin,
            boolean vehiculoActualizado
    ) {
        List<DocumentoRolesLectura> lecturas = lecturasRolesUsables(documentosRoles);
        BatecomPartes partes = detectarPartesBatecom(lecturas, identidades);
        if (partes == null) {
            detalles.add(lecturas.size() < 2
                    ? "BATECOM necesita dos lecturas validas: entrega a compraventa y venta final al comprador."
                    : "No se ha detectado una compraventa comun que aparezca como comprador en una operacion y vendedor en otra.");
            if (vehiculoActualizado) {
                guardarVehiculoActualizado(solicitud, admin, "Se actualizaron datos de vehiculo desde permiso/ficha/informe DGT.");
                detalles.add("Datos de vehiculo actualizados en la solicitud.");
                return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, true,
                        "Datos de vehiculo actualizados; falta identificar las dos operaciones BATECOM.", detalles);
            }
            return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, false, false, true,
                    "Lecturas realizadas, pero falta identificar las dos operaciones BATECOM.", detalles);
        }

        validarPersona(partes.vendedor(), "vendedor inicial");
        validarPersona(partes.compraventa(), "compraventa");
        validarPersona(partes.comprador(), "comprador final");
        if (partes.vendedor().identificador().equals(partes.compraventa().identificador())
                || partes.compraventa().identificador().equals(partes.comprador().identificador())
                || partes.vendedor().identificador().equals(partes.comprador().identificador())) {
            throw new OperacionInvalidaException("BATECOM requiere vendedor inicial, compraventa y comprador final distintos.");
        }

        List<String> faltasCorroboracion = faltasCorroboracionIdentidadBatecom(solicitud, partes, identidades);
        if (!faltasCorroboracion.isEmpty()) {
            detalles.addAll(faltasCorroboracion);
            if (vehiculoActualizado) {
                guardarVehiculoActualizado(solicitud, admin, "Se actualizaron datos de vehiculo desde permiso/ficha/informe DGT.");
                detalles.add("Datos de vehiculo actualizados en la solicitud.");
                return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, true,
                        "Datos de vehiculo actualizados; falta validar identidad antes de actualizar partes BATECOM.", detalles);
            }
            return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, false, false, true,
                    "Lecturas realizadas, pero falta validar identidad antes de actualizar datos.", detalles);
        }
        detalles.addAll(avisosNombreIdentidad(partes.lecturaBate(), identidades));
        detalles.addAll(avisosNombreIdentidad(partes.lecturaCom(), identidades));

        vehiculoActualizado |= aplicarVehiculoSiProcede(solicitud, partes.lecturaBate(), detalles);
        vehiculoActualizado |= aplicarVehiculoSiProcede(solicitud, partes.lecturaCom(), detalles);
        if (solicitudBatecomYaCoincide(solicitud, partes)) {
            marcarIdentidadesUsadas(identidades, partes.vendedor(), partes.compraventa(), partes.comprador());
            detalles.add("La solicitud ya tenia vendedor inicial, compraventa y comprador final coherentes con las lecturas validas.");
            if (vehiculoActualizado) {
                solicitud.setFechaUltimaModificacion(LocalDateTime.now());
                solicitud.setModificadoPor(admin);
                solicitudRepository.save(solicitud);
                historialCambioService.registrarCambioSolicitud(
                        solicitud,
                        admin,
                        "IA DOCUMENTACION BATECOM",
                        "Se actualizaron datos de vehiculo desde contratos/facturas.");
                detalles.add("Datos de vehiculo actualizados en la solicitud.");
                return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, false,
                        "Datos de vehiculo actualizados desde la documentacion BATECOM.", detalles);
            }
            return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, false, true, false,
                    "Sin cambios: la documentacion BATECOM ya estaba procesada correctamente.", detalles);
        }

        aplicarPersona(solicitud, RolInteresado.VENDEDOR, partes.vendedor());
        aplicarPersona(solicitud, RolInteresado.COMPRAVENTA, partes.compraventa());
        aplicarPersona(solicitud, RolInteresado.COMPRADOR, partes.comprador());
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(admin);
        solicitudRepository.save(solicitud);
        marcarIdentidadesUsadas(identidades, partes.vendedor(), partes.compraventa(), partes.comprador());

        historialCambioService.registrarCambioSolicitud(
                solicitud,
                admin,
                "IA DOCUMENTACION BATECOM",
                "Se actualizaron vendedor inicial, compraventa y comprador final desde contratos/facturas.");
        detalles.add("Datos BATECOM actualizados: vendedor inicial, compraventa y comprador final.");
        return respuesta(solicitud.getId(), documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, false,
                "Datos BATECOM actualizados desde la documentacion.", detalles);
    }

    private void validarAdmin(Usuario admin) {
        if (admin == null || admin.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede procesar documentacion con IA.");
        }
    }

    private void validarPermisoSolicitud(Solicitud solicitud, Usuario usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("No tienes permiso para procesar esta solicitud.");
        }
        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            return;
        }
        Long clienteSolicitudId = solicitud.getCliente() != null ? solicitud.getCliente().getId() : null;
        Long clienteUsuarioId = usuario.getCliente() != null ? usuario.getCliente().getId() : null;
        if (clienteSolicitudId == null || !clienteSolicitudId.equals(clienteUsuarioId)) {
            throw new AccesoDenegadoException("No tienes permiso para procesar esta solicitud.");
        }
    }

    private void validarClienteSolicitud(Solicitud solicitud, Usuario usuario) {
        if (usuario == null || usuario.getRolUsuario() != RolUsuario.CLIENTE) {
            throw new AccesoDenegadoException("Solo el cliente puede solicitar esta lectura.");
        }
        validarPermisoSolicitud(solicitud, usuario);
    }

    private LecturaIaSolicitudClienteResponse construirEstadoLecturaCliente(Solicitud solicitud, String mensajePreferente) {
        DocumentacionSolicitudCliente documentacion = documentacionSolicitudCliente(solicitud);
        long usos = historialCambioRepository.countBySolicitudIdAndAccion(solicitud.getId(), ACCION_IA_CLIENTE_SOLICITUD);
        int usosConsumidos = toIntSaturado(usos);
        boolean apiKeyConfigurada = openAiProperties.hasApiKey();
        boolean cerrada = solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA
                || solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO
                || solicitud.getExpediente() != null;
        boolean puedeSolicitar = apiKeyConfigurada && documentacion.suficiente() && !cerrada;
        String mensaje = mensajePreferente != null
                ? mensajePreferente
                : mensajeLecturaClienteSolicitud(apiKeyConfigurada, documentacion.suficiente(), cerrada);
        return new LecturaIaSolicitudClienteResponse(
                solicitud.getId(),
                apiKeyConfigurada,
                documentacion.suficiente(),
                puedeSolicitar,
                documentacion.bloqueos(),
                usosConsumidos,
                USOS_CLIENTE_SIN_LIMITE,
                USOS_RESTANTES_SIN_LIMITE,
                documentacion.documentosIdentidad(),
                documentacion.documentosVehiculo(),
                documentacion.documentosRoles(),
                mensaje
        );
    }

    private DocumentacionSolicitudCliente documentacionSolicitudCliente(Solicitud solicitud) {
        List<Documento> documentos = documentoRepository.findBySolicitudId(solicitud.getId()).stream()
                .filter(documento -> documento.getId() != null)
                .toList();
        int documentosIdentidad = (int) documentos.stream()
                .filter(documento -> esIdentidad(documento.getTipoDocumento()))
                .count();
        int documentosRoles = (int) documentos.stream()
                .filter(documento -> esDocumentoRoles(documento.getTipoDocumento()))
                .count();
        boolean permisoCirculacion = documentos.stream()
                .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.PERMISO_CIRCULACION);
        boolean fichaTecnica = documentos.stream()
                .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.FICHA_TECNICA);
        boolean informeDgt = documentos.stream()
                .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.INFORME_DGT);
        int documentosVehiculo = (int) documentos.stream()
                .filter(documento -> documento.getTipoDocumento() == TipoDocumento.PERMISO_CIRCULACION
                        || documento.getTipoDocumento() == TipoDocumento.FICHA_TECNICA
                        || documento.getTipoDocumento() == TipoDocumento.INFORME_DGT)
                .count();
        List<String> bloqueos = new ArrayList<>();
        if (documentosIdentidad == 0) {
            bloqueos.add("Falta DNI/NIE/CIF para validar la identidad.");
        }
        if (!informeDgt && !(permisoCirculacion && fichaTecnica)) {
            bloqueos.add("Falta permiso de circulacion y ficha tecnica, o Informe DGT.");
        }
        return new DocumentacionSolicitudCliente(documentosIdentidad, documentosVehiculo, documentosRoles, bloqueos);
    }

    private String mensajeLecturaClienteSolicitud(
            boolean apiKeyConfigurada,
            boolean documentacionSuficiente,
            boolean cerrada
    ) {
        if (cerrada) {
            return "La solicitud ya no admite lectura IA.";
        }
        if (!apiKeyConfigurada) {
            return "La lectura IA no esta disponible en este momento.";
        }
        if (!documentacionSuficiente) {
            return "Falta documentacion minima para iniciar la lectura IA.";
        }
        return "Puedes solicitar lectura IA de la documentacion aportada.";
    }

    private int toIntSaturado(long valor) {
        return valor > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.toIntExact(Math.max(0, valor));
    }

    private void validarSolicitudAbierta(Solicitud solicitud) {
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA || solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede procesar una solicitud cerrada.");
        }
        if (solicitud.getExpediente() != null) {
            throw new OperacionInvalidaException("La solicitud ya tiene un expediente asociado.");
        }
    }

    private void leerIdentidades(
            List<Documento> documentos,
            Usuario usuario,
            Contadores contadores,
            List<String> detalles,
            boolean permitirRelectura,
            boolean forzarLecturasExistentes
    ) {
        for (Documento documento : documentos) {
            DocumentoIdentidadLectura lecturaExistente = identidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
            boolean existente = lecturaExistente != null;
            boolean forzar = existente && (forzarLecturasExistentes || (permitirRelectura && !identidadUsable(lecturaExistente)));
            try {
                if (existente && !forzar) {
                    contadores.identidadReutilizada++;
                    continue;
                }
                documentoIdentidadLecturaService.leerIdentidad(documento.getId(), forzar, usuario);
                if (existente && !forzar) {
                    contadores.identidadReutilizada++;
                } else {
                    contadores.identidadNueva++;
                }
            } catch (RuntimeException exception) {
                detalles.add("No se pudo leer identidad en " + nombreDocumento(documento) + ": " + mensaje(exception));
                log.warn("No se pudo leer identidad de solicitud en documento {} ({})",
                        documento.getId(), nombreDocumento(documento), exception);
            }
        }
    }

    private void leerRoles(
            List<Documento> documentos,
            Usuario usuario,
            Contadores contadores,
            List<String> detalles,
            boolean permitirRelectura,
            boolean forzarLecturasExistentes
    ) {
        for (Documento documento : documentos) {
            DocumentoRolesLectura lecturaExistente = rolesLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
            boolean existente = lecturaExistente != null;
            boolean forzar = existente && (forzarLecturasExistentes || (permitirRelectura && !rolesUsables(lecturaExistente)));
            try {
                if (existente && !forzar) {
                    contadores.rolesReutilizada++;
                    continue;
                }
                documentoRolesLecturaService.leerRoles(documento.getId(), forzar, usuario);
                if (existente && !forzar) {
                    contadores.rolesReutilizada++;
                } else {
                    contadores.rolesNueva++;
                }
            } catch (RuntimeException exception) {
                detalles.add("No se pudo leer roles en " + nombreDocumento(documento) + ": " + mensaje(exception));
                log.warn("No se pudo leer roles de solicitud en documento {} ({})",
                        documento.getId(), nombreDocumento(documento), exception);
            }
        }
    }

    private void leerVehiculo(
            List<Documento> documentos,
            Usuario usuario,
            Contadores contadores,
            List<String> detalles,
            boolean permitirRelectura,
            boolean forzarLecturasExistentes
    ) {
        for (Documento documento : documentos) {
            DocumentoVehiculoLectura lecturaExistente = vehiculoLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
            boolean existente = lecturaExistente != null;
            boolean forzar = existente && (forzarLecturasExistentes || (permitirRelectura && !vehiculoUsable(lecturaExistente)));
            try {
                if (existente && !forzar) {
                    contadores.vehiculoReutilizada++;
                    continue;
                }
                documentoVehiculoLecturaService.leerVehiculo(documento.getId(), forzar, usuario);
                if (existente && !forzar) {
                    contadores.vehiculoReutilizada++;
                } else {
                    contadores.vehiculoNueva++;
                }
            } catch (RuntimeException exception) {
                detalles.add("No se pudo leer vehiculo en " + nombreDocumento(documento) + ": " + mensaje(exception));
                log.warn("No se pudo leer vehiculo de solicitud en documento {} ({})",
                        documento.getId(), nombreDocumento(documento), exception);
            }
        }
    }

    private Map<String, IdentidadSolicitud> mejoresIdentidades(List<Documento> documentosIdentidad) {
        List<Long> documentoIds = documentosIdentidad.stream().map(Documento::getId).toList();
        if (documentoIds.isEmpty()) {
            return Map.of();
        }
        Map<String, IdentidadSolicitud> result = new HashMap<>();
        identidadLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .forEach(lectura -> {
                    agregarIdentidad(result, identidadDesdeLecturaPrincipal(lectura));
                    DocumentoIdentidadLecturaJson.extraer(lectura).stream()
                            .map(identidad -> identidadDesdeDetectada(lectura, identidad))
                            .forEach(identidad -> agregarIdentidad(result, identidad));
                });
        return result;
    }

    private void agregarIdentidad(Map<String, IdentidadSolicitud> identidades, IdentidadSolicitud identidad) {
        if (!identidadUsable(identidad)) {
            return;
        }
        IdentidadSolicitud actual = identidades.get(identidad.identificador());
        if (actual == null) {
            identidades.put(identidad.identificador(), identidad);
            return;
        }
        identidades.put(identidad.identificador(), combinarIdentidad(actual, identidad));
    }

    private IdentidadSolicitud combinarIdentidad(IdentidadSolicitud actual, IdentidadSolicitud candidata) {
        boolean candidataMasFiable = confianza(candidata.confianzaGlobal()) > confianza(actual.confianzaGlobal());
        IdentidadSolicitud base = candidataMasFiable ? candidata : actual;
        IdentidadSolicitud respaldo = candidataMasFiable ? actual : candidata;
        return new IdentidadSolicitud(
                base.identificador(),
                nombreMasCompleto(base.nombreCompleto(), respaldo.nombreCompleto(), null),
                direccionMasCompleta(base.direccionTexto(), respaldo.direccionTexto()),
                Math.max(confianza(actual.confianzaGlobal()), confianza(candidata.confianzaGlobal())),
                actual.requiereRevision() && candidata.requiereRevision(),
                base.lecturaPrincipal() != null ? base.lecturaPrincipal() : respaldo.lecturaPrincipal()
        );
    }

    private IdentidadSolicitud identidadDesdeLecturaPrincipal(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return null;
        }
        return new IdentidadSolicitud(
                normalizarIdentificador(lectura.getIdentificador()),
                nombreCompletoIdentidad(lectura),
                normalizarTexto(lectura.getDireccionTexto()),
                lectura.getConfianzaGlobal(),
                lectura.isRequiereRevision(),
                lectura
        );
    }

    private IdentidadSolicitud identidadDesdeDetectada(DocumentoIdentidadLectura lectura, IdentidadDetectada identidad) {
        if (identidad == null) {
            return null;
        }
        return new IdentidadSolicitud(
                normalizarIdentificador(identidad.identificador()),
                nombreCompletoIdentidad(identidad),
                normalizarTexto(identidad.direccionTexto()),
                identidad.confianzaGlobal(),
                identidad.requiereRevision(),
                mismaIdentidad(lectura, identidad) ? lectura : null
        );
    }

    private boolean mismaIdentidad(DocumentoIdentidadLectura lectura, IdentidadDetectada identidad) {
        String principal = lectura != null ? normalizarIdentificador(lectura.getIdentificador()) : null;
        String detectada = identidad != null ? normalizarIdentificador(identidad.identificador()) : null;
        return principal != null && principal.equals(detectada);
    }

    private DocumentoRolesLectura mejorLecturaRoles(List<Documento> documentosRoles) {
        List<Long> documentoIds = documentosRoles.stream().map(Documento::getId).toList();
        if (documentoIds.isEmpty()) {
            return null;
        }
        Map<Long, Integer> orden = new HashMap<>();
        for (int index = 0; index < documentoIds.size(); index++) {
            orden.put(documentoIds.get(index), index);
        }
        return rolesLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .filter(this::rolesUsables)
                .sorted(Comparator
                        .comparing((DocumentoRolesLectura lectura) -> confianza(lectura.getConfianzaGlobal())).reversed()
                        .thenComparing(lectura -> orden.getOrDefault(lectura.getDocumento() != null ? lectura.getDocumento().getId() : null, Integer.MAX_VALUE)))
                .findFirst()
                .orElse(null);
    }

    private DocumentoVehiculoLectura mejorLecturaVehiculo(List<Documento> documentosVehiculo) {
        List<Long> documentoIds = documentosVehiculo.stream().map(Documento::getId).toList();
        if (documentoIds.isEmpty()) {
            return null;
        }
        Map<Long, Integer> orden = new HashMap<>();
        for (int index = 0; index < documentoIds.size(); index++) {
            orden.put(documentoIds.get(index), index);
        }
        return vehiculoLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .filter(this::vehiculoUsable)
                .sorted(Comparator
                        .comparingInt(this::vehiculoScore).reversed()
                        .thenComparing(Comparator.comparing((DocumentoVehiculoLectura lectura) -> confianza(lectura.getConfianzaGlobal())).reversed())
                        .thenComparing(lectura -> orden.getOrDefault(lectura.getDocumento() != null ? lectura.getDocumento().getId() : null, Integer.MAX_VALUE)))
                .findFirst()
                .orElse(null);
    }

    private List<DocumentoRolesLectura> lecturasRolesUsables(List<Documento> documentosRoles) {
        List<Long> documentoIds = documentosRoles.stream().map(Documento::getId).toList();
        if (documentoIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> orden = new HashMap<>();
        for (int index = 0; index < documentoIds.size(); index++) {
            orden.put(documentoIds.get(index), index);
        }
        return rolesLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .filter(this::rolesUsables)
                .sorted(Comparator
                        .comparing((DocumentoRolesLectura lectura) -> confianza(lectura.getConfianzaGlobal())).reversed()
                        .thenComparing(lectura -> orden.getOrDefault(lectura.getDocumento() != null ? lectura.getDocumento().getId() : null, Integer.MAX_VALUE)))
                .toList();
    }

    private BatecomPartes detectarPartesBatecom(
            List<DocumentoRolesLectura> lecturas,
            Map<String, IdentidadSolicitud> identidades
    ) {
        BatecomPartes mejor = null;
        double mejorConfianza = 0;
        for (DocumentoRolesLectura lecturaBate : lecturas) {
            String compraventa = normalizarIdentificador(lecturaBate.getCompradorIdentificador());
            if (compraventa == null) {
                continue;
            }
            for (DocumentoRolesLectura lecturaCom : lecturas) {
                if (mismaLectura(lecturaBate, lecturaCom)) {
                    continue;
                }
                String vendedorCom = normalizarIdentificador(lecturaCom.getVendedorIdentificador());
                if (!compraventa.equals(vendedorCom)) {
                    continue;
                }
                PersonaSolicitud vendedor = personaDesdeLectura(lecturaBate, true, identidades);
                PersonaSolicitud intermediario = personaCompraventaBatecom(lecturaBate, lecturaCom, identidades);
                PersonaSolicitud comprador = personaDesdeLectura(lecturaCom, false, identidades);
                double confianzaMedia = (confianza(lecturaBate.getConfianzaGlobal()) + confianza(lecturaCom.getConfianzaGlobal())) / 2;
                if (mejor == null || confianzaMedia > mejorConfianza) {
                    mejor = new BatecomPartes(vendedor, intermediario, comprador, lecturaBate, lecturaCom);
                    mejorConfianza = confianzaMedia;
                }
            }
        }
        return mejor;
    }

    private boolean mismaLectura(DocumentoRolesLectura first, DocumentoRolesLectura second) {
        Long firstId = first != null && first.getDocumento() != null ? first.getDocumento().getId() : null;
        Long secondId = second != null && second.getDocumento() != null ? second.getDocumento().getId() : null;
        return firstId != null && firstId.equals(secondId);
    }

    private PersonaSolicitud personaCompraventaBatecom(
            DocumentoRolesLectura lecturaBate,
            DocumentoRolesLectura lecturaCom,
            Map<String, IdentidadSolicitud> identidades
    ) {
        String identificador = normalizarIdentificador(lecturaBate.getCompradorIdentificador());
        IdentidadSolicitud identidad = identificador != null ? identidades.get(identificador) : null;
        String nombreIdentidad = nombreCompletoIdentidad(identidad);
        String nombreBate = normalizarNombreRol(lecturaBate.getCompradorNombre(), identificador);
        String nombreCom = normalizarNombreRol(lecturaCom.getVendedorNombre(), identificador);
        String nombreRoles = nombreMasCompleto(nombreBate, nombreCom, null);
        String nombreCatalogo = nombreCatalogoGestion(identificador);
        String direccionIdentidad = identidad != null ? identidad.direccionTexto() : null;
        String direccionBate = normalizarTexto(lecturaBate.getCompradorDireccion());
        String direccionCom = normalizarTexto(lecturaCom.getVendedorDireccion());
        return new PersonaSolicitud(
                identificador,
                nombreMasCompleto(nombreIdentidad, nombreRoles, nombreCatalogo),
                direccionMasCompleta(direccionIdentidad, direccionBate, direccionCom)
        );
    }

    private boolean identidadUsable(DocumentoIdentidadLectura lectura) {
        return lectura != null
                && normalizarIdentificador(lectura.getIdentificador()) != null
                && identificadorValido(normalizarIdentificador(lectura.getIdentificador()))
                && confianza(lectura.getConfianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD;
    }

    private boolean identidadUsable(IdentidadSolicitud identidad) {
        return identidad != null
                && identidad.identificador() != null
                && identificadorValido(identidad.identificador())
                && confianza(identidad.confianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD;
    }

    private boolean rolesUsables(DocumentoRolesLectura lectura) {
        if (lectura == null) {
            return false;
        }
        String vendedor = normalizarIdentificador(lectura.getVendedorIdentificador());
        String comprador = normalizarIdentificador(lectura.getCompradorIdentificador());
        return !lectura.isRequiereRevision()
                && confianza(lectura.getConfianzaGlobal()) >= CONFIANZA_MINIMA_ROLES
                && vendedor != null
                && comprador != null
                && identificadorValido(vendedor)
                && identificadorValido(comprador)
                && !vendedor.equals(comprador)
                && !enBlanco(lectura.getVendedorNombre())
                && !enBlanco(lectura.getCompradorNombre());
    }

    private boolean vehiculoUsable(DocumentoVehiculoLectura lectura) {
        return lectura != null
                && !lectura.isRequiereRevision()
                && confianza(lectura.getConfianzaGlobal()) >= 0.75
                && vehiculoScore(lectura) > 0;
    }

    private int vehiculoScore(DocumentoVehiculoLectura lectura) {
        if (lectura == null) {
            return 0;
        }
        int score = 0;
        if (!enBlanco(lectura.getMatricula())) {
            score += 2;
        }
        if (!enBlanco(lectura.getMarca())) {
            score += 2;
        }
        if (!enBlanco(lectura.getModeloVehiculo())) {
            score += 2;
        }
        String bastidor = normalizarIdentificador(lectura.getBastidor());
        if (bastidor != null && bastidor.length() >= 6) {
            score += 3;
        }
        return score;
    }

    private List<String> faltasCorroboracionIdentidad(
            Solicitud solicitud,
            PersonaSolicitud vendedor,
            PersonaSolicitud comprador,
            Map<String, IdentidadSolicitud> identidades
    ) {
        List<String> faltas = new ArrayList<>();
        if (!identidadCorroboraRol(solicitud, vendedor.identificador(), identidades)) {
            faltas.add("No se aplica el vendedor: su DNI/CIF no esta corroborado por identidad leida ni por el cliente de la solicitud.");
        }
        if (!identidadCorroboraRol(solicitud, comprador.identificador(), identidades)) {
            faltas.add("No se aplica el comprador: su DNI/CIF no esta corroborado por identidad leida ni por el cliente de la solicitud.");
        }
        return faltas;
    }

    private List<String> faltasCorroboracionIdentidadBatecom(
            Solicitud solicitud,
            BatecomPartes partes,
            Map<String, IdentidadSolicitud> identidades
    ) {
        List<String> faltas = new ArrayList<>();
        if (!identidadCorroboraRol(solicitud, partes.vendedor().identificador(), identidades)) {
            faltas.add("No se aplica el vendedor inicial: su DNI/CIF no esta corroborado por identidad leida ni por el cliente de la solicitud.");
        }
        if (!identidadCorroboraRol(solicitud, partes.compraventa().identificador(), identidades)) {
            faltas.add("No se aplica la compraventa: su DNI/CIF no esta corroborado por identidad leida ni por el cliente de la solicitud.");
        }
        if (!identidadCorroboraRol(solicitud, partes.comprador().identificador(), identidades)) {
            faltas.add("No se aplica el comprador final: su DNI/CIF no esta corroborado por identidad leida ni por el cliente de la solicitud.");
        }
        return faltas;
    }

    private boolean identidadCorroboraRol(Solicitud solicitud, String identificador, Map<String, IdentidadSolicitud> identidades) {
        if (identificador == null) {
            return false;
        }
        if (identidades.containsKey(identificador)) {
            return true;
        }
        return solicitud.getCliente() != null
                && identificador.equals(normalizarIdentificador(solicitud.getCliente().getNif()));
    }

    private List<String> avisosNombreIdentidad(
            DocumentoRolesLectura lecturaRoles,
            Map<String, IdentidadSolicitud> identidades
    ) {
        List<String> avisos = new ArrayList<>();
        avisarNombreIdentidad(lecturaRoles.getVendedorIdentificador(), lecturaRoles.getVendedorNombre(), "vendedor", identidades, avisos);
        avisarNombreIdentidad(lecturaRoles.getCompradorIdentificador(), lecturaRoles.getCompradorNombre(), "comprador", identidades, avisos);
        return avisos;
    }

    private void avisarNombreIdentidad(
            String identificador,
            String nombreRoles,
            String etiqueta,
            Map<String, IdentidadSolicitud> identidades,
            List<String> avisos
    ) {
        String identificadorNormalizado = normalizarIdentificador(identificador);
        if (identificadorNormalizado == null) {
            return;
        }
        IdentidadSolicitud identidad = identidades.get(identificadorNormalizado);
        if (identidad == null) {
            return;
        }
        String nombreIdentidad = nombreCompletoIdentidad(identidad);
        String nombreRol = normalizarNombreRol(nombreRoles, identificadorNormalizado);
        if (nombreIdentidad != null && nombreRol != null && !nombresCompatibles(nombreIdentidad, nombreRol)) {
            avisos.add("Aviso: el nombre del " + etiqueta + " difiere entre DNI/CIF y contrato/factura. Revisa la lectura antes de convertir.");
        }
    }

    private PersonaSolicitud personaDesdeLectura(
            DocumentoRolesLectura lectura,
            boolean vendedor,
            Map<String, IdentidadSolicitud> identidades
    ) {
        String identificador = normalizarIdentificador(vendedor ? lectura.getVendedorIdentificador() : lectura.getCompradorIdentificador());
        IdentidadSolicitud identidad = identificador != null ? identidades.get(identificador) : null;
        String nombreIdentidad = nombreCompletoIdentidad(identidad);
        String direccionIdentidad = identidad != null ? identidad.direccionTexto() : null;
        String nombreRoles = normalizarNombreRol(vendedor ? lectura.getVendedorNombre() : lectura.getCompradorNombre(), identificador);
        String nombreCatalogo = nombreCatalogoGestion(identificador);
        String direccionRoles = normalizarTexto(vendedor ? lectura.getVendedorDireccion() : lectura.getCompradorDireccion());
        return new PersonaSolicitud(
                identificador,
                nombreMasCompleto(nombreIdentidad, nombreRoles, nombreCatalogo),
                direccionMasCompleta(direccionIdentidad, direccionRoles)
        );
    }

    private void validarPersona(PersonaSolicitud persona, String etiqueta) {
        if (persona == null || enBlanco(persona.identificador()) || enBlanco(persona.nombre())) {
            throw new OperacionInvalidaException("Faltan datos suficientes del " + etiqueta + ".");
        }
    }

    private boolean solicitudYaCoincide(Solicitud solicitud, PersonaSolicitud vendedor, PersonaSolicitud comprador) {
        return bloqueCoincide(
                solicitud.getInteresado1Rol(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Nombre(), solicitud.getInteresado1Direccion(),
                vendedor, RolInteresado.VENDEDOR)
                && bloqueCoincide(
                solicitud.getInteresado2Rol(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Nombre(), solicitud.getInteresado2Direccion(),
                comprador, RolInteresado.COMPRADOR)
                || bloqueCoincide(
                solicitud.getInteresado1Rol(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Nombre(), solicitud.getInteresado1Direccion(),
                comprador, RolInteresado.COMPRADOR)
                && bloqueCoincide(
                solicitud.getInteresado2Rol(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Nombre(), solicitud.getInteresado2Direccion(),
                vendedor, RolInteresado.VENDEDOR);
    }

    private boolean solicitudBatecomYaCoincide(Solicitud solicitud, BatecomPartes partes) {
        return solicitudContienePersona(solicitud, RolInteresado.VENDEDOR, partes.vendedor())
                && solicitudContienePersona(solicitud, RolInteresado.COMPRAVENTA, partes.compraventa())
                && solicitudContienePersona(solicitud, RolInteresado.COMPRADOR, partes.comprador());
    }

    private boolean solicitudContienePersona(Solicitud solicitud, RolInteresado rol, PersonaSolicitud persona) {
        return bloqueCoincide(solicitud.getInteresado1Rol(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Nombre(), solicitud.getInteresado1Direccion(), persona, rol)
                || bloqueCoincide(solicitud.getInteresado2Rol(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Nombre(), solicitud.getInteresado2Direccion(), persona, rol)
                || bloqueCoincide(solicitud.getInteresado3Rol(), solicitud.getInteresado3Dni(), solicitud.getInteresado3Nombre(), solicitud.getInteresado3Direccion(), persona, rol);
    }

    private boolean bloqueCoincide(
            RolInteresado rolActual,
            String dniActual,
            String nombreActual,
            String direccionActual,
            PersonaSolicitud persona,
            RolInteresado rolEsperado
    ) {
        String nombreNormalizado = normalizarNombre(nombreActual);
        String direccionNormalizada = normalizarTexto(direccionActual);
        return rolActual == rolEsperado
                && normalizarIdentificador(dniActual) != null
                && normalizarIdentificador(dniActual).equals(persona.identificador())
                && NombrePersonaNormalizer.equivalentes(persona.nombre(), nombreNormalizado)
                && (persona.direccion() == null || persona.direccion().equals(direccionNormalizada));
    }

    private void aplicarPersona(Solicitud solicitud, RolInteresado rol, PersonaSolicitud persona) {
        int slot = encontrarSlot(solicitud, rol, persona.identificador());
        if (slot == 1) {
            validarConflictoDni(solicitud.getInteresado1Dni(), persona.identificador(), rol);
            solicitud.setInteresado1Rol(rol);
            solicitud.setInteresado1Dni(persona.identificador());
            solicitud.setInteresado1Nombre(persona.nombre());
            if (persona.direccion() != null) {
                solicitud.setInteresado1Direccion(persona.direccion());
            }
            return;
        }
        if (slot == 2) {
            validarConflictoDni(solicitud.getInteresado2Dni(), persona.identificador(), rol);
            solicitud.setInteresado2Rol(rol);
            solicitud.setInteresado2Dni(persona.identificador());
            solicitud.setInteresado2Nombre(persona.nombre());
            if (persona.direccion() != null) {
                solicitud.setInteresado2Direccion(persona.direccion());
            }
            return;
        }
        if (slot == 3) {
            validarConflictoDni(solicitud.getInteresado3Dni(), persona.identificador(), rol);
            solicitud.setInteresado3Rol(rol);
            solicitud.setInteresado3Dni(persona.identificador());
            solicitud.setInteresado3Nombre(persona.nombre());
            if (persona.direccion() != null) {
                solicitud.setInteresado3Direccion(persona.direccion());
            }
            return;
        }
        throw new OperacionInvalidaException("No hay un bloque libre para aplicar " + rol.name() + " en la solicitud.");
    }

    private int encontrarSlot(Solicitud solicitud, RolInteresado rol, String identificador) {
        String dni1 = normalizarIdentificador(solicitud.getInteresado1Dni());
        String dni2 = normalizarIdentificador(solicitud.getInteresado2Dni());
        String dni3 = normalizarIdentificador(solicitud.getInteresado3Dni());
        if (identificador.equals(dni1)) {
            return solicitud.getInteresado1Rol() == null || solicitud.getInteresado1Rol() == rol ? 1 : 0;
        }
        if (identificador.equals(dni2)) {
            return solicitud.getInteresado2Rol() == null || solicitud.getInteresado2Rol() == rol ? 2 : 0;
        }
        if (identificador.equals(dni3)) {
            return solicitud.getInteresado3Rol() == null || solicitud.getInteresado3Rol() == rol ? 3 : 0;
        }
        if (solicitud.getInteresado1Rol() == rol) {
            return 1;
        }
        if (solicitud.getInteresado2Rol() == rol) {
            return 2;
        }
        if (solicitud.getInteresado3Rol() == rol) {
            return 3;
        }
        if (bloqueVacio(solicitud.getInteresado1Rol(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Nombre())) {
            return 1;
        }
        if (bloqueVacio(solicitud.getInteresado2Rol(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Nombre())) {
            return 2;
        }
        if (bloqueVacio(solicitud.getInteresado3Rol(), solicitud.getInteresado3Dni(), solicitud.getInteresado3Nombre())) {
            return 3;
        }
        return 0;
    }

    private void validarConflictoDni(String actual, String nuevo, RolInteresado rol) {
        String normalizadoActual = normalizarIdentificador(actual);
        if (normalizadoActual != null && !normalizadoActual.equals(nuevo)) {
            throw new OperacionInvalidaException("Ya existe otro DNI/CIF como " + rol.name() + " en la solicitud.");
        }
    }

    private boolean aplicarVehiculoSiProcede(Solicitud solicitud, DocumentoVehiculoLectura lecturaVehiculo, List<String> detalles) {
        if (lecturaVehiculo == null) {
            return false;
        }
        boolean actualizado = false;
        String origen = lecturaVehiculo.getDocumento() != null ? nombreDocumento(lecturaVehiculo.getDocumento()) : "documentacion de vehiculo";

        String matriculaLeida = normalizarMatricula(lecturaVehiculo.getMatricula());
        String matriculaSolicitud = normalizarMatricula(solicitud.getMatricula());
        if (matriculaLeida != null && matriculaSolicitud == null) {
            solicitud.setMatricula(matriculaLeida);
            actualizado = true;
        } else if (matriculaLeida != null && !matriculaSolicitud.equals(matriculaLeida)) {
            detalles.add("La matricula de " + origen + " (" + matriculaLeida + ") no coincide con la solicitud (" + matriculaSolicitud + ").");
        }

        String marcaLeida = TextNormalizer.upperOrNull(lecturaVehiculo.getMarca());
        String marcaSolicitud = TextNormalizer.upperOrNull(solicitud.getVehiculoMarca());
        if (marcaLeida != null && marcaSolicitud == null) {
            solicitud.setVehiculoMarca(marcaLeida);
            actualizado = true;
        } else if (marcaLeida != null && !marcaSolicitud.equals(marcaLeida)) {
            detalles.add("La marca de " + origen + " (" + marcaLeida + ") no coincide con la guardada (" + marcaSolicitud + ").");
        }

        String modeloLeido = TextNormalizer.upperOrNull(lecturaVehiculo.getModeloVehiculo());
        String modeloSolicitud = TextNormalizer.upperOrNull(solicitud.getVehiculoModelo());
        if (modeloLeido != null && modeloSolicitud == null) {
            solicitud.setVehiculoModelo(modeloLeido);
            actualizado = true;
        } else if (modeloLeido != null && !modeloSolicitud.equals(modeloLeido)) {
            detalles.add("El modelo de " + origen + " (" + modeloLeido + ") no coincide con el guardado (" + modeloSolicitud + ").");
        }

        String bastidorLeido = normalizarIdentificador(lecturaVehiculo.getBastidor());
        String bastidorSolicitud = normalizarIdentificador(solicitud.getVehiculoBastidor());
        if (bastidorLeido != null && bastidorLeido.length() >= 6 && bastidorSolicitud == null) {
            solicitud.setVehiculoBastidor(bastidorLeido);
            actualizado = true;
        } else if (bastidorLeido != null && bastidorLeido.length() >= 6 && !bastidorSolicitud.equals(bastidorLeido)) {
            detalles.add("El bastidor de " + origen + " (" + bastidorLeido + ") no coincide con el guardado (" + bastidorSolicitud + ").");
        }

        if (actualizado) {
            detalles.add("Datos de vehiculo leidos desde " + origen + ".");
        }
        return actualizado;
    }

    private boolean aplicarVehiculoSiProcede(Solicitud solicitud, DocumentoRolesLectura lecturaRoles, List<String> detalles) {
        if (lecturaRoles == null) {
            return false;
        }
        boolean actualizado = false;
        String matriculaLeida = normalizarMatricula(lecturaRoles.getMatricula());
        String matriculaSolicitud = normalizarMatricula(solicitud.getMatricula());
        if (matriculaLeida != null && matriculaSolicitud == null) {
            solicitud.setMatricula(matriculaLeida);
            actualizado = true;
        } else if (matriculaLeida != null && !matriculaSolicitud.equals(matriculaLeida)) {
            detalles.add("La matricula de factura/contrato (" + matriculaLeida + ") no coincide con la solicitud (" + matriculaSolicitud + ").");
        }

        String bastidorLeido = normalizarIdentificador(lecturaRoles.getBastidor());
        String bastidorSolicitud = normalizarIdentificador(solicitud.getVehiculoBastidor());
        if (bastidorLeido != null && bastidorLeido.length() >= 6 && bastidorSolicitud == null) {
            solicitud.setVehiculoBastidor(bastidorLeido);
            detalles.add("Bastidor detectado y guardado en la solicitud: " + bastidorLeido + ".");
            actualizado = true;
        } else if (bastidorLeido != null && bastidorLeido.length() >= 6 && !bastidorSolicitud.equals(bastidorLeido)) {
            detalles.add("El bastidor de factura/contrato (" + bastidorLeido + ") no coincide con el guardado (" + bastidorSolicitud + ").");
        }
        String precioLeido = TextNormalizer.upperOrNull(lecturaRoles.getValorDeclarado());
        String precioSolicitud = TextNormalizer.upperOrNull(solicitud.getOperacionPrecioVenta());
        if (precioLeido != null && precioSolicitud == null) {
            solicitud.setOperacionPrecioVenta(precioLeido);
            detalles.add("Precio de venta detectado y guardado en la solicitud: " + precioLeido + ".");
            actualizado = true;
        } else if (precioLeido != null && !precioSolicitud.equals(precioLeido)) {
            detalles.add("El precio de factura/contrato (" + precioLeido + ") no coincide con el guardado (" + precioSolicitud + ").");
        }
        return actualizado;
    }

    private void guardarVehiculoActualizado(Solicitud solicitud, Usuario usuario, String descripcionHistorial) {
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(usuario);
        solicitudRepository.save(solicitud);
        historialCambioService.registrarCambioSolicitud(
                solicitud,
                usuario,
                "IA DOCUMENTACION",
                descripcionHistorial);
    }

    private void marcarIdentidadesUsadas(Map<String, IdentidadSolicitud> identidades, PersonaSolicitud... personas) {
        for (PersonaSolicitud persona : personas) {
            IdentidadSolicitud identidad = identidades.get(persona.identificador());
            DocumentoIdentidadLectura lectura = identidad != null ? identidad.lecturaPrincipal() : null;
            if (lectura == null || lectura.getId() == null) {
                continue;
            }
            lectura.setRequiereRevision(false);
            lectura.setVinculadoAutomaticamente(true);
            lectura.setMensaje("Identidad leida y usada para actualizar la solicitud.");
            identidadLecturaRepository.save(lectura);
        }
    }

    private SolicitudDocumentacionIaResponse respuesta(
            Long solicitudId,
            int documentosIdentidad,
            int documentosRoles,
            Contadores contadores,
            boolean datosAplicados,
            boolean yaEstabaCorrecta,
            boolean requiereRevision,
            String mensaje,
            List<String> detalles
    ) {
        return SolicitudDocumentacionIaResponse.builder()
                .solicitudId(solicitudId)
                .documentosIdentidad(documentosIdentidad)
                .documentosRoles(documentosRoles)
                .lecturasIdentidadNuevas(contadores.identidadNueva)
                .lecturasIdentidadReutilizadas(contadores.identidadReutilizada)
                .lecturasRolesNuevas(contadores.rolesNueva)
                .lecturasRolesReutilizadas(contadores.rolesReutilizada)
                .datosAplicados(datosAplicados)
                .yaEstabaCorrecta(yaEstabaCorrecta)
                .requiereRevision(requiereRevision)
                .mensaje(mensaje)
                .detalles(List.copyOf(detalles))
                .build();
    }

    private boolean esIdentidad(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.DNI || tipoDocumento == TipoDocumento.CIF;
    }

    private boolean esDocumentoRoles(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.FACTURA || tipoDocumento == TipoDocumento.CONTRATO_COMPRAVENTA;
    }

    private boolean esDocumentoVehiculo(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.PERMISO_CIRCULACION
                || tipoDocumento == TipoDocumento.FICHA_TECNICA
                || tipoDocumento == TipoDocumento.INFORME_DGT;
    }

    private boolean bloqueVacio(RolInteresado rol, String dni, String nombre) {
        return rol == null && enBlanco(dni) && enBlanco(nombre);
    }

    private String nombreCompletoIdentidad(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return null;
        }
        String razonSocial = normalizarNombre(lectura.getRazonSocial());
        if (razonSocial != null) {
            return razonSocial;
        }
        String joined = String.join(" ",
                List.of(
                        lectura.getNombre() != null ? lectura.getNombre() : "",
                        lectura.getApellido1() != null ? lectura.getApellido1() : "",
                        lectura.getApellido2() != null ? lectura.getApellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        return normalizarNombre(joined);
    }

    private String nombreCompletoIdentidad(IdentidadDetectada identidad) {
        if (identidad == null) {
            return null;
        }
        String razonSocial = normalizarNombre(identidad.razonSocial());
        if (razonSocial != null) {
            return razonSocial;
        }
        String joined = String.join(" ",
                List.of(
                        identidad.nombre() != null ? identidad.nombre() : "",
                        identidad.apellido1() != null ? identidad.apellido1() : "",
                        identidad.apellido2() != null ? identidad.apellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        return normalizarNombre(joined);
    }

    private String nombreCompletoIdentidad(IdentidadSolicitud identidad) {
        return identidad != null ? identidad.nombreCompleto() : null;
    }

    private String nombreCatalogoGestion(String identificador) {
        String normalizado = normalizarIdentificador(identificador);
        if (normalizado == null) {
            return null;
        }
        return gestionPersonaCatalogoRepository.findFirstByNifNormalizadoOrderByIdAsc(normalizado)
                .map(this::nombreCompletoCatalogo)
                .orElse(null);
    }

    private String nombreCompletoCatalogo(GestionPersonaCatalogo catalogo) {
        if (catalogo == null) {
            return null;
        }
        String joined = String.join(" ",
                List.of(
                        catalogo.getNombre() != null ? catalogo.getNombre() : "",
                        catalogo.getApellido1RazonSocial() != null ? catalogo.getApellido1RazonSocial() : "",
                        catalogo.getApellido2() != null ? catalogo.getApellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        return normalizarNombre(joined);
    }

    private String normalizarNombreRol(String value, String identificador) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (esPersonaFisica(identificador) && trimmed.contains(",")) {
            String[] partes = trimmed.split(",", 2);
            if (partes.length == 2 && !partes[0].isBlank() && !partes[1].isBlank()) {
                return normalizarNombre(partes[1] + " " + partes[0]);
            }
        }
        return normalizarNombre(trimmed);
    }

    private boolean esPersonaFisica(String identificador) {
        String normalizado = normalizarIdentificador(identificador);
        return normalizado != null
                && (normalizado.matches("[0-9]{8}[A-Z]") || normalizado.matches("[XYZ][0-9]{7}[A-Z]"));
    }

    private String nombreMasCompleto(String nombreIdentidad, String nombreRoles, String nombreCatalogo) {
        List<NombreCandidato> candidatos = new ArrayList<>();
        agregarCandidato(candidatos, nombreRoles, 3);
        agregarCandidato(candidatos, nombreIdentidad, 2);
        agregarCandidato(candidatos, nombreCatalogo, 1);
        if (candidatos.isEmpty()) {
            return null;
        }
        String base = nombreIdentidad != null ? nombreIdentidad : nombreRoles != null ? nombreRoles : nombreCatalogo;
        return candidatos.stream()
                .filter(candidato -> nombresCompatibles(base, candidato.valor()))
                .max(Comparator
                        .comparingInt((NombreCandidato candidato) -> tokensNombre(candidato.valor()).size())
                        .thenComparingInt(NombreCandidato::prioridad))
                .map(NombreCandidato::valor)
                .orElse(candidatos.get(0).valor());
    }

    private void agregarCandidato(List<NombreCandidato> candidatos, String value, int prioridad) {
        String normalizado = normalizarNombre(value);
        if (normalizado != null) {
            candidatos.add(new NombreCandidato(normalizado, prioridad));
        }
    }

    private boolean nombresCompatibles(String referencia, String candidato) {
        String ref = normalizarNombre(referencia);
        String cand = normalizarNombre(candidato);
        if (ref == null || cand == null) {
            return ref == null && cand == null;
        }
        if (ref.equals(cand)) {
            return true;
        }
        Set<String> refTokens = tokensNombre(ref);
        Set<String> candTokens = tokensNombre(cand);
        return candTokens.containsAll(refTokens) || refTokens.containsAll(candTokens);
    }

    private Set<String> tokensNombre(String value) {
        String normalizado = normalizarNombre(value);
        if (normalizado == null) {
            return Set.of();
        }
        String sinAcentos = Normalizer.normalize(normalizado, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return Arrays.stream(sinAcentos.split("\\s+"))
                .map(token -> token.replaceAll("[^A-Z0-9]", ""))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalizarNombre(String value) {
        return NombrePersonaNormalizer.normalizar(value);
    }

    private String normalizarTexto(String value) {
        String normalizado = value != null ? value.replaceAll("\\s+", " ").trim() : null;
        return TextNormalizer.upperOrNull(normalizado);
    }

    private String normalizarIdentificador(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private boolean identificadorValido(String value) {
        String identificador = normalizarIdentificador(value);
        if (identificador == null) {
            return false;
        }
        if (identificador.matches("[0-9]{8}[A-Z]") || identificador.matches("[XYZ][0-9]{7}[A-Z]")) {
            return dniNieValidator.esValido(identificador);
        }
        return identificador.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private String normalizarMatricula(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private double confianza(Double value) {
        return value != null ? value : 0.0;
    }

    private boolean enBlanco(String value) {
        return value == null || value.isBlank();
    }

    private boolean esBatecom(Solicitud solicitud) {
        return solicitud.getTipoTramite() != null
                && solicitud.getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM;
    }

    private String direccionMasCompleta(String... values) {
        String mejor = null;
        int mejorScore = -1;
        for (String value : values) {
            String normalizada = normalizarTexto(value);
            if (normalizada == null) {
                continue;
            }
            int score = puntuacionDireccion(normalizada);
            if (score > mejorScore) {
                mejor = normalizada;
                mejorScore = score;
            }
        }
        return mejor;
    }

    private int puntuacionDireccion(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String normalized = normalizarTexto(value);
        if (normalized == null) {
            return 0;
        }
        int score = Math.min(8, normalized.split("[\\s,]+").length)
                + Math.min(5, normalized.length() / 20);
        if (normalized.matches(".*\\d.*")) {
            score += 6;
        }
        if (normalized.matches(".*\\b\\d{5}\\b.*")) {
            score += 4;
        }
        if (normalized.matches(".*\\b(CALLE|CARRETERA|CTRA|AVENIDA|AVDA|PLAZA|PASEO|CAMINO|RAMBLA|TRAVESIA|URBANIZACION|URB)\\b.*")
                || normalized.contains("C/")
                || normalized.contains("C.")) {
            score += 5;
        }
        return score;
    }

    private String nombreDocumento(Documento documento) {
        return documento.getNombreArchivoOriginal() != null && !documento.getNombreArchivoOriginal().isBlank()
                ? documento.getNombreArchivoOriginal()
                : "documento " + documento.getId();
    }

    private String mensaje(RuntimeException exception) {
        return exception.getMessage() != null ? exception.getMessage() : "error no especificado";
    }

    private static class Contadores {
        private int identidadNueva;
        private int identidadReutilizada;
        private int rolesNueva;
        private int rolesReutilizada;
        private int vehiculoNueva;
        private int vehiculoReutilizada;
    }

    private record PersonaSolicitud(String identificador, String nombre, String direccion) {
    }

    private record IdentidadSolicitud(
            String identificador,
            String nombreCompleto,
            String direccionTexto,
            Double confianzaGlobal,
            boolean requiereRevision,
            DocumentoIdentidadLectura lecturaPrincipal
    ) {
    }

    private record BatecomPartes(
            PersonaSolicitud vendedor,
            PersonaSolicitud compraventa,
            PersonaSolicitud comprador,
            DocumentoRolesLectura lecturaBate,
            DocumentoRolesLectura lecturaCom
    ) {
    }

    private record NombreCandidato(String valor, int prioridad) {
    }

    private record DocumentacionSolicitudCliente(int documentosIdentidad, int documentosVehiculo, int documentosRoles, List<String> bloqueos) {
        private boolean suficiente() {
            return bloqueos.isEmpty();
        }
    }
}
