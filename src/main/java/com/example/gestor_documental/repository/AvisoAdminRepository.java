package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.AvisoAdmin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisoAdminRepository extends JpaRepository<AvisoAdmin, Long> {
    long countByLeidoFalse();

    List<AvisoAdmin> findByLeidoFalseOrderByFechaCreacionDesc(Pageable pageable);
}
