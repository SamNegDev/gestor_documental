import type { PagedResponse } from "../listados/types";

export type AuditoriaEvento = {
  id: number;
  fechaEvento: string;
  accion: string;
  resultado: "CORRECTO" | "DENEGADO" | "ERROR";
  recursoTipo?: string | null;
  recursoId?: number | null;
  recursoNombre?: string | null;
  documentoId?: number | null;
  documentoNombre?: string | null;
  documentoTipo?: string | null;
  expedienteId?: number | null;
  solicitudId?: number | null;
  clienteId?: number | null;
  usuarioId?: number | null;
  usuarioEmail?: string | null;
  usuarioRol?: string | null;
  direccionIp?: string | null;
  agenteUsuario?: string | null;
  metodoHttp?: string | null;
  ruta?: string | null;
  detalle?: string | null;
};

export type AuditoriaPage = PagedResponse<AuditoriaEvento>;

export type AuditoriaCatalogos = {
  acciones: string[];
  resultados: string[];
  recursos: string[];
};

export type AuditoriaFiltros = {
  accion?: string;
  resultado?: string;
  recursoTipo?: string;
  recursoId?: string;
  expedienteId?: string;
  usuarioId?: string;
  desde?: string;
  hasta?: string;
  pagina: number;
  tamanio: number;
};
