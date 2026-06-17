package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoTramiteRepository extends JpaRepository<TipoTramite, Long> {
    Optional<TipoTramite> findByNombre(TipoTramiteEnum nombre);
}
