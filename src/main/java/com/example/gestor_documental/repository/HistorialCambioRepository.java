package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.HistorialCambio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistorialCambioRepository extends JpaRepository<HistorialCambio, Long> {
    List<HistorialCambio> findByExpedienteIdOrderByFechaCambioDesc(Long expedienteId);
    List<HistorialCambio> findBySolicitudIdOrderByFechaCambioDesc(Long solicitudId);
    @Query("""
            select h from HistorialCambio h
            join fetch h.expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            left join fetch h.usuario
            where h.expediente is not null
              and h.fechaCambio >= :desde
              and h.fechaCambio < :hasta
            order by h.fechaCambio asc, h.id asc
            """)
    List<HistorialCambio> findCambiosExpedienteEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
    void deleteBySolicitudId(Long solicitudId);
}
