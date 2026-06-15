package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoExpediente;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor

@Entity
@Table(name = "expediente", indexes = {
        @Index(name = "idx_expediente_cliente_estado_fecha", columnList = "cliente_id, estado_expediente, fecha_ultima_modificacion, fecha_creacion"),
        @Index(name = "idx_expediente_estado_fecha", columnList = "estado_expediente, fecha_ultima_modificacion, fecha_creacion"),
        @Index(name = "idx_expediente_cliente_fecha", columnList = "cliente_id, fecha_ultima_modificacion, fecha_creacion"),
        @Index(name = "idx_expediente_matricula", columnList = "matricula"),
        @Index(name = "idx_expediente_tipo_tramite", columnList = "tipo_tramite_id")
})

public class Expediente {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaUltimaModificacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private EstadoExpediente estadoExpediente;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private EstadoExpediente estadoPrevioPausa;

    @Column (length = 10)
    @Size(max=10, message = "La matricula debe tener 10 caracteres")
    private String matricula;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column (length = 200)
    private String observaciones;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_tramite_id")
    private TipoTramite tipoTramite;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modificado_por_usuario_id")
    private Usuario modificadoPor;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @OneToMany(mappedBy = "expediente", fetch = FetchType.LAZY)
    private List<Documento> documentos = new ArrayList<>();

    //pendiente crear entidad incidencia
    @OneToMany(mappedBy = "expediente", fetch = FetchType.LAZY)
    private List<Incidencia> incidencias = new ArrayList<>();

    //pendiente crear tabla intermedia expediente-interesado
    @OneToMany(mappedBy = "expediente", fetch = FetchType.LAZY)
    private List<ExpedienteInteresado> interesados = new ArrayList<>();

    /*En este caso se crean dos expedientes, porque es posible que un admin cree el expediente sin solicitud o puede
    convertir una solicitud */

    public Expediente(TipoTramite tipoTramite, Cliente cliente, Usuario creadoPor) {
        this.tipoTramite = tipoTramite;
        this.cliente = cliente;
        this.creadoPor = creadoPor;
        this.estadoExpediente = EstadoExpediente.EN_TRAMITE;
    }

    public Expediente(TipoTramite tipoTramite, Cliente cliente, Usuario creadoPor, Solicitud solicitud) {
        this.tipoTramite = tipoTramite;
        this.cliente = cliente;
        this.creadoPor = creadoPor;
        this.solicitud = solicitud;
        this.estadoExpediente = EstadoExpediente.EN_TRAMITE;
    }
}
