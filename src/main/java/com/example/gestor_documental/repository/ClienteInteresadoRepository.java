package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.ClienteInteresado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteInteresadoRepository extends JpaRepository<ClienteInteresado, Long> {

    @EntityGraph(attributePaths = "interesado")
    List<ClienteInteresado> findByClienteIdOrderByInteresadoNombreAsc(Long clienteId);

    Optional<ClienteInteresado> findByClienteIdAndInteresadoId(Long clienteId, Long interesadoId);

    boolean existsByClienteIdAndInteresadoId(Long clienteId, Long interesadoId);
}
