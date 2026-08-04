package com.example.gestor_documental.dto.auditoria;

public record AuditoriaDocumentoContext(
        Long documentoId,
        String documentoNombre,
        String documentoTipo,
        Long expedienteId,
        Long solicitudId,
        Long clienteId,
        Long usuarioId,
        String usuarioEmail,
        String usuarioRol,
        String direccionIp,
        String agenteUsuario,
        String recursoTipo,
        Long recursoId,
        String recursoNombre,
        String metodoHttp,
        String ruta
) {
    public AuditoriaDocumentoContext(
            Long documentoId,
            String documentoNombre,
            String documentoTipo,
            Long expedienteId,
            Long solicitudId,
            Long clienteId,
            Long usuarioId,
            String usuarioEmail,
            String usuarioRol,
            String direccionIp,
            String agenteUsuario
    ) {
        this(documentoId, documentoNombre, documentoTipo, expedienteId, solicitudId, clienteId,
                usuarioId, usuarioEmail, usuarioRol, direccionIp, agenteUsuario,
                documentoId != null ? "DOCUMENTO" : null,
                documentoId,
                documentoNombre,
                null,
                null);
    }
}
