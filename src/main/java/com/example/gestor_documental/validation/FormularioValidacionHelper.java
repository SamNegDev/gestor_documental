package com.example.gestor_documental.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

@Component
@RequiredArgsConstructor
public class FormularioValidacionHelper {

    private final DniNieValidator dniNieValidator;

    public void validarDniOpcional(BindingResult result,
                                   String campo,
                                   String valor,
                                   String mensaje) {

        if (!result.hasFieldErrors(campo)
                && valor != null
                && !valor.isBlank()
                && !dniNieValidator.esValido(valor)) {

            result.rejectValue(campo, "error.dni", mensaje);
        }
    }
}