package com.example.gestor_documental.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalFileServiceTest {

    @TempDir
    Path tempDir;

    private final TransactionalFileService service = new TransactionalFileService();

    @BeforeEach
    void iniciarTransaccion() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void conservaOriginalesYEliminaNuevoCuandoLaTransaccionRevierte() throws Exception {
        Path original = tempDir.resolve("original.pdf");
        Path nuevo = tempDir.resolve("nuevo.pdf");
        Files.writeString(original, "original");

        service.escribirNuevoConLimpiezaEnRollback(nuevo, "nuevo".getBytes());
        service.eliminarTrasCommit(List.of(original));

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(original).exists();
        assertThat(Files.readString(original)).isEqualTo("original");
        assertThat(nuevo).doesNotExist();
    }

    @Test
    void eliminaOriginalesSoloDespuesDelCommit() throws Exception {
        Path original = tempDir.resolve("original.pdf");
        Path nuevo = tempDir.resolve("nuevo.pdf");
        Files.writeString(original, "original");

        service.escribirNuevoConLimpiezaEnRollback(nuevo, "nuevo".getBytes());
        service.eliminarTrasCommit(List.of(original));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        completar(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(original).doesNotExist();
        assertThat(nuevo).exists();
        assertThat(Files.readString(nuevo)).isEqualTo("nuevo");
    }

    private void completar(int estado) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(estado);
        }
    }
}
