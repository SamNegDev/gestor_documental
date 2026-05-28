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
public class ClienteResumenResponse {
    private Long id;
    private String nombre;
    private String nif;
    private String email;
    private String telefono;
}
