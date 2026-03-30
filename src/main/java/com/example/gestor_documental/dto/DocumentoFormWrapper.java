package com.example.gestor_documental.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentoFormWrapper {
    private List<DocumentoFormDto> documentos = new ArrayList<>();

    public List<DocumentoFormDto> getDocumentos() {
        return documentos;
    }

    public void setDocumentos(List<DocumentoFormDto> documentos) {
        this.documentos = documentos;
    }
}
