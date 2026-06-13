package com.example.gestor_documental.dto.expediente;

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
public class ClienteAdminResponse {
    private Long id;
    private String nif;
    private String nombre;
    private String email;
    private String direccion;
    private String telefono;
    private String logoPrincipalUrl;
    private String logoCompactoUrl;
}
