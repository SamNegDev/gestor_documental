package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface ExpedienteService {

    List<Expediente> listarTodos();

    Optional<Expediente> buscarPorId(Long id);

    Expediente guardar(Expediente expediente);

    void eliminarPorId(Long id);

    long contarTodos();

    List<Expediente> listarPorClienteId(Long clienteId);

    boolean tienePermisoExpediente(Expediente expediente, Usuario usuario);

    int contarPorCliente(Cliente cliente);

    int contarPorClienteYEstado(Cliente cliente, EstadoExpediente estadoExpediente);

    int contarPorEstado(EstadoExpediente estadoExpediente);

    List<Expediente> listarUltimos();

    List<Expediente> listarUltimosPorCliente(Cliente cliente);

    void guardarInteresados(Expediente expediente,
            InteresadoFormDto interesado1,
            InteresadoFormDto interesado2);

    Expediente crearExpedienteCompleto(Expediente expediente, Usuario usuarioLogueado,
            Long clienteId,
            Long tipoTramiteId,
            InteresadoFormDto interesado1,
            InteresadoFormDto interesado2);

    public void validarInteresados(InteresadoFormDto interesado1, InteresadoFormDto interesado2);

    public void guardarInteresadoSiValido(Expediente expediente, InteresadoFormDto dto);

    void cambiarEstado(Long id, EstadoExpediente nuevoEstado, Usuario usuarioLogueado);

    Expediente actualizarExpediente(Long id, Expediente expedienteActualizado, Usuario usuarioLogueado, Long clienteId, Long tipoTramiteId, InteresadoFormDto interesado1, InteresadoFormDto interesado2);
}
