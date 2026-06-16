package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
