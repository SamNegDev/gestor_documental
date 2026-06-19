export type ExtraccionGaDocumento = {
  id: number;
  tipoDocumento: string;
  nombreArchivoOriginal: string;
  paginas: number;
  tamanoBytes: number;
};

export type ExtraccionGaPreview = {
  expedienteId: number;
  matricula: string | null;
  modelo: string;
  apiKeyConfigurada: boolean;
  extraccionDisponible: boolean;
  bloqueosDocumentales: string[];
  documentosRelevantes: number;
  paginasRelevantes: number;
  tamanoTotalBytes: number;
  costeEstimadoMinUsd: number;
  costeEstimadoMaxUsd: number;
  documentos: ExtraccionGaDocumento[];
};

export type ExtraccionGaResponse = {
  preview: ExtraccionGaPreview;
  ejecutado: boolean;
  resultadoJson: string | null;
  uso: Record<string, unknown> | null;
  aviso: string | null;
};

export type ExtraccionGaRequest = {
  modelo?: string;
  ejecutar: boolean;
};

export type EstadoRevisionGa = "BORRADOR" | "PREPARADO_EXPORTACION" | "EXPORTADO";
export type EstadoExtraccionGaJob = "PENDIENTE" | "PROCESANDO" | "COMPLETADO" | "ERROR" | "CANCELADO";

export type ExtraccionGaRevision = {
  id: number;
  expedienteId: number;
  matricula: string | null;
  clienteNombre: string | null;
  tipoTramite: string | null;
  modelo: string | null;
  estado: EstadoRevisionGa;
  confianzaGlobal: number | null;
  requiereRevisionHumana: boolean;
  resultadoIaJson: string | null;
  datosValidadosJson: string;
  fechaCreacion: string | null;
  fechaUltimaModificacion: string | null;
  fechaPreparado: string | null;
  fechaExportado: string | null;
  revisadoPor: string | null;
};

export type ExtraccionGaRevisionRequest = {
  resultadoIaJson?: string | null;
  datosValidadosJson: string;
  modelo?: string | null;
  estado: EstadoRevisionGa;
};

export type ExtraccionGaSincronizacion = {
  interesadosCreados: number;
  interesadosActualizados: number;
  relacionesCreadas: number;
  vehiculosCreados: number;
  vehiculosActualizados: number;
  mensaje: string;
};

export type ExtraccionGaQueueItem = {
  expedienteId: number;
  matricula: string | null;
  clienteNombre: string | null;
  tipoTramite: string | null;
  revisionId: number | null;
  revisionEstado: EstadoRevisionGa | null;
  confianzaGlobal: number | null;
  requiereRevisionHumana: boolean;
  fechaRevision: string | null;
  jobId: number | null;
  jobEstado: EstadoExtraccionGaJob | null;
  jobProgreso: number | null;
  jobFaseActual: string | null;
  jobMensajeError: string | null;
  jobFechaCreacion: string | null;
};

export type ExtraccionGaJob = {
  id: number;
  expedienteId: number;
  matricula: string | null;
  clienteNombre: string | null;
  tipoTramite: string | null;
  estado: EstadoExtraccionGaJob;
  modelo: string | null;
  progreso: number | null;
  faseActual: string | null;
  mensajeError: string | null;
  intentos: number | null;
  revisionId: number | null;
  revisionEstado: EstadoRevisionGa | null;
  confianzaGlobal: number | null;
  requiereRevisionHumana: boolean | null;
  fechaCreacion: string | null;
  fechaInicio: string | null;
  fechaFin: string | null;
};
