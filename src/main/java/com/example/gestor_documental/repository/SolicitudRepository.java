package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    List<Solicitud> findByClienteId(Long clienteId);
    List<Solicitud> findAllByOrderByFechaCreacionDesc();
    List<Solicitud> findByClienteIdOrderByFechaCreacionDesc(Long clienteId);
    List<Solicitud> findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();

    int countByCliente(Cliente cliente);

    int countByClienteAndEstadoSolicitud(Cliente cliente, EstadoSolicitud estadoSolicitud);

    int countByEstadoSolicitud(EstadoSolicitud estadoSolicitud);

    List<Solicitud> findTop5ByOrderByFechaCreacionDesc();

    List<Solicitud> findTop5ByClienteOrderByFechaCreacionDesc(Cliente cliente);
}
