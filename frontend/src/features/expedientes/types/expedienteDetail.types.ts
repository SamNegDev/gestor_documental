export type ExpedienteStatus =
  | "EN_TRAMITE"
  | "INCIDENCIA"
  | "FINALIZADO"
  | "RECHAZADO"
  | "ENVIADO_DGT"
  | "REVISANDO_INCIDENCIAS"
  | "PENDIENTE_DOCUMENTACION"
  | "SOLICITADA_INFORMACION_ADICIONAL"
  | "INFORMACION_ADICIONAL_RECIBIDA";

export type DocumentoEstado = "SUBIDO" | "PENDIENTE" | "POSTERIOR" | "INCIDENCIA";
export type HitoEstado = "COMPLETADO" | "ACTUAL" | "BLOQUEADO" | "PENDIENTE";
export type RequisitoDocumentalEstado = "REQUERIDO" | "APORTADO" | "OMITIDO" | "POSTERIOR";

export interface HitoAccion {
  tipo: "COMPLETAR_HITO" | "FINALIZAR" | "ABRIR_INCIDENCIA" | string;
  label: string;
  codigoHito?: string | null;
  tono?: "primary" | "success" | "warning" | string | null;
}

export interface ClienteResumen {
  id: number;
  nombre: string;
  nif?: string;
  email?: string;
  telefono?: string;
  logoPrincipalUrl?: string | null;
  logoCompactoUrl?: string | null;
}

export interface UsuarioResumen {
  id: number;
  nombreCompleto: string;
  email?: string;
  rol?: string;
}

export interface InteresadoExpediente {
  id: number;
  nombre: string;
  rol?: string;
  dni?: string;
  telefono?: string;
  direccion?: string;
}

export interface InteresadoSearchResult {
  id: number;
  nombre: string;
  dni?: string | null;
  telefono?: string | null;
  direccion?: string | null;
  tipoPersona?: string | null;
}

export interface DocumentoExpediente {
  id: number | null;
  nombre: string;
  nombreOriginal?: string | null;
  tipo: string;
  descripcion?: string | null;
  fechaSubida?: string | null;
  subidoPor?: string | null;
  operacionId?: number | null;
  operacionLabel?: string | null;
  estado: DocumentoEstado;
  subido: boolean;
  requeridoAhora: boolean;
}

export interface RequisitoDocumental {
  id: number;
  tipoDocumento: string;
  descripcion: string;
  estado: RequisitoDocumentalEstado;
  origen: string;
  interesadoId?: number | null;
  interesadoNombre?: string | null;
  rolInteresado?: string | null;
  documentoId?: number | null;
  documentoNombre?: string | null;
  motivoOmision?: string | null;
  fechaCreacion?: string | null;
  fechaResolucion?: string | null;
}

export interface HitoExpediente {
  id: string;
  titulo: string;
  descripcion: string;
  estado: HitoEstado;
  tipo: string;
  fecha?: string | null;
  usuario?: string | null;
  nota?: string | null;
  accion?: "COMPLETAR_HITO" | "FINALIZAR" | string | null;
  accionLabel?: string | null;
  acciones?: HitoAccion[];
  completado: boolean;
  bloqueado: boolean;
}

export interface IncidenciaExpediente {
  id: number;
  tipo?: string;
  observaciones?: string;
  fechaCreacion?: string;
  resuelta: boolean;
  fechaResolucion?: string | null;
  creadoPor?: string | null;
  resueltoPor?: string | null;
  pendienteRevisionCliente?: boolean;
  documentosRevision?: DocumentoExpediente[];
}

export interface TipoIncidencia {
  id: number;
  nombre: string;
  descripcion?: string | null;
}

export interface TipoTramiteResumen {
  id: number;
  nombre: string;
  descripcion?: string | null;
}

export interface ExpedienteEditCatalogs {
  clientes: ClienteResumen[];
  tiposTramite: TipoTramiteResumen[];
}

export interface ExpedienteEditInput {
  clienteId: number;
  tipoTramiteId: number;
  matricula?: string | null;
  observaciones?: string | null;
  interesados: Array<{
    nombre?: string | null;
    dni?: string | null;
    telefono?: string | null;
    direccion?: string | null;
    rol?: string | null;
  }>;
}

export interface OperacionExpediente {
  id: number;
  tipo: string;
  label: string;
  estado: "PENDIENTE" | "EN_CURSO" | "FINALIZADA" | "BLOQUEADA" | string;
  orden: number;
  descripcion?: string | null;
  bloqueada: boolean;
  motivoBloqueo?: string | null;
  hitos: HitoExpediente[];
}

export interface HistorialExpediente {
  id: number;
  accion: string;
  descripcion?: string;
  fechaCambio?: string;
  usuario?: string | null;
}

export interface MensajeExpediente {
  id: number;
  autor?: string | null;
  rolAutor?: string | null;
  fechaCreacion?: string;
  contenido: string;
}

export interface PlantillaDocumento {
  codigo: string;
  nombre: string;
  descripcion: string;
  tipoDocumento: string;
}

export interface PlantillaInteresado {
  interesadoId: number;
  nombre: string;
  dni: string;
  rol?: string | null;
  direccion?: string | null;
}

export interface PlantillaCampo {
  codigo: string;
  etiqueta: string;
  valor: string;
  requerido: boolean;
  tipo: "INTERESADO" | "TEXT" | "TEXTAREA" | "DATE" | "NUMBER" | string;
  ayuda?: string | null;
}

export interface PlantillasExpediente {
  referencia: string;
  matricula: string;
  tipoTramite: string;
  cliente: string;
  plantillas: PlantillaDocumento[];
  interesados: PlantillaInteresado[];
}

export interface PlantillaPreview {
  codigo: string;
  nombre: string;
  nombreArchivo: string;
  tipoDocumento: string;
  campos: PlantillaCampo[];
  avisos: string[];
}

export interface DocumentoGenerado {
  documentoId: number;
  nombreArchivo: string;
  tipoDocumento: string;
}

export interface ExpedienteDetail {
  id: number;
  referencia: string;
  matricula?: string | null;
  tipoTramite?: string | null;
  tipoTramiteDescripcion?: string | null;
  estado: ExpedienteStatus;
  faseActual?: string | null;
  fechaInicio?: string | null;
  fechaUltimaModificacion?: string | null;
  observaciones?: string | null;
  solicitudId?: number | null;
  siguientePaso?: HitoExpediente | null;
  cliente?: ClienteResumen | null;
  creadoPor?: UsuarioResumen | null;
  modificadoPor?: UsuarioResumen | null;
  interesados: InteresadoExpediente[];
  documentos: DocumentoExpediente[];
  requisitosDocumentales: RequisitoDocumental[];
  operaciones: OperacionExpediente[];
  hitos: HitoExpediente[];
  incidencias: IncidenciaExpediente[];
  historial: HistorialExpediente[];
  mensajes: MensajeExpediente[];
}
