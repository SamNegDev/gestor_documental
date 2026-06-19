package com.example.gestor_documental.dto.catalogo;

public record GestionRepresentanteCatalogoResponse(
        Long id,
        String empresaNifNormalizado,
        String empresaNif,
        String empresaTipoPersonaSugerido,
        String empresaApellido1RazonSocial,
        String representanteNifNormalizado,
        String representanteNif,
        String representanteTipoPersonaSugerido,
        String representanteApellido1RazonSocial,
        String representanteApellido2,
        String representanteNombre,
        String representanteSexo,
        String representanteFechaNacimiento,
        String reprConcepto,
        String reprDocAcreditacion,
        String representanteDirSiglas,
        String representanteDirCalle,
        String representanteDirNumero,
        String representanteDirPiso,
        String representanteDirPuerta,
        String representanteDirMunicipio,
        String representanteDirPueblo,
        String representanteDirProvincia,
        String representanteDirCp,
        String representanteDirPais
) {
}
