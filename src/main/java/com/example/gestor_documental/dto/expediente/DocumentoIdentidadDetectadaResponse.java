package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.stream.Stream;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoIdentidadDetectadaResponse {

    private String tipoDocumentoDetectado;
    private String identificador;
    private String nombre;
    private String apellido1;
    private String apellido2;
    private String razonSocial;
    private String nombreCompleto;
    private String fechaNacimiento;
    private String fechaCaducidad;
    private String direccionTexto;
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
    private Double confianzaGlobal;
    private boolean requiereRevision;
    private String observaciones;

    public static DocumentoIdentidadDetectadaResponse from(IdentidadDetectada identidad) {
        if (identidad == null) {
            return null;
        }
        return DocumentoIdentidadDetectadaResponse.builder()
                .tipoDocumentoDetectado(identidad.tipoDocumento())
                .identificador(identidad.identificador())
                .nombre(identidad.nombre())
                .apellido1(identidad.apellido1())
                .apellido2(identidad.apellido2())
                .razonSocial(identidad.razonSocial())
                .nombreCompleto(nombreCompleto(identidad))
                .fechaNacimiento(identidad.fechaNacimiento())
                .fechaCaducidad(identidad.fechaCaducidad())
                .direccionTexto(identidad.direccionTexto())
                .tipoVia(identidad.tipoVia())
                .nombreVia(identidad.nombreVia())
                .numeroVia(identidad.numeroVia())
                .bloque(identidad.bloque())
                .portal(identidad.portal())
                .escalera(identidad.escalera())
                .piso(identidad.piso())
                .puerta(identidad.puerta())
                .codigoPostal(identidad.codigoPostal())
                .municipio(identidad.municipio())
                .provincia(identidad.provincia())
                .confianzaGlobal(identidad.confianzaGlobal())
                .requiereRevision(identidad.requiereRevision())
                .observaciones(identidad.observaciones())
                .build();
    }

    private static String nombreCompleto(IdentidadDetectada identidad) {
        if (identidad.razonSocial() != null && !identidad.razonSocial().isBlank()) {
            return identidad.razonSocial();
        }
        String nombre = Stream.of(identidad.nombre(), identidad.apellido1(), identidad.apellido2())
                .filter(valor -> valor != null && !valor.isBlank())
                .reduce("", (actual, valor) -> actual.isBlank() ? valor.trim() : actual + " " + valor.trim());
        return nombre.isBlank() ? null : nombre;
    }
}
