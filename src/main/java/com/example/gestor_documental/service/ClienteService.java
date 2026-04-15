package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;



public interface ClienteService {
    List<Cliente> listarTodos();

    Optional<Cliente> buscarPorId(Long id);
}
