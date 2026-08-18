ALTER TABLE solicitud_lectura_ia_item
    DROP FOREIGN KEY fk_sol_lectura_ia_item_documento;

ALTER TABLE solicitud_lectura_ia_item
    MODIFY COLUMN documento_id BIGINT NULL;

ALTER TABLE solicitud_lectura_ia_item
    ADD CONSTRAINT fk_sol_lectura_ia_item_documento
        FOREIGN KEY (documento_id) REFERENCES documento(id) ON DELETE SET NULL;

ALTER TABLE expediente_lectura_ia_item
    DROP FOREIGN KEY fk_exp_lectura_ia_item_documento;

ALTER TABLE expediente_lectura_ia_item
    MODIFY COLUMN documento_id BIGINT NULL;

ALTER TABLE expediente_lectura_ia_item
    ADD CONSTRAINT fk_exp_lectura_ia_item_documento
        FOREIGN KEY (documento_id) REFERENCES documento(id) ON DELETE SET NULL;
