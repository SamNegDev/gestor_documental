package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRolUsuario(RolUsuario rolUsuario);

    Optional<Usuario> findFirstByClienteIdAndRolUsuarioAndActivoTrueOrderByIdAsc(Long clienteId, RolUsuario rolUsuario);

    Optional<Usuario> findFirstByRolUsuarioAndActivoTrueOrderByIdAsc(RolUsuario rolUsuario);

    @EntityGraph(attributePaths = {"cliente", "clientesAutorizados"})
    Optional<Usuario> findWithClienteByEmail(String email);

    @EntityGraph(attributePaths = {"cliente", "clientesAutorizados"})
    @Query("select distinct usuario from Usuario usuario order by usuario.nombre, usuario.apellidos, usuario.id")
    List<Usuario> findAllWithClientes();

    @EntityGraph(attributePaths = {"cliente", "clientesAutorizados"})
    @Query("select usuario from Usuario usuario where usuario.id = :id")
    Optional<Usuario> findWithClientesById(@Param("id") Long id);

}
