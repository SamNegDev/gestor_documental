package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WhatsappWebhookEventoRepository extends JpaRepository<WhatsappWebhookEvento, Long> {
    boolean existsByMessageId(String messageId);

    @Query(
            value = """
                    select evento from WhatsappWebhookEvento evento
                    left join fetch evento.cliente cliente
                    left join fetch evento.expediente expediente
                    where evento.messageId is not null
                      and (:estado = 'TODOS'
                           or (:estado = 'PENDIENTES' and evento.estado = com.example.gestor_documental.enums.EstadoWhatsappEvento.PENDIENTE)
                           or (:estado = 'REVISADOS' and evento.estado = com.example.gestor_documental.enums.EstadoWhatsappEvento.REVISADO)
                           or (:estado = 'ARCHIVADOS' and evento.estado = com.example.gestor_documental.enums.EstadoWhatsappEvento.ARCHIVADO)
                           or (:estado = 'ASOCIADOS' and cliente is not null)
                           or (:estado = 'NO_ASOCIADOS' and cliente is null)
                           or (:estado = 'ERRORES' and evento.procesado = false))
                      and (:telefono is null or evento.telefono like :telefono)
                    order by evento.fechaRecepcion desc
                    """,
            countQuery = """
                    select count(evento) from WhatsappWebhookEvento evento
                    left join evento.cliente cliente
                    where evento.messageId is not null
                      and (:estado = 'TODOS'
                           or (:estado = 'PENDIENTES' and evento.estado = com.example.gestor_documental.enums.EstadoWhatsappEvento.PENDIENTE)
                           or (:estado = 'REVISADOS' and evento.estado = com.example.gestor_documental.enums.EstadoWhatsappEvento.REVISADO)
                           or (:estado = 'ARCHIVADOS' and evento.estado = com.example.gestor_documental.enums.EstadoWhatsappEvento.ARCHIVADO)
                           or (:estado = 'ASOCIADOS' and cliente is not null)
                           or (:estado = 'NO_ASOCIADOS' and cliente is null)
                           or (:estado = 'ERRORES' and evento.procesado = false))
                      and (:telefono is null or evento.telefono like :telefono)
                    """
    )
    Page<WhatsappWebhookEvento> buscarBandeja(@Param("estado") String estado,
                                              @Param("telefono") String telefono,
                                              Pageable pageable);

    @Query("""
            select evento from WhatsappWebhookEvento evento
            left join fetch evento.cliente
            left join fetch evento.expediente expediente
            where evento.messageId is not null
              and evento.estado = :estado
              and expediente is not null
            order by evento.fechaRecepcion asc
            """)
    List<WhatsappWebhookEvento> findByEstadoWithExpediente(@Param("estado") EstadoWhatsappEvento estado);

    @Query("""
            select evento from WhatsappWebhookEvento evento
            left join fetch evento.cliente cliente
            where evento.messageId is not null
              and evento.estado = :estado
              and cliente is null
            order by evento.fechaRecepcion asc
            """)
    List<WhatsappWebhookEvento> findByEstadoWithoutCliente(@Param("estado") EstadoWhatsappEvento estado);

    @Query("""
            select evento from WhatsappWebhookEvento evento
            left join fetch evento.cliente cliente
            left join fetch evento.expediente expediente
            where evento.messageId is not null
              and evento.estado = :estado
              and cliente is not null
              and expediente is null
            order by evento.fechaRecepcion asc
            """)
    List<WhatsappWebhookEvento> findByEstadoWithClienteWithoutExpediente(@Param("estado") EstadoWhatsappEvento estado);

    List<WhatsappWebhookEvento> findByExpedienteIdAndMessageIdIsNotNullOrderByFechaRecepcionDesc(Long expedienteId);
}
