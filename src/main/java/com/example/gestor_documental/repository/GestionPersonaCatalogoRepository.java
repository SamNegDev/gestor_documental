package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.GestionPersonaCatalogo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GestionPersonaCatalogoRepository extends JpaRepository<GestionPersonaCatalogo, Long> {

    Optional<GestionPersonaCatalogo> findFirstByNifNormalizadoOrderByIdAsc(String nifNormalizado);

    @Query("""
            select p
            from GestionPersonaCatalogo p
            where :q is null
               or lower(coalesce(p.nifNormalizado, '')) like :q
               or lower(coalesce(p.nif, '')) like :q
               or lower(coalesce(p.apellido1RazonSocial, '')) like :q
               or lower(coalesce(p.apellido2, '')) like :q
               or lower(coalesce(p.nombre, '')) like :q
            order by p.apellido1RazonSocial asc, p.nombre asc, p.id asc
            """)
    List<GestionPersonaCatalogo> buscar(@Param("q") String q, Pageable pageable);
}
