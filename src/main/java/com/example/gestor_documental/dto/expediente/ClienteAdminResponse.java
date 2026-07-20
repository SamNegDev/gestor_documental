package com.example.gestor_documental.dto.expediente;

import java.util.ArrayList;
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
public class ClienteAdminResponse {
    private Long id;
    private String nif;
    private String nombre;
    private String email;
    private String direccion;
    private String telefono;
    private String preferenciaCanal;
    private String logoPrincipalUrl;
    private String logoCompactoUrl;
    @Builder.Default
    private List<DocumentoExpedienteResponse> documentos = new ArrayList<>();
    @Builder.Default
    private List<AdministradorClienteResponse> administradores = new ArrayList<>();
}
