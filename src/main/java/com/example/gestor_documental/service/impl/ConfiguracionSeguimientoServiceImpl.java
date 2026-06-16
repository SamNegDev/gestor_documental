package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoRequest;
import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ConfiguracionSeguimientoRepository;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfiguracionSeguimientoServiceImpl implements ConfiguracionSeguimientoService {
    private final ConfiguracionSeguimientoRepository repository;

    @Override
    @Transactional
    public ConfiguracionSeguimiento obtener() {
        return repository.findById(ConfiguracionSeguimiento.ID_UNICO)
                .orElseGet(() -> repository.save(new ConfiguracionSeguimiento()));
    }

    @Override
    @Transactional
    public ConfiguracionSeguimientoResponse obtenerResponse(Usuario admin) {
        validarAdmin(admin);
        return map(obtener());
    }

    @Override
    @Transactional
    public ConfiguracionSeguimientoResponse actualizar(ConfiguracionSeguimientoRequest request, Usuario admin) {
        validarAdmin(admin);
        validar(request);
        ConfiguracionSeguimiento config = obtener();
        config.setDiasAviso1(request.diasAviso1());
        config.setDiasAviso2(request.diasAviso2());
        config.setDiasAviso3(request.diasAviso3());
        config.setDiasAviso4(request.diasAviso4());
        config.setDiasAviso5(request.diasAviso5());
        config.setMaxAvisos(request.maxAvisos());
        config.setDiasExpedienteEstancado(request.diasExpedienteEstancado());
        return map(repository.save(config));
    }

    private void validar(ConfiguracionSeguimientoRequest request) {
        if (request == null) throw new OperacionInvalidaException("La configuracion es obligatoria.");
        if (request.maxAvisos() < 1 || request.maxAvisos() > 5) {
            throw new OperacionInvalidaException("El maximo de avisos debe estar entre 1 y 5.");
        }
        if (request.diasExpedienteEstancado() < 1 || request.diasExpedienteEstancado() > 365) {
            throw new OperacionInvalidaException("Los dias de expediente estancado deben estar entre 1 y 365.");
        }
        int[] dias = { request.diasAviso1(), request.diasAviso2(), request.diasAviso3(), request.diasAviso4(), request.diasAviso5() };
        for (int dia : dias) {
            if (dia < 1 || dia > 365) {
                throw new OperacionInvalidaException("Los periodos de aviso deben estar entre 1 y 365 dias.");
            }
        }
    }

    private void validarAdmin(Usuario usuario) {
        if (usuario == null || usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede configurar el seguimiento.");
        }
    }

    private ConfiguracionSeguimientoResponse map(ConfiguracionSeguimiento config) {
        return new ConfiguracionSeguimientoResponse(
                config.getDiasAviso1(),
                config.getDiasAviso2(),
                config.getDiasAviso3(),
                config.getDiasAviso4(),
                config.getDiasAviso5(),
                config.getMaxAvisos(),
                config.getDiasExpedienteEstancado()
        );
    }
}
