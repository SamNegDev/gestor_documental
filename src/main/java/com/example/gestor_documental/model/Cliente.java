package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.PreferenciaCanalCliente;
import com.example.gestor_documental.enums.ModalidadFacturacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table (name="cliente")


public class Cliente {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY )
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String nif;

    @Column(name = "holded_contact_id", unique = true, length = 100)
    private String holdedContactId;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_facturacion_predeterminada", nullable = false, length = 30, columnDefinition = "varchar(30) default 'POR_EXPEDIENTE'")
    private ModalidadFacturacion modalidadFacturacionPredeterminada = ModalidadFacturacion.POR_EXPEDIENTE;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 250, unique = true)
    private String email;

    @Column(name = "email_notificaciones", length = 250)
    private String emailNotificaciones;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cliente_email_notificacion_copia", joinColumns = @JoinColumn(name = "cliente_id"))
    @Column(name = "email", nullable = false, length = 250)
    @OrderColumn(name = "orden")
    private List<String> emailsCopiaNotificaciones = new ArrayList<>();

    @Column(length = 200)
    private String direccion;

    @Column(length = 30)
    private String tipoVia;

    @Column(length = 120)
    private String nombreVia;

    @Column(length = 20)
    private String numeroVia;

    @Column(length = 20)
    private String bloque;

    @Column(length = 20)
    private String portal;

    @Column(length = 20)
    private String escalera;

    @Column(length = 20)
    private String piso;

    @Column(length = 20)
    private String puerta;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 80)
    private String municipio;

    @Column(length = 120)
    private String localidad;

    @Column(length = 80)
    private String provincia;

    @Column(length = 20)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'AMBOS'")
    private PreferenciaCanalCliente preferenciaCanal = PreferenciaCanalCliente.AMBOS;

    @Column(name = "aviso_incidencias_activo", nullable = false, columnDefinition = "boolean default true")
    private boolean avisoIncidenciasActivo = true;

    @Column(name = "hora_aviso_incidencias", nullable = false, columnDefinition = "time default '17:00:00'")
    private LocalTime horaAvisoIncidencias = LocalTime.of(17, 0);

    @Column(name = "ultimo_aviso_incidencias")
    private LocalDate ultimoAvisoIncidencias;

    @Column(name = "aviso_finalizados_activo", nullable = false, columnDefinition = "boolean default true")
    private boolean avisoFinalizadosActivo = true;

    @Column(name = "hora_aviso_finalizados", nullable = false, columnDefinition = "time default '17:00:00'")
    private LocalTime horaAvisoFinalizados = LocalTime.of(17, 0);

    @Column(name = "ultimo_aviso_finalizados")
    private LocalDate ultimoAvisoFinalizados;

    @Column(name = "logo_principal_path", length = 500)
    private String logoPrincipalPath;

    @Column(name = "logo_compacto_path", length = 500)
    private String logoCompactoPath;

    @OneToMany(mappedBy = "cliente", fetch = FetchType.LAZY)
    private List<Usuario> usuarios = new ArrayList<>();


    public String emailNotificacionesEfectivo() {
        return emailNotificaciones != null && !emailNotificaciones.isBlank() ? emailNotificaciones : email;
    }


    public Cliente(String nif, String nombre, String email) {
        this.nif = nif;
        this.nombre = nombre;
        this.email = email;

    }

}
