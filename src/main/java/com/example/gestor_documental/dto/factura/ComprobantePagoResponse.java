package com.example.gestor_documental.dto.factura;
import com.example.gestor_documental.enums.EstadoComprobantePago;
import java.time.LocalDateTime;
public record ComprobantePagoResponse(Long id, String nombreOriginal, String contentType, long tamano, EstadoComprobantePago estado, String observaciones, LocalDateTime creadoEn, LocalDateTime revisadoEn) {}
