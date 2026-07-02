package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.SolicitudPreparacionAccionResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionBloqueResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionDocumentoResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionItemResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionTraspasoResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.SolicitudPreparacionTraspasoService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import com.example.gestor_documental.validation.DniNieValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitudPreparacionTraspasoServiceImpl implements SolicitudPreparacionTraspasoService {

    private static final double CONFIANZA_MINIMA_IDENTIDAD = 0.80;
    private static final double CONFIANZA_MINIMA_ROLES = 0.90;
    private static final Set<TipoDocumento> TIPOS_IDENTIDAD = EnumSet.of(TipoDocumento.DNI, TipoDocumento.CIF);
    private static final Set<TipoDocumento> TIPOS_ROLES = EnumSet.of(TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.FACTURA);

    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final DocumentoRolesLecturaRepository rolesLecturaRepository;
    private final SolicitudService solicitudService;
    private final DniNieValidator dniNieValidator;

    @Override
    @Transactional(readOnly = true)
    public SolicitudPreparacionTraspasoResponse obtenerPreparacion(Long solicitudId, Usuario usuario) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (!solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para consultar esta solicitud");
        }

        List<Documento> documentos = documentoRepository.findBySolicitudId(solicitudId).stream()
                .filter(documento -> documento.getId() != null)
                .toList();
        List<Long> documentoIds = documentos.stream().map(Documento::getId).toList();
        Map<Long, DocumentoIdentidadLectura> lecturasIdentidad = documentoIds.isEmpty()
                ? Map.of()
                : identidadLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                        .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                        .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (first, second) -> first));
        Map<Long, DocumentoRolesLectura> lecturasRoles = documentoIds.isEmpty()
                ? Map.of()
                : rolesLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                        .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                        .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (first, second) -> first));

        PreparacionContext context = new PreparacionContext(
                solicitud,
                documentos,
                lecturasIdentidad,
                lecturasRoles,
                interesados(solicitud),
                rolesEsperados(solicitud)
        );

        List<BloqueCalculo> bloquesCalculados = List.of(
                bloqueInteresados(context),
                bloqueVehiculo(context),
                bloqueOperacion(context)
        );
        List<SolicitudPreparacionBloqueResponse> bloques = bloquesCalculados.stream()
                .map(BloqueCalculo::response)
                .toList();
        List<SolicitudPreparacionDocumentoResponse> documentosGenerables = documentosGenerables(context);

        int total = bloquesCalculados.stream().mapToInt(BloqueCalculo::total).sum()
                + documentosGenerables.stream().mapToInt(SolicitudPreparacionDocumentoResponse::camposTotales).sum();
        int completados = bloquesCalculados.stream().mapToInt(BloqueCalculo::completados).sum()
                + documentosGenerables.stream().mapToInt(SolicitudPreparacionDocumentoResponse::camposCompletos).sum();
        int progreso = total == 0 ? 0 : (int) Math.round((completados * 100.0) / total);
        String estado = estadoGlobal(bloquesCalculados, documentosGenerables);
        SolicitudPreparacionAccionResponse siguienteAccion = siguienteAccion(bloquesCalculados, documentosGenerables);

        return new SolicitudPreparacionTraspasoResponse(
                solicitudId,
                estado,
                progreso,
                siguienteAccion,
                bloques,
                documentosGenerables
        );
    }

    private BloqueCalculo bloqueInteresados(PreparacionContext context) {
        List<ItemCalculo> items = new ArrayList<>();
        Map<RolInteresado, InteresadoSlot> porRol = interesadosPorRol(context.interesados());
        for (RolInteresado rol : context.rolesEsperados()) {
            InteresadoSlot interesado = porRol.get(rol);
            String rolLabel = rolLabel(rol);
            if (interesado == null) {
                items.add(item(
                        "rol_" + rol.name().toLowerCase(Locale.ROOT),
                        capitalize(rolLabel),
                        EstadoItem.BLOQUEANTE,
                        "Falta informar el " + rolLabel + ".",
                        "COMPLETAR_INTERESADO",
                        "Completar " + rolLabel
                ));
                continue;
            }
            items.add(item(
                    "rol_" + rol.name().toLowerCase(Locale.ROOT),
                    capitalize(rolLabel),
                    EstadoItem.OK,
                    nombreCorto(interesado),
                    null,
                    null
            ));
            items.add(itemIdentificador(interesado, rolLabel));
            items.add(itemDocumentoIdentidad(context, interesado, rolLabel));
            items.add(itemDireccion(interesado, rolLabel));
        }
        return bloque("INTERESADOS", "Interesados y roles", items);
    }

    private ItemCalculo itemIdentificador(InteresadoSlot interesado, String rolLabel) {
        String identificador = normalizarIdentificador(interesado.dni());
        if (identificador == null) {
            return item(
                    "identificador_" + interesado.slot(),
                    "Identificacion " + rolLabel,
                    EstadoItem.BLOQUEANTE,
                    "Falta DNI/NIE/CIF del " + rolLabel + ".",
                    "COMPLETAR_INTERESADO",
                    "Anadir identificacion"
            );
        }
        if (!identificadorValido(identificador)) {
            return item(
                    "identificador_" + interesado.slot(),
                    "Identificacion " + rolLabel,
                    EstadoItem.BLOQUEANTE,
                    "El identificador " + identificador + " no pasa la validacion.",
                    "REVISAR_DATO",
                    "Revisar identificador"
            );
        }
        return item(
                "identificador_" + interesado.slot(),
                "Identificacion " + rolLabel,
                EstadoItem.OK,
                identificador,
                null,
                null
        );
    }

    private ItemCalculo itemDocumentoIdentidad(PreparacionContext context, InteresadoSlot interesado, String rolLabel) {
        String identificador = normalizarIdentificador(interesado.dni());
        if (identificador == null || !identificadorValido(identificador)) {
            return item(
                    "soporte_identidad_" + interesado.slot(),
                    "Documento identidad " + rolLabel,
                    EstadoItem.PENDIENTE,
                    "Primero hay que corregir el identificador.",
                    "COMPLETAR_INTERESADO",
                    "Corregir datos"
            );
        }
        if (documentoIdentidadAportado(context, identificador)) {
            return item(
                    "soporte_identidad_" + interesado.slot(),
                    "Documento identidad " + rolLabel,
                    EstadoItem.OK,
                    "DNI/NIE/CIF localizado con lectura valida.",
                    null,
                    null
            );
        }
        return item(
                "soporte_identidad_" + interesado.slot(),
                "Documento identidad " + rolLabel,
                EstadoItem.PENDIENTE,
                "Falta un DNI/NIE/CIF separado o una lectura valida que coincida.",
                "SUBIR_DOCUMENTO",
                "Subir identidad"
        );
    }

    private ItemCalculo itemDireccion(InteresadoSlot interesado, String rolLabel) {
        DireccionEstado direccionEstado = direccionEstado(interesado);
        if (direccionEstado.estado() == EstadoItem.OK) {
            return item(
                    "direccion_" + interesado.slot(),
                    "Direccion " + rolLabel,
                    EstadoItem.OK,
                    direccionEstado.detalle(),
                    null,
                    null
            );
        }
        return item(
                "direccion_" + interesado.slot(),
                "Direccion " + rolLabel,
                direccionEstado.estado(),
                direccionEstado.detalle(),
                "COMPLETAR_DATO",
                "Completar direccion"
        );
    }

    private BloqueCalculo bloqueVehiculo(PreparacionContext context) {
        List<ItemCalculo> items = new ArrayList<>();
        items.add(item(
                "matricula",
                "Matricula",
                texto(context.solicitud().getMatricula()) != null ? EstadoItem.OK : EstadoItem.BLOQUEANTE,
                texto(context.solicitud().getMatricula()) != null ? normalizarTexto(context.solicitud().getMatricula()) : "Falta matricula de la solicitud.",
                texto(context.solicitud().getMatricula()) != null ? null : "COMPLETAR_DATO",
                texto(context.solicitud().getMatricula()) != null ? null : "Completar matricula"
        ));
        items.add(itemDocumentacionVehiculo(context));
        String bastidor = mejorBastidor(context);
        String marca = texto(context.solicitud().getVehiculoMarca());
        String modelo = texto(context.solicitud().getVehiculoModelo());
        items.add(item(
                "bastidor",
                "Bastidor",
                bastidor != null ? EstadoItem.OK : EstadoItem.PENDIENTE,
                bastidor != null ? bastidor : "No consta bastidor estructurado; se pedira para el contrato si hace falta.",
                bastidor != null ? null : "COMPLETAR_DATO",
                bastidor != null ? null : "Completar bastidor"
        ));
        items.add(item(
                "marca_modelo",
                "Marca y modelo",
                marca != null && modelo != null ? EstadoItem.OK : EstadoItem.PENDIENTE,
                marca != null && modelo != null
                        ? normalizarTexto(marca + " " + modelo)
                        : "Falta completar marca y modelo para proponer el contrato.",
                marca != null && modelo != null ? null : "COMPLETAR_DATO",
                marca != null && modelo != null ? null : "Completar vehiculo"
        ));
        return bloque("VEHICULO", "Vehiculo", items);
    }

    private ItemCalculo itemDocumentacionVehiculo(PreparacionContext context) {
        boolean permiso = documentoAportado(context, Set.of(TipoDocumento.PERMISO_CIRCULACION));
        boolean ficha = documentoAportado(context, Set.of(TipoDocumento.FICHA_TECNICA));
        boolean informeDgt = documentoAportado(context, Set.of(TipoDocumento.INFORME_DGT));
        boolean suficiente = informeDgt || (permiso && ficha);
        return item(
                "documentacion_vehiculo",
                "Permiso/ficha o Informe DGT",
                suficiente ? EstadoItem.OK : EstadoItem.PENDIENTE,
                detalleDocumentacionVehiculo(permiso, ficha, informeDgt),
                suficiente ? null : "SUBIR_DOCUMENTO",
                suficiente ? null : "Subir vehiculo"
        );
    }

    private String detalleDocumentacionVehiculo(boolean permiso, boolean ficha, boolean informeDgt) {
        if (informeDgt) {
            return "Informe DGT aportado.";
        }
        if (permiso && ficha) {
            return "Permiso de circulacion y ficha tecnica aportados.";
        }
        if (permiso) {
            return "Permiso aportado; falta ficha tecnica o Informe DGT.";
        }
        if (ficha) {
            return "Ficha tecnica aportada; falta permiso de circulacion o Informe DGT.";
        }
        return "Falta permiso de circulacion y ficha tecnica, o Informe DGT.";
    }

    private BloqueCalculo bloqueOperacion(PreparacionContext context) {
        List<ItemCalculo> items = new ArrayList<>();
        TipoTramiteEnum tramite = tipoTramite(context.solicitud());
        items.add(item(
                "tipo_tramite",
                "Tipo de tramite",
                tramite != null ? EstadoItem.OK : EstadoItem.BLOQUEANTE,
                tramite != null ? tramite.name().replace('_', ' ') : "Falta tipo de tramite.",
                tramite != null ? null : "COMPLETAR_DATO",
                tramite != null ? null : "Completar tramite"
        ));

        boolean rolesMinimos = context.rolesEsperados().stream().allMatch(rol -> interesadosPorRol(context.interesados()).containsKey(rol));
        items.add(item(
                "roles_operacion",
                "Roles de la operacion",
                rolesMinimos ? EstadoItem.OK : EstadoItem.BLOQUEANTE,
                rolesMinimos ? "Los roles minimos estan informados." : "Faltan roles para preparar el traspaso.",
                rolesMinimos ? null : "COMPLETAR_INTERESADO",
                rolesMinimos ? null : "Completar roles"
        ));

        boolean contratoOFactura = context.documentos().stream().anyMatch(documento -> TIPOS_ROLES.contains(documento.getTipoDocumento()));
        items.add(item(
                "contrato_factura",
                "Contrato o factura",
                contratoOFactura ? EstadoItem.OK : EstadoItem.AVISO,
                contratoOFactura ? "Hay contrato/factura aportado." : "No hay contrato/factura aportado; se podra generar contrato si se completa la operacion.",
                contratoOFactura ? null : "GENERAR_DOCUMENTO",
                contratoOFactura ? null : "Preparar contrato"
        ));

        String precio = mejorPrecio(context);
        items.add(item(
                "precio",
                "Precio de venta",
                precio != null ? EstadoItem.OK : EstadoItem.PENDIENTE,
                precio != null ? precio : "Falta precio para generar contrato de compraventa.",
                precio != null ? null : "COMPLETAR_DATO",
                precio != null ? null : "Completar precio"
        ));

        DocumentoRolesLectura lecturaRoles = mejorLecturaRoles(context);
        items.add(item(
                "lectura_roles",
                "Lectura comprador/vendedor",
                lecturaRoles != null ? EstadoItem.OK : EstadoItem.AVISO,
                lecturaRoles != null ? "Hay una lectura de roles aplicable con confianza suficiente." : "No hay lectura aplicable de contrato/factura; se usaran los datos revisados de la solicitud.",
                lecturaRoles != null ? null : "REVISAR_IA",
                lecturaRoles != null ? null : "Revisar lectura"
        ));
        return bloque("OPERACION", "Operacion", items);
    }

    private List<SolicitudPreparacionDocumentoResponse> documentosGenerables(PreparacionContext context) {
        Map<RolInteresado, InteresadoSlot> porRol = interesadosPorRol(context.interesados());
        InteresadoSlot comprador = primerPorRol(porRol, RolInteresado.COMPRADOR, RolInteresado.TITULAR, RolInteresado.COMPRAVENTA);
        InteresadoSlot vendedor = primerPorRol(porRol, RolInteresado.VENDEDOR);
        InteresadoSlot mandante = comprador != null ? comprador : vendedor;
        String matricula = texto(context.solicitud().getMatricula());
        String bastidor = mejorBastidor(context);
        String marca = texto(context.solicitud().getVehiculoMarca());
        String modelo = texto(context.solicitud().getVehiculoModelo());
        String precio = mejorPrecio(context);

        return List.of(
                documento(context, "MANDATO", "Mandato", Set.of(TipoDocumento.MANDATO, TipoDocumento.MANDATO_REPRESENTACION), List.of(
                        requisito(mandante != null && datosPersonaSuficientes(mandante), "Mandante"),
                        requisito(mandante != null && direccionNoVacia(mandante), "Domicilio del mandante"),
                        requisito(mandante != null && texto(mandante.municipio()) != null, "Localidad del mandante"),
                        requisito(matricula != null, "Matricula")
                )),
                documento(context, "CAMBIO_TITULARIDAD", "Cambio de titularidad", Set.of(TipoDocumento.CAMBIO_TITULARIDAD), List.of(
                        requisito(comprador != null && datosPersonaSuficientes(comprador), "Comprador"),
                        requisito(vendedor != null && datosPersonaSuficientes(vendedor), "Vendedor"),
                        requisito(matricula != null, "Matricula"),
                        requisito(comprador != null && direccionNoVacia(comprador), "Domicilio del vehiculo")
                )),
                documento(context, "CONTRATO_COMPRAVENTA", "Contrato de compraventa", Set.of(TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.FACTURA), List.of(
                        requisito(comprador != null && datosPersonaSuficientes(comprador), "Comprador"),
                        requisito(vendedor != null && datosPersonaSuficientes(vendedor), "Vendedor"),
                        requisito(comprador != null && direccionNoVacia(comprador), "Domicilio del comprador"),
                        requisito(vendedor != null && direccionNoVacia(vendedor), "Domicilio del vendedor"),
                        requisito(matricula != null, "Matricula"),
                        requisito(marca != null, "Marca"),
                        requisito(modelo != null, "Modelo"),
                        requisito(bastidor != null, "Bastidor"),
                        requisito(precio != null, "Precio")
                ))
        );
    }

    private SolicitudPreparacionDocumentoResponse documento(
            PreparacionContext context,
            String codigo,
            String nombre,
            Set<TipoDocumento> tiposExistentes,
            List<RequisitoDocumento> requisitos
    ) {
        if (documentoAportado(context, tiposExistentes)) {
            return new SolicitudPreparacionDocumentoResponse(
                    codigo,
                    nombre,
                    "YA_APORTADO",
                    requisitos.size(),
                    requisitos.size(),
                    List.of()
            );
        }
        List<String> faltantes = requisitos.stream()
                .filter(Predicate.not(RequisitoDocumento::completo))
                .map(RequisitoDocumento::etiqueta)
                .toList();
        return new SolicitudPreparacionDocumentoResponse(
                codigo,
                nombre,
                faltantes.isEmpty() ? "LISTO" : "FALTAN_DATOS",
                requisitos.size() - faltantes.size(),
                requisitos.size(),
                faltantes
        );
    }

    private boolean documentoAportado(PreparacionContext context, Set<TipoDocumento> tipos) {
        return context.documentos().stream().anyMatch(documento -> tipos.contains(documento.getTipoDocumento()));
    }

    private RequisitoDocumento requisito(boolean completo, String etiqueta) {
        return new RequisitoDocumento(completo, etiqueta);
    }

    private BloqueCalculo bloque(String codigo, String titulo, List<ItemCalculo> items) {
        int completados = (int) items.stream().filter(item -> item.estado() == EstadoItem.OK).count();
        EstadoItem estado = items.stream()
                .map(ItemCalculo::estado)
                .max(Comparator.comparingInt(EstadoItem::severidad))
                .orElse(EstadoItem.OK);
        return new BloqueCalculo(
                new SolicitudPreparacionBloqueResponse(
                        codigo,
                        titulo,
                        estado.name(),
                        completados,
                        items.size(),
                        items.stream().map(ItemCalculo::response).toList()
                ),
                items
        );
    }

    private ItemCalculo item(String codigo, String etiqueta, EstadoItem estado, String detalle, String accionTipo, String accionLabel) {
        return new ItemCalculo(
                estado,
                new SolicitudPreparacionItemResponse(codigo, etiqueta, estado.name(), detalle, accionTipo, accionLabel)
        );
    }

    private String estadoGlobal(List<BloqueCalculo> bloques, List<SolicitudPreparacionDocumentoResponse> documentos) {
        boolean bloqueante = bloques.stream()
                .flatMap(bloque -> bloque.items().stream())
                .anyMatch(item -> item.estado() == EstadoItem.BLOQUEANTE);
        if (bloqueante) {
            return "BLOQUEADA";
        }
        boolean pendiente = bloques.stream()
                .flatMap(bloque -> bloque.items().stream())
                .anyMatch(item -> item.estado() != EstadoItem.OK)
                || documentos.stream().anyMatch(this::documentoPendiente);
        return pendiente ? "INCOMPLETA" : "LISTA";
    }

    private SolicitudPreparacionAccionResponse siguienteAccion(
            List<BloqueCalculo> bloques,
            List<SolicitudPreparacionDocumentoResponse> documentos
    ) {
        for (EstadoItem estado : List.of(EstadoItem.BLOQUEANTE, EstadoItem.PENDIENTE, EstadoItem.AVISO)) {
            for (BloqueCalculo bloque : bloques) {
                for (ItemCalculo item : bloque.items()) {
                    if (item.estado() == estado) {
                        SolicitudPreparacionItemResponse response = item.response();
                        return new SolicitudPreparacionAccionResponse(
                                response.accionTipo(),
                                response.etiqueta(),
                                response.detalle(),
                                bloque.response().codigo()
                        );
                    }
                }
            }
        }
        return documentos.stream()
                .filter(this::documentoPendiente)
                .findFirst()
                .map(documento -> new SolicitudPreparacionAccionResponse(
                        "COMPLETAR_PLANTILLA",
                        "Completar " + documento.nombre().toLowerCase(Locale.ROOT),
                        "Faltan: " + String.join(", ", documento.faltantes()),
                        "DOCUMENTOS"
                ))
                .orElseGet(() -> {
                    boolean todosAportados = !documentos.isEmpty()
                            && documentos.stream().allMatch(documento -> "YA_APORTADO".equals(documento.estado()));
                    return todosAportados
                            ? new SolicitudPreparacionAccionResponse(
                                    "REVISION_FINAL",
                                    "Documentacion base aportada",
                                    "Los documentos principales ya estan aportados; revisa los datos antes de convertir.",
                                    "DOCUMENTOS"
                            )
                            : new SolicitudPreparacionAccionResponse(
                                    "GENERAR_DOCUMENTOS",
                                    "Documentacion lista",
                                    "Ya se pueden generar los documentos disponibles.",
                                    "DOCUMENTOS"
                            );
                });
    }

    private boolean documentoPendiente(SolicitudPreparacionDocumentoResponse documento) {
        return !"LISTO".equals(documento.estado()) && !"YA_APORTADO".equals(documento.estado());
    }

    private List<InteresadoSlot> interesados(Solicitud solicitud) {
        List<InteresadoSlot> interesados = new ArrayList<>();
        agregarInteresado(interesados, 1, solicitud.getInteresado1Rol(), solicitud.getInteresado1Nombre(),
                solicitud.getInteresado1Dni(), solicitud.getInteresado1Telefono(), solicitud.getInteresado1Direccion(),
                solicitud.getInteresado1TipoVia(), solicitud.getInteresado1NombreVia(), solicitud.getInteresado1CodigoPostal(),
                solicitud.getInteresado1Municipio(), solicitud.getInteresado1Provincia());
        agregarInteresado(interesados, 2, solicitud.getInteresado2Rol(), solicitud.getInteresado2Nombre(),
                solicitud.getInteresado2Dni(), solicitud.getInteresado2Telefono(), solicitud.getInteresado2Direccion(),
                solicitud.getInteresado2TipoVia(), solicitud.getInteresado2NombreVia(), solicitud.getInteresado2CodigoPostal(),
                solicitud.getInteresado2Municipio(), solicitud.getInteresado2Provincia());
        agregarInteresado(interesados, 3, solicitud.getInteresado3Rol(), solicitud.getInteresado3Nombre(),
                solicitud.getInteresado3Dni(), solicitud.getInteresado3Telefono(), solicitud.getInteresado3Direccion(),
                solicitud.getInteresado3TipoVia(), solicitud.getInteresado3NombreVia(), solicitud.getInteresado3CodigoPostal(),
                solicitud.getInteresado3Municipio(), solicitud.getInteresado3Provincia());
        return interesados;
    }

    private void agregarInteresado(
            List<InteresadoSlot> interesados,
            int slot,
            RolInteresado rol,
            String nombre,
            String dni,
            String telefono,
            String direccion,
            String tipoVia,
            String nombreVia,
            String codigoPostal,
            String municipio,
            String provincia
    ) {
        if (rol == null && texto(nombre) == null && texto(dni) == null && texto(telefono) == null && texto(direccion) == null
                && texto(nombreVia) == null && texto(codigoPostal) == null && texto(municipio) == null && texto(provincia) == null) {
            return;
        }
        interesados.add(new InteresadoSlot(slot, rol, nombre, dni, telefono, direccion, tipoVia, nombreVia, codigoPostal, municipio, provincia));
    }

    private Map<RolInteresado, InteresadoSlot> interesadosPorRol(List<InteresadoSlot> interesados) {
        Map<RolInteresado, InteresadoSlot> porRol = new LinkedHashMap<>();
        for (InteresadoSlot interesado : interesados) {
            if (interesado.rol() != null) {
                porRol.putIfAbsent(interesado.rol(), interesado);
            }
        }
        return porRol;
    }

    private InteresadoSlot primerPorRol(Map<RolInteresado, InteresadoSlot> porRol, RolInteresado... roles) {
        for (RolInteresado rol : roles) {
            InteresadoSlot interesado = porRol.get(rol);
            if (interesado != null) {
                return interesado;
            }
        }
        return null;
    }

    private List<RolInteresado> rolesEsperados(Solicitud solicitud) {
        TipoTramiteEnum tramite = tipoTramite(solicitud);
        if (tramite == TipoTramiteEnum.BATECOM) {
            return List.of(RolInteresado.VENDEDOR, RolInteresado.COMPRAVENTA, RolInteresado.COMPRADOR);
        }
        if (tramite == TipoTramiteEnum.ALTA
                || tramite == TipoTramiteEnum.BAJA
                || tramite == TipoTramiteEnum.DUPLICADO
                || tramite == TipoTramiteEnum.MATRICULACION) {
            return List.of(RolInteresado.TITULAR);
        }
        return List.of(RolInteresado.VENDEDOR, RolInteresado.COMPRADOR);
    }

    private boolean documentoIdentidadAportado(PreparacionContext context, String identificador) {
        for (Documento documento : context.documentos()) {
            if (!TIPOS_IDENTIDAD.contains(documento.getTipoDocumento())) {
                continue;
            }
            DocumentoIdentidadLectura lectura = context.lecturasIdentidad().get(documento.getId());
            if (lectura == null) {
                continue;
            }
            if (lecturaIdentidadCoincide(lectura, identificador)) {
                return true;
            }
        }
        return false;
    }

    private boolean lecturaIdentidadCoincide(DocumentoIdentidadLectura lectura, String identificador) {
        if (confianza(lectura.getConfianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD
                && identificador.equals(normalizarIdentificador(lectura.getIdentificador()))) {
            return true;
        }
        return DocumentoIdentidadLecturaJson.extraer(lectura).stream()
                .filter(item -> confianza(item.confianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD)
                .anyMatch(item -> identificador.equals(normalizarIdentificador(item.identificador())));
    }

    private DocumentoRolesLectura mejorLecturaRoles(PreparacionContext context) {
        return context.lecturasRoles().values().stream()
                .filter(this::lecturaRolesUsable)
                .max(Comparator.comparingDouble(lectura -> confianza(lectura.getConfianzaGlobal())))
                .orElse(null);
    }

    private boolean lecturaRolesUsable(DocumentoRolesLectura lectura) {
        if (lectura == null || lectura.isRequiereRevision() || confianza(lectura.getConfianzaGlobal()) < CONFIANZA_MINIMA_ROLES) {
            return false;
        }
        String vendedor = normalizarIdentificador(lectura.getVendedorIdentificador());
        String comprador = normalizarIdentificador(lectura.getCompradorIdentificador());
        return vendedor != null
                && comprador != null
                && identificadorValido(vendedor)
                && identificadorValido(comprador)
                && !vendedor.equals(comprador)
                && texto(lectura.getVendedorNombre()) != null
                && texto(lectura.getCompradorNombre()) != null;
    }

    private String mejorBastidor(PreparacionContext context) {
        String bastidorSolicitud = normalizarIdentificador(context.solicitud().getVehiculoBastidor());
        if (bastidorSolicitud != null && bastidorSolicitud.length() >= 6) {
            return bastidorSolicitud;
        }
        return context.lecturasRoles().values().stream()
                .map(DocumentoRolesLectura::getBastidor)
                .map(this::normalizarIdentificador)
                .filter(value -> value != null && value.length() >= 6)
                .findFirst()
                .orElse(null);
    }

    private String mejorPrecio(PreparacionContext context) {
        return context.lecturasRoles().values().stream()
                .map(DocumentoRolesLectura::getValorDeclarado)
                .map(this::texto)
                .findFirst()
                .orElse(null);
    }

    private boolean datosPersonaSuficientes(InteresadoSlot interesado) {
        String identificador = normalizarIdentificador(interesado.dni());
        return texto(interesado.nombre()) != null && identificador != null && identificadorValido(identificador);
    }

    private boolean direccionNoVacia(InteresadoSlot interesado) {
        return texto(interesado.direccion()) != null
                || texto(interesado.nombreVia()) != null
                || texto(interesado.municipio()) != null
                || texto(interesado.codigoPostal()) != null;
    }

    private DireccionEstado direccionEstado(InteresadoSlot interesado) {
        if (!direccionNoVacia(interesado)) {
            return new DireccionEstado(EstadoItem.PENDIENTE, "Falta direccion del " + rolLabel(interesado.rol()) + ".");
        }
        String compuesta = direccionCompuesta(interesado);
        if (direccionSuficiente(interesado)) {
            return new DireccionEstado(EstadoItem.OK, compuesta);
        }
        return new DireccionEstado(EstadoItem.AVISO, "Direccion parcial: " + compuesta);
    }

    private boolean direccionSuficiente(InteresadoSlot interesado) {
        boolean viaEstructurada = texto(interesado.nombreVia()) != null;
        boolean localidad = texto(interesado.codigoPostal()) != null || texto(interesado.municipio()) != null || texto(interesado.provincia()) != null;
        if (viaEstructurada && localidad) {
            return true;
        }
        String direccion = normalizarTexto(interesado.direccion());
        if (direccion == null) {
            return false;
        }
        boolean pareceVia = direccion.matches(".*\\b(CALLE|CARRETERA|CTRA|AVENIDA|AVDA|PLAZA|PASEO|CAMINO|RAMBLA|TRAVESIA|URBANIZACION|URB)\\b.*")
                || direccion.contains("C/")
                || direccion.contains("C.");
        boolean tieneNumero = direccion.matches(".*\\d.*");
        boolean tieneLocalidad = direccion.contains(",") || direccion.matches(".*\\b\\d{5}\\b.*");
        return pareceVia && tieneNumero && tieneLocalidad;
    }

    private String direccionCompuesta(InteresadoSlot interesado) {
        List<String> partesVia = new ArrayList<>();
        if (texto(interesado.tipoVia()) != null) {
            partesVia.add(texto(interesado.tipoVia()));
        }
        if (texto(interesado.nombreVia()) != null) {
            partesVia.add(texto(interesado.nombreVia()));
        }
        String via = String.join(" ", partesVia);
        List<String> partes = new ArrayList<>();
        if (texto(via) != null) {
            partes.add(via);
        }
        if (texto(interesado.codigoPostal()) != null) {
            partes.add(texto(interesado.codigoPostal()));
        }
        if (texto(interesado.municipio()) != null) {
            partes.add(texto(interesado.municipio()));
        }
        if (texto(interesado.provincia()) != null) {
            partes.add(texto(interesado.provincia()));
        }
        String compuesta = String.join(", ", partes);
        if (texto(compuesta) != null) {
            return normalizarTexto(compuesta);
        }
        return normalizarTexto(interesado.direccion());
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

    private TipoTramiteEnum tipoTramite(Solicitud solicitud) {
        TipoTramite tipoTramite = solicitud.getTipoTramite();
        return tipoTramite != null ? tipoTramite.getNombre() : null;
    }

    private String normalizarIdentificador(String value) {
        String text = texto(value);
        return text != null ? text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "") : null;
    }

    private String normalizarTexto(String value) {
        String text = texto(value);
        return text != null ? text.toUpperCase(Locale.ROOT) : null;
    }

    private String texto(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return text.isBlank() ? null : text;
    }

    private double confianza(Double value) {
        return value != null ? value : 0.0;
    }

    private String nombreCorto(InteresadoSlot interesado) {
        String nombre = texto(interesado.nombre());
        String dni = normalizarIdentificador(interesado.dni());
        if (nombre != null && dni != null) {
            return nombre + " - " + dni;
        }
        return nombre != null ? nombre : dni != null ? dni : "Datos parciales";
    }

    private String rolLabel(RolInteresado rol) {
        if (rol == null) {
            return "interesado";
        }
        return switch (rol) {
            case COMPRADOR -> "comprador";
            case VENDEDOR -> "vendedor";
            case COMPRAVENTA -> "compraventa";
            case TITULAR -> "titular";
        };
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private record PreparacionContext(
            Solicitud solicitud,
            List<Documento> documentos,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidad,
            Map<Long, DocumentoRolesLectura> lecturasRoles,
            List<InteresadoSlot> interesados,
            List<RolInteresado> rolesEsperados
    ) {
    }

    private record InteresadoSlot(
            int slot,
            RolInteresado rol,
            String nombre,
            String dni,
            String telefono,
            String direccion,
            String tipoVia,
            String nombreVia,
            String codigoPostal,
            String municipio,
            String provincia
    ) {
    }

    private record ItemCalculo(EstadoItem estado, SolicitudPreparacionItemResponse response) {
    }

    private record BloqueCalculo(SolicitudPreparacionBloqueResponse response, List<ItemCalculo> items) {
        private int completados() {
            return response.completados();
        }

        private int total() {
            return response.total();
        }
    }

    private record RequisitoDocumento(boolean completo, String etiqueta) {
    }

    private record DireccionEstado(EstadoItem estado, String detalle) {
    }

    private enum EstadoItem {
        OK(0),
        AVISO(1),
        PENDIENTE(2),
        BLOQUEANTE(3);

        private final int severidad;

        EstadoItem(int severidad) {
            this.severidad = severidad;
        }

        private int severidad() {
            return severidad;
        }
    }
}
