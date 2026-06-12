import { apiGet } from "../../../shared/api/http";
import type { TareasPage, TareasResumen } from "../types";

export function getTareas(filters: { tipo?: string; prioridad?: string; ambito?: string; pagina?: number; tamanio?: number }) {
  const params = new URLSearchParams(); Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== "") params.set(key, String(value)); });
  return apiGet<TareasPage>(`/api/tareas?${params.toString()}`);
}
export const getTareasResumen = () => apiGet<TareasResumen>("/api/tareas/resumen");
