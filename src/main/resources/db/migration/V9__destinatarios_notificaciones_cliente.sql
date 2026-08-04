ALTER TABLE cliente
    ADD COLUMN email_notificaciones VARCHAR(250) NULL AFTER email;

CREATE TABLE cliente_email_notificacion_copia (
    cliente_id BIGINT NOT NULL,
    orden INT NOT NULL,
    email VARCHAR(250) NOT NULL,
    PRIMARY KEY (cliente_id, orden),
    CONSTRAINT uk_cliente_email_notificacion_copia UNIQUE (cliente_id, email),
    CONSTRAINT fk_cliente_email_notificacion_copia_cliente
        FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE CASCADE
);
