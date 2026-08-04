package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.AudienciaHistorial;
import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.TipoActividadHistorial;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.HistorialCambioDetalle;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.HistorialCambioDetalleRepository;
import com.example.gestor_documental.repository.HistorialCambioExportRepository;
import com.example.gestor_documental.service.HistorialExpedienteExportService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class HistorialExpedienteExportServiceImpl implements HistorialExpedienteExportService {

    private static final int TAMANIO_LOTE = 500;
    private static final DateTimeFormatter FECHA_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final List<String> ACCIONES_COMUNICACION = List.of(
            "AVISO INCIDENCIA", "AVISO PENDIENTE", "LISTADO INCIDENCIAS", "SEGUIMIENTO POSPUESTO");

    private final HistorialCambioExportRepository historialRepository;
    private final HistorialCambioDetalleRepository detalleRepository;

    @Override
    public void exportarCsv(
            Long expedienteId,
            CategoriaHistorial categoria,
            LocalDate desde,
            LocalDate hasta,
            boolean soloCliente,
            OutputStream outputStream
    ) throws IOException {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ResponseStatusException(BAD_REQUEST, "La fecha hasta no puede ser anterior a la fecha desde");
        }

        LocalDateTime desdeFecha = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime hastaExclusiva = hasta != null ? hasta.plusDays(1).atStartOfDay() : null;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write('\ufeff');
        writer.write("Fecha;Accion;Descripcion;Usuario;Categoria;Tipo de actividad");
        if (!soloCliente) {
            writer.write(";Valores anteriores;Valores posteriores");
        }
        writer.write("\r\n");

        int pagina = 0;
        Slice<HistorialCambio> lote;
        do {
            PageRequest pageable = PageRequest.of(
                    pagina,
                    TAMANIO_LOTE,
                    Sort.by(Sort.Order.desc("fechaCambio"), Sort.Order.desc("id")));
            lote = historialRepository.buscarParaExportar(
                    expedienteId,
                    categoria,
                    desdeFecha,
                    hastaExclusiva,
                    soloCliente,
                    List.of(AudienciaHistorial.CLIENTE, AudienciaHistorial.AMBOS),
                    TipoActividadHistorial.COMUNICACION,
                    ACCIONES_COMUNICACION,
                    pageable);
            Map<Long, List<HistorialCambioDetalle>> detallesPorCambio = soloCliente
                    ? Map.of()
                    : cargarDetalles(lote.getContent());
            for (HistorialCambio cambio : lote.getContent()) {
                escribirFila(writer, cambio, detallesPorCambio.getOrDefault(cambio.getId(), List.of()), !soloCliente);
            }
            writer.flush();
            pagina++;
        } while (lote.hasNext());
    }

    private Map<Long, List<HistorialCambioDetalle>> cargarDetalles(List<HistorialCambio> cambios) {
        List<Long> ids = cambios.stream().map(HistorialCambio::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return detalleRepository.findByHistorialCambioIdInOrderByHistorialCambioIdAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(detalle -> detalle.getHistorialCambio().getId()));
    }

    private void escribirFila(
            BufferedWriter writer,
            HistorialCambio cambio,
            List<HistorialCambioDetalle> detalles,
            boolean incluirDetalles
    ) throws IOException {
        Usuario usuario = cambio.getUsuario();
        String nombreUsuario = usuario == null ? null
                : ((usuario.getNombre() != null ? usuario.getNombre() : "") + " "
                + (usuario.getApellidos() != null ? usuario.getApellidos() : "")).trim();
        writer.write(String.join(";",
                csv(cambio.getFechaCambio() != null ? cambio.getFechaCambio().format(FECHA_CSV) : null),
                csv(cambio.getAccion()),
                csv(cambio.getDescripcion()),
                csv(nombreUsuario),
                csv(cambio.getCategoriaClasificada().name()),
                csv(cambio.getTipoActividad().name())));
        if (incluirDetalles) {
            writer.write(";" + csv(resumenDetalles(detalles, HistorialCambioDetalle::getValorAnterior)));
            writer.write(";" + csv(resumenDetalles(detalles, HistorialCambioDetalle::getValorPosterior)));
        }
        writer.write("\r\n");
    }

    private String resumenDetalles(
            List<HistorialCambioDetalle> detalles,
            Function<HistorialCambioDetalle, String> valor
    ) {
        if (detalles.isEmpty()) {
            return null;
        }
        return detalles.stream()
                .map(detalle -> detalle.getEtiqueta() + ": " + valorVisible(valor.apply(detalle)))
                .collect(Collectors.joining(" | "));
    }

    private String valorVisible(String valor) {
        return valor == null || valor.isBlank() ? "Sin valor" : valor;
    }

    private String csv(String valor) {
        if (valor == null) return "\"\"";
        String seguro = valor.replace('\r', ' ').replace('\n', ' ').trim();
        if (!seguro.isEmpty() && "=+-@".indexOf(seguro.charAt(0)) >= 0) {
            seguro = "'" + seguro;
        }
        return "\"" + seguro.replace("\"", "\"\"") + "\"";
    }
}
