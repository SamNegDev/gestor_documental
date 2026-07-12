package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ClienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.util.DireccionEstructurada;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import com.example.gestor_documental.util.NombrePersonaNormalizer;
import com.example.gestor_documental.util.TextNormalizer;
import com.example.gestor_documental.validation.DniNieValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentoIdentidadLecturaServiceImpl implements DocumentoIdentidadLecturaService {

    private static final int IDENTIDAD_RENDER_DPI = 300;
    private static final int IDENTIDAD_MAX_PAGINAS_PROCESADAS = 2;
    private static final double CONFIANZA_MINIMA_AUTOMATICA = 0.80;

    private final DocumentoService documentoService;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository lecturaRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final InteresadoRepository interesadoRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalExpedienteService;
    private final OpenAiProperties openAiProperties;
    private final DniNieValidator dniNieValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional(readOnly = true)
    public DocumentoIdentidadLecturaResponse obtenerLectura(Long documentoId, Usuario usuario) {
        documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        return lecturaRepository.findByDocumentoId(documentoId)
                .map(DocumentoIdentidadLecturaResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentoIdentidadLecturaResponse leerIdentidad(Long documentoId, boolean forzar, Usuario usuario) {
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        validarTipoDocumento(documento);
        if (!forzar) {
            DocumentoIdentidadLectura lecturaExistente = lecturaRepository.findByDocumentoId(documentoId).orElse(null);
            if (lecturaExistente != null) {
                return DocumentoIdentidadLecturaResponse.from(lecturaExistente);
            }
        }
        if (!openAiProperties.hasApiKey()) {
            throw new OperacionInvalidaException("OPENAI_API_KEY no esta configurada.");
        }

        Path ruta = resolverRutaDocumento(documento);
        String modeloUsado = modeloIdentidad();
        JsonNode resultado = llamarOpenAi(documento, ruta, modeloUsado);
        String modeloAvanzado = modeloAvanzado();
        if (debeReintentarIdentidad(resultado) && modeloDistinto(modeloUsado, modeloAvanzado)) {
            modeloUsado = modeloAvanzado;
            resultado = llamarOpenAi(documento, ruta, modeloUsado);
        }
        DocumentoIdentidadLectura lectura = lecturaRepository.findByDocumentoId(documentoId).orElseGet(DocumentoIdentidadLectura::new);
        lectura.setDocumento(documento);
        aplicarResultado(documento, lectura, resultado, usuario, modeloUsado);
        lectura = lecturaRepository.save(lectura);
        return DocumentoIdentidadLecturaResponse.from(lectura);
    }

    private void validarTipoDocumento(Documento documento) {
        TipoDocumento tipo = documento.getTipoDocumento();
        if (tipo != TipoDocumento.DNI && tipo != TipoDocumento.CIF) {
            throw new OperacionInvalidaException("Solo se puede leer identidad en documentos DNI o CIF.");
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
            payload.set("text", construirFormatoTexto("lectura_identidad_documento", esquemaLecturaIdentidad()));

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
            throw new RuntimeException("Error preparando la lectura de identidad", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lectura de identidad interrumpida", exception);
        }
    }

    private ArrayNode construirInput(Documento documento, Path ruta) throws IOException {
        ArrayNode input = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode texto = objectMapper.createObjectNode();
        texto.put("type", "input_text");
        texto.put("text", promptLecturaIdentidad(documento));
        content.add(texto);

        List<ImagenProcesada> imagenes = procesarDocumento(ruta, documento);
        for (ImagenProcesada imagen : imagenes) {
            ObjectNode nota = objectMapper.createObjectNode();
            nota.put("type", "input_text");
            nota.put("text", "Documento " + documento.getId() + ", imagen " + imagen.nombre()
                    + ". Detecta todas las identidades DNI/NIE/CIF visibles en esta imagen.");
            content.add(nota);

            ObjectNode image = objectMapper.createObjectNode();
            image.put("type", "input_image");
            image.put("image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(imagen.bytes()));
            content.add(image);
        }

        user.set("content", content);
        input.add(user);
        return input;
    }

    private String promptLecturaIdentidad(Documento documento) {
        return """
                Extrae datos estructurados de todos los documentos de identidad espanoles, NIE o CIF visibles.
                El tipo documental esperado es %s, pero si el contenido muestra otra identidad compatible indicalo.
                El documento puede contener una o varias tarjetas DNI/NIE, anversos, reversos, CIF o certificados de empresa.
                No determines comprador, vendedor, titular ni ningun rol de la operacion.
                Devuelve una entrada por cada identidad distinta visible. Si hay dos DNI en el mismo PDF o pagina, devuelve dos entradas.
                Identificador: mayusculas, sin espacios, guiones ni puntos.
                No uses codigos MRZ o codigos de lectura mecanica que empiecen por IDESP como identificador; usa el DNI/NIE/CIF impreso de la persona o empresa.
                Personas fisicas: separa nombre, apellido1 y apellido2 exactamente como aparezcan; no cambies el orden.
                Si el documento tiene campos "Apellidos" y "Nombre", apellido1/apellido2 salen de Apellidos y nombre sale de Nombre.
                Empresas: usa razonSocial y deja nombre/apellidos en null.
                Fechas: formato dd/MM/yyyy. Si una fecha no aparece clara, null.
                Direccion: devuelve direccionTexto en una sola linea si aparece.
                Si la direccion aparece clara, separala tambien en tipoVia, nombreVia, numeroVia, bloque, portal, escalera, piso, puerta, codigoPostal, municipio y provincia.
                No inventes campos de direccion: si una parte no aparece clara, null.
                No inventes datos ni completes segundos nombres si no se ven. Si un identificador no se lee con seguridad, devuelve null y confianza baja en esa entrada.
                Devuelve solo el JSON del esquema.
                """.formatted(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : "");
    }

    private List<ImagenProcesada> procesarDocumento(Path ruta, Documento documento) throws IOException {
        if (esPdf(documento, ruta)) {
            return procesarPdf(ruta);
        }
        BufferedImage imagen = ImageIO.read(ruta.toFile());
        if (imagen == null) {
            throw new OperacionInvalidaException("El documento no se pudo procesar como imagen o PDF.");
        }
        BufferedImage procesada = ampliarSiNecesario(recortarZonaUtil(toRgb(imagen)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(procesada, "png", output);
        return List.of(new ImagenProcesada("identidad_imagen_procesada.png", output.toByteArray()));
    }

    private List<ImagenProcesada> procesarPdf(Path ruta) throws IOException {
        List<ImagenProcesada> imagenes = new ArrayList<>();
        try (PDDocument document = PDDocument.load(Files.readAllBytes(ruta))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int paginas = Math.min(document.getNumberOfPages(), IDENTIDAD_MAX_PAGINAS_PROCESADAS);
            for (int pageIndex = 0; pageIndex < paginas; pageIndex++) {
                BufferedImage render = renderer.renderImageWithDPI(pageIndex, IDENTIDAD_RENDER_DPI, ImageType.RGB);
                BufferedImage recorte = recortarZonaUtil(render);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(recorte, "png", output);
                imagenes.add(new ImagenProcesada("identidad_pagina_" + (pageIndex + 1) + ".png", output.toByteArray()));
            }
        }
        return imagenes;
    }

    private void aplicarResultado(Documento documento, DocumentoIdentidadLectura lectura, JsonNode resultado, Usuario usuario, String modeloUsado) {
        List<IdentidadDetectada> identidades = DocumentoIdentidadLecturaJson.extraer(resultado);
        IdentidadDetectada principal = identidadPrincipal(identidades);
        String identificador = principal != null ? normalizarIdentificador(principal.identificador()) : null;
        Double confianza = principal != null ? principal.confianzaGlobal() : null;
        boolean revisionIa = principal == null || principal.requiereRevision();
        TipoDocumento tipoDetectado = tipoDetectado(principal != null ? principal.tipoDocumento() : null, documento.getTipoDocumento());
        boolean identificadorInvalido = identificador != null && !identificadorValido(identificador);
        boolean lecturaSegura = identificador != null
                && !identificadorInvalido
                && confianza != null
                && confianza >= CONFIANZA_MINIMA_AUTOMATICA
                && !revisionIa;
        Interesado interesadoVinculado = !lecturaSegura
                ? null
                : resolverInteresadoVinculado(documento, identificador, principal, tipoDetectado);
        boolean conflictoInteresado = lecturaSegura
                && documento.getInteresado() != null
                && identificador != null
                && !coincideIdentificador(documento.getInteresado(), identificador);
        boolean documentoDeSolicitud = documento.getSolicitud() != null;
        boolean requiereRevision = !lecturaSegura
                || (!documentoDeSolicitud && interesadoVinculado == null)
                || conflictoInteresado;

        lectura.setTipoDocumentoDetectado(tipoDetectado);
        lectura.setIdentificador(identificador);
        lectura.setNombre(limitar(principal != null ? principal.nombre() : null, 160));
        lectura.setApellido1(limitar(principal != null ? principal.apellido1() : null, 160));
        lectura.setApellido2(limitar(principal != null ? principal.apellido2() : null, 160));
        lectura.setRazonSocial(limitar(principal != null ? principal.razonSocial() : null, 220));
        lectura.setFechaNacimiento(limitar(principal != null ? principal.fechaNacimiento() : null, 20));
        lectura.setFechaCaducidad(limitar(principal != null ? principal.fechaCaducidad() : null, 20));
        lectura.setDireccionTexto(limitar(principal != null ? principal.direccionTexto() : null, 500));
        lectura.setTipoVia(limitar(principal != null ? principal.tipoVia() : null, 30));
        lectura.setNombreVia(limitar(principal != null ? principal.nombreVia() : null, 120));
        lectura.setNumeroVia(limitar(principal != null ? principal.numeroVia() : null, 20));
        lectura.setBloque(limitar(principal != null ? principal.bloque() : null, 20));
        lectura.setPortal(limitar(principal != null ? principal.portal() : null, 20));
        lectura.setEscalera(limitar(principal != null ? principal.escalera() : null, 20));
        lectura.setPiso(limitar(principal != null ? principal.piso() : null, 20));
        lectura.setPuerta(limitar(principal != null ? principal.puerta() : null, 20));
        lectura.setCodigoPostal(limitar(principal != null ? principal.codigoPostal() : null, 10));
        lectura.setMunicipio(limitar(principal != null ? principal.municipio() : null, 80));
        lectura.setProvincia(limitar(principal != null ? principal.provincia() : null, 80));
        if (TextNormalizer.upperOrNull(lectura.getDireccionTexto()) == null) {
            DireccionEstructurada direccion = DireccionEstructurada.fromLectura(lectura);
            if (direccion != null) {
                lectura.setDireccionTexto(direccion.textoCompuesto());
            }
        }
        lectura.setConfianzaGlobal(confianza);
        lectura.setInteresadoVinculado(interesadoVinculado);
        lectura.setVinculadoAutomaticamente(!requiereRevision && interesadoVinculado != null);
        lectura.setRequiereRevision(requiereRevision);
        lectura.setModelo(modeloUsado);
        lectura.setFechaLectura(LocalDateTime.now());
        lectura.setResultadoJson(resultado.toString());
        lectura.setMensaje(mensajeLectura(identificador, identificadorInvalido, interesadoVinculado, conflictoInteresado, requiereRevision,
                identidades.size()));

        if (!requiereRevision && interesadoVinculado != null) {
            boolean documentoActualizado = false;
            if (documento.getInteresado() == null) {
                documento.setInteresado(interesadoVinculado);
                documentoActualizado = true;
            }
            if (tipoDetectado != null && tipoDetectado != documento.getTipoDocumento()) {
                documento.setTipoDocumento(tipoDetectado);
                documentoActualizado = true;
            }
            if (documentoActualizado) {
                documentoRepository.save(documento);
            }
            actualizarInteresadoDesdeLectura(interesadoVinculado, lectura);
            sincronizarRequisitosExpediente(documento, usuario);
        }
    }

    private IdentidadDetectada identidadPrincipal(List<IdentidadDetectada> identidades) {
        if (identidades == null || identidades.isEmpty()) {
            return null;
        }
        return identidades.stream()
                .sorted((first, second) -> {
                    int seguridad = Boolean.compare(esIdentidadSegura(second), esIdentidadSegura(first));
                    if (seguridad != 0) return seguridad;
                    return Double.compare(confianza(second.confianzaGlobal()), confianza(first.confianzaGlobal()));
                })
                .findFirst()
                .orElse(identidades.get(0));
    }

    private boolean esIdentidadSegura(IdentidadDetectada identidad) {
        String identificador = identidad != null ? normalizarIdentificador(identidad.identificador()) : null;
        return identificador != null
                && identificadorValido(identificador)
                && confianza(identidad.confianzaGlobal()) >= CONFIANZA_MINIMA_AUTOMATICA
                && !identidad.requiereRevision();
    }

    private double confianza(Double value) {
        return value != null ? value : 0.0;
    }

    private void actualizarInteresadoDesdeLectura(Interesado interesado, DocumentoIdentidadLectura lectura) {
        String nombreCompleto = nombreCompletoLectura(lectura);
        if ((interesado.getNombre() == null || interesado.getNombre().isBlank()) && nombreCompleto != null) {
            interesado.setNombre(nombreCompleto);
        }
        completarNombreEstructurado(interesado, lectura);
        String direccion = normalizarDireccionCompleta(lectura.getDireccionTexto());
        if (direccion != null) {
            interesado.setDireccion(direccion);
        }
        actualizarDireccionEstructurada(interesado, lectura);
    }

    private void actualizarDireccionEstructurada(Interesado interesado, DocumentoIdentidadLectura lectura) {
        if (TextNormalizer.upperOrNull(lectura.getTipoVia()) != null) {
            interesado.setTipoVia(TextNormalizer.upperOrNull(lectura.getTipoVia()));
        }
        if (TextNormalizer.upperOrNull(lectura.getNombreVia()) != null) {
            interesado.setNombreVia(TextNormalizer.upperOrNull(lectura.getNombreVia()));
        }
        if (TextNormalizer.upperOrNull(lectura.getNumeroVia()) != null) {
            interesado.setNumeroVia(TextNormalizer.upperOrNull(lectura.getNumeroVia()));
        }
        if (TextNormalizer.upperOrNull(lectura.getBloque()) != null) {
            interesado.setBloque(TextNormalizer.upperOrNull(lectura.getBloque()));
        }
        if (TextNormalizer.upperOrNull(lectura.getPortal()) != null) {
            interesado.setPortal(TextNormalizer.upperOrNull(lectura.getPortal()));
        }
        if (TextNormalizer.upperOrNull(lectura.getEscalera()) != null) {
            interesado.setEscalera(TextNormalizer.upperOrNull(lectura.getEscalera()));
        }
        if (TextNormalizer.upperOrNull(lectura.getPiso()) != null) {
            interesado.setPiso(TextNormalizer.upperOrNull(lectura.getPiso()));
        }
        if (TextNormalizer.upperOrNull(lectura.getPuerta()) != null) {
            interesado.setPuerta(TextNormalizer.upperOrNull(lectura.getPuerta()));
        }
        if (TextNormalizer.upperOrNull(lectura.getCodigoPostal()) != null) {
            interesado.setCodigoPostal(TextNormalizer.upperOrNull(lectura.getCodigoPostal()));
        }
        if (TextNormalizer.upperOrNull(lectura.getMunicipio()) != null) {
            interesado.setMunicipio(TextNormalizer.upperOrNull(lectura.getMunicipio()));
        }
        if (TextNormalizer.upperOrNull(lectura.getProvincia()) != null) {
            interesado.setProvincia(TextNormalizer.upperOrNull(lectura.getProvincia()));
        }
    }

    private String nombreCompletoLectura(DocumentoIdentidadLectura lectura) {
        String razonSocial = NombrePersonaNormalizer.normalizar(lectura.getRazonSocial());
        if (razonSocial != null) {
            return razonSocial;
        }
        String joined = String.join(" ",
                List.of(
                        lectura.getNombre() != null ? lectura.getNombre() : "",
                        lectura.getApellido1() != null ? lectura.getApellido1() : "",
                        lectura.getApellido2() != null ? lectura.getApellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        return NombrePersonaNormalizer.normalizar(joined);
    }

    private String normalizarDireccionCompleta(String direccion) {
        String normalizada = direccion != null ? direccion.replaceAll("\\s+", " ").trim() : null;
        return TextNormalizer.upperOrNull(normalizada);
    }

    private void sincronizarRequisitosExpediente(Documento documento, Usuario usuario) {
        if (documento.getExpediente() == null || documento.getExpediente().getId() == null) {
            return;
        }
        Long expedienteId = documento.getExpediente().getId();
        requisitoDocumentalExpedienteService.sincronizarYListar(
                documento.getExpediente(),
                expedienteInteresadoRepository.findByExpedienteId(expedienteId),
                documentoRepository.findByExpedienteId(expedienteId),
                usuario
        );
    }

    private Interesado resolverInteresadoVinculado(Documento documento, String identificador, IdentidadDetectada resultado, TipoDocumento tipoDetectado) {
        if (identificador == null) {
            return null;
        }
        if (documento.getInteresado() != null) {
            return coincideIdentificador(documento.getInteresado(), identificador) ? documento.getInteresado() : null;
        }
        if (documento.getExpediente() == null || documento.getExpediente().getId() == null) {
            return resolverInteresadoCliente(documento, identificador, resultado, tipoDetectado);
        }
        List<Interesado> coincidencias = expedienteInteresadoRepository.findByExpedienteId(documento.getExpediente().getId())
                .stream()
                .map(ExpedienteInteresado::getInteresado)
                .filter(interesado -> coincideIdentificador(interesado, identificador))
                .distinct()
                .toList();
        return coincidencias.size() == 1 ? coincidencias.get(0) : null;
    }

    private Interesado resolverInteresadoCliente(Documento documento, String identificador, IdentidadDetectada resultado, TipoDocumento tipoDetectado) {
        Cliente cliente = documento.getCliente();
        if (cliente == null || cliente.getId() == null) {
            return null;
        }
        Interesado interesado = interesadoRepository.findByDni(identificador).orElse(null);
        if (interesado == null) {
            interesado = new Interesado();
            interesado.setDni(identificador);
            interesado.setNombre(nombreCompletoResultado(resultado, identificador));
            aplicarNombreEstructurado(interesado, resultado);
            interesado.setTipoPersona(inferirTipoPersona(identificador, tipoDetectado));
            interesado.setDireccion(normalizarDireccionCompleta(resultado != null ? resultado.direccionTexto() : null));
            aplicarDireccionEstructurada(interesado, resultado);
            interesado = interesadoRepository.save(interesado);
        }
        Interesado interesadoFinal = interesado;
        ClienteInteresado relacion = clienteInteresadoRepository
                .findByClienteIdAndInteresadoId(cliente.getId(), interesadoFinal.getId())
                .orElseGet(() -> {
                    ClienteInteresado nueva = new ClienteInteresado();
                    nueva.setCliente(cliente);
                    nueva.setInteresado(interesadoFinal);
                    return nueva;
                });
        boolean representanteLegal = esRepresentanteLegalCliente(cliente, identificador, tipoDetectado);
        if (representanteLegal && !Boolean.TRUE.equals(relacion.getRepresentanteLegal())) {
            relacion.setRepresentanteLegal(true);
        }
        if (relacion.getId() == null || representanteLegal) {
            clienteInteresadoRepository.save(relacion);
        }
        return interesado;
    }

    private void aplicarDireccionEstructurada(Interesado interesado, IdentidadDetectada resultado) {
        if (resultado == null) {
            return;
        }
        interesado.setTipoVia(TextNormalizer.upperOrNull(resultado.tipoVia()));
        interesado.setNombreVia(TextNormalizer.upperOrNull(resultado.nombreVia()));
        interesado.setNumeroVia(TextNormalizer.upperOrNull(resultado.numeroVia()));
        interesado.setBloque(TextNormalizer.upperOrNull(resultado.bloque()));
        interesado.setPortal(TextNormalizer.upperOrNull(resultado.portal()));
        interesado.setEscalera(TextNormalizer.upperOrNull(resultado.escalera()));
        interesado.setPiso(TextNormalizer.upperOrNull(resultado.piso()));
        interesado.setPuerta(TextNormalizer.upperOrNull(resultado.puerta()));
        interesado.setCodigoPostal(TextNormalizer.upperOrNull(resultado.codigoPostal()));
        interesado.setMunicipio(TextNormalizer.upperOrNull(resultado.municipio()));
        interesado.setProvincia(TextNormalizer.upperOrNull(resultado.provincia()));
    }

    private void completarNombreEstructurado(Interesado interesado, DocumentoIdentidadLectura lectura) {
        if (TextNormalizer.upperOrNull(interesado.getNombrePila()) == null && TextNormalizer.upperOrNull(lectura.getNombre()) != null) {
            interesado.setNombrePila(TextNormalizer.upperOrNull(lectura.getNombre()));
        }
        if (TextNormalizer.upperOrNull(interesado.getApellido1()) == null && TextNormalizer.upperOrNull(lectura.getApellido1()) != null) {
            interesado.setApellido1(TextNormalizer.upperOrNull(lectura.getApellido1()));
        }
        if (TextNormalizer.upperOrNull(interesado.getApellido2()) == null && TextNormalizer.upperOrNull(lectura.getApellido2()) != null) {
            interesado.setApellido2(TextNormalizer.upperOrNull(lectura.getApellido2()));
        }
        if (TextNormalizer.upperOrNull(interesado.getRazonSocial()) == null && NombrePersonaNormalizer.normalizar(lectura.getRazonSocial()) != null) {
            interesado.setRazonSocial(NombrePersonaNormalizer.normalizar(lectura.getRazonSocial()));
        }
    }

    private void aplicarNombreEstructurado(Interesado interesado, IdentidadDetectada resultado) {
        if (resultado == null) {
            return;
        }
        interesado.setNombrePila(TextNormalizer.upperOrNull(resultado.nombre()));
        interesado.setApellido1(TextNormalizer.upperOrNull(resultado.apellido1()));
        interesado.setApellido2(TextNormalizer.upperOrNull(resultado.apellido2()));
        interesado.setRazonSocial(NombrePersonaNormalizer.normalizar(resultado.razonSocial()));
    }

    private String nombreCompletoResultado(IdentidadDetectada resultado, String fallback) {
        String razonSocial = NombrePersonaNormalizer.normalizar(resultado != null ? resultado.razonSocial() : null);
        if (razonSocial != null) {
            return razonSocial;
        }
        String joined = String.join(" ",
                List.of(
                        resultado != null && resultado.nombre() != null ? resultado.nombre() : "",
                        resultado != null && resultado.apellido1() != null ? resultado.apellido1() : "",
                        resultado != null && resultado.apellido2() != null ? resultado.apellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        String nombre = NombrePersonaNormalizer.normalizar(joined);
        return nombre != null ? nombre : fallback;
    }

    private TipoPersona inferirTipoPersona(String identificador, TipoDocumento tipoDetectado) {
        if (tipoDetectado == TipoDocumento.CIF || identificador.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]")) {
            return TipoPersona.EMPRESA;
        }
        return TipoPersona.PARTICULAR;
    }

    private boolean esRepresentanteLegalCliente(Cliente cliente, String identificador, TipoDocumento tipoDetectado) {
        String nifCliente = normalizarIdentificador(cliente.getNif());
        return tipoDetectado == TipoDocumento.DNI
                && nifCliente != null
                && !nifCliente.equals(identificador)
                && nifCliente.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private boolean coincideIdentificador(Interesado interesado, String identificador) {
        return interesado != null && identificador != null
                && identificador.equals(normalizarIdentificador(interesado.getDni()));
    }

    private String mensajeLectura(
            String identificador,
            boolean identificadorInvalido,
            Interesado interesado,
            boolean conflictoInteresado,
            boolean requiereRevision,
            int identidadesDetectadas
    ) {
        if (identidadesDetectadas > 1) {
            return "Se detectaron " + identidadesDetectadas + " identidades en el documento; revisar asignacion si procede.";
        }
        if (identificador == null) {
            return "No se pudo leer un DNI/CIF con seguridad.";
        }
        if (identificadorInvalido) {
            return "El DNI/NIE leido no supera la validacion de letra; revisar documento.";
        }
        if (conflictoInteresado) {
            return "El documento ya estaba asociado a otro interesado; revisar antes de validar.";
        }
        if (requiereRevision) {
            return "Identidad leida con dudas; revisar antes de vincular.";
        }
        if (interesado != null) {
            return "Identidad leida y vinculada con interesado existente.";
        }
        if (!requiereRevision) {
            return "Identidad leida con datos suficientes.";
        }
        return "Identidad leida.";
    }

    private TipoDocumento tipoDetectado(String valor, TipoDocumento fallback) {
        if (valor == null) {
            return fallback;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.contains("CIF")) {
            return TipoDocumento.CIF;
        }
        if (normalizado.contains("DNI") || normalizado.contains("NIE")) {
            return TipoDocumento.DNI;
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

    private ObjectNode esquemaLecturaIdentidad() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        ObjectNode identidades = objectMapper.createObjectNode();
        identidades.put("type", "array");
        identidades.put("description", "Todas las identidades DNI/NIE/CIF distintas visibles en el documento.");
        identidades.set("items", esquemaIdentidadItem());
        props.set("identidades", identidades);
        props.set("observaciones", nullableString("Resumen breve de dudas generales si las hay."));
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        props.fieldNames().forEachRemaining(required::add);
        schema.set("required", required);
        return schema;
    }

    private ObjectNode esquemaIdentidadItem() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        props.set("tipoDocumento", nullableString("DNI, NIE, CIF o null."));
        props.set("identificador", nullableString("DNI/NIE/CIF normalizado sin separadores."));
        props.set("nombre", nullableString("Nombre de persona fisica."));
        props.set("apellido1", nullableString("Primer apellido de persona fisica."));
        props.set("apellido2", nullableString("Segundo apellido de persona fisica."));
        props.set("razonSocial", nullableString("Razon social si es persona juridica."));
        props.set("fechaNacimiento", nullableString("Formato dd/MM/yyyy."));
        props.set("fechaCaducidad", nullableString("Formato dd/MM/yyyy."));
        props.set("direccionTexto", nullableString("Direccion completa en una linea."));
        props.set("tipoVia", nullableString("Tipo de via: CALLE, AVENIDA, CARRETERA, CAMINO, PLAZA, etc."));
        props.set("nombreVia", nullableString("Nombre de la via sin tipo ni numero."));
        props.set("numeroVia", nullableString("Numero de la via si aparece."));
        props.set("bloque", nullableString("Bloque si aparece."));
        props.set("portal", nullableString("Portal si aparece."));
        props.set("escalera", nullableString("Escalera si aparece."));
        props.set("piso", nullableString("Piso, planta o Pxx si aparece."));
        props.set("puerta", nullableString("Puerta, letra o mano si aparece."));
        props.set("codigoPostal", nullableString("Codigo postal de 5 digitos si aparece."));
        props.set("municipio", nullableString("Municipio/localidad si aparece claro."));
        props.set("provincia", nullableString("Provincia si aparece clara."));
        props.set("confianzaGlobal", nullableNumber("Confianza entre 0 y 1."));
        props.set("requiereRevision", nullableBoolean("true si algun dato clave no es seguro."));
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

    private String limitar(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private boolean esPdf(Documento documento, Path ruta) {
        String nombre = (documento.getNombreArchivoOriginal() != null ? documento.getNombreArchivoOriginal() : ruta.getFileName().toString())
                .toLowerCase(Locale.ROOT);
        return nombre.endsWith(".pdf") || ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private BufferedImage recortarZonaUtil(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int rowThreshold = Math.max(24, width / 140);
        int colThreshold = Math.max(24, height / 140);

        for (int y = 0; y < height; y++) {
            int count = 0;
            for (int x = 0; x < width; x++) {
                if (esPixelContenido(image.getRGB(x, y))) {
                    count++;
                }
            }
            if (count >= rowThreshold) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        for (int x = 0; x < width; x++) {
            int count = 0;
            for (int y = 0; y < height; y++) {
                if (esPixelContenido(image.getRGB(x, y))) {
                    count++;
                }
            }
            if (count >= colThreshold) {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
        }

        if (maxX < minX || maxY < minY) {
            return ampliarSiNecesario(image);
        }

        int margin = Math.max(45, Math.min(width, height) / 35);
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width - 1, maxX + margin);
        maxY = Math.min(height - 1, maxY + margin);

        BufferedImage cropped = image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return ampliarSiNecesario(cropped);
    }

    private boolean esPixelContenido(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int luminance = (red * 30 + green * 59 + blue * 11) / 100;
        return luminance < 232 || max - min > 22;
    }

    private BufferedImage ampliarSiNecesario(BufferedImage image) {
        int minSide = Math.min(image.getWidth(), image.getHeight());
        int maxSide = Math.max(image.getWidth(), image.getHeight());
        if (minSide >= 900 && maxSide <= 1600) {
            return image;
        }
        double scaleUp = 900.0 / Math.max(1, minSide);
        double scaleDown = 1600.0 / Math.max(1, maxSide);
        double scale = minSide < 900 ? Math.min(2.2, scaleUp) : scaleDown;
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private String modeloIdentidad() {
        if (openAiProperties.getIdentityModel() != null && !openAiProperties.getIdentityModel().isBlank()) {
            return openAiProperties.getIdentityModel();
        }
        return openAiProperties.getModel();
    }

    private String modeloAvanzado() {
        return openAiProperties.getModel() != null && !openAiProperties.getModel().isBlank()
                ? openAiProperties.getModel()
                : modeloIdentidad();
    }

    private boolean modeloDistinto(String actual, String candidato) {
        return actual != null && candidato != null && !actual.equalsIgnoreCase(candidato);
    }

    private boolean debeReintentarIdentidad(JsonNode resultado) {
        IdentidadDetectada principal = identidadPrincipal(DocumentoIdentidadLecturaJson.extraer(resultado));
        String identificador = principal != null ? normalizarIdentificador(principal.identificador()) : null;
        Double confianza = principal != null ? principal.confianzaGlobal() : null;
        return identificador == null
                || !identificadorValido(identificador)
                || principal.requiereRevision()
                || confianza == null
                || confianza < 0.85;
    }

    private record ImagenProcesada(String nombre, byte[] bytes) {
    }
}
