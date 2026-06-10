import { apiGet, apiPostJson, apiPutJson } from "../../../shared/api/http";
import type { DashboardData, ExpedienteListItem, ListCatalogs, ListFilters, SolicitudDetail, SolicitudListItem, SolicitudUpsertInput } from "../types";

function buildQuery(filters: ListFilters) {
  const params = new URLSearchParams();

  Object.entries(filters).forEach(([key, value]) => {
    if (value && value.trim() !== "") {
      params.set(key, value.trim());
    }
  });

  const query = params.toString();
  return query ? `?${query}` : "";
}

export function getExpedientes(filters: ListFilters) {
  return apiGet<ExpedienteListItem[]>(`/api/expedientes${buildQuery(filters)}`);
}

export function getExpedienteListCatalogs() {
  return apiGet<ListCatalogs>("/api/expedientes/catalogos-listado");
}

export function bulkAdvanceExpedientes(input: { expedienteIds: number[]; accion: string; codigoHito?: string | null }) {
  return apiPostJson<{ total: number; mensaje: string }>("/api/expedientes/acciones-masivas/avanzar", input);
}

export function bulkFinalDocumentsUrl(expedienteIds: number[]) {
  const params = new URLSearchParams();
  expedienteIds.forEach((id) => params.append("ids", String(id)));
  return `/api/expedientes/justificantes-finales?${params.toString()}`;
}

export function getSolicitudes(filters: ListFilters) {
  return apiGet<SolicitudListItem[]>(`/api/solicitudes${buildQuery(filters)}`);
}

export function getSolicitudListCatalogs() {
  return apiGet<ListCatalogs>("/api/solicitudes/catalogos-listado");
}

export function getSolicitudDetail(id: string | number) {
  return apiGet<SolicitudDetail>(`/api/solicitudes/${id}`);
}

export function createSolicitud(input: SolicitudUpsertInput) {
  return apiPostJson<{ id: number }>("/api/solicitudes", input);
}

export function updateSolicitud(id: string | number, input: SolicitudUpsertInput) {
  return apiPutJson(`/api/solicitudes/${id}`, input);
}

export function convertirSolicitud(id: number) {
  return apiPostJson<{ id: number }>(`/api/solicitudes/${id}/convertir`, {});
}

export function cambiarEstadoSolicitud(id: number, nuevoEstado: string) {
  return apiPostJson<void>(`/api/solicitudes/${id}/estado`, { nuevoEstado });
}

export function enviarMensajeSolicitud(id: number, contenido: string) {
  return apiPostJson<void>(`/api/solicitudes/${id}/mensajes`, { contenido });
}

export function getDashboard() {
  return apiGet<DashboardData>("/api/dashboard");
}
