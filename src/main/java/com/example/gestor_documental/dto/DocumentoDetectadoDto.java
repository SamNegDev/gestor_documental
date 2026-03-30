
package com.example.gestor_documental.dto;

import com.example.gestor_documental.enums.TipoDocumento;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DocumentoDetectadoDto {
    private TipoDocumento tipoDocumento;
    private List<Integer> paginas;
}
