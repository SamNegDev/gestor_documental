package com.example.gestor_documental.dto.registro;

import com.example.gestor_documental.dto.expediente.DocumentoExpedienteResponse;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InteresadoRegistroResponse {
    private Long id;
    private String dni;
    private String nombre;
    private String telefono;
    private String direccion;
    private String tipoVia;
    private String nombreVia;
    private String codigoPostal;
    private String municipio;
    private String provincia;
    private String tipoPersona;
    private boolean habitual;
    private boolean representanteLegal;
    private long totalTramites;
    private String ultimaActividad;
    private List<DocumentoExpedienteResponse> documentos;
    private List<TramiteRegistroResponse> tramites;
}
