package com.example.gestor_documental.event;

public record DocumentoLecturaIaSolicitadaEvent(Long solicitudId, Long usuarioId, String origen) {
}
