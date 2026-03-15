package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor

@Entity
@Table(name= "solicitud")
public class Solicitud {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(length = 10)
    private String matricula;

    @Column(length = 200)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private EstadoSolicitud estadoSolicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_tramite_id")
    private TipoTramite tipoTramite;

    @Column(length = 120)
    private String nombreInteresado;

    @Column(length = 20)
    private String dniInteresado;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RolInteresado rol;

    @OneToMany(mappedBy = "solicitud", fetch = FetchType.LAZY)
    private List<Documento> documentos = new ArrayList<>();

    public Solicitud(Cliente cliente, Usuario usuario, TipoTramite tipoTramite) {
        this.cliente = cliente;
        this.usuario = usuario;
        this.tipoTramite = tipoTramite;
        this.estadoSolicitud = EstadoSolicitud.PENDIENTE_REVISION;
    }
}
