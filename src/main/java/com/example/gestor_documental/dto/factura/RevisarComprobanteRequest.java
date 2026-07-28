package com.example.gestor_documental.dto.factura;
import com.example.gestor_documental.enums.EstadoComprobantePago;
public record RevisarComprobanteRequest(EstadoComprobantePago estado, String observaciones) {}
