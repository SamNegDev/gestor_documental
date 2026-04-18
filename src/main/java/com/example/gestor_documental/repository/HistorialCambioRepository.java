package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.HistorialCambio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialCambioRepository extends JpaRepository<HistorialCambio, Long> {
    List<HistorialCambio> findByExpedienteIdOrderByFechaCambioDesc(Long expedienteId);
    List<HistorialCambio> findBySolicitudIdOrderByFechaCambioDesc(Long solicitudId);
}
