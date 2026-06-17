package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.AvisoAdmin;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.repository.AvisoAdminRepository;
import com.example.gestor_documental.service.AvisoAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AvisoAdminServiceImpl implements AvisoAdminService {
    private final AvisoAdminRepository avisoAdminRepository;

    @Override
    @Transactional
    public void crear(String tipo, String titulo, String detalle, String origen, Expediente expediente, Cliente cliente) {
        AvisoAdmin aviso = new AvisoAdmin();
        aviso.setTipo(StringUtils.hasText(tipo) ? tipo : "GENERAL");
        aviso.setTitulo(limitar(titulo, 180, "Aviso del sistema"));
        aviso.setDetalle(limitar(detalle, 2000, null));
        aviso.setOrigen(limitar(origen, 80, null));
        aviso.setExpediente(expediente);
        aviso.setCliente(cliente != null ? cliente : expediente != null ? expediente.getCliente() : null);
        avisoAdminRepository.save(aviso);
    }

    private String limitar(String valor, int maximo, String defecto) {
        String limpio = StringUtils.hasText(valor) ? valor.trim().replaceAll("\\s+", " ") : defecto;
        if (!StringUtils.hasText(limpio)) {
            return null;
        }
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 3) + "...";
    }
}
