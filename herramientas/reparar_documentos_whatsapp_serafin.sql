-- Diagnostico y reparacion conservadora de adjuntos/documentos WhatsApp
-- asociados por error al cliente Asesoria Serafin.
--
-- Uso:
-- 1. Ejecutar con @aplicar_cambios = 0 para revisar candidatos.
-- 2. Si la lista es correcta, cambiar @aplicar_cambios a 1 y volver a ejecutar.
--
-- Solo reasigna automaticamente registros cuyo telefono remitente coincide de
-- forma unica con otro cliente. Los documentos ya vinculados a expediente o
-- solicitud se listan como revision manual.

SET @cliente_origen_nombre_like = '%SERAFIN%';
SET @aplicar_cambios = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_clientes_tel_unico;
CREATE TEMPORARY TABLE tmp_clientes_tel_unico AS
SELECT MIN(cliente_id) AS cliente_id, tel9
FROM (
    SELECT
        id AS cliente_id,
        RIGHT(REGEXP_REPLACE(COALESCE(telefono, ''), '[^0-9]', ''), 9) AS tel9
    FROM cliente
    WHERE LENGTH(REGEXP_REPLACE(COALESCE(telefono, ''), '[^0-9]', '')) >= 9
) normalizados
GROUP BY tel9
HAVING COUNT(*) = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_clientes_origen;
CREATE TEMPORARY TABLE tmp_clientes_origen AS
SELECT id AS cliente_origen_id, nombre AS cliente_origen
FROM cliente
WHERE nombre COLLATE utf8mb4_unicode_ci
      LIKE CONVERT(@cliente_origen_nombre_like USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS tmp_whatsapp_reasignacion;
CREATE TEMPORARY TABLE tmp_whatsapp_reasignacion AS
SELECT
    a.id AS adjunto_id,
    a.evento_id,
    d.id AS documento_id,
    a.cliente_id AS cliente_actual_id,
    destino.cliente_id AS cliente_destino_id,
    origen.cliente_origen,
    cliente_destino.nombre AS cliente_destino,
    a.telefono,
    a.fecha_recepcion,
    a.nombre_archivo_original,
    a.nombre_archivo
FROM whatsapp_adjunto a
JOIN tmp_clientes_origen origen ON origen.cliente_origen_id = a.cliente_id
JOIN tmp_clientes_tel_unico destino
    ON destino.tel9 = RIGHT(REGEXP_REPLACE(COALESCE(a.telefono, ''), '[^0-9]', ''), 9)
JOIN cliente cliente_destino ON cliente_destino.id = destino.cliente_id
LEFT JOIN documento d
    ON d.nombre_archivo = a.nombre_archivo
   AND d.cliente_id = a.cliente_id
WHERE destino.cliente_id <> a.cliente_id
  AND LENGTH(REGEXP_REPLACE(COALESCE(a.telefono, ''), '[^0-9]', '')) >= 9
  AND (d.id IS NULL OR (d.expediente_id IS NULL AND d.solicitud_id IS NULL));

SELECT
    'AUTO_REASIGNABLE' AS tipo,
    adjunto_id,
    documento_id,
    cliente_origen,
    cliente_destino,
    telefono,
    fecha_recepcion,
    nombre_archivo_original,
    nombre_archivo
FROM tmp_whatsapp_reasignacion
ORDER BY fecha_recepcion DESC;

SELECT
    'REVISION_MANUAL_EXPEDIENTE_O_SOLICITUD' AS tipo,
    a.id AS adjunto_id,
    d.id AS documento_id,
    origen.cliente_origen,
    a.telefono,
    a.fecha_recepcion,
    d.expediente_id,
    d.solicitud_id,
    a.nombre_archivo_original,
    a.nombre_archivo
FROM whatsapp_adjunto a
JOIN tmp_clientes_origen origen ON origen.cliente_origen_id = a.cliente_id
JOIN documento d
    ON d.nombre_archivo = a.nombre_archivo
   AND d.cliente_id = a.cliente_id
WHERE d.expediente_id IS NOT NULL
   OR d.solicitud_id IS NOT NULL
ORDER BY a.fecha_recepcion DESC;

UPDATE documento d
JOIN tmp_whatsapp_reasignacion r ON r.documento_id = d.id
SET d.cliente_id = r.cliente_destino_id
WHERE @aplicar_cambios = 1
  AND d.expediente_id IS NULL
  AND d.solicitud_id IS NULL;

UPDATE whatsapp_adjunto a
JOIN tmp_whatsapp_reasignacion r ON r.adjunto_id = a.id
SET a.cliente_id = r.cliente_destino_id
WHERE @aplicar_cambios = 1;

UPDATE whatsapp_webhook_evento e
JOIN tmp_whatsapp_reasignacion r ON r.evento_id = e.id
SET e.cliente_id = r.cliente_destino_id
WHERE @aplicar_cambios = 1
  AND e.expediente_id IS NULL
  AND e.solicitud_id IS NULL;

SELECT
    CASE
        WHEN @aplicar_cambios = 1 THEN 'Cambios aplicados'
        ELSE 'Modo diagnostico: no se ha modificado ningun registro'
    END AS resultado,
    COUNT(*) AS candidatos_auto_reasignables
FROM tmp_whatsapp_reasignacion;
