package com.example.gestor_documental.dto.seguimiento;

import java.time.LocalDateTime;

public record PosponerSeguimientoRequest(LocalDateTime proximoAviso) {}
