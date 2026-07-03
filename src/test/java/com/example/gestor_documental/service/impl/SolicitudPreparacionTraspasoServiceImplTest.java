package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.SolicitudPreparacionDocumentoResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionTraspasoResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.validation.DniNieValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudPreparacionTraspasoServiceImplTest {

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    @Mock
    private DocumentoRolesLecturaRepository rolesLecturaRepository;
    @Mock
    private ClienteInteresadoRepository clienteInteresadoRepository;
    @Mock
    private SolicitudService solicitudService;

    private SolicitudPreparacionTraspasoServiceImpl service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new SolicitudPreparacionTraspasoServiceImpl(
                solicitudRepository,
                documentoRepository,
                identidadLecturaRepository,
                rolesLecturaRepository,
                clienteInteresadoRepository,
                solicitudService,
                new DniNieValidator()
        );
        usuario = new Usuario();
        when(solicitudService.tienePermisoSolicitud(any(Solicitud.class), any(Usuario.class))).thenReturn(true);
    }

    @Test
    void calculaPreparacionConInteresadosYDocumentosBase() {
        Solicitud solicitud = solicitudBase(10L);
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Maria Luisa Menendez Morejudo");
        solicitud.setInteresado1Dni("50975033H");
        solicitud.setInteresado1Direccion("Calle Rosario de Gaya 89, 38329 El Rosario");
        solicitud.setInteresado1Municipio("El Rosario");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Nombre("Antonio Maldonado Carmona");
        solicitud.setInteresado2Dni("42793999S");
        solicitud.setInteresado2TipoVia("Calle");
        solicitud.setInteresado2NombreVia("Igone 4");
        solicitud.setInteresado2CodigoPostal("38000");
        solicitud.setInteresado2Municipio("Santa Cruz de Tenerife");

        Documento dniVendedor = documento(1L, TipoDocumento.DNI);
        Documento dniComprador = documento(2L, TipoDocumento.DNI);
        Documento permiso = documento(3L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(4L, TipoDocumento.FICHA_TECNICA);
        Documento contrato = documento(5L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento mandato = documento(6L, TipoDocumento.MANDATO);
        Documento cambioTitularidad = documento(7L, TipoDocumento.CAMBIO_TITULARIDAD);
        DocumentoRolesLectura lecturaRoles = lecturaRoles(contrato);

        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(10L)).thenReturn(List.of(dniVendedor, dniComprador, permiso, ficha, contrato, mandato, cambioTitularidad));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L))).thenReturn(List.of(
                lecturaIdentidad(dniVendedor, "50975033H"),
                lecturaIdentidad(dniComprador, "42793999S")
        ));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L))).thenReturn(List.of(lecturaRoles));

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(10L, usuario);

        assertThat(response.estado()).isEqualTo("INCOMPLETA");
        assertThat(response.progreso()).isGreaterThan(70);
        assertThat(response.bloques()).extracting("codigo").containsExactly("INTERESADOS", "VEHICULO", "OPERACION");
        assertThat(response.bloques().get(0).estado()).isEqualTo("OK");
        assertThat(documento(response, "MANDATO").estado()).isEqualTo("YA_APORTADO");
        assertThat(documento(response, "CAMBIO_TITULARIDAD").estado()).isEqualTo("YA_APORTADO");
        assertThat(documento(response, "CONTRATO_COMPRAVENTA").estado()).isEqualTo("YA_APORTADO");
        assertThat(documento(response, "CONTRATO_COMPRAVENTA").faltantes()).isEmpty();
    }

    @Test
    void bloqueaTraspasoSinComprador() {
        Solicitud solicitud = solicitudBase(11L);
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Maria Luisa Menendez Morejudo");
        solicitud.setInteresado1Dni("50975033H");

        when(solicitudRepository.findById(11L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(11L)).thenReturn(List.of());

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(11L, usuario);

        assertThat(response.estado()).isEqualTo("BLOQUEADA");
        assertThat(response.siguienteAccion().bloque()).isEqualTo("INTERESADOS");
        assertThat(response.siguienteAccion().detalle()).contains("comprador");
        assertThat(documento(response, "CAMBIO_TITULARIDAD").faltantes()).contains("Comprador");
    }

    @Test
    void reconoceDosIdentidadesDetectadasEnElMismoPdf() {
        Solicitud solicitud = solicitudBase(12L);
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Maria Luisa Menendez Morejudo");
        solicitud.setInteresado1Dni("50975033H");
        solicitud.setInteresado1Direccion("El Rosario, Santa Cruz de Tenerife");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Nombre("Antonio Maldonado Carmona");
        solicitud.setInteresado2Dni("42793999S");
        solicitud.setInteresado2Direccion("Santa Cruz de Tenerife");

        Documento dniCompartido = documento(20L, TipoDocumento.DNI);
        Documento permiso = documento(21L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(22L, TipoDocumento.FICHA_TECNICA);
        Documento mandato = documento(23L, TipoDocumento.MANDATO);
        Documento cambio = documento(24L, TipoDocumento.CAMBIO_TITULARIDAD);
        Documento contrato = documento(25L, TipoDocumento.CONTRATO_COMPRAVENTA);
        DocumentoIdentidadLectura lectura = lecturaIdentidad(dniCompartido, "50975033H");
        lectura.setResultadoJson("""
                {"identidades":[
                  {"tipoDocumento":"DNI","identificador":"50975033H","nombre":"MARIA LUISA","apellido1":"MENENDEZ","apellido2":"MOREJUDO","confianzaGlobal":0.95,"requiereRevision":false},
                  {"tipoDocumento":"DNI","identificador":"42793999S","nombre":"ANTONIO","apellido1":"MALDONADO","apellido2":"CARMONA","confianzaGlobal":0.95,"requiereRevision":false}
                ]}
                """);

        when(solicitudRepository.findById(12L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(12L)).thenReturn(List.of(dniCompartido, permiso, ficha, mandato, cambio, contrato));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(20L, 21L, 22L, 23L, 24L, 25L))).thenReturn(List.of(lectura));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(20L, 21L, 22L, 23L, 24L, 25L))).thenReturn(List.of());

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(12L, usuario);

        assertThat(itemEstado(response, "INTERESADOS", "soporte_identidad_1")).isEqualTo("OK");
        assertThat(itemEstado(response, "INTERESADOS", "soporte_identidad_2")).isEqualTo("OK");
        assertThat(itemEstado(response, "INTERESADOS", "direccion_2")).isEqualTo("AVISO");
        assertThat(documento(response, "CONTRATO_COMPRAVENTA").estado()).isEqualTo("YA_APORTADO");
    }

    @Test
    void reconoceCifDelCompradorEnFichaCliente() {
        Solicitud solicitud = solicitudBase(14L);
        Cliente cliente = new Cliente("B38313607", "Farray Motor SL", "farray@example.com");
        cliente.setId(3L);
        solicitud.setCliente(cliente);
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Yunior Peraza Perez");
        solicitud.setInteresado1Dni("43332629P");
        solicitud.setInteresado1Direccion("C/ Isla de la Gomera 7, Santa Cruz de Tenerife");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Nombre("Farray Motor SL");
        solicitud.setInteresado2Dni("B38313607");
        solicitud.setInteresado2Direccion("Cruce Autopista del Sur, San Isidro Granadilla de Abona. 38600.TFE");

        Documento dniVendedor = documento(40L, TipoDocumento.DNI);
        Documento permiso = documento(41L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(42L, TipoDocumento.FICHA_TECNICA);
        Documento cambio = documento(44L, TipoDocumento.CAMBIO_TITULARIDAD);
        Documento contrato = documento(45L, TipoDocumento.CONTRATO_COMPRAVENTA);
        Documento cifCliente = documento(56L, TipoDocumento.CIF);

        when(solicitudRepository.findById(14L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(14L)).thenReturn(List.of(dniVendedor, permiso, ficha, cambio, contrato));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(40L, 41L, 42L, 44L, 45L))).thenReturn(List.of(
                lecturaIdentidad(dniVendedor, "43332629P")
        ));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(40L, 41L, 42L, 44L, 45L))).thenReturn(List.of());
        when(documentoRepository.findByClienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(eq(3L), any()))
                .thenReturn(List.of(cifCliente));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(56L))).thenReturn(List.of());
        when(clienteInteresadoRepository.findByClienteIdAndHabitualTrueOrderByInteresadoNombreAsc(3L)).thenReturn(List.of());

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(14L, usuario);

        assertThat(itemEstado(response, "INTERESADOS", "soporte_identidad_1")).isEqualTo("OK");
        assertThat(itemEstado(response, "INTERESADOS", "soporte_identidad_2")).isEqualTo("OK");
        assertThat(itemEstado(response, "INTERESADOS", "direccion_1")).isEqualTo("OK");
        assertThat(itemEstado(response, "INTERESADOS", "direccion_2")).isEqualTo("OK");
        assertThat(documento(response, "MANDATO").faltantes()).doesNotContain("Localidad del mandante");
    }

    @Test
    void aceptaInformeDgtComoAlternativaAPermisoYFicha() {
        Solicitud solicitud = solicitudBase(13L);
        solicitud.setVehiculoMarca("Seat");
        solicitud.setVehiculoModelo("Ibiza");
        solicitud.setVehiculoBastidor("VF123456789ABCDE1");
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Maria Luisa Menendez Morejudo");
        solicitud.setInteresado1Dni("50975033H");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Nombre("Antonio Maldonado Carmona");
        solicitud.setInteresado2Dni("42793999S");

        Documento dniVendedor = documento(30L, TipoDocumento.DNI);
        Documento dniComprador = documento(31L, TipoDocumento.DNI);
        Documento informeDgt = documento(32L, TipoDocumento.INFORME_DGT);

        when(solicitudRepository.findById(13L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(13L)).thenReturn(List.of(dniVendedor, dniComprador, informeDgt));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(30L, 31L, 32L))).thenReturn(List.of(
                lecturaIdentidad(dniVendedor, "50975033H"),
                lecturaIdentidad(dniComprador, "42793999S")
        ));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(30L, 31L, 32L))).thenReturn(List.of());

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(13L, usuario);

        assertThat(itemEstado(response, "VEHICULO", "documentacion_vehiculo")).isEqualTo("OK");
        assertThat(itemEstado(response, "VEHICULO", "bastidor")).isEqualTo("OK");
        assertThat(itemEstado(response, "VEHICULO", "marca_modelo")).isEqualTo("OK");
        assertThat(documento(response, "CONTRATO_COMPRAVENTA").faltantes()).doesNotContain("Marca", "Modelo", "Bastidor");
    }

    @Test
    void usaPrecioManualDeSolicitudParaPrepararContrato() {
        Solicitud solicitud = solicitudBase(15L);
        solicitud.setVehiculoMarca("Seat");
        solicitud.setVehiculoModelo("Ibiza");
        solicitud.setVehiculoBastidor("VF123456789ABCDE1");
        solicitud.setOperacionPrecioVenta("800");
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Maria Luisa Menendez Morejudo");
        solicitud.setInteresado1Dni("50975033H");
        solicitud.setInteresado1Direccion("Calle Rosario de Gaya 89, 38329 El Rosario");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Nombre("Antonio Maldonado Carmona");
        solicitud.setInteresado2Dni("42793999S");
        solicitud.setInteresado2Direccion("Calle Igone 4, 38000 Santa Cruz de Tenerife");

        Documento dniVendedor = documento(60L, TipoDocumento.DNI);
        Documento dniComprador = documento(61L, TipoDocumento.DNI);
        Documento permiso = documento(62L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(63L, TipoDocumento.FICHA_TECNICA);

        when(solicitudRepository.findById(15L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(15L)).thenReturn(List.of(dniVendedor, dniComprador, permiso, ficha));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(60L, 61L, 62L, 63L))).thenReturn(List.of(
                lecturaIdentidad(dniVendedor, "50975033H"),
                lecturaIdentidad(dniComprador, "42793999S")
        ));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(60L, 61L, 62L, 63L))).thenReturn(List.of());

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(15L, usuario);

        assertThat(itemEstado(response, "OPERACION", "precio")).isEqualTo("OK");
        assertThat(itemEstado(response, "OPERACION", "lectura_roles")).isEqualTo("OK");
        assertThat(documento(response, "CONTRATO_COMPRAVENTA").faltantes()).doesNotContain("Precio");
    }

    @Test
    void indicaCampoYAccionCuandoFaltaPrecio() {
        Solicitud solicitud = solicitudBase(16L);
        solicitud.setVehiculoMarca("Seat");
        solicitud.setVehiculoModelo("Ibiza");
        solicitud.setVehiculoBastidor("VF123456789ABCDE1");
        solicitud.setInteresado1Rol(RolInteresado.VENDEDOR);
        solicitud.setInteresado1Nombre("Maria Luisa Menendez Morejudo");
        solicitud.setInteresado1Dni("50975033H");
        solicitud.setInteresado1TipoVia("Calle");
        solicitud.setInteresado1NombreVia("Rosario de Gaya");
        solicitud.setInteresado1NumeroVia("89");
        solicitud.setInteresado1CodigoPostal("38329");
        solicitud.setInteresado1Municipio("El Rosario");
        solicitud.setInteresado2Rol(RolInteresado.COMPRADOR);
        solicitud.setInteresado2Nombre("Antonio Maldonado Carmona");
        solicitud.setInteresado2Dni("42793999S");
        solicitud.setInteresado2TipoVia("Calle");
        solicitud.setInteresado2NombreVia("Igone");
        solicitud.setInteresado2NumeroVia("4");
        solicitud.setInteresado2CodigoPostal("38000");
        solicitud.setInteresado2Municipio("Santa Cruz de Tenerife");

        Documento dniVendedor = documento(70L, TipoDocumento.DNI);
        Documento dniComprador = documento(71L, TipoDocumento.DNI);
        Documento permiso = documento(72L, TipoDocumento.PERMISO_CIRCULACION);
        Documento ficha = documento(73L, TipoDocumento.FICHA_TECNICA);

        when(solicitudRepository.findById(16L)).thenReturn(Optional.of(solicitud));
        when(documentoRepository.findBySolicitudId(16L)).thenReturn(List.of(dniVendedor, dniComprador, permiso, ficha));
        when(identidadLecturaRepository.findByDocumentoIdIn(List.of(70L, 71L, 72L, 73L))).thenReturn(List.of(
                lecturaIdentidad(dniVendedor, "50975033H"),
                lecturaIdentidad(dniComprador, "42793999S")
        ));
        when(rolesLecturaRepository.findByDocumentoIdIn(List.of(70L, 71L, 72L, 73L))).thenReturn(List.of());

        SolicitudPreparacionTraspasoResponse response = service.obtenerPreparacion(16L, usuario);

        assertThat(response.siguienteAccion().titulo()).isEqualTo("Precio de venta");
        assertThat(response.siguienteAccion().campo()).isEqualTo("operacionPrecioVenta");
        assertThat(response.siguienteAccion().label()).isEqualTo("Editar precio");
    }

    private Solicitud solicitudBase(Long id) {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(id);
        solicitud.setMatricula("6347BGK");
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.TRASPASO, "Traspaso", true));
        return solicitud;
    }

    private Documento documento(Long id, TipoDocumento tipo) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setTipoDocumento(tipo);
        documento.setNombreArchivoOriginal(tipo.name() + ".pdf");
        return documento;
    }

    private DocumentoIdentidadLectura lecturaIdentidad(Documento documento, String identificador) {
        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setDocumento(documento);
        lectura.setIdentificador(identificador);
        lectura.setConfianzaGlobal(0.95);
        return lectura;
    }

    private DocumentoRolesLectura lecturaRoles(Documento documento) {
        DocumentoRolesLectura lectura = new DocumentoRolesLectura();
        lectura.setDocumento(documento);
        lectura.setVendedorIdentificador("50975033H");
        lectura.setVendedorNombre("Maria Luisa Menendez Morejudo");
        lectura.setCompradorIdentificador("42793999S");
        lectura.setCompradorNombre("Antonio Maldonado Carmona");
        lectura.setBastidor("VF123456789ABCDE1");
        lectura.setValorDeclarado("800");
        lectura.setConfianzaGlobal(0.95);
        lectura.setRequiereRevision(false);
        return lectura;
    }

    private SolicitudPreparacionDocumentoResponse documento(SolicitudPreparacionTraspasoResponse response, String codigo) {
        return response.documentosGenerables().stream()
                .filter(documento -> codigo.equals(documento.codigo()))
                .findFirst()
                .orElseThrow();
    }

    private String itemEstado(SolicitudPreparacionTraspasoResponse response, String bloqueCodigo, String itemCodigo) {
        return response.bloques().stream()
                .filter(bloque -> bloqueCodigo.equals(bloque.codigo()))
                .flatMap(bloque -> bloque.items().stream())
                .filter(item -> itemCodigo.equals(item.codigo()))
                .map(item -> item.estado())
                .findFirst()
                .orElseThrow();
    }
}
