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
  tipoPersona?: string | null;
  totalTramites: number;
  ultimaActividad?: string | null;
  tramites: TramiteRegistro[];
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
