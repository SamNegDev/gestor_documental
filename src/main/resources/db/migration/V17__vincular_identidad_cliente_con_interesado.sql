-- Una identidad maestra subida a la ficha de un cliente representa al propio
-- cliente cuando su DNI/CIF coincide exactamente con un interesado global.
-- La vinculacion permite reutilizarla en operaciones de otros clientes en las
-- que esa misma persona o empresa intervenga con un rol documental.

ALTER TABLE requisito_documental_expediente
    ADD COLUMN soporte_recurrente_externo BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE documento documento
JOIN cliente cliente ON cliente.id = documento.cliente_id
JOIN (
    SELECT
        UPPER(REPLACE(REPLACE(REPLACE(dni, ' ', ''), '-', ''), '.', '')) AS identificador,
        MIN(id) AS interesado_id
    FROM interesado
    GROUP BY UPPER(REPLACE(REPLACE(REPLACE(dni, ' ', ''), '-', ''), '.', ''))
) interesado_coincidente
    ON interesado_coincidente.identificador = UPPER(REPLACE(REPLACE(REPLACE(cliente.nif, ' ', ''), '-', ''), '.', ''))
SET documento.interesado_id = interesado_coincidente.interesado_id
WHERE documento.interesado_id IS NULL
  AND documento.expediente_id IS NULL
  AND documento.solicitud_id IS NULL
  AND (
      (documento.tipo_documento = 'CIF'
          AND UPPER(REPLACE(REPLACE(REPLACE(cliente.nif, ' ', ''), '-', ''), '.', ''))
              REGEXP '^[ABCDEFGHJNPQRSUVW]')
      OR
      (documento.tipo_documento = 'DNI'
          AND UPPER(REPLACE(REPLACE(REPLACE(cliente.nif, ' ', ''), '-', ''), '.', ''))
              REGEXP '^([0-9]|[XYZ])')
  );

-- Deja satisfechos los requisitos de identidad que ya tienen soporte maestro
-- en otra ficha, sin vincular ni revelar ese documento al cliente del expediente.
UPDATE requisito_documental_expediente requisito
JOIN expediente expediente ON expediente.id = requisito.expediente_id
SET requisito.estado = 'APORTADO',
    requisito.documento_id = NULL,
    requisito.soporte_recurrente_externo = TRUE,
    requisito.fecha_resolucion = COALESCE(requisito.fecha_resolucion, CURRENT_TIMESTAMP),
    requisito.resuelto_por_usuario_id = NULL
WHERE requisito.estado = 'REQUERIDO'
  AND requisito.documento_id IS NULL
  AND requisito.interesado_id IS NOT NULL
  AND requisito.tipo_documento IN ('DNI', 'CIF')
  AND EXISTS (
      SELECT 1
      FROM documento documento
      WHERE documento.interesado_id = requisito.interesado_id
        AND documento.tipo_documento = requisito.tipo_documento
        AND documento.cliente_id IS NOT NULL
        AND documento.cliente_id <> expediente.cliente_id
        AND documento.expediente_id IS NULL
        AND documento.solicitud_id IS NULL
  );
