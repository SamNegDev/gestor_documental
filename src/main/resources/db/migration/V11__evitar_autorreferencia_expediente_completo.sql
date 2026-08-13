UPDATE documento
SET expediente_completo_origen_id = NULL,
    paginas_expediente_completo = NULL
WHERE expediente_completo_origen_id = id;

DROP TRIGGER IF EXISTS trg_documento_no_autorreferencia_insert;
DROP TRIGGER IF EXISTS trg_documento_no_autorreferencia_update;

DELIMITER //

CREATE TRIGGER trg_documento_no_autorreferencia_insert
BEFORE INSERT ON documento
FOR EACH ROW
BEGIN
    IF NEW.id IS NOT NULL AND NEW.expediente_completo_origen_id = NEW.id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Un documento no puede ser su propio expediente completo de origen';
    END IF;
END//

CREATE TRIGGER trg_documento_no_autorreferencia_update
BEFORE UPDATE ON documento
FOR EACH ROW
BEGIN
    IF NEW.expediente_completo_origen_id = NEW.id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Un documento no puede ser su propio expediente completo de origen';
    END IF;
END//

DELIMITER ;
