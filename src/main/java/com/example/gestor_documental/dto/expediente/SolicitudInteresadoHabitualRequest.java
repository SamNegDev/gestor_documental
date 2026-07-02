package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolInteresado;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudInteresadoHabitualRequest {

    private Long interesadoId;
    private RolInteresado rol;
}
