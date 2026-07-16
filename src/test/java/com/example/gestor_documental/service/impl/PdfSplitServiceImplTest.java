package com.example.gestor_documental.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class PdfSplitServiceImplTest {

    private final PdfSplitServiceImpl service = new PdfSplitServiceImpl();

    @Test
    void unePdfYJpegEnUnPdfMultipagina() throws Exception {
        byte[] resultado = service.unirDocumentos(List.of(pdfSimple(), jpegSimple()));

        try (PDDocument documento = PDDocument.load(resultado)) {
            assertEquals(2, documento.getNumberOfPages());
        }
    }

    @Test
    void uneJpegsEnUnPdfMultipagina() throws Exception {
        byte[] resultado = service.unirDocumentos(List.of(jpegSimple(), jpegSimple()));

        try (PDDocument documento = PDDocument.load(resultado)) {
            assertEquals(2, documento.getNumberOfPages());
        }
    }

    private byte[] pdfSimple() throws IOException {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            documento.addPage(new PDPage(PDRectangle.A4));
            documento.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] jpegSimple() throws IOException {
        BufferedImage imagen = new BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = imagen.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.drawString("Documento JPEG de prueba", 40, 60);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "jpg", baos);
            return baos.toByteArray();
        }
    }
}
