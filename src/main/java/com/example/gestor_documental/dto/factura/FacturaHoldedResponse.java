package com.example.gestor_documental.dto.factura;
import com.example.gestor_documental.enums.EstadoFacturaHolded;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public record FacturaHoldedResponse(Long id, String numero, String contactoNombre, String contactoNif, LocalDate fechaEmision, LocalDate fechaVencimiento, BigDecimal total, BigDecimal importePagado, String moneda, EstadoFacturaHolded estado, LocalDateTime sincronizadaEn, int lineasPendientesRevision, String detalleLineasPendientes, List<ComprobantePagoResponse> comprobantes) {}
