package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.AuditoriaDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaDocumentoRepository
        extends JpaRepository<AuditoriaDocumento, Long>, JpaSpecificationExecutor<AuditoriaDocumento> {
}
