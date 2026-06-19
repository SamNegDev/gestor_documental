package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.GestionPersonaRepresentanteCatalogo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GestionPersonaRepresentanteCatalogoRepository extends JpaRepository<GestionPersonaRepresentanteCatalogo, Long> {

    List<GestionPersonaRepresentanteCatalogo> findByEmpresaNifNormalizadoOrderByIdAsc(String empresaNifNormalizado);

    @Query("""
            select r
            from GestionPersonaRepresentanteCatalogo r
            where :q is null
               or lower(coalesce(r.empresaNifNormalizado, '')) like :q
               or lower(coalesce(r.empresaNif, '')) like :q
               or lower(coalesce(r.empresaApellido1RazonSocial, '')) like :q
               or lower(coalesce(r.representanteNifNormalizado, '')) like :q
               or lower(coalesce(r.representanteNif, '')) like :q
               or lower(coalesce(r.representanteApellido1RazonSocial, '')) like :q
               or lower(coalesce(r.representanteApellido2, '')) like :q
               or lower(coalesce(r.representanteNombre, '')) like :q
            order by r.empresaApellido1RazonSocial asc, r.representanteApellido1RazonSocial asc, r.id asc
            """)
    List<GestionPersonaRepresentanteCatalogo> buscar(@Param("q") String q, Pageable pageable);
}
