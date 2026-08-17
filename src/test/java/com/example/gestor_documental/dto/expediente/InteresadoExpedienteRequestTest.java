package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolInteresado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteresadoExpedienteRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void interpretaUnRolVacioComoAusente() throws Exception {
        InteresadoExpedienteRequest request = objectMapper.readValue(
                "{\"nombre\":\"EMPRESA\",\"dni\":\"B38436556\",\"rol\":\"\"}",
                InteresadoExpedienteRequest.class);

        assertThat(request.getRol()).isNull();
    }

    @Test
    void aceptaElRolSinDistinguirMayusculas() throws Exception {
        InteresadoExpedienteRequest request = objectMapper.readValue(
                "{\"rol\":\"vendedor\"}",
                InteresadoExpedienteRequest.class);

        assertThat(request.getRol()).isEqualTo(RolInteresado.VENDEDOR);
    }
}
