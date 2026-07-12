package com.example.gestor_documental.util;

import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoIdentidadLecturaJsonTest {

    @Test
    void ignoraCodigosMrzIdespYUnificaIdentidadesDuplicadas() {
        String resultado = """
                {
                  "identidades": [
                    {
                      "tipoDocumento": "DNI",
                      "identificador": "IDESPBG10194805097503H",
                      "nombre": "MARIA LUISA",
                      "apellido1": "MENENDEZ",
                      "apellido2": "MOREJUDO",
                      "direccionTexto": "CALLE ROSARIO DE GAYA, 89, 2A, 38329 EL ROSARIO DE TENERIFE, SANTA CRUZ DE TENERIFE",
                      "tipoVia": "CALLE",
                      "nombreVia": "ROSARIO DE GAYA",
                      "numeroVia": "89",
                      "piso": "2",
                      "puerta": "A",
                      "codigoPostal": "38329",
                      "municipio": "EL ROSARIO",
                      "provincia": "SANTA CRUZ DE TENERIFE",
                      "confianzaGlobal": 0.95,
                      "requiereRevision": false
                    },
                    {
                      "tipoDocumento": "DNI",
                      "identificador": "IDESPCGG164293142793999S",
                      "nombre": "ANTONIO",
                      "apellido1": "MALDONADO",
                      "apellido2": "CARMONA",
                      "confianzaGlobal": 0.95,
                      "requiereRevision": false
                    },
                    {
                      "tipoDocumento": "DNI",
                      "identificador": "50975033H",
                      "nombre": "MARIA LUISA",
                      "apellido1": "MENENDEZ",
                      "apellido2": "MOREJUDO",
                      "fechaNacimiento": "20/03/1975",
                      "direccionTexto": "EL ROSARIO, SANTA CRUZ DE TENERIFE",
                      "confianzaGlobal": 0.93,
                      "requiereRevision": false
                    },
                    {
                      "tipoDocumento": "DNI",
                      "identificador": "42793999S",
                      "nombre": "ANTONIO",
                      "apellido1": "MALDONADO",
                      "apellido2": "CARMONA",
                      "confianzaGlobal": 0.94,
                      "requiereRevision": false
                    }
                  ]
                }
                """;

        List<IdentidadDetectada> identidades = DocumentoIdentidadLecturaJson.extraer(resultado);

        assertThat(identidades).hasSize(2);
        assertThat(identidades).extracting(IdentidadDetectada::identificador)
                .containsExactly("50975033H", "42793999S");
        assertThat(identidades).extracting(IdentidadDetectada::nombre)
                .containsExactly("MARIA LUISA", "ANTONIO");
        assertThat(identidades.get(0).direccionTexto())
                .isEqualTo("CALLE ROSARIO DE GAYA, 89, 2A, 38329 EL ROSARIO DE TENERIFE, SANTA CRUZ DE TENERIFE");
        assertThat(identidades.get(0).tipoVia()).isEqualTo("CALLE");
        assertThat(identidades.get(0).nombreVia()).isEqualTo("ROSARIO DE GAYA");
        assertThat(identidades.get(0).codigoPostal()).isEqualTo("38329");
        assertThat(identidades.get(0).municipio()).isEqualTo("EL ROSARIO");
    }
}
