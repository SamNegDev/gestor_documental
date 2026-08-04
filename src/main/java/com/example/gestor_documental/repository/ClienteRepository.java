package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Cliente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByNifIgnoreCase(String nif);

    Optional<Cliente> findByHoldedContactId(String holdedContactId);

    Optional<Cliente> findByEmailIgnoreCase(String email);

    @Query("select c from Cliente c where c.avisoIncidenciasActivo = true and c.horaAvisoIncidencias <= :hora and (c.ultimoAvisoIncidencias is null or c.ultimoAvisoIncidencias < :fecha)")
    List<Cliente> findPendientesAvisoIncidencias(@Param("fecha") LocalDate fecha, @Param("hora") LocalTime hora, Pageable pageable);

    @Query("select c from Cliente c where c.avisoFinalizadosActivo = true and c.horaAvisoFinalizados <= :hora and (c.ultimoAvisoFinalizados is null or c.ultimoAvisoFinalizados < :fecha)")
    List<Cliente> findPendientesAvisoFinalizados(@Param("fecha") LocalDate fecha, @Param("hora") LocalTime hora, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Cliente c set c.ultimoAvisoIncidencias = :fecha where c.id = :clienteId and (c.ultimoAvisoIncidencias is null or c.ultimoAvisoIncidencias < :fecha)")
    int reservarAvisoIncidencias(@Param("clienteId") Long clienteId, @Param("fecha") LocalDate fecha);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Cliente c set c.ultimoAvisoFinalizados = :fecha where c.id = :clienteId and (c.ultimoAvisoFinalizados is null or c.ultimoAvisoFinalizados < :fecha)")
    int reservarAvisoFinalizados(@Param("clienteId") Long clienteId, @Param("fecha") LocalDate fecha);

    long countByUltimoAvisoIncidencias(LocalDate fecha);

    long countByUltimoAvisoFinalizados(LocalDate fecha);
}
