package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.catalogo.CatalogoGestionResumenResponse;
import com.example.gestor_documental.dto.catalogo.GestionPersonaCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.GestionRepresentanteCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.GestionVehiculoCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.ImportacionCatalogoResponse;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.model.GestionPersonaCatalogo;
import com.example.gestor_documental.model.GestionPersonaRepresentanteCatalogo;
import com.example.gestor_documental.model.GestionVehiculoCatalogo;
import com.example.gestor_documental.repository.GestionPersonaCatalogoRepository;
import com.example.gestor_documental.repository.GestionPersonaRepresentanteCatalogoRepository;
import com.example.gestor_documental.repository.GestionVehiculoCatalogoRepository;
import com.example.gestor_documental.service.GestionTraficoCatalogoService;
import com.example.gestor_documental.util.GaDateNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GestionTraficoCatalogoServiceImpl implements GestionTraficoCatalogoService {

    private static final int BATCH_SIZE = 1_000;

    private final GestionPersonaCatalogoRepository personaRepository;
    private final GestionPersonaRepresentanteCatalogoRepository representanteRepository;
    private final GestionVehiculoCatalogoRepository vehiculoRepository;

    @Override
    @Transactional(readOnly = true)
    public CatalogoGestionResumenResponse resumen() {
        long personas = personaRepository.count();
        long representantes = representanteRepository.count();
        long vehiculos = vehiculoRepository.count();
        return new CatalogoGestionResumenResponse(personas, representantes, vehiculos, personas > 0, representantes > 0, vehiculos > 0);
    }

    @Override
    @Transactional
    public ImportacionCatalogoResponse importarPersonas(MultipartFile archivo) {
        List<Map<String, String>> rows = leerCsv(archivo, Set.of("nif_normalizado", "apellido1_razon_social", "codigo_persona"));
        LocalDateTime fecha = LocalDateTime.now();
        personaRepository.deleteAllInBatch();
        int importados = 0;
        int omitidos = 0;
        List<GestionPersonaCatalogo> batch = new ArrayList<>(BATCH_SIZE);
        for (Map<String, String> row : rows) {
            if (vacio(row, "nif_normalizado") && vacio(row, "apellido1_razon_social") && vacio(row, "nombre")) {
                omitidos++;
                continue;
            }
            batch.add(mapPersona(row, fecha));
            if (batch.size() >= BATCH_SIZE) {
                personaRepository.saveAll(batch);
                importados += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            personaRepository.saveAll(batch);
            importados += batch.size();
        }
        return new ImportacionCatalogoResponse("personas", rows.size(), importados, omitidos, true, "Catalogo de personas importado.");
    }

    @Override
    @Transactional
    public ImportacionCatalogoResponse importarRepresentantes(MultipartFile archivo) {
        List<Map<String, String>> rows = leerCsv(archivo, Set.of("empresa_nif_normalizado", "representante_nif_normalizado", "repr_codigo_persona"));
        LocalDateTime fecha = LocalDateTime.now();
        representanteRepository.deleteAllInBatch();
        int importados = 0;
        int omitidos = 0;
        List<GestionPersonaRepresentanteCatalogo> batch = new ArrayList<>(BATCH_SIZE);
        for (Map<String, String> row : rows) {
            if (vacio(row, "empresa_nif_normalizado") && vacio(row, "representante_nif_normalizado")) {
                omitidos++;
                continue;
            }
            batch.add(mapRepresentante(row, fecha));
            if (batch.size() >= BATCH_SIZE) {
                representanteRepository.saveAll(batch);
                importados += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            representanteRepository.saveAll(batch);
            importados += batch.size();
        }
        return new ImportacionCatalogoResponse("representantes", rows.size(), importados, omitidos, true, "Catalogo de representantes importado.");
    }

    @Override
    @Transactional
    public ImportacionCatalogoResponse importarVehiculos(MultipartFile archivo) {
        List<Map<String, String>> rows = leerCsv(archivo, Set.of("matricula_normalizada", "marca", "modelo_sugerido"));
        LocalDateTime fecha = LocalDateTime.now();
        vehiculoRepository.deleteAllInBatch();
        int importados = 0;
        int omitidos = 0;
        List<GestionVehiculoCatalogo> batch = new ArrayList<>(BATCH_SIZE);
        for (Map<String, String> row : rows) {
            if (vacio(row, "matricula_normalizada")) {
                omitidos++;
                continue;
            }
            batch.add(mapVehiculo(row, fecha));
            if (batch.size() >= BATCH_SIZE) {
                vehiculoRepository.saveAll(batch);
                importados += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            vehiculoRepository.saveAll(batch);
            importados += batch.size();
        }
        return new ImportacionCatalogoResponse("vehiculos", rows.size(), importados, omitidos, true, "Catalogo de vehiculos importado.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<GestionPersonaCatalogoResponse> buscarPersonas(String q, int limit) {
        return personaRepository.buscar(query(q), PageRequest.of(0, limite(limit))).stream().map(this::mapPersonaResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GestionRepresentanteCatalogoResponse> buscarRepresentantes(String q, int limit) {
        return representanteRepository.buscar(query(q), PageRequest.of(0, limite(limit))).stream().map(this::mapRepresentanteResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GestionVehiculoCatalogoResponse> buscarVehiculos(String q, int limit) {
        return vehiculoRepository.buscar(query(q), PageRequest.of(0, limite(limit))).stream().map(this::mapVehiculoResponse).toList();
    }

    private GestionPersonaCatalogo mapPersona(Map<String, String> row, LocalDateTime fecha) {
        GestionPersonaCatalogo item = new GestionPersonaCatalogo();
        item.setCodigoColegio(valor(row, "codigo_colegio"));
        item.setNumeroColegiado(valor(row, "numero_colegiado"));
        item.setCodigoDespacho(valor(row, "codigo_despacho"));
        item.setCodigoPersona(valor(row, "codigo_persona"));
        item.setNifNormalizado(valor(row, "nif_normalizado"));
        item.setTipoPersonaSugerido(valor(row, "tipo_persona_sugerido"));
        item.setNif(valor(row, "nif"));
        item.setApellido1RazonSocial(valor(row, "apellido1_razon_social"));
        item.setApellido2(valor(row, "apellido2"));
        item.setNombre(valor(row, "nombre"));
        item.setSexo(valor(row, "sexo"));
        item.setFechaNacimiento(fechaGa(row, "fecha_nacimiento"));
        item.setAutonomoSn(valor(row, "autonomo_sn"));
        item.setTelefono(valor(row, "telefono"));
        item.setTelefonoMovil(valor(row, "telefono_movil"));
        item.setTelefono2(valor(row, "telefono_2"));
        item.setEmail(valor(row, "email"));
        item.setEmailFacturacion(valor(row, "email_facturacion"));
        item.setEmailNotificaciones(valor(row, "email_notificaciones"));
        item.setTipoDocumentoSustitutivo(valor(row, "tipo_documento_sustitutivo"));
        item.setFechaCaducidadDocumento(fechaGa(row, "fecha_caducidad_documento"));
        item.setNacionalidad(valor(row, "nacionalidad"));
        item.setMandatoFecha(fechaGa(row, "mandato_fecha"));
        item.setMandatoReferencia(valor(row, "mandato_referencia"));
        item.setMandatoPrimeraVezSn(valor(row, "mandato_primera_vez_sn"));
        item.setDirSiglas(valor(row, "dir_siglas"));
        item.setDirCalle(valor(row, "dir_calle"));
        item.setDirNumero(valor(row, "dir_numero"));
        item.setDirKm(valor(row, "dir_km"));
        item.setDirHectometro(valor(row, "dir_hectometro"));
        item.setDirLetra(valor(row, "dir_letra"));
        item.setDirEscalera(valor(row, "dir_escalera"));
        item.setDirPiso(valor(row, "dir_piso"));
        item.setDirPuerta(valor(row, "dir_puerta"));
        item.setDirBloque(valor(row, "dir_bloque"));
        item.setDirMunicipio(valor(row, "dir_municipio"));
        item.setDirPueblo(valor(row, "dir_pueblo"));
        item.setDirProvincia(valor(row, "dir_provincia"));
        item.setDirCp(valor(row, "dir_cp"));
        item.setDirPais(valor(row, "dir_pais"));
        item.setReprCodigoColegio(valor(row, "repr_codigo_colegio"));
        item.setReprNumeroColegiado(valor(row, "repr_numero_colegiado"));
        item.setReprCodigoDespacho(valor(row, "repr_codigo_despacho"));
        item.setReprCodigoPersona(valor(row, "repr_codigo_persona"));
        item.setReprConcepto(valor(row, "repr_concepto"));
        item.setReprDocAcreditacion(valor(row, "repr_doc_acreditacion"));
        item.setFechaImportacion(fecha);
        return item;
    }

    private GestionPersonaRepresentanteCatalogo mapRepresentante(Map<String, String> row, LocalDateTime fecha) {
        GestionPersonaRepresentanteCatalogo item = new GestionPersonaRepresentanteCatalogo();
        item.setEmpresaCodigoColegio(valor(row, "empresa_codigo_colegio"));
        item.setEmpresaNumeroColegiado(valor(row, "empresa_numero_colegiado"));
        item.setEmpresaCodigoDespacho(valor(row, "empresa_codigo_despacho"));
        item.setEmpresaCodigoPersona(valor(row, "empresa_codigo_persona"));
        item.setEmpresaNifNormalizado(valor(row, "empresa_nif_normalizado"));
        item.setEmpresaTipoPersonaSugerido(valor(row, "empresa_tipo_persona_sugerido"));
        item.setEmpresaNif(valor(row, "empresa_nif"));
        item.setEmpresaApellido1RazonSocial(valor(row, "empresa_apellido1_razon_social"));
        item.setEmpresaApellido2(valor(row, "empresa_apellido2"));
        item.setEmpresaNombre(valor(row, "empresa_nombre"));
        item.setReprCodigoColegio(valor(row, "repr_codigo_colegio"));
        item.setReprNumeroColegiado(valor(row, "repr_numero_colegiado"));
        item.setReprCodigoDespacho(valor(row, "repr_codigo_despacho"));
        item.setReprCodigoPersona(valor(row, "repr_codigo_persona"));
        item.setReprConcepto(valor(row, "repr_concepto"));
        item.setReprDocAcreditacion(valor(row, "repr_doc_acreditacion"));
        item.setRepresentanteNifNormalizado(valor(row, "representante_nif_normalizado"));
        item.setRepresentanteTipoPersonaSugerido(valor(row, "representante_tipo_persona_sugerido"));
        item.setRepresentanteNif(valor(row, "representante_nif"));
        item.setRepresentanteApellido1RazonSocial(valor(row, "representante_apellido1_razon_social"));
        item.setRepresentanteApellido2(valor(row, "representante_apellido2"));
        item.setRepresentanteNombre(valor(row, "representante_nombre"));
        item.setRepresentanteSexo(valor(row, "representante_sexo"));
        item.setRepresentanteFechaNacimiento(fechaGa(row, "representante_fecha_nacimiento"));
        item.setRepresentanteTipoDocumentoSustitutivo(valor(row, "representante_tipo_documento_sustitutivo"));
        item.setRepresentanteFechaCaducidadDocumento(fechaGa(row, "representante_fecha_caducidad_documento"));
        item.setRepresentanteNacionalidad(valor(row, "representante_nacionalidad"));
        item.setRepresentanteDirSiglas(valor(row, "representante_dir_siglas"));
        item.setRepresentanteDirCalle(valor(row, "representante_dir_calle"));
        item.setRepresentanteDirNumero(valor(row, "representante_dir_numero"));
        item.setRepresentanteDirKm(valor(row, "representante_dir_km"));
        item.setRepresentanteDirHectometro(valor(row, "representante_dir_hectometro"));
        item.setRepresentanteDirLetra(valor(row, "representante_dir_letra"));
        item.setRepresentanteDirEscalera(valor(row, "representante_dir_escalera"));
        item.setRepresentanteDirPiso(valor(row, "representante_dir_piso"));
        item.setRepresentanteDirPuerta(valor(row, "representante_dir_puerta"));
        item.setRepresentanteDirBloque(valor(row, "representante_dir_bloque"));
        item.setRepresentanteDirMunicipio(valor(row, "representante_dir_municipio"));
        item.setRepresentanteDirPueblo(valor(row, "representante_dir_pueblo"));
        item.setRepresentanteDirProvincia(valor(row, "representante_dir_provincia"));
        item.setRepresentanteDirCp(valor(row, "representante_dir_cp"));
        item.setRepresentanteDirPais(valor(row, "representante_dir_pais"));
        item.setFechaImportacion(fecha);
        return item;
    }

    private GestionVehiculoCatalogo mapVehiculo(Map<String, String> row, LocalDateTime fecha) {
        GestionVehiculoCatalogo item = new GestionVehiculoCatalogo();
        item.setCodigoColegio(valor(row, "codigo_colegio"));
        item.setNumeroColegiado(valor(row, "numero_colegiado"));
        item.setCodigoDespacho(valor(row, "codigo_despacho"));
        item.setCodigoVehiculo(valor(row, "codigo_vehiculo"));
        item.setMatriculaNormalizada(valor(row, "matricula_normalizada"));
        item.setBastidorNormalizado(valor(row, "bastidor_normalizado"));
        item.setMatricula(valor(row, "matricula"));
        item.setBastidor(valor(row, "bastidor"));
        item.setMarca(valor(row, "marca"));
        item.setModeloSugerido(valor(row, "modelo_sugerido"));
        item.setModeloTransmision(valor(row, "modelo_transmision"));
        item.setModeloMatriculacion(valor(row, "modelo_matriculacion"));
        item.setTipo(valor(row, "tipo"));
        item.setVersion(valor(row, "version"));
        item.setMarcaBase(valor(row, "marca_base"));
        item.setTipoBase(valor(row, "tipo_base"));
        item.setVersionBase(valor(row, "version_base"));
        item.setFechaMatriculacion(fechaGa(row, "fecha_matriculacion"));
        item.setFechaPrimeraMatriculacion(fechaGa(row, "fecha_primera_matriculacion"));
        item.setAnyoFabricacion(valor(row, "anyo_fabricacion"));
        item.setCarburanteCodigo(valor(row, "carburante_codigo"));
        item.setCarburanteDescripcion(valor(row, "carburante_descripcion"));
        item.setCarburanteCodigoMate(valor(row, "carburante_codigo_mate"));
        item.setTipoAlimentacion(valor(row, "tipo_alimentacion"));
        item.setClasificacionItv(valor(row, "clasificacion_itv"));
        item.setCodigoItv(valor(row, "codigo_itv"));
        item.setCodigoTrafTipoVehiculo(valor(row, "codigo_traf_tipo_vehiculo"));
        item.setTrafTipoDescripcion(valor(row, "traf_tipo_descripcion"));
        item.setCodigo620TipoVehiculo(valor(row, "codigo_620_tipo_vehiculo"));
        item.setTipo620Descripcion(valor(row, "tipo_620_descripcion"));
        item.setTipo620Tt(valor(row, "tipo_620_tt"));
        item.setPotencia(valor(row, "potencia"));
        item.setCilindrada(valor(row, "cilindrada"));
        item.setNumeroCilindros(valor(row, "numero_cilindros"));
        item.setMasa(valor(row, "masa"));
        item.setTara(valor(row, "tara"));
        item.setPlazas(valor(row, "plazas"));
        item.setFechaItv(fechaGa(row, "fecha_itv"));
        item.setHistoricoSn(valor(row, "historico_sn"));
        item.setRenting(valor(row, "renting"));
        item.setFechaUltModif(fechaGa(row, "fecha_ult_modif"));
        item.setCompletitudScore(valor(row, "completitud_score"));
        item.setDuplicadoMatricula(valor(row, "duplicado_matricula"));
        item.setPreferenteMatricula(valor(row, "preferente_matricula"));
        item.setFechaImportacion(fecha);
        return item;
    }

    private GestionPersonaCatalogoResponse mapPersonaResponse(GestionPersonaCatalogo p) {
        return new GestionPersonaCatalogoResponse(p.getId(), p.getNifNormalizado(), p.getNif(), p.getTipoPersonaSugerido(),
                p.getApellido1RazonSocial(), p.getApellido2(), p.getNombre(), p.getSexo(), GaDateNormalizer.toGaDate(p.getFechaNacimiento()),
                p.getTelefono(), p.getTelefonoMovil(), p.getEmail(), p.getDirSiglas(), p.getDirCalle(), p.getDirNumero(),
                p.getDirPiso(), p.getDirPuerta(), p.getDirMunicipio(), p.getDirPueblo(), p.getDirProvincia(), p.getDirCp(), p.getDirPais());
    }

    private GestionRepresentanteCatalogoResponse mapRepresentanteResponse(GestionPersonaRepresentanteCatalogo r) {
        return new GestionRepresentanteCatalogoResponse(r.getId(), r.getEmpresaNifNormalizado(), r.getEmpresaNif(),
                r.getEmpresaTipoPersonaSugerido(), r.getEmpresaApellido1RazonSocial(), r.getRepresentanteNifNormalizado(),
                r.getRepresentanteNif(), r.getRepresentanteTipoPersonaSugerido(), r.getRepresentanteApellido1RazonSocial(),
                r.getRepresentanteApellido2(), r.getRepresentanteNombre(), r.getRepresentanteSexo(), GaDateNormalizer.toGaDate(r.getRepresentanteFechaNacimiento()),
                r.getReprConcepto(), r.getReprDocAcreditacion(), r.getRepresentanteDirSiglas(), r.getRepresentanteDirCalle(),
                r.getRepresentanteDirNumero(), r.getRepresentanteDirPiso(), r.getRepresentanteDirPuerta(), r.getRepresentanteDirMunicipio(),
                r.getRepresentanteDirPueblo(), r.getRepresentanteDirProvincia(), r.getRepresentanteDirCp(), r.getRepresentanteDirPais());
    }

    private GestionVehiculoCatalogoResponse mapVehiculoResponse(GestionVehiculoCatalogo v) {
        return new GestionVehiculoCatalogoResponse(v.getId(), v.getMatriculaNormalizada(), v.getMatricula(), v.getBastidor(),
                v.getBastidorNormalizado(), v.getMarca(), v.getModeloSugerido(), v.getModeloTransmision(), v.getModeloMatriculacion(),
                GaDateNormalizer.toGaDate(v.getFechaMatriculacion()), GaDateNormalizer.toGaDate(v.getFechaPrimeraMatriculacion()), v.getAnyoFabricacion(), v.getCarburanteCodigo(),
                v.getCarburanteDescripcion(), v.getClasificacionItv(), v.getCodigoItv(), v.getCodigo620TipoVehiculo(),
                v.getTipo620Descripcion(), v.getPotencia(), v.getCilindrada(), GaDateNormalizer.toGaDate(v.getFechaItv()), v.getCompletitudScore());
    }

    private List<Map<String, String>> leerCsv(MultipartFile archivo, Set<String> requiredHeaders) {
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona un archivo CSV.");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new OperacionInvalidaException("El CSV esta vacio.");
            }
            headerLine = quitarBom(headerLine);
            char delimiter = delimiter(headerLine);
            List<String> headers = parseCsvLine(headerLine, delimiter);
            for (String required : requiredHeaders) {
                if (!headers.contains(required)) {
                    throw new OperacionInvalidaException("El CSV no parece correcto. Falta la columna " + required + ".");
                }
            }
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line, delimiter);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? clean(values.get(i)) : null);
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException exception) {
            throw new OperacionInvalidaException("No se pudo leer el CSV: " + exception.getMessage());
        }
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private char delimiter(String headerLine) {
        long commas = headerLine.chars().filter(ch -> ch == ',').count();
        long semicolons = headerLine.chars().filter(ch -> ch == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    private String quitarBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private String valor(Map<String, String> row, String key) {
        return clean(row.get(key));
    }

    private String fechaGa(Map<String, String> row, String key) {
        String value = valor(row, key);
        return value == null ? null : GaDateNormalizer.toGaDate(value);
    }

    private boolean vacio(Map<String, String> row, String key) {
        String value = valor(row, key);
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String query(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private int limite(int limit) {
        if (limit <= 0) {
            return 25;
        }
        return Math.min(limit, 100);
    }
}
