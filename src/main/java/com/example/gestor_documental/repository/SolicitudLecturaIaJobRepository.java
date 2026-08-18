package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoLecturaIaJob;
import com.example.gestor_documental.model.SolicitudLecturaIaJob;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitudLecturaIaJobRepository extends JpaRepository<SolicitudLecturaIaJob, Long> {

    @EntityGraph(attributePaths = {"solicitud", "creadoPor"})
    Optional<SolicitudLecturaIaJob> findTopBySolicitudIdOrderByFechaCreacionDescIdDesc(Long solicitudId);

    @EntityGraph(attributePaths = {"solicitud", "creadoPor"})
    Optional<SolicitudLecturaIaJob> findTopBySolicitudIdAndEstadoInOrderByFechaCreacionDescIdDesc(
            Long solicitudId, Collection<EstadoLecturaIaJob> estados);

    List<SolicitudLecturaIaJob> findByEstadoInOrderByFechaCreacionAsc(Collection<EstadoLecturaIaJob> estados);

    @Modifying(flushAutomatically = true)
    @Query("delete from SolicitudLecturaIaJob job where job.solicitud.id = :solicitudId")
    int deleteBySolicitudId(@Param("solicitudId") Long solicitudId);
}
