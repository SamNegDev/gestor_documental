package com.example.gestor_documental.dto.catalogo;

public record GestionVehiculoCatalogoResponse(
        Long id,
        String matriculaNormalizada,
        String matricula,
        String bastidor,
        String bastidorNormalizado,
        String marca,
        String modeloSugerido,
        String modeloTransmision,
        String modeloMatriculacion,
        String fechaMatriculacion,
        String fechaPrimeraMatriculacion,
        String anyoFabricacion,
        String carburanteCodigo,
        String carburanteDescripcion,
        String clasificacionItv,
        String codigoItv,
        String codigo620TipoVehiculo,
        String tipo620Descripcion,
        String potencia,
        String cilindrada,
        String fechaItv,
        String completitudScore
) {
}
