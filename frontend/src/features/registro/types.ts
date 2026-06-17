import type { DocumentoExpediente } from "../expedientes/types/expedienteDetail.types";

export interface TramiteRegistro {
  id: number;
  matricula?: string | null;
  tipoTramite?: string | null;
  estado?: string | null;
  rol?: string | null;
  cliente?: string | null;
  fechaUltimaModificacion?: string | null;
}

export interface InteresadoRegistro {
  id: number;
  dni: string;
  nombre: string;
  telefono?: string | null;
  direccion?: string | null;
  tipoVia?: string | null;
  nombreVia?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
  tipoPersona?: string | null;
  habitual?: boolean;
  documentos?: DocumentoExpediente[];
  totalTramites: number;
  ultimaActividad?: string | null;
  tramites: TramiteRegistro[];
}

export interface InteresadoRegistroUpdateInput {
  dni?: string | null;
  nombre?: string | null;
  telefono?: string | null;
  direccion?: string | null;
  tipoVia?: string | null;
  nombreVia?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
  tipoPersona?: string | null;
}

export interface VehiculoRegistro {
  id?: number | null;
  matricula: string;
  bastidor?: string | null;
  marca?: string | null;
  modelo?: string | null;
  fechaPrimeraMatriculacion?: string | null;
  observaciones?: string | null;
  totalTramites: number;
  ultimaActividad?: string | null;
  interesados: string[];
  tramites: TramiteRegistro[];
}

export interface VehiculoRegistroUpdateInput {
  bastidor?: string | null;
  marca?: string | null;
  modelo?: string | null;
  fechaPrimeraMatriculacion?: string | null;
  observaciones?: string | null;
}
