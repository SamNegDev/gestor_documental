package com.example.gestor_documental.dto.expediente;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteUpsertRequest {
    private String nif;
    private String nombre;
    private String email;
    private String emailNotificaciones;
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
    private boolean avisoIncidenciasActivo = true;
    private String horaAvisoIncidencias = "17:00";
    private boolean avisoFinalizadosActivo = true;
    private String horaAvisoFinalizados = "17:00";
}