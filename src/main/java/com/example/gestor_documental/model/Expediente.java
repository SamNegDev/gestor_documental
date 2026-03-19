package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoExpediente;
import jakarta.persistence.*;
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
@Table(name="expediente")

public class Expediente {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private EstadoExpediente estadoExpediente;

    @Column (length = 10)
    private String matricula;

    @Column (length = 200)
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_tramite_id")
    private TipoTramite tipoTramite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario usuario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @OneToMany(mappedBy = "expediente", fetch = FetchType.EAGER)
    private List<Documento> documentos = new ArrayList<>();

    //pendiente crear entidad incidencia
    @OneToMany(mappedBy = "expediente", fetch = FetchType.LAZY)
    private List<Incidencia> incidencias = new ArrayList<>();

    //pendiente crear tabla intermedia expediente-interesado
    @OneToMany(mappedBy = "expediente", fetch = FetchType.LAZY)
    private List<ExpedienteInteresado> interesados = new ArrayList<>();

    /*En este caso se crean dos expedientes, porque es posible que un admin cree el expediente sin solicitud o puede
    convertir una solicitud */

    public Expediente(TipoTramite tipoTramite, Cliente cliente, Usuario usuario) {
        this.tipoTramite = tipoTramite;
        this.cliente = cliente;
        this.usuario = usuario;
        this.estadoExpediente = EstadoExpediente.EN_TRAMITE;
    }

    public Expediente(TipoTramite tipoTramite, Cliente cliente, Usuario usuario, Solicitud solicitud) {
        this.tipoTramite = tipoTramite;
        this.cliente = cliente;
        this.usuario = usuario;
        this.solicitud = solicitud;
        this.estadoExpediente = EstadoExpediente.EN_TRAMITE;
    }
}
