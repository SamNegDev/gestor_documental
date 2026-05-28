package com.example.gestor_documental.enums;

public enum TipoOperacionExpediente {
    TRASPASO_DIRECTO("Traspaso directo"),
    ENTREGA_COMPRAVENTA_BATE("Entrega a compraventa (BATE)"),
    FINALIZACION_ENTREGA_COMPRAVENTA_COM("Finalización entrega a compraventa (COM)");

    private final String label;

    TipoOperacionExpediente(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
