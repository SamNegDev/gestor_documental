package com.example.gestor_documental.dto.expediente;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResumenResponse {
    private Long id;
    private String nombreCompleto;
    private String email;
    private String rol;
    private ClienteResumenResponse cliente;
    private List<ClienteResumenResponse> clientes;
}
