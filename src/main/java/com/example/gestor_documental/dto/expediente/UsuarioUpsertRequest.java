package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolUsuario;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioUpsertRequest {
    private String nombre;
    private String apellidos;
    private String email;
    private String password;
    private RolUsuario rolUsuario;
    private List<Long> clienteIds;
    private boolean activo;
    private Long clienteId;
}
