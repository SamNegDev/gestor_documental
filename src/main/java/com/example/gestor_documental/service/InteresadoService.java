package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Interesado;

import java.util.Optional;

public interface InteresadoService {

    Optional<Interesado> buscarInteresadoPorDNI(String dni);

    Interesado guardar(Interesado nuevoInteresado);
}
