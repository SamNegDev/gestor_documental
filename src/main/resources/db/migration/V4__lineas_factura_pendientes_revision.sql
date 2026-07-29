ALTER TABLE factura_holded
    ADD COLUMN lineas_pendientes_revision INT NOT NULL DEFAULT 0,
    ADD COLUMN detalle_lineas_pendientes VARCHAR(1000) NULL;