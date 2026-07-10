package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ActualizacionDocumentalExpedienteResponse;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Vehiculo;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.VehiculoRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.service.VehiculoService;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import com.example.gestor_documental.util.NombrePersonaNormalizer;
import com.example.gestor_documental.util.TextNormalizer;
import com.example.gestor_documental.validation.DniNieValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpedienteDocumentacionActualizacionService {

    private static final Set<TipoDocumento> DOCUMENTOS_IDENTIDAD = EnumSet.of(TipoDocumento.DNI, TipoDocumento.CIF);
    private static final Set<TipoDocumento> DOCUMENTOS_OPERACION = EnumSet.of(TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.FACTURA);
    private static final Set<TipoDocumento> DOCUMENTOS_VEHICULO = EnumSet.of(
            TipoDocumento.PERMISO_CIRCULACION,
            TipoDocumento.FICHA_TECNICA,
            TipoDocumento.INFORME_DGT);
    private static final double CONFIANZA_MINIMA_IDENTIDAD = 0.80;
    private static final double CONFIANZA_MINIMA_ROLES = 0.90;
    private static final double CONFIANZA_MINIMA_ROLES_CON_IDENTIDAD = 0.80;

    private final ExpedienteService expedienteService;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    private final DocumentoRolesLecturaRepository documentoRolesLecturaRepository;
    private final DocumentoVehiculoLecturaRepository documentoVehiculoLecturaRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final InteresadoRepository interesadoRepository;
    private final VehiculoRepository vehiculoRepository;
    private final DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    private final DocumentoRolesLecturaService documentoRolesLecturaService;
    private final DocumentoVehiculoLecturaService documentoVehiculoLecturaService;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalExpedienteService;
    private final HistorialCambioService historialCambioService;
    private final VehiculoService vehiculoService;
    private final DniNieValidator dniNieValidator;

    @Transactional
    public ActualizacionDocumentalExpedienteResponse actualizarDesdeDocumentos(Long expedienteId, Usuario admin) {
        return actualizarDesdeDocumentos(expedienteId, admin, false);
    }

    @Transactional
    public ActualizacionDocumentalExpedienteResponse actualizarDesdeDocumentos(Long expedienteId, Usuario admin, boolean forzarRelectura) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, admin)) {
            throw new AccesoDenegadoException("No tienes permiso para modificar este expediente");
        }

        List<Documento> documentos = documentoRepository.findByExpedienteId(expedienteId).stream()
                .filter(documento -> documento.getId() != null && documento.getTipoDocumento() != null)
                .sorted(Comparator.comparing(Documento::getFechaSubida, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (documentos.stream().noneMatch(documento -> DOCUMENTOS_IDENTIDAD.contains(documento.getTipoDocumento())
                || DOCUMENTOS_OPERACION.contains(documento.getTipoDocumento())
                || DOCUMENTOS_VEHICULO.contains(documento.getTipoDocumento()))) {
            throw new OperacionInvalidaException("No hay DNI/CIF, contrato/factura ni documentacion de vehiculo en el expediente.");
        }

        Contadores contadores = new Contadores();
        int datosAplicados = 0;
        boolean requiereRevision = false;
        List<String> detalles = new ArrayList<>();
        if (forzarRelectura) {
            detalles.add("Se forzo la relectura de DNI/CIF, roles y vehiculo ya leidos previamente.");
        }

        for (Documento documento : documentos) {
            if (!DOCUMENTOS_IDENTIDAD.contains(documento.getTipoDocumento())) {
                continue;
            }
            try {
                boolean existia = documentoIdentidadLecturaRepository.findByDocumentoId(documento.getId()).isPresent();
                DocumentoIdentidadLecturaResponse lectura = documentoIdentidadLecturaService.leerIdentidad(documento.getId(), forzarRelectura, admin);
                contadores.identidadesLeidas++;
                if (existia && !forzarRelectura) {
                    contadores.identidadReutilizada++;
                } else {
                    contadores.identidadNueva++;
                }
                if (lectura != null && lectura.isRequiereRevision()) {
                    requiereRevision = true;
                    detalles.add(nombreDocumento(documento) + ": identidad leida, requiere revision.");
                }
            } catch (RuntimeException exception) {
                requiereRevision = true;
                detalles.add(nombreDocumento(documento) + ": no se pudo leer identidad (" + mensaje(exception) + ").");
            }
        }

        for (Documento documento : documentos) {
            if (!DOCUMENTOS_VEHICULO.contains(documento.getTipoDocumento())) {
                continue;
            }
            try {
                boolean existia = documentoVehiculoLecturaRepository.findByDocumentoId(documento.getId()).isPresent();
                DocumentoVehiculoLecturaResponse lectura = documentoVehiculoLecturaService.leerVehiculo(documento.getId(), forzarRelectura, admin);
                contadores.vehiculosLeidos++;
                if (existia && !forzarRelectura) {
                    contadores.vehiculoReutilizada++;
                } else {
                    contadores.vehiculoNueva++;
                }
                if (lectura == null || lectura.isRequiereRevision()) {
                    requiereRevision = true;
                    detalles.add(nombreDocumento(documento) + ": vehiculo leido, requiere revision.");
                    continue;
                }
                if (aplicarVehiculoSiProcede(expediente, lectura, detalles)) {
                    datosAplicados++;
                }
            } catch (RuntimeException exception) {
                requiereRevision = true;
                detalles.add(nombreDocumento(documento) + ": no se pudo leer vehiculo (" + mensaje(exception) + ").");
            }
        }

        Map<String, IdentidadExpediente> identidades = mejoresIdentidades(documentos);

        for (Documento documento : documentos) {
            if (!DOCUMENTOS_OPERACION.contains(documento.getTipoDocumento())) {
                continue;
            }
            try {
                boolean existia = documentoRolesLecturaRepository.findByDocumentoId(documento.getId()).isPresent();
                String interesadosAntes = resumenInteresados(expedienteId);
                DocumentoRolesLecturaResponse lectura = documentoRolesLecturaService.leerRoles(documento.getId(), forzarRelectura, admin);
                contadores.operacionesLeidas++;
                if (existia && !forzarRelectura) {
                    contadores.rolesReutilizada++;
                } else {
                    contadores.rolesNueva++;
                }
                if (lectura == null) {
                    requiereRevision = true;
                    detalles.add(nombreDocumento(documento) + ": roles no leidos.");
                    continue;
                }
                DocumentoRolesLectura lecturaRoles = documentoRolesLecturaRepository.findByDocumentoId(documento.getId())
                        .orElseThrow(() -> new OperacionInvalidaException("Primero debes leer roles del contrato o factura."));
                ConsolidacionRoles consolidacion = consolidarRoles(expediente, documento, lecturaRoles, identidades, admin);
                String interesadosDespues = resumenInteresados(expedienteId);
                if (consolidacion.datosAplicados() || !Objects.equals(interesadosAntes, interesadosDespues)) {
                    datosAplicados++;
                    detalles.add(nombreDocumento(documento) + ": interesados aplicados desde contrato/factura.");
                } else {
                    detalles.add(nombreDocumento(documento) + ": interesados ya estaban aplicados; se comprobaron sin cambios.");
                }
                detalles.addAll(consolidacion.avisos());
            } catch (RuntimeException exception) {
                requiereRevision = true;
                detalles.add(nombreDocumento(documento) + ": no se pudieron aplicar datos (" + mensaje(exception) + ").");
            }
        }

        requisitoDocumentalExpedienteService.sincronizarYListar(
                expediente,
                expedienteInteresadoRepository.findByExpedienteId(expedienteId),
                documentoRepository.findByExpedienteId(expedienteId),
                admin
        );

        if (datosAplicados > 0) {
            expediente.setFechaUltimaModificacion(java.time.LocalDateTime.now());
            expediente.setModificadoPor(admin);
            expedienteService.guardar(expediente);
        }

        historialCambioService.registrarCambioExpediente(
                expediente,
                admin,
                "IA DOCUMENTACION",
                "Se revisaron documentos existentes: " + contadores.identidadesLeidas + " identidades, "
                        + contadores.vehiculosLeidos + " documentos de vehiculo, "
                        + contadores.operacionesLeidas + " contratos/facturas, " + datosAplicados + " aplicaciones.");

        boolean yaEstabaCorrecta = datosAplicados == 0 && !requiereRevision && documentosProcesablesLeidos(contadores);
        String mensaje = requiereRevision
                ? "Lecturas realizadas, pero falta revisar algunos datos antes de consolidarlo todo."
                : datosAplicados > 0
                ? "Datos actualizados desde la documentacion del expediente."
                : "Sin cambios: la documentacion ya estaba procesada correctamente.";

        return ActualizacionDocumentalExpedienteResponse.builder()
                .identidadesLeidas(contadores.identidadesLeidas)
                .operacionesLeidas(contadores.operacionesLeidas)
                .vehiculosLeidos(contadores.vehiculosLeidos)
                .lecturasIdentidadNuevas(contadores.identidadNueva)
                .lecturasIdentidadReutilizadas(contadores.identidadReutilizada)
                .lecturasRolesNuevas(contadores.rolesNueva)
                .lecturasRolesReutilizadas(contadores.rolesReutilizada)
                .lecturasVehiculoNuevas(contadores.vehiculoNueva)
                .lecturasVehiculoReutilizadas(contadores.vehiculoReutilizada)
                .datosAplicados(datosAplicados)
                .yaEstabaCorrecta(yaEstabaCorrecta)
                .requiereRevision(requiereRevision)
                .mensaje(mensaje)
                .avisos(detalles)
                .detalles(detalles)
                .build();
    }

    private Map<String, IdentidadExpediente> mejoresIdentidades(List<Documento> documentos) {
        List<Long> documentoIds = documentos.stream()
                .filter(documento -> DOCUMENTOS_IDENTIDAD.contains(documento.getTipoDocumento()))
                .map(Documento::getId)
                .filter(id -> id != null)
                .toList();
        if (documentoIds.isEmpty()) {
            return Map.of();
        }
        Map<String, IdentidadExpediente> result = new HashMap<>();
        documentoIdentidadLecturaRepository.findByDocumentoIdIn(documentoIds).forEach(lectura -> {
            agregarIdentidad(result, identidadDesdeLecturaPrincipal(lectura));
            DocumentoIdentidadLecturaJson.extraer(lectura).stream()
                    .map(identidad -> identidadDesdeDetectada(lectura, identidad))
                    .forEach(identidad -> agregarIdentidad(result, identidad));
        });
        return result;
    }

    private void agregarIdentidad(Map<String, IdentidadExpediente> identidades, IdentidadExpediente identidad) {
        if (!identidadUsable(identidad)) {
            return;
        }
        IdentidadExpediente actual = identidades.get(identidad.identificador());
        if (actual == null) {
            identidades.put(identidad.identificador(), identidad);
            return;
        }
        identidades.put(identidad.identificador(), combinarIdentidad(actual, identidad));
    }

    private IdentidadExpediente combinarIdentidad(IdentidadExpediente actual, IdentidadExpediente candidata) {
        boolean candidataMasFiable = confianza(candidata.confianzaGlobal()) > confianza(actual.confianzaGlobal());
        IdentidadExpediente base = candidataMasFiable ? candidata : actual;
        IdentidadExpediente respaldo = candidataMasFiable ? actual : candidata;
        return new IdentidadExpediente(
                base.identificador(),
                nombreMasCompleto(base.nombreCompleto(), respaldo.nombreCompleto()),
                direccionMasCompleta(base.direccionTexto(), respaldo.direccionTexto()),
                Math.max(confianza(actual.confianzaGlobal()), confianza(candidata.confianzaGlobal())),
                base.lecturaPrincipal() != null ? base.lecturaPrincipal() : respaldo.lecturaPrincipal()
        );
    }

    private IdentidadExpediente identidadDesdeLecturaPrincipal(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return null;
        }
        return new IdentidadExpediente(
                normalizarIdentificador(lectura.getIdentificador()),
                nombreCompletoIdentidad(lectura),
                normalizarTexto(lectura.getDireccionTexto()),
                lectura.getConfianzaGlobal(),
                lectura
        );
    }

    private IdentidadExpediente identidadDesdeDetectada(DocumentoIdentidadLectura lectura, IdentidadDetectada identidad) {
        if (identidad == null) {
            return null;
        }
        return new IdentidadExpediente(
                normalizarIdentificador(identidad.identificador()),
                nombreCompletoIdentidad(identidad),
                normalizarTexto(identidad.direccionTexto()),
                identidad.confianzaGlobal(),
                mismaIdentidad(lectura, identidad) ? lectura : null
        );
    }

    private boolean mismaIdentidad(DocumentoIdentidadLectura lectura, IdentidadDetectada identidad) {
        String principal = lectura != null ? normalizarIdentificador(lectura.getIdentificador()) : null;
        String detectada = identidad != null ? normalizarIdentificador(identidad.identificador()) : null;
        return principal != null && principal.equals(detectada);
    }

    private ConsolidacionRoles consolidarRoles(
            Expediente expediente,
            Documento documento,
            DocumentoRolesLectura lectura,
            Map<String, IdentidadExpediente> identidades,
            Usuario admin
    ) {
        PersonaExpediente vendedor = personaDesdeLectura(lectura, true, identidades);
        PersonaExpediente comprador = personaDesdeLectura(lectura, false, identidades);
        validarLecturaRolesUsable(lectura, vendedor, comprador, expediente, identidades);
        validarPersona(vendedor, "vendedor");
        validarPersona(comprador, "comprador");
        if (vendedor.identificador().equals(comprador.identificador())) {
            throw new OperacionInvalidaException("Comprador y vendedor tienen el mismo DNI/CIF.");
        }

        List<String> avisos = new ArrayList<>();
        validarIdentidadCorroborada(expediente, vendedor.identificador(), identidades, "vendedor");
        validarIdentidadCorroborada(expediente, comprador.identificador(), identidades, "comprador");
        avisarNombreIdentidad(lectura.getVendedorIdentificador(), lectura.getVendedorNombre(), "vendedor", identidades, avisos);
        avisarNombreIdentidad(lectura.getCompradorIdentificador(), lectura.getCompradorNombre(), "comprador", identidades, avisos);

        boolean actualizado = false;
        Interesado interesadoVendedor = obtenerOCrearInteresado(vendedor);
        Interesado interesadoComprador = obtenerOCrearInteresado(comprador);
        if (interesadoVendedor.getId().equals(interesadoComprador.getId())) {
            throw new OperacionInvalidaException("Comprador y vendedor apuntan al mismo interesado.");
        }
        actualizado |= aplicarRelacion(expediente, interesadoVendedor, rolVendedor(documento));
        actualizado |= aplicarRelacion(expediente, interesadoComprador, rolComprador(documento));
        actualizado |= vincularIdentidadLeida(expediente, interesadoVendedor, identidades.get(vendedor.identificador()));
        actualizado |= vincularIdentidadLeida(expediente, interesadoComprador, identidades.get(comprador.identificador()));

        lectura.setVendedorInteresado(interesadoVendedor);
        lectura.setCompradorInteresado(interesadoComprador);
        lectura.setAplicadoExpediente(true);
        lectura.setFechaAplicacion(java.time.LocalDateTime.now());
        lectura.setMensaje("Datos aplicados al expediente desde la consolidacion documental.");
        documentoRolesLecturaRepository.save(lectura);

        if (actualizado) {
            historialCambioService.registrarCambioExpediente(
                    expediente,
                    admin,
                    "IA APLICAR DATOS",
                    "Se aplicaron " + rolVendedor(documento).name().toLowerCase(Locale.ROOT) + " y "
                            + rolComprador(documento).name().toLowerCase(Locale.ROOT) + " desde " + nombreDocumento(documento) + ".");
        }
        return new ConsolidacionRoles(actualizado, avisos);
    }

    private void validarLecturaRolesUsable(
            DocumentoRolesLectura lectura,
            PersonaExpediente vendedor,
            PersonaExpediente comprador,
            Expediente expediente,
            Map<String, IdentidadExpediente> identidades
    ) {
        if (lectura == null) {
            throw new OperacionInvalidaException("Primero debes leer roles del contrato o factura.");
        }
        String vendedorId = vendedor != null ? vendedor.identificador() : null;
        String compradorId = comprador != null ? comprador.identificador() : null;
        if (!identificadorValido(vendedorId) || !identificadorValido(compradorId)) {
            throw new OperacionInvalidaException("El DNI/NIE/CIF leido no supera las validaciones basicas.");
        }
        if (enBlanco(lectura.getVendedorNombre()) || enBlanco(lectura.getCompradorNombre())) {
            throw new OperacionInvalidaException("Faltan nombres suficientes de comprador o vendedor.");
        }
        if (vendedorId.equals(compradorId)) {
            throw new OperacionInvalidaException("Comprador y vendedor tienen el mismo DNI/CIF.");
        }
        boolean rolesCorroborados = identidadCorroboraRol(expediente, vendedorId, identidades)
                && identidadCorroboraRol(expediente, compradorId, identidades);
        double confianza = confianza(lectura.getConfianzaGlobal());
        if (rolesCorroborados && confianza >= CONFIANZA_MINIMA_ROLES_CON_IDENTIDAD) {
            return;
        }
        if (lectura.isRequiereRevision()) {
            throw new OperacionInvalidaException("La lectura requiere revision manual antes de aplicar.");
        }
        if (confianza < CONFIANZA_MINIMA_ROLES) {
            throw new OperacionInvalidaException("La confianza de la lectura no es suficiente para aplicar datos.");
        }
    }

    private PersonaExpediente personaDesdeLectura(
            DocumentoRolesLectura lectura,
            boolean vendedor,
            Map<String, IdentidadExpediente> identidades
    ) {
        String identificador = normalizarIdentificador(vendedor ? lectura.getVendedorIdentificador() : lectura.getCompradorIdentificador());
        String nombreRoles = normalizarNombre(vendedor ? lectura.getVendedorNombre() : lectura.getCompradorNombre());
        IdentidadExpediente identidad = identificador != null ? identidades.get(identificador) : null;
        if (identidad == null) {
            identidad = identidadPorNombre(nombreRoles, identidades);
            if (identidad != null) {
                identificador = identidad.identificador();
            }
        }
        String nombreIdentidad = identidad != null ? identidad.nombreCompleto() : null;
        String direccionIdentidad = identidad != null ? identidad.direccionTexto() : null;
        String direccionRoles = normalizarTexto(vendedor ? lectura.getVendedorDireccion() : lectura.getCompradorDireccion());
        return new PersonaExpediente(
                identificador,
                nombreMasCompleto(nombreIdentidad, nombreRoles),
                direccionMasCompleta(direccionIdentidad, direccionRoles)
        );
    }

    private IdentidadExpediente identidadPorNombre(String nombreRoles, Map<String, IdentidadExpediente> identidades) {
        String nombre = normalizarNombre(nombreRoles);
        if (nombre == null || identidades.isEmpty()) {
            return null;
        }
        return identidades.values().stream()
                .filter(identidad -> identidad.nombreCompleto() != null)
                .map(identidad -> new IdentidadPuntuada(identidad, puntuacionNombre(nombre, identidad.nombreCompleto())))
                .filter(item -> item.puntuacion() >= 3)
                .max(java.util.Comparator.comparingInt(IdentidadPuntuada::puntuacion))
                .map(IdentidadPuntuada::identidad)
                .orElse(null);
    }

    private int puntuacionNombre(String nombreRoles, String nombreIdentidad) {
        Set<String> roles = tokensNombre(nombreRoles);
        Set<String> identidad = tokensNombre(nombreIdentidad);
        if (roles.isEmpty() || identidad.isEmpty()) {
            return 0;
        }
        int comunes = 0;
        for (String token : roles) {
            if (identidad.contains(token)) {
                comunes++;
            }
        }
        return comunes;
    }

    private Set<String> tokensNombre(String value) {
        String normalizado = normalizarNombre(value);
        if (normalizado == null) {
            return Set.of();
        }
        String sinAcentos = java.text.Normalizer.normalize(normalizado, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return java.util.Arrays.stream(sinAcentos.split("\\s+"))
                .map(token -> token.replaceAll("[^A-Z0-9]", ""))
                .filter(token -> !token.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private void validarPersona(PersonaExpediente persona, String etiqueta) {
        if (persona == null || enBlanco(persona.identificador()) || enBlanco(persona.nombre())) {
            throw new OperacionInvalidaException("Faltan datos suficientes del " + etiqueta + ".");
        }
    }

    private void validarIdentidadCorroborada(
            Expediente expediente,
            String identificador,
            Map<String, IdentidadExpediente> identidades,
            String etiqueta
    ) {
        if (identidadCorroboraRol(expediente, identificador, identidades)) {
            return;
        }
        throw new OperacionInvalidaException("No se aplica el " + etiqueta + ": falta DNI/CIF leido que corrobore el rol.");
    }

    private boolean identidadCorroboraRol(Expediente expediente, String identificador, Map<String, IdentidadExpediente> identidades) {
        if (identificador != null && identidades.containsKey(identificador)) {
            return true;
        }
        String nifCliente = expediente.getCliente() != null ? normalizarIdentificador(expediente.getCliente().getNif()) : null;
        if (identificador != null && identificador.equals(nifCliente)) {
            return true;
        }
        return false;
    }

    private void avisarNombreIdentidad(
            String identificador,
            String nombreRoles,
            String etiqueta,
            Map<String, IdentidadExpediente> identidades,
            List<String> avisos
    ) {
        String identificadorNormalizado = normalizarIdentificador(identificador);
        if (identificadorNormalizado == null) {
            return;
        }
        IdentidadExpediente identidad = identidades.get(identificadorNormalizado);
        if (identidad == null) {
            return;
        }
        String nombreIdentidad = identidad.nombreCompleto();
        String nombreRol = normalizarNombre(nombreRoles);
        if (nombreIdentidad != null && nombreRol != null && !NombrePersonaNormalizer.equivalentes(nombreIdentidad, nombreRol)) {
            avisos.add("Aviso: el nombre del " + etiqueta + " difiere entre DNI/CIF y contrato/factura. Revisa la lectura.");
        }
    }

    private Interesado obtenerOCrearInteresado(PersonaExpediente persona) {
        Interesado interesado = interesadoRepository.findByDni(persona.identificador()).orElse(null);
        boolean guardar = false;
        if (interesado == null) {
            interesado = new Interesado();
            interesado.setDni(persona.identificador());
            interesado.setNombre(TextNormalizer.upperOrNull(persona.nombre()));
            interesado.setDireccion(persona.direccion());
            interesado.setTipoPersona(inferirTipoPersona(persona.identificador()));
            guardar = true;
        } else {
            guardar |= setIfBlank(interesado.getNombre(), TextNormalizer.upperOrNull(persona.nombre()), interesado::setNombre);
            if (persona.direccion() != null && !persona.direccion().equals(interesado.getDireccion())) {
                interesado.setDireccion(persona.direccion());
                guardar = true;
            }
            if (interesado.getTipoPersona() == null) {
                interesado.setTipoPersona(inferirTipoPersona(persona.identificador()));
                guardar = true;
            }
        }
        return guardar ? interesadoRepository.save(interesado) : interesado;
    }

    private boolean aplicarRelacion(Expediente expediente, Interesado interesado, RolInteresado rol) {
        List<ExpedienteInteresado> relaciones = expedienteInteresadoRepository.findByExpedienteId(expediente.getId());
        ExpedienteInteresado mismaPersona = relaciones.stream()
                .filter(relacion -> relacion.getInteresado() != null && relacion.getInteresado().getId().equals(interesado.getId()))
                .findFirst()
                .orElse(null);
        if (mismaPersona != null) {
            if (mismaPersona.getRol() != null && mismaPersona.getRol() != rol) {
                throw new OperacionInvalidaException("El interesado " + interesado.getNombre() + " ya figura como " + mismaPersona.getRol().name() + ".");
            }
            if (mismaPersona.getRol() == null) {
                mismaPersona.setRol(rol);
                expedienteInteresadoRepository.save(mismaPersona);
                return true;
            }
            return false;
        }

        ExpedienteInteresado mismoRol = relaciones.stream()
                .filter(relacion -> relacion.getRol() == rol)
                .filter(relacion -> relacion.getInteresado() != null)
                .findFirst()
                .orElse(null);
        if (mismoRol != null && !mismoRol.getInteresado().getId().equals(interesado.getId())) {
            throw new OperacionInvalidaException("Ya existe otro interesado como " + rol.name() + ". Revisa manualmente.");
        }

        ExpedienteInteresado nuevaRelacion = new ExpedienteInteresado();
        nuevaRelacion.setExpediente(expediente);
        nuevaRelacion.setInteresado(interesado);
        nuevaRelacion.setRol(rol);
        expedienteInteresadoRepository.save(nuevaRelacion);
        return true;
    }

    private boolean vincularIdentidadLeida(Expediente expediente, Interesado interesado, IdentidadExpediente identidad) {
        if (identidad == null || identidad.lecturaPrincipal() == null) {
            return false;
        }
        DocumentoIdentidadLectura lectura = identidad.lecturaPrincipal();
        boolean actualizado = false;
        if (!Objects.equals(lectura.getInteresadoVinculado() != null ? lectura.getInteresadoVinculado().getId() : null, interesado.getId())) {
            lectura.setInteresadoVinculado(interesado);
            actualizado = true;
        }
        if (lectura.isRequiereRevision()) {
            lectura.setRequiereRevision(false);
            actualizado = true;
        }
        if (!lectura.isVinculadoAutomaticamente()) {
            lectura.setVinculadoAutomaticamente(true);
            actualizado = true;
        }
        lectura.setMensaje("Identidad leida y usada para actualizar el expediente.");
        documentoIdentidadLecturaRepository.save(lectura);

        Documento documento = lectura.getDocumento();
        if (documento != null && documento.getExpediente() != null && Objects.equals(documento.getExpediente().getId(), expediente.getId())) {
            boolean documentoActualizado = false;
            if (documento.getInteresado() == null || !Objects.equals(documento.getInteresado().getId(), interesado.getId())) {
                documento.setInteresado(interesado);
                documentoActualizado = true;
            }
            if (lectura.getTipoDocumentoDetectado() != null && lectura.getTipoDocumentoDetectado() != documento.getTipoDocumento()) {
                documento.setTipoDocumento(lectura.getTipoDocumentoDetectado());
                documentoActualizado = true;
            }
            if (documentoActualizado) {
                documentoRepository.save(documento);
                actualizado = true;
            }
        }
        return actualizado;
    }

    private RolInteresado rolVendedor(Documento documento) {
        if (documento.getOperacion() != null
                && documento.getOperacion().getTipo() == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM) {
            return RolInteresado.COMPRAVENTA;
        }
        return RolInteresado.VENDEDOR;
    }

    private RolInteresado rolComprador(Documento documento) {
        if (documento.getOperacion() != null
                && documento.getOperacion().getTipo() == TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE) {
            return RolInteresado.COMPRAVENTA;
        }
        return RolInteresado.COMPRADOR;
    }

    private boolean identidadUsable(IdentidadExpediente identidad) {
        return identidad != null
                && identidad.identificador() != null
                && identificadorValido(identidad.identificador())
                && confianza(identidad.confianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD;
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

    private String nombreMasCompleto(String first, String second) {
        String primero = normalizarNombre(first);
        String segundo = normalizarNombre(second);
        if (primero == null) {
            return segundo;
        }
        if (segundo == null) {
            return primero;
        }
        if (!NombrePersonaNormalizer.equivalentes(primero, segundo)) {
            return primero.length() >= segundo.length() ? primero : segundo;
        }
        return segundo.length() > primero.length() ? segundo : primero;
    }

    private String direccionMasCompleta(String... values) {
        String mejor = null;
        int mejorScore = -1;
        for (String value : values) {
            String normalizada = normalizarTexto(value);
            if (normalizada == null) {
                continue;
            }
            int score = normalizada.length() + (normalizada.matches(".*\\d.*") ? 20 : 0);
            if (score > mejorScore) {
                mejor = normalizada;
                mejorScore = score;
            }
        }
        return mejor;
    }

    private String normalizarNombre(String value) {
        return NombrePersonaNormalizer.normalizar(value);
    }

    private String normalizarTexto(String value) {
        String normalizado = value != null ? value.replaceAll("\\s+", " ").trim() : null;
        return TextNormalizer.upperOrNull(normalizado);
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

    private TipoPersona inferirTipoPersona(String identificador) {
        String normalizado = normalizarIdentificador(identificador);
        if (normalizado != null && normalizado.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]")) {
            return TipoPersona.EMPRESA;
        }
        return TipoPersona.PARTICULAR;
    }

    private double confianza(Double value) {
        return value != null ? value : 0.0;
    }

    private boolean enBlanco(String value) {
        return value == null || value.isBlank();
    }

    private boolean aplicarVehiculoSiProcede(Expediente expediente, DocumentoVehiculoLecturaResponse lectura, List<String> detalles) {
        boolean actualizado = false;
        String matriculaLeida = normalizarMatricula(lectura.getMatricula());
        String matriculaExpediente = normalizarMatricula(expediente.getMatricula());
        if (matriculaLeida != null && matriculaExpediente == null) {
            expediente.setMatricula(matriculaLeida);
            matriculaExpediente = matriculaLeida;
            actualizado = true;
            detalles.add("Matricula detectada y guardada en el expediente: " + matriculaLeida + ".");
        } else if (matriculaLeida != null && !matriculaExpediente.equals(matriculaLeida)) {
            detalles.add("La matricula leida (" + matriculaLeida + ") no coincide con el expediente (" + matriculaExpediente + ").");
            return actualizado;
        }

        boolean vehiculoVinculado = false;
        Vehiculo vehiculo = expediente.getVehiculo();
        if (vehiculo == null && matriculaExpediente != null) {
            vehiculo = vehiculoService.obtenerOCrearPorMatricula(matriculaExpediente);
            expediente.setVehiculo(vehiculo);
            vehiculoVinculado = vehiculo != null;
            actualizado = true;
        }
        if (vehiculo == null) {
            return actualizado;
        }

        boolean datosVehiculoActualizados = false;
        datosVehiculoActualizados |= setIfBlank(vehiculo.getMarca(), TextNormalizer.upperOrNull(lectura.getMarca()), vehiculo::setMarca);
        datosVehiculoActualizados |= setIfBlank(vehiculo.getModelo(), TextNormalizer.upperOrNull(lectura.getModeloVehiculo()), vehiculo::setModelo);
        datosVehiculoActualizados |= setIfBlank(vehiculo.getBastidor(), normalizarIdentificador(lectura.getBastidor()), vehiculo::setBastidor);
        actualizado |= datosVehiculoActualizados;
        if (datosVehiculoActualizados) {
            vehiculoRepository.save(vehiculo);
            detalles.add("Datos de vehiculo actualizados desde documentacion: "
                    + java.util.stream.Stream.of(vehiculo.getMarca(), vehiculo.getModelo(), vehiculo.getBastidor())
                    .filter(valor -> valor != null && !valor.isBlank())
                    .collect(java.util.stream.Collectors.joining(" / "))
                    + ".");
        } else if (vehiculoVinculado) {
            detalles.add("Vehiculo vinculado al expediente por matricula " + vehiculo.getMatricula() + ".");
        }
        return actualizado;
    }

    private boolean setIfBlank(String actual, String nuevo, java.util.function.Consumer<String> setter) {
        if ((actual == null || actual.isBlank()) && nuevo != null && !nuevo.isBlank()) {
            setter.accept(nuevo);
            return true;
        }
        return false;
    }

    private boolean documentosProcesablesLeidos(Contadores contadores) {
        return contadores.identidadesLeidas > 0 || contadores.operacionesLeidas > 0 || contadores.vehiculosLeidos > 0;
    }

    private String nombreDocumento(Documento documento) {
        return documento.getNombreArchivoOriginal() != null && !documento.getNombreArchivoOriginal().isBlank()
                ? documento.getNombreArchivoOriginal()
                : "documento " + documento.getId();
    }

    private String mensaje(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof OperacionInvalidaException && current.getMessage() != null) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return exception.getMessage() != null ? exception.getMessage() : "error no especificado";
    }

    private String resumenInteresados(Long expedienteId) {
        return expedienteInteresadoRepository.findByExpedienteId(expedienteId).stream()
                .map(relacion -> {
                    String rol = relacion.getRol() != null ? relacion.getRol().name() : "SIN_ROL";
                    if (relacion.getInteresado() == null) {
                        return rol + ":SIN_INTERESADO";
                    }
                    return rol + ":"
                            + normalizarIdentificador(relacion.getInteresado().getDni()) + ":"
                            + TextNormalizer.upperOrNull(relacion.getInteresado().getNombre()) + ":"
                            + TextNormalizer.upperOrNull(relacion.getInteresado().getDireccion());
                })
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private String normalizarMatricula(String value) {
        if (value == null) {
            return null;
        }
        String normalizada = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizada.isBlank() ? null : normalizada;
    }

    private String normalizarIdentificador(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private static class Contadores {
        private int identidadesLeidas;
        private int operacionesLeidas;
        private int vehiculosLeidos;
        private int identidadNueva;
        private int identidadReutilizada;
        private int rolesNueva;
        private int rolesReutilizada;
        private int vehiculoNueva;
        private int vehiculoReutilizada;
    }

    private record IdentidadExpediente(
            String identificador,
            String nombreCompleto,
            String direccionTexto,
            Double confianzaGlobal,
            DocumentoIdentidadLectura lecturaPrincipal
    ) {
    }

    private record PersonaExpediente(String identificador, String nombre, String direccion) {
    }

    private record ConsolidacionRoles(boolean datosAplicados, List<String> avisos) {
    }

    private record IdentidadPuntuada(IdentidadExpediente identidad, int puntuacion) {
    }
}
