package com.example.gestor_documental.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpedienteLoteImpresionServiceTest {
    @Mock ExpedienteRepository expedienteRepository;
    @Mock DocumentoRepository documentoRepository;
    @TempDir Path tempDir;

    @Test
    void anteponeLaPortadaAlDocumentoCompleto() throws Exception {
        Path original = tempDir.resolve("completo.pdf");
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.addPage(new PDPage());
            pdf.save(original.toFile());
        }

        Cliente cliente = new Cliente("B123", "Cliente Norte", "norte@example.com");
        Expediente expediente = new Expediente();
        expediente.setId(7L);
        expediente.setMatricula("1234 ABC");
        expediente.setCliente(cliente);
        Interesado interesado = new Interesado("12345678A", "Maria Lopez");
        expediente.setInteresados(List.of(new ExpedienteInteresado(expediente, interesado, RolInteresado.COMPRADOR)));

        Documento documento = new Documento();
        documento.setNombreArchivo("completo.pdf");
        documento.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        documento.setFechaSubida(LocalDateTime.of(2026, 7, 21, 10, 35));
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente));
        when(documentoRepository.findFirstByExpedienteIdAndTipoDocumentoOrderByFechaSubidaDesc(7L, TipoDocumento.EXPEDIENTE_COMPLETO)).thenReturn(Optional.of(documento));

        ExpedienteLoteImpresionService service = new ExpedienteLoteImpresionService(expedienteRepository, documentoRepository);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        service.escribirLote(List.of(7L), salida);

        try (PDDocument resultado = PDDocument.load(salida.toByteArray())) {
            assertThat(resultado.getNumberOfPages()).isEqualTo(3);
            String portada = new PDFTextStripper().getText(resultado);
            assertThat(portada).contains("1234 ABC", "Cliente Norte", "Maria Lopez", "INCIDENCIAS / ANOTACIONES");
        }
    }
}