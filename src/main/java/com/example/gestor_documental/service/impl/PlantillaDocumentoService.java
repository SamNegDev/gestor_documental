package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.plantilla.DocumentoGeneradoResponse;
import com.example.gestor_documental.dto.plantilla.GenerarPlantillaRequest;
import com.example.gestor_documental.dto.plantilla.PlantillaCampoResponse;
import com.example.gestor_documental.dto.plantilla.PlantillaDestinatarioResponse;
import com.example.gestor_documental.dto.plantilla.PlantillaDocumentoItemResponse;
import com.example.gestor_documental.dto.plantilla.PlantillaPreviewRequest;
import com.example.gestor_documental.dto.plantilla.PlantillaPreviewResponse;
import com.example.gestor_documental.dto.plantilla.PlantillasExpedienteResponse;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Vehiculo;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.SolicitudService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlantillaDocumentoService {

    private static final DateTimeFormatter DATE_NUMBER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("dd 'DE' MMMM 'DE' yyyy", new Locale("es", "ES"));
    private final ExpedienteRepository expedienteRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final SolicitudRepository solicitudRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;

    @Transactional(readOnly = true)
    public PlantillasExpedienteResponse catalogo(Long expedienteId, Usuario usuario) {
        Expediente expediente = expediente(expedienteId, usuario);
        List<PlantillaDestinatarioResponse> interesados = relaciones(expedienteId).stream()
                .map(this::mapInteresado)
                .toList();
        return new PlantillasExpedienteResponse(
                referencia(expediente),
                valor(expediente.getMatricula()),
                tipoTramite(expediente),
                expediente.getCliente() != null ? valor(expediente.getCliente().getNombre()) : "SIN CLIENTE",
                Arrays.stream(TipoPlantilla.values()).map(this::mapPlantilla).toList(),
                interesados);
    }

    @Transactional(readOnly = true)
    public PlantillaPreviewResponse preview(Long expedienteId, PlantillaPreviewRequest request, Usuario usuario) {
        Expediente expediente = expediente(expedienteId, usuario);
        TipoPlantilla plantilla = tipo(request != null ? request.codigo() : null);
        return construirPreview(expediente, referencia(expediente), plantilla, request != null ? request.campos() : null,
                relaciones(expediente.getId()));
    }

    @Transactional
    public DocumentoGeneradoResponse generar(Long expedienteId, GenerarPlantillaRequest request, Usuario usuario) {
        throw new OperacionInvalidaException("Los documentos generados no se archivan automaticamente. Genera el PDF temporal y subelo firmado.");
    }

    @Transactional(readOnly = true)
    public PlantillasExpedienteResponse catalogoSolicitud(Long solicitudId, Usuario usuario) {
        Solicitud solicitud = solicitud(solicitudId, usuario);
        Expediente contexto = contextoSolicitud(solicitud);
        List<PlantillaDestinatarioResponse> interesados = relacionesSolicitud(solicitud, contexto).stream()
                .map(this::mapInteresado)
                .toList();
        return new PlantillasExpedienteResponse(
                referencia(solicitud),
                valor(solicitud.getMatricula()),
                tipoTramite(solicitud),
                solicitud.getCliente() != null ? valor(solicitud.getCliente().getNombre()) : "SIN CLIENTE",
                Arrays.stream(TipoPlantilla.values()).map(this::mapPlantilla).toList(),
                interesados);
    }

    @Transactional(readOnly = true)
    public PlantillaPreviewResponse previewSolicitud(Long solicitudId, PlantillaPreviewRequest request, Usuario usuario) {
        Solicitud solicitud = solicitud(solicitudId, usuario);
        Expediente contexto = contextoSolicitud(solicitud);
        TipoPlantilla plantilla = tipo(request != null ? request.codigo() : null);
        return construirPreview(contexto, referencia(solicitud), plantilla, request != null ? request.campos() : null,
                relacionesSolicitud(solicitud, contexto), valoresSolicitud(solicitud));
    }

    @Transactional
    public DocumentoGeneradoResponse generarSolicitud(Long solicitudId, GenerarPlantillaRequest request, Usuario usuario) {
        throw new OperacionInvalidaException("Los documentos generados no se archivan automaticamente. Genera el PDF temporal y subelo firmado.");
    }

    @Transactional(readOnly = true)
    public PlantillaPdfFile generarPdfTemporal(Long expedienteId, GenerarPlantillaRequest request, Usuario usuario) {
        Expediente expediente = expediente(expedienteId, usuario);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede generar documentos en el expediente");
        }
        TipoPlantilla plantilla = tipo(request != null ? request.codigo() : null);
        List<ExpedienteInteresado> relaciones = relaciones(expediente.getId());
        PlantillaPreviewResponse preview = construirPreview(expediente, referencia(expediente), plantilla,
                request != null ? request.campos() : null, relaciones);
        validarRequeridos(preview.campos());
        Map<String, String> valores = valores(preview.campos());
        byte[] pdf = rellenarPdf(expediente, plantilla, valores, relaciones);
        return new PlantillaPdfFile(preview.nombreArchivo(), pdf);
    }

    @Transactional(readOnly = true)
    public PlantillaPdfFile generarPdfTemporalSolicitud(Long solicitudId, GenerarPlantillaRequest request, Usuario usuario) {
        Solicitud solicitud = solicitud(solicitudId, usuario);
        Expediente contexto = contextoSolicitud(solicitud);
        List<ExpedienteInteresado> relaciones = relacionesSolicitud(solicitud, contexto);
        TipoPlantilla plantilla = tipo(request != null ? request.codigo() : null);
        PlantillaPreviewResponse preview = construirPreview(contexto, referencia(solicitud), plantilla,
                request != null ? request.campos() : null, relaciones, valoresSolicitud(solicitud));
        validarRequeridos(preview.campos());
        Map<String, String> valores = valores(preview.campos());
        byte[] pdf = rellenarPdf(contexto, plantilla, valores, relaciones);
        return new PlantillaPdfFile(preview.nombreArchivo(), pdf);
    }

    private PlantillaPreviewResponse construirPreview(Expediente expediente, String referencia,
            TipoPlantilla plantilla, Map<String, String> cambios, List<ExpedienteInteresado> relaciones) {
        return construirPreview(expediente, referencia, plantilla, cambios, relaciones, Map.of());
    }

    private PlantillaPreviewResponse construirPreview(Expediente expediente, String referencia,
            TipoPlantilla plantilla, Map<String, String> cambios, List<ExpedienteInteresado> relaciones,
            Map<String, String> valoresSolicitud) {
        Map<String, String> valores = valoresIniciales(expediente, plantilla, relaciones);
        if (valoresSolicitud != null) {
            valoresSolicitud.forEach((clave, valor) -> {
                if (clave != null && !vacio(valor)) valores.put(clave, limpiar(valor));
            });
        }
        if (cambios != null) {
            cambios.forEach((clave, valor) -> {
                if (clave != null) valores.put(clave, limpiar(valor));
            });
        }
        completarDependencias(plantilla, valores, relaciones);
        List<PlantillaCampoResponse> campos = campos(plantilla, valores);
        List<String> avisos = new ArrayList<>();
        campos.stream()
                .filter(PlantillaCampoResponse::requerido)
                .filter(campo -> campo.valor() == null || campo.valor().isBlank())
                .forEach(campo -> avisos.add("Falta completar: " + campo.etiqueta() + "."));
        return new PlantillaPreviewResponse(
                plantilla.name(),
                plantilla.nombre,
                nombreArchivo(expediente, referencia, plantilla),
                plantilla.tipoDocumento.name(),
                campos,
                avisos);
    }

    private Map<String, String> valoresIniciales(Expediente expediente, TipoPlantilla plantilla,
            List<ExpedienteInteresado> relaciones) {
        Map<String, String> valores = new LinkedHashMap<>();
        String comprador = idPorRol(relaciones, RolInteresado.COMPRADOR, RolInteresado.TITULAR, RolInteresado.COMPRAVENTA);
        String vendedor = idPorRol(relaciones, RolInteresado.VENDEDOR);
        Vehiculo vehiculo = expediente.getVehiculo();
        switch (plantilla) {
            case MANDATO -> {
                valores.put("mandanteId", !comprador.isBlank() ? comprador : idPorRol(relaciones, RolInteresado.VENDEDOR));
                valores.put("localidadMandante", "");
                valores.put("direccionMandante", "");
                valores.put("asunto", "CAMBIO DE TITULARIDAD DEL VEHICULO MATRICULA " + valor(expediente.getMatricula()));
                valores.put("asunto2", "");
            }
            case CAMBIO_TITULARIDAD -> {
                valores.put("compradorId", comprador);
                valores.put("vendedorId", vendedor);
                valores.put("fechaMatriculacion", "");
                valores.put("servicio", "B-00");
                valores.put("tipoVia", "");
                valores.put("domicilioVehiculo", "");
                valores.put("codigoPostal", "");
                valores.put("numero", "");
                valores.put("municipio", "");
                valores.put("poblacion", "");
                valores.put("provincia", "SANTA CRUZ DE TENERIFE");
                valores.put("otrosDatos", "");
                valores.put("localidad", "SC DE TENERIFE");
            }
            case CONTRATO_COMPRAVENTA -> {
                valores.put("compradorId", comprador);
                valores.put("vendedorId", vendedor);
                valores.put("direccionComprador", "");
                valores.put("direccionVendedor", "");
                valores.put("marca", vehiculo != null ? limpiar(vehiculo.getMarca()) : "");
                valores.put("modelo", vehiculo != null ? limpiar(vehiculo.getModelo()) : "");
                valores.put("bastidor", vehiculo != null ? limpiar(vehiculo.getBastidor()) : "");
                valores.put("cvf", "");
                valores.put("precio", "");
                valores.put("localidad", "SC DE TENERIFE");
            }
        }
        return valores;
    }

    private void completarDependencias(TipoPlantilla plantilla, Map<String, String> valores,
            List<ExpedienteInteresado> relaciones) {
        if (plantilla == TipoPlantilla.MANDATO && vacio(valores.get("direccionMandante"))) {
            Interesado mandante = interesado(relaciones, valores.get("mandanteId"), false);
            if (mandante != null) {
                valores.put("direccionMandante", nombreVia(mandante));
                if (vacio(valores.get("localidadMandante"))) {
                    valores.put("localidadMandante", limpiar(mandante.getMunicipio()));
                }
            }
        }
        if (plantilla == TipoPlantilla.CAMBIO_TITULARIDAD) {
            Interesado comprador = interesado(relaciones, valores.get("compradorId"), false);
            if (comprador != null) {
                if (vacio(valores.get("tipoVia"))) valores.put("tipoVia", limpiar(comprador.getTipoVia()));
                if (vacio(valores.get("domicilioVehiculo"))) valores.put("domicilioVehiculo", nombreVia(comprador));
                if (vacio(valores.get("codigoPostal"))) valores.put("codigoPostal", limpiar(comprador.getCodigoPostal()));
                if (vacio(valores.get("municipio"))) valores.put("municipio", limpiar(comprador.getMunicipio()));
                if (vacio(valores.get("poblacion"))) valores.put("poblacion", limpiar(comprador.getMunicipio()));
                if (vacio(valores.get("provincia")) || "SANTA CRUZ DE TENERIFE".equals(valores.get("provincia"))) {
                    String provincia = limpiar(comprador.getProvincia());
                    if (!vacio(provincia)) valores.put("provincia", provincia);
                }
            }
        }
        if (plantilla == TipoPlantilla.CONTRATO_COMPRAVENTA) {
            Interesado comprador = interesado(relaciones, valores.get("compradorId"), false);
            Interesado vendedor = interesado(relaciones, valores.get("vendedorId"), false);
            if (comprador != null && vacio(valores.get("direccionComprador"))) {
                valores.put("direccionComprador", direccion(comprador));
            }
            if (vendedor != null && vacio(valores.get("direccionVendedor"))) {
                valores.put("direccionVendedor", direccion(vendedor));
            }
        }
    }

    private List<PlantillaCampoResponse> campos(TipoPlantilla plantilla, Map<String, String> valores) {
        return switch (plantilla) {
            case MANDATO -> List.of(
                    campo("mandanteId", "Mandante", valores, true, "INTERESADO", "Persona o empresa que otorga el mandato."),
                    campo("localidadMandante", "Localidad del mandante", valores, true, "TEXT", null),
                    campo("direccionMandante", "Domicilio del mandante", valores, true, "TEXT", "Se completa desde la ficha del interesado cuando consta."),
                    campo("asunto", "Asunto", valores, true, "TEXT", "Primera linea del asunto del mandato."),
                    campo("asunto2", "Detalle adicional", valores, false, "TEXT", "Segunda linea opcional."));
            case CAMBIO_TITULARIDAD -> List.of(
                    campo("compradorId", "Comprador", valores, true, "INTERESADO", null),
                    campo("vendedorId", "Vendedor", valores, true, "INTERESADO", null),
                    campo("fechaMatriculacion", "Fecha de matriculacion", valores, false, "DATE", "Formato DD/MM/AAAA."),
                    campo("servicio", "Servicio del vehiculo", valores, false, "TEXT", "Por defecto, B-00 sin especificar."),
                    campo("tipoVia", "Tipo de via", valores, false, "TEXT", null),
                    campo("domicilioVehiculo", "Nombre de la via", valores, true, "TEXT", "Se propone la via del comprador."),
                    campo("codigoPostal", "Codigo postal", valores, false, "TEXT", null),
                    campo("numero", "Numero", valores, false, "TEXT", null),
                    campo("municipio", "Municipio", valores, false, "TEXT", null),
                    campo("poblacion", "Poblacion", valores, false, "TEXT", null),
                    campo("provincia", "Provincia", valores, false, "TEXT", null),
                    campo("otrosDatos", "Otros datos", valores, false, "TEXTAREA", null),
                    campo("localidad", "Localidad de firma", valores, true, "TEXT", null));
            case CONTRATO_COMPRAVENTA -> List.of(
                    campo("compradorId", "Comprador", valores, true, "INTERESADO", null),
                    campo("vendedorId", "Vendedor", valores, true, "INTERESADO", null),
                    campo("direccionComprador", "Domicilio del comprador", valores, true, "TEXT", "Se completa desde la ficha del interesado cuando consta."),
                    campo("direccionVendedor", "Domicilio del vendedor", valores, true, "TEXT", "Se completa desde la ficha del interesado cuando consta."),
                    campo("marca", "Marca", valores, true, "TEXT", null),
                    campo("modelo", "Modelo", valores, true, "TEXT", null),
                    campo("bastidor", "Numero de bastidor", valores, true, "TEXT", null),
                    campo("cvf", "C.V.F.", valores, false, "TEXT", null),
                    campo("precio", "Precio de venta", valores, true, "NUMBER", "Importe en euros, sin incluir el simbolo."),
                    campo("localidad", "Localidad de firma", valores, true, "TEXT", null));
        };
    }

    private byte[] rellenarPdf(Expediente expediente, TipoPlantilla plantilla, Map<String, String> valores,
            List<ExpedienteInteresado> relaciones) {
        ClassPathResource resource = new ClassPathResource("plantillas/" + plantilla.archivo);
        try (InputStream input = resource.getInputStream();
                PDDocument document = PDDocument.load(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) throw new IOException("El modelo no contiene campos rellenables");
            switch (plantilla) {
                case MANDATO -> rellenarMandato(form, valores, relaciones);
                case CAMBIO_TITULARIDAD -> rellenarCambioTitularidad(form, expediente, valores, relaciones);
                case CONTRATO_COMPRAVENTA -> rellenarContrato(form, expediente, valores, relaciones);
            }
            form.refreshAppearances();
            form.flatten();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("No se pudo completar el modelo PDF", exception);
        }
    }

    private void rellenarMandato(PDAcroForm form, Map<String, String> valores,
            List<ExpedienteInteresado> relaciones) throws IOException {
        Interesado mandante = interesado(relaciones, valores.get("mandanteId"), true);
        if (mandante.getTipoPersona() == TipoPersona.EMPRESA) {
            set(form, "Nombre de la empresa", mandante.getNombre());
            set(form, "CIF", mandante.getDni());
        } else {
            set(form, "Nombre", mandante.getNombre());
            set(form, "DNI", mandante.getDni());
        }
        String[] direccion = dividirPorCampos(form, valores.get("direccionMandante"), "calle", "calle1", 38);
        set(form, "Localidad", valores.get("localidadMandante"));
        set(form, "calle", direccion[0]);
        set(form, "calle1", direccion[1]);
        set(form, "n\u00ba", mandante.getNumeroVia());
        set(form, "CP", mandante.getCodigoPostal());
        set(form, "ASUNTO", valores.get("asunto"));
        set(form, "ASUNTO1", valores.get("asunto2"));
        set(form, "FECHA", LocalDate.now().format(DATE_LONG).toUpperCase(Locale.ROOT));
    }

    private void rellenarCambioTitularidad(PDAcroForm form, Expediente expediente, Map<String, String> valores,
            List<ExpedienteInteresado> relaciones) throws IOException {
        Interesado comprador = interesado(relaciones, valores.get("compradorId"), true);
        Interesado vendedor = interesado(relaciones, valores.get("vendedorId"), true);
        set(form, "Bot\u00f3n de radio1", "S\u00ed");
        set(form, "Bot\u00f3n de radio2", "S\u00ed");
        set(form, "Bot\u00f3n de radio4", "S\u00ed");
        set(form, "Matrícula", expediente.getMatricula());
        set(form, "Fecha matriculación", valores.get("fechaMatriculacion"));
        set(form, "Servicio al que destina el vehículo", valores.get("servicio"));
        set(form, "Nombre de la vía", valores.get("domicilioVehiculo"));
        set(form, "Cód postal", valores.get("codigoPostal"));
        set(form, "Número", valores.get("numero"));
        set(form, "Municipio", valores.get("municipio"));
        set(form, "Población", valores.get("poblacion"));
        set(form, "Provincia", valores.get("provincia"));
        set(form, "Nombre o Razón social comprador", comprador.getNombre());
        set(form, "NIF NIE CIF comprador", comprador.getDni());
        set(form, "Nombre o Razón social vendedor", vendedor.getNombre());
        set(form, "NIF NIE CIF vendedor", vendedor.getDni());
        set(form, "Otros datos", valores.get("otrosDatos"));
        set(form, "Tipo de vía", valores.get("tipoVia"));
        LocalDate hoy = LocalDate.now();
        set(form, "En", valores.get("localidad"));
        set(form, "a", String.valueOf(hoy.getDayOfMonth()));
        set(form, "de", hoy.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "ES")));
        set(form, "de2", String.valueOf(hoy.getYear()));
    }

    private void rellenarContrato(PDAcroForm form, Expediente expediente, Map<String, String> valores,
            List<ExpedienteInteresado> relaciones) throws IOException {
        Interesado comprador = interesado(relaciones, valores.get("compradorId"), true);
        Interesado vendedor = interesado(relaciones, valores.get("vendedorId"), true);
        set(form, "Texto1", vendedor.getNombre());
        set(form, "Texto3", vendedor.getDni());
        String[] direccionVendedor = dividirPorCampos(form, valores.get("direccionVendedor"), "Texto4", "Texto5", 56);
        set(form, "Texto4", direccionVendedor[0]);
        set(form, "Texto5", direccionVendedor[1]);
        set(form, "Texto6", valores.get("marca"));
        set(form, "Texto7", valores.get("modelo"));
        set(form, "Texto8", expediente.getMatricula());
        set(form, "Texto9", valores.get("bastidor"));
        set(form, "Texto10", valores.get("cvf"));
        set(form, "Texto11", comprador.getNombre());
        set(form, "Texto12", comprador.getDni());
        String[] direccionComprador = dividirPorCampos(form, valores.get("direccionComprador"), "Texto13", "Texto14", 56);
        set(form, "Texto13", direccionComprador[0]);
        set(form, "Texto14", direccionComprador[1]);
        set(form, "Texto15", valores.get("precio"));
        LocalDate hoy = LocalDate.now();
        set(form, "Texto16", valores.get("localidad"));
        set(form, "Texto17", String.valueOf(hoy.getDayOfMonth()));
        set(form, "Texto18", hoy.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "ES")));
        set(form, "Texto19", String.valueOf(hoy.getYear()));
    }

    private void set(PDAcroForm form, String nombre, String valor) throws IOException {
        PDField field = form.getField(nombre);
        if (field == null) return;
        String contenido = field instanceof PDTextField ? limpiar(valor) : valor;
        if (field instanceof PDTextField textField && textField.getMaxLen() > 0 && contenido.length() > textField.getMaxLen()) {
            contenido = contenido.substring(0, textField.getMaxLen());
        }
        field.setValue(contenido);
    }

    private Expediente expediente(Long id, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para consultar las plantillas de este expediente");
        }
        return expediente;
    }

    private Solicitud solicitud(Long id, Usuario usuario) {
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (!solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para consultar las plantillas de esta solicitud");
        }
        return solicitud;
    }

    private Expediente contextoSolicitud(Solicitud solicitud) {
        Expediente contexto = new Expediente();
        contexto.setId(solicitud.getId());
        contexto.setMatricula(solicitud.getMatricula());
        contexto.setCliente(solicitud.getCliente());
        contexto.setTipoTramite(solicitud.getTipoTramite());
        contexto.setVehiculo(vehiculoSolicitud(solicitud));
        return contexto;
    }

    private Vehiculo vehiculoSolicitud(Solicitud solicitud) {
        if (vacio(solicitud.getMatricula())
                && vacio(solicitud.getVehiculoMarca())
                && vacio(solicitud.getVehiculoModelo())
                && vacio(solicitud.getVehiculoBastidor())) {
            return null;
        }
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMatricula(limpiar(solicitud.getMatricula()));
        vehiculo.setMarca(limpiar(solicitud.getVehiculoMarca()));
        vehiculo.setModelo(limpiar(solicitud.getVehiculoModelo()));
        vehiculo.setBastidor(limpiar(solicitud.getVehiculoBastidor()));
        return vehiculo;
    }

    private Map<String, String> valoresSolicitud(Solicitud solicitud) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("precio", solicitud.getOperacionPrecioVenta());
        return valores;
    }

    private List<ExpedienteInteresado> relaciones(Long expedienteId) {
        return expedienteInteresadoRepository.findByExpedienteId(expedienteId).stream()
                .sorted(Comparator.comparingInt(item -> ordenRol(item.getRol())))
                .toList();
    }

    private List<ExpedienteInteresado> relacionesSolicitud(Solicitud solicitud, Expediente contexto) {
        List<ExpedienteInteresado> relaciones = new ArrayList<>();
        agregarInteresadoSolicitud(relaciones, contexto, 1L, solicitud.getInteresado1Rol(),
                solicitud.getInteresado1Nombre(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Telefono(),
                solicitud.getInteresado1Direccion(), solicitud.getInteresado1TipoVia(),
                solicitud.getInteresado1NombreVia(), solicitud.getInteresado1CodigoPostal(),
                solicitud.getInteresado1NumeroVia(), solicitud.getInteresado1Bloque(), solicitud.getInteresado1Portal(),
                solicitud.getInteresado1Escalera(), solicitud.getInteresado1Piso(), solicitud.getInteresado1Puerta(),
                solicitud.getInteresado1Municipio(), solicitud.getInteresado1Provincia());
        agregarInteresadoSolicitud(relaciones, contexto, 2L, solicitud.getInteresado2Rol(),
                solicitud.getInteresado2Nombre(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Telefono(),
                solicitud.getInteresado2Direccion(), solicitud.getInteresado2TipoVia(),
                solicitud.getInteresado2NombreVia(), solicitud.getInteresado2CodigoPostal(),
                solicitud.getInteresado2NumeroVia(), solicitud.getInteresado2Bloque(), solicitud.getInteresado2Portal(),
                solicitud.getInteresado2Escalera(), solicitud.getInteresado2Piso(), solicitud.getInteresado2Puerta(),
                solicitud.getInteresado2Municipio(), solicitud.getInteresado2Provincia());
        agregarInteresadoSolicitud(relaciones, contexto, 3L, solicitud.getInteresado3Rol(),
                solicitud.getInteresado3Nombre(), solicitud.getInteresado3Dni(), solicitud.getInteresado3Telefono(),
                solicitud.getInteresado3Direccion(), solicitud.getInteresado3TipoVia(),
                solicitud.getInteresado3NombreVia(), solicitud.getInteresado3CodigoPostal(),
                solicitud.getInteresado3NumeroVia(), solicitud.getInteresado3Bloque(), solicitud.getInteresado3Portal(),
                solicitud.getInteresado3Escalera(), solicitud.getInteresado3Piso(), solicitud.getInteresado3Puerta(),
                solicitud.getInteresado3Municipio(), solicitud.getInteresado3Provincia());
        return relaciones.stream()
                .sorted(Comparator.comparingInt(item -> ordenRol(item.getRol())))
                .toList();
    }

    private void agregarInteresadoSolicitud(List<ExpedienteInteresado> relaciones, Expediente contexto, Long id,
            RolInteresado rol, String nombre, String dni, String telefono, String direccion, String tipoVia,
            String nombreVia, String codigoPostal, String numeroVia, String bloque, String portal, String escalera,
            String piso, String puerta, String municipio, String provincia) {
        if (vacio(nombre) && vacio(dni) && rol == null && vacio(telefono) && vacio(direccion)
                && vacio(nombreVia) && vacio(numeroVia) && vacio(bloque) && vacio(portal) && vacio(escalera)
                && vacio(piso) && vacio(puerta) && vacio(codigoPostal) && vacio(municipio) && vacio(provincia)) {
            return;
        }
        Interesado interesado = new Interesado();
        interesado.setId(id);
        interesado.setNombre(limpiar(nombre));
        interesado.setDni(limpiar(dni));
        interesado.setTelefono(limpiar(telefono));
        interesado.setDireccion(limpiar(direccion));
        interesado.setTipoVia(limpiar(tipoVia));
        interesado.setNombreVia(limpiar(nombreVia));
        interesado.setNumeroVia(limpiar(numeroVia));
        interesado.setBloque(limpiar(bloque));
        interesado.setPortal(limpiar(portal));
        interesado.setEscalera(limpiar(escalera));
        interesado.setPiso(limpiar(piso));
        interesado.setPuerta(limpiar(puerta));
        interesado.setCodigoPostal(limpiar(codigoPostal));
        interesado.setMunicipio(limpiar(municipio));
        interesado.setProvincia(limpiar(provincia));
        interesado.setTipoPersona(inferirTipoPersona(dni));
        relaciones.add(new ExpedienteInteresado(contexto, interesado, rol));
    }

    private Interesado interesado(List<ExpedienteInteresado> relaciones, String id, boolean requerido) {
        if (vacio(id)) {
            if (requerido) throw new OperacionInvalidaException("Selecciona todos los interesados necesarios");
            return null;
        }
        try {
            Long interesadoId = Long.valueOf(id);
            return relaciones.stream()
                    .map(ExpedienteInteresado::getInteresado)
                    .filter(item -> item != null && item.getId().equals(interesadoId))
                    .findFirst()
                    .orElseThrow(() -> new OperacionInvalidaException("El interesado seleccionado no pertenece al expediente"));
        } catch (NumberFormatException exception) {
            throw new OperacionInvalidaException("El interesado seleccionado no es valido");
        }
    }

    private String idPorRol(List<ExpedienteInteresado> relaciones, RolInteresado... roles) {
        for (RolInteresado rol : roles) {
            String id = relaciones.stream()
                    .filter(item -> item.getRol() == rol && item.getInteresado() != null)
                    .map(item -> String.valueOf(item.getInteresado().getId()))
                    .findFirst()
                    .orElse("");
            if (!id.isBlank()) return id;
        }
        return "";
    }

    private PlantillaDestinatarioResponse mapInteresado(ExpedienteInteresado relacion) {
        Interesado interesado = relacion.getInteresado();
        return new PlantillaDestinatarioResponse(
                interesado.getId(), valor(interesado.getNombre()), valor(interesado.getDni()),
                relacion.getRol() != null ? relacion.getRol().name() : null, valor(interesado.getDireccion()));
    }

    private String direccion(Interesado interesado) {
        String via = nombreVia(interesado);
        String cp = limpiar(interesado.getCodigoPostal());
        String municipio = limpiar(interesado.getMunicipio());
        String provincia = limpiar(interesado.getProvincia());
        String compuesta = java.util.stream.Stream.of(via, cp, municipio, provincia)
                .filter(parte -> !vacio(parte))
                .collect(java.util.stream.Collectors.joining(", "));
        return !vacio(compuesta) ? compuesta : limpiar(interesado.getDireccion());
    }

    private String nombreVia(Interesado interesado) {
        String tipoVia = limpiar(interesado.getTipoVia());
        String nombreVia = limpiar(interesado.getNombreVia());
        String numeroVia = limpiar(interesado.getNumeroVia());
        String bloque = conEtiqueta("BLOQ", interesado.getBloque());
        String portal = conEtiqueta("PORTAL", interesado.getPortal());
        String escalera = conEtiqueta("ESC", interesado.getEscalera());
        String piso = conEtiqueta("PISO", interesado.getPiso());
        String puerta = conEtiqueta("PTA", interesado.getPuerta());
        String compuesta = java.util.stream.Stream.of(tipoVia, nombreVia, numeroVia, bloque, portal, escalera, piso, puerta)
                .filter(parte -> !vacio(parte))
                .collect(java.util.stream.Collectors.joining(" "));
        return !vacio(compuesta) ? compuesta : limpiar(interesado.getDireccion());
    }

    private String conEtiqueta(String etiqueta, String valor) {
        String limpio = limpiar(valor);
        return !vacio(limpio) ? etiqueta + " " + limpio : null;
    }

    private PlantillaDocumentoItemResponse mapPlantilla(TipoPlantilla plantilla) {
        return new PlantillaDocumentoItemResponse(
                plantilla.name(), plantilla.nombre, plantilla.descripcion, plantilla.tipoDocumento.name());
    }

    private PlantillaCampoResponse campo(String codigo, String etiqueta, Map<String, String> valores,
            boolean requerido, String tipo, String ayuda) {
        return new PlantillaCampoResponse(codigo, etiqueta, valores.getOrDefault(codigo, ""), requerido, tipo, ayuda);
    }

    private void validarRequeridos(List<PlantillaCampoResponse> campos) {
        campos.stream()
                .filter(PlantillaCampoResponse::requerido)
                .filter(campo -> vacio(campo.valor()))
                .findFirst()
                .ifPresent(campo -> {
                    throw new OperacionInvalidaException("Completa el campo " + campo.etiqueta());
                });
    }

    private Map<String, String> valores(List<PlantillaCampoResponse> campos) {
        Map<String, String> valores = new LinkedHashMap<>();
        campos.forEach(campo -> valores.put(campo.codigo(), campo.valor()));
        return valores;
    }

    private TipoPlantilla tipo(String codigo) {
        try {
            return TipoPlantilla.valueOf(codigo != null ? codigo : "");
        } catch (IllegalArgumentException exception) {
            throw new OperacionInvalidaException("Plantilla documental no valida");
        }
    }

    private String nombreArchivo(Expediente expediente, String referencia, TipoPlantilla plantilla) {
        String base = !vacio(expediente.getMatricula())
                ? expediente.getMatricula().trim().toUpperCase(Locale.ROOT)
                : referencia;
        return base + "_" + plantilla.tipoDocumento.name() + ".pdf";
    }

    private String[] dividir(String texto, int maxPrimeraLinea) {
        String contenido = limpiar(texto);
        if (contenido.length() <= maxPrimeraLinea) return new String[] { contenido, "" };
        int corte = contenido.lastIndexOf(' ', maxPrimeraLinea);
        if (corte < maxPrimeraLinea / 2) corte = maxPrimeraLinea;
        return new String[] { contenido.substring(0, corte).trim(), contenido.substring(corte).trim() };
    }

    private String[] dividirPorCampos(PDAcroForm form, String texto, String primerCampo, String segundoCampo,
            int maxPrimeraLineaFallback) {
        int primeraLinea = capacidadCampo(form, primerCampo, maxPrimeraLineaFallback);
        int segundaLinea = capacidadCampo(form, segundoCampo, Math.max(maxPrimeraLineaFallback, primeraLinea));
        String contenido = limpiar(texto);
        if (contenido.length() <= primeraLinea) {
            return new String[] { contenido, "" };
        }
        int objetivo = contenido.length() <= primeraLinea + segundaLinea
                ? Math.max(1, contenido.length() - segundaLinea)
                : primeraLinea;
        int limite = Math.min(primeraLinea, Math.max(objetivo, primeraLinea / 2));
        int corte = contenido.lastIndexOf(' ', limite);
        if (corte < objetivo / 2) {
            corte = contenido.indexOf(' ', objetivo);
        }
        if (corte <= 0 || corte > primeraLinea) {
            corte = primeraLinea;
        }
        return new String[] { contenido.substring(0, corte).trim(), contenido.substring(corte).trim() };
    }

    private int capacidadCampo(PDAcroForm form, String nombreCampo, int fallback) {
        PDField field = form.getField(nombreCampo);
        if (field == null || field.getWidgets().isEmpty() || field.getWidgets().get(0).getRectangle() == null) {
            return fallback;
        }
        float ancho = field.getWidgets().get(0).getRectangle().getWidth();
        return Math.max(8, Math.min(120, (int) Math.floor(ancho / 4.85)));
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String referencia(Expediente expediente) {
        return "EXP-" + expediente.getId();
    }

    private String referencia(Solicitud solicitud) {
        return "SOL-" + solicitud.getId();
    }

    private String tipoTramite(Expediente expediente) {
        return expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                ? expediente.getTipoTramite().getNombre().name().replace('_', ' ')
                : "SIN TIPO";
    }

    private String tipoTramite(Solicitud solicitud) {
        return solicitud.getTipoTramite() != null && solicitud.getTipoTramite().getNombre() != null
                ? solicitud.getTipoTramite().getNombre().name().replace('_', ' ')
                : "SIN TIPO";
    }

    private String valor(String valor) {
        return vacio(valor) ? "NO CONSTA" : limpiar(valor);
    }

    private TipoPersona inferirTipoPersona(String identificador) {
        String normalizado = limpiar(identificador).replaceAll("[^A-Z0-9]", "");
        if (normalizado.matches("^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$")) {
            return TipoPersona.EMPRESA;
        }
        return TipoPersona.PARTICULAR;
    }

    private int ordenRol(RolInteresado rol) {
        if (rol == RolInteresado.COMPRAVENTA) return 0;
        if (rol == RolInteresado.COMPRADOR || rol == RolInteresado.TITULAR) return 1;
        if (rol == RolInteresado.VENDEDOR) return 2;
        return 3;
    }

    private enum TipoPlantilla {
        MANDATO(
                "Mandato",
                "Mandato con representacion a favor del gestor administrativo.",
                TipoDocumento.MANDATO,
                "mandato.pdf"),
        CAMBIO_TITULARIDAD(
                "Cambio de titularidad",
                "Solicitud oficial de cambio de titularidad de la DGT.",
                TipoDocumento.CAMBIO_TITULARIDAD,
                "cambio-titularidad.pdf"),
        CONTRATO_COMPRAVENTA(
                "Contrato de compraventa",
                "Contrato de compraventa de vehiculos usados en dos copias.",
                TipoDocumento.CONTRATO_COMPRAVENTA,
                "contrato-compraventa.pdf");

        private final String nombre;
        private final String descripcion;
        private final TipoDocumento tipoDocumento;
        private final String archivo;

        TipoPlantilla(String nombre, String descripcion, TipoDocumento tipoDocumento, String archivo) {
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.tipoDocumento = tipoDocumento;
            this.archivo = archivo;
        }
    }

    public record PlantillaPdfFile(String nombreArchivo, byte[] contenido) {
    }
}
