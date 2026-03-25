package com.example.gestor_documental.repository;


import com.example.gestor_documental.model.Interesado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InteresadoRepository extends JpaRepository<Interesado, Long> {

    Optional<Interesado> findByDni(String dni);

}
