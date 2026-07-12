import type {
  ClienteResumen,
  DocumentoExpediente,
  HistorialExpediente,
  IncidenciaExpediente,
  InteresadoExpediente,
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
  incidenciasActivas?: string[];
  fechaCreacion?: string | null;
  fechaUltimaModificacion?: string | null;
  cliente?: ClienteResumen | null;
  modificadoPor?: UsuarioResumen | null;
  interesados: InteresadoExpediente[];
  faseActual?: string | null;
  siguientePasoTitulo?: string | null;
  siguientePasoDetalle?: string | null;
  siguienteAccion?: {
    tipo: string;
    label?: string | null;
    codigoHito?: string | null;
    tono?: string | null;
  } | null;
  justificantesFinalesDisponibles?: boolean;
  justificantesFinalesPendientes?: string[];
  documentacionHaciendaDisponible?: boolean;
}

export interface PagedResponse<T> {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
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
  interesados: InteresadoSolicitud[];
  situacionDocumental?: string | null;
}

export interface InteresadoSolicitud {
  nombre?: string | null;
  nombrePila?: string | null;
  apellido1?: string | null;
  apellido2?: string | null;
  razonSocial?: string | null;
  rol?: string | null;
  dni?: string | null;
  telefono?: string | null;
  direccion?: string | null;
  tipoVia?: string | null;
  nombreVia?: string | null;
  numeroVia?: string | null;
  bloque?: string | null;
  portal?: string | null;
  escalera?: string | null;
  piso?: string | null;
  puerta?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
  personaJuridica?: boolean;
  clienteHabitual?: boolean;
  documentoIdentidadAportado?: boolean;
  documentoIdentidadOrigen?: string | null;
  requiereRepresentanteLegal?: boolean;
  representanteLegalAportado?: boolean;
  representanteLegalNombre?: string | null;
  representanteLegalDni?: string | null;
}

export interface SolicitudDetail {
  id: number;
  matricula?: string | null;
  vehiculo?: SolicitudVehiculo | null;
  tipoTramite?: string | null;
  estado?: string | null;
  fechaCreacion?: string | null;
  fechaUltimaModificacion?: string | null;
  observaciones?: string | null;
  operacionPrecioVenta?: string | null;
  situacionDocumental?: string | null;
  expedienteId?: number | null;
  cliente?: ClienteResumen | null;
  creadoPor?: UsuarioResumen | null;
  modificadoPor?: UsuarioResumen | null;
  lecturaIaCliente?: LecturaIaSolicitudCliente | null;
  interesados: InteresadoSolicitud[];
  documentos: DocumentoExpediente[];
  incidencias: IncidenciaExpediente[];
  historial: HistorialExpediente[];
  mensajes: MensajeExpediente[];
}

export interface SolicitudVehiculo {
  matricula?: string | null;
  marca?: string | null;
  modelo?: string | null;
  bastidor?: string | null;
}

export interface LecturaIaSolicitudCliente {
  solicitudId: number;
  apiKeyConfigurada: boolean;
  documentacionSuficiente: boolean;
  puedeSolicitar: boolean;
  bloqueosDocumentales: string[];
  usosConsumidos: number;
  usosMaximos: number;
  usosRestantes: number;
  documentosIdentidad: number;
  documentosVehiculo?: number;
  documentosRoles: number;
  mensaje?: string | null;
}

export interface SolicitudInteresadoCoincidencia {
  rol?: string | null;
  dni?: string | null;
  nombreRegistrado?: string | null;
  nombreDeclarado?: string | null;
  telefonoRegistrado?: string | null;
  telefonoDeclarado?: string | null;
  direccionRegistrada?: string | null;
  direccionDeclarada?: string | null;
  camposDiferentes: string[];
}

export interface SolicitudIdentidadDetectadaInput {
  documentoId?: number | null;
  rol: string;
  tipoDocumentoDetectado?: string | null;
  identificador?: string | null;
  identificadorOriginal?: string | null;
  nombre?: string | null;
  apellido1?: string | null;
  apellido2?: string | null;
  razonSocial?: string | null;
  nombreCompleto?: string | null;
  fechaNacimiento?: string | null;
  fechaCaducidad?: string | null;
  direccionTexto?: string | null;
  tipoVia?: string | null;
  nombreVia?: string | null;
  numeroVia?: string | null;
  bloque?: string | null;
  portal?: string | null;
  escalera?: string | null;
  piso?: string | null;
  puerta?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
}

export interface SolicitudInteresadoHabitual {
  id: number;
  dni?: string | null;
  nombre?: string | null;
  nombrePila?: string | null;
  apellido1?: string | null;
  apellido2?: string | null;
  razonSocial?: string | null;
  telefono?: string | null;
  direccion?: string | null;
  tipoVia?: string | null;
  nombreVia?: string | null;
  numeroVia?: string | null;
  bloque?: string | null;
  portal?: string | null;
  escalera?: string | null;
  piso?: string | null;
  puerta?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
  tipoPersona?: string | null;
  documentos: number;
  documentoIdentidadAportado: boolean;
}

export interface SolicitudInteresadoHabitualInput {
  interesadoId: number;
  rol: string;
}

export interface SolicitudDocumentacionIaResponse {
  solicitudId: number;
  documentosIdentidad: number;
  documentosRoles: number;
  lecturasIdentidadNuevas: number;
  lecturasIdentidadReutilizadas: number;
  lecturasRolesNuevas: number;
  lecturasRolesReutilizadas: number;
  datosAplicados: boolean;
  yaEstabaCorrecta: boolean;
  requiereRevision: boolean;
  mensaje?: string | null;
  detalles: string[];
}

export interface SolicitudPreparacionAccion {
  tipo?: string | null;
  titulo: string;
  detalle?: string | null;
  bloque?: string | null;
  campo?: string | null;
  label?: string | null;
}

export interface SolicitudPreparacionItem {
  codigo: string;
  etiqueta: string;
  estado: "OK" | "AVISO" | "PENDIENTE" | "BLOQUEANTE" | string;
  detalle?: string | null;
  accionTipo?: string | null;
  accionLabel?: string | null;
  accionCampo?: string | null;
}

export interface SolicitudPreparacionBloque {
  codigo: string;
  titulo: string;
  estado: "OK" | "AVISO" | "PENDIENTE" | "BLOQUEANTE" | string;
  completados: number;
  total: number;
  items: SolicitudPreparacionItem[];
}

export interface SolicitudPreparacionDocumento {
  codigo: string;
  nombre: string;
  estado: "LISTO" | "FALTAN_DATOS" | "YA_APORTADO" | string;
  camposCompletos: number;
  camposTotales: number;
  faltantes: string[];
}

export interface SolicitudPreparacionTraspaso {
  solicitudId: number;
  estado: "LISTA" | "INCOMPLETA" | "BLOQUEADA" | string;
  progreso: number;
  siguienteAccion: SolicitudPreparacionAccion;
  bloques: SolicitudPreparacionBloque[];
  documentosGenerables: SolicitudPreparacionDocumento[];
}

export interface SolicitudUpsertInput {
  tipoTramiteId: number;
  matricula: string;
  vehiculoMarca?: string | null;
  vehiculoModelo?: string | null;
  vehiculoBastidor?: string | null;
  operacionPrecioVenta?: string | null;
  observaciones?: string | null;
  interesado1Rol?: string | null;
  interesado1Nombre?: string | null;
  interesado1NombrePila?: string | null;
  interesado1Apellido1?: string | null;
  interesado1Apellido2?: string | null;
  interesado1RazonSocial?: string | null;
  interesado1Dni?: string | null;
  interesado1Telefono?: string | null;
  interesado1Direccion?: string | null;
  interesado1TipoVia?: string | null;
  interesado1NombreVia?: string | null;
  interesado1NumeroVia?: string | null;
  interesado1Bloque?: string | null;
  interesado1Portal?: string | null;
  interesado1Escalera?: string | null;
  interesado1Piso?: string | null;
  interesado1Puerta?: string | null;
  interesado1CodigoPostal?: string | null;
  interesado1Municipio?: string | null;
  interesado1Provincia?: string | null;
  interesado2Rol?: string | null;
  interesado2Nombre?: string | null;
  interesado2NombrePila?: string | null;
  interesado2Apellido1?: string | null;
  interesado2Apellido2?: string | null;
  interesado2RazonSocial?: string | null;
  interesado2Dni?: string | null;
  interesado2Telefono?: string | null;
  interesado2Direccion?: string | null;
  interesado2TipoVia?: string | null;
  interesado2NombreVia?: string | null;
  interesado2NumeroVia?: string | null;
  interesado2Bloque?: string | null;
  interesado2Portal?: string | null;
  interesado2Escalera?: string | null;
  interesado2Piso?: string | null;
  interesado2Puerta?: string | null;
  interesado2CodigoPostal?: string | null;
  interesado2Municipio?: string | null;
  interesado2Provincia?: string | null;
  interesado3Rol?: string | null;
  interesado3Nombre?: string | null;
  interesado3NombrePila?: string | null;
  interesado3Apellido1?: string | null;
  interesado3Apellido2?: string | null;
  interesado3RazonSocial?: string | null;
  interesado3Dni?: string | null;
  interesado3Telefono?: string | null;
  interesado3Direccion?: string | null;
  interesado3TipoVia?: string | null;
  interesado3NombreVia?: string | null;
  interesado3NumeroVia?: string | null;
  interesado3Bloque?: string | null;
  interesado3Portal?: string | null;
  interesado3Escalera?: string | null;
  interesado3Piso?: string | null;
  interesado3Puerta?: string | null;
  interesado3CodigoPostal?: string | null;
  interesado3Municipio?: string | null;
  interesado3Provincia?: string | null;
}

export interface SolicitudBulkConvertResult {
  solicitudId: number;
  expedienteId?: number | null;
  convertida: boolean;
  mensaje?: string | null;
}

export interface SolicitudBulkConvertResponse {
  total: number;
  convertidas: number;
  fallidas: number;
  resultados: SolicitudBulkConvertResult[];
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
  estados?: string;
  archivo?: string;
  tipoTramiteId?: string;
  clienteId?: string;
  matricula?: string;
  interesado?: string;
  periodo?: string;
  fechaDesde?: string;
  fechaHasta?: string;
  pagina?: string;
  tamanio?: string;
}

export interface ProductivitySeriesItem {
  etiqueta: string;
  creados: number;
  finalizados: number;
}

export interface ProductivityBreakdownItem {
  codigo: string;
  etiqueta: string;
  total: number;
  valorMedio: number;
}

export interface ProductivityData {
  periodo: string;
  fechaDesde: string;
  fechaHasta: string;
  expedientesCreados: number;
  expedientesFinalizados: number;
  tiempoMedioDias: number;
  expedientesEnCurso: number;
  incidenciasActivas: number;
  expedientesConDocumentacionPendiente: number;
  evolucion: ProductivitySeriesItem[];
  tiemposPorTramite: ProductivityBreakdownItem[];
  volumenPorCliente: ProductivityBreakdownItem[];
  cuellosBotella: ProductivityBreakdownItem[];
}
