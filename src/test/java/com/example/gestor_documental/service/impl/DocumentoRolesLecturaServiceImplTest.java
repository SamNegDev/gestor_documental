package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoRolesLecturaServiceImplTest {

    @Mock DocumentoService documentoService;
    @Mock DocumentoRepository documentoRepository;
    @Mock DocumentoRolesLecturaRepository lecturaRepository;
    @Mock ExpedienteInteresadoRepository expedienteInteresadoRepository;
    @Mock OperacionExpedienteRepository operacionExpedienteRepository;
    @Mock OpenAiProperties openAiProperties;
    @Mock DniNieValidator dniNieValidator;

    @InjectMocks
    DocumentoRolesLecturaServiceImpl service;

    @Test
    void asignaBateCuandoLaCompraventaEsCompradora() {
        Expediente expediente = expedienteBatecom();
        Interesado vendedor = interesado(10L, "11111111H");
        Interesado compraventa = interesado(20L, "B76527464");
        Documento documento = documento(expediente);
        DocumentoRolesLectura lectura = lectura("11111111H", "B76527464");
        OperacionExpediente bate = operacion(TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE);
        when(expedienteInteresadoRepository.findByExpedienteId(42L)).thenReturn(List.of(
                new ExpedienteInteresado(expediente, vendedor, RolInteresado.VENDEDOR),
                new ExpedienteInteresado(expediente, compraventa, RolInteresado.COMPRAVENTA)));
        when(operacionExpedienteRepository.findByExpedienteIdAndTipo(
                42L, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE)).thenReturn(Optional.of(bate));

        Boolean asignada = ReflectionTestUtils.invokeMethod(
                service, "asignarOperacionBatecomSiInequivoca", documento, lectura);

        assertThat(asignada).isTrue();
        assertThat(documento.getOperacion()).isSameAs(bate);
        assertThat(lectura.getMensaje()).contains("Operacion BATE asignada automaticamente");
        verify(documentoRepository).save(documento);
    }

    @Test
    void asignaComCuandoLaCompraventaEsVendedora() {
        Expediente expediente = expedienteBatecom();
        Interesado compraventa = interesado(20L, "B76527464");
        Interesado comprador = interesado(30L, "22222222J");
        Documento documento = documento(expediente);
        DocumentoRolesLectura lectura = lectura("B76527464", "22222222J");
        OperacionExpediente com = operacion(TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM);
        when(expedienteInteresadoRepository.findByExpedienteId(42L)).thenReturn(List.of(
                new ExpedienteInteresado(expediente, compraventa, RolInteresado.COMPRAVENTA),
                new ExpedienteInteresado(expediente, comprador, RolInteresado.COMPRADOR)));
        when(operacionExpedienteRepository.findByExpedienteIdAndTipo(
                42L, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM)).thenReturn(Optional.of(com));

        Boolean asignada = ReflectionTestUtils.invokeMethod(
                service, "asignarOperacionBatecomSiInequivoca", documento, lectura);

        assertThat(asignada).isTrue();
        assertThat(documento.getOperacion()).isSameAs(com);
        assertThat(lectura.getMensaje()).contains("Operacion COM asignada automaticamente");
    }

    @Test
    void noAsignaOperacionCuandoLosRolesNoFormanUnaCadenaBatecom() {
        Expediente expediente = expedienteBatecom();
        Interesado vendedor = interesado(10L, "11111111H");
        Interesado comprador = interesado(30L, "22222222J");
        Documento documento = documento(expediente);
        DocumentoRolesLectura lectura = lectura("11111111H", "22222222J");
        when(expedienteInteresadoRepository.findByExpedienteId(42L)).thenReturn(List.of(
                new ExpedienteInteresado(expediente, vendedor, RolInteresado.VENDEDOR),
                new ExpedienteInteresado(expediente, comprador, RolInteresado.COMPRADOR)));

        Boolean asignada = ReflectionTestUtils.invokeMethod(
                service, "asignarOperacionBatecomSiInequivoca", documento, lectura);

        assertThat(asignada).isFalse();
        assertThat(documento.getOperacion()).isNull();
        verify(documentoRepository, never()).save(documento);
    }

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
    private Expediente expedienteBatecom() {
        TipoTramite tipoTramite = new TipoTramite();
        tipoTramite.setNombre(TipoTramiteEnum.BATECOM);
        Expediente expediente = new Expediente();
        expediente.setId(42L);
        expediente.setTipoTramite(tipoTramite);
        return expediente;
    }

    private Interesado interesado(Long id, String dni) {
        Interesado interesado = new Interesado();
        interesado.setId(id);
        interesado.setDni(dni);
        interesado.setNombre("INTERESADO " + id);
        return interesado;
    }

    private Documento documento(Expediente expediente) {
        Documento documento = new Documento();
        documento.setId(100L);
        documento.setExpediente(expediente);
        return documento;
    }

    private DocumentoRolesLectura lectura(String vendedor, String comprador) {
        DocumentoRolesLectura lectura = new DocumentoRolesLectura();
        lectura.setVendedorIdentificador(vendedor);
        lectura.setCompradorIdentificador(comprador);
        lectura.setConfianzaGlobal(0.98);
        lectura.setRequiereRevision(false);
        lectura.setMensaje("Roles leidos.");
        return lectura;
    }

    private OperacionExpediente operacion(TipoOperacionExpediente tipo) {
        OperacionExpediente operacion = new OperacionExpediente();
        operacion.setTipo(tipo);
        return operacion;
    }
}
