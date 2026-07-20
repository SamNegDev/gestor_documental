package com.example.gestor_documental.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static String observacionClienteActual(String contenido) {
        if (contenido == null || contenido.isBlank()) return null;
        Matcher reclamaciones = Pattern.compile("(?i)RECLAMACI[OÓ]N\\s+ADMIN\\s*:\\s*").matcher(contenido);
        int inicioUltima = -1;
        while (reclamaciones.find()) inicioUltima = reclamaciones.end();
        String visible = inicioUltima >= 0 ? contenido.substring(inicioUltima).trim() : contenido.trim();
        return visible.isBlank() || esObservacionTecnicaIncidencia(visible) ? null : visible;
    }
}
