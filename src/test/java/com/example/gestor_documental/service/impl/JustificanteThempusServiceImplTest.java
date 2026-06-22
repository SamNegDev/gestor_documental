package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.ThempusJustificantesProperties;
import com.example.gestor_documental.dto.justificante.JustificanteThempusPreviewResponse;
import com.example.gestor_documental.dto.justificante.JustificanteThempusRequest;
import com.example.gestor_documental.dto.justificante.JustificanteThempusSendResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class JustificanteThempusServiceImplTest {

    @Test
    void generaXmlJustificanteComoCuerpoCrudo() {
        JustificanteThempusServiceImpl service = new JustificanteThempusServiceImpl(properties(false));

        String xml = service.construirXml(request());

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        assertThat(xml).contains("<FORMATO_GA>");
        assertThat(xml).contains("<JUSTIFICANTE>");
        assertThat(xml).contains("<JEFATURA>TF</JEFATURA>");
        assertThat(xml).contains("<MATRICULA>1234ABC</MATRICULA>");
        assertThat(xml).doesNotContain("xml=");
        assertThat(xml).doesNotContain("%3C");
    }

    @Test
    void previewMantieneCabecerasCapturadasYUrlRedactada() {
        JustificanteThempusServiceImpl service = new JustificanteThempusServiceImpl(properties(false));

        JustificanteThempusPreviewResponse response = service.preview(request());

        assertThat(response.method()).isEqualTo("POST");
        assertThat(response.urlRedacted()).contains("/subir_tramites.php");
        assertThat(response.urlRedacted()).contains("despacho=<REDACTADO>");
        assertThat(response.headers()).contains("Content-type: application/x-www-form-urlencoded");
        assertThat(response.bodyBytes()).isEqualTo(response.xml().getBytes(Charset.forName("ISO-8859-1")).length);
    }

    @Test
    void enviarNoHacePostCuandoLaIntegracionEstaDesactivada() {
        JustificanteThempusServiceImpl service = new JustificanteThempusServiceImpl(properties(false));

        JustificanteThempusSendResponse response = service.enviar(request());

        assertThat(response.enviado()).isFalse();
        assertThat(response.enabled()).isFalse();
        assertThat(response.statusCode()).isZero();
    }

    private ThempusJustificantesProperties properties(boolean enabled) {
        ThempusJustificantesProperties properties = new ThempusJustificantesProperties();
        properties.setEnabled(enabled);
        properties.setSubidaUrl(URI.create("https://gt.thempus.com/subir_tramites.php"));
        properties.setDespacho("D001");
        properties.setNif("00000000T");
        properties.setVersion("500821");
        return properties;
    }

    private JustificanteThempusRequest request() {
        return new JustificanteThempusRequest(
                null,
                null,
                null,
                "TF",
                "15",
                "",
                "TRANSFERENCIA",
                "DNI,PERMISO",
                "EXP-1",
                "",
                new JustificanteThempusRequest.DatosAdquirente(
                        "",
                        "ANA",
                        "PEREZ",
                        "LOPEZ",
                        "00000000T",
                        "M",
                        "CL",
                        "MAYOR",
                        "",
                        "",
                        "1",
                        "",
                        "",
                        "2",
                        "A",
                        "",
                        "SANTA CRUZ",
                        "SANTA CRUZ",
                        "SANTA CRUZ DE TENERIFE",
                        "38001",
                        ""
                ),
                new JustificanteThempusRequest.DatosVehiculo(
                        "T",
                        "1234ABC",
                        "PEUGEOT",
                        "208",
                        "VF000000000000000"
                )
        );
    }
}
