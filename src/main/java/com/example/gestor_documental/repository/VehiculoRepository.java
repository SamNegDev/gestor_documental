package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {
    Optional<Vehiculo> findByMatricula(String matricula);
}
