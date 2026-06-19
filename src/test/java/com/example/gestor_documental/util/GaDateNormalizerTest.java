package com.example.gestor_documental.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GaDateNormalizerTest {

    @Test
    void normalizaFechaSqlConMesEnIngles() {
        assertEquals("04/08/2000", GaDateNormalizer.toGaDate("Aug  4 2000 12:00AM"));
    }

    @Test
    void normalizaFechaIsoConHora() {
        assertEquals("04/08/2000", GaDateNormalizer.toGaDate("2000-08-04 00:00:00"));
    }

    @Test
    void mantieneFormatoGaConDosDigitos() {
        assertEquals("04/08/2000", GaDateNormalizer.toGaDate("4/8/2000"));
    }
}
