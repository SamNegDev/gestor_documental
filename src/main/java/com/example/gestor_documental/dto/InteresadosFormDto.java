package com.example.gestor_documental.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;

@Getter
@Setter
@NoArgsConstructor
public class InteresadosFormDto {

    @Valid
    private InteresadoFormDto interesado1 = new InteresadoFormDto();
    @Valid
    private InteresadoFormDto interesado2 = new InteresadoFormDto();
}
