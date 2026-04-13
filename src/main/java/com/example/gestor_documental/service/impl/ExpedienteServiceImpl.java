package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.service.TipoTramiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpedienteServiceImpl implements ExpedienteService {

    private final ExpedienteRepository expedienteRepository;
    private final InteresadoService interesadoService;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;

    @Override
    public List<Expediente> listarTodos() {
        return expedienteRepository.findAll();
    }

    @Override
    public Optional<Expediente> buscarPorId(Long id) {
        return expedienteRepository.findById(id);
    }

    @Override
    public Expediente guardar(Expediente expediente) {
        return expedienteRepository.save(expediente);
    }

    @Override
    public void eliminarPorId(Long id) {
        expedienteRepository.deleteById(id);
    }

    @Override
    public long contarTodos() {
        return expedienteRepository.count();
    }

    @Override
    public List<Expediente> listarPorClienteId(Long clienteId) {
        return expedienteRepository.findByClienteId(clienteId);
    }

    @Override
    public boolean tienePermisoExpediente(Expediente expediente, Usuario usuario) {

        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            return true;
        }
        //Si el usuarioLogueado/expediente no tiene cliente asignado se deniega el acceso ya que no podemos comprobar de quien es
        if (usuario.getCliente() == null || expediente.getCliente() == null) {
            return false;
        }

        return expediente.getCliente().getId().equals(usuario.getCliente().getId());
    }

    @Override
    public int contarPorCliente(Cliente cliente) {
        return expedienteRepository.countByCliente(cliente);
    }

    @Override
    public int contarPorClienteYEstado(Cliente cliente, EstadoExpediente estadoExpediente) {
        return expedienteRepository.countByClienteAndEstadoExpediente(cliente, estadoExpediente);
    }

    @Override
    public int contarPorEstado(EstadoExpediente estadoExpediente) {
        return expedienteRepository.countByEstadoExpediente(estadoExpediente);
    }

    @Override
    public List<Expediente> listarUltimos() {
        return expedienteRepository.findTop5ByOrderByFechaCreacionDesc();
    }

    @Override
    public List<Expediente> listarUltimosPorCliente(Cliente cliente) {
        return expedienteRepository.findTop5ByClienteOrderByFechaCreacionDesc(cliente);
    }



    @Override
    public void guardarInteresados(Expediente expediente,
                                   InteresadoFormDto interesado1,
                                   InteresadoFormDto interesado2) {

        guardarInteresadoSiValido(expediente, interesado1);
        guardarInteresadoSiValido(expediente, interesado2);
    }

    public void guardarInteresadoSiValido(Expediente expediente, InteresadoFormDto dto) {

        if (interesadoVacio(dto)) {
            return;
        }

        Interesado interesado = interesadoService.buscarInteresadoPorDNI(dto.getDni())
                .orElseGet(() -> {
                    Interesado nuevoInteresado = new Interesado();
                    nuevoInteresado.setNombre(dto.getNombre());
                    nuevoInteresado.setDni(dto.getDni());
                    nuevoInteresado.setTelefono(dto.getTelefono());
                    nuevoInteresado.setDireccion(dto.getDireccion());
                    return interesadoService.guardar(nuevoInteresado);
                });

        boolean yaRelacionado = expedienteInteresadoRepository
                .findByExpedienteIdAndInteresadoId(expediente.getId(), interesado.getId())
                .isPresent();

        if (yaRelacionado) {
            return;
        }

        ExpedienteInteresado relacion = new ExpedienteInteresado();
        relacion.setExpediente(expediente);
        relacion.setInteresado(interesado);
        relacion.setRol(dto.getRol());

        expedienteInteresadoRepository.save(relacion);
    }

    @Override
    public void cambiarEstado(Long id, EstadoExpediente nuevoEstado, Usuario usuarioLogueado) {

        Expediente expediente = expedienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expediente no encontrado"));

        if(!tienePermisoExpediente(expediente,usuarioLogueado)){
            throw new RuntimeException("No tienes permiso para acceder a este expediente");
        }
        if (usuarioLogueado.getRolUsuario() != RolUsuario.ADMIN) {
            throw new RuntimeException("Solo el administrador puede cambiar el estado del expediente.");
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new RuntimeException("No se puede modificar un expediente finalizado");
        }

        expediente.setEstadoExpediente(nuevoEstado);

        expedienteRepository.save(expediente);
    }


    private boolean interesadoValido(InteresadoFormDto dto) {
        return dto != null
                && dto.getNombre() != null && !dto.getNombre().isBlank()
                && dto.getDni() != null && !dto.getDni().isBlank()
                && dto.getRol() != null;
    }
    private boolean interesadoVacio(InteresadoFormDto dto) {
        return dto == null
                || ((dto.getNombre() == null || dto.getNombre().isBlank())
                && (dto.getDni() == null || dto.getDni().isBlank())
                && dto.getRol() == null);
    }
    private void validarInteresado(InteresadoFormDto dto, String nombreInteresado) {
        if (interesadoVacio(dto)) {
            return;
        }

        if (!interesadoValido(dto)) {
            throw new IllegalArgumentException(nombreInteresado + " está incompleto. Debe tener nombre, DNI y rol.");
        }
    }
    public void validarInteresados(InteresadoFormDto interesado1, InteresadoFormDto interesado2) {
        validarInteresado(interesado1, "Interesado 1");
        validarInteresado(interesado2, "Interesado 2");

        if (!interesadoVacio(interesado1) && !interesadoVacio(interesado2)) {
            if (interesado1.getDni().equalsIgnoreCase(interesado2.getDni())) {
                throw new IllegalArgumentException("Los dos interesados no pueden tener el mismo DNI.");
            }
        }
    }
    @Override
    @Transactional
    public Expediente crearExpedienteCompleto(Expediente expediente,
                                              Usuario usuarioLogueado,
                                              Long clienteId,
                                              Long tipoTramiteId,
                                              InteresadoFormDto interesado1,
                                              InteresadoFormDto interesado2) {

        validarInteresados(interesado1, interesado2);

        Cliente cliente = clienteService.buscarPorId(clienteId).orElseThrow();
        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId).orElseThrow();

        expediente.setCliente(cliente);
        expediente.setTipoTramite(tipoTramite);
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
        expediente.setCreadoPor(usuarioLogueado);


        Expediente expedienteGuardado = expedienteRepository.save(expediente);

        guardarInteresadoSiValido(expedienteGuardado, interesado1);
        guardarInteresadoSiValido(expedienteGuardado, interesado2);

        return expedienteGuardado;
    }


}
