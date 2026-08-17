UPDATE interesado
SET tipo_persona = 'EMPRESA',
    razon_social = COALESCE(NULLIF(TRIM(razon_social), ''), nombre)
WHERE REGEXP_LIKE(
        REGEXP_REPLACE(UPPER(COALESCE(dni, '')), '[^A-Z0-9]', ''),
        '^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$'
      );

INSERT INTO cliente_interesado (
    cliente_id,
    interesado_id,
    representante_legal,
    habitual,
    fecha_alta
)
SELECT cliente.id,
       interesado.id,
       0,
       1,
       CURRENT_TIMESTAMP(6)
FROM cliente
JOIN interesado
  ON REGEXP_REPLACE(UPPER(COALESCE(interesado.dni, '')), '[^A-Z0-9]', '')
     = REGEXP_REPLACE(UPPER(COALESCE(cliente.nif, '')), '[^A-Z0-9]', '')
WHERE REGEXP_LIKE(
        REGEXP_REPLACE(UPPER(COALESCE(cliente.nif, '')), '[^A-Z0-9]', ''),
        '^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$'
      )
ON DUPLICATE KEY UPDATE
    representante_legal = 0,
    habitual = 1;

UPDATE cliente_interesado
SET habitual = 1
WHERE representante_legal = 1;

INSERT INTO cliente_interesado (
    cliente_id,
    interesado_id,
    representante_legal,
    habitual,
    fecha_alta
)
SELECT cliente.id,
       interesado.id,
       1,
       1,
       CURRENT_TIMESTAMP(6)
FROM cliente
JOIN gestion_persona_representante_catalogo catalogo
  ON REGEXP_REPLACE(
       UPPER(COALESCE(NULLIF(catalogo.empresa_nif_normalizado, ''), catalogo.empresa_nif, '')),
       '[^A-Z0-9]',
       '')
     = REGEXP_REPLACE(UPPER(COALESCE(cliente.nif, '')), '[^A-Z0-9]', '')
JOIN interesado
  ON REGEXP_REPLACE(UPPER(COALESCE(interesado.dni, '')), '[^A-Z0-9]', '')
     = REGEXP_REPLACE(
         UPPER(COALESCE(NULLIF(catalogo.representante_nif_normalizado, ''), catalogo.representante_nif, '')),
         '[^A-Z0-9]',
         '')
WHERE REGEXP_LIKE(
        REGEXP_REPLACE(UPPER(COALESCE(cliente.nif, '')), '[^A-Z0-9]', ''),
        '^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$'
      )
ON DUPLICATE KEY UPDATE
    representante_legal = 1,
    habitual = 1;
