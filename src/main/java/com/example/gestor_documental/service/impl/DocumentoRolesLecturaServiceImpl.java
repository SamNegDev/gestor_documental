package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.validation.DniNieValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentoRolesLecturaServiceImpl implements DocumentoRolesLecturaService {

    private static final double CONFIANZA_MINIMA_AUTOMATICA = 0.90;

    private final DocumentoService documentoService;
    private final DocumentoRepository documentoRepository;
    private final DocumentoRolesLecturaRepository lecturaRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final OpenAiProperties openAiProperties;
    private final DniNieValidator dniNieValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional(readOnly = true)
    public DocumentoRolesLecturaResponse obtenerLectura(Long documentoId, Usuario usuario) {
        documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        return lecturaRepository.findByDocumentoId(documentoId)
                .map(DocumentoRolesLecturaResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentoRolesLecturaResponse leerRoles(Long documentoId, boolean forzar, Usuario usuario) {
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        validarTipoDocumento(documento);
        if (!forzar) {
            DocumentoRolesLectura lecturaExistente = lecturaRepository.findByDocumentoId(documentoId).orElse(null);
            if (lecturaExistente != null) {
                return DocumentoRolesLecturaResponse.from(lecturaExistente);
            }
        }
        if (!openAiProperties.hasApiKey()) {
            throw new OperacionInvalidaException("OPENAI_API_KEY no esta configurada.");
        }

        Path ruta = resolverRutaDocumento(documento);
        String modeloUsado = modeloLectura();
        JsonNode resultado = llamarOpenAi(documento, ruta, modeloUsado);
        String modeloAvanzado = modeloAvanzado();
        if (debeReintentarRoles(resultado) && modeloDistinto(modeloUsado, modeloAvanzado)) {
            modeloUsado = modeloAvanzado;
            resultado = llamarOpenAi(documento, ruta, modeloUsado);
        }
        DocumentoRolesLectura lectura = lecturaRepository.findByDocumentoId(documentoId).orElseGet(DocumentoRolesLectura::new);
        lectura.setDocumento(documento);
        aplicarResultado(documento, lectura, resultado, modeloUsado);
        lectura = lecturaRepository.save(lectura);
        return DocumentoRolesLecturaResponse.from(lectura);
    }

    private void validarTipoDocumento(Documento documento) {
        TipoDocumento tipo = documento.getTipoDocumento();
        if (tipo != TipoDocumento.CONTRATO_COMPRAVENTA && tipo != TipoDocumento.FACTURA) {
            throw new OperacionInvalidaException("Solo se pueden leer roles en contrato de compraventa o factura.");
        }
    }

    private Path resolverRutaDocumento(Documento documento) {
        Path carpetaUploads = Path.of(uploadDir).toAbsolutePath().normalize();
        Path ruta = carpetaUploads.resolve(documento.getNombreArchivo()).normalize();
        if (!ruta.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida.");
        }
        if (!Files.exists(ruta)) {
            throw new RecursoNoEncontradoException("El archivo fisico del documento no existe.");
        }
        return ruta;
    }

    private JsonNode llamarOpenAi(Documento documento, Path ruta, String modelo) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", modelo);
            payload.set("input", construirInput(documento, ruta));
            payload.set("text", construirFormatoTexto("lectura_roles_documento", esquemaLecturaRoles()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiProperties.getResponsesUrl()))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OperacionInvalidaException("OpenAI devolvio HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return objectMapper.readTree(extraerTexto(root));
        } catch (IOException exception) {
            throw new RuntimeException("Error preparando la lectura de roles", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lectura de roles interrumpida", exception);
        }
    }

    private ArrayNode construirInput(Documento documento, Path ruta) throws IOException {
        ArrayNode input = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode texto = objectMapper.createObjectNode();
        texto.put("type", "input_text");
        texto.put("text", promptLecturaRoles(documento));
        content.add(texto);

        if (esPdf(documento, ruta)) {
            ObjectNode file = objectMapper.createObjectNode();
            file.put("type", "input_file");
            file.put("filename", documento.getNombreArchivoOriginal());
            file.put("file_data", "data:application/pdf;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(ruta)));
            content.add(file);
        } else {
            ObjectNode image = objectMapper.createObjectNode();
            image.put("type", "input_image");
            image.put("image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(leerImagenComoPng(ruta)));
            content.add(image);
        }

        user.set("content", content);
        input.add(user);
        return input;
    }

    private String promptLecturaRoles(Documento documento) {
        String contextoBatecom = esDocumentoSolicitudBatecom(documento)
                ? """
                Contexto BATECOM: el expediente puede tener dos operaciones documentales distintas.
                - Operacion BATE / ENTREGA A COMPRAVENTA: el titular inicial es VENDEDOR y la compraventa/profesional es COMPRADOR.
                - Operacion COM / VENTA FINAL: la compraventa/profesional es VENDEDOR y el cliente final es COMPRADOR.
                Extrae SOLO los roles visibles en ESTE documento y respeta los encabezados o secciones del contrato/factura.
                Si una empresa de compraventa aparece bajo "comprador", "adquirente" o despues del encabezado de comprador en BATE, debe ir en compradorIdentificador/compradorNombre, nunca como vendedor.
                Si esa misma compraventa aparece bajo "vendedor", "transmitente" o emisor en COM, debe ir en vendedorIdentificador/vendedorNombre.
                No cambies el rol por el tipo de entidad: una compraventa puede comprar en BATE y vender en COM.
                No intentes adivinar la otra operacion. La consolidacion posterior detectara la compraventa comun.
                %s
                """.formatted(contextoOperacionBatecom(documento))
                : "";
        return """
                Extrae roles de una operacion de transmision de vehiculo usando solo este contrato o factura.
                Tipo documental esperado: %s.
                %s
                En contrato: vendedor/transmitente/propietario es VENDEDOR; comprador/adquirente es COMPRADOR.
                Determina los roles por las clausulas operativas (quien transmite/vende y quien adquiere/compra), no por el orden de aparicion, la posicion de las firmas ni el orden de los anexos o documentos de identidad.
                Frases como "vende y transmite a", "entrega a" o "transfiere a" situan al sujeto antes del verbo como VENDEDOR y a la persona introducida por "a" como COMPRADOR. Frases como "compra/adquiere de" situan al sujeto como COMPRADOR y a la persona introducida por "de" como VENDEDOR.
                Si las etiquetas, las clausulas y las firmas se contradicen, prioriza la clausula que describe expresamente la transmision y marca requiereRevision true.
                En factura: emisor/proveedor/vendedor es VENDEDOR; cliente/receptor/comprador es COMPRADOR.
                Ignora gestoria, asesor, mandatario, tramitador, datos bancarios, pie legal y cualquier tercero que no sea parte de la compraventa.
                Si aparece una empresa vendedora, usa su CIF/razon social; no confundas su representante o administrador con el comprador.
                Si aparecen DNI/NIE/CIF junto a nombre de cliente o destinatario en factura, extraelos aunque el documento no diga literalmente comprador.
                Extrae tambien matricula, bastidor, fecha y valor si aparecen.
                Identificadores: DNI/NIE/CIF en mayusculas, sin espacios, guiones ni puntos.
                Nombres: respeta el orden visible. Si el documento usa "APELLIDOS, NOMBRE", conviertelo a "NOMBRE APELLIDOS".
                Fechas en formato dd/MM/yyyy. Valor como texto normalizado con coma decimal si aparece.
                Direcciones: una sola linea por persona si aparecen.
                No uses datos de DNI sueltos salvo que el contrato/factura los vincule claramente al rol.
                No inventes datos. Si un rol no aparece claro, devuelve null y requiereRevision true.
                Devuelve solo el JSON del esquema.
                """.formatted(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : "", contextoBatecom);
    }

    private boolean esDocumentoSolicitudBatecom(Documento documento) {
        return (documento.getSolicitud() != null
                && documento.getSolicitud().getTipoTramite() != null
                && documento.getSolicitud().getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM)
                || esExpedienteBatecom(documento);
    }

    private boolean esExpedienteBatecom(Documento documento) {
        return documento.getExpediente() != null
                && documento.getExpediente().getTipoTramite() != null
                && documento.getExpediente().getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM;
    }

    private String contextoOperacionBatecom(Documento documento) {
        if (documento.getOperacion() == null || documento.getOperacion().getTipo() == null) {
            return "Operacion concreta no indicada: usa exclusivamente los encabezados visibles del documento.";
        }
        if (documento.getOperacion().getTipo() == TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE) {
            return "Este documento esta asignado a BATE: la compraventa/profesional que recibe el vehiculo debe salir como COMPRADOR.";
        }
        if (documento.getOperacion().getTipo() == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM) {
            return "Este documento esta asignado a COM: la compraventa/profesional que transmite al cliente final debe salir como VENDEDOR.";
        }
        return "Operacion directa: aplica vendedor y comprador visibles sin reglas BATECOM.";
    }

    private void aplicarResultado(Documento documento, DocumentoRolesLectura lectura, JsonNode resultado, String modeloUsado) {
        resultado = corregirRolesBatecomPorOperacion(documento, resultado);
        String vendedorIdentificador = normalizarIdentificador(texto(resultado, "vendedorIdentificador"));
        String compradorIdentificador = normalizarIdentificador(texto(resultado, "compradorIdentificador"));
        Double confianza = numero(resultado, "confianzaGlobal");
        Interesado vendedorInteresado = resolverInteresadoVinculado(documento, vendedorIdentificador);
        Interesado compradorInteresado = resolverInteresadoVinculado(documento, compradorIdentificador);
        boolean revisionIa = booleano(resultado, "requiereRevision");
        boolean identificadoresValidos = identificadorValido(vendedorIdentificador) && identificadorValido(compradorIdentificador);
        boolean requiereRevision = revisionIa
                || confianza == null
                || confianza < CONFIANZA_MINIMA_AUTOMATICA
                || !identificadoresValidos
                || vendedorIdentificador == null
                || compradorIdentificador == null
                || texto(resultado, "vendedorNombre") == null
                || texto(resultado, "compradorNombre") == null
                || vendedorIdentificador.equals(compradorIdentificador);

        lectura.setTipoDocumentoDetectado(tipoDetectado(resultado, documento.getTipoDocumento()));
        lectura.setFechaDocumento(limitar(texto(resultado, "fechaDocumento"), 20));
        lectura.setMatricula(limitar(normalizarMatricula(texto(resultado, "matricula")), 20));
        lectura.setBastidor(limitar(normalizarIdentificador(texto(resultado, "bastidor")), 40));
        lectura.setValorDeclarado(limitar(texto(resultado, "valorDeclarado"), 30));
        lectura.setVendedorIdentificador(vendedorIdentificador);
        lectura.setVendedorNombre(limitar(texto(resultado, "vendedorNombre"), 220));
        lectura.setVendedorDireccion(limitar(texto(resultado, "vendedorDireccion"), 500));
        lectura.setVendedorInteresado(vendedorInteresado);
        lectura.setCompradorIdentificador(compradorIdentificador);
        lectura.setCompradorNombre(limitar(texto(resultado, "compradorNombre"), 220));
        lectura.setCompradorDireccion(limitar(texto(resultado, "compradorDireccion"), 500));
        lectura.setCompradorInteresado(compradorInteresado);
        lectura.setConfianzaGlobal(confianza);
        lectura.setRequiereRevision(requiereRevision);
        lectura.setMensaje(mensajeLectura(vendedorIdentificador, compradorIdentificador, vendedorInteresado, compradorInteresado, requiereRevision));
        lectura.setModelo(modeloUsado);
        lectura.setFechaLectura(LocalDateTime.now());
        lectura.setAplicadoExpediente(false);
        lectura.setFechaAplicacion(null);
        lectura.setResultadoJson(resultado.toString());
    }

    private JsonNode corregirRolesBatecomPorOperacion(Documento documento, JsonNode resultado) {
        resultado = corregirRolesConInteresadosConfirmados(documento, resultado);
        if (!(resultado instanceof ObjectNode objectNode)
                || documento.getOperacion() == null
                || documento.getOperacion().getTipo() == null
                || !esExpedienteBatecom(documento)) {
            return resultado;
        }
        TipoOperacionExpediente tipoOperacion = documento.getOperacion().getTipo();
        String vendedor = normalizarIdentificador(texto(resultado, "vendedorIdentificador"));
        String comprador = normalizarIdentificador(texto(resultado, "compradorIdentificador"));
        if (vendedor == null || comprador == null || vendedor.equals(comprador)) {
            return resultado;
        }

        if (tipoOperacion == TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE
                && lecturaBatecomInvertidaEnBate(documento, vendedor, comprador)) {
            intercambiarCompradorVendedor(objectNode);
            objectNode.put("observaciones", "Roles intercambiados automaticamente: en BATE la compraventa debe figurar como comprador.");
            return objectNode;
        }
        if (tipoOperacion == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM
                && lecturaBatecomInvertidaEnCom(documento, vendedor, comprador)) {
            intercambiarCompradorVendedor(objectNode);
            objectNode.put("observaciones", "Roles intercambiados automaticamente: en COM la compraventa debe figurar como vendedor.");
        }
        return objectNode;
    }

    private JsonNode corregirRolesConInteresadosConfirmados(Documento documento, JsonNode resultado) {
        if (!(resultado instanceof ObjectNode objectNode)) {
            return resultado;
        }
        String vendedor = normalizarIdentificador(texto(resultado, "vendedorIdentificador"));
        String comprador = normalizarIdentificador(texto(resultado, "compradorIdentificador"));
        if (vendedor == null || comprador == null || vendedor.equals(comprador)) {
            return resultado;
        }

        RolInteresado rolVendedorDetectado = rolConfirmado(documento, vendedor);
        RolInteresado rolCompradorDetectado = rolConfirmado(documento, comprador);
        boolean invertidos = rolVendedorDetectado == RolInteresado.COMPRADOR
                || rolCompradorDetectado == RolInteresado.VENDEDOR;
        boolean coherentes = rolVendedorDetectado == RolInteresado.VENDEDOR
                || rolCompradorDetectado == RolInteresado.COMPRADOR;
        if (invertidos && !coherentes) {
            intercambiarCompradorVendedor(objectNode);
            objectNode.put("requiereRevision", true);
            objectNode.put("observaciones", "Roles corregidos con los interesados confirmados; revisar la contradiccion antes de aplicar la lectura.");
        }
        return objectNode;
    }

    private RolInteresado rolConfirmado(Documento documento, String identificador) {
        if (documento.getSolicitud() != null) {
            Solicitud solicitud = documento.getSolicitud();
            RolInteresado rol = rolSolicitud(solicitud, identificador);
            if (rol != null) return rol;
        }
        if (documento.getExpediente() == null || documento.getExpediente().getId() == null) {
            return null;
        }
        return expedienteInteresadoRepository.findByExpedienteId(documento.getExpediente().getId()).stream()
                .filter(relacion -> relacion.getInteresado() != null)
                .filter(relacion -> identificador.equals(normalizarIdentificador(relacion.getInteresado().getDni())))
                .map(ExpedienteInteresado::getRol)
                .filter(rol -> rol == RolInteresado.VENDEDOR || rol == RolInteresado.COMPRADOR)
                .findFirst()
                .orElse(null);
    }

    private RolInteresado rolSolicitud(Solicitud solicitud, String identificador) {
        if (identificador.equals(normalizarIdentificador(solicitud.getInteresado1Dni()))) return solicitud.getInteresado1Rol();
        if (identificador.equals(normalizarIdentificador(solicitud.getInteresado2Dni()))) return solicitud.getInteresado2Rol();
        if (identificador.equals(normalizarIdentificador(solicitud.getInteresado3Dni()))) return solicitud.getInteresado3Rol();
        return null;
    }

    private boolean lecturaBatecomInvertidaEnBate(Documento documento, String vendedor, String comprador) {
        return vendedorEsCompraventaExistente(documento, vendedor)
                || (esCif(vendedor) && !esCif(comprador));
    }

    private boolean lecturaBatecomInvertidaEnCom(Documento documento, String vendedor, String comprador) {
        return vendedorEsCompraventaExistente(documento, comprador)
                || (!esCif(vendedor) && esCif(comprador));
    }

    private boolean vendedorEsCompraventaExistente(Documento documento, String identificador) {
        if (documento.getExpediente() == null || documento.getExpediente().getId() == null || identificador == null) {
            return false;
        }
        return expedienteInteresadoRepository.findByExpedienteId(documento.getExpediente().getId()).stream()
                .filter(relacion -> relacion.getRol() == RolInteresado.COMPRAVENTA)
                .map(ExpedienteInteresado::getInteresado)
                .anyMatch(interesado -> interesado != null && identificador.equals(normalizarIdentificador(interesado.getDni())));
    }

    private boolean esCif(String identificador) {
        String normalizado = normalizarIdentificador(identificador);
        return normalizado != null && normalizado.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private void intercambiarCompradorVendedor(ObjectNode node) {
        intercambiarCampos(node, "vendedorIdentificador", "compradorIdentificador");
        intercambiarCampos(node, "vendedorNombre", "compradorNombre");
        intercambiarCampos(node, "vendedorDireccion", "compradorDireccion");
    }

    private void intercambiarCampos(ObjectNode node, String first, String second) {
        JsonNode firstValue = node.get(first);
        JsonNode secondValue = node.get(second);
        node.set(first, secondValue == null ? objectMapper.nullNode() : secondValue);
        node.set(second, firstValue == null ? objectMapper.nullNode() : firstValue);
    }

    private Interesado resolverInteresadoVinculado(Documento documento, String identificador) {
        if (identificador == null || documento.getExpediente() == null || documento.getExpediente().getId() == null) {
            return null;
        }
        List<Interesado> coincidencias = expedienteInteresadoRepository.findByExpedienteId(documento.getExpediente().getId())
                .stream()
                .map(ExpedienteInteresado::getInteresado)
                .filter(interesado -> interesado != null && identificador.equals(normalizarIdentificador(interesado.getDni())))
                .distinct()
                .toList();
        return coincidencias.size() == 1 ? coincidencias.get(0) : null;
    }

    private String mensajeLectura(
            String vendedorIdentificador,
            String compradorIdentificador,
            Interesado vendedorInteresado,
            Interesado compradorInteresado,
            boolean requiereRevision
    ) {
        if (vendedorIdentificador == null && compradorIdentificador == null) {
            return "No se pudieron leer comprador ni vendedor con seguridad.";
        }
        if (vendedorIdentificador == null || compradorIdentificador == null) {
            return "Falta un rol principal; revisar contrato o factura.";
        }
        if (vendedorInteresado != null && compradorInteresado != null && !requiereRevision) {
            return "Roles leidos y cruzados con interesados existentes.";
        }
        return requiereRevision
                ? "Roles leidos con dudas; revisar antes de consolidar."
                : "Roles leidos con datos suficientes para aplicar.";
    }

    private TipoDocumento tipoDetectado(JsonNode resultado, TipoDocumento fallback) {
        String valor = texto(resultado, "tipoDocumento");
        if (valor == null) {
            return fallback;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.contains("FACTURA")) {
            return TipoDocumento.FACTURA;
        }
        if (normalizado.contains("CONTRATO")) {
            return TipoDocumento.CONTRATO_COMPRAVENTA;
        }
        return fallback;
    }

    private ObjectNode construirFormatoTexto(String schemaName, ObjectNode schema) {
        ObjectNode text = objectMapper.createObjectNode();
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);
        text.set("format", format);
        return text;
    }

    private ObjectNode esquemaLecturaRoles() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        props.set("tipoDocumento", nullableString("CONTRATO_COMPRAVENTA, FACTURA o null."));
        props.set("fechaDocumento", nullableString("Fecha del contrato/factura en dd/MM/yyyy."));
        props.set("matricula", nullableString("Matricula del vehiculo sin espacios."));
        props.set("bastidor", nullableString("Numero de bastidor si aparece."));
        props.set("valorDeclarado", nullableString("Precio, valor o base si aparece."));
        props.set("vendedorIdentificador", nullableString("DNI/NIE/CIF del vendedor/transmitente/emisor."));
        props.set("vendedorNombre", nullableString("Nombre o razon social del vendedor/transmitente/emisor."));
        props.set("vendedorDireccion", nullableString("Direccion del vendedor/transmitente/emisor."));
        props.set("compradorIdentificador", nullableString("DNI/NIE/CIF del comprador/adquirente/receptor."));
        props.set("compradorNombre", nullableString("Nombre o razon social del comprador/adquirente/receptor."));
        props.set("compradorDireccion", nullableString("Direccion del comprador/adquirente/receptor."));
        props.set("confianzaGlobal", nullableNumber("Confianza entre 0 y 1."));
        props.set("requiereRevision", nullableBoolean("true si comprador/vendedor no estan claros."));
        props.set("observaciones", nullableString("Motivo breve si requiere revision."));
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        props.fieldNames().forEachRemaining(required::add);
        schema.set("required", required);
        return schema;
    }

    private ObjectNode nullableString(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("string");
        types.add("null");
        node.set("type", types);
        node.put("description", description);
        return node;
    }

    private ObjectNode nullableNumber(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("number");
        types.add("null");
        node.set("type", types);
        node.put("description", description);
        return node;
    }

    private ObjectNode nullableBoolean(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("boolean");
        types.add("null");
        node.set("type", types);
        node.put("description", description);
        return node;
    }

    private String extraerTexto(JsonNode root) throws IOException {
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        JsonNode text = part.get("text");
                        if (text != null && text.isTextual()) {
                            return text.asText();
                        }
                    }
                }
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    private String texto(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isBlank() ? null : text;
    }

    private Double numero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().trim().replace(",", "."));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean booleano(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText());
        }
        return true;
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

    private String limitar(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private boolean enBlanco(String value) {
        return value == null || value.isBlank();
    }

    private boolean esPdf(Documento documento, Path ruta) {
        String nombre = (documento.getNombreArchivoOriginal() != null ? documento.getNombreArchivoOriginal() : ruta.getFileName().toString())
                .toLowerCase(Locale.ROOT);
        return nombre.endsWith(".pdf") || ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private byte[] leerImagenComoPng(Path ruta) throws IOException {
        BufferedImage imagen = ImageIO.read(ruta.toFile());
        if (imagen == null) {
            throw new OperacionInvalidaException("El documento no se pudo procesar como imagen o PDF.");
        }
        BufferedImage converted = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(imagen, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(converted, "png", output);
        return output.toByteArray();
    }

    private String modeloLectura() {
        if (openAiProperties.getIdentityModel() != null && !openAiProperties.getIdentityModel().isBlank()) {
            return openAiProperties.getIdentityModel();
        }
        return openAiProperties.getModel();
    }

    private String modeloAvanzado() {
        return openAiProperties.getModel() != null && !openAiProperties.getModel().isBlank()
                ? openAiProperties.getModel()
                : modeloLectura();
    }

    private boolean modeloDistinto(String actual, String candidato) {
        return actual != null && candidato != null && !actual.equalsIgnoreCase(candidato);
    }

    private boolean debeReintentarRoles(JsonNode resultado) {
        String vendedor = normalizarIdentificador(texto(resultado, "vendedorIdentificador"));
        String comprador = normalizarIdentificador(texto(resultado, "compradorIdentificador"));
        Double confianza = numero(resultado, "confianzaGlobal");
        return booleano(resultado, "requiereRevision")
                || confianza == null
                || confianza < CONFIANZA_MINIMA_AUTOMATICA
                || vendedor == null
                || comprador == null
                || !identificadorValido(vendedor)
                || !identificadorValido(comprador)
                || vendedor.equals(comprador)
                || texto(resultado, "vendedorNombre") == null
                || texto(resultado, "compradorNombre") == null;
    }
}
