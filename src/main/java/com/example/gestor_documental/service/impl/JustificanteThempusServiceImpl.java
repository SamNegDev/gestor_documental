package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.ThempusJustificantesProperties;
import com.example.gestor_documental.dto.justificante.JustificanteThempusPreviewResponse;
import com.example.gestor_documental.dto.justificante.JustificanteThempusRequest;
import com.example.gestor_documental.dto.justificante.JustificanteThempusSendResponse;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.service.JustificanteThempusService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

@Service
@RequiredArgsConstructor
@Slf4j
public class JustificanteThempusServiceImpl implements JustificanteThempusService {

    private static final Charset XML_CHARSET = Charset.forName("ISO-8859-1");
    private static final String USER_AGENT = "Java/1.8.0_121";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final ThempusJustificantesProperties properties;

    @Override
    public String consultarRespaldo() {
        HttpClient client = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(properties.getRespaldoUrl())
                .timeout(timeout())
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(Charset.forName("ISO-8859-15"))).body();
        } catch (IOException exception) {
            throw new OperacionInvalidaException("No se pudo consultar respaldo Thempus: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperacionInvalidaException("Consulta de respaldo Thempus interrumpida");
        }
    }

    @Override
    public JustificanteThempusPreviewResponse preview(JustificanteThempusRequest request) {
        String xml = construirXml(request);
        URI uri = buildUri(request);
        return new JustificanteThempusPreviewResponse(
                "POST",
                redactUri(uri),
                List.of(
                        "Content-type: " + CONTENT_TYPE,
                        "User-Agent: " + USER_AGENT,
                        "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2",
                        "Cache-Control: no-cache",
                        "Pragma: no-cache"
                ),
                xml.getBytes(XML_CHARSET).length,
                xml
        );
    }

    @Override
    public JustificanteThempusSendResponse enviar(JustificanteThempusRequest request) {
        String xml = construirXml(request);
        URI uri = buildUri(request);
        if (!properties.isEnabled()) {
            return new JustificanteThempusSendResponse(false, false, 0, "Integracion Thempus desactivada", redactUri(uri));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(timeout())
                .header("Content-type", CONTENT_TYPE)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2")
                .POST(HttpRequest.BodyPublishers.ofByteArray(xml.getBytes(XML_CHARSET)))
                .build();

        try {
            HttpResponse<String> response = newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString(Charset.forName("ISO-8859-15")));
            boolean enviado = response.statusCode() >= 200 && response.statusCode() < 300;
            log.info("Respuesta Thempus justificante: status={}, enviado={}, url={}", response.statusCode(), enviado, redactUri(uri));
            return new JustificanteThempusSendResponse(true, enviado, response.statusCode(), response.body(), redactUri(uri));
        } catch (IOException exception) {
            throw new OperacionInvalidaException("No se pudo enviar justificante Thempus: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperacionInvalidaException("Envio de justificante Thempus interrumpido");
        }
    }

    public String construirXml(JustificanteThempusRequest request) {
        JustificanteThempusRequest.DatosAdquirente adquirente = request != null && request.adquirente() != null
                ? request.adquirente()
                : new JustificanteThempusRequest.DatosAdquirente(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        JustificanteThempusRequest.DatosVehiculo vehiculo = request != null && request.vehiculo() != null
                ? request.vehiculo()
                : new JustificanteThempusRequest.DatosVehiculo(null, null, null, null, null);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n");
        xml.append("<FORMATO_GA>\r\n");
        xml.append("  <JUSTIFICANTE>\r\n");
        append(xml, "JEFATURA", value(request != null ? request.jefatura() : null), 4);
        append(xml, "DIAS_VALIDEZ", value(request != null ? request.diasValidez() : null), 4);
        append(xml, "SUCURSAL", value(request != null ? request.sucursal() : null), 4);
        append(xml, "TIPO_TRAMITE", value(request != null ? request.tipoTramite() : null), 4);
        append(xml, "DOCUMENTOS", value(request != null ? request.documentos() : null), 4);
        append(xml, "EXPEDIENTE_PLATAFORMA", value(request != null ? request.expedientePlataforma() : null), 4);
        append(xml, "MOTIVO", value(request != null ? request.motivo() : null), 4);
        xml.append("    <DATOS_ADQUIRENTE>\r\n");
        append(xml, "RAZON_SOCIAL_ADQUIRENTE", adquirente.razonSocial(), 6);
        append(xml, "NOMBRE_ADQUIRENTE", adquirente.nombre(), 6);
        append(xml, "APELLIDO1_ADQUIRENTE", adquirente.apellido1(), 6);
        append(xml, "APELLIDO2_ADQUIRENTE", adquirente.apellido2(), 6);
        append(xml, "DNI_ADQUIRENTE", adquirente.dni(), 6);
        append(xml, "SEXO_ADQUIRENTE", adquirente.sexo(), 6);
        append(xml, "SIGLAS_DIRECCION_ADQUIRENTE", adquirente.siglasDireccion(), 6);
        append(xml, "NOMBRE_VIA_DIRECCION_ADQUIRENTE", adquirente.nombreViaDireccion(), 6);
        append(xml, "KM_DIRECCION_ADQUIRENTE", adquirente.kmDireccion(), 6);
        append(xml, "HECTOMETRO_DIRECCION_ADQUIRENTE", adquirente.hectometroDireccion(), 6);
        append(xml, "NUMERO_DIRECCION_ADQUIRENTE", adquirente.numeroDireccion(), 6);
        append(xml, "LETRA_DIRECCION_ADQUIRENTE", adquirente.letraDireccion(), 6);
        append(xml, "ESCALERA_DIRECCION_ADQUIRENTE", adquirente.escaleraDireccion(), 6);
        append(xml, "PISO_DIRECCION_ADQUIRENTE", adquirente.pisoDireccion(), 6);
        append(xml, "PUERTA_DIRECCION_ADQUIRENTE", adquirente.puertaDireccion(), 6);
        append(xml, "BLOQUE_DIRECCION_ADQUIRENTE", adquirente.bloqueDireccion(), 6);
        append(xml, "MUNICIPIO_ADQUIRENTE", adquirente.municipio(), 6);
        append(xml, "PUEBLO_ADQUIRENTE", adquirente.pueblo(), 6);
        append(xml, "PROVINCIA_ADQUIRENTE", adquirente.provincia(), 6);
        append(xml, "CP_ADQUIRENTE", adquirente.cp(), 6);
        append(xml, "IFA_ADQUIRENTE", adquirente.ifa(), 6);
        xml.append("    </DATOS_ADQUIRENTE>\r\n");
        xml.append("    <DATOS_VEHICULO>\r\n");
        append(xml, "TIPO_VEHICULO", vehiculo.tipoVehiculo(), 6);
        append(xml, "MATRICULA", vehiculo.matricula(), 6);
        append(xml, "MARCA", vehiculo.marca(), 6);
        append(xml, "MODELO", vehiculo.modelo(), 6);
        append(xml, "NUMERO_BASTIDOR", vehiculo.numeroBastidor(), 6);
        xml.append("    </DATOS_VEHICULO>\r\n");
        xml.append("  </JUSTIFICANTE>\r\n");
        xml.append("</FORMATO_GA>\r\n\r\n");
        return xml.toString();
    }

    private URI buildUri(JustificanteThempusRequest request) {
        String despacho = primerNoVacio(request != null ? request.despacho() : null, properties.getDespacho());
        String nif = primerNoVacio(request != null ? request.nif() : null, properties.getNif());
        String version = primerNoVacio(request != null ? request.version() : null, properties.getVersion());
        validarParametro("despacho", despacho);
        validarParametro("nif", nif);
        validarParametro("version", version);
        return UriComponentsBuilder.fromUri(properties.getSubidaUrl())
                .queryParam("despacho", despacho)
                .queryParam("nif", nif)
                .queryParam("version", version)
                .build()
                .encode()
                .toUri();
    }

    private void validarParametro(String nombre, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new OperacionInvalidaException("Falta configurar parametro Thempus: " + nombre);
        }
    }

    private HttpClient newHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout())
                .followRedirects(HttpClient.Redirect.NORMAL);
        SSLContext sslContext = clientCertificateSslContext();
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    private SSLContext clientCertificateSslContext() {
        if (properties.getKeyStorePath() == null || properties.getKeyStorePath().isBlank()) {
            return null;
        }
        char[] password = properties.getKeyStorePassword() != null
                ? properties.getKeyStorePassword().toCharArray()
                : new char[0];
        String type = properties.getKeyStoreType() != null && !properties.getKeyStoreType().isBlank()
                ? properties.getKeyStoreType()
                : "PKCS12";
        Path path = Path.of(properties.getKeyStorePath());
        try (InputStream input = Files.newInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(input, password);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception exception) {
            throw new OperacionInvalidaException("No se pudo cargar el certificado cliente Thempus: " + exception.getMessage());
        }
    }

    private Duration timeout() {
        return properties.getTimeout() != null ? properties.getTimeout() : Duration.ofSeconds(20);
    }

    private void append(StringBuilder xml, String tag, String value, int indent) {
        xml.append(" ".repeat(indent))
                .append("<").append(tag).append(">")
                .append(escapeXml(value))
                .append("</").append(tag).append(">\r\n");
    }

    private String escapeXml(String value) {
        return value(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String primerNoVacio(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : value(second);
    }

    private String redactUri(URI uri) {
        return UriComponentsBuilder.fromUri(uri)
                .replaceQueryParam("despacho", "<REDACTADO>")
                .replaceQueryParam("nif", "<REDACTADO>")
                .replaceQueryParam("version", "<REDACTADO>")
                .build(false)
                .toUriString();
    }
}
