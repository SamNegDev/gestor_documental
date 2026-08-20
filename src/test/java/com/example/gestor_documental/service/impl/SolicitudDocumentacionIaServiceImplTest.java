package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.LecturaIaSolicitudClienteResponse;
import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.GestionPersonaCatalogoRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.validation.DniNieValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudDocumentacionIaServiceImplTest {

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    @Mock
    private DocumentoRolesLecturaRepository rolesLecturaRepository;
    @Mock
    private DocumentoVehiculoLecturaRepository vehiculoLecturaRepository;
    @Mock
    private GestionPersonaCatalogoRepository gestionPersonaCatalogoRepository;
    @Mock
    private InteresadoRepository interesadoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private HistorialCambioRepository historialCambioRepository;
    @Mock
    private DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    @Mock
    private DocumentoRolesLecturaService documentoRolesLecturaService;
    @Mock
    private DocumentoVehiculoLecturaService documentoVehiculoLecturaService;
    @Mock
    private HistorialCambioService historialCambioService;

    private SolicitudDocumentacionIaServiceImpl service;
    private Usuario usuarioCliente;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setApiKey("test-key");
        service = new SolicitudDocumentacionIaServiceImpl(
                solicitudRepository,
                documentoRepository,
                identidadLecturaRepository,
                rolesLecturaRepository,
                vehiculoLecturaRepository,
                gestionPersonaCatalogoRepository,
                interesadoRepository,
                clienteRepository,
                historialCambioRepository,
                documentoIdentidadLecturaService,
                documentoRolesLecturaService,
                documentoVehiculoLecturaService,
                historialCambioService,
                new DniNieValidator(),
                openAiProperties
        );
        cliente = new Cliente();
        cliente.setId(7L);
        usuarioCliente = new Usuario();
        usuarioCliente.setRolUsuario(RolUsuario.CLIENTE);
        usuarioCliente.setCliente(cliente);
    }

    @Test
    void permiteLecturaClienteConDniYPermisoFichaSinContrato() {
        Solicitud solicitud = solicitudCliente(30L);
        when(solicitudRepository.findById(30L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(30L)).thenReturn(List.of(
                documento(1L, TipoDocumento.DNI),
                documento(2L, TipoDocumento.PERMISO_CIRCULACION),
                documento(3L, TipoDocumento.FICHA_TECNICA)
        ));
        when(historialCambioRepository.countBySolicitudIdAndAccion(30L, "IA DOCUMENTACION CLIENTE")).thenReturn(0L);

        LecturaIaSolicitudClienteResponse response = service.obtenerLecturaCliente(30L, usuarioCliente);

        assertThat(response.documentacionSuficiente()).isTrue();
        assertThat(response.puedeSolicitar()).isTrue();
        assertThat(response.bloqueosDocumentales()).isEmpty();
        assertThat(response.documentosIdentidad()).isEqualTo(1);
        assertThat(response.documentosVehiculo()).isEqualTo(2);
        assertThat(response.documentosRoles()).isZero();
    }

    @Test
    void permiteLecturaClienteConDniEInformeDgt() {
        Solicitud solicitud = solicitudCliente(31L);
        when(solicitudRepository.findById(31L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(31L)).thenReturn(List.of(
                documento(4L, TipoDocumento.DNI),
                documento(5L, TipoDocumento.INFORME_DGT)
        ));
        when(historialCambioRepository.countBySolicitudIdAndAccion(31L, "IA DOCUMENTACION CLIENTE")).thenReturn(0L);

        LecturaIaSolicitudClienteResponse response = service.obtenerLecturaCliente(31L, usuarioCliente);

        assertThat(response.documentacionSuficiente()).isTrue();
        assertThat(response.puedeSolicitar()).isTrue();
        assertThat(response.documentosVehiculo()).isEqualTo(1);
    }

    @Test
    void lecturaClienteAplicaVehiculoAunqueFalteContrato() {
        Solicitud solicitud = solicitudCliente(32L);
        Documento dni = documento(6L, TipoDocumento.DNI);
        Documento permiso = documento(7L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(8L, TipoDocumento.FICHA_TECNICA);
        DocumentoVehiculoLectura lectura = new DocumentoVehiculoLectura();
        lectura.setDocumento(permiso);
        lectura.setMatricula("1234ABC");
        lectura.setMarca("SEAT");
        lectura.setModeloVehiculo("LEON");
        lectura.setBastidor("VSSZZZ1PZ9R000001");
        lectura.setConfianzaGlobal(0.93);
        lectura.setRequiereRevision(false);

        when(solicitudRepository.findById(32L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(32L)).thenReturn(List.of(dni, permiso, ficha));
        when(historialCambioRepository.countBySolicitudIdAndAccion(32L, "IA DOCUMENTACION CLIENTE")).thenReturn(0L);
        when(identidadLecturaRepository.findByDocumentoId(6L)).thenReturn(Optional.empty());
        when(vehiculoLecturaRepository.findByDocumentoId(7L)).thenReturn(Optional.empty());
        when(vehiculoLecturaRepository.findByDocumentoId(8L)).thenReturn(Optional.empty());
        when(vehiculoLecturaRepository.findByDocumentoIdIn(List.of(7L, 8L))).thenReturn(List.of(lectura));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacionCliente(32L, usuarioCliente);

        assertThat(response.isDatosAplicados()).isTrue();
        assertThat(response.isRequiereRevision()).isTrue();
        assertThat(solicitud.getMatricula()).isEqualTo("1234ABC");
        assertThat(solicitud.getVehiculoMarca()).isEqualTo("SEAT");
        assertThat(solicitud.getVehiculoModelo()).isEqualTo("LEON");
        assertThat(solicitud.getVehiculoBastidor()).isEqualTo("VSSZZZ1PZ9R000001");
    }

    @Test
    void noAplicaLecturaConCodigoLargoConfundidoConBastidor() {
        Solicitud solicitud = solicitudCliente(33L);
        Documento dni = documento(9L, TipoDocumento.DNI);
        Documento permiso = documento(10L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(11L, TipoDocumento.FICHA_TECNICA);
        DocumentoVehiculoLectura lectura = new DocumentoVehiculoLectura();
        lectura.setDocumento(permiso);
        lectura.setMatricula("3900JXR");
        lectura.setMarca("NISSAN");
        lectura.setModeloVehiculo("JUKE");
        lectura.setBastidor("647E2D452A0F4F1281667A6257798850");
        lectura.setConfianzaGlobal(0.95);
        lectura.setRequiereRevision(false);

        when(solicitudRepository.findById(33L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(33L)).thenReturn(List.of(dni, permiso, ficha));
        when(historialCambioRepository.countBySolicitudIdAndAccion(33L, "IA DOCUMENTACION CLIENTE")).thenReturn(0L);
        when(identidadLecturaRepository.findByDocumentoId(9L)).thenReturn(Optional.empty());
        when(vehiculoLecturaRepository.findByDocumentoId(10L)).thenReturn(Optional.empty());
        when(vehiculoLecturaRepository.findByDocumentoId(11L)).thenReturn(Optional.empty());
        when(vehiculoLecturaRepository.findByDocumentoIdIn(List.of(10L, 11L))).thenReturn(List.of(lectura));

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacionCliente(33L, usuarioCliente);

        assertThat(response.isDatosAplicados()).isFalse();
        assertThat(response.isRequiereRevision()).isTrue();
        assertThat(solicitud.getMatricula()).isNull();
        assertThat(solicitud.getVehiculoBastidor()).isNull();
    }

    @Test
    void noConsolidaIdentidadMarcadaParaRevisionAunqueLaConfianzaSeaAlta() {
        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setIdentificador("12345678Z");
        lectura.setConfianzaGlobal(1.0);
        lectura.setRequiereRevision(true);

        Boolean usable = ReflectionTestUtils.invokeMethod(service, "identidadUsable", lectura);

        assertThat(usable).isFalse();
    }

    @Test
    void batecomAceptaCompraventaCorroboradaEnLasDosOperaciones() {
        Solicitud solicitud = solicitudCliente(34L);
        cliente.setNif("B38436556");
        cliente.setNombre("CANARIOALEMANA DE AUTOMOVILES SL");
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM"));

        Documento contratoBate = documento(12L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento contratoCom = documento(13L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento dniVendedor = documento(14L, TipoDocumento.DNI);
        DocumentoRolesLectura lecturaBate = lecturaRoles(
                contratoBate,
                "X8237277B", "FRANCISCUS WILHELMUS ADRIANUS CORNELIS",
                "B38501631", "MG MOTOR CANARIAS, S.L.");
        DocumentoRolesLectura lecturaCom = lecturaRoles(
                contratoCom,
                "B38501631", "MG MOTOR CANARIAS SL",
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL");
        DocumentoIdentidadLectura identidadVendedor = new DocumentoIdentidadLectura();
        identidadVendedor.setDocumento(dniVendedor);
        identidadVendedor.setIdentificador("X8237277B");
        identidadVendedor.setNombre("FRANCISCUS WILHELMUS ADRIANUS");
        identidadVendedor.setApellido1("CORNELIS");
        identidadVendedor.setConfianzaGlobal(1.0);
        identidadVendedor.setRequiereRevision(false);

        when(solicitudRepository.findById(34L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(34L)).thenReturn(List.of(contratoBate, contratoCom, dniVendedor));
        when(rolesLecturaRepository.findByDocumentoId(12L)).thenReturn(Optional.of(lecturaBate));
        when(rolesLecturaRepository.findByDocumentoId(13L)).thenReturn(Optional.of(lecturaCom));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(12L, 13L))).thenReturn(List.of(lecturaBate, lecturaCom));
        when(identidadLecturaRepository.findByDocumentoId(14L)).thenReturn(Optional.of(identidadVendedor));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(14L))).thenReturn(List.of(identidadVendedor));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);
        Usuario admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacion(34L, admin);

        assertThat(response.isDatosAplicados()).isTrue();
        assertThat(response.isRequiereRevision()).isFalse();
        assertThat(solicitud.getInteresado1Rol()).isEqualTo(RolInteresado.VENDEDOR);
        assertThat(solicitud.getInteresado1Dni()).isEqualTo("X8237277B");
        assertThat(solicitud.getInteresado2Rol()).isEqualTo(RolInteresado.COMPRAVENTA);
        assertThat(solicitud.getInteresado2Dni()).isEqualTo("B38501631");
        assertThat(solicitud.getInteresado3Rol()).isEqualTo(RolInteresado.COMPRADOR);
        assertThat(solicitud.getInteresado3Dni()).isEqualTo("B38436556");
    }

    @Test
    void batecomCompletaCadenaConEntidadConocidaClienteEInteresadoValidadoEnSolicitud() {
        Solicitud solicitud = solicitudCliente(35L);
        cliente.setId(4L);
        cliente.setNif("B38436556");
        cliente.setNombre("CANARIOALEMANA DE AUTOMOVILES SL");
        cliente.setTelefono("922111111");
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM"));
        solicitud.setInteresado1Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado1Dni("B76631407");
        solicitud.setInteresado1Nombre("ALBERTO'S FERROGRUPO SL");

        Documento contratoBate = documento(15L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento contratoCom = documento(16L, TipoDocumento.CONTRATO_COMPRAVENTA);
        DocumentoRolesLectura lecturaBate = lecturaRoles(
                contratoBate,
                "B38501631", "MG MOTOR CANARIAS SL",
                "B38436556", "CANAAUTO SL");
        DocumentoRolesLectura lecturaCom = lecturaRoles(
                contratoCom,
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL",
                "B76631407", "ALBERTOS FERROGRUPO SL");
        lecturaCom.setConfianzaGlobal(0.98);

        Interesado mgMotor = new Interesado("B38501631", "MG MOTOR CANARIAS SL");
        mgMotor.setId(593L);
        mgMotor.setRazonSocial("MG MOTOR CANARIAS SL");
        mgMotor.setTelefono("922000000");
        mgMotor.setDireccion("CALLE EJEMPLO 1, 38001 SANTA CRUZ DE TENERIFE");
        Interesado canauto = new Interesado("B38436556", "CANARIOALEMANA DE AUTOMOVILES SL");
        canauto.setId(555L);
        Documento cifCanauto = documento(18L, TipoDocumento.CIF);
        cifCanauto.setCliente(cliente);
        cifCanauto.setInteresado(canauto);

        when(solicitudRepository.findById(35L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(35L)).thenReturn(List.of(contratoBate, contratoCom));
        when(rolesLecturaRepository.findByDocumentoId(15L)).thenReturn(Optional.of(lecturaBate));
        when(rolesLecturaRepository.findByDocumentoId(16L)).thenReturn(Optional.of(lecturaCom));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(15L, 16L))).thenReturn(List.of(lecturaBate, lecturaCom));
        when(interesadoRepository.findByIdentificadorNormalizado(eq("B38501631"), any()))
                .thenReturn(List.of(mgMotor));
        when(clienteRepository.findByNifIgnoreCase("B38436556")).thenReturn(Optional.of(cliente));
        when(documentoRepository.findIdentidadesRecurrentesPorIdentificadores(
                any(), eq(List.of("B38436556")), any())).thenReturn(List.of(cifCanauto));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);
        Usuario admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacion(35L, admin);

        assertThat(response.isDatosAplicados()).isTrue();
        assertThat(response.isRequiereRevision()).isFalse();
        assertThat(solicitud.getInteresado1Rol()).isEqualTo(RolInteresado.COMPRADOR);
        assertThat(solicitud.getInteresado1Dni()).isEqualTo("B76631407");
        assertThat(solicitud.getInteresado2Rol()).isEqualTo(RolInteresado.VENDEDOR);
        assertThat(solicitud.getInteresado2Dni()).isEqualTo("B38501631");
        assertThat(solicitud.getInteresado2Telefono()).isEqualTo("922000000");
        assertThat(solicitud.getInteresado2Direccion()).contains("CALLE EJEMPLO 1");
        assertThat(solicitud.getInteresado3Rol()).isEqualTo(RolInteresado.COMPRAVENTA);
        assertThat(solicitud.getInteresado3Dni()).isEqualTo("B38436556");
        assertThat(solicitud.getInteresado3Nombre()).isEqualTo("CANARIOALEMANA DE AUTOMOVILES SL");
        assertThat(solicitud.getInteresado3Telefono()).isEqualTo("922111111");
    }

    @Test
    void batecomCompletaCadenaConInteresadosValidadosClienteYOperacionFinal() {
        Solicitud solicitud = solicitudCliente(549L);
        cliente.setId(4L);
        cliente.setNif("B38436556");
        cliente.setNombre("CANARIOALEMANA DE AUTOMOVILES");
        cliente.setTelefono("922111111");
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM"));
        solicitud.setInteresado1Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado1Dni("79083702L");
        solicitud.setInteresado1Nombre("PEDRO JOSE DEL BOSQUE DE ARMAS");
        solicitud.setInteresado2Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado2Dni("43780353Z");
        solicitud.setInteresado2Nombre("CRISTINA PEREZ BENCOMO");

        Documento factura = documento(9248L, TipoDocumento.FACTURA);
        Documento contrato = documento(9249L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento dniComprador = documento(9252L, TipoDocumento.DNI);
        Documento dniVendedor = documento(9331L, TipoDocumento.DNI);
        DocumentoRolesLectura lecturaFactura = lecturaRoles(
                factura,
                null, "CANAAUTO SL",
                "79083702L", "PEDRO JOSE DEL BOSQUE DE ARMAS");
        lecturaFactura.setConfianzaGlobal(0.95);
        lecturaFactura.setRequiereRevision(true);
        DocumentoRolesLectura lecturaFinal = lecturaRoles(
                contrato,
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL",
                "79083702L", "PEDRO JOSE DEL BOSQUE DE ARMAS");
        lecturaFinal.setConfianzaGlobal(0.98);
        DocumentoIdentidadLectura identidadComprador = identidad(
                dniComprador, "79083702L", "PEDRO JOSE", "DEL BOSQUE", "DE ARMAS", 0.99);
        DocumentoIdentidadLectura identidadVendedor = identidad(
                dniVendedor, "43780353Z", "CRISTINA", "PEREZ", "BENCOMO", 1.0);

        when(solicitudRepository.findById(549L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(549L)).thenReturn(List.of(
                factura, contrato, dniComprador, dniVendedor));
        when(rolesLecturaRepository.findByDocumentoId(9248L)).thenReturn(Optional.of(lecturaFactura));
        when(rolesLecturaRepository.findByDocumentoId(9249L)).thenReturn(Optional.of(lecturaFinal));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(9248L, 9249L)))
                .thenReturn(List.of(lecturaFactura, lecturaFinal));
        when(identidadLecturaRepository.findByDocumentoId(9252L)).thenReturn(Optional.of(identidadComprador));
        when(identidadLecturaRepository.findByDocumentoId(9331L)).thenReturn(Optional.of(identidadVendedor));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(9252L, 9331L)))
                .thenReturn(List.of(identidadComprador, identidadVendedor));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);
        Usuario admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacion(549L, admin);

        assertThat(response.isDatosAplicados()).isTrue();
        assertThat(response.isRequiereRevision()).isFalse();
        assertThat(response.getDetalles()).anyMatch(detalle -> detalle.contains("Cadena BATECOM completada"));
        assertThat(solicitud.getInteresado1Rol()).isEqualTo(RolInteresado.COMPRADOR);
        assertThat(solicitud.getInteresado1Dni()).isEqualTo("79083702L");
        assertThat(solicitud.getInteresado2Rol()).isEqualTo(RolInteresado.VENDEDOR);
        assertThat(solicitud.getInteresado2Dni()).isEqualTo("43780353Z");
        assertThat(solicitud.getInteresado3Rol()).isEqualTo(RolInteresado.COMPRAVENTA);
        assertThat(solicitud.getInteresado3Dni()).isEqualTo("B38436556");
        assertThat(solicitud.getInteresado3Nombre()).isEqualTo("CANARIOALEMANA DE AUTOMOVILES");
        assertThat(solicitud.getInteresado3Telefono()).isEqualTo("922111111");
    }

    @Test
    void batecomCompletaCadena598TrasValidarManualmenteAmbasIdentidades() {
        Solicitud solicitud = solicitudCliente(598L);
        cliente.setId(4L);
        cliente.setNif("B38436556");
        cliente.setNombre("CANARIOALEMANA DE AUTOMOVILES");
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM"));
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Dni("78642521R");
        solicitud.setInteresado1Nombre("NELSON DE LA CRUZ CABRERA");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Dni("79083702L");
        solicitud.setInteresado2Nombre("PEDRO JOSE DEL BOSQUE DE ARMAS");

        Documento contratoInicial = documento(9875L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento contratoFinal = documento(9879L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento dniComprador = documento(9882L, TipoDocumento.DNI);
        Documento dniVendedor = documento(9914L, TipoDocumento.DNI);
        DocumentoRolesLectura lecturaInicial = lecturaRoles(
                contratoInicial,
                "78642521R", "NESTOR CRUZ CABRERA",
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL");
        lecturaInicial.setConfianzaGlobal(0.93);
        DocumentoRolesLectura lecturaFinal = lecturaRoles(
                contratoFinal,
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL",
                "79083702L", "PEDRO JOSE DEL BOSQUE DE ARMAS");
        lecturaFinal.setConfianzaGlobal(0.97);
        DocumentoIdentidadLectura identidadComprador = identidad(
                dniComprador, "79083702L", "PEDRO JOSE", "DEL BOSQUE", "DE ARMAS", 1.0);
        DocumentoIdentidadLectura identidadVendedor = identidad(
                dniVendedor, "78642521R", "NELSON", "DE LA CRUZ", "CABRERA", 1.0);

        when(solicitudRepository.findById(598L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(598L)).thenReturn(List.of(
                contratoInicial, contratoFinal, dniComprador, dniVendedor));
        when(rolesLecturaRepository.findByDocumentoId(9875L)).thenReturn(Optional.of(lecturaInicial));
        when(rolesLecturaRepository.findByDocumentoId(9879L)).thenReturn(Optional.of(lecturaFinal));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(9875L, 9879L)))
                .thenReturn(List.of(lecturaInicial, lecturaFinal));
        when(identidadLecturaRepository.findByDocumentoId(9882L)).thenReturn(Optional.of(identidadComprador));
        when(identidadLecturaRepository.findByDocumentoId(9914L)).thenReturn(Optional.of(identidadVendedor));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(9882L, 9914L)))
                .thenReturn(List.of(identidadComprador, identidadVendedor));
        when(solicitudRepository.save(solicitud)).thenReturn(solicitud);
        Usuario admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacion(598L, admin);

        assertThat(response.isDatosAplicados()).isTrue();
        assertThat(response.isRequiereRevision()).isFalse();
        assertThat(solicitud.getInteresado1Rol()).isEqualTo(RolInteresado.VENDEDOR);
        assertThat(solicitud.getInteresado1Dni()).isEqualTo("78642521R");
        assertThat(solicitud.getInteresado2Rol()).isEqualTo(RolInteresado.COMPRADOR);
        assertThat(solicitud.getInteresado2Dni()).isEqualTo("79083702L");
        assertThat(solicitud.getInteresado3Rol()).isEqualTo(RolInteresado.COMPRAVENTA);
        assertThat(solicitud.getInteresado3Dni()).isEqualTo("B38436556");
    }

    @Test
    void batecomNoInfiereCadenaSinVendedorValidadoEnLaSolicitud() {
        Solicitud solicitud = solicitudCliente(550L);
        cliente.setNif("B38436556");
        cliente.setNombre("CANARIOALEMANA DE AUTOMOVILES");
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM"));
        solicitud.setInteresado1Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado1Dni("79083702L");
        solicitud.setInteresado1Nombre("PEDRO JOSE DEL BOSQUE DE ARMAS");
        Documento contrato = documento(9253L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento dniComprador = documento(9254L, TipoDocumento.DNI);
        DocumentoRolesLectura lecturaFinal = lecturaRoles(
                contrato,
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL",
                "79083702L", "PEDRO JOSE DEL BOSQUE DE ARMAS");
        lecturaFinal.setConfianzaGlobal(0.98);
        DocumentoIdentidadLectura identidadComprador = identidad(
                dniComprador, "79083702L", "PEDRO JOSE", "DEL BOSQUE", "DE ARMAS", 0.99);

        when(solicitudRepository.findById(550L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(550L)).thenReturn(List.of(contrato, dniComprador));
        when(rolesLecturaRepository.findByDocumentoId(9253L)).thenReturn(Optional.of(lecturaFinal));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(9253L))).thenReturn(List.of(lecturaFinal));
        when(identidadLecturaRepository.findByDocumentoId(9254L)).thenReturn(Optional.of(identidadComprador));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(9254L))).thenReturn(List.of(identidadComprador));
        Usuario admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacion(550L, admin);

        assertThat(response.isDatosAplicados()).isFalse();
        assertThat(response.isRequiereRevision()).isTrue();
        assertThat(solicitud.getInteresado2Rol()).isNull();
        assertThat(solicitud.getInteresado3Rol()).isNull();
    }

    @Test
    void noUsaEntidadConocidaSiLaLecturaDeRolesNoEsPerfecta() {
        Solicitud solicitud = solicitudCliente(36L);
        cliente.setNif("B38436556");
        Documento contrato = documento(17L, TipoDocumento.CONTRATO_COMPRAVENTA);
        DocumentoRolesLectura lectura = lecturaRoles(
                contrato,
                "B38501631", "MG MOTOR CANARIAS SL",
                "B38436556", "CANARIOALEMANA DE AUTOMOVILES SL");
        lectura.setConfianzaGlobal(0.96);
        Interesado mgMotor = new Interesado("B38501631", "MG MOTOR CANARIAS SL");

        when(solicitudRepository.findById(36L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(36L)).thenReturn(List.of(contrato));
        when(rolesLecturaRepository.findByDocumentoId(17L)).thenReturn(Optional.of(lectura));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(17L))).thenReturn(List.of(lectura));
        when(interesadoRepository.findByIdentificadorNormalizado(eq("B38501631"), any()))
                .thenReturn(List.of(mgMotor));
        Usuario admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);

        SolicitudDocumentacionIaResponse response = service.procesarDocumentacion(36L, admin);

        assertThat(response.isDatosAplicados()).isFalse();
        assertThat(response.isRequiereRevision()).isTrue();
        assertThat(solicitud.getInteresado1Rol()).isNull();
        assertThat(solicitud.getInteresado2Rol()).isNull();
    }

    private Solicitud solicitudCliente(Long id) {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(id);
        solicitud.setCliente(cliente);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        return solicitud;
    }

    private Documento documento(Long id, TipoDocumento tipo) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setTipoDocumento(tipo);
        return documento;
    }

    private DocumentoRolesLectura lecturaRoles(
            Documento documento,
            String vendedorDni,
            String vendedorNombre,
            String compradorDni,
            String compradorNombre
    ) {
        DocumentoRolesLectura lectura = new DocumentoRolesLectura();
        lectura.setDocumento(documento);
        lectura.setVendedorIdentificador(vendedorDni);
        lectura.setVendedorNombre(vendedorNombre);
        lectura.setCompradorIdentificador(compradorDni);
        lectura.setCompradorNombre(compradorNombre);
        lectura.setConfianzaGlobal(0.99);
        lectura.setRequiereRevision(false);
        return lectura;
    }

    private DocumentoIdentidadLectura identidad(
            Documento documento,
            String identificador,
            String nombre,
            String apellido1,
            String apellido2,
            double confianza
    ) {
        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setDocumento(documento);
        lectura.setIdentificador(identificador);
        lectura.setNombre(nombre);
        lectura.setApellido1(apellido1);
        lectura.setApellido2(apellido2);
        lectura.setConfianzaGlobal(confianza);
        lectura.setRequiereRevision(false);
        return lectura;
    }
}
