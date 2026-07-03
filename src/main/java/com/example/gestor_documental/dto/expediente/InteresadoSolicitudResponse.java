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
public class InteresadoSolicitudResponse {
    private String nombre;
    private String rol;
    private String dni;
    private String telefono;
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
    private String provincia;
    private boolean personaJuridica;
    private boolean clienteHabitual;
    private boolean documentoIdentidadAportado;
    private String documentoIdentidadOrigen;
    private boolean requiereRepresentanteLegal;
    private boolean representanteLegalAportado;
    private String representanteLegalNombre;
    private String representanteLegalDni;
}
