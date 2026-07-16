package com.example.gestor_documental.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionalFileService {

    private static final Logger log = LoggerFactory.getLogger(TransactionalFileService.class);

    public void escribirNuevoConLimpiezaEnRollback(Path ruta, byte[] contenido) throws IOException {
        exigirTransaccionActiva();
        try {
            Files.write(ruta, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        eliminarSinPropagar(ruta, "archivo creado por una transaccion revertida");
                    }
                }
            });
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(ruta);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public void eliminarTrasCommit(Collection<Path> rutas) {
        exigirTransaccionActiva();
        List<Path> rutasUnicas = rutas.stream()
                .filter(java.util.Objects::nonNull)
                .map(Path::normalize)
                .distinct()
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Path ruta : rutasUnicas) {
                    eliminarSinPropagar(ruta, "archivo confirmado como eliminado en base de datos");
                }
            }
        });
    }

    private void exigirTransaccionActiva() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("La operacion de archivo requiere una transaccion activa");
        }
    }

    private void eliminarSinPropagar(Path ruta, String contexto) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException exception) {
            log.error("No se pudo eliminar {}: {}", contexto, ruta, exception);
        }
    }
}
