package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auditoria_documento", indexes = {
        @Index(name = "idx_aud_doc_fecha", columnList = "fecha_evento"),
        @Index(name = "idx_aud_doc_documento_fecha", columnList = "documento_id, fecha_evento"),
        @Index(name = "idx_aud_doc_expediente_fecha", columnList = "expediente_id, fecha_evento"),
        @Index(name = "idx_aud_doc_cliente_fecha", columnList = "cliente_id, fecha_evento"),
        @Index(name = "idx_aud_doc_usuario_fecha", columnList = "usuario_id, fecha_evento"),
        @Index(name = "idx_aud_doc_accion_resultado_fecha", columnList = "accion, resultado, fecha_evento"),
        @Index(name = "idx_aud_evento_recurso_fecha", columnList = "recurso_tipo, recurso_id, fecha_evento")
})
public class AuditoriaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "fecha_evento", nullable = false, updatable = false)
    private LocalDateTime fechaEvento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60, updatable = false)
    private AccionAuditoriaDocumento accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ResultadoAuditoriaDocumento resultado;

    @Column(name = "recurso_tipo", length = 40, updatable = false)
    private String recursoTipo;

    @Column(name = "recurso_id", updatable = false)
    private Long recursoId;

    @Column(name = "recurso_nombre", length = 200, updatable = false)
    private String recursoNombre;

    @Column(name = "documento_id", updatable = false)
    private Long documentoId;

    @Column(name = "documento_nombre", length = 200, updatable = false)
    private String documentoNombre;

    @Column(name = "documento_tipo", length = 100, updatable = false)
    private String documentoTipo;

    @Column(name = "expediente_id", updatable = false)
    private Long expedienteId;

    @Column(name = "solicitud_id", updatable = false)
    private Long solicitudId;

    @Column(name = "cliente_id", updatable = false)
    private Long clienteId;

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "usuario_email", length = 150, updatable = false)
    private String usuarioEmail;

    @Column(name = "usuario_rol", length = 20, updatable = false)
    private String usuarioRol;

    @Column(name = "direccion_ip", length = 64, updatable = false)
    private String direccionIp;

    @Column(name = "agente_usuario", length = 500, updatable = false)
    private String agenteUsuario;

    @Column(name = "metodo_http", length = 10, updatable = false)
    private String metodoHttp;

    @Column(length = 300, updatable = false)
    private String ruta;

    @Column(length = 1000, updatable = false)
    private String detalle;
}
