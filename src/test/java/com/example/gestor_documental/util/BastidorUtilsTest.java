package com.example.gestor_documental.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BastidorUtilsTest {

    @Test
    void normalizaSeparadoresYMinusculas() {
        assertThat(BastidorUtils.normalizar(" sjnfcaf15u-7279300 "))
                .isEqualTo("SJNFCAF15U7279300");
    }

    @Test
    void admiteLecturasAutomaticasConUnMargenControlado() {
        assertThat(BastidorUtils.esLecturaAutomaticaPlausible("SJNFCAF15U7279300")).isTrue();
        assertThat(BastidorUtils.esLecturaAutomaticaPlausible("12345678901234567890")).isTrue();
    }

    @Test
    void rechazaCodigosLargosConfundidosConOtrosCampos() {
        assertThat(BastidorUtils.esLecturaAutomaticaPlausible("647E2D452A0F4F1281667A6257798850")).isFalse();
    }

    @Test
    void mantieneMargenDePersistenciaHastaCuarentaCaracteres() {
        assertThat(BastidorUtils.excedeLongitudMaxima("1".repeat(40))).isFalse();
        assertThat(BastidorUtils.excedeLongitudMaxima("1".repeat(41))).isTrue();
    }
}
