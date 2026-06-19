package com.example.gestor_documental.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

    private String apiKey;
    private String model = "gpt-5.4";
    private String identityModel = "gpt-5.4-mini";
    private String responsesUrl = "https://api.openai.com/v1/responses";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getIdentityModel() {
        return identityModel;
    }

    public void setIdentityModel(String identityModel) {
        this.identityModel = identityModel;
    }

    public String getResponsesUrl() {
        return responsesUrl;
    }

    public void setResponsesUrl(String responsesUrl) {
        this.responsesUrl = responsesUrl;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
