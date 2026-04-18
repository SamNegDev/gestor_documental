
package com.example.gestor_documental.service;

import java.util.List;

public interface PdfSplitService {
    byte[] extraerPaginas(byte[] pdfOriginal, List<Integer> paginas);
    byte[] eliminarPaginas(byte[] pdfOriginal, List<Integer> paginas);
    List<Integer> parseRangoPaginas(String rango, int totalPaginas);
}
