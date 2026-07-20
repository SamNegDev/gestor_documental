package com.example.gestor_documental.util;

public final class MensajeAutomaticoUtils {

    private MensajeAutomaticoUtils() {
    }

    public static boolean esResumenAutomatico(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return false;
        }
        String normalizado = contenido.trim().toUpperCase();
        return normalizado.contains("RESUMEN DE TRAMITES -")
                && normalizado.contains("FINALIZADOS HOY:")
                && normalizado.contains("INCIDENCIAS ACTIVAS:");
    }

    public static boolean esAvisoSeguimientoAutomatico(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return false;
        }
        String normalizado = contenido.trim().toUpperCase();
        return normalizado.startsWith("HOLA,")
                && normalizado.contains("EXPEDIENTE:")
                && normalizado.contains("MOTIVO:")
                && normalizado.contains("DETALLE:")
                && normalizado.contains("ACCEDE AL PORTAL:");
    }

    public static boolean esMensajeAutomaticoSeguimiento(String contenido) {
        return esResumenAutomatico(contenido)
                || esAvisoSeguimientoAutomatico(contenido)
                || esObservacionTecnicaIncidencia(contenido);
    }

    public static boolean esObservacionTecnicaIncidencia(String contenido) {
        return contenido != null
                && contenido.trim().equalsIgnoreCase("Incidencia abierta desde el cierre del expediente.");
    }
}
