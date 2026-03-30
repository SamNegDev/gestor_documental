package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.PdfSplitService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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
}