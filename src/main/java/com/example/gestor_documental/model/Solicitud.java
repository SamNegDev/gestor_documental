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
@Table(name = "solicitud", indexes = {
        @Index(name = "idx_solicitud_cliente_estado_fecha", columnList = "cliente_id, estado_solicitud, fecha_ultima_modificacion, fecha_creacion"),
        @Index(name = "idx_solicitud_estado_fecha", columnList = "estado_solicitud, fecha_ultima_modificacion, fecha_creacion"),
        @Index(name = "idx_solicitud_cliente_fecha", columnList = "cliente_id, fecha_ultima_modificacion, fecha_creacion"),
        @Index(name = "idx_solicitud_matricula", columnList = "matricula"),
        @Index(name = "idx_solicitud_tipo_tramite", columnList = "tipo_tramite_id")
})
public class Solicitud {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaUltimaModificacion;

    @Column(length = 10)
    private String matricula;

    @Column(length = 200)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private EstadoSolicitud estadoSolicitud;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modificado_por_usuario_id")
    private Usuario modificadoPor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_tramite_id")
    private TipoTramite tipoTramite;

    @OneToOne(mappedBy = "solicitud")
    private Expediente expediente;

    @OneToMany(mappedBy = "solicitud", fetch = FetchType.LAZY)
    private List<Documento> documentos = new ArrayList<>();
    //Añadimos los campos de solicitudes simplemente para persistir los formularios, el registro definitivo se hara al convertir la solicitud en expediente.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RolInteresado interesado1Rol;

    private String interesado1Nombre;
    private String interesado1Dni;
    private String interesado1Telefono;
    private String interesado1Direccion;
    private String interesado1TipoVia;
    private String interesado1NombreVia;
    private String interesado1CodigoPostal;
    private String interesado1Municipio;
    private String interesado1Provincia;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RolInteresado interesado2Rol;

    private String interesado2Nombre;
    private String interesado2Dni;
    private String interesado2Telefono;
    private String interesado2Direccion;
    private String interesado2TipoVia;
    private String interesado2NombreVia;
    private String interesado2CodigoPostal;
    private String interesado2Municipio;
    private String interesado2Provincia;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RolInteresado interesado3Rol;

    private String interesado3Nombre;
    private String interesado3Dni;
    private String interesado3Telefono;
    private String interesado3Direccion;
    private String interesado3TipoVia;
    private String interesado3NombreVia;
    private String interesado3CodigoPostal;
    private String interesado3Municipio;
    private String interesado3Provincia;

}
