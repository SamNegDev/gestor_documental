package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoIdentidadLecturaResponse {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Long id;
    private Long documentoId;
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
    private Double confianzaGlobal;
    private boolean requiereRevision;
    private boolean vinculadoAutomaticamente;
    private Long interesadoVinculadoId;
    private String interesadoVinculadoNombre;
    private String mensaje;
    private String modelo;
    private String fechaLectura;
    private int identidadesDetectadasTotal;
    private List<DocumentoIdentidadDetectadaResponse> identidadesDetectadas;

    public static DocumentoIdentidadLecturaResponse from(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return null;
        }
        var identidadesDetectadas = DocumentoIdentidadLecturaJson.extraer(lectura);
        return DocumentoIdentidadLecturaResponse.builder()
                .id(lectura.getId())
                .documentoId(lectura.getDocumento() != null ? lectura.getDocumento().getId() : null)
                .tipoDocumentoDetectado(lectura.getTipoDocumentoDetectado() != null ? lectura.getTipoDocumentoDetectado().name() : null)
                .identificador(lectura.getIdentificador())
                .nombre(lectura.getNombre())
                .apellido1(lectura.getApellido1())
                .apellido2(lectura.getApellido2())
                .razonSocial(lectura.getRazonSocial())
                .nombreCompleto(nombreCompleto(lectura))
                .fechaNacimiento(lectura.getFechaNacimiento())
                .fechaCaducidad(lectura.getFechaCaducidad())
                .direccionTexto(lectura.getDireccionTexto())
                .confianzaGlobal(lectura.getConfianzaGlobal())
                .requiereRevision(lectura.isRequiereRevision())
                .vinculadoAutomaticamente(lectura.isVinculadoAutomaticamente())
                .interesadoVinculadoId(lectura.getInteresadoVinculado() != null ? lectura.getInteresadoVinculado().getId() : null)
                .interesadoVinculadoNombre(lectura.getInteresadoVinculado() != null ? lectura.getInteresadoVinculado().getNombre() : null)
                .mensaje(mensajeNormalizado(lectura.getMensaje(), identidadesDetectadas.size()))
                .modelo(lectura.getModelo())
                .fechaLectura(lectura.getFechaLectura() != null ? FORMATTER.format(lectura.getFechaLectura()) : null)
                .identidadesDetectadasTotal(identidadesDetectadas.size())
                .identidadesDetectadas(identidadesDetectadas.stream()
                        .map(DocumentoIdentidadDetectadaResponse::from)
                        .toList())
                .build();
    }

    private static String nombreCompleto(DocumentoIdentidadLectura lectura) {
        if (lectura.getRazonSocial() != null && !lectura.getRazonSocial().isBlank()) {
            return lectura.getRazonSocial();
        }
        String nombre = Stream.of(lectura.getNombre(), lectura.getApellido1(), lectura.getApellido2())
                .filter(valor -> valor != null && !valor.isBlank())
                .reduce("", (actual, valor) -> actual.isBlank() ? valor.trim() : actual + " " + valor.trim());
        return nombre.isBlank() ? null : nombre;
    }

    private static String mensajeNormalizado(String mensaje, int identidadesDetectadas) {
        if (identidadesDetectadas > 1 && mensaje != null && mensaje.startsWith("Se detectaron ")) {
            return "Se detectaron " + identidadesDetectadas + " identidades en el documento; revisar asignacion si procede.";
        }
        return mensaje;
    }
}
