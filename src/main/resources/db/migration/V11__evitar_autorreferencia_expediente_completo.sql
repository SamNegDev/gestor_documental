UPDATE documento
SET expediente_completo_origen_id = NULL,
    paginas_expediente_completo = NULL
WHERE expediente_completo_origen_id = id;
