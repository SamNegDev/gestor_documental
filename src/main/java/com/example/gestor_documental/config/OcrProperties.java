package com.example.gestor_documental.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {
    private String tessdataPath;
    private String language = "spa";
    private float dpi = 300;
}