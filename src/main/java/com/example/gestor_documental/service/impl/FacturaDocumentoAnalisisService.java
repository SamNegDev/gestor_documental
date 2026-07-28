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
                                                     MultipartFile archivo) throws IOException {
        if (facturaId == null || modalidad == null || expedienteIds == null || expedienteIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan factura, modalidad o expedientes");
        }
        if (modalidad == com.example.gestor_documental.enums.ModalidadFacturacion.POR_EXPEDIENTE && expedienteIds.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La facturacion por expediente requiere exactamente un expediente");
        }
        if (modalidad != com.example.gestor_documental.enums.ModalidadFacturacion.POR_EXPEDIENTE && (periodoDesde == null || periodoHasta == null || periodoHasta.isBefore(periodoDesde))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote requiere un periodo valido");
        }
        AnalisisFacturaArchivoResponse analisis = analizarUno(archivo);
        if (!facturaId.equals(analisis.facturaId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El numero del PDF no coincide con la factura seleccionada");
        Set<Long> seguros = new HashSet<>();
        analisis.lineas().stream().filter(l -> "COINCIDENCIA_SEGURA".equals(l.estado()) && l.expedienteId() != null).forEach(l -> seguros.add(l.expedienteId()));
        if (!seguros.containsAll(expedienteIds)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay expedientes que requieren revision antes de confirmar");

        FacturaHolded factura = facturaRepository.findById(facturaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Factura no encontrada"));
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
        for (Long expedienteId : new LinkedHashSet<>(expedienteIds)) {
            if (vinculacionRepository.existsActivoByExpedienteId(expedienteId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Un expediente ya pertenece a otra factura activa");
            FacturaExpediente vinculacion = new FacturaExpediente();
            vinculacion.setFactura(factura);
            vinculacion.setExpediente(expedienteRepository.findByIdForUpdate(expedienteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado")));
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
        List<LineaFacturaDetectadaResponse> lineas = extraerLineas(texto, factura, fecha);
        String estado = numero == null ? "NUMERO_NO_DETECTADO" : factura == null ? "SIN_COINCIDENCIA_HOLDED" : lineas.isEmpty() ? "SIN_EXPEDIENTES_DETECTADOS" : "PROPUESTA_LISTA";
        return new AnalisisFacturaArchivoResponse(nombreSeguro(archivo.getOriginalFilename()), numero, fecha, factura == null ? null : factura.getId(), estado, lineas);
    }

    private List<LineaFacturaDetectadaResponse> extraerLineas(String texto, FacturaHolded factura, LocalDate fechaFactura) {
        List<LineaFacturaDetectadaResponse> resultado = new ArrayList<>();
        Matcher documentos = DOCUMENTO.matcher(texto);
        List<Integer> inicios = new ArrayList<>();
        List<String> numeros = new ArrayList<>();
        while (documentos.find()) { inicios.add(documentos.start()); numeros.add(documentos.group(1)); }
        for (int i = 0; i < inicios.size(); i++) {
            String bloque = texto.substring(inicios.get(i), i + 1 < inicios.size() ? inicios.get(i + 1) : texto.length());
            String matricula = primero(MATRICULA, bloque);
            String bastidor = primero(BASTIDOR, bloque);
            String identificador = identificadorAntesDelBastidor(bloque, bastidor);
            String nombre = extraerNombre(bloque, identificador, bastidor);
            resultado.add(proponer(numeros.get(i), matricula, bastidor, identificador, nombre, factura, fechaFactura));
        }
        return resultado;
    }

    private LineaFacturaDetectadaResponse proponer(String documento, String matricula, String bastidor, String compradorId, String compradorNombre, FacturaHolded factura, LocalDate fechaFactura) {
        if (factura == null || factura.getCliente() == null) return linea(documento, matricula, bastidor, compradorId, compradorNombre, null, 0, "REVISION", "Factura sin cliente local enlazado");
        if (matricula == null) return linea(documento, null, bastidor, compradorId, compradorNombre, null, 0, "REVISION", "No se detecto matricula");
        List<Expediente> candidatos = expedienteRepository.findByClienteIdAndMatriculaNormalizada(factura.getCliente().getId(), normalizar(matricula));
        if (candidatos.size() != 1) return linea(documento, matricula, bastidor, compradorId, compradorNombre, null, 0, "REVISION", candidatos.isEmpty() ? "No existe expediente para la matricula" : "Hay varios expedientes posibles");
        Expediente expediente = candidatos.get(0);
        List<String> motivos = new ArrayList<>();
        int confianza = 45;
        if (expediente.getEstadoExpediente() != EstadoExpediente.FINALIZADO) motivos.add("El expediente no esta finalizado"); else confianza += 15;
        if (vinculacionRepository.existsActivoByExpedienteId(expediente.getId())) motivos.add("El expediente ya esta facturado");
        boolean justificante = !documentoRepository.findByExpedienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(expediente.getId(), JUSTIFICANTES).isEmpty();
        if (!justificante) motivos.add("Faltan justificantes finales"); else confianza += 15;
        String bastidorLocal = expediente.getVehiculo() == null ? null : normalizar(expediente.getVehiculo().getBastidor());
        if (bastidor != null && bastidorLocal != null) { if (normalizar(bastidor).equals(bastidorLocal)) confianza += 15; else motivos.add("El bastidor no coincide"); }
        ExpedienteInteresado comprador = expediente.getInteresados().stream().filter(x -> x.getRol() == RolInteresado.COMPRADOR).findFirst().orElse(null);
        if (compradorId != null && comprador != null && comprador.getInteresado() != null) {
            if (normalizar(compradorId).equals(normalizar(comprador.getInteresado().getDni()))) confianza += 10; else motivos.add("El comprador no coincide");
        }
        if (fechaFactura != null && factura.getFechaEmision() != null && !fechaFactura.equals(factura.getFechaEmision())) motivos.add("La fecha no coincide con Holded");
        String estado = motivos.isEmpty() && confianza >= 85 ? "COINCIDENCIA_SEGURA" : "REVISION";
        return linea(documento, matricula, bastidor, compradorId, compradorNombre, expediente.getId(), confianza, estado, String.join("; ", motivos));
    }

    private LineaFacturaDetectadaResponse linea(String doc,String mat,String vin,String id,String nombre,Long expediente,int confianza,String estado,String motivo){return new LineaFacturaDetectadaResponse(doc,mat,vin,id,nombre,expediente,confianza,estado,motivo);}
    private String primero(Pattern patron,String texto){Matcher m=patron.matcher(texto.toUpperCase(Locale.ROOT));return m.find()?m.group(1):null;}
    private String identificadorAntesDelBastidor(String bloque,String bastidor){if(bastidor==null)return primero(IDENTIFICADOR,bloque);String upper=bloque.toUpperCase(Locale.ROOT);int limite=upper.indexOf(bastidor.toUpperCase(Locale.ROOT));if(limite<0)return primero(IDENTIFICADOR,bloque);Matcher m=IDENTIFICADOR.matcher(upper.substring(0,limite));String ultimo=null;while(m.find())ultimo=m.group(1);return ultimo;}
    private String extraerNombre(String bloque,String id,String vin){if(id==null||vin==null)return null;String upper=bloque.toUpperCase(Locale.ROOT);int a=upper.indexOf(id)+id.length(),b=upper.indexOf(vin,a);return b>a?bloque.substring(a,b).replaceAll("\\s+"," ").trim():null;}
    private String normalizar(String valor){return valor==null?null:valor.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);}
    private String nombreSeguro(String nombre){return Optional.ofNullable(nombre).orElse("factura.pdf").replaceAll("[\\\\/:*?\"<>|]","_");}
    private void validarPdf(MultipartFile archivo){if(archivo==null||archivo.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Factura vacia");if(archivo.getSize()>MAX_PDF)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La factura supera 20 MB");if(!"application/pdf".equalsIgnoreCase(archivo.getContentType()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solo se admiten facturas PDF");}
}