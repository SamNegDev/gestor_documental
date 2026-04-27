
package com.example.gestor_documental.enums;

public enum TipoDocumento {
    DNI,
    CONTRATO_COMPRAVENTA,
    PERMISO_CIRCULACION,
    FICHA_TECNICA,
    MANDATO,
    FACTURA,
    EXPEDIENTE_COMPLETO,
    OTROS,
    MANDATO_REPRESENTACION,
    CAMBIO_TITULARIDAD,
    AUTORIZACION_SERAFIN,
    HUELLA_TRAMITE

    ;

    public String getLabel() {
        return name().replace('_', ' ');
    }
}
