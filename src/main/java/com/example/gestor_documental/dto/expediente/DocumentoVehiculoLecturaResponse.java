package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoVehiculoLecturaResponse {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Long id;
    private Long documentoId;
    private String tipoDocumentoDetectado;
    private String matricula;
    private String marca;
    private String modeloVehiculo;
    private String bastidor;
    private String fechaMatriculacion;
    private String fechaPrimeraMatriculacion;
    private Double confianzaGlobal;
    private boolean requiereRevision;
    private String mensaje;
    private String modelo;
    private String fechaLectura;

    public static DocumentoVehiculoLecturaResponse from(DocumentoVehiculoLectura lectura) {
        if (lectura == null) {
            return null;
        }
        return DocumentoVehiculoLecturaResponse.builder()
                .id(lectura.getId())
                .documentoId(lectura.getDocumento() != null ? lectura.getDocumento().getId() : null)
                .tipoDocumentoDetectado(lectura.getTipoDocumentoDetectado() != null ? lectura.getTipoDocumentoDetectado().name() : null)
                .matricula(lectura.getMatricula())
                .marca(lectura.getMarca())
                .modeloVehiculo(lectura.getModeloVehiculo())
                .bastidor(lectura.getBastidor())
                .fechaMatriculacion(lectura.getFechaMatriculacion())
                .fechaPrimeraMatriculacion(lectura.getFechaPrimeraMatriculacion())
                .confianzaGlobal(lectura.getConfianzaGlobal())
                .requiereRevision(lectura.isRequiereRevision())
                .mensaje(lectura.getMensaje())
                .modelo(lectura.getModelo())
                .fechaLectura(lectura.getFechaLectura() != null ? FORMATTER.format(lectura.getFechaLectura()) : null)
                .build();
    }
}
