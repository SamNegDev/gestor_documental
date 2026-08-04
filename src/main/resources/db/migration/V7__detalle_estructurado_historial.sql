CREATE TABLE historial_cambio_detalle (
    id BIGINT NOT NULL AUTO_INCREMENT,
    historial_cambio_id BIGINT NOT NULL,
    campo VARCHAR(100) NOT NULL,
    etiqueta VARCHAR(120) NOT NULL,
    valor_anterior TEXT NULL,
    valor_posterior TEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_historial_detalle_cambio
        FOREIGN KEY (historial_cambio_id) REFERENCES historial_cambio (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_historial_detalle_cambio
    ON historial_cambio_detalle (historial_cambio_id);
