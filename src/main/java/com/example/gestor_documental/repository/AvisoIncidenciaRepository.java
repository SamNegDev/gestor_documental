package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.AvisoIncidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AvisoIncidenciaRepository extends JpaRepository<AvisoIncidencia, Long> {
    List<AvisoIncidencia> findByIncidenciaIdOrderByFechaEnvioAsc(Long incidenciaId);
}
