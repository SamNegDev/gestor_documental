package com.example.gestor_documental.util;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoIdentidadCalidadTest {

    @Test
    void clasificaComoConsistenteCuandoElIdentificadorCoincideConElRegistro() {
        Interesado interesado = new Interesado();
        interesado.setDni("12345678Z");
        interesado.setNombre("ANA PEREZ GARCIA");
        interesado.setDireccion("CALLE MAYOR 12, 38001 SANTA CRUZ");

        DocumentoIdentidadLectura lectura = lectura(interesado, "CALLE MAYOR 12, 38001 SANTA CRUZ");
        var evaluacion = DocumentoIdentidadCalidad.evaluar(lectura, List.of(identidad()));

        assertThat(evaluacion.nivel()).isEqualTo("CONSISTENTE");
        assertThat(evaluacion.indicadores())
                .contains("Identificador fiscal válido", "Vinculada por identificador", "Una identidad válida");
        assertThat(evaluacion.advertencias()).isEmpty();
    }

    @Test
    void avisaCuandoLaDireccionLeidaDifiereSinInvalidarElDni() {
        Interesado interesado = new Interesado();
        interesado.setDni("12345678Z");
        interesado.setNombre("ANA PEREZ GARCIA");
        interesado.setDireccion("CALLE MAYOR 12, 38001 SANTA CRUZ");

        DocumentoIdentidadLectura lectura = lectura(interesado, "AVENIDA DEL MAR 8, 35001 LAS PALMAS");
        var evaluacion = DocumentoIdentidadCalidad.evaluar(lectura, List.of(identidad()));

        assertThat(evaluacion.nivel()).isEqualTo("CON_DIFERENCIAS");
        assertThat(evaluacion.datosDifierenInteresado()).isTrue();
        assertThat(evaluacion.advertencias()).contains("La dirección leída es distinta y no se ha reemplazado.");
    }

    private DocumentoIdentidadLectura lectura(Interesado interesado, String direccion) {
        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setIdentificador("12345678Z");
        lectura.setNombre("ANA");
        lectura.setApellido1("PEREZ");
        lectura.setApellido2("GARCIA");
        lectura.setDireccionTexto(direccion);
        lectura.setConfianzaGlobal(0.99);
        lectura.setInteresadoVinculado(interesado);
        return lectura;
    }

    private IdentidadDetectada identidad() {
        return new IdentidadDetectada("DNI", "12345678Z", "ANA", "PEREZ", "GARCIA",
                null, null, null, null, null, 0.99, false, null);
    }
}
