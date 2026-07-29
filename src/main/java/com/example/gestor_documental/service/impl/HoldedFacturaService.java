package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.factura.*;
import com.example.gestor_documental.enums.*;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class HoldedFacturaService {
    private static final long MAX_COMPROBANTE = 10L * 1024 * 1024;
    private static final Set<String> TIPOS_COMPROBANTE = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private final FacturaHoldedRepository facturaRepository;
    private final ClienteRepository clienteRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final FacturaExpedienteRepository facturaExpedienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final HoldedWebhookEventoRepository webhookRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${app.holded.enabled:false}") private boolean enabled;
    @Value("${app.holded.api-token:}") private String apiToken;
    @Value("${app.holded.api-base-url:https://api.holded.com/api/v2}") private String apiBaseUrl;
    @Value("${app.holded.webhook-secret:}") private String webhookSecret;
    @Value("${app.holded.sync-year:2026}") private int syncYear;
    @Value("${app.holded.connect-timeout-seconds:10}") private int connectTimeoutSeconds;
    @Value("${app.holded.read-timeout-seconds:30}") private int readTimeoutSeconds;
    @Value("${app.upload.dir:uploads}") private String uploadDir;

    @Transactional
    public SincronizacionHoldedResponse sincronizarTodo() {
        validarConfiguracion();
        List<Cliente> clientesLocales = clienteRepository.findAll();
        Map<String, Cliente> clientesPorNif = new HashMap<>();
        for (Cliente cliente : clientesLocales) {
            registrarClientePorNif(clientesPorNif, cliente);
        }

        Map<String, JsonNode> contactos = cargarContactosClientes(clientesLocales);
        for (JsonNode contacto : contactos.values()) {
            Cliente cliente = buscarClientePorNif(
                    clientesPorNif,
                    texto(contacto, "vatnumber", "vat_number", "vatNumber", "tax_id", "taxId", "code"));
            String contactId = texto(contacto, "id");
            if (cliente != null && contactId != null && cliente.getHoldedContactId() == null) {
                cliente.setHoldedContactId(contactId);
            }
        }

        Map<String, Cliente> clientesPorContacto = new LinkedHashMap<>();
        for (Cliente cliente : clientesLocales) {
            if (cliente.getHoldedContactId() != null && !cliente.getHoldedContactId().isBlank()) {
                clientesPorContacto.put(cliente.getHoldedContactId(), cliente);
            }
        }

        int recibidas = 0, actualizadas = 0, sinCliente = 0;
        for (Map.Entry<String, Cliente> entrada : clientesPorContacto.entrySet()) {
            String contactId = entrada.getKey();
            String cursor = null;
            do {
                LocalDate desde = LocalDate.of(syncYear, 1, 1);
                LocalDate hasta = LocalDate.of(syncYear, 12, 31);
                String path = "/invoices?limit=100&contact_id=" + codificar(contactId)
                        + "&start_date=" + desde + "&end_date=" + hasta + parametroCursor(cursor);
                JsonNode body = getJson(path);
                JsonNode items = body.path("items");
                if (items.isArray() && !items.isEmpty()) {
                    List<String> ids = new ArrayList<>();
                    items.forEach(item -> { String itemId = texto(item, "id"); if (itemId != null) ids.add(itemId); });
                    Map<String, FacturaHolded> existentes = new HashMap<>();
                    facturaRepository.findByHoldedInvoiceIdIn(ids).forEach(f -> existentes.put(f.getHoldedInvoiceId(), f));
                    List<FacturaHolded> lote = new ArrayList<>();
                    for (JsonNode item : items) {
                        recibidas++;
                        FacturaHolded factura = mapear(item, contactos.get(contactId), existentes.get(texto(item, "id")), clientesPorContacto, clientesPorNif);
                        if (factura.getFechaEmision() != null && factura.getFechaEmision().getYear() == syncYear) {
                            lote.add(factura);
                            actualizadas++;
                            if (factura.getCliente() == null) sinCliente++;
                        }
                    }
                    facturaRepository.saveAll(lote);
                }
                cursor = texto(body, "cursor");
            } while (cursor != null && !cursor.isBlank());
        }
        return new SincronizacionHoldedResponse(recibidas, actualizadas, sinCliente);
    }
    private Map<String, JsonNode> cargarContactosClientes(List<Cliente> clientes) {
        Map<String, JsonNode> contactos = new HashMap<>();
        Set<String> nifsConsultados = new HashSet<>();
        for (Cliente cliente : clientes) {
            if (cliente.getHoldedContactId() != null && !cliente.getHoldedContactId().isBlank()) continue;
            String nif = cliente.getNif();
            if (nif == null || nif.isBlank()) continue;
            Set<String> candidatos = new LinkedHashSet<>();
            candidatos.add(nif.trim());
            candidatos.add(normalizarNif(nif));
            for (String candidato : candidatos) {
                if (candidato == null || candidato.isBlank() || !nifsConsultados.add(candidato)) continue;
                JsonNode items = getJson("/contacts?limit=100&code=" + codificar(candidato)).path("items");
                if (items.isArray()) for (JsonNode contacto : items) {
                    String id = texto(contacto, "id");
                    if (id != null) contactos.put(id, contacto);
                }
            }
        }
        return contactos;
    }
    private String parametroCursor(String cursor) {
        return cursor == null || cursor.isBlank() ? "" : "&cursor=" + codificar(cursor);
    }

    private String codificar(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Scheduled(cron = "${app.holded.sync-cron:0 15 */4 * * *}", zone = "${app.holded.sync-zone:Atlantic/Canary}")
    public void sincronizacionSeguridad() {
        if (enabled && apiToken != null && !apiToken.isBlank()) sincronizarTodo();
    }

    @Transactional
    public void recibirWebhook(String eventId, String signature, byte[] payload) {
        validarFirma(signature, payload);
        String id = eventId;
        try {
            JsonNode evento = objectMapper.readTree(payload);
            if (id == null || id.isBlank()) id = texto(evento, "id", "eventId", "event_id");
            if (id == null || id.isBlank()) id = hex(sha256(payload));
            if (webhookRepository.existsByEventId(id)) return;
            HoldedWebhookEvento registro = new HoldedWebhookEvento();
            registro.setEventId(id); registro.setRecibidoEn(LocalDateTime.now()); registro.setProcesado(false);
            webhookRepository.saveAndFlush(registro);
            String invoiceId = texto(evento.path("data"), "invoiceId", "invoice_id", "documentId", "document_id", "id");
            if (invoiceId == null) invoiceId = texto(evento, "invoiceId", "invoice_id", "documentId", "document_id");
            // El webhook solo dispara una relectura: nunca se confia en sus importes o estados.
            sincronizarTodo();
            registro.setProcesado(true);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook de Holded no valido");
        }
    }

    @Transactional
    public PagedResponse<FacturaHoldedResponse> listar(Usuario usuario, String busqueda, EstadoFacturaHolded estado, LocalDate desde, LocalDate hasta, int pagina, int tamanio) {
        Set<Long> clientes = clientesPermitidos(usuario);
        Specification<FacturaHolded> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (usuario.getRolUsuario() != RolUsuario.ADMIN || usuario.getCliente() != null) ps.add(root.get("cliente").get("id").in(clientes));
            if (estado != null) ps.add(cb.equal(root.get("estado"), estado));
            if (desde != null) ps.add(cb.greaterThanOrEqualTo(root.get("fechaEmision"), desde));
            if (hasta != null) ps.add(cb.lessThanOrEqualTo(root.get("fechaEmision"), hasta));
            if (busqueda != null && !busqueda.isBlank()) {
                String like = "%" + busqueda.trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("numero")), like), cb.like(cb.lower(root.get("contactoNombre")), like), cb.like(cb.lower(root.get("contactoNif")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<FacturaHolded> page = facturaRepository.findAll(spec, PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(tamanio, 100)), Sort.by(Sort.Direction.DESC, "fechaEmision", "id")));
        page.getContent().forEach(this::repararTotalDesdePdf);
        return PagedResponse.of(page.map(this::mapFactura));
    }

    @Transactional(readOnly = true)
    public byte[] descargarPdf(Long id, Usuario usuario) {
        FacturaHolded factura = requireFactura(id, usuario);
        if (factura.getArchivoFactura() == null || factura.getArchivoFactura().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La factura todavia no tiene un PDF local asociado");
        }
        Path base = Paths.get(uploadDir, "facturas").toAbsolutePath().normalize();
        Path archivo = base.resolve(factura.getArchivoFactura()).normalize();
        if (!archivo.startsWith(base) || !Files.isRegularFile(archivo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF de factura no disponible");
        }
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el PDF de la factura", e);
        }
    }

    @Transactional(readOnly = true)
    public void descargarZip(List<Long> ids, Usuario usuario, OutputStream out) throws IOException {
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona entre 1 y 100 facturas");
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            Set<String> usados = new HashSet<>();
            for (Long id : ids) {
                FacturaHolded f = requireFactura(id, usuario);
                String nombre = nombreSeguro((f.getNumero() == null ? "factura-" + id : f.getNumero()) + ".pdf");
                if (!usados.add(nombre)) nombre = id + "-" + nombre;
                zip.putNextEntry(new ZipEntry(nombre)); zip.write(descargarPdf(id, usuario)); zip.closeEntry();
            }
        }
    }

    @Transactional
    public ComprobantePagoResponse aportarComprobante(Long facturaId, MultipartFile archivo, Usuario usuario) throws IOException {
        FacturaHolded factura = requireFactura(facturaId, usuario);
        validarComprobante(archivo);
        Path base = Paths.get(uploadDir, "comprobantes-pago").toAbsolutePath().normalize(); Files.createDirectories(base);
        String extension = extension(archivo.getOriginalFilename()); String almacenado = UUID.randomUUID() + extension;
        Path destino = base.resolve(almacenado).normalize(); if (!destino.startsWith(base)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de archivo no valido");
        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        ComprobantePago c = new ComprobantePago(); c.setFactura(factura); c.setAportadoPor(usuario); c.setNombreArchivo(almacenado);
        c.setNombreOriginal(nombreSeguro(archivo.getOriginalFilename())); c.setContentType(archivo.getContentType()); c.setTamano(archivo.getSize());
        return mapComprobante(comprobanteRepository.save(c));
    }

    @Transactional
    public ComprobantePagoResponse revisarComprobante(Long id, RevisarComprobanteRequest request, Usuario admin) {
        if (admin.getRolUsuario() != RolUsuario.ADMIN) throw new AccesoDenegadoException("Solo administracion puede revisar comprobantes");
        if (request == null || request.estado() == null || request.estado() == EstadoComprobantePago.PENDIENTE_VERIFICACION) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona verificado o descartado");
        ComprobantePago c = comprobanteRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
        c.setEstado(request.estado()); c.setObservaciones(request.observaciones()); c.setRevisadoEn(LocalDateTime.now()); return mapComprobante(c);
    }

    @Transactional(readOnly = true)
    public Download comprobante(Long id, Usuario usuario) throws IOException {
        ComprobantePago c = comprobanteRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
        validarAcceso(c.getFactura(), usuario);
        Path base = Paths.get(uploadDir, "comprobantes-pago").toAbsolutePath().normalize(); Path path = base.resolve(c.getNombreArchivo()).normalize();
        if (!path.startsWith(base) || !Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado");
        return new Download(Files.readAllBytes(path), c.getContentType(), c.getNombreOriginal());
    }

    private FacturaHolded mapear(JsonNode n, JsonNode contacto, FacturaHolded existente, Map<String, Cliente> clientesPorContacto, Map<String, Cliente> clientesPorNif) {
        String id = texto(n, "id");
        if (id == null || id.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Holded devolvio una factura sin identificador");
        FacturaHolded f = existente != null ? existente : new FacturaHolded(); f.setHoldedInvoiceId(id);
        String contactId = texto(n, "contact_id");
        f.setHoldedContactId(contactId); f.setNumero(texto(n, "document_number")); f.setContactoNombre(texto(n, "contact_name"));
        String nif = contacto == null
                ? texto(n, "contact_vatnumber", "contact_vat_number", "contactVatNumber",
                        "contact_tax_id", "contactTaxId", "vatnumber", "vat_number")
                : texto(contacto, "vatnumber", "vat_number", "vatNumber", "tax_id", "taxId", "code");
        String nifNormalizado = normalizarNif(nif);
        Cliente cliente = contactId == null ? null : clientesPorContacto.get(contactId);
        if (cliente == null) cliente = buscarClientePorNif(clientesPorNif, nif);
        if (cliente != null && contactId != null && cliente.getHoldedContactId() == null) {
            cliente.setHoldedContactId(contactId); clientesPorContacto.put(contactId, cliente);
        }
        f.setContactoNif(nifNormalizado); f.setCliente(cliente); f.setFechaEmision(fecha(n, "date")); f.setFechaVencimiento(fecha(n, "due_date"));
        f.setTotal(decimal(n, "total")); f.setImportePagado(decimal(n, "payments_total")); f.setMoneda(Optional.ofNullable(texto(n, "currency")).orElse("EUR"));
        f.setEstado(estado(n, f.getTotal(), f.getImportePagado())); f.setActualizadaHolded(fechaHora(n, "updated_at")); f.setSincronizadaEn(LocalDateTime.now()); return f;
    }

    private Set<Long> clientesPermitidos(Usuario u) {
        if (u.getRolUsuario() == RolUsuario.ADMIN) return u.getCliente() == null ? Set.of() : Set.of(u.getCliente().getId());
        Set<Long> ids = new HashSet<>(); if (u.getCliente() != null) ids.add(u.getCliente().getId()); u.getClientesAutorizados().forEach(c -> ids.add(c.getId())); return ids;
    }
    private FacturaHolded requireFactura(Long id, Usuario u) { FacturaHolded f = facturaRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Factura no encontrada")); validarAcceso(f, u); return f; }
    private void validarAcceso(FacturaHolded f, Usuario u) { if (u.getRolUsuario() == RolUsuario.ADMIN && u.getCliente() == null) return; if (f.getCliente() == null || !clientesPermitidos(u).contains(f.getCliente().getId())) throw new AccesoDenegadoException("No tienes acceso a esta factura"); }
    @Transactional
    public FacturaDetalleResponse detalle(Long id, Usuario usuario) {
        FacturaHolded factura = requireFactura(id, usuario);
        repararTotalDesdePdf(factura);
        boolean administracion = usuario.getRolUsuario() == RolUsuario.ADMIN;
        List<FacturaVinculacionResponse> vinculaciones = facturaExpedienteRepository.findByFacturaIdOrderByIdAsc(id).stream()
                .map(administracion ? this::mapVinculacion : this::mapVinculacionCliente)
                .toList();
        return new FacturaDetalleResponse(
                administracion ? mapFactura(factura) : mapFacturaCliente(factura),
                vinculaciones,
                administracion ? lineasPendientes(factura) : List.of());
    }

    private List<LineaFacturaPendienteResponse> lineasPendientes(FacturaHolded factura) {
        if (factura.getDetalleLineasPendientes() == null || factura.getDetalleLineasPendientes().isBlank()) return List.of();
        String[] detalles = factura.getDetalleLineasPendientes().split(";\\s*");
        List<LineaFacturaPendienteResponse> resultado = new ArrayList<>();
        for (int i = 0; i < detalles.length; i++) {
            String[] partes = detalles[i].trim().split("\\s+-\\s+", 2);
            String documento = partes.length > 0 && !partes[0].isBlank() ? partes[0] : "Linea " + (i + 1);
            String matricula = partes.length > 1 && !"sin matricula".equalsIgnoreCase(partes[1]) ? partes[1] : null;
            resultado.add(new LineaFacturaPendienteResponse(i, documento, matricula));
        }
        return resultado;
    }

    @Transactional
    public FacturaDetalleResponse asignarLineaPendiente(Long facturaId, int indice, Long expedienteId, Usuario admin) {
        if (admin.getRolUsuario() != RolUsuario.ADMIN) throw new AccesoDenegadoException("Solo administracion puede asignar lineas pendientes");
        FacturaHolded factura = requireFactura(facturaId, admin);
        List<LineaFacturaPendienteResponse> pendientes = new ArrayList<>(lineasPendientes(factura));
        if (indice < 0 || indice >= pendientes.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La linea pendiente ya no existe");
        Expediente expediente = expedienteRepository.findById(expedienteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado"));
        if (expediente.getCliente() == null || factura.getCliente() == null || !expediente.getCliente().getId().equals(factura.getCliente().getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El expediente debe pertenecer al cliente de la factura");
        facturaExpedienteRepository.findByExpedienteId(expedienteId).ifPresent(otra -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "El expediente ya esta vinculado a una factura"); });
        LineaFacturaPendienteResponse linea = pendientes.remove(indice);
        FacturaExpediente vinculacion = new FacturaExpediente();
        vinculacion.setFactura(factura); vinculacion.setExpediente(expediente); vinculacion.setEstado(EstadoVinculacionFactura.CONFIRMADA);
        vinculacion.setMatriculaDetectada(linea.matricula()); vinculacion.setConfianza(0);
        vinculacion.setMotivoRevision("Asignacion manual de linea pendiente " + linea.documento()); vinculacion.setConfirmadoEn(LocalDateTime.now());
        facturaExpedienteRepository.save(vinculacion);
        factura.setLineasPendientesRevision(pendientes.size());
        factura.setDetalleLineasPendientes(pendientes.isEmpty() ? null : pendientes.stream().map(item -> item.documento() + " - " + (item.matricula() != null ? item.matricula() : "sin matricula")).collect(java.util.stream.Collectors.joining("; ")));
        facturaRepository.save(factura);
        return detalle(facturaId, admin);
    }

    @Transactional
    public void eliminar(Long facturaId, Usuario admin) {
        if (admin.getRolUsuario() != RolUsuario.ADMIN) throw new AccesoDenegadoException("Solo administracion puede eliminar facturas");
        FacturaHolded factura = requireFactura(facturaId, admin);
        if (factura.getHoldedInvoiceId() == null || !factura.getHoldedInvoiceId().startsWith("LOCAL:")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Las facturas sincronizadas desde Holded no se pueden eliminar");
        if (comprobanteRepository.existsByFacturaId(facturaId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar una factura con comprobantes de pago");
        Path archivo = factura.getArchivoFactura() == null ? null : Paths.get(uploadDir, "facturas", factura.getArchivoFactura()).toAbsolutePath().normalize();
        facturaExpedienteRepository.deleteByFacturaId(facturaId);
        facturaRepository.delete(factura);
        if (archivo != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { try { Files.deleteIfExists(archivo); } catch (IOException ignored) { } }
            });
        }
    }
    private void repararTotalDesdePdf(FacturaHolded factura) {
        if (factura.getHoldedInvoiceId() == null || !factura.getHoldedInvoiceId().startsWith("LOCAL:") || factura.getArchivoFactura() == null) return;
        Path archivo = Paths.get(uploadDir, "facturas", factura.getArchivoFactura()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(archivo)) return;
        try (PDDocument pdf = PDDocument.load(archivo.toFile())) {
            PDFTextStripper extractor = new PDFTextStripper(); extractor.setSortByPosition(true);
            BigDecimal total = FacturaDocumentoAnalisisService.extraerTotalTexto(extractor.getText(pdf));
            if (total != null && (factura.getTotal() == null || total.compareTo(factura.getTotal()) != 0)) { factura.setTotal(total); facturaRepository.save(factura); }
        } catch (IOException ignored) {
            // El detalle sigue disponible aunque el PDF historico no pueda releerse.
        }
    }
    @Transactional
    public FacturaDetalleResponse corregirVinculacion(Long facturaId, Long vinculacionId, Long expedienteId, Usuario admin) {
        if (admin.getRolUsuario() != RolUsuario.ADMIN) throw new AccesoDenegadoException("Solo administracion puede corregir vinculaciones");
        FacturaHolded factura = requireFactura(facturaId, admin);
        FacturaExpediente vinculacion = facturaExpedienteRepository.findById(vinculacionId)
                .filter(v -> v.getFactura() != null && facturaId.equals(v.getFactura().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculacion no encontrada"));
        Expediente nuevo = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado"));
        if (nuevo.getCliente() == null || factura.getCliente() == null || !nuevo.getCliente().getId().equals(factura.getCliente().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El expediente debe pertenecer al cliente de la factura");
        }
        facturaExpedienteRepository.findByExpedienteId(expedienteId)
                .filter(otra -> !otra.getId().equals(vinculacionId))
                .ifPresent(otra -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "El expediente ya esta vinculado a otra factura"); });
        Long anterior = vinculacion.getExpediente().getId();
        vinculacion.setExpediente(nuevo);
        vinculacion.setEstado(EstadoVinculacionFactura.CONFIRMADA);
        vinculacion.setConfirmadoEn(LocalDateTime.now());
        vinculacion.setMotivoRevision("Correccion manual: expediente anterior #" + anterior);
        facturaExpedienteRepository.save(vinculacion);
        return detalle(facturaId, admin);
    }

    private FacturaVinculacionResponse mapVinculacion(FacturaExpediente v) {
        Expediente e = v.getExpediente();
        return new FacturaVinculacionResponse(v.getId(), e.getId(), e.getMatricula(),
                e.getCliente() != null ? e.getCliente().getNombre() : null,
                e.getEstadoExpediente() != null ? e.getEstadoExpediente().name() : null,
                v.getEstado().name(), v.getMatriculaDetectada(), v.getBastidorDetectado(),
                v.getCompradorIdentificadorDetectado(), v.getConfianza(), v.getMotivoRevision());
    }
    private FacturaVinculacionResponse mapVinculacionCliente(FacturaExpediente vinculacion) {
        Expediente expediente = vinculacion.getExpediente();
        return new FacturaVinculacionResponse(
                vinculacion.getId(),
                expediente.getId(),
                expediente.getMatricula(),
                expediente.getCliente() != null ? expediente.getCliente().getNombre() : null,
                expediente.getEstadoExpediente() != null ? expediente.getEstadoExpediente().name() : null,
                vinculacion.getEstado().name(),
                null, null, null, 0, null);
    }

    private FacturaHoldedResponse mapFacturaCliente(FacturaHolded f) {
        FacturaHoldedResponse factura = mapFactura(f);
        return new FacturaHoldedResponse(
                factura.id(), factura.numero(), factura.contactoNombre(), factura.contactoNif(),
                factura.fechaEmision(), factura.fechaVencimiento(), factura.total(), factura.importePagado(),
                factura.moneda(), factura.estado(), factura.sincronizadaEn(), 0, null, false,
                factura.comprobantes());
    }

    private FacturaHoldedResponse mapFactura(FacturaHolded f) { return new FacturaHoldedResponse(f.getId(), f.getNumero(), f.getContactoNombre(), f.getContactoNif(), f.getFechaEmision(), f.getFechaVencimiento(), f.getTotal(), f.getImportePagado(), f.getMoneda(), f.getEstado(), f.getSincronizadaEn(), f.getLineasPendientesRevision(), f.getDetalleLineasPendientes(), f.getHoldedInvoiceId() != null && f.getHoldedInvoiceId().startsWith("LOCAL:"), comprobanteRepository.findByFacturaIdOrderByCreadoEnDesc(f.getId()).stream().map(this::mapComprobante).toList()); }
    private ComprobantePagoResponse mapComprobante(ComprobantePago c) { return new ComprobantePagoResponse(c.getId(), c.getNombreOriginal(), c.getContentType(), c.getTamano(), c.getEstado(), c.getObservaciones(), c.getCreadoEn(), c.getRevisadoEn()); }
    private void validarConfiguracion() { if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integracion Holded desactivada"); if (apiToken == null || apiToken.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta configurar el token de Holded"); }
    private RestClient client() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)));
        return RestClient.builder().requestFactory(requestFactory).baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }
    private JsonNode getJson(String path) { byte[] bytes = getBytes(path); try { return objectMapper.readTree(bytes); } catch (IOException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta no valida de Holded"); } }
    private byte[] getBytes(String path) { try { return client().get().uri(path).retrieve().body(byte[].class); } catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar Holded", e); } }
    private void validarFirma(String signature, byte[] payload) { if (webhookSecret == null || webhookSecret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook de Holded no configurado"); try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); String expected=hex(mac.doFinal(payload)); String supplied=signature==null?"":signature.replaceFirst("^sha256=","").trim().toLowerCase(Locale.ROOT); if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII))) throw new AccesoDenegadoException("Firma de webhook no valida"); } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); } }
    private byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
    private String texto(JsonNode n, String... keys) { for(String k:keys){ JsonNode v=n.path(k); if(v.isValueNode()&&!v.asText().isBlank()) return v.asText(); } return null; }
    private BigDecimal decimal(JsonNode n,String...keys){String v=texto(n,keys);try{return v==null?BigDecimal.ZERO:new BigDecimal(v);}catch(Exception e){return BigDecimal.ZERO;}}
    private LocalDate fecha(JsonNode n,String...keys){String v=texto(n,keys);if(v==null)return null;try{if(v.matches("\\d+"))return Instant.ofEpochSecond(Long.parseLong(v)).atZone(ZoneId.systemDefault()).toLocalDate();return LocalDate.parse(v.substring(0,10));}catch(Exception e){return null;}}
    private LocalDateTime fechaHora(JsonNode n,String...keys){String v=texto(n,keys);if(v==null)return null;try{if(v.matches("\\d+"))return Instant.ofEpochSecond(Long.parseLong(v)).atZone(ZoneId.systemDefault()).toLocalDateTime();return OffsetDateTime.parse(v).toLocalDateTime();}catch(DateTimeParseException e){return null;}}
    private EstadoFacturaHolded estado(JsonNode n,BigDecimal total,BigDecimal paid){String s=Optional.ofNullable(texto(n,"status","state")).orElse("").toLowerCase(Locale.ROOT);if(s.contains("cancel")||s.contains("void")||s.contains("draft")&&n.path("cancelled").asBoolean())return EstadoFacturaHolded.ANULADA;if(s.contains("paid")&&!s.contains("partial")||total.signum()>0&&paid.compareTo(total)>=0)return EstadoFacturaHolded.PAGADA;if(s.contains("partial")||paid.signum()>0)return EstadoFacturaHolded.PARCIALMENTE_PAGADA;return EstadoFacturaHolded.PENDIENTE;}
    private void registrarClientePorNif(Map<String, Cliente> clientesPorNif, Cliente cliente) {
        String nif = normalizarNif(cliente.getNif());
        if (nif == null) return;
        clientesPorNif.putIfAbsent(nif, cliente);
        String sinPais = quitarPrefijoPais(nif);
        if (sinPais != null) clientesPorNif.putIfAbsent(sinPais, cliente);
    }
    private Cliente buscarClientePorNif(Map<String, Cliente> clientesPorNif, String value) {
        String nif = normalizarNif(value);
        if (nif == null) return null;
        Cliente cliente = clientesPorNif.get(nif);
        return cliente != null ? cliente : clientesPorNif.get(quitarPrefijoPais(nif));
    }
    private String quitarPrefijoPais(String nif) {
        if (nif != null && nif.startsWith("ES") && nif.length() > 9) return nif.substring(2);
        return nif;
    }
    private String normalizarNif(String value){if(value==null)return null;String nif=value.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);return nif.isBlank()?null:nif;}
    private void validarComprobante(MultipartFile a){if(a==null||a.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona un archivo");if(a.getSize()>MAX_COMPROBANTE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El comprobante supera 10 MB");if(a.getContentType()==null||!TIPOS_COMPROBANTE.contains(a.getContentType().toLowerCase(Locale.ROOT)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solo se admiten PDF, JPG, PNG o WebP");}
    private String extension(String n){if(n==null)return "";int i=n.lastIndexOf('.');return i<0?"":n.substring(i).replaceAll("[^A-Za-z0-9.]","").toLowerCase(Locale.ROOT);}
    private String nombreSeguro(String n){return Optional.ofNullable(n).orElse("archivo").replaceAll("[\\\\/:*?\"<>|]","_");}
    public record Download(byte[] bytes,String contentType,String filename) {}
}



