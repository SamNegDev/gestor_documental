package com.example.gestor_documental.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AvisosAdminResumenResponse {
    private long pendientes;
    private List<AvisoAdminResponse> avisos;
}
