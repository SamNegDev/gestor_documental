package com.example.gestor_documental.dto.catalogo;

public record GestionPersonaCatalogoResponse(
        Long id,
        String nifNormalizado,
        String nif,
        String tipoPersonaSugerido,
        String apellido1RazonSocial,
        String apellido2,
        String nombre,
        String sexo,
        String fechaNacimiento,
        String telefono,
        String telefonoMovil,
        String email,
        String dirSiglas,
        String dirCalle,
        String dirNumero,
        String dirPiso,
        String dirPuerta,
        String dirMunicipio,
        String dirPueblo,
        String dirProvincia,
        String dirCp,
        String dirPais
) {
}
