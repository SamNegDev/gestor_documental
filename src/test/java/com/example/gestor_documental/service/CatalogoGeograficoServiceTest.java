package com.example.gestor_documental.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CatalogoGeograficoServiceTest {

    private CatalogoGeograficoService service;

    @BeforeEach
    void setUp() {
        service = new CatalogoGeograficoService(
                new ClassPathResource("catalogos/municipios_ine_2026.csv"),
                new ClassPathResource("catalogos/codigos_postales_gestion_trafico.tsv"),
                new ClassPathResource("catalogos/codigos_postales_geonames_es.tsv")
        );
        service.cargarCatalogo();
    }

    @Test
    void cargaElCatalogoOficialCompleto() {
        assertThat(service.provincias()).hasSize(52);
        assertThat(service.buscarMunicipios(null, null, 0, 9_000).getTotalElementos()).isEqualTo(8_132);
    }

    @Test
    void filtraYPaginaMunicipiosSinDependerDeAcentos() {
        var resultado = service.buscarMunicipios("Santa Cruz de Tenerife", "orotava", 0, 10);

        assertThat(resultado.getContenido())
                .extracting("nombre")
                .containsExactly("Orotava, La");
    }

    @Test
    void devuelveCodigosPostalesYLocalidadesPorMunicipio() {
        var resultado = service.buscarCodigosPostales("Santa Cruz de Tenerife", "Santa Cruz de Tenerife");

        assertThat(resultado).extracting("codigoPostal").contains("38001", "38007", "38107");
        assertThat(resultado).anySatisfy(item -> {
            assertThat(item.codigoPostal()).isEqualTo("38107");
            assertThat(item.localidad()).isEqualTo("GALLEGA, LA");
        });
    }
    @Test
    void asignaElCodigoPostal38108ALaLaguna() {
        var resultado = service.buscarPorCodigoPostal("38108");

        assertThat(resultado).hasSize(5);
        assertThat(resultado).extracting("localidad")
                .containsExactly("ANDENES, LOS", "CHUMBERAS, LAS", "SAN CRISTÓBAL DE LA LAGUNA", "SAN MATIAS", "TACO");
        assertThat(resultado).allSatisfy(item -> {
            assertThat(item.municipioCodigo()).isEqualTo("38023");
            assertThat(item.municipio()).isEqualTo("San Cristóbal de La Laguna");
        });
    }
    @Test
    void resuelveElNombreOficialIgnorandoMayusculasYAcentos() {
        var municipio = service.resolverMunicipio("38", "Santa Cruz de Tenerife");

        assertThat(municipio).isPresent();
        assertThat(municipio.orElseThrow().codigo()).isEqualTo("38038");
        assertThat(municipio.orElseThrow().nombre()).isEqualTo("Santa Cruz de Tenerife");
    }
}
