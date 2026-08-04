package com.example.gestor_documental.dto.historial;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Valor anterior y posterior de un campo relevante asociado a una entrada del historial.
 */
public record DetalleCambioHistorial(
        String campo,
        String etiqueta,
        String valorAnterior,
        String valorPosterior
) {

    public static DetalleCambioHistorial de(String campo, String etiqueta, Object valorAnterior, Object valorPosterior) {
        String anterior = texto(valorAnterior);
        String posterior = texto(valorPosterior);
        if (Objects.equals(anterior, posterior)) {
            return null;
        }
        return new DetalleCambioHistorial(campo, etiqueta, anterior, posterior);
    }

    public static List<DetalleCambioHistorial> lista(DetalleCambioHistorial... detalles) {
        return Arrays.stream(detalles).filter(Objects::nonNull).toList();
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Enum<?> enumerado) {
            return enumerado.name();
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }
}
