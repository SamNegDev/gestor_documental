-- Los mensajes y adjuntos de WhatsApp son historico operativo. Al borrar un cliente
-- se conserva el registro y solo se elimina su vinculacion con el cliente borrado.

SET @fk_evento_cliente = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'whatsapp_webhook_evento'
      AND COLUMN_NAME = 'cliente_id'
      AND REFERENCED_TABLE_NAME = 'cliente'
    LIMIT 1
);
SET @drop_fk_evento = IF(
    @fk_evento_cliente IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE whatsapp_webhook_evento DROP FOREIGN KEY `', @fk_evento_cliente, '`')
);
PREPARE drop_fk_evento_stmt FROM @drop_fk_evento;
EXECUTE drop_fk_evento_stmt;
DEALLOCATE PREPARE drop_fk_evento_stmt;

ALTER TABLE whatsapp_webhook_evento
    ADD CONSTRAINT fk_whatsapp_evento_cliente
    FOREIGN KEY (cliente_id) REFERENCES cliente(id)
    ON DELETE SET NULL;

SET @fk_adjunto_cliente = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'whatsapp_adjunto'
      AND COLUMN_NAME = 'cliente_id'
      AND REFERENCED_TABLE_NAME = 'cliente'
    LIMIT 1
);
SET @drop_fk_adjunto = IF(
    @fk_adjunto_cliente IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE whatsapp_adjunto DROP FOREIGN KEY `', @fk_adjunto_cliente, '`')
);
PREPARE drop_fk_adjunto_stmt FROM @drop_fk_adjunto;
EXECUTE drop_fk_adjunto_stmt;
DEALLOCATE PREPARE drop_fk_adjunto_stmt;

ALTER TABLE whatsapp_adjunto
    ADD CONSTRAINT fk_whatsapp_adjunto_cliente
    FOREIGN KEY (cliente_id) REFERENCES cliente(id)
    ON DELETE SET NULL;
