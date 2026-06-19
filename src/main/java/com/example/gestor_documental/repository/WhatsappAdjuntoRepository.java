package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface WhatsappAdjuntoRepository extends JpaRepository<WhatsappAdjunto, Long> {
    boolean existsByMediaId(String mediaId);

    @Query("""
            select adjunto from WhatsappAdjunto adjunto
            left join fetch adjunto.cliente
            left join fetch adjunto.expediente
            left join fetch adjunto.solicitud
            where adjunto.estado = :estado
            order by adjunto.fechaRecepcion asc
            """)
    List<WhatsappAdjunto> findByEstadoForTareas(@Param("estado") EstadoWhatsappAdjunto estado);

    @Query(value = """
            select adjunto from WhatsappAdjunto adjunto
            left join fetch adjunto.cliente
            left join fetch adjunto.expediente
            left join fetch adjunto.solicitud
            left join fetch adjunto.evento
            where (:estado is null or adjunto.estado = :estado)
            """,
            countQuery = """
            select count(adjunto) from WhatsappAdjunto adjunto
            where (:estado is null or adjunto.estado = :estado)
            """)
    Page<WhatsappAdjunto> buscarBandeja(@Param("estado") EstadoWhatsappAdjunto estado, Pageable pageable);

    @Query("""
            select count(distinct adjunto.mediaId) from WhatsappAdjunto adjunto
            where adjunto.estado = :estado
              and adjunto.telefono = :telefono
              and adjunto.fechaRecepcion >= :desde
              and ((:expedienteId is not null and adjunto.expediente.id = :expedienteId)
                   or (:solicitudId is not null and adjunto.solicitud.id = :solicitudId))
            """)
    long countMediaRegistradosEnContextoDesde(@Param("telefono") String telefono,
                                              @Param("expedienteId") Long expedienteId,
                                              @Param("solicitudId") Long solicitudId,
                                              @Param("estado") EstadoWhatsappAdjunto estado,
                                              @Param("desde") LocalDateTime desde);

    List<WhatsappAdjunto> findByEventoId(Long eventoId);
}
