package com.example.gestor_documental.dto.expediente;

public record AdministradorClienteResponse(
        Long id, String dni, String nombre, String telefono, String direccion,
        String tipoVia, String nombreVia, String numeroVia, String bloque,
        String portal, String escalera, String piso, String puerta,
        String codigoPostal, String municipio, String localidad, String provincia
) {}