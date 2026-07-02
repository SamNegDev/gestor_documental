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
public class DocumentoExpedienteResponse {
    private Long id;
    private String nombre;
    private String nombreOriginal;
    private String tipo;
    private String descripcion;
    private String fechaSubida;
    private String subidoPor;
    private Long interesadoId;
    private String interesadoNombre;
    private Long operacionId;
    private String operacionLabel;
    private String estado;
    private boolean subido;
    private boolean requeridoAhora;
    private DocumentoIdentidadLecturaResponse lecturaIdentidad;
    private DocumentoRolesLecturaResponse lecturaRoles;
    private DocumentoVehiculoLecturaResponse lecturaVehiculo;
}
