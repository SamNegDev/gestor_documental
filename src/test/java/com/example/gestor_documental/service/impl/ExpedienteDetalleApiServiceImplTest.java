package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.HitoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.OperacionExpedienteResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpedienteDetalleApiServiceImplTest {

    @Test
    void batecomUsaLosHitosDeSusOperacionesParaCalcularElSiguientePaso() {
        HitoExpedienteResponse hitoGeneral = HitoExpedienteResponse.builder()
                .id("tramite-programa-gestion")
                .build();
        HitoExpedienteResponse hitoBate = HitoExpedienteResponse.builder()
                .id("entrega-compraventa-bate-tramite")
                .build();
        HitoExpedienteResponse hitoCom = HitoExpedienteResponse.builder()
                .id("finalizacion-entrega-compraventa-com-tramite")
                .build();
        OperacionExpedienteResponse com = OperacionExpedienteResponse.builder()
                .orden(2)
                .hitos(List.of(hitoCom))
                .build();
        OperacionExpedienteResponse bate = OperacionExpedienteResponse.builder()
                .orden(1)
                .hitos(List.of(hitoBate))
                .build();

        List<HitoExpedienteResponse> resultado = ExpedienteDetalleApiServiceImpl.hitosParaSiguientePaso(
                true,
                List.of(hitoGeneral),
                List.of(com, bate));

        assertThat(resultado).containsExactly(hitoBate, hitoCom);
    }

    @Test
    void expedienteNormalConservaSusHitosGenerales() {
        HitoExpedienteResponse hitoGeneral = HitoExpedienteResponse.builder()
                .id("tramite-programa-gestion")
                .build();

        List<HitoExpedienteResponse> resultado = ExpedienteDetalleApiServiceImpl.hitosParaSiguientePaso(
                false,
                List.of(hitoGeneral),
                List.of());

        assertThat(resultado).containsExactly(hitoGeneral);
    }
}