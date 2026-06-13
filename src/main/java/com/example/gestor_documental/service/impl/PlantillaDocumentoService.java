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
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteService;
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
    private final ExpedienteService expedienteService;
    private final DocumentoService documentoService;

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
        return construirPreview(expediente, plantilla, request != null ? request.campos() : null);
    }

    @Transactional
    public DocumentoGeneradoResponse generar(Long expedienteId, GenerarPlantillaRequest request, Usuario usuario) {
        Expediente expediente = expediente(expedienteId, usuario);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede generar documentos");
        }
        TipoPlantilla plantilla = tipo(request != null ? request.codigo() : null);
        PlantillaPreviewResponse preview = construirPreview(expediente, plantilla, request != null ? request.campos() : null);
        validarRequeridos(preview.campos());
        Map<String, String> valores = valores(preview.campos());
        byte[] pdf = rellenarPdf(expediente, plantilla, valores);
        Documento documento = documentoService.guardarGeneradoParaExpediente(
                expedienteId,
                pdf,
                plantilla.tipoDocumento,
                preview.nombreArchivo(),
                "Documento generado a partir del modelo oficial " + plantilla.nombre,
                usuario);
        return new DocumentoGeneradoResponse(documento.getId(), documento.getNombreArchivoOriginal(), documento.getTipoDocumento().name());
    }

    private PlantillaPreviewResponse construirPreview(Expediente expediente, TipoPlantilla plantilla, Map<String, String> cambios) {
        List<ExpedienteInteresado> relaciones = relaciones(expediente.getId());
        Map<String, String> valores = valoresIniciales(expediente, plantilla, relaciones);
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
                nombreArchivo(expediente, plantilla),
                plantilla.tipoDocumento.name(),
                campos,
                avisos);
    }

    private Map<String, String> valoresIniciales(Expediente expediente, TipoPlantilla plantilla,
            List<ExpedienteInteresado> relaciones) {
        Map<String, String> valores = new LinkedHashMap<>();
        String comprador = idPorRol(relaciones, RolInteresado.COMPRADOR, RolInteresado.TITULAR, RolInteresado.COMPRAVENTA);
        String vendedor = idPorRol(relaciones, RolInteresado.VENDEDOR);
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
                valores.put("marca", "");
                valores.put("modelo", "");
                valores.put("bastidor", "");
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
            if (mandante != null) valores.put("direccionMandante", limpiar(mandante.getDireccion()));
        }
        if (plantilla == TipoPlantilla.CAMBIO_TITULARIDAD && vacio(valores.get("domicilioVehiculo"))) {
            Interesado comprador = interesado(relaciones, valores.get("compradorId"), false);
            if (comprador != null) valores.put("domicilioVehiculo", limpiar(comprador.getDireccion()));
        }
        if (plantilla == TipoPlantilla.CONTRATO_COMPRAVENTA) {
            Interesado comprador = interesado(relaciones, valores.get("compradorId"), false);
            Interesado vendedor = interesado(relaciones, valores.get("vendedorId"), false);
            if (comprador != null && vacio(valores.get("direccionComprador"))) {
                valores.put("direccionComprador", limpiar(comprador.getDireccion()));
            }
            if (vendedor != null && vacio(valores.get("direccionVendedor"))) {
                valores.put("direccionVendedor", limpiar(vendedor.getDireccion()));
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
                    campo("domicilioVehiculo", "Domicilio del vehiculo", valores, true, "TEXT", "Se propone el domicilio del comprador."),
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

    private byte[] rellenarPdf(Expediente expediente, TipoPlantilla plantilla, Map<String, String> valores) {
        ClassPathResource resource = new ClassPathResource("plantillas/" + plantilla.archivo);
        try (InputStream input = resource.getInputStream();
                PDDocument document = PDDocument.load(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) throw new IOException("El modelo no contiene campos rellenables");
            List<ExpedienteInteresado> relaciones = relaciones(expediente.getId());
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
        String[] direccion = dividir(valores.get("direccionMandante"), 38);
        set(form, "Localidad", valores.get("localidadMandante"));
        set(form, "calle", direccion[0]);
        set(form, "calle1", direccion[1]);
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
        String[] direccionVendedor = dividir(valores.get("direccionVendedor"), 56);
        set(form, "Texto4", direccionVendedor[0]);
        set(form, "Texto5", direccionVendedor[1]);
        set(form, "Texto6", valores.get("marca"));
        set(form, "Texto7", valores.get("modelo"));
        set(form, "Texto8", expediente.getMatricula());
        set(form, "Texto9", valores.get("bastidor"));
        set(form, "Texto10", valores.get("cvf"));
        set(form, "Texto11", comprador.getNombre());
        set(form, "Texto12", comprador.getDni());
        String[] direccionComprador = dividir(valores.get("direccionComprador"), 56);
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

    private List<ExpedienteInteresado> relaciones(Long expedienteId) {
        return expedienteInteresadoRepository.findByExpedienteId(expedienteId).stream()
                .sorted(Comparator.comparingInt(item -> ordenRol(item.getRol())))
                .toList();
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

    private String nombreArchivo(Expediente expediente, TipoPlantilla plantilla) {
        String base = !vacio(expediente.getMatricula())
                ? expediente.getMatricula().trim().toUpperCase(Locale.ROOT)
                : referencia(expediente);
        return base + "_" + plantilla.tipoDocumento.name() + ".pdf";
    }

    private String[] dividir(String texto, int maxPrimeraLinea) {
        String contenido = limpiar(texto);
        if (contenido.length() <= maxPrimeraLinea) return new String[] { contenido, "" };
        int corte = contenido.lastIndexOf(' ', maxPrimeraLinea);
        if (corte < maxPrimeraLinea / 2) corte = maxPrimeraLinea;
        return new String[] { contenido.substring(0, corte).trim(), contenido.substring(corte).trim() };
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

    private String tipoTramite(Expediente expediente) {
        return expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                ? expediente.getTipoTramite().getNombre().name().replace('_', ' ')
                : "SIN TIPO";
    }

    private String valor(String valor) {
        return vacio(valor) ? "NO CONSTA" : limpiar(valor);
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
}
