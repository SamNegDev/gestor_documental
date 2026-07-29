export type EstadoFactura = "PENDIENTE" | "PARCIALMENTE_PAGADA" | "PAGADA" | "ANULADA";
export type EstadoComprobante = "PENDIENTE_VERIFICACION" | "VERIFICADO" | "DESCARTADO";
export type ComprobantePago = { id:number; nombreOriginal:string; contentType:string; tamano:number; estado:EstadoComprobante; observaciones?:string; creadoEn:string; revisadoEn?:string };
export type Factura = { id:number; numero?:string; contactoNombre?:string; contactoNif?:string; fechaEmision?:string; fechaVencimiento?:string; total:number; importePagado:number; moneda:string; estado:EstadoFactura; sincronizadaEn:string; lineasPendientesRevision:number; detalleLineasPendientes?:string; comprobantes:ComprobantePago[] };
export type FacturasPage = { contenido:Factura[]; pagina:number; tamanio:number; totalElementos:number; totalPaginas:number };

export type ModalidadFacturacion = "POR_EXPEDIENTE" | "LOTE_QUINCENAL" | "LOTE_MENSUAL";
export type LineaFacturaDetectada = { documento?:string; matricula?:string; bastidor?:string; compradorIdentificador?:string; compradorNombre?:string; expedienteId?:number; confianza:number; estado:"COINCIDENCIA_SEGURA"|"REVISION"; motivo?:string; confirmacionManualPermitida:boolean };
export type AnalisisFactura = { archivo:string; numeroFactura?:string; fechaFactura?:string; facturaId?:number; estado:string; lineas:LineaFacturaDetectada[] };
export type FacturaVinculacion={id:number;expedienteId:number;matricula?:string;cliente?:string;estadoExpediente?:string;estadoVinculacion:string;matriculaDetectada?:string;bastidorDetectado?:string;compradorIdentificadorDetectado?:string;confianza:number;motivoRevision?:string};
export type FacturaDetalle={factura:Factura;vinculaciones:FacturaVinculacion[]};