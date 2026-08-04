ALTER TABLE auditoria_documento
    MODIFY COLUMN accion VARCHAR(60) NOT NULL,
    ADD COLUMN recurso_tipo VARCHAR(40) NULL AFTER resultado,
    ADD COLUMN recurso_id BIGINT NULL AFTER recurso_tipo,
    ADD COLUMN recurso_nombre VARCHAR(200) NULL AFTER recurso_id,
    ADD COLUMN metodo_http VARCHAR(10) NULL AFTER agente_usuario,
    ADD COLUMN ruta VARCHAR(300) NULL AFTER metodo_http;

UPDATE auditoria_documento
SET recurso_tipo = 'DOCUMENTO',
    recurso_id = documento_id,
    recurso_nombre = documento_nombre
WHERE recurso_tipo IS NULL;

CREATE INDEX idx_aud_evento_recurso_fecha
    ON auditoria_documento (recurso_tipo, recurso_id, fecha_evento);
