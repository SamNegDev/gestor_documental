import type { PagedResponse } from "../listados/types";

export interface Tarea {
  id: string; tipo: string; ambito: "GESTION" | "SEGUIMIENTO" | "CLIENTE"; prioridad: "ALTA" | "MEDIA" | "BAJA"; titulo: string; detalle: string;
  contexto?: string | null;
  entidad: string; entidadId: number; matricula?: string | null; clienteId?: number | null; cliente?: string | null;
  fechaReferencia?: string | null; diasPendiente: number; enlace: string;
  incidenciaIdsAvisoConjunto?: number[] | null; motivoAvisoConjuntoNoDisponible?: string | null;
}
export type TareasPage = PagedResponse<Tarea>;
export interface TareasResumen { total: number; urgentes: number; estancados: number; }
export interface ResumenDiarioResponse { clientesEnviados: number; cambiosIncluidos: number; avisos: string[]; }
export interface AvisoSeleccionadoPreview {
  destinatario: string; asunto: string; texto: string; html: string;
  incidencias: number; expedientes: number; envioReal: boolean;
}
