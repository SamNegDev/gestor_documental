package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.PdfSplitService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PdfSplitServiceImpl implements PdfSplitService {

    @Override
    public byte[] extraerPaginas(byte[] pdfOriginal, List<Integer> paginas) {
        try (PDDocument original = PDDocument.load(pdfOriginal);
             PDDocument nuevo = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            for (Integer index : paginas) {
                if (index != null && index >= 0 && index < original.getNumberOfPages()) {
                    nuevo.importPage(original.getPage(index));
                }
            }

            nuevo.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al dividir el PDF", e);
        }
    }

    @Override
    public byte[] eliminarPaginas(byte[] pdfOriginal, List<Integer> paginasAEliminar) {
        try (PDDocument original = PDDocument.load(pdfOriginal);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Ordenar de mayor a menor para que los índices no se desplacen al borrar
            List<Integer> ordenadasReverse = new ArrayList<>(paginasAEliminar);
            ordenadasReverse.sort(Collections.reverseOrder());

            for (Integer index : ordenadasReverse) {
                if (index != null && index >= 0 && index < original.getNumberOfPages()) {
                    original.removePage(index);
                }
            }

            original.save(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Error al eliminar páginas del PDF", e);
        }
    }

    @Override
    public List<Integer> parseRangoPaginas(String rango, int totalPaginas) {
        if (rango == null || rango.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> paginasUnicas = new TreeSet<>();
        String[] partes = rango.split(",");

        for (String parte : partes) {
            parte = parte.trim();
            if (parte.isEmpty()) continue;

            try {
                if (parte.contains("-")) {
                    String[] limites = parte.split("-");
                    if (limites.length == 2) {
                        int inicio = Integer.parseInt(limites[0].trim());
                        int fin = Integer.parseInt(limites[1].trim());

                        if (inicio <= fin) {
                            for (int i = inicio; i <= fin; i++) {
                                // 1-indexed for the user, 0-indexed for pdfbox
                                if (i >= 1 && i <= totalPaginas) {
                                    paginasUnicas.add(i - 1);
                                }
                            }
                        }
                    }
                } else {
                    int pagina = Integer.parseInt(parte);
                    if (pagina >= 1 && pagina <= totalPaginas) {
                        paginasUnicas.add(pagina - 1);
                    }
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid parts
            }
        }

        return new ArrayList<>(paginasUnicas);
    }
}