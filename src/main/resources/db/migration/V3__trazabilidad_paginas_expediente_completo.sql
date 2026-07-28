ALTER TABLE documento
    ADD COLUMN expediente_completo_origen_id BIGINT NULL,
    ADD COLUMN paginas_expediente_completo VARCHAR(1000) NULL,
    ADD CONSTRAINT fk_documento_expediente_completo_origen
        FOREIGN KEY (expediente_completo_origen_id) REFERENCES documento (id),
    ADD INDEX idx_documento_expediente_completo_origen (expediente_completo_origen_id);
