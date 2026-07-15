package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.SolicitudPreparacionAccionResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionBloqueResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionDocumentoResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionItemResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionTraspasoResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.ClienteInteresado;
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
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.SolicitudDocumentacionBasicaService;
import com.example.gestor_documental.service.SolicitudPreparacionTraspasoService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.validation.DniNieValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
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

    private static final double CONFIANZA_MINIMA_ROLES = 0.90;
    private static final Set<TipoDocumento> TIPOS_ROLES = Set.of(TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.FACTURA);

    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final DocumentoRolesLecturaRepository rolesLecturaRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final SolicitudDocumentacionBasicaService solicitudDocumentacionBasicaService;
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
        Long clienteId = solicitud.getCliente() != null ? solicitud.getCliente().getId() : null;
        List<Documento> documentosCliente = clienteId == null
                ? List.of()
                : documentoRepository.findByClienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(
                        clienteId,
                        solicitudDocumentacionBasicaService.tiposIdentidad()
                );
        List<Long> documentosClienteIds = documentosCliente.stream()
                .map(Documento::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente = documentosClienteIds.isEmpty()
                ? Map.of()
                : identidadLecturaRepository.findByDocumentoIdIn(documentosClienteIds).stream()
                        .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                        .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (first, second) -> first));
        List<ClienteInteresado> relacionesCliente = clienteId == null
                ? List.of()
                : clienteInteresadoRepository.findByClienteIdAndHabitualTrueOrderByInteresadoNombreAsc(clienteId);

        PreparacionContext context = new PreparacionContext(
                solicitud,
                documentos,
                lecturasIdentidad,
                lecturasRoles,
                documentosCliente,
                lecturasIdentidadCliente,
                relacionesCliente,
                interesados(solicitud),
                solicitudDocumentacionBasicaService.rolesEsperados(solicitud)
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
        for (int index = 0; index < context.rolesEsperados().size(); index++) {
            RolInteresado rol = context.rolesEsperados().get(index);
            InteresadoSlot interesado = porRol.get(rol);
            String rolLabel = rolLabel(rol);
            if (interesado == null) {
                int slotSugerido = Math.min(index + 1, 3);
                items.add(item(
                        "rol_" + rol.name().toLowerCase(Locale.ROOT),
                        capitalize(rolLabel),
                        EstadoItem.BLOQUEANTE,
                        "Falta informar el " + rolLabel + ".",
                        "COMPLETAR_INTERESADO",
                        "Editar " + rolLabel,
                        "interesado" + slotSugerido + "Rol"
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
                    "Editar identificacion",
                    "interesado" + interesado.slot() + "Dni"
            );
        }
        if (!identificadorValido(identificador)) {
            return item(
                    "identificador_" + interesado.slot(),
                    "Identificacion " + rolLabel,
                    EstadoItem.BLOQUEANTE,
                    "El identificador " + identificador + " no pasa la validacion.",
                    "REVISAR_DATO",
                    "Revisar identificador",
                    "interesado" + interesado.slot() + "Dni"
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
                    "Corregir identificacion",
                    "interesado" + interesado.slot() + "Dni"
            );
        }
        if (documentoIdentidadAportado(context, identificador)) {
            return item(
                    "soporte_identidad_" + interesado.slot(),
                    "Documento identidad " + rolLabel,
                    EstadoItem.OK,
                    "DNI/NIE/CIF del " + rolLabel + " localizado.",
                    null,
                    null
            );
        }
        return item(
                "soporte_identidad_" + interesado.slot(),
                "Documento identidad " + rolLabel,
                EstadoItem.PENDIENTE,
                "Falta DNI/NIE/CIF del " + rolLabel + " separado o una lectura valida que coincida.",
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
                "Editar direccion " + rolLabel,
                direccionEstado.accionCampo()
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
                texto(context.solicitud().getMatricula()) != null ? null : "Editar matricula",
                texto(context.solicitud().getMatricula()) != null ? null : "matricula"
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
                bastidor != null ? null : "Editar bastidor",
                bastidor != null ? null : "vehiculoBastidor"
        ));
        items.add(item(
                "marca_modelo",
                "Marca y modelo",
                marca != null && modelo != null ? EstadoItem.OK : EstadoItem.PENDIENTE,
                marca != null && modelo != null
                        ? normalizarTexto(marca + " " + modelo)
                        : "Falta completar marca y modelo para proponer el contrato.",
                marca != null && modelo != null ? null : "COMPLETAR_DATO",
                marca != null && modelo != null ? null : (marca == null ? "Editar marca" : "Editar modelo"),
                marca != null && modelo != null ? null : (marca == null ? "vehiculoMarca" : "vehiculoModelo")
        ));
        return bloque("VEHICULO", "Vehiculo", items);
    }

    private ItemCalculo itemDocumentacionVehiculo(PreparacionContext context) {
        boolean permiso = documentoAportado(context, Set.of(TipoDocumento.PERMISO_CIRCULACION));
        boolean ficha = documentoAportado(context, Set.of(TipoDocumento.FICHA_TECNICA));
        boolean informeDgt = documentoAportado(context, Set.of(TipoDocumento.INFORME_DGT));
        boolean suficiente = permiso || ficha || informeDgt;
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
            return "Permiso de circulacion aportado.";
        }
        if (ficha) {
            return "Ficha tecnica aportada.";
        }
        return "Falta permiso de circulacion, ficha tecnica o Informe DGT.";
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
                tramite != null ? null : "Editar tramite",
                tramite != null ? null : "tipoTramiteId"
        ));

        Map<RolInteresado, InteresadoSlot> porRol = interesadosPorRol(context.interesados());
        boolean rolesMinimos = context.rolesEsperados().stream().allMatch(porRol::containsKey);
        boolean rolesConDatosRevisados = context.rolesEsperados().stream()
                .map(porRol::get)
                .allMatch(interesado -> interesado != null && datosPersonaSuficientes(interesado));
        items.add(item(
                "roles_operacion",
                "Roles de la operacion",
                rolesMinimos ? EstadoItem.OK : EstadoItem.BLOQUEANTE,
                rolesMinimos ? "Los roles minimos estan informados." : "Faltan roles para preparar el traspaso.",
                rolesMinimos ? null : "COMPLETAR_INTERESADO",
                rolesMinimos ? null : "Editar roles",
                rolesMinimos ? null : "interesado1Rol"
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
                precio != null ? null : "Editar precio",
                precio != null ? null : "operacionPrecioVenta"
        ));

        DocumentoRolesLectura lecturaRoles = mejorLecturaRoles(context);
        boolean lecturaRolesResuelta = lecturaRoles != null || rolesConDatosRevisados;
        items.add(item(
                "lectura_roles",
                "Lectura comprador/vendedor",
                lecturaRolesResuelta ? EstadoItem.OK : EstadoItem.AVISO,
                lecturaRoles != null
                        ? "Hay una lectura de roles aplicable con confianza suficiente."
                        : rolesConDatosRevisados
                                ? "Se usaran los datos revisados de la solicitud."
                                : "No hay lectura aplicable de contrato/factura; se usaran los datos revisados de la solicitud.",
                lecturaRolesResuelta ? null : "REVISAR_IA",
                lecturaRolesResuelta ? null : "Revisar lectura"
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
                        requisito(mandante != null && localidadNoVacia(mandante), "Localidad del mandante"),
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
        return item(codigo, etiqueta, estado, detalle, accionTipo, accionLabel, campoAccionPorCodigo(codigo));
    }

    private ItemCalculo item(
            String codigo,
            String etiqueta,
            EstadoItem estado,
            String detalle,
            String accionTipo,
            String accionLabel,
            String accionCampo
    ) {
        return new ItemCalculo(
                estado,
                new SolicitudPreparacionItemResponse(codigo, etiqueta, estado.name(), detalle, accionTipo, accionLabel, accionCampo)
        );
    }

    private String campoAccionPorCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        if ("matricula".equals(codigo)) {
            return "matricula";
        }
        if ("bastidor".equals(codigo)) {
            return "vehiculoBastidor";
        }
        if ("marca_modelo".equals(codigo)) {
            return "vehiculoMarca";
        }
        if ("tipo_tramite".equals(codigo)) {
            return "tipoTramiteId";
        }
        if ("precio".equals(codigo)) {
            return "operacionPrecioVenta";
        }
        if ("roles_operacion".equals(codigo)) {
            return "interesado1Rol";
        }
        Integer slot = slotDesdeCodigo(codigo);
        if (slot == null) {
            return null;
        }
        if (codigo.startsWith("identificador_") || codigo.startsWith("soporte_identidad_")) {
            return "interesado" + slot + "Dni";
        }
        if (codigo.startsWith("direccion_")) {
            return "interesado" + slot + "NombreVia";
        }
        return null;
    }

    private Integer slotDesdeCodigo(String codigo) {
        String[] partes = codigo.split("_");
        if (partes.length == 0) {
            return null;
        }
        try {
            int slot = Integer.parseInt(partes[partes.length - 1]);
            return slot >= 1 && slot <= 3 ? slot : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String campoDocumentoPendiente(SolicitudPreparacionDocumentoResponse documento) {
        return documento.faltantes().stream()
                .findFirst()
                .map(this::campoDocumentoFaltante)
                .orElse(null);
    }

    private String labelDocumentoPendiente(SolicitudPreparacionDocumentoResponse documento) {
        return documento.faltantes().stream()
                .findFirst()
                .map(faltante -> "Editar " + faltante.toLowerCase(Locale.ROOT))
                .orElse("Completar datos");
    }

    private String campoDocumentoFaltante(String faltante) {
        if (faltante == null) {
            return null;
        }
        return switch (faltante) {
            case "Precio" -> "operacionPrecioVenta";
            case "Bastidor" -> "vehiculoBastidor";
            case "Marca" -> "vehiculoMarca";
            case "Modelo" -> "vehiculoModelo";
            case "Matricula" -> "matricula";
            case "Domicilio del comprador", "Domicilio del vehiculo" -> "interesado1NombreVia";
            case "Domicilio del vendedor" -> "interesado2NombreVia";
            case "Comprador" -> "interesado1Nombre";
            case "Vendedor" -> "interesado2Nombre";
            case "Mandante", "Domicilio del mandante", "Localidad del mandante" -> "interesado1NombreVia";
            default -> null;
        };
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
                                bloque.response().codigo(),
                                response.accionCampo(),
                                response.accionLabel()
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
                        "DOCUMENTOS",
                        campoDocumentoPendiente(documento),
                        labelDocumentoPendiente(documento)
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
                solicitud.getInteresado1NumeroVia(), solicitud.getInteresado1Bloque(), solicitud.getInteresado1Portal(),
                solicitud.getInteresado1Escalera(), solicitud.getInteresado1Piso(), solicitud.getInteresado1Puerta(),
                solicitud.getInteresado1Municipio(), solicitud.getInteresado1Provincia());
        agregarInteresado(interesados, 2, solicitud.getInteresado2Rol(), solicitud.getInteresado2Nombre(),
                solicitud.getInteresado2Dni(), solicitud.getInteresado2Telefono(), solicitud.getInteresado2Direccion(),
                solicitud.getInteresado2TipoVia(), solicitud.getInteresado2NombreVia(), solicitud.getInteresado2CodigoPostal(),
                solicitud.getInteresado2NumeroVia(), solicitud.getInteresado2Bloque(), solicitud.getInteresado2Portal(),
                solicitud.getInteresado2Escalera(), solicitud.getInteresado2Piso(), solicitud.getInteresado2Puerta(),
                solicitud.getInteresado2Municipio(), solicitud.getInteresado2Provincia());
        agregarInteresado(interesados, 3, solicitud.getInteresado3Rol(), solicitud.getInteresado3Nombre(),
                solicitud.getInteresado3Dni(), solicitud.getInteresado3Telefono(), solicitud.getInteresado3Direccion(),
                solicitud.getInteresado3TipoVia(), solicitud.getInteresado3NombreVia(), solicitud.getInteresado3CodigoPostal(),
                solicitud.getInteresado3NumeroVia(), solicitud.getInteresado3Bloque(), solicitud.getInteresado3Portal(),
                solicitud.getInteresado3Escalera(), solicitud.getInteresado3Piso(), solicitud.getInteresado3Puerta(),
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
            String numeroVia,
            String bloque,
            String portal,
            String escalera,
            String piso,
            String puerta,
            String municipio,
            String provincia
    ) {
        if (rol == null && texto(nombre) == null && texto(dni) == null && texto(telefono) == null && texto(direccion) == null
                && texto(nombreVia) == null && texto(numeroVia) == null && texto(bloque) == null && texto(portal) == null
                && texto(escalera) == null && texto(piso) == null && texto(puerta) == null
                && texto(codigoPostal) == null && texto(municipio) == null && texto(provincia) == null) {
            return;
        }
        interesados.add(new InteresadoSlot(slot, rol, nombre, dni, telefono, direccion, tipoVia, nombreVia,
                numeroVia, bloque, portal, escalera, piso, puerta, codigoPostal, municipio, provincia));
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

    private boolean documentoIdentidadAportado(PreparacionContext context, String identificador) {
        return solicitudDocumentacionBasicaService.documentoIdentidadAportado(
                context.solicitud(),
                identificador,
                context.documentos(),
                context.lecturasIdentidad(),
                context.documentosCliente(),
                context.lecturasIdentidadCliente(),
                context.relacionesCliente()
        );
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
        String precioSolicitud = texto(context.solicitud().getOperacionPrecioVenta());
        if (precioSolicitud != null) {
            return precioSolicitud;
        }
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
                || texto(interesado.numeroVia()) != null
                || texto(interesado.bloque()) != null
                || texto(interesado.portal()) != null
                || texto(interesado.escalera()) != null
                || texto(interesado.piso()) != null
                || texto(interesado.puerta()) != null
                || texto(interesado.municipio()) != null
                || texto(interesado.codigoPostal()) != null;
    }

    private DireccionEstado direccionEstado(InteresadoSlot interesado) {
        if (!direccionNoVacia(interesado)) {
            return new DireccionEstado(
                    EstadoItem.PENDIENTE,
                    "Falta direccion del " + rolLabel(interesado.rol()) + ".",
                    "interesado" + interesado.slot() + "NombreVia"
            );
        }
        String compuesta = direccionCompuesta(interesado);
        if (direccionSuficiente(interesado)) {
            return new DireccionEstado(EstadoItem.OK, compuesta, null);
        }
        String campo = primerCampoDireccionPendiente(interesado);
        return new DireccionEstado(
                EstadoItem.AVISO,
                "Direccion parcial: falta " + etiquetaCampoDireccion(campo) + ". Actual: " + compuesta,
                campo
        );
    }

    private String primerCampoDireccionPendiente(InteresadoSlot interesado) {
        String prefix = "interesado" + interesado.slot();
        if (texto(interesado.nombreVia()) == null) {
            return prefix + "NombreVia";
        }
        boolean detalleVia = texto(interesado.numeroVia()) != null
                || texto(interesado.bloque()) != null
                || texto(interesado.portal()) != null
                || texto(interesado.escalera()) != null
                || texto(interesado.piso()) != null
                || texto(interesado.puerta()) != null;
        if (!detalleVia && texto(interesado.codigoPostal()) == null) {
            return prefix + "NumeroVia";
        }
        if (texto(interesado.codigoPostal()) == null && !tieneCodigoPostal(interesado)) {
            return prefix + "CodigoPostal";
        }
        if (texto(interesado.provincia()) == null) {
            return prefix + "Provincia";
        }
        if (texto(interesado.municipio()) == null) {
            return prefix + "Municipio";
        }
        return prefix + "NombreVia";
    }

    private String etiquetaCampoDireccion(String campo) {
        if (campo == null) {
            return "revisar la direccion";
        }
        if (campo.endsWith("NombreVia")) {
            return "separar la via";
        }
        if (campo.endsWith("NumeroVia")) {
            return "numero o detalle de via";
        }
        if (campo.endsWith("CodigoPostal")) {
            return "codigo postal";
        }
        if (campo.endsWith("Provincia")) {
            return "provincia";
        }
        if (campo.endsWith("Municipio")) {
            return "municipio";
        }
        return "revisar la direccion";
    }

    private boolean direccionSuficiente(InteresadoSlot interesado) {
        boolean viaEstructurada = texto(interesado.nombreVia()) != null;
        boolean detalleVia = texto(interesado.numeroVia()) != null
                || texto(interesado.bloque()) != null
                || texto(interesado.portal()) != null
                || texto(interesado.escalera()) != null
                || texto(interesado.piso()) != null
                || texto(interesado.puerta()) != null;
        boolean localidad = texto(interesado.codigoPostal()) != null || texto(interesado.municipio()) != null || texto(interesado.provincia()) != null;
        if (viaEstructurada && localidad && (detalleVia || texto(interesado.codigoPostal()) != null)) {
            return true;
        }
        String direccion = normalizarTexto(interesado.direccion());
        if (direccion == null) {
            return false;
        }
        boolean pareceVia = direccion.matches(".*\\b(CALLE|CARRETERA|CTRA|AVENIDA|AVDA|PLAZA|PASEO|CAMINO|RAMBLA|TRAVESIA|URBANIZACION|URB)\\b.*")
                || direccion.matches(".*\\b(CRUCE|AUTOPISTA|POLIGONO|POL)\\b.*")
                || direccion.contains("C/")
                || direccion.contains("C.");
        boolean tieneNumero = direccion.matches(".*\\d.*");
        boolean tieneCodigoPostal = tieneCodigoPostal(interesado);
        boolean tieneLocalidad = direccion.contains(",") || tieneCodigoPostal;
        return pareceVia && tieneLocalidad && (tieneNumero || tieneCodigoPostal);
    }

    private boolean localidadNoVacia(InteresadoSlot interesado) {
        return texto(interesado.codigoPostal()) != null
                || texto(interesado.municipio()) != null
                || texto(interesado.provincia()) != null
                || tieneCodigoPostal(interesado);
    }

    private boolean tieneCodigoPostal(InteresadoSlot interesado) {
        return texto(interesado.codigoPostal()) != null
                || (texto(interesado.direccion()) != null
                && normalizarTexto(interesado.direccion()).matches(".*\\b\\d{5}\\b.*"));
    }

    private String direccionCompuesta(InteresadoSlot interesado) {
        List<String> partesVia = new ArrayList<>();
        if (texto(interesado.tipoVia()) != null) {
            partesVia.add(texto(interesado.tipoVia()));
        }
        if (texto(interesado.nombreVia()) != null) {
            partesVia.add(texto(interesado.nombreVia()));
        }
        if (texto(interesado.numeroVia()) != null) {
            partesVia.add(texto(interesado.numeroVia()));
        }
        if (texto(interesado.bloque()) != null) {
            partesVia.add("BLOQ " + texto(interesado.bloque()));
        }
        if (texto(interesado.portal()) != null) {
            partesVia.add("PORTAL " + texto(interesado.portal()));
        }
        if (texto(interesado.escalera()) != null) {
            partesVia.add("ESC " + texto(interesado.escalera()));
        }
        if (texto(interesado.piso()) != null) {
            partesVia.add("PISO " + texto(interesado.piso()));
        }
        if (texto(interesado.puerta()) != null) {
            partesVia.add("PTA " + texto(interesado.puerta()));
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
            List<Documento> documentosCliente,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente,
            List<ClienteInteresado> relacionesCliente,
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
            String numeroVia,
            String bloque,
            String portal,
            String escalera,
            String piso,
            String puerta,
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

    private record DireccionEstado(EstadoItem estado, String detalle, String accionCampo) {
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
