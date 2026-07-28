package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FacturaDocumentoAnalisisServiceTest {
    @Test
    void extraeFacturaYProponeTodasLasMatriculasSinAsignarSiNoExisteHolded() throws Exception {
        FacturaHoldedRepository facturas = mock(FacturaHoldedRepository.class);
        var service = new FacturaDocumentoAnalisisService(facturas, mock(ExpedienteRepository.class), mock(DocumentoRepository.class), mock(FacturaExpedienteRepository.class));
        MockMultipartFile archivo = new MockMultipartFile("archivos", "factura.pdf", "application/pdf", pdf("16/07/2026 2026/1997 Documento: 2026/4605 CODIGO MATRICULACION J76711373 TEXTO LEGAL EMISOR 78854946K COMPRADOR UNO WB10P2101T6N28733 2565NPX Documento: 2026/4606 CODIGO MATRICULACION B38501631 COMPRADOR DOS WBA31GE0307V19978 2543NPX"));

        var resultado = service.analizar(List.of(archivo)).get(0);

        assertThat(resultado.numeroFactura()).isEqualTo("2026/1997");
        assertThat(resultado.lineas()).extracting("matricula").containsExactly("2565NPX", "2543NPX");
        assertThat(resultado.lineas().get(0).compradorIdentificador()).isEqualTo("78854946K");
        assertThat(resultado.estado()).isEqualTo("SIN_COINCIDENCIA_HOLDED");
    }

    private byte[] pdf(String texto) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 8); stream.newLineAtOffset(30, 700); stream.showText(texto); stream.endText();
            }
            doc.save(out); return out.toByteArray();
        }
    }
}