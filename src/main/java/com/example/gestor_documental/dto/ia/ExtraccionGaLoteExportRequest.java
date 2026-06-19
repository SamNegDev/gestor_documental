package com.example.gestor_documental.dto.ia;

import java.util.List;

public record ExtraccionGaLoteExportRequest(
        List<Long> expedienteIds
) {
}
