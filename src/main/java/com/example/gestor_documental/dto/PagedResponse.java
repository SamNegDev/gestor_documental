package com.example.gestor_documental.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PagedResponse<T> {
    private List<T> contenido;
    private int pagina;
    private int tamanio;
    private long totalElementos;
    private int totalPaginas;

    public static <T> PagedResponse<T> of(List<T> elementos, int pagina, int tamanio) {
        int size = Math.max(1, Math.min(tamanio, 100));
        int total = elementos.size();
        int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        int page = Math.max(0, Math.min(pagina, Math.max(totalPaginas - 1, 0)));
        int desde = Math.min(page * size, total);
        int hasta = Math.min(desde + size, total);
        return PagedResponse.<T>builder()
                .contenido(elementos.subList(desde, hasta))
                .pagina(page).tamanio(size).totalElementos(total).totalPaginas(totalPaginas)
                .build();
    }
}
