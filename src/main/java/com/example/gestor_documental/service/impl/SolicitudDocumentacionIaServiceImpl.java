package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SolicitudDocumentacionIaServiceImpl implements SolicitudDocumentacionIaService {

    private static final double CONFIANZA_MINIMA_IDENTIDAD = 0.80;
    private static final double CONFIANZA_MINIMA_ROLES = 0.78;

    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final DocumentoRolesLecturaRepository rolesLecturaRepository;
    private final DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    private final DocumentoRolesLecturaService documentoRolesLecturaService;
    private final HistorialCambioService historialCambioService;

    @Override
    @Transactional
    public SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario admin) {
        validarAdmin(admin);
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
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

        if (documentosIdentidad.isEmpty() && documentosRoles.isEmpty()) {
            throw new OperacionInvalidaException("No hay DNI/CIF ni factura/contrato en la solicitud.");
        }

        List<String> detalles = new ArrayList<>();
        Contadores contadores = new Contadores();
        leerIdentidades(documentosIdentidad, admin, contadores, detalles);
        leerRoles(documentosRoles, admin, contadores, detalles);

        Map<String, DocumentoIdentidadLectura> identidades = mejoresIdentidades(documentosIdentidad);
        DocumentoRolesLectura lecturaRoles = mejorLecturaRoles(documentosRoles);
        if (lecturaRoles == null) {
            detalles.add(documentosRoles.isEmpty()
                    ? "Falta factura o contrato para determinar comprador y vendedor."
                    : "La factura/contrato aun no tiene comprador y vendedor con confianza suficiente.");
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

        boolean yaCorrecta = solicitudYaCoincide(solicitud, vendedor, comprador);
        if (yaCorrecta) {
            marcarIdentidadesUsadas(identidades, vendedor, comprador);
            detalles.add("La solicitud ya tenia comprador y vendedor coherentes con la lectura valida.");
            return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, false, true, false,
                    "Sin cambios: la documentacion ya estaba procesada correctamente.", detalles);
        }

        aplicarPersona(solicitud, RolInteresado.VENDEDOR, vendedor);
        aplicarPersona(solicitud, RolInteresado.COMPRADOR, comprador);
        aplicarMatriculaSiProcede(solicitud, lecturaRoles, detalles);
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(admin);
        solicitudRepository.save(solicitud);
        marcarIdentidadesUsadas(identidades, vendedor, comprador);

        historialCambioService.registrarCambioSolicitud(
                solicitud,
                admin,
                "IA DOCUMENTACION",
                "Se actualizaron comprador y vendedor desde DNI/CIF y factura/contrato.");
        detalles.add("Datos de comprador y vendedor actualizados en la solicitud.");
        return respuesta(solicitudId, documentosIdentidad.size(), documentosRoles.size(), contadores, true, false, false,
                "Datos actualizados desde la documentacion.", detalles);
    }

    private void validarAdmin(Usuario admin) {
        if (admin == null || admin.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede procesar documentacion con IA.");
        }
    }

    private void validarSolicitudAbierta(Solicitud solicitud) {
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA || solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede procesar una solicitud cerrada.");
        }
        if (solicitud.getExpediente() != null) {
            throw new OperacionInvalidaException("La solicitud ya tiene un expediente asociado.");
        }
    }

    private void leerIdentidades(List<Documento> documentos, Usuario admin, Contadores contadores, List<String> detalles) {
        for (Documento documento : documentos) {
            boolean existente = identidadLecturaRepository.findByDocumentoId(documento.getId()).isPresent();
            try {
                documentoIdentidadLecturaService.leerIdentidad(documento.getId(), false, admin);
                if (existente) {
                    contadores.identidadReutilizada++;
                } else {
                    contadores.identidadNueva++;
                }
            } catch (RuntimeException exception) {
                detalles.add("No se pudo leer identidad en " + nombreDocumento(documento) + ": " + mensaje(exception));
            }
        }
    }

    private void leerRoles(List<Documento> documentos, Usuario admin, Contadores contadores, List<String> detalles) {
        for (Documento documento : documentos) {
            boolean existente = rolesLecturaRepository.findByDocumentoId(documento.getId()).isPresent();
            try {
                documentoRolesLecturaService.leerRoles(documento.getId(), false, admin);
                if (existente) {
                    contadores.rolesReutilizada++;
                } else {
                    contadores.rolesNueva++;
                }
            } catch (RuntimeException exception) {
                detalles.add("No se pudo leer roles en " + nombreDocumento(documento) + ": " + mensaje(exception));
            }
        }
    }

    private Map<String, DocumentoIdentidadLectura> mejoresIdentidades(List<Documento> documentosIdentidad) {
        List<Long> documentoIds = documentosIdentidad.stream().map(Documento::getId).toList();
        if (documentoIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DocumentoIdentidadLectura> result = new HashMap<>();
        identidadLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .filter(this::identidadUsable)
                .forEach(lectura -> {
                    String identificador = normalizarIdentificador(lectura.getIdentificador());
                    DocumentoIdentidadLectura actual = result.get(identificador);
                    if (actual == null || confianza(lectura.getConfianzaGlobal()) > confianza(actual.getConfianzaGlobal())) {
                        result.put(identificador, lectura);
                    }
                });
        return result;
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

    private boolean identidadUsable(DocumentoIdentidadLectura lectura) {
        return lectura != null
                && normalizarIdentificador(lectura.getIdentificador()) != null
                && confianza(lectura.getConfianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD;
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
                && !vendedor.equals(comprador)
                && !enBlanco(lectura.getVendedorNombre())
                && !enBlanco(lectura.getCompradorNombre());
    }

    private PersonaSolicitud personaDesdeLectura(
            DocumentoRolesLectura lectura,
            boolean vendedor,
            Map<String, DocumentoIdentidadLectura> identidades
    ) {
        String identificador = normalizarIdentificador(vendedor ? lectura.getVendedorIdentificador() : lectura.getCompradorIdentificador());
        DocumentoIdentidadLectura identidad = identificador != null ? identidades.get(identificador) : null;
        String nombreIdentidad = nombreCompletoIdentidad(identidad);
        String direccionIdentidad = identidad != null ? normalizarTexto(identidad.getDireccionTexto()) : null;
        String nombreRoles = normalizarTexto(vendedor ? lectura.getVendedorNombre() : lectura.getCompradorNombre());
        String direccionRoles = normalizarTexto(vendedor ? lectura.getVendedorDireccion() : lectura.getCompradorDireccion());
        return new PersonaSolicitud(
                identificador,
                nombreIdentidad != null ? nombreIdentidad : nombreRoles,
                direccionIdentidad != null ? direccionIdentidad : direccionRoles
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

    private boolean bloqueCoincide(
            RolInteresado rolActual,
            String dniActual,
            String nombreActual,
            String direccionActual,
            PersonaSolicitud persona,
            RolInteresado rolEsperado
    ) {
        String nombreNormalizado = normalizarTexto(nombreActual);
        String direccionNormalizada = normalizarTexto(direccionActual);
        return rolActual == rolEsperado
                && normalizarIdentificador(dniActual) != null
                && normalizarIdentificador(dniActual).equals(persona.identificador())
                && persona.nombre().equals(nombreNormalizado)
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
        throw new OperacionInvalidaException("No hay un bloque libre para aplicar " + rol.name() + " en la solicitud.");
    }

    private int encontrarSlot(Solicitud solicitud, RolInteresado rol, String identificador) {
        String dni1 = normalizarIdentificador(solicitud.getInteresado1Dni());
        String dni2 = normalizarIdentificador(solicitud.getInteresado2Dni());
        if (identificador.equals(dni1)) {
            return solicitud.getInteresado1Rol() == null || solicitud.getInteresado1Rol() == rol ? 1 : 0;
        }
        if (identificador.equals(dni2)) {
            return solicitud.getInteresado2Rol() == null || solicitud.getInteresado2Rol() == rol ? 2 : 0;
        }
        if (solicitud.getInteresado1Rol() == rol) {
            return 1;
        }
        if (solicitud.getInteresado2Rol() == rol) {
            return 2;
        }
        if (bloqueVacio(solicitud.getInteresado1Rol(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Nombre())) {
            return 1;
        }
        if (bloqueVacio(solicitud.getInteresado2Rol(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Nombre())) {
            return 2;
        }
        return 0;
    }

    private void validarConflictoDni(String actual, String nuevo, RolInteresado rol) {
        String normalizadoActual = normalizarIdentificador(actual);
        if (normalizadoActual != null && !normalizadoActual.equals(nuevo)) {
            throw new OperacionInvalidaException("Ya existe otro DNI/CIF como " + rol.name() + " en la solicitud.");
        }
    }

    private void aplicarMatriculaSiProcede(Solicitud solicitud, DocumentoRolesLectura lecturaRoles, List<String> detalles) {
        String matriculaLeida = normalizarMatricula(lecturaRoles.getMatricula());
        String matriculaSolicitud = normalizarMatricula(solicitud.getMatricula());
        if (matriculaLeida == null) {
            return;
        }
        if (matriculaSolicitud == null) {
            solicitud.setMatricula(matriculaLeida);
            return;
        }
        if (!matriculaSolicitud.equals(matriculaLeida)) {
            detalles.add("La matricula de factura/contrato (" + matriculaLeida + ") no coincide con la solicitud (" + matriculaSolicitud + ").");
        }
    }

    private void marcarIdentidadesUsadas(Map<String, DocumentoIdentidadLectura> identidades, PersonaSolicitud... personas) {
        for (PersonaSolicitud persona : personas) {
            DocumentoIdentidadLectura lectura = identidades.get(persona.identificador());
            if (lectura == null) {
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

    private boolean bloqueVacio(RolInteresado rol, String dni, String nombre) {
        return rol == null && enBlanco(dni) && enBlanco(nombre);
    }

    private String nombreCompletoIdentidad(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return null;
        }
        String razonSocial = normalizarTexto(lectura.getRazonSocial());
        if (razonSocial != null) {
            return razonSocial;
        }
        String joined = String.join(" ",
                List.of(
                        lectura.getNombre() != null ? lectura.getNombre() : "",
                        lectura.getApellido1() != null ? lectura.getApellido1() : "",
                        lectura.getApellido2() != null ? lectura.getApellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        return normalizarTexto(joined);
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
    }

    private record PersonaSolicitud(String identificador, String nombre, String direccion) {
    }
}
