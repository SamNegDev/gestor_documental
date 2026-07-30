package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.*;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import com.example.gestor_documental.service.*;
import com.example.gestor_documental.validation.DniNieValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpedienteDocumentacionActualizacionServiceTest {
    @Mock ExpedienteService expedienteService;
    @Mock DocumentoRepository documentoRepository;
    @Mock DocumentoIdentidadLecturaRepository identidadRepository;
    @Mock DocumentoRolesLecturaRepository rolesRepository;
    @Mock DocumentoVehiculoLecturaRepository vehiculoLecturaRepository;
    @Mock ExpedienteInteresadoRepository expedienteInteresadoRepository;
    @Mock InteresadoRepository interesadoRepository;
    @Mock OperacionExpedienteRepository operacionRepository;
    @Mock VehiculoRepository vehiculoRepository;
    @Mock DocumentoIdentidadLecturaService identidadService;
    @Mock DocumentoRolesLecturaService rolesService;
    @Mock DocumentoVehiculoLecturaService vehiculoLecturaService;
    @Mock RequisitoDocumentalExpedienteService requisitoService;
    @Mock HistorialCambioService historialService;
    @Mock VehiculoService vehiculoService;
    @Mock DniNieValidator dniNieValidator;
    @InjectMocks ExpedienteDocumentacionActualizacionService service;

    @Test
    void batecomConsolidaTresInteresadosDesdeDosContratosSinDniSeparados() {
        Expediente expediente = new Expediente();
        expediente.setId(42L);
        TipoTramite tramite = new TipoTramite();
        tramite.setNombre(TipoTramiteEnum.BATECOM);
        expediente.setTipoTramite(tramite);
        Documento documentoBate = documento(10L, expediente);
        Documento documentoCom = documento(20L, expediente);
        DocumentoRolesLectura bate = lectura(documentoBate, "11111111H", "VENDEDOR INICIAL", "B76527464", "COMPRAVENTA SL");
        DocumentoRolesLectura com = lectura(documentoCom, "B76527464", "COMPRAVENTA SL", "22222222J", "COMPRADOR FINAL");
        OperacionExpediente operacionBate = new OperacionExpediente();
        operacionBate.setTipo(TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE);
        OperacionExpediente operacionCom = new OperacionExpediente();
        operacionCom.setTipo(TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM);
        when(rolesRepository.findByDocumentoIdIn(any())).thenReturn(List.of(bate, com));
        when(operacionRepository.findByExpedienteIdAndTipo(42L, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE)).thenReturn(Optional.of(operacionBate));
        when(operacionRepository.findByExpedienteIdAndTipo(42L, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM)).thenReturn(Optional.of(operacionCom));
        Map<String, Interesado> interesadosGuardados = new java.util.HashMap<>();
        Interesado compraventaRegistrada = new Interesado();
        compraventaRegistrada.setId(99L);
        compraventaRegistrada.setDni("B76527464");
        compraventaRegistrada.setRazonSocial("COMPRAVENTA SL");
        compraventaRegistrada.setDireccion("CALLE REGISTRADA 1");
        interesadosGuardados.put(compraventaRegistrada.getDni(), compraventaRegistrada);
        when(interesadoRepository.findByDni(any())).thenAnswer(invocation ->
                Optional.ofNullable(interesadosGuardados.get(invocation.getArgument(0))));
        List<ExpedienteInteresado> relacionesGuardadas = new ArrayList<>();
        when(expedienteInteresadoRepository.findByExpedienteId(42L)).thenAnswer(invocation -> List.copyOf(relacionesGuardadas));
        when(expedienteInteresadoRepository.save(any())).thenAnswer(invocation -> {
            ExpedienteInteresado relacion = invocation.getArgument(0);
            if (!relacionesGuardadas.contains(relacion)) relacionesGuardadas.add(relacion);
            return relacion;
        });
        AtomicLong ids = new AtomicLong(1);
        when(interesadoRepository.save(any())).thenAnswer(invocation -> {
            Interesado interesado = invocation.getArgument(0);
            interesado.setId(ids.getAndIncrement());
            interesadosGuardados.put(interesado.getDni(), interesado);
            return interesado;
        });
        when(dniNieValidator.esValido(any())).thenReturn(true);
        List<String> detalles = new ArrayList<>();

        Integer aplicados = ReflectionTestUtils.invokeMethod(service, "consolidarCadenaBatecom",
                expediente, List.of(documentoBate, documentoCom), Map.of(), null, detalles);

        assertThat(aplicados).isEqualTo(2);
        assertThat(documentoBate.getOperacion()).isSameAs(operacionBate);
        assertThat(documentoCom.getOperacion()).isSameAs(operacionCom);
        assertThat(relacionesGuardadas).extracting(ExpedienteInteresado::getRol)
                .containsExactly(RolInteresado.VENDEDOR, RolInteresado.COMPRAVENTA, RolInteresado.COMPRADOR);
        assertThat(relacionesGuardadas).anyMatch(relacion -> relacion.getInteresado() == compraventaRegistrada
                && relacion.getRol() == RolInteresado.COMPRAVENTA);
        verify(interesadoRepository, times(3)).save(any());
        assertThat(interesadosGuardados).hasSize(3);
        assertThat(detalles).anyMatch(detalle -> detalle.contains("cadena consolidada"));
    }

    @Test
    void guardaMarcaYModeloConMatriculaCoincidenteAunqueLaLecturaRequieraRevision() {
        Expediente expediente = new Expediente();
        expediente.setMatricula("1234ABC");
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMatricula("1234ABC");
        expediente.setVehiculo(vehiculo);
        var lectura = new com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse();
        lectura.setMatricula("1234 ABC");
        lectura.setMarca("Toyota");
        lectura.setModeloVehiculo("Corolla");
        lectura.setConfianzaGlobal(0.91);
        lectura.setRequiereRevision(true);
        List<String> detalles = new ArrayList<>();

        Boolean actualizado = ReflectionTestUtils.invokeMethod(
                service, "aplicarMarcaModeloSeguros", expediente, lectura, detalles);

        assertThat(actualizado).isTrue();
        assertThat(vehiculo.getMarca()).isEqualTo("TOYOTA");
        assertThat(vehiculo.getModelo()).isEqualTo("COROLLA");
        assertThat(detalles).anyMatch(detalle -> detalle.contains("Marca y modelo guardados"));
        verify(vehiculoRepository).save(vehiculo);
    }

    @Test
    void normalizaNombreEstructuradoAlVincularUnaLecturaYaExistente() {
        Interesado interesado = new Interesado();
        interesado.setId(7L);
        interesado.setDni("12345678Z");
        interesado.setNombre("PEREZ GARCIA JUAN");
        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setNombre("Juan");
        lectura.setApellido1("Pérez");
        lectura.setApellido2("García");

        Boolean actualizado = ReflectionTestUtils.invokeMethod(
                service, "actualizarNombreEstructurado", interesado, lectura);

        assertThat(actualizado).isTrue();
        assertThat(interesado.getNombrePila()).isEqualTo("JUAN");
        assertThat(interesado.getApellido1()).isEqualTo("PÉREZ");
        assertThat(interesado.getApellido2()).isEqualTo("GARCÍA");
        assertThat(interesado.getNombre()).isEqualTo("JUAN PÉREZ GARCÍA");
        verify(interesadoRepository).save(interesado);
    }
    private Documento documento(Long id, Expediente expediente) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setExpediente(expediente);
        documento.setTipoDocumento(TipoDocumento.CONTRATO_COMPRAVENTA);
        return documento;
    }

    private DocumentoRolesLectura lectura(Documento documento, String vendedorId, String vendedorNombre,
                                           String compradorId, String compradorNombre) {
        DocumentoRolesLectura lectura = new DocumentoRolesLectura();
        lectura.setDocumento(documento);
        lectura.setVendedorIdentificador(vendedorId);
        lectura.setVendedorNombre(vendedorNombre);
        lectura.setCompradorIdentificador(compradorId);
        lectura.setCompradorNombre(compradorNombre);
        lectura.setConfianzaGlobal(0.97);
        lectura.setRequiereRevision(false);
        return lectura;
    }
}
