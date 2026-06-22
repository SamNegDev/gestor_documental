package com.example.gestor_documental.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.thempus.justificantes")
public class ThempusJustificantesProperties {

    private boolean enabled = false;
    private URI respaldoUrl = URI.create("http://trafico.gestores.net:8080/sigaActualizacion/respaldoThempus.ctrl");
    private URI subidaUrl = URI.create("https://gt.thempus.com/subir_tramites.php");
    private String despacho = "";
    private String nif = "";
    private String version = "500821";
    private Duration timeout = Duration.ofSeconds(20);
    private String keyStorePath = "";
    private String keyStorePassword = "";
    private String keyStoreType = "PKCS12";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getRespaldoUrl() {
        return respaldoUrl;
    }

    public void setRespaldoUrl(URI respaldoUrl) {
        this.respaldoUrl = respaldoUrl;
    }

    public URI getSubidaUrl() {
        return subidaUrl;
    }

    public void setSubidaUrl(URI subidaUrl) {
        this.subidaUrl = subidaUrl;
    }

    public String getDespacho() {
        return despacho;
    }

    public void setDespacho(String despacho) {
        this.despacho = despacho;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }
}
