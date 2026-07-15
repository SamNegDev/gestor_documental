package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Expediente;
import org.springframework.stereotype.Service;

@Service
public class ExpedienteTipoTramitePolicyService {

    public boolean requiereModelo620(Expediente expediente) {
        TipoTramiteEnum tramite = expediente != null && expediente.getTipoTramite() != null
                ? expediente.getTipoTramite().getNombre()
                : null;
        return requiereModelo620(tramite);
    }

    public boolean requiereModelo620(String tipoTramite) {
        if (tipoTramite == null || tipoTramite.isBlank()) {
            return true;
        }
        try {
            return requiereModelo620(TipoTramiteEnum.valueOf(tipoTramite));
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    public boolean requiereModelo620(TipoTramiteEnum tramite) {
        return tramite != TipoTramiteEnum.NOTIFICACION_VENTA
                && tramite != TipoTramiteEnum.HERENCIA;
    }
}
