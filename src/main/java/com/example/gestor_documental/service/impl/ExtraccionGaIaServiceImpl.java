package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.ia.ExtraccionGaDocumentoSeleccionadoResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaJobRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaJobResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaLoteExportRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaPreviewResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaQueueItemResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaRevisionRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaRevisionResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaSincronizacionResponse;
import com.example.gestor_documental.dto.ia.LecturaIaClienteResponse;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoExtraccionGaJob;
import com.example.gestor_documental.enums.EstadoRevisionGa;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.ExtraccionGaJob;
import com.example.gestor_documental.model.ExtraccionGaRevision;
import com.example.gestor_documental.model.GestionPersonaCatalogo;
import com.example.gestor_documental.model.GestionPersonaRepresentanteCatalogo;
import com.example.gestor_documental.model.GestionVehiculoCatalogo;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Vehiculo;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.ExtraccionGaJobRepository;
import com.example.gestor_documental.repository.ExtraccionGaRevisionRepository;
import com.example.gestor_documental.repository.GestionPersonaCatalogoRepository;
import com.example.gestor_documental.repository.GestionPersonaRepresentanteCatalogoRepository;
import com.example.gestor_documental.repository.GestionVehiculoCatalogoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.repository.VehiculoRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.ExtraccionGaIaService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.util.GaDateNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ExtraccionGaIaServiceImpl implements ExtraccionGaIaService {

    private static final Set<TipoDocumento> TIPOS_RELEVANTES = EnumSet.of(
            TipoDocumento.DNI,
            TipoDocumento.CIF,
            TipoDocumento.CONTRATO_COMPRAVENTA,
            TipoDocumento.FACTURA,
            TipoDocumento.PERMISO_CIRCULACION,
            TipoDocumento.FICHA_TECNICA,
            TipoDocumento.INFORME_DGT,
            TipoDocumento.CAMBIO_TITULARIDAD,
            TipoDocumento.MANDATO,
            TipoDocumento.MANDATO_REPRESENTACION,
            TipoDocumento.AUTORIZACION_SERAFIN,
            TipoDocumento.MODELO_620
    );
    private static final Set<TipoDocumento> TIPOS_REUTILIZABLES_CLIENTE = EnumSet.of(
            TipoDocumento.DNI,
            TipoDocumento.CIF,
            TipoDocumento.MANDATO,
            TipoDocumento.MANDATO_REPRESENTACION
    );
    private static final Set<TipoDocumento> TIPOS_DNI = EnumSet.of(
            TipoDocumento.DNI,
            TipoDocumento.CIF,
            TipoDocumento.MANDATO,
            TipoDocumento.MANDATO_REPRESENTACION,
            TipoDocumento.EXPEDIENTE_COMPLETO
    );

    private static final Map<TipoDocumento, Integer> PRIORIDAD_TIPO = prioridadTipo();
    private static final long LIMITE_BYTES_REQUEST = 50L * 1024L * 1024L;
    private static final int DNI_RENDER_DPI = 300;
    private static final int DNI_MAX_PAGINAS_PROCESADAS = 2;
    private static final int USOS_CLIENTE_SIN_LIMITE = 0;
    private static final int USOS_RESTANTES_SIN_LIMITE = Integer.MAX_VALUE;
    private static final DateTimeFormatter FORMATO_FECHA_GA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_DOCUMENTO_GA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Charset XML_GA_CHARSET = Charset.forName("ISO-8859-1");
    private static final double CARTOCIUDAD_CONFIANZA_MINIMA = 0.78;

    private final DocumentoRepository documentoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final GestionPersonaCatalogoRepository gestionPersonaCatalogoRepository;
    private final GestionPersonaRepresentanteCatalogoRepository gestionPersonaRepresentanteCatalogoRepository;
    private final GestionVehiculoCatalogoRepository gestionVehiculoCatalogoRepository;
    private final ExtraccionGaJobRepository extraccionGaJobRepository;
    private final ExtraccionGaRevisionRepository extraccionGaRevisionRepository;
    private final InteresadoRepository interesadoRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ExpedienteService expedienteService;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalService;
    private final OpenAiProperties openAiProperties;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Optional<CartoCiudadCandidate>> cartoCiudadCache = new ConcurrentHashMap<>();
    private final ExecutorService extraccionGaExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "extraccion-ga-worker");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.cartociudad.enabled:true}")
    private boolean cartoCiudadEnabled;

    @Value("${app.cartociudad.candidates-url:https://www.cartociudad.es/geocoder/api/geocoder/candidates}")
    private String cartoCiudadCandidatesUrl;

    @Value("${app.cartociudad.timeout-seconds:8}")
    private int cartoCiudadTimeoutSeconds;

    @PreDestroy
    void cerrarExecutorExtraccionGa() {
        extraccionGaExecutor.shutdownNow();
    }

    @Override
    @Transactional
    public ExtraccionGaPreviewResponse preview(Long expedienteId, Usuario admin) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        List<DocumentoSeleccionado> documentos = seleccionarDocumentos(expediente);
        String modelo = modeloNormalizado(null);
        DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, admin);
        return construirPreview(expediente, documentos, modelo, documentacion);
    }

    @Override
    @Transactional
    public ExtraccionGaResponse probar(Long expedienteId, ExtraccionGaRequest request, Usuario admin) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        List<DocumentoSeleccionado> documentos = seleccionarDocumentos(expediente);
        String modelo = modeloNormalizado(request != null ? request.modelo() : null);
        DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, admin);
        ExtraccionGaPreviewResponse preview = construirPreview(expediente, documentos, modelo, documentacion);

        if (documentacion.bloqueada()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), mensajeBloqueoDocumental(expediente, documentacion));
        }
        if (documentos.isEmpty()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), "No hay documentos relevantes para extraer.");
        }
        if (request == null || !request.debeEjecutar()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), "Previsualizacion generada. Envia ejecutar=true para llamar a la API.");
        }
        if (!openAiProperties.hasApiKey()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), "OPENAI_API_KEY no esta configurada.");
        }
        if (preview.tamanoTotalBytes() > LIMITE_BYTES_REQUEST) {
            throw new OperacionInvalidaException("Los documentos seleccionados superan el limite de 50 MB por request.");
        }

        RespuestaOpenAi respuesta = llamarOpenAi(modelo, documentos);
        return new ExtraccionGaResponse(preview, true, respuesta.resultadoJson(), respuesta.uso(), null);
    }

    @Override
    @Transactional
    public ExtraccionGaResponse probarMultiple(Long expedienteId, ExtraccionGaRequest request, Usuario admin) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        List<DocumentoSeleccionado> documentos = seleccionarDocumentos(expediente);
        String modelo = modeloNormalizado(request != null ? request.modelo() : null);
        DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, admin);
        ExtraccionGaPreviewResponse preview = construirPreview(expediente, documentos, modelo, documentacion);

        if (documentacion.bloqueada()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), mensajeBloqueoDocumental(expediente, documentacion));
        }
        if (documentos.isEmpty()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), "No hay documentos relevantes para extraer.");
        }
        if (request == null || !request.debeEjecutar()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), "Previsualizacion generada. Envia ejecutar=true para llamar a la API.");
        }
        if (!openAiProperties.hasApiKey()) {
            return new ExtraccionGaResponse(preview, false, null, Map.of(), "OPENAI_API_KEY no esta configurada.");
        }

        ObjectNode bloqueContrato = ejecutarBloque(modelo, documentos, "bloqueContrato", Set.of(
                TipoDocumento.CONTRATO_COMPRAVENTA,
                TipoDocumento.FACTURA
        ), promptContrato(), "extraccion_ga_contrato", esquemaContratoCompacto());
        ObjectNode bloqueDni = ejecutarBloque(modelo, documentos, "bloqueDni", TIPOS_DNI, promptDni(), "extraccion_ga_dni", esquemaDniCompacto());
        ObjectNode bloqueVehiculo = ejecutarBloque(modelo, documentos, "bloqueVehiculo", Set.of(
                TipoDocumento.PERMISO_CIRCULACION,
                TipoDocumento.FICHA_TECNICA,
                TipoDocumento.INFORME_DGT
        ), promptVehiculo(), "extraccion_ga_vehiculo", esquemaVehiculoCompacto());
        ObjectNode bloqueFiscal = ejecutarBloque(modelo, documentos, "bloqueFiscal", Set.of(
                TipoDocumento.MODELO_620
        ), promptFiscal(), "extraccion_ga_fiscal", esquemaFiscalCompacto());
        ObjectNode bloqueConsolidacion = ejecutarConsolidacion(modelo, bloqueContrato, bloqueDni, bloqueVehiculo, bloqueFiscal);
        JsonNode consolidado = bloqueConsolidacion.path("resultado");
        if (consolidado instanceof ObjectNode consolidadoObject) {
            bloqueConsolidacion.set("catalogoGestionTrafico", aplicarCatalogoAuxiliar(consolidadoObject, expediente));
            bloqueConsolidacion.set("cartoCiudad", aplicarCartoCiudadDirecciones(consolidadoObject));
            normalizarDireccionesConsolidadas(consolidadoObject);
            normalizarFechasConsolidadas(consolidadoObject);
        }

        ObjectNode resultado = objectMapper.createObjectNode();
        resultado.put("modo", "multiple");
        resultado.set("bloqueContrato", bloqueContrato);
        resultado.set("bloqueDni", bloqueDni);
        resultado.set("bloqueVehiculo", bloqueVehiculo);
        resultado.set("bloqueFiscal", bloqueFiscal);
        resultado.set("bloqueConsolidacion", bloqueConsolidacion);
        resultado.set("formatoGaNormalizado", construirFormatoGaNormalizado(bloqueConsolidacion.path("resultado")));
        aplicarRevisionDeterminista(resultado, bloqueConsolidacion.path("resultado"));

        Map<String, Object> uso = usoMultiple(resultado);
        try {
            return new ExtraccionGaResponse(preview, true, objectMapper.writeValueAsString(resultado), uso, null);
        } catch (IOException exception) {
            throw new RuntimeException("No se pudo serializar el resultado multiple", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExtraccionGaRevisionResponse obtenerRevision(Long expedienteId, Usuario admin) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        return extraccionGaRevisionRepository.findByExpedienteId(expediente.getId())
                .map(this::mapRevision)
                .orElse(null);
    }

    @Override
    @Transactional
    public ExtraccionGaRevisionResponse guardarRevision(Long expedienteId, ExtraccionGaRevisionRequest request, Usuario admin) {
        if (request == null || request.datosValidadosJson() == null || request.datosValidadosJson().isBlank()) {
            throw new OperacionInvalidaException("No hay datos validados para guardar");
        }
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        JsonNode datosValidados = leerJson(request.datosValidadosJson(), "datos validados");
        String datosValidadosJson = normalizarDatosValidadosParaXml(datosValidados);
        if (request.resultadoIaJson() != null && !request.resultadoIaJson().isBlank()) {
            leerJson(request.resultadoIaJson(), "resultado IA");
        }

        ExtraccionGaRevision revision = extraccionGaRevisionRepository.findByExpedienteId(expediente.getId())
                .orElseGet(() -> {
                    ExtraccionGaRevision nueva = new ExtraccionGaRevision();
                    nueva.setExpediente(expediente);
                    nueva.setCreadoPor(admin);
                    return nueva;
                });
        EstadoRevisionGa estado = request.estado() != null ? request.estado() : EstadoRevisionGa.BORRADOR;
        if (estado == EstadoRevisionGa.EXPORTADO) {
            throw new OperacionInvalidaException("El estado EXPORTADO solo se asigna al generar el XML");
        }

        revision.setResultadoIaJson(request.resultadoIaJson());
        revision.setDatosValidadosJson(datosValidadosJson);
        revision.setModelo(limitar(request.modelo(), 80));
        revision.setConfianzaGlobal(extraerConfianzaGlobal(datosValidados));
        revision.setRequiereRevisionHumana(extraerRequiereRevision(datosValidados));
        revision.setEstado(estado);
        revision.setRevisadoPor(admin);
        if (estado == EstadoRevisionGa.PREPARADO_EXPORTACION && revision.getFechaPreparado() == null) {
            revision.setFechaPreparado(LocalDateTime.now());
        }
        if (estado == EstadoRevisionGa.BORRADOR) {
            revision.setFechaPreparado(null);
        }
        ExtraccionGaRevision guardada = extraccionGaRevisionRepository.save(revision);
        requisitoDocumentalService.sincronizarYListar(
                expediente,
                expedienteInteresadoRepository.findByExpedienteId(expediente.getId()),
                documentoRepository.findByExpedienteId(expediente.getId()),
                admin
        );
        return mapRevision(guardada);
    }

    private String normalizarDatosValidadosParaXml(JsonNode datosValidados) {
        if (!(datosValidados instanceof ObjectNode datosObject)) {
            throw new OperacionInvalidaException("Los datos validados deben tener formato de objeto");
        }
        JsonNode consolidado = nodoConsolidado(datosObject);
        if (consolidado instanceof ObjectNode consolidadoObject) {
            aplicarCartoCiudadDirecciones(consolidadoObject);
            normalizarDireccionesConsolidadas(consolidadoObject);
            normalizarFechasConsolidadas(consolidadoObject);
        }
        datosObject.set("formatoGaNormalizado", construirFormatoGaNormalizado(consolidado));
        aplicarRevisionDeterminista(datosObject, consolidado);
        try {
            return objectMapper.writeValueAsString(datosObject);
        } catch (IOException exception) {
            throw new OperacionInvalidaException("No se pudieron preparar los datos validados para XML");
        }
    }

    @Override
    @Transactional
    public ExtraccionGaRevisionResponse prepararRevision(Long expedienteId, Usuario admin) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        ExtraccionGaRevision revision = extraccionGaRevisionRepository.findByExpedienteId(expediente.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay revision GA guardada para este expediente"));
        revision.setEstado(EstadoRevisionGa.PREPARADO_EXPORTACION);
        revision.setFechaPreparado(LocalDateTime.now());
        revision.setRevisadoPor(admin);
        return mapRevision(extraccionGaRevisionRepository.save(revision));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtraccionGaRevisionResponse> listarPreparadas(Usuario admin) {
        return extraccionGaRevisionRepository.findByEstadoOrderByFechaPreparadoAsc(EstadoRevisionGa.PREPARADO_EXPORTACION)
                .stream()
                .filter(revision -> expedienteService.tienePermisoExpediente(revision.getExpediente(), admin))
                .map(this::mapRevision)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtraccionGaQueueItemResponse> listarPendientesRevision(Usuario admin) {
        List<Expediente> expedientes = expedienteRepository.findCandidatosExtraccionGa(
                tiposCandidatosExtraccionGa(),
                List.of(EstadoRevisionGa.PREPARADO_EXPORTACION, EstadoRevisionGa.EXPORTADO),
                PageRequest.of(0, 150)
        ).stream()
                .filter(expediente -> expedienteService.tienePermisoExpediente(expediente, admin))
                .toList();
        if (expedientes.isEmpty()) {
            return List.of();
        }
        List<Long> expedienteIds = expedientes.stream().map(Expediente::getId).toList();
        Map<Long, ExtraccionGaRevision> revisionesPorExpediente = new LinkedHashMap<>();
        for (ExtraccionGaRevision revision : extraccionGaRevisionRepository.findByExpedienteIdIn(expedienteIds)) {
            revisionesPorExpediente.put(revision.getExpediente().getId(), revision);
        }
        Map<Long, ExtraccionGaJob> trabajosPorExpediente = new LinkedHashMap<>();
        for (ExtraccionGaJob job : extraccionGaJobRepository.findUltimosPorExpediente(expedienteIds)) {
            trabajosPorExpediente.put(job.getExpediente().getId(), job);
        }
        return expedientes.stream()
                .map(expediente -> mapQueueItem(
                        expediente,
                        revisionesPorExpediente.get(expediente.getId()),
                        trabajosPorExpediente.get(expediente.getId())
                ))
                .toList();
    }

    @Override
    public List<ExtraccionGaJobResponse> crearJobs(ExtraccionGaJobRequest request, Usuario admin) {
        if (request == null || request.expedienteIds() == null || request.expedienteIds().isEmpty()) {
            throw new OperacionInvalidaException("Selecciona al menos un expediente para extraer");
        }
        String modelo = modeloNormalizado(request.modelo());
        List<Long> expedienteIds = request.expedienteIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (expedienteIds.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona al menos un expediente para extraer");
        }

        List<ExtraccionGaJobResponse> respuestas = new ArrayList<>();
        List<Long> jobsCreados = new ArrayList<>();
        List<Expediente> expedientesPreparados = new ArrayList<>();
        List<String> bloqueos = new ArrayList<>();
        for (Long expedienteId : expedienteIds) {
            Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
            Optional<ExtraccionGaJob> activo = extraccionGaJobRepository.findTopByExpedienteIdOrderByFechaCreacionDesc(expedienteId)
                    .filter(job -> esJobActivo(job.getEstado()));
            if (activo.isPresent()) {
                respuestas.add(mapJob(activo.get()));
                continue;
            }
            DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, admin);
            if (documentacion.bloqueada()) {
                bloqueos.add(mensajeBloqueoDocumental(expediente, documentacion));
                continue;
            }
            expedientesPreparados.add(expediente);
        }
        if (!bloqueos.isEmpty()) {
            throw new OperacionInvalidaException("No se ha lanzado la extraccion porque hay documentacion pendiente: "
                    + resumenBloqueos(bloqueos));
        }

        for (Expediente expediente : expedientesPreparados) {
            ExtraccionGaJob guardado = crearJobExtraccion(expediente, admin, modelo, "ADMIN", false, "En cola");
            respuestas.add(mapJob(guardado));
            jobsCreados.add(guardado.getId());
        }
        jobsCreados.forEach(this::programarJob);
        return respuestas;
    }

    @Override
    public ExtraccionGaJobResponse crearLecturaInicialSiProcede(Long expedienteId, Usuario usuario) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, usuario);
        Optional<ExtraccionGaJob> activo = extraccionGaJobRepository.findTopByExpedienteIdOrderByFechaCreacionDesc(expedienteId)
                .filter(job -> esJobActivo(job.getEstado()));
        if (activo.isPresent()) {
            return null;
        }
        if (extraccionGaRevisionRepository.findByExpedienteId(expediente.getId()).isPresent()) {
            return null;
        }
        if (!openAiProperties.hasApiKey()) {
            return null;
        }
        DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, usuario);
        if (documentacion.bloqueada() || !hayDocumentosSeleccionables(expediente)) {
            return null;
        }

        ExtraccionGaJob job = crearJobExtraccion(
                expediente,
                usuario,
                modeloNormalizado(null),
                "AUTO_INICIAL",
                false,
                "Lectura IA inicial en cola"
        );
        programarJob(job.getId());
        return mapJob(job);
    }

    @Override
    @Transactional
    public LecturaIaClienteResponse obtenerLecturaCliente(Long expedienteId, Usuario cliente) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, cliente);
        return construirEstadoLecturaCliente(expediente, cliente, false, null);
    }

    @Override
    public LecturaIaClienteResponse solicitarLecturaCliente(Long expedienteId, Usuario cliente) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, cliente);
        Optional<ExtraccionGaJob> activo = extraccionGaJobRepository.findTopByExpedienteIdOrderByFechaCreacionDesc(expedienteId)
                .filter(job -> esJobActivo(job.getEstado()));
        if (activo.isPresent()) {
            return construirEstadoLecturaCliente(expediente, cliente, false, "Ya hay una lectura IA en curso.");
        }

        if (!openAiProperties.hasApiKey()) {
            return construirEstadoLecturaCliente(expediente, cliente, false, "La lectura IA no esta disponible en este momento.");
        }

        DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, cliente);
        if (documentacion.bloqueada()) {
            return construirEstadoLecturaCliente(expediente, cliente, false, "Falta documentacion minima para iniciar la lectura IA.");
        }
        if (!hayDocumentosSeleccionables(expediente)) {
            return construirEstadoLecturaCliente(expediente, cliente, false, "No hay documentos relevantes para iniciar la lectura IA.");
        }

        ExtraccionGaJob job = crearJobExtraccion(
                expediente,
                cliente,
                modeloNormalizado(null),
                cliente != null && cliente.getRolUsuario() == RolUsuario.CLIENTE ? "CLIENTE" : "PORTAL_CLIENTE",
                cliente != null && cliente.getRolUsuario() == RolUsuario.CLIENTE,
                "Lectura IA solicitada por cliente"
        );
        programarJob(job.getId());
        return construirEstadoLecturaCliente(expediente, cliente, true, "Lectura IA en cola.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtraccionGaJobResponse> listarJobsActivos(Usuario admin) {
        return extraccionGaJobRepository.findByEstadoInOrderByFechaCreacionAsc(
                        List.of(EstadoExtraccionGaJob.PENDIENTE, EstadoExtraccionGaJob.PROCESANDO),
                        PageRequest.of(0, 100)
                )
                .stream()
                .filter(job -> expedienteService.tienePermisoExpediente(job.getExpediente(), admin))
                .map(this::mapJob)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExtraccionGaJobResponse obtenerJob(Long jobId, Usuario admin) {
        ExtraccionGaJob job = extraccionGaJobRepository.findById(jobId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Trabajo de extraccion no encontrado"));
        if (!expedienteService.tienePermisoExpediente(job.getExpediente(), admin)) {
            throw new OperacionInvalidaException("No tienes permiso para consultar este trabajo");
        }
        return mapJob(job);
    }

    private ExtraccionGaJob crearJobExtraccion(
            Expediente expediente,
            Usuario usuario,
            String modelo,
            String origen,
            boolean usoCliente,
            String fase
    ) {
        ExtraccionGaJob job = new ExtraccionGaJob();
        job.setExpediente(expediente);
        job.setCreadoPor(usuario);
        job.setModelo(modelo);
        job.setEstado(EstadoExtraccionGaJob.PENDIENTE);
        job.setProgreso(0);
        job.setFaseActual(fase);
        job.setOrigen(origen);
        job.setUsoCliente(usoCliente);
        return extraccionGaJobRepository.save(job);
    }

    private LecturaIaClienteResponse construirEstadoLecturaCliente(
            Expediente expediente,
            Usuario cliente,
            boolean jobCreado,
        String mensajePreferente
    ) {
        long usosCliente = extraccionGaJobRepository.countUsosClienteByExpedienteId(expediente.getId());
        int usosConsumidos = toIntSaturado(usosCliente);
        Optional<ExtraccionGaJob> ultimo = extraccionGaJobRepository.findTopByExpedienteIdOrderByFechaCreacionDesc(expediente.getId());
        boolean jobActivo = ultimo.filter(job -> esJobActivo(job.getEstado())).isPresent();
        DocumentacionExtraccion documentacion = validarDocumentacionExtraccion(expediente, cliente);
        boolean hayDocumentos = hayDocumentosSeleccionables(expediente);
        boolean documentacionSuficiente = !documentacion.bloqueada() && hayDocumentos;
        boolean apiKeyConfigurada = openAiProperties.hasApiKey();
        boolean puedeSolicitar = apiKeyConfigurada && documentacionSuficiente && !jobActivo;
        String mensaje = mensajePreferente != null
                ? mensajePreferente
                : mensajeLecturaCliente(apiKeyConfigurada, documentacionSuficiente, hayDocumentos, jobActivo);
        return new LecturaIaClienteResponse(
                expediente.getId(),
                apiKeyConfigurada,
                documentacionSuficiente,
                puedeSolicitar,
                jobCreado,
                documentacion.bloqueosDocumentales(),
                usosConsumidos,
                USOS_CLIENTE_SIN_LIMITE,
                USOS_RESTANTES_SIN_LIMITE,
                mensaje,
                ultimo.map(this::mapJob).orElse(null)
        );
    }

    private String mensajeLecturaCliente(
            boolean apiKeyConfigurada,
            boolean documentacionSuficiente,
            boolean hayDocumentos,
            boolean jobActivo
    ) {
        if (jobActivo) {
            return "Lectura IA en curso.";
        }
        if (!apiKeyConfigurada) {
            return "La lectura IA no esta disponible en este momento.";
        }
        if (!hayDocumentos) {
            return "No hay documentos relevantes para iniciar la lectura IA.";
        }
        if (!documentacionSuficiente) {
            return "Falta documentacion minima para iniciar la lectura IA.";
        }
        return "Lectura IA disponible.";
    }

    private int toIntSaturado(long valor) {
        return valor > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.toIntExact(Math.max(0, valor));
    }

    private boolean hayDocumentosSeleccionables(Expediente expediente) {
        try {
            return !seleccionarDocumentos(expediente).isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    @Transactional
    public ExtraccionGaSincronizacionResponse sincronizarRevision(Long expedienteId, Usuario admin) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, admin);
        ExtraccionGaRevision revision = extraccionGaRevisionRepository.findByExpedienteId(expediente.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay revision GA guardada para sincronizar"));
        JsonNode datos = leerJson(revision.getDatosValidadosJson(), "datos validados");
        JsonNode consolidado = nodoConsolidado(datos);

        ContadorSincronizacion contador = new ContadorSincronizacion();
        sincronizarInteresado(expediente, consolidado.path("transmitente"), RolInteresado.VENDEDOR, contador);
        sincronizarInteresado(expediente, consolidado.path("adquirente"), RolInteresado.COMPRADOR, contador);
        sincronizarVehiculo(expediente, consolidado.path("vehiculo"), datos.path("formatoGaNormalizado").path("DATOS_VEHICULO"), contador);

        return new ExtraccionGaSincronizacionResponse(
                contador.interesadosCreados,
                contador.interesadosActualizados,
                contador.relacionesCreadas,
                contador.vehiculosCreados,
                contador.vehiculosActualizados,
                "Datos sincronizados con el expediente"
        );
    }

    @Override
    @Transactional
    public byte[] exportarPreparadas(List<Long> expedienteIds, Usuario admin) {
        if (expedienteIds == null || expedienteIds.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona al menos un expediente preparado");
        }
        List<ExtraccionGaRevision> revisiones = extraccionGaRevisionRepository
                .findByExpedienteIdInAndEstado(expedienteIds, EstadoRevisionGa.PREPARADO_EXPORTACION);
        if (revisiones.size() != expedienteIds.stream().filter(Objects::nonNull).distinct().count()) {
            throw new OperacionInvalidaException("Hay expedientes sin revision preparada para exportacion");
        }
        Map<Long, Integer> orden = new LinkedHashMap<>();
        for (int i = 0; i < expedienteIds.size(); i++) {
            orden.putIfAbsent(expedienteIds.get(i), i);
        }
        revisiones.sort(Comparator.comparingInt(revision -> orden.getOrDefault(revision.getExpediente().getId(), Integer.MAX_VALUE)));
        for (ExtraccionGaRevision revision : revisiones) {
            if (!expedienteService.tienePermisoExpediente(revision.getExpediente(), admin)) {
                throw new OperacionInvalidaException("No tienes permiso para exportar uno de los expedientes");
            }
        }
        String xml = construirXmlLote(revisiones);
        LocalDateTime ahora = LocalDateTime.now();
        for (ExtraccionGaRevision revision : revisiones) {
            revision.setEstado(EstadoRevisionGa.EXPORTADO);
            revision.setFechaExportado(ahora);
            revision.setExportadoPor(admin);
        }
        extraccionGaRevisionRepository.saveAll(revisiones);
        return xml.getBytes(XML_GA_CHARSET);
    }

    private void programarJob(Long jobId) {
        extraccionGaExecutor.submit(() -> procesarJob(jobId));
    }

    private void procesarJob(Long jobId) {
        try {
            JobContexto contexto = prepararJob(jobId);
            Usuario admin = usuarioRepository.findById(contexto.adminId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario creador del trabajo no encontrado"));

            actualizarJob(jobId, 15, "Seleccionando documentacion");
            ExtraccionGaResponse resultado = probarMultiple(
                    contexto.expedienteId(),
                    new ExtraccionGaRequest(contexto.modelo(), true),
                    admin
            );
            if (!resultado.ejecutado() || resultado.resultadoJson() == null || resultado.resultadoJson().isBlank()) {
                throw new OperacionInvalidaException(resultado.aviso() != null
                        ? resultado.aviso()
                        : "La extraccion no devolvio datos");
            }

            actualizarJob(jobId, 85, "Guardando revision");
            ExtraccionGaRevisionResponse revision = new TransactionTemplate(transactionManager).execute(status -> guardarRevision(
                    contexto.expedienteId(),
                    new ExtraccionGaRevisionRequest(
                            resultado.resultadoJson(),
                            resultado.resultadoJson(),
                            contexto.modelo(),
                            EstadoRevisionGa.BORRADOR
                    ),
                    admin
            ));
            finalizarJob(jobId, revision != null ? revision.id() : null);
        } catch (Exception exception) {
            marcarJobError(jobId, exception);
        }
    }

    private JobContexto prepararJob(Long jobId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ExtraccionGaJob job = extraccionGaJobRepository.findById(jobId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Trabajo de extraccion no encontrado"));
            if (job.getCreadoPor() == null || job.getCreadoPor().getId() == null) {
                throw new OperacionInvalidaException("El trabajo no tiene usuario creador");
            }
            job.setEstado(EstadoExtraccionGaJob.PROCESANDO);
            job.setProgreso(5);
            job.setFaseActual("Preparando extraccion");
            job.setMensajeError(null);
            job.setFechaInicio(LocalDateTime.now());
            job.setIntentos((job.getIntentos() == null ? 0 : job.getIntentos()) + 1);
            return new JobContexto(job.getExpediente().getId(), job.getModelo(), job.getCreadoPor().getId());
        });
    }

    private void actualizarJob(Long jobId, int progreso, String fase) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ExtraccionGaJob job = extraccionGaJobRepository.findById(jobId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Trabajo de extraccion no encontrado"));
            job.setProgreso(Math.max(job.getProgreso() == null ? 0 : job.getProgreso(), progreso));
            job.setFaseActual(fase);
        });
    }

    private void finalizarJob(Long jobId, Long revisionId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ExtraccionGaJob job = extraccionGaJobRepository.findById(jobId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Trabajo de extraccion no encontrado"));
            if (revisionId != null) {
                extraccionGaRevisionRepository.findById(revisionId).ifPresent(job::setRevision);
            }
            job.setEstado(EstadoExtraccionGaJob.COMPLETADO);
            job.setProgreso(100);
            job.setFaseActual("Revision creada");
            job.setMensajeError(null);
            job.setFechaFin(LocalDateTime.now());
        });
    }

    private void marcarJobError(Long jobId, Exception exception) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ExtraccionGaJob job = extraccionGaJobRepository.findById(jobId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Trabajo de extraccion no encontrado"));
            job.setEstado(EstadoExtraccionGaJob.ERROR);
            job.setFaseActual("Error");
            job.setMensajeError(limitar(primerNoVacio(exception.getMessage(), exception.getClass().getSimpleName()), 1000));
            job.setFechaFin(LocalDateTime.now());
        });
    }

    private Set<TipoDocumento> tiposCandidatosExtraccionGa() {
        Set<TipoDocumento> tipos = EnumSet.copyOf(TIPOS_RELEVANTES);
        tipos.add(TipoDocumento.EXPEDIENTE_COMPLETO);
        return tipos;
    }

    private boolean esJobActivo(EstadoExtraccionGaJob estado) {
        return estado == EstadoExtraccionGaJob.PENDIENTE || estado == EstadoExtraccionGaJob.PROCESANDO;
    }

    private Expediente obtenerExpedienteConPermiso(Long expedienteId, Usuario admin) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, admin)) {
            throw new OperacionInvalidaException("No tienes permiso para acceder al expediente");
        }
        return expediente;
    }

    private ExtraccionGaQueueItemResponse mapQueueItem(Expediente expediente, ExtraccionGaRevision revision, ExtraccionGaJob job) {
        RevisionVista revisionVista = revision != null ? normalizarRevisionVista(revision) : null;
        return new ExtraccionGaQueueItemResponse(
                expediente.getId(),
                expediente.getMatricula(),
                expediente.getCliente() != null ? expediente.getCliente().getNombre() : null,
                nombreTipoTramite(expediente),
                revision != null ? revision.getId() : null,
                revision != null ? revision.getEstado() : null,
                revisionVista != null ? revisionVista.confianzaGlobal() : null,
                revisionVista != null && revisionVista.requiereRevisionHumana(),
                revision != null ? primerNoNulo(revision.getFechaUltimaModificacion(), revision.getFechaCreacion()) : null,
                job != null ? job.getId() : null,
                job != null ? job.getEstado() : null,
                job != null ? job.getProgreso() : null,
                job != null ? job.getFaseActual() : null,
                job != null ? job.getMensajeError() : null,
                job != null ? job.getFechaCreacion() : null
        );
    }

    private ExtraccionGaJobResponse mapJob(ExtraccionGaJob job) {
        Expediente expediente = job.getExpediente();
        ExtraccionGaRevision revision = job.getRevision();
        RevisionVista revisionVista = revision != null ? normalizarRevisionVista(revision) : null;
        return new ExtraccionGaJobResponse(
                job.getId(),
                expediente.getId(),
                expediente.getMatricula(),
                expediente.getCliente() != null ? expediente.getCliente().getNombre() : null,
                nombreTipoTramite(expediente),
                job.getEstado(),
                job.getModelo(),
                job.getProgreso(),
                job.getFaseActual(),
                job.getMensajeError(),
                job.getIntentos(),
                revision != null ? revision.getId() : null,
                revision != null ? revision.getEstado() : null,
                revisionVista != null ? revisionVista.confianzaGlobal() : null,
                revisionVista != null ? revisionVista.requiereRevisionHumana() : null,
                job.getFechaCreacion(),
                job.getFechaInicio(),
                job.getFechaFin()
        );
    }

    private ExtraccionGaRevisionResponse mapRevision(ExtraccionGaRevision revision) {
        Expediente expediente = revision.getExpediente();
        RevisionVista revisionVista = normalizarRevisionVista(revision);
        String revisadoPor = revision.getRevisadoPor() != null
                ? nombreCompleto(revision.getRevisadoPor().getNombre(), revision.getRevisadoPor().getApellidos(), "")
                : null;
        return new ExtraccionGaRevisionResponse(
                revision.getId(),
                expediente.getId(),
                expediente.getMatricula(),
                expediente.getCliente() != null ? expediente.getCliente().getNombre() : null,
                nombreTipoTramite(expediente),
                revision.getModelo(),
                revision.getEstado(),
                revisionVista.confianzaGlobal(),
                revisionVista.requiereRevisionHumana(),
                revision.getResultadoIaJson(),
                revisionVista.datosValidadosJson(),
                revision.getFechaCreacion(),
                revision.getFechaUltimaModificacion(),
                revision.getFechaPreparado(),
                revision.getFechaExportado(),
                revisadoPor
        );
    }

    private RevisionVista normalizarRevisionVista(ExtraccionGaRevision revision) {
        String datosJson = revision.getDatosValidadosJson();
        Double confianza = revision.getConfianzaGlobal();
        boolean requiereRevision = revision.isRequiereRevisionHumana();
        if (datosJson == null || datosJson.isBlank()) {
            return new RevisionVista(datosJson, confianza, requiereRevision);
        }
        try {
            JsonNode datos = objectMapper.readTree(datosJson);
            if (datos instanceof ObjectNode datosObject) {
                JsonNode consolidado = nodoConsolidado(datosObject);
                if (consolidado instanceof ObjectNode consolidadoObject) {
                    normalizarDireccionesConsolidadas(consolidadoObject);
                    normalizarFechasConsolidadas(consolidadoObject);
                }
                datosObject.set("formatoGaNormalizado", construirFormatoGaNormalizado(consolidado));
                aplicarRevisionDeterminista(datosObject, consolidado);
                datosJson = objectMapper.writeValueAsString(datosObject);
                confianza = extraerConfianzaGlobal(datosObject);
                requiereRevision = extraerRequiereRevision(datosObject);
            }
        } catch (Exception ignored) {
            return new RevisionVista(revision.getDatosValidadosJson(), revision.getConfianzaGlobal(), revision.isRequiereRevisionHumana());
        }
        return new RevisionVista(datosJson, confianza, requiereRevision);
    }

    private String nombreTipoTramite(Expediente expediente) {
        return expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                ? expediente.getTipoTramite().getNombre().name()
                : null;
    }

    private LocalDateTime primerNoNulo(LocalDateTime primero, LocalDateTime segundo) {
        return primero != null ? primero : segundo;
    }

    private JsonNode leerJson(String json, String etiqueta) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new OperacionInvalidaException("El JSON de " + etiqueta + " no es valido");
        }
    }

    private Double extraerConfianzaGlobal(JsonNode datos) {
        JsonNode control = nodoControl(datos);
        return control.path("confianzaGlobal").isNumber() ? control.path("confianzaGlobal").asDouble() : null;
    }

    private boolean extraerRequiereRevision(JsonNode datos) {
        JsonNode control = nodoControl(datos);
        return control.path("requiereRevisionHumana").asBoolean(false);
    }

    private JsonNode nodoControl(JsonNode datos) {
        JsonNode direct = datos.path("control");
        if (!direct.isMissingNode() && direct.isObject()) {
            return direct;
        }
        JsonNode consolidado = datos.path("bloqueConsolidacion").path("resultado").path("control");
        return consolidado.isMissingNode() ? objectMapper.createObjectNode() : consolidado;
    }

    private JsonNode nodoConsolidado(JsonNode datos) {
        JsonNode consolidado = datos.path("bloqueConsolidacion").path("resultado");
        return consolidado.isMissingNode() || !consolidado.isObject() ? datos : consolidado;
    }

    private void aplicarRevisionDeterminista(ObjectNode datos, JsonNode consolidado) {
        ObjectNode control = controlEditable(datos, consolidado);
        JsonNode camposIa = control.path("camposDudosos");
        if (camposIa.isArray() && !control.has("camposDudososIa")) {
            control.set("camposDudososIa", camposIa.deepCopy());
        }
        ArrayNode camposDudosos = objectMapper.createArrayNode();
        control.set("camposDudosos", camposDudosos);
        ArrayNode avisos = objectMapper.createArrayNode();
        boolean requiereRevision = false;

        requiereRevision |= requerirPersona(consolidado, "transmitente", "Transmitente", false, camposDudosos, avisos);
        requiereRevision |= requerirPersona(consolidado, "adquirente", "Adquirente", true, camposDudosos, avisos);
        requiereRevision |= requerirCampo(consolidado, "vehiculo", "matricula", "Vehiculo: matricula", camposDudosos, avisos);
        requiereRevision |= requerirCampo(consolidado, "vehiculo", "numeroBastidor", "Vehiculo: bastidor", camposDudosos, avisos);
        requiereRevision |= requerirCampo(consolidado, "vehiculo", "marca", "Vehiculo: marca", camposDudosos, avisos);
        requiereRevision |= requerirCampo(consolidado, "vehiculo", "modelo", "Vehiculo: modelo", camposDudosos, avisos);
        if (!tieneAlguno(consolidado.path("vehiculo"), "fechaPrimeraMatriculacion", "fechaMatriculacion")) {
            marcarCampoDudoso(camposDudosos, "Vehiculo: fecha de matriculacion");
            avisos.add("Falta fecha de matriculacion del vehiculo.");
            requiereRevision = true;
        }
        if (!tieneAlguno(consolidado.path("acreditacion"), "fechaContrato", "fechaFactura")
                && !tieneAlguno(consolidado.path("impuestos"), "fechaDevengo")) {
            marcarCampoDudoso(camposDudosos, "Operacion: fecha de contrato/devengo");
            avisos.add("Falta fecha de contrato, factura o devengo.");
            requiereRevision = true;
        }

        control.set("avisosDeterministas", avisos);
        control.put("requiereRevisionHumana", requiereRevision);
        control.put("criterioRevision", "Solo campos clave de interesados, vehiculo y fechas necesarias para XML.");
    }

    private boolean requerirPersona(
            JsonNode consolidado,
            String path,
            String etiqueta,
            boolean fechaNacimientoObligatoria,
            ArrayNode camposDudosos,
            ArrayNode avisos
    ) {
        JsonNode persona = consolidado.path(path);
        boolean requiereRevision = false;
        String dni = normalizarDni(valor(persona, "dni"));
        requiereRevision |= requerirCampo(consolidado, path, "dni", etiqueta + ": DNI/NIF", camposDudosos, avisos);
        if (esDocumentoEmpresa(dni)) {
            if (!tieneAlguno(persona, "razonSocial", "nombreCompleto", "nombre")) {
                marcarCampoDudoso(camposDudosos, etiqueta + ": razon social");
                avisos.add("Falta razon social de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
                requiereRevision = true;
            }
        } else {
            if (!tieneAlguno(persona, "nombre", "nombreCompleto")) {
                marcarCampoDudoso(camposDudosos, etiqueta + ": nombre");
                avisos.add("Falta nombre de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
                requiereRevision = true;
            }
            if (valor(persona, "apellido1").isBlank() && valor(persona, "nombreCompleto").isBlank()) {
                marcarCampoDudoso(camposDudosos, etiqueta + ": primer apellido");
                avisos.add("Falta primer apellido de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
                requiereRevision = true;
            }
            if (fechaNacimientoObligatoria && valor(persona, "fechaNacimiento").isBlank()) {
                marcarCampoDudoso(camposDudosos, etiqueta + ": fecha de nacimiento");
                avisos.add("Falta fecha de nacimiento de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
                requiereRevision = true;
            }
        }

        JsonNode direccion = persona.path("direccion");
        if (valor(direccion, "nombreVia").isBlank()) {
            marcarCampoDudoso(camposDudosos, etiqueta + ": domicilio");
            avisos.add("Falta domicilio de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
            requiereRevision = true;
        }
        if (normalizarCodigoPostal(valor(direccion, "codigoPostal")).isBlank()) {
            marcarCampoDudoso(camposDudosos, etiqueta + ": codigo postal");
            avisos.add("Falta codigo postal de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
            requiereRevision = true;
        }
        if (valor(direccion, "municipio").isBlank()) {
            marcarCampoDudoso(camposDudosos, etiqueta + ": municipio");
            avisos.add("Falta municipio de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
            requiereRevision = true;
        }
        if (valor(direccion, "provincia").isBlank()) {
            marcarCampoDudoso(camposDudosos, etiqueta + ": provincia");
            avisos.add("Falta provincia de " + etiqueta.toLowerCase(Locale.ROOT) + ".");
            requiereRevision = true;
        }
        return requiereRevision;
    }

    private boolean requerirCampo(
            JsonNode consolidado,
            String grupo,
            String campo,
            String etiqueta,
            ArrayNode camposDudosos,
            ArrayNode avisos
    ) {
        if (!valor(consolidado, grupo, campo).isBlank()) {
            return false;
        }
        marcarCampoDudoso(camposDudosos, etiqueta);
        avisos.add("Falta " + etiqueta.toLowerCase(Locale.ROOT) + ".");
        return true;
    }

    private ObjectNode controlEditable(ObjectNode datos, JsonNode consolidado) {
        if (consolidado instanceof ObjectNode consolidadoObject) {
            JsonNode control = consolidadoObject.path("control");
            if (control instanceof ObjectNode controlObject) {
                return controlObject;
            }
            ObjectNode controlObject = objectMapper.createObjectNode();
            consolidadoObject.set("control", controlObject);
            return controlObject;
        }
        JsonNode control = datos.path("control");
        if (control instanceof ObjectNode controlObject) {
            return controlObject;
        }
        ObjectNode controlObject = objectMapper.createObjectNode();
        datos.set("control", controlObject);
        return controlObject;
    }

    private ArrayNode camposDudosos(ObjectNode control) {
        JsonNode current = control.path("camposDudosos");
        if (current instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode arrayNode = objectMapper.createArrayNode();
        control.set("camposDudosos", arrayNode);
        return arrayNode;
    }

    private void marcarCampoDudoso(ArrayNode camposDudosos, String campo) {
        for (JsonNode item : camposDudosos) {
            if (campo.equalsIgnoreCase(item.asText(""))) {
                return;
            }
        }
        camposDudosos.add(campo);
    }

    private boolean tieneAlguno(JsonNode node, String... fields) {
        for (String field : fields) {
            if (!valor(node, field).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String limitar(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private void sincronizarInteresado(Expediente expediente, JsonNode persona, RolInteresado rol, ContadorSincronizacion contador) {
        String dni = normalizarDni(valor(persona, "dni"));
        String nombre = nombreCompleto(valor(persona, "apellido1"), valor(persona, "apellido2"), valor(persona, "nombre"));
        if (dni.isBlank() || nombre.isBlank()) {
            return;
        }
        Interesado interesado = interesadoRepository.findByDni(dni).orElse(null);
        boolean creado = false;
        boolean actualizado = false;
        if (interesado == null) {
            interesado = new Interesado();
            interesado.setDni(dni);
            interesado.setNombre(normalizarTexto(nombre));
            creado = true;
        } else {
            actualizado |= setIfBlank(interesado.getNombre(), normalizarTexto(nombre), interesado::setNombre);
        }

        JsonNode direccion = persona.path("direccion");
        actualizado |= setIfBlank(interesado.getTelefono(), normalizarTexto(valor(persona, "telefono")), interesado::setTelefono);
        actualizado |= setIfBlank(interesado.getTipoVia(), normalizarTexto(valor(direccion, "siglas")), interesado::setTipoVia);
        actualizado |= setIfBlank(interesado.getNombreVia(), normalizarTexto(valor(direccion, "nombreVia")), interesado::setNombreVia);
        actualizado |= setIfBlank(interesado.getNumeroVia(), normalizarTexto(valor(direccion, "numero")), interesado::setNumeroVia);
        actualizado |= setIfBlank(interesado.getBloque(), normalizarTexto(valor(direccion, "bloque")), interesado::setBloque);
        actualizado |= setIfBlank(interesado.getPortal(), normalizarTexto(valor(direccion, "portal")), interesado::setPortal);
        actualizado |= setIfBlank(interesado.getEscalera(), normalizarTexto(valor(direccion, "escalera")), interesado::setEscalera);
        actualizado |= setIfBlank(interesado.getPiso(), normalizarTexto(valor(direccion, "piso")), interesado::setPiso);
        actualizado |= setIfBlank(interesado.getPuerta(), normalizarTexto(valor(direccion, "puerta")), interesado::setPuerta);
        actualizado |= setIfBlank(interesado.getCodigoPostal(), normalizarCodigoPostal(valor(direccion, "codigoPostal")), interesado::setCodigoPostal);
        actualizado |= setIfBlank(interesado.getMunicipio(), normalizarTexto(valor(direccion, "municipio")), interesado::setMunicipio);
        actualizado |= setIfBlank(interesado.getProvincia(), normalizarProvincia(valor(direccion, "provincia"), valor(direccion, "codigoPostal")), interesado::setProvincia);
        actualizado |= setIfBlank(interesado.getDireccion(), direccionInteresado(direccion), interesado::setDireccion);
        interesado.setTipoPersona(inferirTipoPersona(dni, valor(persona, "tipoPersona")));

        if (creado || actualizado) {
            interesadoRepository.save(interesado);
            if (creado) {
                contador.interesadosCreados++;
            } else {
                contador.interesadosActualizados++;
            }
        }

        if (expedienteInteresadoRepository.findByExpedienteIdAndInteresadoId(expediente.getId(), interesado.getId()).isEmpty()) {
            expedienteInteresadoRepository.save(new ExpedienteInteresado(expediente, interesado, rol));
            contador.relacionesCreadas++;
        }
    }

    private void sincronizarVehiculo(Expediente expediente, JsonNode vehiculoIa, JsonNode vehiculoNormalizado, ContadorSincronizacion contador) {
        String matricula = normalizarMatricula(primerNoVacio(valor(vehiculoIa, "matricula"), valor(vehiculoNormalizado, "MATRICULA"), expediente.getMatricula()));
        if (matricula.isBlank()) {
            return;
        }
        Vehiculo vehiculo = vehiculoRepository.findByMatricula(matricula).orElse(null);
        boolean creado = false;
        boolean actualizado = false;
        if (vehiculo == null) {
            vehiculo = new Vehiculo();
            vehiculo.setMatricula(matricula);
            creado = true;
        }
        actualizado |= setIfBlank(vehiculo.getBastidor(), normalizarTexto(primerNoVacio(valor(vehiculoIa, "numeroBastidor"), valor(vehiculoNormalizado, "NUMERO_BASTIDOR"))), vehiculo::setBastidor);
        actualizado |= setIfBlank(vehiculo.getMarca(), normalizarTexto(primerNoVacio(valor(vehiculoIa, "marca"), valor(vehiculoNormalizado, "MARCA"))), vehiculo::setMarca);
        actualizado |= setIfBlank(vehiculo.getModelo(), normalizarTexto(primerNoVacio(valor(vehiculoIa, "modelo"), valor(vehiculoNormalizado, "MODELO"))), vehiculo::setModelo);
        LocalDate fecha = parseFechaLocal(primerNoVacio(valor(vehiculoIa, "fechaPrimeraMatriculacion"), valor(vehiculoIa, "fechaMatriculacion"), valor(vehiculoNormalizado, "FECHA_PRIMERA_MATRICULACION")));
        if (vehiculo.getFechaPrimeraMatriculacion() == null && fecha != null) {
            vehiculo.setFechaPrimeraMatriculacion(fecha);
            actualizado = true;
        }
        if (creado || actualizado) {
            vehiculoRepository.save(vehiculo);
            if (creado) {
                contador.vehiculosCreados++;
            } else {
                contador.vehiculosActualizados++;
            }
        }
        if (expediente.getVehiculo() == null) {
            expediente.setVehiculo(vehiculo);
            expediente.setFechaUltimaModificacion(LocalDateTime.now());
            expedienteRepository.save(expediente);
        }
    }

    private boolean setIfBlank(String current, String next, java.util.function.Consumer<String> setter) {
        if ((current == null || current.isBlank()) && next != null && !next.isBlank()) {
            setter.accept(next);
            return true;
        }
        return false;
    }

    private String direccionInteresado(JsonNode direccion) {
        String via = String.join(" ", java.util.stream.Stream.of(
                        normalizarTexto(valor(direccion, "siglas")),
                        normalizarTexto(valor(direccion, "nombreVia")),
                        normalizarTexto(valor(direccion, "numero")),
                        direccionConEtiqueta("BLOQ", valor(direccion, "bloque")),
                        direccionConEtiqueta("PORTAL", valor(direccion, "portal")),
                        direccionConEtiqueta("ESC", valor(direccion, "escalera")),
                        direccionConEtiqueta("PISO", valor(direccion, "piso")),
                        direccionConEtiqueta("PTA", valor(direccion, "puerta"))
                )
                .filter(value -> value != null && !value.isBlank())
                .toList());
        return String.join(", ", java.util.stream.Stream.of(
                        via,
                        normalizarCodigoPostal(valor(direccion, "codigoPostal")),
                        normalizarTexto(valor(direccion, "municipio")),
                        normalizarProvincia(valor(direccion, "provincia"), valor(direccion, "codigoPostal")))
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private String direccionConEtiqueta(String etiqueta, String valor) {
        String normalizado = normalizarTexto(valor);
        return normalizado != null && !normalizado.isBlank() ? etiqueta + " " + normalizado : null;
    }

    private TipoPersona inferirTipoPersona(String dni, String tipoPersona) {
        String tipo = normalizarTexto(tipoPersona);
        if (tipo.contains("JURIDICA") || tipo.contains("EMPRESA") || dni.matches("^[ABCDEFGHJKLMNPQRSUVW].*")) {
            return TipoPersona.EMPRESA;
        }
        return TipoPersona.PARTICULAR;
    }

    private LocalDate parseFechaLocal(String value) {
        String normalizada = normalizarFecha(value);
        if (normalizada.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(normalizada, FORMATO_FECHA_GA);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String construirXmlLote(List<ExtraccionGaRevision> revisiones) {
        String fecha = LocalDate.now().format(FORMATO_FECHA_GA);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.0' encoding='iso-8859-1'?>\n");
        xml.append("<FORMATO_GA FechaCreacion=\"").append(fecha).append("\" NIFGestoria=\"78852398A\">\n");
        for (ExtraccionGaRevision revision : revisiones) {
            appendTransmision(xml, revision, fecha);
        }
        xml.append("</FORMATO_GA>\n");
        return xml.toString();
    }

    private void appendTransmision(StringBuilder xml, ExtraccionGaRevision revision, String fecha) {
        JsonNode datos = leerJson(revision.getDatosValidadosJson(), "datos validados");
        JsonNode consolidado = nodoConsolidado(datos);
        JsonNode normalizado = datos.path("formatoGaNormalizado");
        String matricula = normalizarMatricula(primerNoVacio(valor(consolidado, "vehiculo", "matricula"), revision.getExpediente().getMatricula()));
        String numeroDocumento = "IA" + LocalDateTime.now().format(FORMATO_DOCUMENTO_GA) + matricula + revision.getExpediente().getId() + "XXXXX";

        xml.append("  <TRANSMISION ProcesarTransmision=\"1\" Procesar620=\"1\" Version=\"1.0\">\n");
        appendTag(xml, 2, "TIPO_TRANSFERENCIA", primerNoVacio(valor(consolidado, "transmision", "tipoTransferencia"), "1"));
        appendTag(xml, 2, "NOTIFICACION_PREVIA", "No");
        appendTag(xml, 2, "NUMERO_EXPEDIENTE", "");
        appendTag(xml, 2, "NUMERO_DOCUMENTO", numeroDocumento);
        appendTag(xml, 2, "NUMERO_PROFESIONAL", "00387");
        appendTag(xml, 2, "FECHA_CREACION", fecha);
        appendTag(xml, 2, "FECHA_PRESENTACION", fecha);
        appendTag(xml, 2, "JEFATURA", "TF");
        appendTag(xml, 2, "TIPO_TASA", primerNoVacio(valor(consolidado, "transmision", "tipoTasa"), "4"));
        appendTag(xml, 2, "TASA", "");
        appendTag(xml, 2, "OBSERVACIONES", "");
        appendTag(xml, 2, "IMPRESION_PERMISO_CIRCULACION", "No");
        appendTag(xml, 2, "SUBTIPO_RELE", "STCCM");
        appendTag(xml, 2, "SEPARACION_DIVORCIO", "");
        appendBlock(xml, 2, "DATOS_TRANSMITENTE", normalizado.path("DATOS_TRANSMITENTE"));
        appendBlock(xml, 2, "DATOS_REPRESENTANTE_TRANSMITENTE", normalizado.path("DATOS_REPRESENTANTE_TRANSMITENTE"));
        appendBlock(xml, 2, "DATOS_ADQUIRENTE", normalizado.path("DATOS_ADQUIRENTE"));
        appendPresentador(xml);
        appendBlock(xml, 2, "DATOS_VEHICULO", normalizado.path("DATOS_VEHICULO"));
        appendBlock(xml, 2, "DATOS_IMPUESTOS", normalizado.path("DATOS_IMPUESTOS"));
        appendBlock(xml, 2, "ACREDITACION_DERECHO", normalizado.path("ACREDITACION_DERECHO"));
        appendBlock(xml, 2, "ACREDITACION_FISCAL", normalizado.path("ACREDITACION_FISCAL"));
        xml.append("  </TRANSMISION>\n");
    }

    private void appendPresentador(StringBuilder xml) {
        xml.append("    <DATOS_PRESENTADOR>\n");
        appendTag(xml, 3, "DNI_PRESENTADOR", "78852398A");
        appendTag(xml, 3, "RAZON_SOCIAL_PRESENTADOR", "");
        appendTag(xml, 3, "APELLIDO1_PRESENTADOR", "NEGRIN");
        appendTag(xml, 3, "APELLIDO2_PRESENTADOR", "");
        appendTag(xml, 3, "NOMBRE_PRESENTADOR", "SAMUEL");
        appendTag(xml, 3, "SEXO_PRESENTADOR", "");
        appendTag(xml, 3, "FECHA_NACIMIENTO_PRESENTADOR", "");
        appendTag(xml, 3, "AUTONOMO_PRESENTADOR", "");
        appendTag(xml, 3, "IAE_PRESENTADOR", "");
        appendTag(xml, 3, "CODIGO_IAE_PRESENTADOR", "");
        appendTag(xml, 3, "ANAGRAMA_PRESENTADOR", "");
        appendTag(xml, 3, "TELEFONO_PRESENTADOR", "");
        appendTag(xml, 3, "FAX_PRESENTADOR", "");
        appendTag(xml, 3, "DOI_SUSTITUTIVO_PRESENTADOR", "");
        appendTag(xml, 3, "FECHA_CADU_DOI_PRESENTADOR", "");
        appendTag(xml, 3, "EXENTO_CADU_DOI_PRESENTADOR", "Si");
        appendTag(xml, 3, "ESTADO_CIVIL_PRESENTADOR", "");
        appendTag(xml, 3, "NACIONALIDAD_PRESENTADOR", "");
        appendTag(xml, 3, "COTITULARES_PRESENTADOR", "");
        xml.append("      <DIRECCION_PRESENTADOR>\n");
        appendTag(xml, 4, "SIGLAS_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "NOMBRE_VIA_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "NUMERO_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "KM_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "HECTOMETRO_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "LETRA_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "ESCALERA_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "PISO_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "PUERTA_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "BLOQUE_DIRECCION_PRESENTADOR", "");
        appendTag(xml, 4, "MUNICIPIO_PRESENTADOR", "");
        appendTag(xml, 4, "PUEBLO_PRESENTADOR", "");
        appendTag(xml, 4, "PROVINCIA_PRESENTADOR", "");
        appendTag(xml, 4, "CP_PRESENTADOR", "");
        appendTag(xml, 4, "PAIS_PRESENTADOR", "ESP");
        xml.append("      </DIRECCION_PRESENTADOR>\n");
        appendTag(xml, 3, "MANDATARIO_PRESENTADOR", "No");
        appendTag(xml, 3, "INTERESADO_PRESENTADOR", "No");
        xml.append("    </DATOS_PRESENTADOR>\n");
    }

    private void appendBlock(StringBuilder xml, int level, String name, JsonNode block) {
        indent(xml, level).append("<").append(name).append(">\n");
        if (block != null && block.isObject()) {
            block.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isObject()) {
                    appendBlock(xml, level + 1, entry.getKey(), value);
                } else {
                    appendTag(xml, level + 1, entry.getKey(), value.asText(""));
                }
            });
        }
        indent(xml, level).append("</").append(name).append(">\n");
    }

    private void appendTag(StringBuilder xml, int level, String name, String value) {
        String safe = value == null ? "" : value;
        indent(xml, level).append("<").append(name).append(">");
        if (!safe.isBlank()) {
            xml.append(escapeXml(safe));
        }
        xml.append("</").append(name).append(">\n");
    }

    private StringBuilder indent(StringBuilder xml, int level) {
        return xml.append("  ".repeat(Math.max(0, level)));
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static class ContadorSincronizacion {
        private int interesadosCreados;
        private int interesadosActualizados;
        private int relacionesCreadas;
        private int vehiculosCreados;
        private int vehiculosActualizados;
    }

    private List<DocumentoSeleccionado> seleccionarDocumentos(Expediente expediente) {
        Path base = carpetaUploads();
        List<DocumentoSeleccionado> seleccionados = new ArrayList<>();
        Set<Long> idsSeleccionados = new HashSet<>();
        List<Documento> documentosExpediente = documentoRepository.findByExpedienteId(expediente.getId());
        for (Documento documento : documentosExpediente) {
            agregarDocumentoSeleccionado(base, seleccionados, idsSeleccionados, documento, TIPOS_RELEVANTES);
        }
        if (!tieneDocumentoIdentidadPropio(documentosExpediente)) {
            documentosExpediente.stream()
                    .filter(documento -> documento.getTipoDocumento() == TipoDocumento.EXPEDIENTE_COMPLETO)
                    .min(Comparator.comparing(Documento::getFechaSubida))
                    .ifPresent(documento -> agregarDocumentoSeleccionado(base, seleccionados, idsSeleccionados, documento, Set.of(TipoDocumento.EXPEDIENTE_COMPLETO)));
        }
        if (expediente.getCliente() != null) {
            Set<TipoDocumento> tiposClienteIncluidos = EnumSet.noneOf(TipoDocumento.class);
            for (Documento documento : documentoRepository.findByClienteIdOrderByFechaSubidaDesc(expediente.getCliente().getId())) {
                TipoDocumento tipo = documento.getTipoDocumento();
                if (tipo == null || tiposClienteIncluidos.contains(tipo)) {
                    continue;
                }
                if (agregarDocumentoSeleccionado(base, seleccionados, idsSeleccionados, documento, TIPOS_REUTILIZABLES_CLIENTE)) {
                    tiposClienteIncluidos.add(tipo);
                }
            }
        }
        seleccionados.sort(Comparator
                .comparingInt((DocumentoSeleccionado item) -> PRIORIDAD_TIPO.getOrDefault(item.documento().getTipoDocumento(), 99))
                .thenComparing(item -> item.documento().getFechaSubida()));
        return seleccionados;
    }

    private boolean tieneDocumentoIdentidadPropio(List<Documento> documentosExpediente) {
        return documentosExpediente.stream()
                .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.DNI || documento.getTipoDocumento() == TipoDocumento.CIF);
    }

    private boolean agregarDocumentoSeleccionado(
            Path base,
            List<DocumentoSeleccionado> seleccionados,
            Set<Long> idsSeleccionados,
            Documento documento,
            Set<TipoDocumento> tiposPermitidos
    ) {
        if (documento.getId() == null || idsSeleccionados.contains(documento.getId())) {
            return false;
        }
        if (documento.getTipoDocumento() == null || !tiposPermitidos.contains(documento.getTipoDocumento())) {
            return false;
        }
        if (documento.getNombreArchivo() == null || !documento.getNombreArchivo().toLowerCase().endsWith(".pdf")) {
            return false;
        }
        Path ruta = base.resolve(documento.getNombreArchivo()).normalize();
        if (!ruta.startsWith(base) || !Files.exists(ruta)) {
            return false;
        }
        try {
            long bytes = Files.size(ruta);
            int paginas = contarPaginas(ruta);
            seleccionados.add(new DocumentoSeleccionado(documento, ruta, paginas, bytes));
            idsSeleccionados.add(documento.getId());
            return true;
        } catch (IOException exception) {
            throw new RuntimeException("No se pudo leer el documento " + documento.getId(), exception);
        }
    }

    private DocumentacionExtraccion validarDocumentacionExtraccion(Expediente expediente, Usuario admin) {
        List<Documento> documentos = documentoRepository.findByExpedienteId(expediente.getId());
        List<ExpedienteInteresado> interesados = expedienteInteresadoRepository.findByExpedienteId(expediente.getId());
        List<RequisitoDocumentalExpediente> requisitos = requisitoDocumentalService.sincronizarYListar(
                expediente,
                interesados,
                documentos,
                admin
        );
        Set<String> bloqueos = new LinkedHashSet<>();
        boolean hayDocumentoIdentidad = documentos.stream()
                .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.DNI || documento.getTipoDocumento() == TipoDocumento.CIF);
        requisitos.stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .filter(requisito -> !esRequisitoPosteriorExtraccion(requisito))
                .filter(requisito -> !esRequisitoIdentidadPotencialmenteAgrupado(requisito, hayDocumentoIdentidad))
                .map(this::descripcionBloqueoDocumental)
                .filter(descripcion -> !descripcion.isBlank())
                .forEach(bloqueos::add);
        return new DocumentacionExtraccion(List.copyOf(bloqueos));
    }

    private boolean esRequisitoIdentidadPotencialmenteAgrupado(
            RequisitoDocumentalExpediente requisito,
            boolean hayDocumentoIdentidad
    ) {
        return hayDocumentoIdentidad
                && requisito.getInteresado() != null
                && (requisito.getTipoDocumento() == TipoDocumento.DNI || requisito.getTipoDocumento() == TipoDocumento.CIF);
    }

    private boolean esRequisitoPosteriorExtraccion(RequisitoDocumentalExpediente requisito) {
        return requisito.getTipoDocumento() == TipoDocumento.MODELO_620
                || requisito.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT
                || requisito.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE;
    }

    private String descripcionBloqueoDocumental(RequisitoDocumentalExpediente requisito) {
        String descripcion = primerNoVacio(
                requisito.getDescripcion(),
                requisito.getTipoDocumento() != null ? requisito.getTipoDocumento().getLabel() : "Documento requerido"
        );
        List<String> contexto = new ArrayList<>();
        if (requisito.getRolInteresado() != null) {
            contexto.add(requisito.getRolInteresado().name().replace('_', ' '));
        }
        if (requisito.getInteresado() != null) {
            contexto.add(nombreInteresado(requisito.getInteresado()));
        }
        if (requisito.getRolRepresentado() != null) {
            contexto.add("representante " + requisito.getRolRepresentado().name().replace('_', ' '));
        }
        if (requisito.getInteresadoRepresentado() != null) {
            contexto.add("de " + nombreInteresado(requisito.getInteresadoRepresentado()));
        }
        if (requisito.getOperacion() != null && requisito.getOperacion().getTipo() != null) {
            contexto.add(requisito.getOperacion().getTipo().name().replace('_', ' '));
        }
        return contexto.isEmpty() ? descripcion : descripcion + " (" + String.join(", ", contexto) + ")";
    }

    private String nombreInteresado(Interesado interesado) {
        return primerNoVacio(interesado.getNombre(), interesado.getDni(), "interesado " + interesado.getId());
    }

    private String mensajeBloqueoDocumental(Expediente expediente, DocumentacionExtraccion documentacion) {
        String etiqueta = primerNoVacio(expediente.getMatricula(), "Expediente " + expediente.getId());
        return etiqueta + ": falta " + resumenBloqueos(documentacion.bloqueosDocumentales());
    }

    private String resumenBloqueos(List<String> bloqueos) {
        if (bloqueos == null || bloqueos.isEmpty()) {
            return "documentacion minima";
        }
        List<String> visibles = bloqueos.stream()
                .filter(Objects::nonNull)
                .filter(bloqueo -> !bloqueo.isBlank())
                .limit(5)
                .toList();
        String resumen = String.join("; ", visibles);
        if (bloqueos.size() > visibles.size()) {
            resumen += "; y " + (bloqueos.size() - visibles.size()) + " mas";
        }
        return resumen;
    }

    private ExtraccionGaPreviewResponse construirPreview(
            Expediente expediente,
            List<DocumentoSeleccionado> documentos,
            String modelo,
            DocumentacionExtraccion documentacion
    ) {
        int paginas = documentos.stream().mapToInt(DocumentoSeleccionado::paginas).sum();
        long bytes = documentos.stream().mapToLong(DocumentoSeleccionado::bytes).sum();
        CosteEstimado coste = estimarCoste(modelo, paginas);
        List<ExtraccionGaDocumentoSeleccionadoResponse> docs = documentos.stream()
                .map(item -> new ExtraccionGaDocumentoSeleccionadoResponse(
                        item.documento().getId(),
                        item.documento().getTipoDocumento(),
                        item.documento().getNombreArchivoOriginal(),
                        item.paginas(),
                        item.bytes()))
                .toList();
        return new ExtraccionGaPreviewResponse(
                expediente.getId(),
                expediente.getMatricula(),
                modelo,
                openAiProperties.hasApiKey(),
                !documentacion.bloqueada(),
                documentacion.bloqueosDocumentales(),
                docs.size(),
                paginas,
                bytes,
                coste.min(),
                coste.max(),
                docs);
    }

    private RespuestaOpenAi llamarOpenAi(String modelo, List<DocumentoSeleccionado> documentos) {
        return llamarOpenAi(modelo, documentos, promptExtraccion());
    }

    private RespuestaOpenAi llamarOpenAi(String modelo, List<DocumentoSeleccionado> documentos, String prompt) {
        return llamarOpenAi(modelo, documentos, prompt, "extraccion_formato_ga", esquemaExtraccion());
    }

    private RespuestaOpenAi llamarOpenAi(String modelo, List<DocumentoSeleccionado> documentos, String prompt, String schemaName, ObjectNode schema) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", modelo);
            payload.set("input", construirInput(documentos, prompt));
            payload.set("text", construirFormatoTexto(schemaName, schema));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiProperties.getResponsesUrl()))
                    .timeout(Duration.ofMinutes(3))
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
            return new RespuestaOpenAi(extraerTexto(root), extraerUso(root));
        } catch (IOException exception) {
            throw new RuntimeException("Error preparando la llamada a OpenAI", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Llamada a OpenAI interrumpida", exception);
        }
    }

    private RespuestaOpenAi llamarOpenAiTexto(String modelo, String prompt, String schemaName, ObjectNode schema) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", modelo);
            payload.set("input", construirInputTexto(prompt));
            payload.set("text", construirFormatoTexto(schemaName, schema));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiProperties.getResponsesUrl()))
                    .timeout(Duration.ofMinutes(3))
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
            return new RespuestaOpenAi(extraerTexto(root), extraerUso(root));
        } catch (IOException exception) {
            throw new RuntimeException("Error preparando la llamada a OpenAI", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Llamada a OpenAI interrumpida", exception);
        }
    }

    private ArrayNode construirInput(List<DocumentoSeleccionado> documentos, String prompt) throws IOException {
        ArrayNode input = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode texto = objectMapper.createObjectNode();
        texto.put("type", "input_text");
        texto.put("text", prompt);
        content.add(texto);
        for (DocumentoSeleccionado documento : documentos) {
            if (documento.documento().getTipoDocumento() == TipoDocumento.DNI) {
                agregarImagenesDniProcesadas(content, documento);
                continue;
            }
            ObjectNode file = objectMapper.createObjectNode();
            file.put("type", "input_file");
            file.put("filename", documento.documento().getNombreArchivoOriginal());
            file.put("file_data", "data:application/pdf;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(documento.ruta())));
            content.add(file);
        }
        user.set("content", content);
        input.add(user);
        return input;
    }

    private ArrayNode construirInputTexto(String prompt) {
        ArrayNode input = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode texto = objectMapper.createObjectNode();
        texto.put("type", "input_text");
        texto.put("text", prompt);
        content.add(texto);
        user.set("content", content);
        input.add(user);
        return input;
    }

    private ObjectNode ejecutarBloque(String modelo, List<DocumentoSeleccionado> documentos, String nombre, Set<TipoDocumento> tipos, String prompt, String schemaName, ObjectNode schema) {
        ObjectNode bloque = objectMapper.createObjectNode();
        List<DocumentoSeleccionado> docsBloque = documentos.stream()
                .filter(documento -> tipos.contains(documento.documento().getTipoDocumento()))
                .toList();
        bloque.put("ejecutado", !docsBloque.isEmpty());
        bloque.put("documentos", docsBloque.size());
        if (docsBloque.isEmpty()) {
            bloque.put("aviso", "No hay documentos para " + nombre + ".");
            return bloque;
        }
        RespuestaOpenAi respuesta = llamarOpenAi(modelo, docsBloque, prompt, schemaName, schema);
        try {
            bloque.set("resultado", objectMapper.readTree(respuesta.resultadoJson()));
        } catch (IOException exception) {
            bloque.put("resultadoTexto", respuesta.resultadoJson());
        }
        bloque.set("uso", objectMapper.valueToTree(respuesta.uso()));
        return bloque;
    }

    private ObjectNode ejecutarConsolidacion(String modelo, ObjectNode bloqueContrato, ObjectNode bloqueDni, ObjectNode bloqueVehiculo, ObjectNode bloqueFiscal) {
        ObjectNode bloque = objectMapper.createObjectNode();
        bloque.put("ejecutado", true);
        bloque.put("documentos", 0);
        try {
            ObjectNode datos = objectMapper.createObjectNode();
            datos.set("bloqueContrato", bloqueContrato.path("resultado"));
            datos.set("bloqueDni", bloqueDni.path("resultado"));
            datos.set("bloqueVehiculo", bloqueVehiculo.path("resultado"));
            datos.set("bloqueFiscal", bloqueFiscal.path("resultado"));
            RespuestaOpenAi respuesta = llamarOpenAiTexto(
                    modelo,
                    promptConsolidacion(objectMapper.writeValueAsString(datos)),
                    "extraccion_ga_consolidada",
                    esquemaConsolidacionCompacto()
            );
            try {
                bloque.set("resultado", objectMapper.readTree(respuesta.resultadoJson()));
            } catch (IOException exception) {
                bloque.put("resultadoTexto", respuesta.resultadoJson());
            }
            bloque.set("uso", objectMapper.valueToTree(respuesta.uso()));
            return bloque;
        } catch (IOException exception) {
            throw new RuntimeException("No se pudo preparar la consolidacion IA", exception);
        }
    }

    private ObjectNode aplicarCatalogoAuxiliar(ObjectNode consolidado, Expediente expediente) {
        ObjectNode resumen = objectMapper.createObjectNode();
        ArrayNode fuentes = objectMapper.createArrayNode();
        int completados = 0;

        ObjectNode vehiculo = objectNode(consolidado, "vehiculo");
        String matricula = normalizarMatricula(primerNoVacio(valor(vehiculo, "matricula"), expediente.getMatricula()));
        if (!matricula.isBlank()) {
            var catalogoVehiculo = gestionVehiculoCatalogoRepository.findFirstByMatriculaNormalizadaOrderByIdAsc(matricula);
            if (catalogoVehiculo.isPresent()) {
                int fields = aplicarVehiculoCatalogo(vehiculo, catalogoVehiculo.get());
                if (fields > 0) {
                    completados += fields;
                    ObjectNode source = objectMapper.createObjectNode();
                    source.put("tipo", "vehiculo");
                    source.put("clave", matricula);
                    source.put("campos", fields);
                    fuentes.add(source);
                }
            }
        }

        completados += aplicarPersonaCatalogo(objectNode(consolidado, "transmitente"), "transmitente", fuentes);
        completados += aplicarPersonaCatalogo(objectNode(consolidado, "adquirente"), "adquirente", fuentes);
        completados += aplicarRepresentanteCatalogo(consolidado, fuentes);

        resumen.put("camposCompletados", completados);
        resumen.put("criterio", "Solo completa campos vacios; no sobrescribe evidencia documental.");
        resumen.set("fuentes", fuentes);
        return resumen;
    }

    private ObjectNode aplicarCartoCiudadDirecciones(ObjectNode consolidado) {
        ObjectNode resumen = objectMapper.createObjectNode();
        ArrayNode fuentes = objectMapper.createArrayNode();
        if (!cartoCiudadEnabled) {
            resumen.put("habilitado", false);
            resumen.put("camposCompletados", 0);
            resumen.set("fuentes", fuentes);
            return resumen;
        }

        int completados = 0;
        completados += aplicarCartoCiudadDireccion(consolidado, "transmitente", "transmitente", fuentes);
        completados += aplicarCartoCiudadDireccion(consolidado, "adquirente", "adquirente", fuentes);
        completados += aplicarCartoCiudadDireccion(consolidado, "representanteTransmitente", "representanteTransmitente", fuentes);

        resumen.put("habilitado", true);
        resumen.put("camposCompletados", completados);
        resumen.put("criterio", "Solo completa campos vacios a partir del servicio REST Geocoder de CartoCiudad.");
        resumen.set("fuentes", fuentes);
        return resumen;
    }

    private int aplicarCartoCiudadDireccion(ObjectNode consolidado, String personaKey, String rol, ArrayNode fuentes) {
        JsonNode personaNode = consolidado.path(personaKey);
        if (!(personaNode instanceof ObjectNode persona)) {
            return 0;
        }
        JsonNode direccionNode = persona.path("direccion");
        if (!(direccionNode instanceof ObjectNode direccion)) {
            return 0;
        }
        if (!valor(direccion, "codigoPostal").isBlank()
                && !valor(direccion, "municipio").isBlank()
                && !valor(direccion, "provincia").isBlank()) {
            return 0;
        }
        DireccionCartoCiudadConsulta consulta = consultaCartoCiudad(rol, direccion);
        if (consulta == null) {
            return 0;
        }
        Optional<CartoCiudadCandidate> candidate = resolverCartoCiudad(consulta);
        if (candidate.isEmpty() || candidate.get().confianza() < CARTOCIUDAD_CONFIANZA_MINIMA) {
            return 0;
        }

        CartoCiudadCandidate match = candidate.get();
        String observacion = "Dato sugerido por CartoCiudad: " + match.address();
        int count = 0;
        count += rellenarCampoSiVacio(direccion, "codigoPostal", match.postalCode(), match.confianza(), "CartoCiudad", observacion);
        count += rellenarCampoSiVacio(direccion, "municipio", match.municipio(), match.confianza(), "CartoCiudad", observacion);
        count += rellenarCampoSiVacio(direccion, "pueblo", match.poblacion(), match.confianza(), "CartoCiudad", observacion);
        count += rellenarCampoSiVacio(direccion, "provincia", match.provincia(), match.confianza(), "CartoCiudad", observacion);
        if (count > 0) {
            ObjectNode source = objectMapper.createObjectNode();
            source.put("tipo", rol);
            source.put("consulta", consulta.query());
            source.put("codigoPostal", match.postalCode());
            source.put("direccion", match.address());
            source.put("confianza", match.confianza());
            source.put("campos", count);
            fuentes.add(source);
        }
        return count;
    }

    private DireccionCartoCiudadConsulta consultaCartoCiudad(String rol, ObjectNode direccion) {
        String via = normalizarNombreVia(valor(direccion, "nombreVia"));
        String numero = normalizarNumeroDireccion(valor(direccion, "numero"));
        String municipio = normalizarTexto(valor(direccion, "municipio"));
        String pueblo = normalizarTexto(valor(direccion, "pueblo"));
        String provincia = normalizarTexto(valor(direccion, "provincia"));
        if (via.isBlank() || (municipio.isBlank() && pueblo.isBlank() && provincia.isBlank())) {
            return null;
        }
        String query = String.join(" ", java.util.stream.Stream.of(via, numero, primerNoVacio(pueblo, municipio), provincia, "Espana")
                .filter(value -> value != null && !value.isBlank())
                .toList());
        if (query.length() < 8) {
            return null;
        }
        return new DireccionCartoCiudadConsulta(rol, query, via, numero, municipio, provincia, pueblo);
    }

    private Optional<CartoCiudadCandidate> resolverCartoCiudad(DireccionCartoCiudadConsulta consulta) {
        String cacheKey = normalizarBusqueda(consulta.query());
        if (cacheKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return cartoCiudadCache.computeIfAbsent(cacheKey, ignored -> buscarCartoCiudad(consulta));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<CartoCiudadCandidate> buscarCartoCiudad(DireccionCartoCiudadConsulta consulta) {
        try {
            String encodedQuery = URLEncoder.encode(consulta.query(), StandardCharsets.UTF_8);
            String separator = cartoCiudadCandidatesUrl.contains("?") ? "&" : "?";
            URI uri = URI.create(cartoCiudadCandidatesUrl + separator + "q=" + encodedQuery + "&limit=5");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.max(2, cartoCiudadTimeoutSeconds)))
                    .header("Accept", "application/json")
                    .header("User-Agent", "gestor-documental/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                return Optional.empty();
            }
            CartoCiudadCandidate best = null;
            for (JsonNode item : root) {
                CartoCiudadCandidate candidate = parseCartoCiudadCandidate(item, consulta);
                if (candidate == null) {
                    continue;
                }
                if (best == null || candidate.confianza() > best.confianza()) {
                    best = candidate;
                }
            }
            return Optional.ofNullable(best);
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private CartoCiudadCandidate parseCartoCiudadCandidate(JsonNode item, DireccionCartoCiudadConsulta consulta) {
        String postalCode = soloDigitos(item.path("postalCode").asText(""));
        if (postalCode.length() != 5) {
            return null;
        }
        String address = item.path("address").asText("");
        String municipio = item.path("muni").asText("");
        String provincia = item.path("province").asText("");
        String poblacion = item.path("poblacion").asText("");
        double confidence = confianzaCartoCiudad(item, consulta, address, municipio, provincia);
        return new CartoCiudadCandidate(
                postalCode,
                municipio,
                provincia,
                poblacion,
                address,
                confidence
        );
    }

    private double confianzaCartoCiudad(JsonNode item, DireccionCartoCiudadConsulta consulta, String address, String municipio, String provincia) {
        double confidence = 0.58;
        String normalizedAddress = normalizarBusqueda(address);
        String normalizedVia = normalizarBusqueda(consulta.nombreVia());
        String normalizedMunicipio = normalizarBusqueda(consulta.municipio());
        String normalizedPueblo = normalizarBusqueda(consulta.pueblo());
        String normalizedProvincia = normalizarBusqueda(consulta.provincia());
        if (!normalizedVia.isBlank() && normalizedAddress.contains(normalizedVia)) {
            confidence += 0.16;
        }
        String candidateMunicipio = normalizarBusqueda(municipio);
        if (!normalizedMunicipio.isBlank() && (candidateMunicipio.contains(normalizedMunicipio) || normalizedMunicipio.contains(candidateMunicipio))) {
            confidence += 0.10;
        } else if (!normalizedPueblo.isBlank() && normalizedAddress.contains(normalizedPueblo)) {
            confidence += 0.08;
        }
        String candidateProvincia = normalizarBusqueda(provincia);
        if (!normalizedProvincia.isBlank() && (candidateProvincia.contains(normalizedProvincia) || normalizedProvincia.contains(candidateProvincia))) {
            confidence += 0.05;
        }
        String type = normalizarBusqueda(item.path("type").asText(""));
        if (type.equals("PORTAL")) {
            confidence += 0.04;
        }
        Integer numeroConsulta = parseEntero(consulta.numero());
        Integer numeroCandidato = item.path("portalNumber").canConvertToInt() ? item.path("portalNumber").asInt() : null;
        if (numeroConsulta != null && numeroCandidato != null) {
            int diff = Math.abs(numeroConsulta - numeroCandidato);
            if (diff == 0) {
                confidence += 0.10;
            } else if (diff <= 4) {
                confidence += 0.06;
            }
        } else if (numeroConsulta == null) {
            confidence += 0.03;
        }
        if (item.path("state").asInt(0) == 0) {
            confidence += 0.03;
        }
        return Math.min(0.96, confidence);
    }

    private Integer parseEntero(String value) {
        String digits = soloDigitos(value);
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int aplicarVehiculoCatalogo(ObjectNode vehiculo, GestionVehiculoCatalogo catalogo) {
        int count = 0;
        count += rellenarCampoSiVacio(vehiculo, "matricula", catalogo.getMatriculaNormalizada(), "Matricula del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "numeroBastidor", catalogo.getBastidor(), "Bastidor del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "marca", catalogo.getMarca(), "Marca del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "modelo", catalogo.getModeloSugerido(), "Modelo del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "fechaMatriculacion", normalizarFecha(catalogo.getFechaPrimeraMatriculacion()), "Fecha tomada del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "fechaPrimeraMatriculacion", normalizarFecha(catalogo.getFechaPrimeraMatriculacion()), "Fecha tomada del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "cilindrada", catalogo.getCilindrada(), "Cilindrada del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "potencia", catalogo.getPotencia(), "Potencia del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "carburante", primerNoVacio(catalogo.getCarburanteCodigo(), catalogo.getCarburanteDescripcion()), "Carburante del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "fechaItv", normalizarFecha(catalogo.getFechaItv()), "Fecha ITV del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "claseVehiculo", primerNoVacio(catalogo.getTipo620Descripcion(), catalogo.getClasificacionItv()), "Tipo de vehiculo del catalogo historico");
        count += rellenarCampoSiVacio(vehiculo, "tipoVehiculo", primerNoVacio(catalogo.getTipo620Descripcion(), catalogo.getClasificacionItv()), "Tipo de vehiculo del catalogo historico");
        return count;
    }

    private int aplicarPersonaCatalogo(ObjectNode persona, String rol, ArrayNode fuentes) {
        String nif = normalizarDni(valor(persona, "dni"));
        if (nif.isBlank()) {
            return 0;
        }
        var catalogo = gestionPersonaCatalogoRepository.findFirstByNifNormalizadoOrderByIdAsc(nif);
        if (catalogo.isEmpty()) {
            return 0;
        }
        int fields = aplicarDatosPersona(persona, catalogo.get(), "Datos personales del catalogo historico");
        if (fields > 0) {
            ObjectNode source = objectMapper.createObjectNode();
            source.put("tipo", rol);
            source.put("clave", nif);
            source.put("campos", fields);
            fuentes.add(source);
        }
        return fields;
    }

    private int aplicarRepresentanteCatalogo(ObjectNode consolidado, ArrayNode fuentes) {
        ObjectNode transmitente = objectNode(consolidado, "transmitente");
        String nifEmpresa = normalizarDni(valor(transmitente, "dni"));
        if (nifEmpresa.isBlank()) {
            return 0;
        }
        List<GestionPersonaRepresentanteCatalogo> representantes = gestionPersonaRepresentanteCatalogoRepository.findByEmpresaNifNormalizadoOrderByIdAsc(nifEmpresa);
        if (representantes.isEmpty()) {
            return 0;
        }
        ObjectNode representante = objectNode(consolidado, "representanteTransmitente");
        GestionPersonaRepresentanteCatalogo catalogo = representantes.get(0);
        int count = 0;
        count += rellenarCampoSiVacio(representante, "dni", catalogo.getRepresentanteNifNormalizado(), "Representante vinculado a la empresa en catalogo historico");
        boolean corregirOrdenNombre = debeCorregirOrdenNombre(representante, catalogo.getRepresentanteApellido1RazonSocial(), catalogo.getRepresentanteApellido2(), catalogo.getRepresentanteNombre());
        count += rellenarCampoNombreCatalogo(representante, "apellido1", catalogo.getRepresentanteApellido1RazonSocial(), corregirOrdenNombre, "Representante vinculado a la empresa en catalogo historico");
        count += rellenarCampoNombreCatalogo(representante, "apellido2", catalogo.getRepresentanteApellido2(), corregirOrdenNombre, "Representante vinculado a la empresa en catalogo historico");
        count += rellenarCampoNombreCatalogo(representante, "nombre", catalogo.getRepresentanteNombre(), corregirOrdenNombre, "Representante vinculado a la empresa en catalogo historico");
        count += rellenarCampoNombreCatalogo(representante, "nombreCompleto", nombreCompleto(catalogo.getRepresentanteApellido1RazonSocial(), catalogo.getRepresentanteApellido2(), catalogo.getRepresentanteNombre()), corregirOrdenNombre, "Representante vinculado a la empresa en catalogo historico");
        count += rellenarCampoSiVacio(representante, "sexo", catalogo.getRepresentanteSexo(), "Representante vinculado a la empresa en catalogo historico");
        count += rellenarCampoSiVacio(representante, "fechaNacimiento", normalizarFecha(catalogo.getRepresentanteFechaNacimiento()), "Representante vinculado a la empresa en catalogo historico");
        ObjectNode direccion = objectNode(representante, "direccion");
        count += rellenarCampoSiVacio(direccion, "siglas", catalogo.getRepresentanteDirSiglas(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "nombreVia", catalogo.getRepresentanteDirCalle(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "numero", catalogo.getRepresentanteDirNumero(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "piso", catalogo.getRepresentanteDirPiso(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "puerta", catalogo.getRepresentanteDirPuerta(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "municipio", catalogo.getRepresentanteDirMunicipio(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "pueblo", catalogo.getRepresentanteDirPueblo(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "provincia", catalogo.getRepresentanteDirProvincia(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "codigoPostal", catalogo.getRepresentanteDirCp(), "Domicilio de representante del catalogo historico");
        count += rellenarCampoSiVacio(direccion, "pais", catalogo.getRepresentanteDirPais(), "Domicilio de representante del catalogo historico");
        if (count > 0) {
            ObjectNode source = objectMapper.createObjectNode();
            source.put("tipo", "representanteTransmitente");
            source.put("clave", nifEmpresa);
            source.put("campos", count);
            fuentes.add(source);
        }
        return count;
    }

    private int aplicarDatosPersona(ObjectNode persona, GestionPersonaCatalogo catalogo, String observacion) {
        int count = 0;
        count += rellenarCampoSiVacio(persona, "dni", catalogo.getNifNormalizado(), observacion);
        boolean corregirOrdenNombre = debeCorregirOrdenNombre(persona, catalogo.getApellido1RazonSocial(), catalogo.getApellido2(), catalogo.getNombre());
        count += rellenarCampoNombreCatalogo(persona, "apellido1", catalogo.getApellido1RazonSocial(), corregirOrdenNombre, observacion);
        count += rellenarCampoNombreCatalogo(persona, "apellido2", catalogo.getApellido2(), corregirOrdenNombre, observacion);
        count += rellenarCampoNombreCatalogo(persona, "nombre", catalogo.getNombre(), corregirOrdenNombre, observacion);
        count += rellenarCampoNombreCatalogo(persona, "nombreCompleto", nombreCompleto(catalogo.getApellido1RazonSocial(), catalogo.getApellido2(), catalogo.getNombre()), corregirOrdenNombre, observacion);
        count += rellenarCampoSiVacio(persona, "sexo", catalogo.getSexo(), observacion);
        count += rellenarCampoSiVacio(persona, "fechaNacimiento", normalizarFecha(catalogo.getFechaNacimiento()), observacion);
        ObjectNode direccion = objectNode(persona, "direccion");
        count += rellenarCampoSiVacio(direccion, "siglas", catalogo.getDirSiglas(), observacion);
        count += rellenarCampoSiVacio(direccion, "nombreVia", catalogo.getDirCalle(), observacion);
        count += rellenarCampoSiVacio(direccion, "numero", catalogo.getDirNumero(), observacion);
        count += rellenarCampoSiVacio(direccion, "piso", catalogo.getDirPiso(), observacion);
        count += rellenarCampoSiVacio(direccion, "puerta", catalogo.getDirPuerta(), observacion);
        count += rellenarCampoSiVacio(direccion, "municipio", catalogo.getDirMunicipio(), observacion);
        count += rellenarCampoSiVacio(direccion, "pueblo", catalogo.getDirPueblo(), observacion);
        count += rellenarCampoSiVacio(direccion, "provincia", catalogo.getDirProvincia(), observacion);
        count += rellenarCampoSiVacio(direccion, "codigoPostal", catalogo.getDirCp(), observacion);
        count += rellenarCampoSiVacio(direccion, "pais", catalogo.getDirPais(), observacion);
        return count;
    }

    private ObjectNode objectNode(ObjectNode parent, String key) {
        JsonNode existing = parent.path(key);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(key, created);
        return created;
    }

    private int rellenarCampoSiVacio(ObjectNode parent, String key, String value, String observacion) {
        return rellenarCampoSiVacio(parent, key, value, 0.72, "catalogo Gestion Trafico", observacion);
    }

    private int rellenarCampoSiVacio(ObjectNode parent, String key, String value, double confianza, String fuente, String observacion) {
        if (value == null || value.isBlank() || !valor(parent, key).isBlank()) {
            return 0;
        }
        ObjectNode field = objectMapper.createObjectNode();
        field.put("valor", value.trim());
        field.put("confianza", confianza);
        field.put("fuente", fuente);
        field.put("observacion", observacion);
        parent.set(key, field);
        return 1;
    }

    private int rellenarCampoNombreCatalogo(ObjectNode parent, String key, String value, boolean corregirOrden, String observacion) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String current = valor(parent, key);
        if (current.isBlank()) {
            return rellenarCampoSiVacio(parent, key, value, observacion);
        }
        if (!corregirOrden || normalizarBusqueda(current).equals(normalizarBusqueda(value))) {
            return 0;
        }
        ObjectNode field = objectMapper.createObjectNode();
        field.put("valor", value.trim());
        field.put("confianza", 0.88);
        field.put("fuente", "catalogo Gestion Trafico");
        field.put("observacion", "Orden de nombre y apellidos corregido por coincidencia exacta de documento en catalogo historico.");
        parent.set(key, field);
        return 1;
    }

    private boolean debeCorregirOrdenNombre(ObjectNode persona, String apellido1Catalogo, String apellido2Catalogo, String nombreCatalogo) {
        String catalogo = nombreCompleto(apellido1Catalogo, apellido2Catalogo, nombreCatalogo);
        if (catalogo.isBlank()) {
            return false;
        }
        String actual = nombreCompleto(valor(persona, "apellido1"), valor(persona, "apellido2"), valor(persona, "nombre"));
        if (actual.isBlank()) {
            actual = valor(persona, "nombreCompleto");
        }
        if (actual.isBlank() || normalizarBusqueda(actual).equals(normalizarBusqueda(catalogo))) {
            return false;
        }
        return firmaTokensNombre(actual).equals(firmaTokensNombre(catalogo));
    }

    private String firmaTokensNombre(String value) {
        return java.util.Arrays.stream(normalizarBusqueda(value).replaceAll("[^A-Z0-9 ]", " ").split("\\s+"))
                .filter(token -> !token.isBlank())
                .sorted()
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void normalizarFechasConsolidadas(ObjectNode consolidado) {
        normalizarCampoFecha(consolidado.path("transmitente"), "fechaNacimiento");
        normalizarCampoFecha(consolidado.path("adquirente"), "fechaNacimiento");
        normalizarCampoFecha(consolidado.path("representanteTransmitente"), "fechaNacimiento");
        normalizarCampoFecha(consolidado.path("vehiculo"), "fechaMatriculacion");
        normalizarCampoFecha(consolidado.path("vehiculo"), "fechaPrimeraMatriculacion");
        normalizarCampoFecha(consolidado.path("vehiculo"), "fechaItv");
        normalizarCampoFecha(consolidado.path("impuestos"), "fechaDevengo");
        normalizarCampoFecha(consolidado.path("impuestos"), "fechaPrimeraMatriculacion");
        normalizarCampoFecha(consolidado.path("acreditacion"), "fechaContrato");
    }

    private void normalizarDireccionesConsolidadas(ObjectNode consolidado) {
        normalizarDireccionConsolidada(consolidado.path("transmitente").path("direccion"));
        normalizarDireccionConsolidada(consolidado.path("adquirente").path("direccion"));
        normalizarDireccionConsolidada(consolidado.path("representanteTransmitente").path("direccion"));
    }

    private void normalizarDireccionConsolidada(JsonNode direccionNode) {
        if (!(direccionNode instanceof ObjectNode direccion)) {
            return;
        }
        String codigoPostal = normalizarCodigoPostal(valor(direccion, "codigoPostal"));
        actualizarCampoNormalizado(direccion, "codigoPostal", codigoPostal, false);

        String municipio = normalizarMunicipio(valor(direccion, "municipio"), codigoPostal);
        actualizarCampoNormalizado(direccion, "municipio", municipio, false);
        actualizarCampoNormalizado(direccion, "pueblo", normalizarPueblo(valor(direccion, "pueblo"), municipio, codigoPostal), true);
        actualizarCampoNormalizado(direccion, "provincia", normalizarProvincia(valor(direccion, "provincia"), codigoPostal), false);

        String nombreVia = normalizarNombreVia(valor(direccion, "nombreVia"));
        actualizarCampoNormalizado(direccion, "nombreVia", nombreVia, false);
        actualizarCampoNormalizado(direccion, "numero", normalizarNumeroDireccion(valor(direccion, "numero")), false);
        actualizarCampoNormalizado(direccion, "siglas", normalizarSiglas(valor(direccion, "siglas"), nombreVia), false);

        String pais = normalizarPais(valor(direccion, "pais"));
        if (pais.isBlank() && direccionConDatos(direccion)) {
            pais = "ESP";
        }
        actualizarCampoNormalizado(direccion, "pais", pais, false);
    }

    private boolean direccionConDatos(ObjectNode direccion) {
        return !valor(direccion, "nombreVia").isBlank()
                || !valor(direccion, "codigoPostal").isBlank()
                || !valor(direccion, "municipio").isBlank();
    }

    private void actualizarCampoNormalizado(ObjectNode parent, String key, String value, boolean permitirVacio) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() && !permitirVacio) {
            return;
        }
        String current = valor(parent, key);
        if (current.equals(normalized)) {
            return;
        }
        JsonNode existing = parent.get(key);
        if (existing instanceof ObjectNode field) {
            field.put("valor", normalized);
            if (valor(field, "fuente").isBlank()) {
                field.put("fuente", "NORMALIZACION");
            }
            field.put("observacion", "Normalizado al formato esperado por XML GA");
            return;
        }
        if (existing != null && !existing.isMissingNode() && !existing.isNull()) {
            parent.put(key, normalized);
            return;
        }
        if (!normalized.isBlank()) {
            ObjectNode field = objectMapper.createObjectNode();
            field.put("valor", normalized);
            field.put("confianza", 0.86);
            field.put("fuente", "NORMALIZACION");
            field.put("observacion", "Dato completado por reglas de normalizacion para XML GA");
            parent.set(key, field);
        }
    }

    private void normalizarCampoFecha(JsonNode parent, String key) {
        if (!(parent instanceof ObjectNode objectNode)) {
            return;
        }
        JsonNode valueNode = objectNode.get(key);
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return;
        }
        if (valueNode instanceof ObjectNode fieldNode) {
            String current = valor(fieldNode, "valor");
            String normalized = normalizarFecha(current);
            if (!normalized.isBlank()) {
                fieldNode.put("valor", normalized);
            }
        } else if (valueNode.isTextual()) {
            String normalized = normalizarFecha(valueNode.asText());
            if (!normalized.isBlank()) {
                objectNode.put(key, normalized);
            }
        }
    }

    private String nombreCompleto(String apellido1, String apellido2, String nombre) {
        return String.join(" ", java.util.stream.Stream.of(apellido1, apellido2, nombre)
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private ObjectNode construirFormatoGaNormalizado(JsonNode consolidado) {
        ObjectNode formato = objectMapper.createObjectNode();
        ArrayNode avisos = objectMapper.createArrayNode();
        avisos.add("Normalizacion determinista sin coste de tokens; revisar defaults No/ESP/B00 en casos especiales.");

        JsonNode transmitente = consolidado.path("transmitente");
        JsonNode adquirente = consolidado.path("adquirente");
        JsonNode representanteTransmitente = consolidado.path("representanteTransmitente");
        JsonNode vehiculo = consolidado.path("vehiculo");
        JsonNode impuestos = consolidado.path("impuestos");
        JsonNode acreditacion = consolidado.path("acreditacion");

        formato.set("DATOS_TRANSMITENTE", normalizarPersonaGa(transmitente, "TRANSMITENTE", false));
        formato.set("DATOS_REPRESENTANTE_TRANSMITENTE", normalizarPersonaGa(representanteTransmitente, "REP_TRANSMITENTE", false));
        formato.set("DATOS_ADQUIRENTE", normalizarPersonaGa(adquirente, "ADQUIRENTE", true));
        formato.set("DATOS_VEHICULO", normalizarVehiculoGa(vehiculo));
        formato.set("DATOS_IMPUESTOS", normalizarImpuestosGa(impuestos, acreditacion));
        formato.set("ACREDITACION_DERECHO", normalizarAcreditacionDerechoGa(acreditacion));
        formato.set("ACREDITACION_FISCAL", normalizarAcreditacionFiscalGa(acreditacion, impuestos, adquirente, transmitente));

        ObjectNode control = objectMapper.createObjectNode();
        control.set("avisos", avisos);
        formato.set("CONTROL_NORMALIZACION", control);
        return formato;
    }

    private ObjectNode normalizarPersonaGa(JsonNode persona, String sufijo, boolean adquirente) {
        ObjectNode node = objectMapper.createObjectNode();
        String dni = normalizarDni(valor(persona, "dni"));
        String razonSocial = normalizarTexto(valor(persona, "razonSocial"));
        String nombreCompleto = normalizarTexto(valor(persona, "nombreCompleto"));
        boolean personaJuridica = esDocumentoEmpresa(dni) || !razonSocial.isBlank();
        putValue(node, "DNI_" + sufijo, dni);
        putValue(node, "RAZON_SOCIAL_" + sufijo, personaJuridica ? primerNoVacio(razonSocial, nombreCompleto, normalizarTexto(valor(persona, "nombre"))) : "");
        putValue(node, "APELLIDO1_" + sufijo, personaJuridica ? "" : normalizarTexto(valor(persona, "apellido1")));
        putValue(node, "APELLIDO2_" + sufijo, personaJuridica ? "" : normalizarTexto(valor(persona, "apellido2")));
        putValue(node, "NOMBRE_" + sufijo, personaJuridica ? "" : normalizarTexto(primerNoVacio(valor(persona, "nombre"), nombreCompleto)));
        putValue(node, "SEXO_" + sufijo, normalizarSexo(valor(persona, "sexo")));
        putValue(node, "FECHA_NACIMIENTO_" + sufijo, normalizarFecha(valor(persona, "fechaNacimiento")));
        putValue(node, "AUTONOMO_" + sufijo, normalizarSiNo(valor(persona, "autonomo"), "No"));
        if (adquirente) {
            putValue(node, "CODIGO_IAE_" + sufijo, "");
            putValue(node, "CAMBIO_DOMICILIO_" + sufijo, normalizarSiNo(valor(persona, "cambioDomicilio"), "No"));
        } else {
            putValue(node, "IAE_" + sufijo, "");
        }
        putValue(node, "ESCOMPRAVENTA", normalizarSiNo(valor(persona, "esCompraventa"), "No"));
        putValue(node, "COTITULARES_" + sufijo, "0");

        JsonNode direccionOrigen = persona.path("direccion");
        ObjectNode direccion = objectMapper.createObjectNode();
        String codigoPostal = normalizarCodigoPostal(valor(direccionOrigen, "codigoPostal"));
        String provincia = normalizarProvincia(valor(direccionOrigen, "provincia"), codigoPostal);
        String municipio = normalizarMunicipio(valor(direccionOrigen, "municipio"), codigoPostal);
        String pueblo = normalizarPueblo(valor(direccionOrigen, "pueblo"), municipio, codigoPostal);
        putValue(direccion, "SIGLAS_DIRECCION_" + sufijo, normalizarSiglas(valor(direccionOrigen, "siglas"), valor(direccionOrigen, "nombreVia")));
        putValue(direccion, "NOMBRE_VIA_DIRECCION_" + sufijo, normalizarNombreVia(valor(direccionOrigen, "nombreVia")));
        putValue(direccion, "NUMERO_DIRECCION_" + sufijo, normalizarNumeroDireccion(valor(direccionOrigen, "numero")));
        putValue(direccion, "KM_DIRECCION_" + sufijo, "");
        putValue(direccion, "HECTOMETRO_DIRECCION_" + sufijo, "");
        putValue(direccion, "LETRA_DIRECCION_" + sufijo, "");
        putValue(direccion, "ESCALERA_DIRECCION_" + sufijo, "");
        putValue(direccion, "PISO_DIRECCION_" + sufijo, normalizarTexto(valor(direccionOrigen, "piso")));
        putValue(direccion, "PUERTA_DIRECCION_" + sufijo, normalizarTexto(valor(direccionOrigen, "puerta")));
        putValue(direccion, "BLOQUE_DIRECCION_" + sufijo, "");
        putValue(direccion, "MUNICIPIO_" + sufijo, municipio);
        putValue(direccion, "PUEBLO_" + sufijo, pueblo);
        putValue(direccion, "PROVINCIA_" + sufijo, provincia);
        putValue(direccion, "CP_" + sufijo, codigoPostal);
        putValue(direccion, "PAIS_" + sufijo, normalizarPais(valor(direccionOrigen, "pais")));
        node.set("DIRECCION_" + sufijo, direccion);

        putValue(node, "TELEFONO_" + sufijo, "");
        putValue(node, "FAX_" + sufijo, "");
        putValue(node, "DOI_SUSTITUTIVO_" + sufijo, "");
        putValue(node, "FECHA_CADU_DOI_" + sufijo, "");
        putValue(node, "EXENTO_CADU_DOI_" + sufijo, "No");
        putValue(node, "ESTADO_CIVIL_" + sufijo, "");
        putValue(node, "NACIONALIDAD_" + sufijo, "");
        return node;
    }

    private boolean esDocumentoEmpresa(String dni) {
        return dni != null && dni.matches("^[ABCDEFGHJKLMNPQRSUVW].*");
    }

    private ObjectNode normalizarVehiculoGa(JsonNode vehiculo) {
        ObjectNode node = objectMapper.createObjectNode();
        String clase = normalizarClaseVehiculo(valor(vehiculo, "claseVehiculo"), valor(vehiculo, "tipoVehiculo"), valor(vehiculo, "modelo"));
        String tipo = normalizarTipoVehiculo(valor(vehiculo, "tipoVehiculo"), clase);
        String fechaMatriculacion = normalizarFecha(valor(vehiculo, "fechaMatriculacion"));
        String fechaPrimeraMatriculacion = normalizarFecha(valor(vehiculo, "fechaPrimeraMatriculacion"));
        String fechaReferenciaMatriculacion = primerNoVacio(fechaPrimeraMatriculacion, fechaMatriculacion);
        putValue(node, "MATRICULA", normalizarMatricula(valor(vehiculo, "matricula")));
        putValue(node, "FECHA_MATRICULACION", fechaReferenciaMatriculacion);
        putValue(node, "FECHA_PRIMERA_MATRICULACION", fechaReferenciaMatriculacion);
        putValue(node, "PROVINCIA_PRIMERA_MATRICULACION", "ND");
        putValue(node, "A\u00d1O_FABRICACION", extraerAnio(fechaReferenciaMatriculacion));
        putValue(node, "MARCA", normalizarTexto(valor(vehiculo, "marca")));
        putValue(node, "MODELO", normalizarTexto(valor(vehiculo, "modelo")));
        putValue(node, "NUMERO_BASTIDOR", normalizarBastidor(valor(vehiculo, "numeroBastidor")));
        putValue(node, "CILINDRADA", formatearCilindrada(valor(vehiculo, "cilindrada")));
        putValue(node, "POTENCIA", formatearPotencia(valor(vehiculo, "potencia"), clase));
        putValue(node, "CARBURANTE", normalizarCarburante(valor(vehiculo, "carburante")));
        putValue(node, "NUMERO_CILINDROS", "00");
        putValue(node, "MASA", "000000");
        putValue(node, "TARA", "000000");
        putValue(node, "PLAZAS", "000");
        putValue(node, "MODO_ADJUDICACION", "1");
        putValue(node, "SERVICIO_DESTINA", normalizarServicioDestino(valor(vehiculo, "servicioDestino")));
        putValue(node, "CAMBIO_SERVICIO", "No");
        putValue(node, "SERVICIO_DESTINA_NUEVO", "");
        putValue(node, "CLASE_VEHICULO", clase);
        putValue(node, "TIPO_VEHICULO", tipo);
        putValue(node, "FECHA_ITV", normalizarFecha(valor(vehiculo, "fechaItv")));
        return node;
    }

    private ObjectNode normalizarImpuestosGa(JsonNode impuestos, JsonNode acreditacion) {
        ObjectNode node = objectMapper.createObjectNode();
        String base = primerNoVacio(valor(impuestos, "baseImponible"), valor(impuestos, "valorDeclarado"));
        String valorDeclarado = primerNoVacio(valor(impuestos, "valorDeclarado"), base);
        String cuota = valor(impuestos, "cuotaTributaria");
        String total = primerNoVacio(valor(impuestos, "totalIngresar"), cuota);
        String fechaDevengo = normalizarFecha(valor(impuestos, "fechaDevengo"));

        putValue(node, "CAVALORACION", "GC");
        putValue(node, "ANYOVALORACION", extraerAnio(fechaDevengo));
        putValue(node, "FECHA_DEVENGO", fechaDevengo);
        putValue(node, "IMPUESTO_EXENTO", "No");
        putValue(node, "IMPUESTO_NO_SUJETO", "No");
        putValue(node, "REDUCCION", "00000");
        putValue(node, "BASE_IMPONIBLE", formatearImporteCentimos(base));
        putValue(node, "PORCENTAJE_ADQUISICION", "10000");
        putValue(node, "TIPO_GRAVAMEN", calcularTipoGravamen(base, cuota));
        putValue(node, "CUOTA_TRIBUTARIA", formatearImporteCentimos(cuota));
        putValue(node, "RECARGO", "00000000000");
        putValue(node, "INTERESES_DEMORA", "00000000000");
        putValue(node, "TOTAL_INGRESAR", formatearImporteCentimos(total));
        putValue(node, "IMPORTE_INGRESADO", "00000000000");
        putValue(node, "COMPLEMENTARIA", "No");
        putValue(node, "BASE_LIQUIDABLE", formatearImporteCentimos(base));
        putValue(node, "INGRESAR", formatearImporteCentimos(total));
        putValue(node, "VALOR_DECLARADO", formatearImporteCentimos(valorDeclarado));
        putValue(node, "SUJETO_IGIC", "No");
        putValue(node, "VENDEDOR_HABITUAL", "No");
        putValue(node, "CODIGO_ELECTRONICO_TRANSFERENCIA", "");
        return node;
    }

    private ObjectNode normalizarAcreditacionDerechoGa(JsonNode acreditacion) {
        ObjectNode node = objectMapper.createObjectNode();
        putValue(node, "SOLICITUD", "Si");
        putValue(node, "CONSENTIMIENTO", "N/A");
        putValue(node, "CONTRATO_COMPRAVENTA", normalizarSiNo(valor(acreditacion, "contratoCompraventa"), "Si"));
        putValue(node, "FACTURA", normalizarSiNo(valor(acreditacion, "factura"), ""));
        putValue(node, "FECHA_CONTRATO", normalizarFecha(valor(acreditacion, "fechaContrato")));
        putValue(node, "FECHA_FACTURA", "");
        putValue(node, "IVA", "No");
        putValue(node, "ACTA_ADJUDICACION_SUBASTA", "No");
        putValue(node, "SENTENCIA_JUDICIAL_ADJUDICACION", "No");
        putValue(node, "ACTA_NOTARIAL", "No");
        putValue(node, "ACREDITACION_POSESION", "No");
        putValue(node, "ACREDITACION_HERENCIA", "No");
        return node;
    }

    private ObjectNode normalizarAcreditacionFiscalGa(JsonNode acreditacion, JsonNode impuestos, JsonNode adquirente, JsonNode transmitente) {
        ObjectNode node = objectMapper.createObjectNode();
        putValue(node, "MODELO_ITP", normalizarTexto(valor(acreditacion, "modeloItp")));
        putValue(node, "EXENCION_ITP", "No");
        putValue(node, "NO_SUJECION_ITP", "No");
        putValue(node, "NUMERO_AUTOLIQUIDACION_ITP", "");
        putValue(node, "PROVINCIACET", "TF");
        putValue(node, "CET_ITP", "");
        putValue(node, "NO_OBLIGADO_ITP", "No");
        putValue(node, "MODELO_ISD", "");
        putValue(node, "EXENCION_ISD", "No");
        putValue(node, "NO_SUJECION_ISD", "No");
        putValue(node, "MODELO_IEDMT", "");
        putValue(node, "EXENCION_IEDMT", "No");
        putValue(node, "NO_SUJECION_IEDMT", "No");
        return node;
    }

    private Map<String, Object> usoMultiple(ObjectNode resultado) {
        Map<String, Object> uso = new LinkedHashMap<>();
        long input = 0;
        long output = 0;
        long total = 0;
        for (String bloque : List.of("bloqueContrato", "bloqueDni", "bloqueVehiculo", "bloqueFiscal", "bloqueConsolidacion")) {
            JsonNode usoBloque = resultado.path(bloque).path("uso");
            input += usoBloque.path("input_tokens").asLong(0);
            output += usoBloque.path("output_tokens").asLong(0);
            total += usoBloque.path("total_tokens").asLong(0);
            if (!usoBloque.isMissingNode()) {
                uso.put(bloque, objectMapper.convertValue(usoBloque, Map.class));
            }
        }
        uso.put("input_tokens", input);
        uso.put("output_tokens", output);
        uso.put("total_tokens", total);
        return uso;
    }

    private void agregarImagenesDniProcesadas(ArrayNode content, DocumentoSeleccionado documento) throws IOException {
        List<ImagenProcesada> imagenes = procesarDni(documento.ruta());
        for (ImagenProcesada imagen : imagenes) {
            ObjectNode nota = objectMapper.createObjectNode();
            nota.put("type", "input_text");
            nota.put("text", "Documento DNI/CIF id " + documento.documento().getId()
                    + " (" + documento.documento().getNombreArchivoOriginal() + "), imagen preprocesada " + imagen.nombre()
                    + ". Extrae una persona de esta imagen si hay DNI/CIF visible; no la omitas aunque haya otros DNI en la llamada.");
            content.add(nota);

            ObjectNode image = objectMapper.createObjectNode();
            image.put("type", "input_image");
            image.put("image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(imagen.bytes()));
            content.add(image);
        }
    }

    private List<ImagenProcesada> procesarDni(Path ruta) throws IOException {
        List<ImagenProcesada> imagenes = new ArrayList<>();
        try (PDDocument document = PDDocument.load(Files.readAllBytes(ruta))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int paginas = Math.min(document.getNumberOfPages(), DNI_MAX_PAGINAS_PROCESADAS);
            for (int pageIndex = 0; pageIndex < paginas; pageIndex++) {
                BufferedImage render = renderer.renderImageWithDPI(pageIndex, DNI_RENDER_DPI, ImageType.RGB);
                BufferedImage recorte = recortarZonaUtil(render);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(recorte, "png", output);
                imagenes.add(new ImagenProcesada("dni_pagina_" + (pageIndex + 1) + "_procesada.png", output.toByteArray()));
            }
        }
        return imagenes;
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
            return image;
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

    private void putValue(ObjectNode node, String key, String value) {
        node.put(key, value == null ? "" : value);
    }

    private String valor(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current.path(part);
        }
        JsonNode value = current.path("valor");
        if (value.isMissingNode()) {
            value = current;
        }
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private String primerNoVacio(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizarBusqueda(String value) {
        String normalized = Normalizer.normalize(normalizarTexto(value), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private String normalizarDni(String value) {
        return normalizarTexto(value).replaceAll("[^A-Z0-9]", "");
    }

    private String normalizarMatricula(String value) {
        return normalizarTexto(value).replaceAll("[^A-Z0-9]", "");
    }

    private String normalizarBastidor(String value) {
        return normalizarTexto(value).replaceAll("[^A-Z0-9]", "");
    }

    private String normalizarFecha(String value) {
        return GaDateNormalizer.toGaDate(value);
    }

    private String normalizarSexo(String value) {
        String text = normalizarBusqueda(value);
        if (text.isBlank()) {
            return "";
        }
        if (text.equals("V") || text.equals("M") || text.contains("MASCULINO") || text.contains("HOMBRE") || text.contains("VARON")) {
            return "V";
        }
        if (text.equals("F") || text.contains("FEMENINO") || text.contains("MUJER")) {
            return "M";
        }
        return normalizarTexto(value);
    }

    private String normalizarSiNo(String value, String fallback) {
        String text = normalizarBusqueda(value);
        if (text.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        if (text.equals("SI") || text.equals("S") || text.equals("TRUE") || text.equals("1")) {
            return "Si";
        }
        if (text.equals("NO") || text.equals("N") || text.equals("FALSE") || text.equals("0")) {
            return "No";
        }
        return normalizarTexto(value);
    }

    private String normalizarCodigoPostal(String value) {
        String digits = soloDigitos(value);
        if (digits.length() >= 5) {
            return digits.substring(0, 5);
        }
        return digits;
    }

    private String normalizarNumeroDireccion(String value) {
        String text = normalizarTexto(value);
        if (text.equals("SN") || text.equals("S/N") || text.equals("S N")) {
            return "0";
        }
        return text;
    }

    private String normalizarProvincia(String value, String codigoPostal) {
        String text = normalizarBusqueda(value);
        String cp = soloDigitos(codigoPostal);
        if (text.equals("TF") || text.equals("TFE") || text.equals("38") || text.equals("TENERIFE")
                || text.contains("SANTA CRUZ") || text.contains("S C TENERIFE") || cp.startsWith("38")) {
            return "TF";
        }
        if (text.equals("GC") || text.equals("35") || text.contains("PALMAS") || text.contains("GRAN CANARIA") || cp.startsWith("35")) {
            return "GC";
        }
        if (text.length() == 2 && text.matches("[A-Z]{2}")) {
            return text;
        }
        return normalizarTexto(value);
    }

    private String normalizarMunicipio(String value, String codigoPostal) {
        String text = normalizarBusqueda(value);
        String cp = soloDigitos(codigoPostal);
        String municipioCp = municipioPorCodigoPostal(cp);
        if (!municipioCp.isBlank()) {
            return municipioCp;
        }
        if (text.contains("GUIMAR") || cp.equals("38500")) {
            return "G\u00DC\u00CDMAR";
        }
        if ((text.contains("SANTA CRUZ") && text.contains("TENERIFE")) || cp.startsWith("381") || cp.startsWith("380")) {
            return "SANTA CRUZ DE TENERIFE";
        }
        return normalizarTexto(value);
    }

    private String normalizarPueblo(String value, String municipio, String codigoPostal) {
        String text = normalizarBusqueda(value);
        String cp = soloDigitos(codigoPostal);
        String puebloCp = puebloPorCodigoPostal(cp, text);
        if (!puebloCp.isBlank()) {
            return puebloCp;
        }
        if (text.contains("CHORRILLO")) {
            return "CHORRILLO, EL";
        }
        if (text.contains("SOBRADILLO")) {
            return "SOBRADILLO, EL";
        }
        if (text.contains("MEDANO")) {
            return "MEDANO, EL";
        }
        if (text.equals(normalizarBusqueda(municipio))) {
            return "";
        }
        return normalizarTexto(value);
    }

    private String municipioPorCodigoPostal(String codigoPostal) {
        String cp = soloDigitos(codigoPostal);
        return switch (cp) {
            case "38107" -> "SANTA CRUZ DE TENERIFE";
            case "38108" -> "SAN CRISTOBAL DE LA LAGUNA";
            case "38600", "38611", "38612" -> "GRANADILLA DE ABONA";
            case "35600" -> "PUERTO DEL ROSARIO";
            case "35200", "35210", "35211", "35212" -> "TELDE";
            default -> "";
        };
    }

    private String puebloPorCodigoPostal(String codigoPostal, String textoPueblo) {
        String cp = soloDigitos(codigoPostal);
        if (cp.equals("38611") && (textoPueblo.isBlank() || textoPueblo.contains("ABRIGOS"))) {
            return "LOS ABRIGOS";
        }
        if (cp.equals("38612") && (textoPueblo.isBlank() || textoPueblo.contains("MEDANO"))) {
            return "MEDANO, EL";
        }
        if (cp.equals("38107") && textoPueblo.contains("SOBRADILLO")) {
            return "SOBRADILLO, EL";
        }
        return "";
    }

    private String normalizarPais(String value) {
        String text = normalizarBusqueda(value);
        if (text.isBlank() || text.equals("ES") || text.equals("ESP") || text.contains("ESPANA") || text.contains("SPAIN")) {
            return "ESP";
        }
        return normalizarTexto(value);
    }

    private String normalizarSiglas(String siglas, String nombreVia) {
        String text = normalizarBusqueda(primerNoVacio(siglas, nombreVia));
        if (text.matches("\\d+")) {
            return text;
        }
        if (text.startsWith("C/") || text.startsWith("CL ") || text.startsWith("CALLE ") || text.equals("CALLE") || text.equals("CL")) {
            return "6";
        }
        if (text.startsWith("AV ") || text.startsWith("AVDA") || text.startsWith("AVENIDA") || text.equals("AV")) {
            return "2";
        }
        if (text.startsWith("CTRA") || text.startsWith("CARRETERA") || text.equals("CR")) {
            return "7";
        }
        if (text.startsWith("CAMINO") || text.equals("CM")) {
            return "50";
        }
        return nombreVia == null || nombreVia.isBlank() ? "" : "6";
    }

    private String normalizarNombreVia(String value) {
        String text = normalizarTexto(value);
        return text
                .replaceFirst("^(C/|CL\\.?|CALLE)\\s*", "")
                .replaceFirst("^(AVDA\\.?|AV\\.?|AVENIDA)\\s*", "")
                .replaceFirst("^(CTRA\\.?|CARRETERA)\\s*", "")
                .replaceFirst("^(CM\\.?|CAMINO)\\s*", "")
                .trim();
    }

    private String normalizarCarburante(String value) {
        String text = normalizarBusqueda(value);
        if (text.equals("GA") || text.equals("G") || text.contains("GASOLINA")) {
            if (text.contains("GLP")) {
                return "GG";
            }
            return "GA";
        }
        if (text.equals("GO") || text.equals("D") || text.contains("GASOIL") || text.contains("DIESEL")) {
            return "GO";
        }
        if (text.equals("GG") || text.contains("GLP")) {
            return "GG";
        }
        if (text.equals("EL") || text.equals("E") || text.contains("ELECTR")) {
            return "EL";
        }
        return normalizarTexto(value);
    }

    private String normalizarServicioDestino(String value) {
        String text = normalizarBusqueda(value);
        if (text.isBlank() || text.equals("B00") || text.contains("PARTICULAR") || text.contains("SIN ESPECIFICAR")) {
            return "B00";
        }
        return normalizarTexto(value);
    }

    private String normalizarClaseVehiculo(String clase, String tipo, String modelo) {
        String text = normalizarBusqueda(primerNoVacio(clase, tipo, modelo));
        if (text.equals("50")) {
            return "M";
        }
        if (text.equals("90")) {
            return "S";
        }
        if (text.equals("40")) {
            return "T";
        }
        if (text.equals("20")) {
            return "C";
        }
        if (text.equals("M") || text.contains("MOTO") || text.contains("MOTOCICLETA")) {
            return "M";
        }
        if (text.equals("S") || text.contains("SCOOTER") || text.contains("CICLOMOTOR")) {
            return "S";
        }
        if (text.equals("T") || text.contains("TURISMO")) {
            return "T";
        }
        if (text.equals("C") || text.contains("COMERCIAL") || text.contains("FURGON")) {
            return "C";
        }
        if (text.length() == 1 && text.matches("[A-Z]")) {
            return text;
        }
        return "";
    }

    private String normalizarTipoVehiculo(String tipo, String clase) {
        String text = normalizarBusqueda(tipo);
        if (text.matches("\\d{2}")) {
            return text;
        }
        if (clase.equals("M") || text.contains("MOTO") || text.contains("MOTOCICLETA")) {
            return "50";
        }
        if (clase.equals("S") || text.contains("SCOOTER") || text.contains("CICLOMOTOR")) {
            return "90";
        }
        if (clase.equals("T") || text.contains("TURISMO")) {
            return "40";
        }
        if (clase.equals("C") || text.contains("COMERCIAL") || text.contains("FURGON")) {
            return "20";
        }
        return "";
    }

    private String formatearCilindrada(String value) {
        String text = normalizarTexto(value).replace(",", ".");
        if (text.matches("\\d{7}")) {
            return text;
        }
        Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(text);
        if (!matcher.find()) {
            return "";
        }
        try {
            BigDecimal cc = new BigDecimal(matcher.group());
            long scaled = cc.multiply(BigDecimal.valueOf(100L)).setScale(0, RoundingMode.HALF_UP).longValue();
            return rellenarIzquierda(scaled, 7);
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private String formatearPotencia(String value, String clase) {
        if (clase.equals("M") || clase.equals("S")) {
            return "00000";
        }
        String text = normalizarTexto(value).replace(",", ".");
        if (text.isBlank()) {
            return "";
        }
        String digits = soloDigitos(text);
        if (digits.length() == 5) {
            return digits;
        }
        try {
            BigDecimal number = new BigDecimal(text.replaceAll("[^0-9.]", ""));
            long scaled = number.multiply(BigDecimal.valueOf(100L)).setScale(0, RoundingMode.HALF_UP).longValue();
            return rellenarIzquierda(scaled, 5);
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private String formatearImporteCentimos(String value) {
        Long centimos = importeCentimos(value);
        if (centimos == null) {
            return "";
        }
        return rellenarIzquierda(centimos, 11);
    }

    private String calcularTipoGravamen(String base, String cuota) {
        Long baseCentimos = importeCentimos(base);
        Long cuotaCentimos = importeCentimos(cuota);
        if (baseCentimos == null || baseCentimos == 0 || cuotaCentimos == null) {
            return "";
        }
        BigDecimal porcentaje = BigDecimal.valueOf(cuotaCentimos)
                .multiply(BigDecimal.valueOf(10000L))
                .divide(BigDecimal.valueOf(baseCentimos), 0, RoundingMode.HALF_UP);
        return rellenarIzquierda(porcentaje.longValue(), 5);
    }

    private Long importeCentimos(String value) {
        String text = normalizarTexto(value).replace("\u20AC", "").replace(" ", "");
        if (text.isBlank()) {
            return null;
        }
        String digits = soloDigitos(text);
        if (digits.length() == 11 && text.matches("\\d+")) {
            return Long.parseLong(digits);
        }
        if (text.contains(",") || text.contains(".")) {
            String decimal = normalizarDecimal(text);
            try {
                return new BigDecimal(decimal).multiply(BigDecimal.valueOf(100L)).setScale(0, RoundingMode.HALF_UP).longValue();
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (digits.isBlank()) {
            return null;
        }
        try {
            long number = Long.parseLong(digits);
            if (digits.length() >= 6 || digits.startsWith("0")) {
                return number;
            }
            return number * 100L;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizarDecimal(String value) {
        String text = value.replaceAll("[^0-9,.]", "");
        int lastComma = text.lastIndexOf(',');
        int lastDot = text.lastIndexOf('.');
        if (lastComma > lastDot) {
            return text.replace(".", "").replace(',', '.');
        }
        return text.replace(",", "");
    }

    private String extraerAnio(String fecha) {
        String normalized = normalizarFecha(fecha);
        if (normalized.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return normalized.substring(6);
        }
        return "";
    }

    private String soloDigitos(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String rellenarIzquierda(long value, int length) {
        String text = Long.toString(Math.max(0L, value));
        if (text.length() >= length) {
            return text;
        }
        return "0".repeat(length - text.length()) + text;
    }

    private ObjectNode construirFormatoTexto() {
        return construirFormatoTexto("extraccion_formato_ga", esquemaExtraccion());
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

    private ObjectNode esquemaContratoCompacto() {
        ObjectNode schema = schemaBase("contrato", "vehiculo", "transmitente", "adquirente", "control");
        ObjectNode props = (ObjectNode) schema.get("properties");
        props.set("contrato", obj("fechaContrato", "valorDeclarado", "tipoTransferencia", "observaciones"));
        props.set("vehiculo", obj("matricula", "marca", "modelo", "numeroBastidor"));
        props.set("transmitente", personaCompacta(true));
        props.set("adquirente", personaCompacta(true));
        props.set("control", controlSchema());
        return schema;
    }

    private ObjectNode esquemaDniCompacto() {
        ObjectNode schema = schemaBase("personas", "control");
        ObjectNode props = (ObjectNode) schema.get("properties");
        props.set("personas", arrayOf(personaDni()));
        props.set("control", controlSchema());
        return schema;
    }

    private ObjectNode esquemaVehiculoCompacto() {
        ObjectNode schema = schemaBase("vehiculo", "titularPermiso", "control");
        ObjectNode props = (ObjectNode) schema.get("properties");
        props.set("vehiculo", obj("matricula", "fechaMatriculacion", "fechaPrimeraMatriculacion", "marca", "modelo", "numeroBastidor", "cilindrada", "potencia", "carburante", "fechaItv", "servicioDestino", "claseVehiculo", "tipoVehiculo"));
        props.set("titularPermiso", personaCompacta(false));
        props.set("control", controlSchema());
        return schema;
    }

    private ObjectNode esquemaFiscalCompacto() {
        ObjectNode schema = schemaBase("impuestos", "acreditacion", "control");
        ObjectNode props = (ObjectNode) schema.get("properties");
        props.set("impuestos", obj("fechaDevengo", "fechaPrimeraMatriculacion", "valorDeclarado", "baseImponible", "cuotaTributaria", "totalIngresar", "codigoElectronicoTransferencia"));
        props.set("acreditacion", obj("modeloItp", "cetItp", "provinciaCet", "contratoCompraventa"));
        props.set("control", controlSchema());
        return schema;
    }

    private ObjectNode esquemaConsolidacionCompacto() {
        ObjectNode schema = schemaBase("vehiculo", "transmitente", "representanteTransmitente", "adquirente", "impuestos", "acreditacion", "control");
        ObjectNode props = (ObjectNode) schema.get("properties");
        props.set("vehiculo", obj("matricula", "fechaMatriculacion", "fechaPrimeraMatriculacion", "marca", "modelo", "numeroBastidor", "cilindrada", "potencia", "carburante", "fechaItv", "servicioDestino", "claseVehiculo", "tipoVehiculo"));
        props.set("transmitente", personaCompacta(true));
        props.set("representanteTransmitente", personaCompacta(true));
        props.set("adquirente", personaCompacta(true));
        props.set("impuestos", obj("fechaDevengo", "valorDeclarado", "baseImponible", "cuotaTributaria", "totalIngresar", "codigoElectronicoTransferencia"));
        props.set("acreditacion", obj("contratoCompraventa", "fechaContrato", "factura", "modeloItp", "cetItp", "provinciaCet"));
        props.set("control", controlSchema());
        return schema;
    }

    private ObjectNode schemaBase(String... required) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("required", array(required));
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    private ObjectNode personaCompacta(boolean conDireccion) {
        ObjectNode persona = obj("dni", "nombre", "apellido1", "apellido2", "nombreCompleto", "sexo", "fechaNacimiento");
        if (conDireccion) {
            ((ObjectNode) persona.get("properties")).set("direccion", obj("siglas", "nombreVia", "numero", "piso", "puerta", "municipio", "pueblo", "provincia", "codigoPostal", "pais"));
            ((ArrayNode) persona.get("required")).add("direccion");
        }
        return persona;
    }

    private ObjectNode personaDni() {
        ObjectNode persona = personaCompacta(true);
        ((ObjectNode) persona.get("properties")).set("rolSugerido", campoExtraido());
        ((ObjectNode) persona.get("properties")).set("documentoId", campoExtraido());
        ((ObjectNode) persona.get("properties")).set("documentoNombre", campoExtraido());
        ((ArrayNode) persona.get("required")).add("rolSugerido");
        ((ArrayNode) persona.get("required")).add("documentoId");
        ((ArrayNode) persona.get("required")).add("documentoNombre");
        return persona;
    }

    private ObjectNode controlSchema() {
        ObjectNode control = objectMapper.createObjectNode();
        control.put("type", "object");
        control.put("additionalProperties", false);
        control.set("required", array("camposDudosos", "confianzaGlobal", "requiereRevisionHumana", "observaciones"));
        ObjectNode controlProps = objectMapper.createObjectNode();
        controlProps.set("camposDudosos", arraySchema());
        controlProps.set("confianzaGlobal", numberOrNull());
        controlProps.set("requiereRevisionHumana", booleanSchema());
        controlProps.set("observaciones", stringOrNull());
        control.set("properties", controlProps);
        return control;
    }

    private ObjectNode arrayOf(ObjectNode itemSchema) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", itemSchema);
        return node;
    }

    private ObjectNode esquemaExtraccion() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("required", array("transmision", "vehiculo", "transmitente", "adquirente", "representanteTransmitente", "presentador", "impuestos", "acreditacion", "control"));
        ObjectNode props = objectMapper.createObjectNode();
        props.set("transmision", obj("fechaPresentacion", "observaciones", "tipoTransferencia", "notificacionPrevia"));
        props.set("vehiculo", obj("matricula", "fechaMatriculacion", "fechaPrimeraMatriculacion", "marca", "modelo", "numeroBastidor", "cilindrada", "potencia", "carburante", "fechaItv", "servicioDestino", "claseVehiculo", "tipoVehiculo"));
        props.set("transmitente", persona(true));
        props.set("adquirente", persona(true));
        props.set("representanteTransmitente", persona(true));
        props.set("presentador", persona(false));
        props.set("impuestos", obj("fechaDevengo", "valorDeclarado", "baseImponible", "cuotaTributaria", "totalIngresar", "sujetoIgic", "vendedorHabitual", "codigoElectronicoTransferencia"));
        props.set("acreditacion", obj("solicitud", "contratoCompraventa", "factura", "fechaContrato", "fechaFactura", "modeloItp", "exencionItp", "noSujecionItp", "cetItp"));
        props.set("control", controlSchema());
        schema.set("properties", props);
        return schema;
    }

    private ObjectNode persona(boolean conDireccion) {
        ObjectNode persona = obj("dni", "razonSocial", "apellido1", "apellido2", "nombre", "sexo", "fechaNacimiento", "autonomo", "iae", "esCompraventa", "cambioDomicilio");
        if (conDireccion) {
            ((ObjectNode) persona.get("properties")).set("direccion", obj("siglas", "nombreVia", "numero", "piso", "puerta", "municipio", "pueblo", "provincia", "codigoPostal", "pais"));
            ((ArrayNode) persona.get("required")).add("direccion");
        }
        return persona;
    }

    private ObjectNode obj(String... fields) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        node.set("required", array(fields));
        ObjectNode props = objectMapper.createObjectNode();
        for (String field : fields) {
            props.set(field, campoExtraido());
        }
        node.set("properties", props);
        return node;
    }

    private ObjectNode campoExtraido() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        node.set("required", array("valor", "confianza", "fuente", "observacion"));
        ObjectNode props = objectMapper.createObjectNode();
        props.set("valor", stringOrNull());
        props.set("confianza", numberOrNull());
        props.set("fuente", stringOrNull());
        props.set("observacion", stringOrNull());
        node.set("properties", props);
        return node;
    }

    private ObjectNode stringOrNull() {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode type = objectMapper.createArrayNode();
        type.add("string");
        type.add("null");
        node.set("type", type);
        return node;
    }

    private ObjectNode numberOrNull() {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode type = objectMapper.createArrayNode();
        type.add("number");
        type.add("null");
        node.set("type", type);
        return node;
    }

    private ObjectNode booleanSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "boolean");
        return node;
    }

    private ObjectNode arraySchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", stringOrNull());
        return node;
    }

    private ArrayNode array(String... values) {
        ArrayNode node = objectMapper.createArrayNode();
        for (String value : values) {
            node.add(value);
        }
        return node;
    }

    private String promptExtraccion() {
        return """
                Extrae datos para preparar un XML FORMATO_GA de transmision de vehiculo.
                Usa solo informacion visible en los documentos aportados.
                Cada campo extraido debe devolver valor, confianza, fuente y observacion.
                En fuente indica el documento donde se ha visto el dato, por ejemplo PERMISO_CIRCULACION, FICHA_TECNICA, CONTRATO_COMPRAVENTA, DNI o MODELO_620.
                Usa confianza entre 0 y 1 para cada campo. Si un dato no aparece con claridad, devuelve valor null, confianza baja y anade el campo a camposDudosos.
                En observacion explica brevemente la duda o el criterio usado; si no hay duda, puede ser null.
                Normaliza fechas como dd/MM/yyyy cuando sea posible.
                No uses el ano actual ni la fecha del sistema para completar fechas manuscritas dudosas.
                No infieras sexo, pais, autonomo, esCompraventa, vendedorHabitual, sujetoIgic, noSujecionItp ni exencionItp salvo que aparezcan explicitamente en el documento.
                Para campos booleanos o equivalentes, usa Si/No solo si hay marca, texto o evidencia documental directa; si no, devuelve valor null.
                Si la fuente documental no existe o no contiene el dato, fuente debe ser null.
                No inventes codigos administrativos, tasas, datos de gestoria ni valores que dependan de configuracion interna.
                El adquirente es el comprador o nuevo titular. El transmitente es el vendedor o titular anterior.
                """;
    }

    private String promptContrato() {
        return """
                FORMATO_GA bloque contrato/factura. Devuelve solo el schema.
                Cada campo: valor, confianza 0..1, fuente, observacion.
                Fuente: CONTRATO_COMPRAVENTA o FACTURA. Fecha dd/MM/yyyy.
                Extrae solo vendedor=transmitente, comprador=adquirente, fecha, precio, matricula, marca/modelo/bastidor si aparecen.
                Si una persona aparece como "APELLIDOS, NOMBRE", respeta la coma: antes de la coma son apellidos y despues de la coma es nombre.
                No infieras sexo, pais, autonomo, compraventa, impuestos ni datos tecnicos no visibles.
                Si falta o es dudoso: valor null, confianza baja, campo en control.camposDudosos.
                """;
    }

    private String promptDni() {
        return """
                FORMATO_GA bloque DNI/CIF/mandato. Devuelve solo personas visibles en el schema.
                Cada campo: valor, confianza 0..1, fuente DNI/CIF/MANDATO, observacion.
                Usa imagen DNI preprocesada y tambien documentos CIF, MANDATO o MANDATO_REPRESENTACION si existen.
                Si se aporta EXPEDIENTE_COMPLETO como apoyo, busca dentro del PDF documentos de identidad visibles del comprador/adquirente antes de reutilizar documentos historicos del cliente.
                Extrae todas las personas visibles: una persona por cada DNI/CIF/anverso-reverso o representante identificado en mandato.
                En documentoId indica el id numerico del documento origen cuando aparezca en la nota previa a la imagen o archivo; en documentoNombre indica su nombre.
                No te quedes con el primer documento si hay varios. Extrae documento, nombre, apellidos, sexo, nacimiento, domicilio, numero, codigo postal, municipio y localidad/pueblo si se leen.
                En DNI/NIE/TIE y MRZ, diferencia apellidos y nombre: en MRZ el separador "<<" divide apellidos antes y nombre despues; no cambies el orden de campos.
                rolSugerido debe ser REPRESENTANTE_TRANSMITENTE, ADMINISTRADOR_TRANSMITENTE o APODERADO_TRANSMITENTE solo si el documento lo indica o lo vincula claramente con la empresa transmitente; si no, null.
                No extraigas vehiculo, contrato ni impuestos. No inventes segunda persona.
                Si falta o es dudoso: valor null, confianza baja, campo en control.camposDudosos.
                """;
    }

    private String promptVehiculo() {
        return """
                FORMATO_GA bloque vehiculo. Usa solo PERMISO_CIRCULACION, FICHA_TECNICA o INFORME_DGT. Devuelve solo el schema.
                Cada campo: valor, confianza 0..1, fuente, observacion breve.
                Extrae matricula, bastidor, marca, modelo, fechas, cilindrada, potencia, carburante, ITV, servicio, clase/tipo.
                No confundas potencia con cilindrada, masa, tara, plazas o cotas; si duda, null.
                titularPermiso solo si aparece en permiso. No extraigas comprador, impuestos ni contrato.
                """;
    }

    private String promptFiscal() {
        return """
                FORMATO_GA bloque fiscal. Usa solo MODELO_620. Devuelve solo el schema.
                Cada campo: valor, confianza 0..1, fuente MODELO_620, observacion breve.
                Extrae fecha devengo, fecha de primera matriculacion si aparece, valor declarado, base, cuota, total y modelo ITP.
                CET/codigo electronico y provincia CET deben extraerse si aparecen, pero por defecto se dejan vacios al generar XML salvo confirmacion de 620 ya dado de alta.
                No extraigas personas, DNI, contrato ni otros datos tecnicos de vehiculo salvo que sean necesarios para distinguir el expediente.
                Si falta o es dudoso: valor null, confianza baja, campo en control.camposDudosos.
                """;
    }

    private String promptConsolidacion(String datosJson) {
        return """
                Consolida FORMATO_GA desde JSON ya extraido. No inventes.
                Contrato fija roles: vendedor=transmitente, comprador=adquirente.
                Si un DNI/CIF coincide con dni/nie de una parte, DNI/CIF manda en nombre, apellidos, sexo, fecha de nacimiento y domicilio de esa parte.
                Si el transmitente es empresa y una persona aparece en DNI/CIF/MANDATO como representante, administrador o apoderado de esa empresa, ponla en representanteTransmitente con su domicilio.
                No copies el comprador ni el presentador como representanteTransmitente salvo evidencia documental expresa.
                Vehiculo manda en datos tecnicos. Contrato manda en roles, fecha y precio.
                Fecha de nacimiento y domicilio son campos criticos: no los inventes ni uses placeholders; si faltan, marca revision humana.
                Fiscal/MODELO_620 manda en impuestos y modelo ITP. Si el 620 aporta fecha de primera matriculacion, usala tambien como fecha de matriculacion del vehiculo.
                Si hay discrepancia, conserva fuente fuerte y marca dudoso. No completes sexo, pais, autonomo, compraventa, IGIC ni exenciones sin evidencia.
                Devuelve solo el schema con valor/confianza/fuente/observacion y control breve.
                JSON:
                """ + datosJson;
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

    private Map<String, Object> extraerUso(JsonNode root) {
        JsonNode usage = root.get("usage");
        if (usage == null || usage.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(usage, Map.class);
    }

    private CosteEstimado estimarCoste(String modelo, int paginas) {
        Precio precio = precio(modelo);
        long inputMin = paginas * 1_500L + 5_000L;
        long inputMax = paginas * 3_000L + 8_000L;
        long outputEstimado = 3_000L;
        BigDecimal min = BigDecimal.valueOf(inputMin).multiply(precio.inputUsdPorMillon())
                .add(BigDecimal.valueOf(outputEstimado).multiply(precio.outputUsdPorMillon()))
                .divide(BigDecimal.valueOf(1_000_000L), 4, RoundingMode.HALF_UP);
        BigDecimal max = BigDecimal.valueOf(inputMax).multiply(precio.inputUsdPorMillon())
                .add(BigDecimal.valueOf(outputEstimado).multiply(precio.outputUsdPorMillon()))
                .divide(BigDecimal.valueOf(1_000_000L), 4, RoundingMode.HALF_UP);
        return new CosteEstimado(min, max);
    }

    private Precio precio(String modelo) {
        return switch (modelo) {
            case "gpt-5.4-mini" -> new Precio(new BigDecimal("0.75"), new BigDecimal("4.50"));
            case "gpt-5.5" -> new Precio(new BigDecimal("5.00"), new BigDecimal("30.00"));
            default -> new Precio(new BigDecimal("2.50"), new BigDecimal("15.00"));
        };
    }

    private String modeloNormalizado(String modelo) {
        if (modelo == null || modelo.isBlank()) {
            return openAiProperties.getModel();
        }
        return modelo.trim();
    }

    private int contarPaginas(Path ruta) throws IOException {
        try (PDDocument document = PDDocument.load(Files.readAllBytes(ruta))) {
            return document.getNumberOfPages();
        }
    }

    private Path carpetaUploads() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private static Map<TipoDocumento, Integer> prioridadTipo() {
        Map<TipoDocumento, Integer> prioridad = new EnumMap<>(TipoDocumento.class);
        prioridad.put(TipoDocumento.CAMBIO_TITULARIDAD, 1);
        prioridad.put(TipoDocumento.CONTRATO_COMPRAVENTA, 2);
        prioridad.put(TipoDocumento.FACTURA, 3);
        prioridad.put(TipoDocumento.PERMISO_CIRCULACION, 4);
        prioridad.put(TipoDocumento.FICHA_TECNICA, 5);
        prioridad.put(TipoDocumento.INFORME_DGT, 6);
        prioridad.put(TipoDocumento.DNI, 7);
        prioridad.put(TipoDocumento.CIF, 8);
        prioridad.put(TipoDocumento.MANDATO, 9);
        prioridad.put(TipoDocumento.MANDATO_REPRESENTACION, 10);
        prioridad.put(TipoDocumento.AUTORIZACION_SERAFIN, 11);
        prioridad.put(TipoDocumento.MODELO_620, 12);
        return prioridad;
    }

    private record DocumentoSeleccionado(Documento documento, Path ruta, int paginas, long bytes) {
    }

    private record DocumentacionExtraccion(List<String> bloqueosDocumentales) {
        private boolean bloqueada() {
            return bloqueosDocumentales != null && !bloqueosDocumentales.isEmpty();
        }
    }

    private record ImagenProcesada(String nombre, byte[] bytes) {
    }

    private record JobContexto(Long expedienteId, String modelo, Long adminId) {
    }

    private record DireccionCartoCiudadConsulta(
            String rol,
            String query,
            String nombreVia,
            String numero,
            String municipio,
            String provincia,
            String pueblo
    ) {
    }

    private record CartoCiudadCandidate(
            String postalCode,
            String municipio,
            String provincia,
            String poblacion,
            String address,
            double confianza
    ) {
    }

    private record RevisionVista(
            String datosValidadosJson,
            Double confianzaGlobal,
            boolean requiereRevisionHumana
    ) {
    }

    private record CosteEstimado(BigDecimal min, BigDecimal max) {
    }

    private record Precio(BigDecimal inputUsdPorMillon, BigDecimal outputUsdPorMillon) {
    }

    private record RespuestaOpenAi(String resultadoJson, Map<String, Object> uso) {
    }
}
