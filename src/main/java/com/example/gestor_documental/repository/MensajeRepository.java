package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByExpedienteIdOrderByFechaCreacionAsc(Long expedienteId);
    List<Mensaje> findByExpedienteIdInOrderByFechaCreacionAsc(List<Long> expedienteIds);
    List<Mensaje> findBySolicitudIdOrderByFechaCreacionAsc(Long solicitudId);
    void deleteBySolicitudId(Long solicitudId);

    long countByExpedienteIdAndAutorRolUsuarioAndFechaLecturaAdminIsNull(Long expedienteId, com.example.gestor_documental.enums.RolUsuario rolUsuario);

    long countByExpedienteIdAndAutorRolUsuarioAndFechaLecturaClienteIsNull(Long expedienteId, com.example.gestor_documental.enums.RolUsuario rolUsuario);

    @Modifying
    @Query("""
            update Mensaje m
            set m.fechaLecturaAdmin = :fecha
            where m.expediente.id = :expedienteId
              and m.autor.rolUsuario = com.example.gestor_documental.enums.RolUsuario.CLIENTE
              and m.fechaLecturaAdmin is null
            """)
    int marcarLeidosParaAdmin(@Param("expedienteId") Long expedienteId, @Param("fecha") LocalDateTime fecha);

    @Modifying
    @Query("""
            update Mensaje m
            set m.fechaLecturaCliente = :fecha
            where m.expediente.id = :expedienteId
              and m.autor.rolUsuario = com.example.gestor_documental.enums.RolUsuario.ADMIN
              and m.fechaLecturaCliente is null
            """)
    int marcarLeidosParaCliente(@Param("expedienteId") Long expedienteId, @Param("fecha") LocalDateTime fecha);
}
