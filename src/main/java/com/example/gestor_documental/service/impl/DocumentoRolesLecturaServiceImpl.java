package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.util.TextNormalizer;
import com.example.gestor_documental.validation.DniNieValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentoRolesLecturaServiceImpl implements DocumentoRolesLecturaService {

    private static final double CONFIANZA_MINIMA_AUTOMATICA = 0.90;
    private static final double CONFIANZA_MINIMA_IDENTIDAD_CORROBORACION = 0.80;

    private final DocumentoService documentoService;
    private final DocumentoRepository documentoRepository;
    private final DocumentoRolesLecturaRepository lecturaRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final InteresadoRepository interesadoRepository;
    private final HistorialCambioService historialCambioService;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalExpedienteService;
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
    @Transactional
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

    @Override
    @Transactional
    public DocumentoRolesLecturaResponse aplicarDatos(Long documentoId, Usuario admin) {
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, admin);
        DocumentoRolesLectura lectura = lecturaRepository.findByDocumentoId(documentoId)
                .orElseThrow(() -> new OperacionInvalidaException("Primero debes leer roles del contrato o factura."));
        Expediente expediente = documento.getExpediente();
        if (expediente == null) {
            throw new OperacionInvalidaException("El documento no pertenece a un expediente.");
        }
        validarLecturaAplicable(lectura);
        validarIdentidadesCorroboradas(documento, lectura);

        Interesado vendedor = obtenerOCrearInteresado(
                lectura.getVendedorIdentificador(),
                lectura.getVendedorNombre(),
                lectura.getVendedorDireccion());
        Interesado comprador = obtenerOCrearInteresado(
                lectura.getCompradorIdentificador(),
                lectura.getCompradorNombre(),
                lectura.getCompradorDireccion());
        if (vendedor.getId().equals(comprador.getId())) {
            throw new OperacionInvalidaException("Comprador y vendedor apuntan al mismo interesado.");
        }

        aplicarRelacion(expediente, vendedor, RolInteresado.VENDEDOR);
        aplicarRelacion(expediente, comprador, RolInteresado.COMPRADOR);
        vincularIdentidadesLeidas(expediente);
        sincronizarRequisitosExpediente(expediente, admin);
        lectura.setVendedorInteresado(vendedor);
        lectura.setCompradorInteresado(comprador);
        lectura.setAplicadoExpediente(true);
        lectura.setFechaAplicacion(LocalDateTime.now());
        lectura.setMensaje("Datos aplicados al expediente.");
        lectura = lecturaRepository.save(lectura);

        historialCambioService.registrarCambioExpediente(
                expediente,
                admin,
                "IA APLICAR DATOS",
                "Se aplicaron comprador y vendedor desde " + nombreDocumento(documento) + ".");
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
                Contexto BATECOM: el expediente puede tener dos operaciones. En una, el titular inicial vende/entrega a la compraventa; en otra, la compraventa vende al comprador final.
                Extrae SOLO los roles visibles en ESTE documento: si la compraventa compra, ponla como COMPRADOR; si vende, ponla como VENDEDOR.
                No intentes adivinar la otra operacion ni cambies el rol porque sea una compraventa. La consolidacion posterior detectara la compraventa comun.
                """
                : "";
        return """
                Extrae roles de una operacion de transmision de vehiculo usando solo este contrato o factura.
                Tipo documental esperado: %s.
                %s
                En contrato: vendedor/transmitente/propietario es VENDEDOR; comprador/adquirente es COMPRADOR.
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
        return documento.getSolicitud() != null
                && documento.getSolicitud().getTipoTramite() != null
                && documento.getSolicitud().getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM;
    }

    private void aplicarResultado(Documento documento, DocumentoRolesLectura lectura, JsonNode resultado, String modeloUsado) {
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

    private void validarLecturaAplicable(DocumentoRolesLectura lectura) {
        if (lectura.isAplicadoExpediente()) {
            throw new OperacionInvalidaException("Estos datos ya se aplicaron al expediente.");
        }
        if (lectura.isRequiereRevision()) {
            throw new OperacionInvalidaException("La lectura requiere revision manual antes de aplicar.");
        }
        if (lectura.getConfianzaGlobal() == null || lectura.getConfianzaGlobal() < CONFIANZA_MINIMA_AUTOMATICA) {
            throw new OperacionInvalidaException("La confianza de la lectura no es suficiente para aplicar datos.");
        }
        if (!identificadorValido(lectura.getVendedorIdentificador()) || !identificadorValido(lectura.getCompradorIdentificador())) {
            throw new OperacionInvalidaException("El DNI/NIE/CIF leido no supera las validaciones basicas.");
        }
        if (enBlanco(lectura.getVendedorIdentificador()) || enBlanco(lectura.getVendedorNombre())) {
            throw new OperacionInvalidaException("Faltan datos suficientes del vendedor.");
        }
        if (enBlanco(lectura.getCompradorIdentificador()) || enBlanco(lectura.getCompradorNombre())) {
            throw new OperacionInvalidaException("Faltan datos suficientes del comprador.");
        }
        if (lectura.getVendedorIdentificador().equalsIgnoreCase(lectura.getCompradorIdentificador())) {
            throw new OperacionInvalidaException("Comprador y vendedor tienen el mismo DNI/CIF.");
        }
    }

    private void validarIdentidadesCorroboradas(Documento documento, DocumentoRolesLectura lectura) {
        Expediente expediente = documento.getExpediente();
        if (expediente == null || expediente.getId() == null) {
            return;
        }
        Set<String> identidades = identidadesConfirmadasExpediente(expediente);
        validarRolCorroborado("vendedor", lectura.getVendedorIdentificador(), lectura.getVendedorNombre(), identidades, expediente);
        validarRolCorroborado("comprador", lectura.getCompradorIdentificador(), lectura.getCompradorNombre(), identidades, expediente);
    }

    private Set<String> identidadesConfirmadasExpediente(Expediente expediente) {
        Set<String> identidades = new HashSet<>();
        if (expediente.getCliente() != null) {
            String nifCliente = normalizarIdentificador(expediente.getCliente().getNif());
            if (nifCliente != null) {
                identidades.add(nifCliente);
            }
        }
        List<Long> documentoIds = documentoRepository.findByExpedienteId(expediente.getId()).stream()
                .filter(documento -> esDocumentoIdentidad(documento.getTipoDocumento()))
                .map(Documento::getId)
                .filter(id -> id != null)
                .toList();
        if (documentoIds.isEmpty()) {
            return identidades;
        }
        identidadLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .filter(this::identidadConfirmada)
                .map(DocumentoIdentidadLectura::getIdentificador)
                .map(this::normalizarIdentificador)
                .filter(identificador -> identificador != null)
                .forEach(identidades::add);
        return identidades;
    }

    private boolean identidadConfirmada(DocumentoIdentidadLectura lectura) {
        String identificador = lectura != null ? normalizarIdentificador(lectura.getIdentificador()) : null;
        return identificador != null
                && identificadorValido(identificador)
                && lectura.getConfianzaGlobal() != null
                && lectura.getConfianzaGlobal() >= CONFIANZA_MINIMA_IDENTIDAD_CORROBORACION;
    }

    private void validarRolCorroborado(
            String etiqueta,
            String identificador,
            String nombre,
            Set<String> identidades,
            Expediente expediente
    ) {
        String normalizado = normalizarIdentificador(identificador);
        if (normalizado == null || !identidades.contains(normalizado)) {
            throw new OperacionInvalidaException("No se aplica el " + etiqueta + ": falta DNI/CIF leido que corrobore el rol.");
        }
    }

    private Interesado obtenerOCrearInteresado(String identificador, String nombre, String direccion) {
        Interesado interesado = interesadoRepository.findByDni(identificador).orElse(null);
        String direccionNormalizada = normalizarDireccionCompleta(direccion);
        boolean creado = false;
        boolean actualizado = false;
        if (interesado == null) {
            interesado = new Interesado();
            interesado.setDni(identificador);
            interesado.setNombre(TextNormalizer.upperOrNull(nombre));
            interesado.setDireccion(direccionNormalizada);
            interesado.setTipoPersona(inferirTipoPersona(identificador));
            creado = true;
        } else {
            actualizado |= setIfBlank(interesado.getNombre(), TextNormalizer.upperOrNull(nombre), interesado::setNombre);
            if (direccionNormalizada != null && !direccionNormalizada.equals(interesado.getDireccion())) {
                interesado.setDireccion(direccionNormalizada);
                actualizado = true;
            }
            if (interesado.getTipoPersona() == null) {
                interesado.setTipoPersona(inferirTipoPersona(identificador));
                actualizado = true;
            }
        }
        return creado || actualizado ? interesadoRepository.save(interesado) : interesado;
    }

    private void aplicarRelacion(Expediente expediente, Interesado interesado, RolInteresado rol) {
        List<ExpedienteInteresado> relaciones = expedienteInteresadoRepository.findByExpedienteId(expediente.getId());
        relaciones.stream()
                .filter(relacion -> relacion.getRol() == rol)
                .filter(relacion -> relacion.getInteresado() != null)
                .filter(relacion -> !relacion.getInteresado().getId().equals(interesado.getId()))
                .findFirst()
                .ifPresent(relacion -> {
                    throw new OperacionInvalidaException("Ya existe otro interesado como " + rol.name() + ". Revisa manualmente.");
                });

        ExpedienteInteresado relacionExistente = relaciones.stream()
                .filter(relacion -> relacion.getInteresado() != null && relacion.getInteresado().getId().equals(interesado.getId()))
                .findFirst()
                .orElse(null);
        if (relacionExistente != null) {
            if (relacionExistente.getRol() != null && relacionExistente.getRol() != rol) {
                throw new OperacionInvalidaException("El interesado " + interesado.getNombre() + " ya figura como "
                        + relacionExistente.getRol().name() + ".");
            }
            if (relacionExistente.getRol() == null) {
                relacionExistente.setRol(rol);
                expedienteInteresadoRepository.save(relacionExistente);
            }
            return;
        }

        ExpedienteInteresado nuevaRelacion = new ExpedienteInteresado();
        nuevaRelacion.setExpediente(expediente);
        nuevaRelacion.setInteresado(interesado);
        nuevaRelacion.setRol(rol);
        expedienteInteresadoRepository.save(nuevaRelacion);
    }

    private void vincularIdentidadesLeidas(Expediente expediente) {
        if (expediente == null || expediente.getId() == null) {
            return;
        }
        List<ExpedienteInteresado> relaciones = expedienteInteresadoRepository.findByExpedienteId(expediente.getId());
        List<Documento> documentos = documentoRepository.findByExpedienteId(expediente.getId());
        List<Long> documentoIds = documentos.stream()
                .filter(documento -> esDocumentoIdentidad(documento.getTipoDocumento()))
                .map(Documento::getId)
                .filter(id -> id != null)
                .toList();
        if (documentoIds.isEmpty()) {
            return;
        }
        identidadLecturaRepository.findByDocumentoIdIn(documentoIds).forEach(lectura -> {
            String identificador = normalizarIdentificador(lectura.getIdentificador());
            Interesado interesado = interesadoPorIdentificador(relaciones, identificador);
            if (interesado == null || lectura.getConfianzaGlobal() == null
                    || lectura.getConfianzaGlobal() < CONFIANZA_MINIMA_AUTOMATICA) {
                return;
            }
            Documento documento = lectura.getDocumento();
            if (documento != null) {
                if (documento.getInteresado() == null) {
                    documento.setInteresado(interesado);
                }
                if (lectura.getTipoDocumentoDetectado() != null && lectura.getTipoDocumentoDetectado() != documento.getTipoDocumento()) {
                    documento.setTipoDocumento(lectura.getTipoDocumentoDetectado());
                }
                documentoRepository.save(documento);
            }
            actualizarDireccionDesdeIdentidad(interesado, lectura);
            lectura.setInteresadoVinculado(interesado);
            lectura.setVinculadoAutomaticamente(true);
            lectura.setRequiereRevision(false);
            lectura.setMensaje("Identidad leida y vinculada con interesado existente.");
            identidadLecturaRepository.save(lectura);
        });
    }

    private Interesado interesadoPorIdentificador(List<ExpedienteInteresado> relaciones, String identificador) {
        if (identificador == null) {
            return null;
        }
        return relaciones.stream()
                .map(ExpedienteInteresado::getInteresado)
                .filter(interesado -> interesado != null && identificador.equals(normalizarIdentificador(interesado.getDni())))
                .distinct()
                .findFirst()
                .orElse(null);
    }

    private void actualizarDireccionDesdeIdentidad(Interesado interesado, DocumentoIdentidadLectura lectura) {
        String direccion = normalizarDireccionCompleta(lectura.getDireccionTexto());
        if (direccion != null && !direccion.equals(interesado.getDireccion())) {
            interesado.setDireccion(direccion);
            interesadoRepository.save(interesado);
        }
    }

    private void sincronizarRequisitosExpediente(Expediente expediente, Usuario usuario) {
        requisitoDocumentalExpedienteService.sincronizarYListar(
                expediente,
                expedienteInteresadoRepository.findByExpedienteId(expediente.getId()),
                documentoRepository.findByExpedienteId(expediente.getId()),
                usuario
        );
    }

    private boolean setIfBlank(String actual, String nuevo, java.util.function.Consumer<String> setter) {
        if ((actual == null || actual.isBlank()) && nuevo != null && !nuevo.isBlank()) {
            setter.accept(nuevo);
            return true;
        }
        return false;
    }

    private String normalizarDireccionCompleta(String direccion) {
        String normalizada = direccion != null ? direccion.replaceAll("\\s+", " ").trim() : null;
        return TextNormalizer.upperOrNull(normalizada);
    }

    private boolean esDocumentoIdentidad(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.DNI || tipoDocumento == TipoDocumento.CIF;
    }

    private TipoPersona inferirTipoPersona(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return TipoPersona.PARTICULAR;
        }
        char first = identificador.charAt(0);
        if (Character.isLetter(first) && first != 'X' && first != 'Y' && first != 'Z') {
            return TipoPersona.EMPRESA;
        }
        return TipoPersona.PARTICULAR;
    }

    private String nombreDocumento(Documento documento) {
        return documento.getNombreArchivoOriginal() != null && !documento.getNombreArchivoOriginal().isBlank()
                ? documento.getNombreArchivoOriginal()
                : "documento " + documento.getId();
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
