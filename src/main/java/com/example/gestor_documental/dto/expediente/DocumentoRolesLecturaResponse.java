package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.model.DocumentoRolesLectura;
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
public class DocumentoRolesLecturaResponse {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final double CONFIANZA_MINIMA_APLICACION = 0.78;

    private Long id;
    private Long documentoId;
    private String tipoDocumentoDetectado;
    private String fechaDocumento;
    private String matricula;
    private String bastidor;
    private String valorDeclarado;
    private String vendedorIdentificador;
    private String vendedorNombre;
    private String vendedorDireccion;
    private Long vendedorInteresadoId;
    private String vendedorInteresadoNombre;
    private String compradorIdentificador;
    private String compradorNombre;
    private String compradorDireccion;
    private Long compradorInteresadoId;
    private String compradorInteresadoNombre;
    private Double confianzaGlobal;
    private boolean requiereRevision;
    private boolean aplicable;
    private String motivoAplicacion;
    private boolean aplicadoExpediente;
    private String fechaAplicacion;
    private String mensaje;
    private String modelo;
    private String fechaLectura;

    public static DocumentoRolesLecturaResponse from(DocumentoRolesLectura lectura) {
        if (lectura == null) {
            return null;
        }
        return DocumentoRolesLecturaResponse.builder()
                .id(lectura.getId())
                .documentoId(lectura.getDocumento() != null ? lectura.getDocumento().getId() : null)
                .tipoDocumentoDetectado(lectura.getTipoDocumentoDetectado() != null ? lectura.getTipoDocumentoDetectado().name() : null)
                .fechaDocumento(lectura.getFechaDocumento())
                .matricula(lectura.getMatricula())
                .bastidor(lectura.getBastidor())
                .valorDeclarado(lectura.getValorDeclarado())
                .vendedorIdentificador(lectura.getVendedorIdentificador())
                .vendedorNombre(lectura.getVendedorNombre())
                .vendedorDireccion(lectura.getVendedorDireccion())
                .vendedorInteresadoId(lectura.getVendedorInteresado() != null ? lectura.getVendedorInteresado().getId() : null)
                .vendedorInteresadoNombre(lectura.getVendedorInteresado() != null ? lectura.getVendedorInteresado().getNombre() : null)
                .compradorIdentificador(lectura.getCompradorIdentificador())
                .compradorNombre(lectura.getCompradorNombre())
                .compradorDireccion(lectura.getCompradorDireccion())
                .compradorInteresadoId(lectura.getCompradorInteresado() != null ? lectura.getCompradorInteresado().getId() : null)
                .compradorInteresadoNombre(lectura.getCompradorInteresado() != null ? lectura.getCompradorInteresado().getNombre() : null)
                .confianzaGlobal(lectura.getConfianzaGlobal())
                .requiereRevision(lectura.isRequiereRevision())
                .aplicable(esAplicable(lectura))
                .motivoAplicacion(motivoAplicacion(lectura))
                .aplicadoExpediente(lectura.isAplicadoExpediente())
                .fechaAplicacion(lectura.getFechaAplicacion() != null ? FORMATTER.format(lectura.getFechaAplicacion()) : null)
                .mensaje(lectura.getMensaje())
                .modelo(lectura.getModelo())
                .fechaLectura(lectura.getFechaLectura() != null ? FORMATTER.format(lectura.getFechaLectura()) : null)
                .build();
    }

    private static boolean esAplicable(DocumentoRolesLectura lectura) {
        return motivoNoAplicable(lectura) == null;
    }

    private static String motivoAplicacion(DocumentoRolesLectura lectura) {
        String motivo = motivoNoAplicable(lectura);
        return motivo != null ? motivo : "Datos suficientes para aplicar al expediente.";
    }

    private static String motivoNoAplicable(DocumentoRolesLectura lectura) {
        if (lectura.isAplicadoExpediente()) {
            return "Datos ya aplicados al expediente.";
        }
        if (lectura.isRequiereRevision()) {
            return "La lectura requiere revision.";
        }
        if (lectura.getConfianzaGlobal() == null || lectura.getConfianzaGlobal() < CONFIANZA_MINIMA_APLICACION) {
            return "Confianza insuficiente para aplicar automaticamente.";
        }
        if (enBlanco(lectura.getVendedorIdentificador()) || enBlanco(lectura.getVendedorNombre())) {
            return "Faltan datos suficientes del vendedor.";
        }
        if (enBlanco(lectura.getCompradorIdentificador()) || enBlanco(lectura.getCompradorNombre())) {
            return "Faltan datos suficientes del comprador.";
        }
        if (lectura.getVendedorIdentificador().equalsIgnoreCase(lectura.getCompradorIdentificador())) {
            return "Comprador y vendedor tienen el mismo identificador.";
        }
        return null;
    }

    private static boolean enBlanco(String value) {
        return value == null || value.isBlank();
    }
}
