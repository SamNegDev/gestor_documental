package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    List<Solicitud> findByClienteId(Long clienteId);

    @Query("select s from Solicitud s order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc")
    List<Solicitud> findAllOrderByFechaReferenciaDesc();

    @Query("select s from Solicitud s where s.cliente.id = :clienteId order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc")
    List<Solicitud> findByClienteIdOrderByFechaReferenciaDesc(Long clienteId);

    List<Solicitud> findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();

    int countByCliente(Cliente cliente);

    int countByClienteAndEstadoSolicitud(Cliente cliente, EstadoSolicitud estadoSolicitud);

    int countByEstadoSolicitud(EstadoSolicitud estadoSolicitud);

    @Query("select s from Solicitud s order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc limit 5")
    List<Solicitud> findTop5OrderByFechaReferenciaDesc();

    @Query("select s from Solicitud s where s.cliente = :cliente order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc limit 5")
    List<Solicitud> findTop5ByClienteOrderByFechaReferenciaDesc(Cliente cliente);

    @Query("""
            select s from Solicitud s
            where (:clienteId is null or s.cliente.id = :clienteId)
              and (upper(coalesce(s.matricula, '')) like :texto
                   or str(s.id) like :identificador
                   or upper(coalesce(s.interesado1Dni, '')) like :texto
                   or upper(concat(coalesce(s.interesado1Nombre, ''), ' ', coalesce(s.interesado1Apellidos, ''))) like :texto
                   or upper(coalesce(s.interesado2Dni, '')) like :texto
                   or upper(concat(coalesce(s.interesado2Nombre, ''), ' ', coalesce(s.interesado2Apellidos, ''))) like :texto)
            order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc
            """)
    List<Solicitud> buscarGlobal(@Param("clienteId") Long clienteId,
                                 @Param("texto") String texto,
                                 @Param("identificador") String identificador,
                                 Pageable pageable);
}
