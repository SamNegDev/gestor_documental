package com.example.gestor_documental.dto.factura;
import com.example.gestor_documental.enums.EstadoJustificanteProvisional;
import java.time.LocalDateTime;
public record JustificanteProvisionalResponse(Long id, Long solicitudId, EstadoJustificanteProvisional estado, String nombreOriginal, LocalDateTime solicitadoEn, LocalDateTime actualizadoEn) {}
