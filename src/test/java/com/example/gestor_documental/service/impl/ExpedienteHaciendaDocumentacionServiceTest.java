package com.example.gestor_documental.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.dto.expediente.HitoAccionResponse;
import com.example.gestor_documental.dto.expediente.HitoExpedienteResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExpedienteHaciendaDocumentacionServiceTest {

    @Mock ExpedienteDetalleApiService expedienteDetalleApiService;
    @Mock DocumentoRepository documentoRepository;
    @Mock Usuario admin;
    @TempDir Path tempDir;

    @Test
    void habilitaDocumentacionHaciendaEnLosHitosBatecomDelModelo620() {
        ExpedienteHaciendaDocumentacionService service =
                new ExpedienteHaciendaDocumentacionService(expedienteDetalleApiService, documentoRepository);

        for (String codigoHito : List.of("BATE_MODELO_620_PRESENTADO", "COM_MODELO_620_PRESENTADO")) {
            ExpedienteDetailResponse detalle = ExpedienteDetailResponse.builder()
                    .tipoTramite("BATECOM")
                    .siguientePaso(HitoExpedienteResponse.builder()
                            .id(codigoHito.toLowerCase().replace("_presentado", "").replace('_', '-'))
                            .accion("COMPLETAR_HITO")
                            .acciones(List.of(HitoAccionResponse.builder().codigoHito(codigoHito).build()))
                            .build())
                    .build();

            assertThat(service.tieneDocumentacionHaciendaDisponible(detalle)).isTrue();
        }
    }

    @Test
    void informaDeTodosLosExpedientesIncompletosEnUnaSolaRespuesta() throws Exception {
        Files.write(tempDir.resolve("venta.pdf"), new byte[] {1});
        Files.write(tempDir.resolve("vehiculo.pdf"), new byte[] {1});
        when(expedienteDetalleApiService.obtenerDetalle(10L, admin))
                .thenReturn(detalle("TF5724BU", "TRASPASO"));
        when(expedienteDetalleApiService.obtenerDetalle(20L, admin))
                .thenReturn(detalle("9816MFV", "TRASPASO"));
        when(documentoRepository.findByExpedienteId(10L))
                .thenReturn(List.of(documento(TipoDocumento.FACTURA, "venta.pdf")));
        when(documentoRepository.findByExpedienteId(20L))
                .thenReturn(List.of(documento(TipoDocumento.FICHA_TECNICA, "vehiculo.pdf")));
        ExpedienteHaciendaDocumentacionService service =
                new ExpedienteHaciendaDocumentacionService(expedienteDetalleApiService, documentoRepository);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.escribirZipDocumentacionHacienda(
                        List.of(10L, 20L), admin, new ByteArrayOutputStream()));

        assertThat(error.getReason())
                .contains("TF5724BU (EXP-10): falta documentacion del vehiculo")
                .contains("9816MFV (EXP-20): falta contrato o factura");
    }

    private ExpedienteDetailResponse detalle(String matricula, String tipoTramite) {
        return ExpedienteDetailResponse.builder()
                .matricula(matricula)
                .tipoTramite(tipoTramite)
                .siguientePaso(HitoExpedienteResponse.builder().id("modelo-620-presentado").build())
                .build();
    }

    private Documento documento(TipoDocumento tipo, String nombreArchivo) {
        Documento documento = new Documento();
        documento.setTipoDocumento(tipo);
        documento.setNombreArchivo(nombreArchivo);
        documento.setNombreArchivoOriginal(nombreArchivo);
        return documento;
    }
}