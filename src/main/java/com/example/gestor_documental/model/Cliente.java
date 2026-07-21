package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.PreferenciaCanalCliente;
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

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 250, unique = true)
    private String email;

    @Column(length = 200)
    private String direccion;

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


    public Cliente(String nif, String nombre, String email) {
        this.nif = nif;
        this.nombre = nombre;
        this.email = email;

    }

}
