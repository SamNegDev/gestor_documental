package com.example.gestor_documental.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCompatibilityRunner implements ApplicationRunner {

    private static final String SOLICITUD_ROL_DEFINITION =
            "ENUM('COMPRADOR','COMPRAVENTA','TITULAR','VENDEDOR') NULL";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        if (!isMySql()) {
            return;
        }
        ensureSolicitudRolColumn("interesado1_rol");
        ensureSolicitudRolColumn("interesado2_rol");
        ensureSolicitudRolColumn("interesado3_rol");
        migrateUsuarioClienteAssignments();
    }

    private boolean isMySql() {
        try (var connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("mysql");
        } catch (Exception exception) {
            log.warn("No se pudo detectar el motor de base de datos para compatibilidad de esquema", exception);
            return false;
        }
    }

    private void ensureSolicitudRolColumn(String columnName) {
        try {
            String columnType = jdbcTemplate.queryForObject(
                    """
                    select column_type
                    from information_schema.columns
                    where table_schema = database()
                      and table_name = 'solicitud'
                      and column_name = ?
                    """,
                    String.class,
                    columnName
            );
            if (columnType == null || columnType.toUpperCase().contains("COMPRAVENTA")) {
                return;
            }
            jdbcTemplate.execute("alter table solicitud modify column " + columnName + " " + SOLICITUD_ROL_DEFINITION);
            log.info("Actualizada columna solicitud.{} para admitir COMPRAVENTA", columnName);
        } catch (Exception exception) {
            log.warn("No se pudo verificar/actualizar la columna solicitud.{}", columnName, exception);
        }
    }

    private void migrateUsuarioClienteAssignments() {
        try {
            jdbcTemplate.update("""
                    insert ignore into usuario_cliente (usuario_id, cliente_id)
                    select id, cliente_id
                    from usuario
                    where cliente_id is not null
                    """);
        } catch (Exception exception) {
            log.warn("No se pudieron migrar las asignaciones actuales a usuario_cliente", exception);
        }
    }
}
