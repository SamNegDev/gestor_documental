package com.example.gestor_documental.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DireccionNormalizerTest {

    @Test
    void consideraEquivalentesVariantesConCodigoPostalYProvincia() {
        assertThat(DireccionNormalizer.equivalentes(
                "CRUCE AUTOPISTA DEL SUR - SAN ISIDRO GRANADILLA DE ABONA, TENERIFE",
                "CRUCE AUTOPISTA DEL SUR - SAN ISIDRO 38611 GRANADILLA DE ABONA, SANTA CRUZ DE TENERIFE"
        )).isTrue();
    }

    @Test
    void detectaDireccionesConViaAdicionalDistinta() {
        assertThat(DireccionNormalizer.equivalentes(
                "CRUCE AUTOPISTA DEL SUR - SAN ISIDRO GRANADILLA DE ABONA, TENERIFE",
                "CRUCE AUTOPISTA DEL SUR - SAN ISIDRO, CL PRINCESA IFARA,35 A PBJ A3, 38611 GRANADILLA DE ABONA, TENERIFE"
        )).isFalse();
    }
}
