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
public class MensajeExpedienteResponse {
    private Long id;
    private String autor;
    private String rolAutor;
    private String fechaCreacion;
    private String contenido;
    private boolean noLeidoParaUsuario;
}
