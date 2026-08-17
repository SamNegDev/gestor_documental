package com.example.gestor_documental.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentificadorFiscalValidatorTest {

    @Test
    void validaYNormalizaDniNieYCif() {
        assertThat(IdentificadorFiscalValidator.esValido("12.345.678-Z")).isTrue();
        assertThat(IdentificadorFiscalValidator.esValido("B-38436556")).isTrue();
        assertThat(IdentificadorFiscalValidator.normalizar(" b-38436556 ")).isEqualTo("B38436556");
    }

    @Test
    void rechazaLetrasDeControlIncorrectas() {
        assertThat(IdentificadorFiscalValidator.esValido("12345678A")).isFalse();
        assertThat(IdentificadorFiscalValidator.esValido("B38436555")).isFalse();
    }
}
