package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Vehiculo;
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
        assertThat(resultado.estado()).isEqualTo("FACTURA_LOCAL_NUEVA");
    }

    @Test
    void proponeExpedientePorMatriculaAunqueLaFacturaNoExistaEnHolded() throws Exception {
        FacturaHoldedRepository facturas = mock(FacturaHoldedRepository.class);
        ExpedienteRepository expedientes = mock(ExpedienteRepository.class);
        DocumentoRepository documentos = mock(DocumentoRepository.class);
        FacturaExpedienteRepository vinculaciones = mock(FacturaExpedienteRepository.class);
        Cliente cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(9L);
        Expediente expediente = mock(Expediente.class);
        when(expediente.getId()).thenReturn(4605L);
        when(expediente.getCliente()).thenReturn(cliente);
        when(expediente.getEstadoExpediente()).thenReturn(EstadoExpediente.FINALIZADO);
        Vehiculo vehiculo = mock(Vehiculo.class);
        when(vehiculo.getBastidor()).thenReturn("WB10P2101T6N28733");
        when(expediente.getVehiculo()).thenReturn(vehiculo);
        when(expediente.getInteresados()).thenReturn(List.of());
        when(expedientes.findByMatriculaNormalizada("2565NPX")).thenReturn(List.of(expediente));
        when(documentos.findByExpedienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(eq(4605L), anySet()))
                .thenReturn(List.of(mock(Documento.class)));
        var service = new FacturaDocumentoAnalisisService(facturas, expedientes, documentos, vinculaciones);
        MockMultipartFile archivo = new MockMultipartFile("archivos", "factura.pdf", "application/pdf",
                pdf("16/07/2026 2026/1997 Documento: 2026/4605 CODIGO MATRICULACION 78854946K COMPRADOR UNO WB10P2101T6N28733 2565NPX"));

        var resultado = service.analizar(List.of(archivo)).get(0);

        assertThat(resultado.estado()).isEqualTo("FACTURA_LOCAL_NUEVA");
        assertThat(resultado.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.expedienteId()).isEqualTo(4605L);
            assertThat(linea.estado()).isEqualTo("COINCIDENCIA_SEGURA");
            assertThat(linea.confianza()).isGreaterThanOrEqualTo(85);
        });
        verify(expedientes).findByMatriculaNormalizada("2565NPX");
        verify(expedientes, never()).findByClienteIdAndMatriculaNormalizada(anyLong(), anyString());
    }
    @Test
    void excluyeElCifDelEmisorCuandoSeRepiteEnVariasLineas() throws Exception {
        FacturaHoldedRepository facturas = mock(FacturaHoldedRepository.class);
        var service = new FacturaDocumentoAnalisisService(facturas, mock(ExpedienteRepository.class), mock(DocumentoRepository.class), mock(FacturaExpedienteRepository.class));
        MockMultipartFile archivo = new MockMultipartFile("archivos", "factura.pdf", "application/pdf", pdf(
                "16/07/2026 2026/1997 Documento: 2026/4605 J76711373 GESTORIA 78854946K COMPRADOR UNO WB10P2101T6N28733 2565NPX " +
                "Documento: 2026/4606 J76711373 GESTORIA B38501631 COMPRADOR DOS WBA31GE0307V19978 2543NPX"));

        var resultado = service.analizar(List.of(archivo)).get(0);

        assertThat(resultado.lineas()).extracting("compradorIdentificador").containsExactly("78854946K", "B38501631");
    }

    @Test
    void permiteConfirmarManualmenteUnExpedienteNoFinalizadoSinContradicciones() throws Exception {
        FacturaHoldedRepository facturas = mock(FacturaHoldedRepository.class);
        ExpedienteRepository expedientes = mock(ExpedienteRepository.class);
        DocumentoRepository documentos = mock(DocumentoRepository.class);
        FacturaExpedienteRepository vinculaciones = mock(FacturaExpedienteRepository.class);
        Expediente expediente = mock(Expediente.class);
        when(expediente.getId()).thenReturn(4605L);
        when(expediente.getEstadoExpediente()).thenReturn(EstadoExpediente.EN_TRAMITE);
        Vehiculo vehiculo = mock(Vehiculo.class);
        when(vehiculo.getBastidor()).thenReturn("WB10P2101T6N28733");
        when(expediente.getVehiculo()).thenReturn(vehiculo);
        when(expediente.getInteresados()).thenReturn(List.of());
        when(expedientes.findByMatriculaNormalizada("2565NPX")).thenReturn(List.of(expediente));
        when(documentos.findByExpedienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(eq(4605L), anySet())).thenReturn(List.of());
        var service = new FacturaDocumentoAnalisisService(facturas, expedientes, documentos, vinculaciones);
        MockMultipartFile archivo = new MockMultipartFile("archivos", "factura.pdf", "application/pdf",
                pdf("16/07/2026 2026/1997 Documento: 2026/4605 78854946K COMPRADOR UNO WB10P2101T6N28733 2565NPX"));

        var linea = service.analizar(List.of(archivo)).get(0).lineas().get(0);

        assertThat(linea.estado()).isEqualTo("REVISION");
        assertThat(linea.confirmacionManualPermitida()).isTrue();
        assertThat(linea.motivo()).contains("no esta finalizado");
    }
    @Test
    void detectaCompradorAunqueLaLineaNoTengaBastidor() throws Exception {
        FacturaHoldedRepository facturas = mock(FacturaHoldedRepository.class);
        var service = new FacturaDocumentoAnalisisService(facturas, mock(ExpedienteRepository.class), mock(DocumentoRepository.class), mock(FacturaExpedienteRepository.class));
        MockMultipartFile archivo = new MockMultipartFile("archivos", "factura.pdf", "application/pdf", pdf(
                "16/07/2026 2026/1997 Documento: 2026/4605 79083702L PEDRO JOSE DEL BOSQUE DE ARMAS / INFORMACION BASICA DE PROTECCION DE DATOS J76711373 GESTORIA 2565NPX"));

        var linea = service.analizar(List.of(archivo)).get(0).lineas().get(0);

        assertThat(linea.matricula()).isEqualTo("2565NPX");
        assertThat(linea.bastidor()).isNull();
        assertThat(linea.compradorIdentificador()).isEqualTo("79083702L");
        assertThat(linea.compradorNombre()).isEqualTo("PEDRO JOSE DEL BOSQUE DE ARMAS");
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
    @Test
    void extraeTotalFacturaConFormatoEspanol() {
        assertThat(FacturaDocumentoAnalisisService.extraerTotalTexto("Base 1.000,00\nIVA 210,00\nTOTAL FACTURA 1.210,00 EUR"))
                .isEqualByComparingTo("1210.00");
    }
}