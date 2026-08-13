UPDATE documento
SET expediente_completo_origen_id = NULL,
    paginas_expediente_completo = NULL
WHERE expediente_completo_origen_id = id;

ALTER TABLE documento
    ADD CONSTRAINT chk_documento_dossier_no_autorreferencia
        CHECK (expediente_completo_origen_id IS NULL OR expediente_completo_origen_id <> id);
