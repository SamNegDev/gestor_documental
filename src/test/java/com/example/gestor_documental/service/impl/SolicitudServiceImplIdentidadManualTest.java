package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.SolicitudIdentidadDetectadaRequest;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.CorreccionClasificacionDocumentoRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.SolicitudLecturaIaJobRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.service.OperacionExpedienteService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.VehiculoService;
import com.example.gestor_documental.validation.DniNieValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudServiceImplIdentidadManualTest {

    @Mock SolicitudRepository solicitudRepository;
    @Mock SolicitudLecturaIaJobRepository solicitudLecturaIaJobRepository;
    @Mock TipoTramiteService tipoTramiteService;
    @Mock ExpedienteRepository expedienteRepository;
    @Mock ExpedienteService expedienteService;
    @Mock DocumentoRepository documentoRepository;
    @Mock DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    @Mock IncidenciaRepository incidenciaRepository;
    @Mock HistorialCambioRepository historialCambioRepository;
    @Mock MensajeRepository mensajeRepository;
    @Mock HistorialCambioService historialCambioService;
    @Mock InteresadoService interesadoService;
    @Mock ClienteInteresadoRepository clienteInteresadoRepository;
    @Mock ExpedienteInteresadoRepository expedienteInteresadoRepository;
    @Mock CorreccionClasificacionDocumentoRepository correccionClasificacionDocumentoRepository;
    @Mock TransactionalFileService transactionalFileService;
    @Mock OperacionExpedienteService operacionExpedienteService;
    @Mock VehiculoService vehiculoService;
    @Mock ObjectProvider<RequisitoDocumentalExpedienteService> requisitoDocumentalExpedienteService;
    @Mock DniNieValidator dniNieValidator;

    @InjectMocks SolicitudServiceImpl service;

    @Test
    void laRevisionManualReemplazaLosDatosIncorrectosAunqueYaEstuvieranInformados() {
        Solicitud solicitud = solicitudBase();
        solicitud.setInteresado1Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado1Dni("12345678Z");
        solicitud.setInteresado1Nombre("NOMBRE INCORRECTO");
        solicitud.setInteresado1Direccion("CALLE INCORRECTA 1");
        solicitud.setInteresado1NombreVia("INCORRECTA");
        solicitud.setInteresado1NumeroVia("1");
        solicitud.setInteresado1Telefono("600000000");

        Interesado fichaConsolidada = new Interesado("12345678Z", "NOMBRE INCORRECTO");
        fichaConsolidada.setId(20L);
        fichaConsolidada.setDireccion("CALLE INCORRECTA 1");
        fichaConsolidada.setNombreVia("INCORRECTA");

        SolicitudIdentidadDetectadaRequest request = request(
                "12345678Z", "12345678Z", "ANA", "PEREZ", "GARCIA");
        request.setTipoVia("CALLE");
        request.setNombreVia("MAYOR");
        request.setNumeroVia("12");
        request.setCodigoPostal("38001");
        request.setMunicipio("SANTA CRUZ DE TENERIFE");
        request.setProvincia("SANTA CRUZ DE TENERIFE");

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(interesadoService.buscarInteresadoPorDNI("12345678Z")).thenReturn(Optional.of(fichaConsolidada));
        when(interesadoService.guardar(any(Interesado.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Solicitud actualizada = service.anadirInteresadoDetectado(1L, request, admin());

        assertThat(actualizada.getInteresado1Nombre()).isEqualTo("ANA PEREZ GARCIA");
        assertThat(actualizada.getInteresado1NombrePila()).isEqualTo("ANA");
        assertThat(actualizada.getInteresado1NombreVia()).isEqualTo("MAYOR");
        assertThat(actualizada.getInteresado1NumeroVia()).isEqualTo("12");
        assertThat(actualizada.getInteresado1Direccion()).contains("MAYOR", "12");
        assertThat(actualizada.getInteresado1Telefono()).isEqualTo("600000000");
        assertThat(fichaConsolidada.getNombre()).isEqualTo("ANA PEREZ GARCIA");
        assertThat(fichaConsolidada.getNombreVia()).isEqualTo("MAYOR");
    }

    @Test
    void corregirElDniReutilizaElHuecoDeLaLecturaOriginalAunqueNoQuedenHuecosLibres() {
        Solicitud solicitud = solicitudBase();
        solicitud.setInteresado1Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado1Dni("12345678Z");
        solicitud.setInteresado1Nombre("LECTURA INCORRECTA");
        solicitud.setInteresado1Telefono("600000000");
        solicitud.setInteresado2Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado2Dni("42793999S");
        solicitud.setInteresado2Nombre("VENDEDOR CORRECTO");
        solicitud.setInteresado3Rol(RolInteresado.COMPRAVENTA);
        solicitud.setInteresado3Dni("B38313607");
        solicitud.setInteresado3Nombre("COMPRAVENTA CORRECTO");

        SolicitudIdentidadDetectadaRequest request = request(
                "50975033H", "12345678Z", "MARIA", "MENENDEZ", "MOREJUDO");

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(interesadoService.buscarInteresadoPorDNI("50975033H")).thenReturn(Optional.empty());
        when(interesadoService.guardar(any(Interesado.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Solicitud actualizada = service.anadirInteresadoDetectado(1L, request, admin());

        assertThat(actualizada.getInteresado1Dni()).isEqualTo("50975033H");
        assertThat(actualizada.getInteresado1Nombre()).isEqualTo("MARIA MENENDEZ MOREJUDO");
        assertThat(actualizada.getInteresado1Telefono()).isNull();
        assertThat(actualizada.getInteresado2Dni()).isEqualTo("42793999S");
        assertThat(actualizada.getInteresado3Dni()).isEqualTo("B38313607");
    }

    private Solicitud solicitudBase() {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(1L);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        return solicitud;
    }

    private SolicitudIdentidadDetectadaRequest request(
            String identificador,
            String identificadorOriginal,
            String nombre,
            String apellido1,
            String apellido2
    ) {
        SolicitudIdentidadDetectadaRequest request = new SolicitudIdentidadDetectadaRequest();
        request.setRol(RolInteresado.COMPRADOR);
        request.setIdentificador(identificador);
        request.setIdentificadorOriginal(identificadorOriginal);
        request.setNombre(nombre);
        request.setApellido1(apellido1);
        request.setApellido2(apellido2);
        request.setNombreCompleto(String.join(" ", nombre, apellido1, apellido2));
        return request;
    }

    private Usuario admin() {
        return new Usuario("Admin", "Pruebas", "admin@test.local", "secret", RolUsuario.ADMIN, true);
    }
}
