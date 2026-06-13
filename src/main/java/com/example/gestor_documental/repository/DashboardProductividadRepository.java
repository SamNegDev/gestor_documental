package com.example.gestor_documental.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardProductividadRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public LocalDate fechaPrimerExpediente() {
        Object value = entityManager.createNativeQuery("select min(fecha_creacion) from expediente").getSingleResult();
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T')).toLocalDate();
    }

    public long contarCreados(LocalDateTime desde, LocalDateTime hasta) {
        return number(query("""
                select count(*)
                from expediente
                where fecha_creacion >= :desde and fecha_creacion < :hasta
                """, desde, hasta).getSingleResult()).longValue();
    }

    public long contarFinalizados(LocalDateTime desde, LocalDateTime hasta) {
        return number(query("""
                select count(distinct expediente_id)
                from historial_cambio
                where accion = 'CAMBIO ESTADO'
                  and descripcion like '%FINALIZADO%'
                  and fecha_cambio >= :desde and fecha_cambio < :hasta
                """, desde, hasta).getSingleResult()).longValue();
    }

    public double tiempoMedioFinalizacion(LocalDateTime desde, LocalDateTime hasta) {
        Object value = query("""
                select coalesce(avg(timestampdiff(hour, e.fecha_creacion, cierre.fecha_finalizacion)) / 24, 0)
                from expediente e
                join (
                    select expediente_id, min(fecha_cambio) as fecha_finalizacion
                    from historial_cambio
                    where accion = 'CAMBIO ESTADO' and descripcion like '%FINALIZADO%'
                    group by expediente_id
                ) cierre on cierre.expediente_id = e.id
                where cierre.fecha_finalizacion >= :desde and cierre.fecha_finalizacion < :hasta
                """, desde, hasta).getSingleResult();
        return number(value).doubleValue();
    }

    public long contarEnCurso() {
        return number(entityManager.createNativeQuery("""
                select count(*) from expediente
                where estado_expediente not in ('FINALIZADO', 'RECHAZADO')
                """).getSingleResult()).longValue();
    }

    public long contarIncidenciasActivas() {
        return number(entityManager.createNativeQuery("""
                select count(*) from incidencia
                where resuelta = false and expediente_id is not null
                """).getSingleResult()).longValue();
    }

    public long contarExpedientesConDocumentacionPendiente() {
        return number(entityManager.createNativeQuery("""
                select count(distinct expediente_id)
                from requisito_documental_expediente
                where estado = 'REQUERIDO'
                """).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> creadosPorDia(LocalDateTime desde, LocalDateTime hasta) {
        return query("""
                select date(fecha_creacion), count(*)
                from expediente
                where fecha_creacion >= :desde and fecha_creacion < :hasta
                group by date(fecha_creacion)
                order by date(fecha_creacion)
                """, desde, hasta).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> finalizadosPorDia(LocalDateTime desde, LocalDateTime hasta) {
        return query("""
                select date(fecha_cambio), count(distinct expediente_id)
                from historial_cambio
                where accion = 'CAMBIO ESTADO'
                  and descripcion like '%FINALIZADO%'
                  and fecha_cambio >= :desde and fecha_cambio < :hasta
                group by date(fecha_cambio)
                order by date(fecha_cambio)
                """, desde, hasta).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> tiemposPorTramite(LocalDateTime desde, LocalDateTime hasta) {
        return query("""
                select tt.nombre,
                       count(*),
                       coalesce(avg(timestampdiff(hour, e.fecha_creacion, cierre.fecha_finalizacion)) / 24, 0)
                from expediente e
                join tipo_tramite tt on tt.id = e.tipo_tramite_id
                join (
                    select expediente_id, min(fecha_cambio) as fecha_finalizacion
                    from historial_cambio
                    where accion = 'CAMBIO ESTADO' and descripcion like '%FINALIZADO%'
                    group by expediente_id
                ) cierre on cierre.expediente_id = e.id
                where cierre.fecha_finalizacion >= :desde and cierre.fecha_finalizacion < :hasta
                group by tt.nombre
                order by count(*) desc, tt.nombre
                limit 6
                """, desde, hasta).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> volumenPorCliente(LocalDateTime desde, LocalDateTime hasta) {
        return query("""
                select c.id, c.nombre, count(e.id),
                       sum(case when cierre.fecha_finalizacion >= :desde and cierre.fecha_finalizacion < :hasta then 1 else 0 end)
                from cliente c
                join expediente e on e.cliente_id = c.id
                left join (
                    select expediente_id, min(fecha_cambio) as fecha_finalizacion
                    from historial_cambio
                    where accion = 'CAMBIO ESTADO' and descripcion like '%FINALIZADO%'
                    group by expediente_id
                ) cierre on cierre.expediente_id = e.id
                where e.fecha_creacion >= :desde and e.fecha_creacion < :hasta
                group by c.id, c.nombre
                order by count(e.id) desc, c.nombre
                limit 6
                """, desde, hasta).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> cuellosBotella() {
        return entityManager.createNativeQuery("""
                select fase, count(*), coalesce(avg(dias_sin_actividad), 0)
                from (
                    select case
                        when e.estado_expediente = 'PENDIENTE_DOCUMENTACION' then 'DOCUMENTACION'
                        when e.estado_expediente = 'SOLICITADA_INFORMACION_ADICIONAL' then 'RESPUESTA_CLIENTE'
                        when e.estado_expediente = 'INCIDENCIA' then 'INCIDENCIA'
                        when e.estado_expediente in ('REVISANDO_INCIDENCIAS', 'INFORMACION_ADICIONAL_RECIBIDA') then 'REVISION_APORTACION'
                        when e.estado_expediente = 'ENVIADO_DGT' then 'CIERRE'
                        when not exists (
                            select 1 from hito_expediente h
                            where h.expediente_id = e.id
                              and h.codigo in ('TRAMITE_PROGRAMA_GESTION', 'BATE_TRAMITE_PROGRAMA_GESTION', 'COM_TRAMITE_PROGRAMA_GESTION')
                        ) then 'COMPROBACION'
                        when not exists (
                            select 1 from hito_expediente h
                            where h.expediente_id = e.id
                              and h.codigo in ('MODELO_620_PRESENTADO', 'BATE_MODELO_620_PRESENTADO', 'COM_MODELO_620_PRESENTADO')
                        ) then 'MODELO_620'
                        else 'CIERRE'
                    end as fase,
                    timestampdiff(day, coalesce(e.fecha_ultima_modificacion, e.fecha_creacion), now()) as dias_sin_actividad
                    from expediente e
                    where e.estado_expediente not in ('FINALIZADO', 'RECHAZADO')
                ) fases
                group by fase
                order by count(*) desc, avg(dias_sin_actividad) desc
                """).getResultList();
    }

    private Query query(String sql, LocalDateTime desde, LocalDateTime hasta) {
        return entityManager.createNativeQuery(sql)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta);
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }
}
