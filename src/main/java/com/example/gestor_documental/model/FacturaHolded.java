package com.example.gestor_documental.model;
import com.example.gestor_documental.enums.EstadoFacturaHolded;
import com.example.gestor_documental.enums.ModalidadFacturacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "factura_holded", indexes = {@Index(name = "idx_factura_holded_cliente_fecha", columnList = "cliente_id, fecha_emision"), @Index(name = "idx_factura_holded_contacto", columnList = "holded_contact_id")})
public class FacturaHolded {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(name="holded_invoice_id",nullable=false,unique=true,length=100) private String holdedInvoiceId;
 @Column(name="holded_contact_id",length=100) private String holdedContactId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cliente_id") private Cliente cliente;
 @Column(length=80) private String numero; @Column(name="contacto_nombre",length=180) private String contactoNombre; @Column(name="contacto_nif",length=30) private String contactoNif;
 @Column(name="fecha_emision") private LocalDate fechaEmision; @Column(name="fecha_vencimiento") private LocalDate fechaVencimiento;
 @Column(precision=14,scale=2) private BigDecimal total; @Column(name="importe_pagado",precision=14,scale=2) private BigDecimal importePagado; @Column(length=3) private String moneda;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoFacturaHolded estado;
@Column(name="actualizada_holded") private LocalDateTime actualizadaHolded; @Column(name="sincronizada_en",nullable=false) private LocalDateTime sincronizadaEn;
 @Enumerated(EnumType.STRING) @Column(name="modalidad_facturacion",length=30) private ModalidadFacturacion modalidadFacturacion;
 @Column(name="periodo_desde") private LocalDate periodoDesde; @Column(name="periodo_hasta") private LocalDate periodoHasta;
 @Column(name="archivo_factura",length=180) private String archivoFactura; @Column(name="archivo_factura_original",length=255) private String archivoFacturaOriginal;
 @Column(name="archivo_factura_content_type",length=100) private String archivoFacturaContentType; @Column(name="archivo_factura_tamano") private Long archivoFacturaTamano;
}
