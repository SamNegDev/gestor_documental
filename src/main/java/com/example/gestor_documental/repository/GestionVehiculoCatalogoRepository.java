package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.GestionVehiculoCatalogo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GestionVehiculoCatalogoRepository extends JpaRepository<GestionVehiculoCatalogo, Long> {

    Optional<GestionVehiculoCatalogo> findFirstByMatriculaNormalizadaOrderByIdAsc(String matriculaNormalizada);

    @Query("""
            select v
            from GestionVehiculoCatalogo v
            where :q is null
               or lower(coalesce(v.matriculaNormalizada, '')) like :q
               or lower(coalesce(v.matricula, '')) like :q
               or lower(coalesce(v.bastidorNormalizado, '')) like :q
               or lower(coalesce(v.bastidor, '')) like :q
               or lower(coalesce(v.marca, '')) like :q
               or lower(coalesce(v.modeloSugerido, '')) like :q
            order by v.matriculaNormalizada asc, v.id asc
            """)
    List<GestionVehiculoCatalogo> buscar(@Param("q") String q, Pageable pageable);
}
