
package com.example.gestor_documental.enums;

public enum TipoDocumento {
    DNI,
    CONTRATO_COMPRAVENTA,
    PERMISO_CIRCULACION,
    FICHA_TECNICA,
    MANDATO,
    CIF,
    FACTURA,
    EXPEDIENTE_COMPLETO,
    OTROS,
    MANDATO_REPRESENTACION,
    CAMBIO_TITULARIDAD,
    AUTORIZACION_SERAFIN,
    INFORME_DGT,
    HUELLA_TRAMITE,
    COMPROBANTE_DGT,
    MODELO_620,
    DOCUMENTO_INCIDENCIA

    ;

    public String getLabel() {
        return name().replace('_', ' ');
    }
}
