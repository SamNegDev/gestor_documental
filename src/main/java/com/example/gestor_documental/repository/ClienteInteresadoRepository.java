package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.ClienteInteresado;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteInteresadoRepository extends JpaRepository<ClienteInteresado, Long> {

    @EntityGraph(attributePaths = "interesado")
    List<ClienteInteresado> findByClienteIdOrderByInteresadoNombreAsc(Long clienteId);

    @EntityGraph(attributePaths = "interesado")
    @Query("""
            select ci
            from ClienteInteresado ci
            join ci.interesado i
            where ci.cliente.id = :clienteId
              and (:texto is null
                   or upper(coalesce(i.dni, '')) like :texto
                   or upper(coalesce(i.nombre, '')) like :texto)
            order by i.nombre asc
            """)
    List<ClienteInteresado> buscarPorClienteYTexto(
            @Param("clienteId") Long clienteId,
            @Param("texto") String texto,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "interesado")
    List<ClienteInteresado> findByClienteIdAndRepresentanteLegalTrueOrderByInteresadoNombreAsc(Long clienteId);

    Optional<ClienteInteresado> findByClienteIdAndInteresadoId(Long clienteId, Long interesadoId);

    boolean existsByClienteIdAndInteresadoId(Long clienteId, Long interesadoId);

    @Query("""
            select count(ci) > 0
            from ClienteInteresado ci
            join ci.interesado i
            where ci.cliente.id = :clienteId
              and upper(replace(replace(replace(coalesce(i.dni, ''), ' ', ''), '-', ''), '.', '')) = :identificador
            """)
    boolean existsByClienteIdAndInteresadoIdentificador(
            @Param("clienteId") Long clienteId,
            @Param("identificador") String identificador
    );

    boolean existsByClienteIdAndInteresadoIdAndRepresentanteLegalTrue(Long clienteId, Long interesadoId);
}
