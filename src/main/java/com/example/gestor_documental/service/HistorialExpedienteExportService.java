package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.CategoriaHistorial;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

public interface HistorialExpedienteExportService {
    void exportarCsv(Long expedienteId, CategoriaHistorial categoria, LocalDate desde, LocalDate hasta,
                     boolean soloCliente, OutputStream outputStream) throws IOException;
}
