package com.example.gestor_documental.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InteresadosFormDto {

    private InteresadoFormDto interesado1 = new InteresadoFormDto();
    private InteresadoFormDto interesado2 = new InteresadoFormDto();
}
