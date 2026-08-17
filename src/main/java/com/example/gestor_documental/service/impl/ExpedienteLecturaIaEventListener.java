package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.event.ExpedienteLecturaIaSolicitadaEvent;
import com.example.gestor_documental.service.ExpedienteLecturaIaJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ExpedienteLecturaIaEventListener {
    private final ExpedienteLecturaIaJobService jobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void encolar(ExpedienteLecturaIaSolicitadaEvent event) {
        jobService.crearAutomatico(event.expedienteId(), event.usuarioId(), event.origen());
    }
}
