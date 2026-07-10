package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ActualizacionDocumentalExpedienteResponse;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Vehiculo;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.VehiculoRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.service.VehiculoService;
import com.example.gestor_documental.util.TextNormalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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

    private final ExpedienteService expedienteService;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    private final DocumentoRolesLecturaRepository documentoRolesLecturaRepository;
    private final DocumentoVehiculoLecturaRepository documentoVehiculoLecturaRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final VehiculoRepository vehiculoRepository;
    private final DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    private final DocumentoRolesLecturaService documentoRolesLecturaService;
    private final DocumentoVehiculoLecturaService documentoVehiculoLecturaService;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalExpedienteService;
    private final HistorialCambioService historialCambioService;
    private final VehiculoService vehiculoService;

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

        for (Documento documento : documentos) {
            if (!DOCUMENTOS_OPERACION.contains(documento.getTipoDocumento())) {
                continue;
            }
            try {
                boolean existia = documentoRolesLecturaRepository.findByDocumentoId(documento.getId()).isPresent();
                DocumentoRolesLectura lecturaAnterior = documentoRolesLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
                boolean aplicadaAntes = lecturaAnterior != null && lecturaAnterior.isAplicadoExpediente();
                String interesadosAntes = resumenInteresados(expedienteId);
                DocumentoRolesLecturaResponse lectura = documentoRolesLecturaService.leerRoles(documento.getId(), forzarRelectura, admin);
                contadores.operacionesLeidas++;
                if (existia && !forzarRelectura) {
                    contadores.rolesReutilizada++;
                } else {
                    contadores.rolesNueva++;
                }
                if (lectura == null || (!lectura.isAplicable() && !lectura.isAplicadoExpediente())) {
                    requiereRevision = true;
                    detalles.add(nombreDocumento(documento) + ": roles leidos, no aplicables automaticamente"
                            + motivo(lectura != null ? lectura.getMotivoAplicacion() : null) + ".");
                    continue;
                }
                documentoRolesLecturaService.aplicarDatos(documento.getId(), admin);
                String interesadosDespues = resumenInteresados(expedienteId);
                if (!aplicadaAntes || !Objects.equals(interesadosAntes, interesadosDespues)) {
                    datosAplicados++;
                } else {
                    detalles.add(nombreDocumento(documento) + ": interesados ya estaban aplicados; se comprobaron sin cambios.");
                }
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

    private String motivo(String motivo) {
        return motivo != null && !motivo.isBlank() ? ": " + motivo : "";
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
}
