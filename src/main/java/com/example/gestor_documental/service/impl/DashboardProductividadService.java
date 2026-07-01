package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ProductividadDashboardResponse;
import com.example.gestor_documental.dto.expediente.ProductividadDesgloseResponse;
import com.example.gestor_documental.dto.expediente.ProductividadSerieResponse;
import com.example.gestor_documental.repository.DashboardProductividadRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardProductividadService {

    private static final ZoneId APP_ZONE = ZoneId.of("Atlantic/Canary");
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DashboardProductividadRepository repository;

    public ProductividadDashboardResponse obtener(String periodo, LocalDate fechaDesde, LocalDate fechaHasta) {
        DateRange range = rango(periodo, fechaDesde, fechaHasta);
        LocalDateTime desde = range.desde().atStartOfDay();
        LocalDateTime hasta = range.hastaExclusiva().atStartOfDay();

        return new ProductividadDashboardResponse(
                range.etiqueta(),
                range.desde().format(ISO),
                range.hastaExclusiva().minusDays(1).format(ISO),
                repository.contarCreados(desde, hasta),
                repository.contarFinalizados(desde, hasta),
                redondear(repository.tiempoMedioFinalizacion(desde, hasta)),
                repository.contarEnCurso(),
                repository.contarIncidenciasActivas(),
                repository.contarExpedientesConDocumentacionPendiente(),
                evolucion(range, desde, hasta),
                mapTiempos(repository.tiemposPorTramite(desde, hasta)),
                mapClientes(repository.volumenPorCliente(desde, hasta)),
                mapFases(repository.cuellosBotella())
        );
    }

    private List<ProductividadSerieResponse> evolucion(DateRange range, LocalDateTime desde, LocalDateTime hasta) {
        Map<LocalDate, Long> creados = porFecha(repository.creadosPorDia(desde, hasta));
        Map<LocalDate, Long> finalizados = porFecha(repository.finalizadosPorDia(desde, hasta));
        List<Bucket> buckets = buckets(range.desde(), range.hastaExclusiva());
        return buckets.stream().map(bucket -> new ProductividadSerieResponse(
                bucket.etiqueta(),
                sumar(creados, bucket),
                sumar(finalizados, bucket)
        )).toList();
    }

    private List<ProductividadDesgloseResponse> mapTiempos(List<Object[]> rows) {
        return rows.stream().map(row -> new ProductividadDesgloseResponse(
                text(row[0]),
                humanizar(text(row[0])),
                number(row[1]).longValue(),
                redondear(number(row[2]).doubleValue())
        )).toList();
    }

    private List<ProductividadDesgloseResponse> mapClientes(List<Object[]> rows) {
        return rows.stream().map(row -> new ProductividadDesgloseResponse(
                text(row[0]),
                text(row[1]),
                number(row[2]).longValue(),
                number(row[3]).doubleValue()
        )).toList();
    }

    private List<ProductividadDesgloseResponse> mapFases(List<Object[]> rows) {
        return rows.stream().map(row -> new ProductividadDesgloseResponse(
                text(row[0]),
                etiquetaFase(text(row[0])),
                number(row[1]).longValue(),
                redondear(number(row[2]).doubleValue())
        )).toList();
    }

    private Map<LocalDate, Long> porFecha(List<Object[]> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        rows.forEach(row -> result.put(localDate(row[0]), number(row[1]).longValue()));
        return result;
    }

    private long sumar(Map<LocalDate, Long> values, Bucket bucket) {
        return values.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(bucket.desde()) && entry.getKey().isBefore(bucket.hasta()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private List<Bucket> buckets(LocalDate desde, LocalDate hastaExclusiva) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(desde, hastaExclusiva);
        List<Bucket> result = new ArrayList<>();
        if (dias <= 45) {
            for (LocalDate date = desde; date.isBefore(hastaExclusiva); date = date.plusDays(1)) {
                result.add(new Bucket(date, date.plusDays(1), date.format(DateTimeFormatter.ofPattern("dd/MM"))));
            }
            return result;
        }
        if (dias <= 150) {
            LocalDate cursor = desde;
            while (cursor.isBefore(hastaExclusiva)) {
                LocalDate end = cursor.plusWeeks(1).isBefore(hastaExclusiva) ? cursor.plusWeeks(1) : hastaExclusiva;
                result.add(new Bucket(cursor, end, cursor.format(DateTimeFormatter.ofPattern("dd/MM"))));
                cursor = end;
            }
            return result;
        }
        YearMonth cursor = YearMonth.from(desde);
        YearMonth last = YearMonth.from(hastaExclusiva.minusDays(1));
        while (!cursor.isAfter(last)) {
            LocalDate start = cursor.atDay(1).isBefore(desde) ? desde : cursor.atDay(1);
            LocalDate end = cursor.plusMonths(1).atDay(1).isAfter(hastaExclusiva) ? hastaExclusiva : cursor.plusMonths(1).atDay(1);
            String label = cursor.getMonth().getDisplayName(TextStyle.SHORT, ES).replace(".", "") + " " + String.valueOf(cursor.getYear()).substring(2);
            result.add(new Bucket(start, end, label));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    private DateRange rango(String periodo, LocalDate fechaDesde, LocalDate fechaHasta) {
        LocalDate hoy = LocalDate.now(APP_ZONE);
        String value = periodo != null ? periodo : "ULTIMA_SEMANA";
        return switch (value) {
            case "ULTIMA_SEMANA" -> new DateRange(hoy.minusDays(6), hoy.plusDays(1), "Ultima semana");
            case "MES_ANTERIOR" -> {
                YearMonth anterior = YearMonth.from(hoy).minusMonths(1);
                yield new DateRange(anterior.atDay(1), anterior.plusMonths(1).atDay(1), "Mes anterior");
            }
            case "ULTIMOS_3_MESES" -> new DateRange(hoy.minusMonths(3), hoy.plusDays(1), "Ultimos 3 meses");
            case "ESTE_ANIO" -> new DateRange(hoy.withDayOfYear(1), hoy.plusDays(1), "Este ano");
            case "TODO" -> new DateRange(
                    repository.fechaPrimerExpediente() != null ? repository.fechaPrimerExpediente() : hoy.withDayOfMonth(1),
                    hoy.plusDays(1),
                    "Todo el historico");
            case "PERSONALIZADO" -> {
                if (fechaDesde == null || fechaHasta == null || fechaDesde.isAfter(fechaHasta)) {
                    throw new IllegalArgumentException("El rango personalizado no es valido");
                }
                yield new DateRange(fechaDesde, fechaHasta.plusDays(1), "Rango personalizado");
            }
            default -> new DateRange(hoy.withDayOfMonth(1), hoy.plusDays(1), "Este mes");
        };
    }

    private LocalDate localDate(Object value) {
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(value.toString());
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private String text(Object value) {
        return value != null ? value.toString() : "";
    }

    private double redondear(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String humanizar(String value) {
        if (value == null || value.isBlank()) return "Sin tipo";
        String normalized = value.toLowerCase(ES).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String etiquetaFase(String code) {
        return switch (code) {
            case "COMPROBACION" -> "Comprobacion documental";
            case "MODELO_620" -> "Pendiente Modelo 620";
            case "CIERRE" -> "Envio DGT o cierre";
            case "DOCUMENTACION" -> "Documentacion del cliente";
            case "RESPUESTA_CLIENTE" -> "Respuesta del cliente";
            case "INCIDENCIA" -> "Incidencias abiertas";
            case "REVISION_APORTACION" -> "Aportaciones por revisar";
            default -> humanizar(code);
        };
    }

    private record DateRange(LocalDate desde, LocalDate hastaExclusiva, String etiqueta) {
    }

    private record Bucket(LocalDate desde, LocalDate hasta, String etiqueta) {
    }
}
