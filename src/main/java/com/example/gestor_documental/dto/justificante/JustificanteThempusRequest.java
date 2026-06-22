package com.example.gestor_documental.dto.justificante;

public record JustificanteThempusRequest(
        String despacho,
        String nif,
        String version,
        String jefatura,
        String diasValidez,
        String sucursal,
        String tipoTramite,
        String documentos,
        String expedientePlataforma,
        String motivo,
        DatosAdquirente adquirente,
        DatosVehiculo vehiculo
) {

    public record DatosAdquirente(
            String razonSocial,
            String nombre,
            String apellido1,
            String apellido2,
            String dni,
            String sexo,
            String siglasDireccion,
            String nombreViaDireccion,
            String kmDireccion,
            String hectometroDireccion,
            String numeroDireccion,
            String letraDireccion,
            String escaleraDireccion,
            String pisoDireccion,
            String puertaDireccion,
            String bloqueDireccion,
            String municipio,
            String pueblo,
            String provincia,
            String cp,
            String ifa
    ) {
    }

    public record DatosVehiculo(
            String tipoVehiculo,
            String matricula,
            String marca,
            String modelo,
            String numeroBastidor
    ) {
    }
}
