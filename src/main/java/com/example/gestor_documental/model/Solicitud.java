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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_tramite_id")
    private TipoTramite tipoTramite;

    @OneToOne(mappedBy = "solicitud")
    private Expediente expediente;

    @OneToMany(mappedBy = "solicitud", fetch = FetchType.LAZY)
    private List<Documento> documentos = new ArrayList<>();
    //Añadimos los campos de solicitudes simplemente para persistir los formularios, el registro definitivo se hara al convertir la solicitud en expediente.
    @Enumerated(EnumType.STRING)
    private RolInteresado interesado1Rol;

    private String interesado1Nombre;
    private String interesado1Apellidos;
    private String interesado1Dni;
    private String interesado1Telefono;
    private String interesado1Direccion;

    @Enumerated(EnumType.STRING)
    private RolInteresado interesado2Rol;

    private String interesado2Nombre;
    private String interesado2Apellidos;
    private String interesado2Dni;
    private String interesado2Telefono;
    private String interesado2Direccion;

}
