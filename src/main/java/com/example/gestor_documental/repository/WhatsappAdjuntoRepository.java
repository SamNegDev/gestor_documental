package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface WhatsappAdjuntoRepository extends JpaRepository<WhatsappAdjunto, Long> {
    boolean existsByMediaId(String mediaId);

    @Query("""
            select adjunto from WhatsappAdjunto adjunto
            left join fetch adjunto.cliente
            left join fetch adjunto.expediente
            where adjunto.estado = :estado
            order by adjunto.fechaRecepcion asc
            """)
    List<WhatsappAdjunto> findByEstadoForTareas(@Param("estado") EstadoWhatsappAdjunto estado);

    @Query(value = """
            select adjunto from WhatsappAdjunto adjunto
            left join fetch adjunto.cliente
            left join fetch adjunto.expediente
            left join fetch adjunto.evento
            where (:estado is null or adjunto.estado = :estado)
            """,
            countQuery = """
            select count(adjunto) from WhatsappAdjunto adjunto
            where (:estado is null or adjunto.estado = :estado)
            """)
    Page<WhatsappAdjunto> buscarBandeja(@Param("estado") EstadoWhatsappAdjunto estado, Pageable pageable);

    List<WhatsappAdjunto> findByEventoId(Long eventoId);
}
