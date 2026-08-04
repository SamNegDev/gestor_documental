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
    private String emailNotificaciones;
    @Builder.Default
    private List<String> emailsCopiaNotificaciones = new ArrayList<>();
    private String direccion;
    private String tipoVia;
    private String nombreVia;
    private String numeroVia;
    private String bloque;
    private String portal;
    private String escalera;
    private String piso;
    private String puerta;
    private String codigoPostal;
    private String municipio;
    private String localidad;
    private String provincia;
    private String telefono;
    private String preferenciaCanal;
    private boolean avisoIncidenciasActivo;
    private String horaAvisoIncidencias;
    private boolean avisoFinalizadosActivo;
    private String horaAvisoFinalizados;
    private String logoPrincipalUrl;
    private String logoCompactoUrl;
    @Builder.Default
    private List<DocumentoExpedienteResponse> documentos = new ArrayList<>();
    @Builder.Default
    private List<AdministradorClienteResponse> administradores = new ArrayList<>();
}
