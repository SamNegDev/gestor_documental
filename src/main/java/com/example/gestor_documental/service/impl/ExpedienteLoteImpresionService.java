package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpedienteLoteImpresionService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color AZUL = new Color(22, 87, 111), GRIS = new Color(84, 98, 106);
    private final ExpedienteRepository expedienteRepository;
    private final DocumentoRepository documentoRepository;
    @Value("${app.upload.dir:uploads}") private String uploadDir;

    @Transactional(readOnly = true)
    public void escribirLote(List<Long> expedienteIds, OutputStream salida) throws IOException {
        if (expedienteIds == null || expedienteIds.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Selecciona al menos un expediente");
        List<Long> ids = expedienteIds.stream().distinct().toList();
        if (ids.size() > 100) throw error(HttpStatus.BAD_REQUEST, "El lote no puede superar los 100 expedientes");
        Path base = Paths.get(uploadDir).normalize().toAbsolutePath();
        try (PDDocument resultado = new PDDocument()) {
            for (Long id : ids) {
                Expediente expediente = expedienteRepository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "No existe el expediente " + id));
                Documento completo = documentoRepository.findFirstByExpedienteIdAndTipoDocumentoOrderByFechaSubidaDesc(id, TipoDocumento.EXPEDIENTE_COMPLETO)
                        .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El expediente " + id + " no tiene documento completo"));
                Path ruta = ruta(completo, base, id);
                resultado.addPage(portada(resultado, expediente, completo.getFechaSubida()));
                try (PDDocument original = PDDocument.load(Files.readAllBytes(ruta))) {
                    new PDFMergerUtility().appendDocument(resultado, original);
                }
            }
            resultado.save(salida);
        }
    }

    private Path ruta(Documento documento, Path base, Long id) {
        if (documento.getNombreArchivo() == null || documento.getNombreArchivo().isBlank()) throw error(HttpStatus.BAD_REQUEST, "El documento completo del expediente " + id + " no tiene archivo");
        Path ruta = base.resolve(documento.getNombreArchivo()).normalize();
        if (!ruta.startsWith(base) || !Files.isRegularFile(ruta)) throw error(HttpStatus.NOT_FOUND, "No se encuentra el documento completo del expediente " + id);
        return ruta;
    }

    private PDPage portada(PDDocument pdf, Expediente e, LocalDateTime recepcion) throws IOException {
        PDPage pagina = new PDPage(PDRectangle.A4);
        try (PDPageContentStream c = new PDPageContentStream(pdf, pagina)) {
            float izq = 54, der = PDRectangle.A4.getWidth() - izq;
            texto(c, "EXPEDIENTE", izq, 785, 13, PDType1Font.HELVETICA_BOLD, AZUL);
            derecha(c, valor(e.getCliente() != null ? e.getCliente().getNombre() : null, "SIN CLIENTE"), der, 783, 11, PDType1Font.HELVETICA_BOLD, GRIS);
            seccion(c, "DATOS DEL TRAMITE", izq, der, 735);
            campo(c, "Tipo de tramite", tramite(e), izq, 705);
            centro(c, valor(e.getMatricula(), "SIN MATRICULA"), PDRectangle.A4.getWidth() / 2, 654, 27, PDType1Font.HELVETICA_BOLD, AZUL);
            campo(c, "Fecha de recepcion", recepcion != null ? FECHA.format(recepcion) : "Sin fecha", izq, 615);
            campo(c, "Creado por", creador(e), izq, 591);
            seccion(c, "VEHICULO", izq, der, 545);
            campo(c, "Marca y modelo", valor(e.getVehiculo() != null ? unir(e.getVehiculo().getMarca(), e.getVehiculo().getModelo()) : null, "Sin datos"), izq, 515);
            campo(c, "Bastidor", valor(e.getVehiculo() != null ? e.getVehiculo().getBastidor() : null, "Sin datos"), izq, 491);
            seccion(c, "INTERESADOS", izq, der, 445);
            float y = 415;
            List<ExpedienteInteresado> interesados = e.getInteresados().stream().sorted(Comparator.comparing(r -> r.getRol() != null ? r.getRol().name() : "")).limit(4).toList();
            if (interesados.isEmpty()) { campo(c, "", "Sin interesados registrados", izq, y); y -= 28; }
            for (ExpedienteInteresado r : interesados) {
                Interesado i = r.getInteresado();
                campo(c, capitalizar(r.getRol() != null ? r.getRol().name().replace('_', ' ') : "Interesado"), i != null ? unir(i.getNombre(), i.getDni()) : "Sin datos", izq, y);
                y -= 28;
            }
            float bloque = Math.min(y - 16, 285);
            seccion(c, "INCIDENCIAS / ANOTACIONES", izq, der, bloque);
            for (int i = 0; i < 3; i++) { float ly = bloque - 42 - i * 42; c.setStrokingColor(new Color(170,179,184)); c.setLineWidth(.6f); c.moveTo(izq, ly); c.lineTo(der, ly); c.stroke(); }
            dibujarLogo(pdf, c);
        }
        return pagina;
    }

    private void dibujarLogo(PDDocument pdf, PDPageContentStream c) throws IOException {
        ClassPathResource recurso = new ClassPathResource("branding/casado-negrin-logo.png");
        try (InputStream entrada = recurso.getInputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(pdf, entrada.readAllBytes(), "logo-gestoria");
            float ancho = 145;
            float alto = logo.getHeight() * ancho / logo.getWidth();
            c.drawImage(logo, (PDRectangle.A4.getWidth() - ancho) / 2, 40, ancho, alto);
        }
    }
    private void seccion(PDPageContentStream c, String t, float x, float der, float y) throws IOException { texto(c,t,x,y,9,PDType1Font.HELVETICA_BOLD,AZUL); c.setStrokingColor(AZUL); c.setLineWidth(.8f); c.moveTo(x,y-7); c.lineTo(der,y-7); c.stroke(); }
    private void campo(PDPageContentStream c, String e, String v, float x, float y) throws IOException { if (!e.isBlank()) texto(c,e,x,y,9,PDType1Font.HELVETICA_BOLD,GRIS); texto(c,v,x+132,y,10,PDType1Font.HELVETICA,Color.BLACK); }
    private void texto(PDPageContentStream c, String v, float x, float y, float s, PDFont f, Color color) throws IOException { c.beginText(); c.setFont(f,s); c.setNonStrokingColor(color); c.newLineAtOffset(x,y); c.showText(limpiar(v)); c.endText(); }
    private void derecha(PDPageContentStream c,String v,float x,float y,float s,PDFont f,Color color) throws IOException { String l=limpiar(v); texto(c,l,x-f.getStringWidth(l)/1000*s,y,s,f,color); }
    private void centro(PDPageContentStream c,String v,float x,float y,float s,PDFont f,Color color) throws IOException { String l=limpiar(v); texto(c,l,x-f.getStringWidth(l)/2000*s,y,s,f,color); }
    private String tramite(Expediente e) { if (e.getTipoTramite()==null) return "Sin tipo"; if (e.getTipoTramite().getDescripcion()!=null && !e.getTipoTramite().getDescripcion().isBlank()) return e.getTipoTramite().getDescripcion(); return e.getTipoTramite().getNombre()!=null ? e.getTipoTramite().getNombre().name().replace('_',' ') : "Sin tipo"; }
    private String creador(Expediente e) { return e.getCreadoPor()==null ? "Sistema" : valor(unir(e.getCreadoPor().getNombre(),e.getCreadoPor().getApellidos()),e.getCreadoPor().getEmail()); }
    private String unir(String a,String b) { a=a!=null?a.trim():""; b=b!=null?b.trim():""; return (a+(a.isEmpty()||b.isEmpty()?"":" - ")+b).trim(); }
    private String valor(String v,String defecto) { return v==null||v.isBlank()?defecto:v.trim(); }
    private String capitalizar(String v) { v=v.toLowerCase(); return Character.toUpperCase(v.charAt(0))+v.substring(1); }
    private String limpiar(String v) { return valor(v,"").replace('–','-').replace('—','-').replace('’','\''); }
    private ResponseStatusException error(HttpStatus estado,String mensaje) { return new ResponseStatusException(estado,mensaje); }
}
