package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByExpedienteIdOrderByFechaCreacionAsc(Long expedienteId);
    List<Mensaje> findBySolicitudIdOrderByFechaCreacionAsc(Long solicitudId);
}
