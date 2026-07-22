package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.AudienciaHistorial;
import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.TipoActividadHistorial;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistorialCambioTest {

    @Test
    void clasificaCambiosDeEstadoComoVisiblesParaCliente() {
        HistorialCambio cambio = new HistorialCambio("CAMBIO ESTADO", "EN_TRAMITE -> FINALIZADO", null, null, null);

        assertEquals(CategoriaHistorial.ESTADO, cambio.getCategoriaClasificada());
        assertEquals(AudienciaHistorial.AMBOS, cambio.getAudienciaClasificada());
    }

    @Test
    void mantieneOperacionesSensiblesDeDocumentoComoInternas() {
        HistorialCambio cambio = new HistorialCambio("DESCARGA DOCUMENTO", "DNI.pdf", null, null, null);

        assertEquals(CategoriaHistorial.DOCUMENTO, cambio.getCategoriaClasificada());
        assertEquals(AudienciaHistorial.INTERNO, cambio.getAudienciaClasificada());
    }

    @Test
    void clasificaLasComunicacionesComoVisiblesSinDependerDelTexto() {
        HistorialCambio cambio = new HistorialCambio("NOTIFICACION", "Aviso enviado", null, null, null);
        cambio.setTipoActividad(TipoActividadHistorial.COMUNICACION);

        assertEquals(CategoriaHistorial.COMUNICACION, cambio.getCategoriaClasificada());
        assertEquals(AudienciaHistorial.AMBOS, cambio.getAudienciaClasificada());
    }
}
