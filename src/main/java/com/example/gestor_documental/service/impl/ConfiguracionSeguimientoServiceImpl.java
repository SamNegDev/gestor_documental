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
                .map(this::normalizarConfiguracionAnterior)
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
        config.setDiasPrimerAviso(request.diasPrimerAviso());
        config.setAutomatizacionActiva(request.automatizacionActiva());
        config.setModoSupervisado(request.modoSupervisado());
        config.setDiasEnvio(request.diasEnvio().trim().toUpperCase());
        config.setHoraEnvio(request.horaEnvio());
        config.setTamanioLote(request.tamanioLote());
        config.setCanalAutomatico(request.canalAutomatico().trim().toUpperCase());
        return map(repository.save(config));
    }

    private ConfiguracionSeguimiento normalizarConfiguracionAnterior(ConfiguracionSeguimiento config) {
        boolean anterior = config.getDiasEnvio() == null || config.getDiasEnvio().isBlank()
                || config.getCanalAutomatico() == null || config.getCanalAutomatico().isBlank()
                || config.getTamanioLote() <= 0;
        if (!anterior) {
            return config;
        }
        config.setDiasPrimerAviso(2);
        config.setAutomatizacionActiva(false);
        config.setModoSupervisado(true);
        config.setDiasEnvio("LABORABLES");
        config.setHoraEnvio(9);
        config.setTamanioLote(50);
        config.setCanalAutomatico("EMAIL");
        return repository.save(config);
    }
    private void validar(ConfiguracionSeguimientoRequest request) {
        if (request == null) throw new OperacionInvalidaException("La configuracion es obligatoria.");
        if (request.maxAvisos() < 1 || request.maxAvisos() > 5) {
            throw new OperacionInvalidaException("El maximo de avisos debe estar entre 1 y 5.");
        }
        if (request.diasExpedienteEstancado() < 1 || request.diasExpedienteEstancado() > 365) {
            throw new OperacionInvalidaException("Los dias de expediente estancado deben estar entre 1 y 365.");
        }
        if (request.diasPrimerAviso() < 0 || request.diasPrimerAviso() > 365) {
            throw new OperacionInvalidaException("La espera del primer aviso debe estar entre 0 y 365 dias.");
        }
        if (!"LABORABLES".equalsIgnoreCase(request.diasEnvio()) && !"TODOS".equalsIgnoreCase(request.diasEnvio())) {
            throw new OperacionInvalidaException("Los dias de envio deben ser LABORABLES o TODOS.");
        }
        if (request.horaEnvio() < 0 || request.horaEnvio() > 23) {
            throw new OperacionInvalidaException("La hora de envio debe estar entre 0 y 23.");
        }
        if (request.tamanioLote() < 1 || request.tamanioLote() > 500) {
            throw new OperacionInvalidaException("El lote de envios debe estar entre 1 y 500.");
        }
        if (!"EMAIL".equalsIgnoreCase(request.canalAutomatico()) && !"WHATSAPP".equalsIgnoreCase(request.canalAutomatico())) {
            throw new OperacionInvalidaException("El canal automatico debe ser EMAIL o WHATSAPP.");
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
                config.getDiasExpedienteEstancado(),
                config.getDiasPrimerAviso(),
                config.isAutomatizacionActiva(),
                config.isModoSupervisado(),
                config.getDiasEnvio(),
                config.getHoraEnvio(),
                config.getTamanioLote(),
                config.getCanalAutomatico()
        );
    }
}
