package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.TipoIncidencia;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoIncidenciaRepository extends JpaRepository<TipoIncidencia, Long> {
    Optional<TipoIncidencia> findByNombre(TipoIncidenciaEnum nombre);
}
