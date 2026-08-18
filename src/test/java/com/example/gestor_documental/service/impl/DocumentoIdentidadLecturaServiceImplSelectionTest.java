package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.repository.*;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import com.example.gestor_documental.validation.DniNieValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DocumentoIdentidadLecturaServiceImplSelectionTest {
    @Mock DocumentoService documentoService;
    @Mock DocumentoRepository documentoRepository;
    @Mock DocumentoIdentidadLecturaRepository lecturaRepository;
    @Mock ExpedienteInteresadoRepository expedienteInteresadoRepository;
    @Mock InteresadoRepository interesadoRepository;
    @Mock ClienteInteresadoRepository clienteInteresadoRepository;
    @Mock RequisitoDocumentalExpedienteService requisitoDocumentalExpedienteService;
    @Mock OpenAiProperties openAiProperties;
    @Mock DniNieValidator dniNieValidator;

    @InjectMocks DocumentoIdentidadLecturaServiceImpl service;

    @Test
    void seleccionaLaIdentidadQueCoincideConLaSolicitudAunqueTengaMenorConfianza() {
        Solicitud solicitud = new Solicitud();
        solicitud.setInteresado1Dni("12345678Z");
        Documento documento = new Documento();
        documento.setSolicitud(solicitud);

        IdentidadDetectada ajena = identidad("00000000T", 0.99);
        IdentidadDetectada esperada = identidad("12345678Z", 0.86);

        IdentidadDetectada seleccionada = ReflectionTestUtils.invokeMethod(
                service, "identidadPrincipal", documento, List.of(ajena, esperada));

        assertEquals("12345678Z", seleccionada.identificador());
    }

    @Test
    void seleccionaElCifDelClienteAunqueTengaMenorConfianza() {
        Cliente cliente = new Cliente();
        cliente.setId(4L);
        cliente.setNif("B38436556");
        Solicitud solicitud = new Solicitud();
        solicitud.setCliente(cliente);
        Documento documento = new Documento();
        documento.setSolicitud(solicitud);

        IdentidadDetectada ajena = identidad("00000000T", 0.99);
        IdentidadDetectada esperada = identidad("B38436556", 0.82);

        IdentidadDetectada seleccionada = ReflectionTestUtils.invokeMethod(
                service, "identidadPrincipal", documento, List.of(ajena, esperada));

        assertEquals("B38436556", seleccionada.identificador());
    }

    @Test
    void completaHuecosPeroNoReemplazaDatosConsolidadosDelInteresado() {
        Interesado interesado = new Interesado();
        interesado.setNombre("NOMBRE REVISADO");
        interesado.setDireccion("CALLE CONSOLIDADA 1");
        interesado.setNombreVia("CONSOLIDADA");

        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setNombre("OTRO NOMBRE");
        lectura.setDireccionTexto("AVENIDA PROPUESTA 2");
        lectura.setNombreVia("PROPUESTA");
        lectura.setPortal("3");

        ReflectionTestUtils.invokeMethod(service, "actualizarInteresadoDesdeLectura", interesado, lectura);

        assertEquals("NOMBRE REVISADO", interesado.getNombre());
        assertEquals("CALLE CONSOLIDADA 1", interesado.getDireccion());
        assertEquals("CONSOLIDADA", interesado.getNombreVia());
        assertEquals("3", interesado.getPortal());
        assertNull(interesado.getApellido1());
    }

    @Test
    void unaLecturaIaDeSolicitudNoCreaUnaFichaGlobalDeInteresado() {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(519L);
        Documento documento = new Documento();
        documento.setSolicitud(solicitud);
        IdentidadDetectada lecturaIa = identidad("12345678Z", 0.96);
        when(interesadoRepository.findByDni("12345678Z")).thenReturn(Optional.empty());

        Interesado vinculado = ReflectionTestUtils.invokeMethod(
                service, "resolverInteresadoVinculado", documento, "12345678Z", lecturaIa, TipoDocumento.DNI);

        assertNull(vinculado);
        verify(interesadoRepository, never()).save(any(Interesado.class));
        verify(clienteInteresadoRepository, never()).save(any());
    }

    private IdentidadDetectada identidad(String identificador, double confianza) {
        return new IdentidadDetectada("DNI", identificador, "NOMBRE", "APELLIDO", null,
                null, null, null, null, null, confianza, false, null);
    }
}
