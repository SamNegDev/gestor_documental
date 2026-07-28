package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.FacturaExpediente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FacturaExpedienteRepository extends JpaRepository<FacturaExpediente, Long> {
    List<FacturaExpediente> findByFacturaIdOrderByIdAsc(Long facturaId);
    Optional<FacturaExpediente> findByExpedienteId(Long expedienteId);
    @Query("select count(v) > 0 from FacturaExpediente v where v.expediente.id = :expedienteId and v.factura.estado <> com.example.gestor_documental.enums.EstadoFacturaHolded.ANULADA")
    boolean existsActivoByExpedienteId(@Param("expedienteId") Long expedienteId);
}