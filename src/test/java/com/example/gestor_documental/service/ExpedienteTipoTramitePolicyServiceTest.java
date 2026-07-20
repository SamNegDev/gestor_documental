package com.example.gestor_documental.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gestor_documental.enums.TipoTramiteEnum;
import org.junit.jupiter.api.Test;

class ExpedienteTipoTramitePolicyServiceTest {

    private final ExpedienteTipoTramitePolicyService service = new ExpedienteTipoTramitePolicyService();

    @Test
    void notificacionVentaHerenciaYCuestionesVariasNoRequierenModelo620() {
        assertFalse(service.requiereModelo620(TipoTramiteEnum.NOTIFICACION_VENTA));
        assertFalse(service.requiereModelo620(TipoTramiteEnum.HERENCIA));
        assertFalse(service.requiereModelo620(TipoTramiteEnum.CUESTIONES_VARIAS));
    }

    @Test
    void restoDeTramitesRequiereModelo620PorDefecto() {
        assertTrue(service.requiereModelo620(TipoTramiteEnum.TRASPASO));
        assertTrue(service.requiereModelo620((TipoTramiteEnum) null));
        assertTrue(service.requiereModelo620("TIPO_DESCONOCIDO"));
    }
}
