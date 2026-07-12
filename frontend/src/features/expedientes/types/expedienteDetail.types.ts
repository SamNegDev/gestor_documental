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
  nombrePila?: string | null;
  apellido1?: string | null;
  apellido2?: string | null;
  razonSocial?: string | null;
  rol?: string;
  dni?: string;
  telefono?: string;
  direccion?: string;
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

export interface InteresadoSearchResult {
  id: number;
  nombre: string;
  nombrePila?: string | null;
  apellido1?: string | null;
  apellido2?: string | null;
  razonSocial?: string | null;
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
  interesadoId?: number | null;
  interesadoNombre?: string | null;
  operacionId?: number | null;
  operacionLabel?: string | null;
  estado: DocumentoEstado;
  subido: boolean;
  requeridoAhora: boolean;
  lecturaIdentidad?: DocumentoIdentidadLectura | null;
  lecturaRoles?: DocumentoRolesLectura | null;
  lecturaVehiculo?: DocumentoVehiculoLectura | null;
}

export interface DocumentoIdentidadLectura {
  id: number;
  documentoId: number;
  tipoDocumentoDetectado?: string | null;
  identificador?: string | null;
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
  confianzaGlobal?: number | null;
  requiereRevision: boolean;
  vinculadoAutomaticamente: boolean;
  interesadoVinculadoId?: number | null;
  interesadoVinculadoNombre?: string | null;
  mensaje?: string | null;
  modelo?: string | null;
  fechaLectura?: string | null;
  identidadesDetectadasTotal?: number;
  identidadesDetectadas?: DocumentoIdentidadDetectada[];
}

export interface DocumentoIdentidadDetectada {
  tipoDocumentoDetectado?: string | null;
  identificador?: string | null;
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
  confianzaGlobal?: number | null;
  requiereRevision: boolean;
  observaciones?: string | null;
}

export interface DocumentoRolesLectura {
  id: number;
  documentoId: number;
  tipoDocumentoDetectado?: string | null;
  fechaDocumento?: string | null;
  matricula?: string | null;
  bastidor?: string | null;
  valorDeclarado?: string | null;
  vendedorIdentificador?: string | null;
  vendedorNombre?: string | null;
  vendedorDireccion?: string | null;
  vendedorInteresadoId?: number | null;
  vendedorInteresadoNombre?: string | null;
  compradorIdentificador?: string | null;
  compradorNombre?: string | null;
  compradorDireccion?: string | null;
  compradorInteresadoId?: number | null;
  compradorInteresadoNombre?: string | null;
  confianzaGlobal?: number | null;
  requiereRevision: boolean;
  aplicable: boolean;
  motivoAplicacion?: string | null;
  aplicadoExpediente: boolean;
  fechaAplicacion?: string | null;
  mensaje?: string | null;
  modelo?: string | null;
  fechaLectura?: string | null;
}

export interface DocumentoVehiculoLectura {
  id: number;
  documentoId: number;
  tipoDocumentoDetectado?: string | null;
  matricula?: string | null;
  marca?: string | null;
  modeloVehiculo?: string | null;
  bastidor?: string | null;
  fechaMatriculacion?: string | null;
  fechaPrimeraMatriculacion?: string | null;
  confianzaGlobal?: number | null;
  requiereRevision: boolean;
  mensaje?: string | null;
  modelo?: string | null;
  fechaLectura?: string | null;
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
  interesadoRepresentadoId?: number | null;
  interesadoRepresentadoNombre?: string | null;
  rolRepresentado?: string | null;
  operacionId?: number | null;
  operacionLabel?: string | null;
  documentoId?: number | null;
  documentoNombre?: string | null;
  motivoOmision?: string | null;
  fechaCreacion?: string | null;
  fechaResolucion?: string | null;
}

export interface InconsistenciaDocumental {
  codigo: string;
  severidad: "BAJA" | "MEDIA" | "ALTA" | string;
  titulo: string;
  detalle: string;
  requisitoId?: number | null;
  documentoSugeridoId?: number | null;
  documentoSugeridoNombre?: string | null;
  accionSugerida?: string | null;
}

export interface ActualizacionDocumentalExpediente {
  identidadesLeidas: number;
  operacionesLeidas: number;
  vehiculosLeidos: number;
  lecturasIdentidadNuevas: number;
  lecturasIdentidadReutilizadas: number;
  lecturasRolesNuevas: number;
  lecturasRolesReutilizadas: number;
  lecturasVehiculoNuevas: number;
  lecturasVehiculoReutilizadas: number;
  datosAplicados: number;
  yaEstabaCorrecta: boolean;
  requiereRevision: boolean;
  mensaje?: string | null;
  detalles: string[];
  avisos: string[];
}

export type ProcesamientoExpedienteCompletoEstado = "PENDIENTE" | "PROCESANDO" | "COMPLETADO" | "ERROR";

export interface ProcesamientoExpedienteCompleto {
  jobId: string;
  expedienteId?: number | null;
  solicitudId?: number | null;
  documentoId: number;
  archivo: string;
  estado: ProcesamientoExpedienteCompletoEstado;
  documentosGenerados: number;
  mensaje?: string | null;
  fechaCreacion?: string | null;
  fechaActualizacion?: string | null;
}

export interface CreacionConProcesamiento {
  expedienteId?: number | null;
  solicitudId?: number | null;
  procesamiento: ProcesamientoExpedienteCompleto;
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
    nombrePila?: string | null;
    apellido1?: string | null;
    apellido2?: string | null;
    razonSocial?: string | null;
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
  noLeidoParaUsuario?: boolean;
}

export interface WhatsappExpediente {
  id: number;
  telefono?: string | null;
  nombrePerfil?: string | null;
  tipo?: string | null;
  texto?: string | null;
  estado?: "PENDIENTE" | "REVISADO" | "ARCHIVADO" | string | null;
  fechaRecepcion?: string | null;
  fechaRevision?: string | null;
  revisadoPor?: string | null;
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
  mensajesNoLeidos?: number;
  cliente?: ClienteResumen | null;
  creadoPor?: UsuarioResumen | null;
  modificadoPor?: UsuarioResumen | null;
  interesados: InteresadoExpediente[];
  documentos: DocumentoExpediente[];
  requisitosDocumentales: RequisitoDocumental[];
  inconsistenciasDocumentales: InconsistenciaDocumental[];
  operaciones: OperacionExpediente[];
  hitos: HitoExpediente[];
  incidencias: IncidenciaExpediente[];
  historial: HistorialExpediente[];
  mensajes: MensajeExpediente[];
  whatsappMensajes: WhatsappExpediente[];
}
