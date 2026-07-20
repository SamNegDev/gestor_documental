package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.validation.DniNieValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocumentoRolesLecturaServiceImplTest {

    @Mock DocumentoService documentoService;
    @Mock DocumentoRepository documentoRepository;
    @Mock DocumentoRolesLecturaRepository lecturaRepository;
    @Mock ExpedienteInteresadoRepository expedienteInteresadoRepository;
    @Mock OpenAiProperties openAiProperties;
    @Mock DniNieValidator dniNieValidator;

    @InjectMocks
    DocumentoRolesLecturaServiceImpl service;

    @Test
    void rolesContrariosALaSolicitudSeIntercambianYExigenRevision() {
        Solicitud solicitud = new Solicitud();
        solicitud.setInteresado1Dni("B12345678");
        solicitud.setInteresado1Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Dni("A87654321");
        solicitud.setInteresado2Rol(RolInteresado.VENDEDOR);

        Documento documento = new Documento();
        documento.setSolicitud(solicitud);
        DocumentoRolesLectura lectura = new DocumentoRolesLectura();
        ObjectNode resultado = new ObjectMapper().createObjectNode();
        resultado.put("vendedorIdentificador", "B12345678");
        resultado.put("vendedorNombre", "Comprador detectado como vendedor");
        resultado.put("compradorIdentificador", "A87654321");
        resultado.put("compradorNombre", "Vendedor detectado como comprador");
        resultado.put("confianzaGlobal", 0.98);
        resultado.put("requiereRevision", false);

        ReflectionTestUtils.invokeMethod(service, "aplicarResultado", documento, lectura, resultado, "modelo-test");

        assertThat(lectura.getVendedorIdentificador()).isEqualTo("A87654321");
        assertThat(lectura.getCompradorIdentificador()).isEqualTo("B12345678");
        assertThat(lectura.getVendedorNombre()).isEqualTo("Vendedor detectado como comprador");
        assertThat(lectura.getCompradorNombre()).isEqualTo("Comprador detectado como vendedor");
        assertThat(lectura.isRequiereRevision()).isTrue();
        assertThat(lectura.getResultadoJson()).contains("revisar la contradiccion");
    }
}
