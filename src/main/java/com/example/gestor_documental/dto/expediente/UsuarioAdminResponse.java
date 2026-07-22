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
public class UsuarioAdminResponse {
    private Long id;
    private String nombre;
    private String apellidos;
    private String nombreCompleto;
    private String email;
    private String rol;
    private boolean activo;
    private List<ClienteResumenResponse> clientes;
    private ClienteResumenResponse cliente;
}
