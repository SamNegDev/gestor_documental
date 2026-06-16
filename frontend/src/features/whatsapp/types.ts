import type { PagedResponse } from "../listados/types";

export interface WhatsappEvento {
  id: number;
  messageId?: string | null;
  telefono?: string | null;
  nombrePerfil?: string | null;
  tipo?: string | null;
  texto?: string | null;
  accionCodigo?: string | null;
  procesado: boolean;
  estado?: "PENDIENTE" | "REVISADO" | "ARCHIVADO" | string | null;
  errorProcesado?: string | null;
  fechaRecepcion?: string | null;
  fechaRevision?: string | null;
  revisadoPor?: string | null;
  clienteId?: number | null;
  cliente?: string | null;
  expedienteId?: number | null;
  matricula?: string | null;
}

export type WhatsappEventosPage = PagedResponse<WhatsappEvento>;
