package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.factura.AnalisisFacturaArchivoResponse;
import com.example.gestor_documental.dto.factura.LineaFacturaDetectadaResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FacturaDocumentoAnalisisService {
    private static final long MAX_PDF = 20L * 1024 * 1024;
    private static final Pattern FECHA_FACTURA = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern NUMERO_FACTURA = Pattern.compile("(\\d{4}[/\\-]\\d+)");
    private static final Pattern DOCUMENTO = Pattern.compile("Documento:\\s*(\\d{4}/\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATRICULA = Pattern.compile("(?<![A-Z0-9])(\\d{4}[BCDFGHJKLMNPRSTVWXYZ]{3})(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASTIDOR = Pattern.compile("(?<![A-Z0-9])([A-HJ-NPR-Z0-9]{17})(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTIFICADOR = Pattern.compile("(?<![A-Z0-9])([ABCDEFGHJNPQRSUVW]\\d{7}[A-Z0-9]|[XYZ]\\d{7}[A-Z]|\\d{8}[A-Z])(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIN_DATOS_COMPRADOR = Pattern.compile("\\s(?:/|CONTIN.A|INFORMACI.N B.SICA|PROTECCI.N DE DATOS|GESTORIA ADMINISTRATIVA|RESPONSABLE DEL TRATAMIENTO)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<com.example.gestor_documental.enums.TipoDocumento> JUSTIFICANTES = Set.of(
            com.example.gestor_documental.enums.TipoDocumento.HUELLA_TRAMITE,
            com.example.gestor_documental.enums.TipoDocumento.COMPROBANTE_DGT,
            com.example.gestor_documental.enums.TipoDocumento.MODELO_620);

    private final FacturaHoldedRepository facturaRepository;
    private final ExpedienteRepository expedienteRepository;
    private final DocumentoRepository documentoRepository;
    private final FacturaExpedienteRepository vinculacionRepository;
    @Value("${app.upload.dir:uploads}") private String uploadDir;

    @Transactional(readOnly = true)
    public List<AnalisisFacturaArchivoResponse> analizar(List<MultipartFile> archivos) throws IOException {
        if (archivos == null || archivos.isEmpty() || archivos.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona entre 1 y 100 facturas PDF");
        }
        List<AnalisisFacturaArchivoResponse> resultado = new ArrayList<>();
        for (MultipartFile archivo : archivos) resultado.add(analizarUno(archivo));
        return resultado;
    }

    @Transactional
    public AnalisisFacturaArchivoResponse confirmar(Long facturaId,
                                                     com.example.gestor_documental.enums.ModalidadFacturacion modalidad,
                                                     LocalDate periodoDesde,
                                                     LocalDate periodoHasta,
                                                     List<Long> expedienteIds,
                                                     List<Long> expedienteIdsManuales,
                                                     List<Integer> lineasAsignadasManualmente,
                                                     MultipartFile archivo) throws IOException {
        if (modalidad == null || expedienteIds == null || expedienteIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan modalidad o expedientes");
        }
        if (modalidad != com.example.gestor_documental.enums.ModalidadFacturacion.POR_EXPEDIENTE && (periodoDesde == null || periodoHasta == null || periodoHasta.isBefore(periodoDesde))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote requiere un periodo valido");
        }
        AnalisisFacturaArchivoResponse analisis = analizarUno(archivo);
        if (facturaId != null && !facturaId.equals(analisis.facturaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El numero del PDF no coincide con la factura seleccionada");
        }
        Set<Long> confirmables = new HashSet<>();
        analisis.lineas().stream()
                .filter(l -> l.expedienteId() != null && ("COINCIDENCIA_SEGURA".equals(l.estado()) || l.confirmacionManualPermitida()))
                .forEach(l -> confirmables.add(l.expedienteId()));
        Set<Long> manuales = expedienteIdsManuales == null ? Set.of() : new HashSet<>(expedienteIdsManuales);
        if (!new HashSet<>(expedienteIds).containsAll(manuales)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las asignaciones manuales no pertenecen a la seleccion");
        }
        confirmables.addAll(manuales);
        if (!confirmables.containsAll(expedienteIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La seleccion contiene expedientes no confirmables");
        }
        List<Expediente> expedientes = new ArrayList<>();
        Cliente cliente = null;
        for (Long expedienteId : new LinkedHashSet<>(expedienteIds)) {
            Expediente expediente = expedienteRepository.findByIdForUpdate(expedienteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado"));
            if (expediente.getCliente() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El expediente no tiene cliente asignado");
            }
            if (cliente != null && !cliente.getId().equals(expediente.getCliente().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Todos los expedientes de una factura deben pertenecer al mismo cliente");
            }
            cliente = expediente.getCliente();
            expedientes.add(expediente);
        }
        FacturaHolded factura = facturaId == null
                ? crearFacturaLocal(analisis, cliente)
                : facturaRepository.findById(facturaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Factura no encontrada"));
        if (factura.getCliente() != null && !factura.getCliente().getId().equals(cliente.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La factura ya pertenece a otro cliente");
        }
        factura.setCliente(cliente);
        if (factura.getContactoNif() == null || factura.getContactoNif().isBlank()) factura.setContactoNif(cliente.getNif());
        if (factura.getContactoNombre() == null || factura.getContactoNombre().isBlank()) factura.setContactoNombre(cliente.getNombre());
        Path base = Paths.get(uploadDir, "facturas").toAbsolutePath().normalize();
        Files.createDirectories(base);
        String almacenado = UUID.randomUUID() + ".pdf";
        Path destino = base.resolve(almacenado).normalize();
        if (!destino.startsWith(base)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de factura no valida");
        Path temporal = Files.createTempFile(base, "factura-", ".tmp");
        registrarLimpiezaRollback(temporal, destino);
        try {
            Files.copy(archivo.getInputStream(), temporal, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporal);
            Files.deleteIfExists(destino);
            throw e;
        }
        factura.setModalidadFacturacion(modalidad);
        factura.setPeriodoDesde(periodoDesde);
        factura.setPeriodoHasta(periodoHasta);
        factura.setArchivoFactura(almacenado);
        factura.setArchivoFacturaOriginal(nombreSeguro(archivo.getOriginalFilename()));
        factura.setArchivoFacturaContentType("application/pdf");
        factura.setArchivoFacturaTamano(archivo.getSize());
        Set<Integer> indicesManuales = lineasAsignadasManualmente == null ? Set.of() : new HashSet<>(lineasAsignadasManualmente);
        if (indicesManuales.stream().anyMatch(i -> i == null || i < 0 || i >= analisis.lineas().size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La seleccion manual contiene lineas no validas");
        }
        Set<Long> idsSeleccionados = new HashSet<>(expedienteIds);
        List<LineaFacturaDetectadaResponse> pendientes = new ArrayList<>();
        for (int i = 0; i < analisis.lineas().size(); i++) {
            LineaFacturaDetectadaResponse linea = analisis.lineas().get(i);
            boolean asignada = indicesManuales.contains(i) || (linea.expedienteId() != null && idsSeleccionados.contains(linea.expedienteId()));
            if (!asignada) pendientes.add(linea);
        }
        factura.setLineasPendientesRevision(pendientes.size());
        factura.setDetalleLineasPendientes(pendientes.isEmpty() ? null : pendientes.stream()
                .map(l -> (l.documento() != null ? l.documento() : "Linea") + (l.matricula() != null ? " - " + l.matricula() : " - sin matricula"))
                .collect(java.util.stream.Collectors.joining("; ")));
        for (Expediente expediente : expedientes) {
            Long expedienteId = expediente.getId();
            Optional<FacturaExpediente> vinculacionExistente = vinculacionRepository.findByExpedienteId(expedienteId);
            if (vinculacionExistente.isPresent() && factura.getId().equals(vinculacionExistente.get().getFactura().getId())) continue;
            if (vinculacionRepository.existsActivoByExpedienteId(expedienteId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Un expediente ya pertenece a otra factura activa");
            FacturaExpediente vinculacion = new FacturaExpediente();
            vinculacion.setFactura(factura);
            vinculacion.setExpediente(expediente);
            analisis.lineas().stream().filter(l -> expedienteId.equals(l.expedienteId())).findFirst().ifPresent(l -> {
                vinculacion.setMatriculaDetectada(l.matricula()); vinculacion.setBastidorDetectado(l.bastidor());
                vinculacion.setCompradorIdentificadorDetectado(l.compradorIdentificador()); vinculacion.setConfianza(l.confianza());
            });
            vinculacion.setEstado(com.example.gestor_documental.enums.EstadoVinculacionFactura.CONFIRMADA);
            vinculacion.setConfirmadoEn(LocalDateTime.now());
            vinculacionRepository.save(vinculacion);
        }
        return analisis;
    }
    private FacturaHolded crearFacturaLocal(AnalisisFacturaArchivoResponse analisis, Cliente cliente) {
        if (analisis.numeroFactura() == null || analisis.numeroFactura().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo detectar el numero de la factura");
        }
        FacturaHolded factura = facturaRepository.findFirstByNumeroIgnoreCase(analisis.numeroFactura()).orElseGet(FacturaHolded::new);
        if (factura.getCliente() != null && !factura.getCliente().getId().equals(cliente.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El numero de factura ya esta asignado a otro cliente");
        }
        if (factura.getId() == null) {
            factura.setHoldedInvoiceId("LOCAL:" + UUID.randomUUID());
            factura.setNumero(analisis.numeroFactura());
            factura.setFechaEmision(analisis.fechaFactura());
            factura.setEstado(com.example.gestor_documental.enums.EstadoFacturaHolded.PENDIENTE);
            factura.setTotal(BigDecimal.ZERO);
            factura.setImportePagado(BigDecimal.ZERO);
            factura.setMoneda("EUR");
            factura.setSincronizadaEn(LocalDateTime.now());
        }
        factura.setCliente(cliente);
        factura.setContactoNif(cliente.getNif());
        factura.setContactoNombre(cliente.getNombre());
        return facturaRepository.save(factura);
    }
    private void registrarLimpiezaRollback(Path temporal, Path destino) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try {
                    Files.deleteIfExists(temporal);
                    Files.deleteIfExists(destino);
                } catch (IOException ignored) {
                    // El mantenimiento de archivos huerfanos puede reintentar la limpieza.
                }
            }
        });
    }

    private AnalisisFacturaArchivoResponse analizarUno(MultipartFile archivo) throws IOException {
        validarPdf(archivo);
        String texto;
        try (PDDocument pdf = PDDocument.load(archivo.getBytes())) {
            texto = new PDFTextStripper().getText(pdf).replace('\u00a0', ' ');
        }
        int primerDocumento = texto.toLowerCase(Locale.ROOT).indexOf("documento:");
        String cabeceraTexto = primerDocumento > 0 ? texto.substring(0, primerDocumento) : texto;
        Matcher fechaMatcher = FECHA_FACTURA.matcher(cabeceraTexto);
        Matcher numeroMatcher = NUMERO_FACTURA.matcher(cabeceraTexto);
        String numero = numeroMatcher.find() ? numeroMatcher.group(1).replace('-', '/') : null;
        LocalDate fecha = fechaMatcher.find() ? LocalDate.parse(fechaMatcher.group(1), DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;
        FacturaHolded factura = numero == null ? null : facturaRepository.findFirstByNumeroIgnoreCase(numero).orElse(null);
        List<LineaFacturaDetectadaResponse> lineas = extraerLineas(texto, factura, fecha, identificadores(cabeceraTexto));
        String estado = numero == null ? "NUMERO_NO_DETECTADO" : lineas.isEmpty() ? "SIN_EXPEDIENTES_DETECTADOS" : factura == null ? "FACTURA_LOCAL_NUEVA" : "PROPUESTA_LISTA";
        return new AnalisisFacturaArchivoResponse(nombreSeguro(archivo.getOriginalFilename()), numero, fecha, factura == null ? null : factura.getId(), estado, lineas);
    }

    private List<LineaFacturaDetectadaResponse> extraerLineas(String texto, FacturaHolded factura, LocalDate fechaFactura, Set<String> identificadoresCabecera) {
        Matcher documentos = DOCUMENTO.matcher(texto);
        List<Integer> inicios = new ArrayList<>();
        List<String> numeros = new ArrayList<>();
        while (documentos.find()) {
            inicios.add(documentos.start());
            numeros.add(documentos.group(1));
        }
        List<String> bloques = new ArrayList<>();
        Map<String, Integer> aparicionesPorBloque = new HashMap<>();
        for (int i = 0; i < inicios.size(); i++) {
            String bloque = texto.substring(inicios.get(i), i + 1 < inicios.size() ? inicios.get(i + 1) : texto.length());
            bloques.add(bloque);
            identificadores(bloque).forEach(id -> aparicionesPorBloque.merge(id, 1, Integer::sum));
        }
        Set<String> identificadoresEmisor = new HashSet<>(identificadoresCabecera);
        aparicionesPorBloque.forEach((id, apariciones) -> {
            if (apariciones > 1) identificadoresEmisor.add(id);
        });
        List<LineaFacturaDetectadaResponse> resultado = new ArrayList<>();
        for (int i = 0; i < bloques.size(); i++) {
            String bloque = bloques.get(i);
            String matricula = primero(MATRICULA, bloque);
            String bastidor = primero(BASTIDOR, bloque);
            String identificador = identificadorAntesDelBastidor(bloque, bastidor, identificadoresEmisor);
            String nombre = extraerNombre(bloque, identificador, bastidor);
            resultado.add(proponer(numeros.get(i), matricula, bastidor, identificador, nombre, factura, fechaFactura));
        }
        return resultado;
    }

    private Set<String> identificadores(String texto) {
        Set<String> resultado = new LinkedHashSet<>();
        Matcher matcher = IDENTIFICADOR.matcher(texto.toUpperCase(Locale.ROOT));
        while (matcher.find()) resultado.add(normalizar(matcher.group(1)));
        return resultado;
    }

    private LineaFacturaDetectadaResponse proponer(String documento, String matricula, String bastidor, String compradorId, String compradorNombre, FacturaHolded factura, LocalDate fechaFactura) {
        if (matricula == null) return linea(documento, null, bastidor, compradorId, compradorNombre, null, 0, "REVISION", "No se detecto matricula", false);
        List<Expediente> candidatos = factura != null && factura.getCliente() != null
                ? expedienteRepository.findByClienteIdAndMatriculaNormalizada(factura.getCliente().getId(), normalizar(matricula))
                : expedienteRepository.findByMatriculaNormalizada(normalizar(matricula));
        if (candidatos.size() != 1) return linea(documento, matricula, bastidor, compradorId, compradorNombre, null, 0, "REVISION", candidatos.isEmpty() ? "No existe expediente para la matricula" : "Hay varios expedientes posibles", false);
        Expediente expediente = candidatos.get(0);
        List<String> avisos = new ArrayList<>();
        List<String> bloqueos = new ArrayList<>();
        int confianza = 65;
        if (expediente.getEstadoExpediente() != EstadoExpediente.FINALIZADO) avisos.add("El expediente no esta finalizado"); else confianza += 5;
        if (vinculacionRepository.existsActivoByExpedienteId(expediente.getId())) {
            boolean perteneceAEstaFactura = factura != null && vinculacionRepository.findByExpedienteId(expediente.getId())
                    .map(v -> v.getFactura() != null && factura.getId().equals(v.getFactura().getId()))
                    .orElse(false);
            if (!perteneceAEstaFactura) bloqueos.add("El expediente ya esta facturado");
        }
        boolean justificante = !documentoRepository.findByExpedienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(expediente.getId(), JUSTIFICANTES).isEmpty();
        if (!justificante) avisos.add("Faltan justificantes finales"); else confianza += 5;
        String bastidorLocal = expediente.getVehiculo() == null ? null : normalizar(expediente.getVehiculo().getBastidor());
        if (bastidor != null && bastidorLocal != null) {
            if (normalizar(bastidor).equals(bastidorLocal)) confianza += 10; else bloqueos.add("El bastidor no coincide");
        }
        ExpedienteInteresado comprador = expediente.getInteresados().stream().filter(x -> x.getRol() == RolInteresado.COMPRADOR).findFirst().orElse(null);
        if (compradorId != null && comprador != null && comprador.getInteresado() != null) {
            if (normalizar(compradorId).equals(normalizar(comprador.getInteresado().getDni()))) confianza += 20; else bloqueos.add("El comprador no coincide");
        }
        if (factura != null && fechaFactura != null && factura.getFechaEmision() != null && !fechaFactura.equals(factura.getFechaEmision())) bloqueos.add("La fecha no coincide con la factura registrada");
        List<String> motivos = new ArrayList<>(bloqueos);
        motivos.addAll(avisos);
        boolean segura = motivos.isEmpty() && confianza >= 85;
        boolean confirmacionManual = !segura && bloqueos.isEmpty();
        if (confirmacionManual && motivos.isEmpty()) motivos.add("Coincidencia por matricula pendiente de confirmacion manual");
        return linea(documento, matricula, bastidor, compradorId, compradorNombre, expediente.getId(), confianza,
                segura ? "COINCIDENCIA_SEGURA" : "REVISION", String.join("; ", motivos), confirmacionManual);
    }

    private LineaFacturaDetectadaResponse linea(String doc, String mat, String vin, String id, String nombre, Long expediente,
                                                  int confianza, String estado, String motivo, boolean confirmacionManualPermitida) {
        return new LineaFacturaDetectadaResponse(doc, mat, vin, id, nombre, expediente, confianza, estado, motivo, confirmacionManualPermitida);
    }

    private String primero(Pattern patron, String texto) {
        Matcher matcher = patron.matcher(texto.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String identificadorAntesDelBastidor(String bloque, String bastidor, Set<String> excluidos) {
        String upper = bloque.toUpperCase(Locale.ROOT);
        int limite = bastidor == null ? upper.length() : upper.indexOf(bastidor.toUpperCase(Locale.ROOT));
        if (limite < 0) limite = upper.length();
        Matcher finDatos = FIN_DATOS_COMPRADOR.matcher(upper);
        if (finDatos.find() && finDatos.start() < limite) limite = finDatos.start();
        Matcher matcher = IDENTIFICADOR.matcher(upper.substring(0, limite));
        String ultimo = null;
        while (matcher.find()) {
            String candidato = matcher.group(1);
            if (!excluidos.contains(normalizar(candidato))) ultimo = candidato;
        }
        if (ultimo != null) return ultimo;
        matcher = IDENTIFICADOR.matcher(upper);
        while (matcher.find()) {
            String candidato = matcher.group(1);
            if (!excluidos.contains(normalizar(candidato))) return candidato;
        }
        return null;
    }
    private String extraerNombre(String bloque, String id, String vin) {
        if (id == null) return null;
        String upper = bloque.toUpperCase(Locale.ROOT);
        int inicio = upper.indexOf(id.toUpperCase(Locale.ROOT));
        if (inicio < 0) return null;
        inicio += id.length();
        int fin = vin == null ? bloque.length() : upper.indexOf(vin.toUpperCase(Locale.ROOT), inicio);
        if (fin < inicio) fin = bloque.length();
        String candidato = bloque.substring(inicio, fin).replaceAll("\\s+", " ").trim();
        candidato = candidato.split("(?i)\\s*(?:/|CONTIN.A|INFORMACI.N B.SICA|PROTECCI.N DE DATOS|GESTORIA ADMINISTRATIVA|RESPONSABLE DEL TRATAMIENTO)\\s*", 2)[0].trim();
        return candidato.isBlank() || candidato.length() > 120 ? null : candidato;
    }
    private String normalizar(String valor){return valor==null?null:valor.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);}
    private String nombreSeguro(String nombre){return Optional.ofNullable(nombre).orElse("factura.pdf").replaceAll("[\\\\/:*?\"<>|]","_");}
    private void validarPdf(MultipartFile archivo){if(archivo==null||archivo.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Factura vacia");if(archivo.getSize()>MAX_PDF)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La factura supera 20 MB");if(!"application/pdf".equalsIgnoreCase(archivo.getContentType()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solo se admiten facturas PDF");}
}
