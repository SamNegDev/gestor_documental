import type {
  ClienteResumen,
  DocumentoExpediente,
  HistorialExpediente,
  IncidenciaExpediente,
  MensajeExpediente,
  TipoTramiteResumen,
  UsuarioResumen,
} from "../expedientes/types/expedienteDetail.types";

export interface ListCatalogs {
  estados: string[];
  tiposTramite: TipoTramiteResumen[];
  clientes: ClienteResumen[];
}

export interface ExpedienteListItem {
  id: number;
  matricula?: string | null;
  tipoTramite?: string | null;
  estado?: string | null;
  fechaCreacion?: string | null;
  fechaUltimaModificacion?: string | null;
  cliente?: ClienteResumen | null;
  modificadoPor?: UsuarioResumen | null;
}

export interface SolicitudListItem {
  id: number;
  matricula?: string | null;
  tipoTramite?: string | null;
  estado?: string | null;
  fechaCreacion?: string | null;
  fechaUltimaModificacion?: string | null;
  cliente?: ClienteResumen | null;
  modificadoPor?: UsuarioResumen | null;
  expedienteId?: number | null;
}

export interface InteresadoSolicitud {
  nombre?: string | null;
  apellidos?: string | null;
  rol?: string | null;
  dni?: string | null;
  telefono?: string | null;
  direccion?: string | null;
}

export interface SolicitudDetail {
  id: number;
  matricula?: string | null;
  tipoTramite?: string | null;
  estado?: string | null;
  fechaCreacion?: string | null;
  fechaUltimaModificacion?: string | null;
  observaciones?: string | null;
  expedienteId?: number | null;
  cliente?: ClienteResumen | null;
  creadoPor?: UsuarioResumen | null;
  modificadoPor?: UsuarioResumen | null;
  interesados: InteresadoSolicitud[];
  documentos: DocumentoExpediente[];
  incidencias: IncidenciaExpediente[];
  historial: HistorialExpediente[];
  mensajes: MensajeExpediente[];
}

export interface SolicitudUpsertInput {
  tipoTramiteId: number;
  matricula: string;
  observaciones?: string | null;
  interesado1Rol?: string | null;
  interesado1Nombre?: string | null;
  interesado1Apellidos?: string | null;
  interesado1Dni?: string | null;
  interesado1Telefono?: string | null;
  interesado1Direccion?: string | null;
  interesado2Rol?: string | null;
  interesado2Nombre?: string | null;
  interesado2Apellidos?: string | null;
  interesado2Dni?: string | null;
  interesado2Telefono?: string | null;
  interesado2Direccion?: string | null;
}

export interface DashboardMetrics {
  totalExpedientes: number;
  enTramite: number;
  finalizados: number;
  incidenciasExpedientes: number;
  totalSolicitudes: number;
  pendienteRevision: number;
  convertidas: number;
  incidenciasSolicitudes: number;
  totalIncidencias: number;
}

export interface DashboardData {
  scope: "ADMIN" | "CLIENTE";
  metrics: DashboardMetrics;
  ultimosExpedientes: ExpedienteListItem[];
  ultimasSolicitudes: SolicitudListItem[];
}

export interface ListFilters {
  estado?: string;
  tipoTramiteId?: string;
  clienteId?: string;
  matricula?: string;
  periodo?: string;
}
