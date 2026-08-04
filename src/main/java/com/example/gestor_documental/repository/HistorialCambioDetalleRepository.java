package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.HistorialCambioDetalle;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialCambioDetalleRepository extends JpaRepository<HistorialCambioDetalle, Long> {

    List<HistorialCambioDetalle> findByHistorialCambioIdInOrderByHistorialCambioIdAscIdAsc(Collection<Long> historialIds);
}
