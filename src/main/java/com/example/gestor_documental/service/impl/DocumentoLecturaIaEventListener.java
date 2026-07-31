package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.event.DocumentoLecturaIaSolicitadaEvent;
import com.example.gestor_documental.service.SolicitudLecturaIaJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentoLecturaIaEventListener {
    private final SolicitudLecturaIaJobService jobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void encolar(DocumentoLecturaIaSolicitadaEvent event) {
        jobService.crearAutomatico(event.solicitudId(), event.usuarioId(), event.origen());
    }
}
