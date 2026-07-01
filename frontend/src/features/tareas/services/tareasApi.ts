import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { ResumenDiarioResponse, TareasPage, TareasResumen } from "../types";

export function getTareas(filters: { tipo?: string; prioridad?: string; ambito?: string; clienteId?: string; pagina?: number; tamanio?: number }) {
  const params = new URLSearchParams(); Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== "") params.set(key, String(value)); });
  return apiGet<TareasPage>(`/api/tareas?${params.toString()}`);
}
export const getTareasResumen = () => apiGet<TareasResumen>("/api/tareas/resumen");
export const enviarAvisosConjuntos = (clienteId?: string) => apiPostJson<ResumenDiarioResponse>(`/api/admin/resumen-diario-tramites/incidencias/pendientes-notificar/enviar-masivo${clienteId ? `?clienteId=${clienteId}` : ""}`, {});
export const revisarTareaWhatsapp = (id: number) => apiPostJson(`/api/whatsapp/eventos/${id}/revisar`, {});
