import { apiDelete, apiGet, apiPostForm, apiPostJson, apiPutJson } from "../../../shared/api/http";
import type { CreacionConProcesamiento } from "../../expedientes/types/expedienteDetail.types";
import type { DashboardData, ExpedienteListItem, ListCatalogs, ListFilters, PagedResponse, ProductivityData, SolicitudBulkConvertResponse, SolicitudDetail, SolicitudDocumentacionIaResponse, SolicitudIdentidadDetectadaInput, SolicitudInteresadoCoincidencia, SolicitudListItem, SolicitudPreparacionTraspaso, SolicitudUpsertInput } from "../types";

function buildQuery(filters: ListFilters) {
  const params = new URLSearchParams();

  Object.entries(filters).forEach(([key, value]) => {
    if (typeof value === "string" && value.trim() !== "") {
      params.set(key, value.trim());
    }
  });

  const query = params.toString();
  return query ? `?${query}` : "";
}

export function getExpedientes(filters: ListFilters) {
  return apiGet<PagedResponse<ExpedienteListItem>>(`/api/expedientes${buildQuery(filters)}`);
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

export function bulkHaciendaDocumentsUrl(expedienteIds: number[]) {
  const params = new URLSearchParams();
  expedienteIds.forEach((id) => params.append("ids", String(id)));
  return `/api/expedientes/documentacion-hacienda?${params.toString()}`;
}

export function getSolicitudes(filters: ListFilters) {
  return apiGet<PagedResponse<SolicitudListItem>>(`/api/solicitudes${buildQuery(filters)}`);
}

export function getSolicitudListCatalogs() {
  return apiGet<ListCatalogs>("/api/solicitudes/catalogos-listado");
}

export function getSolicitudDetail(id: string | number) {
  return apiGet<SolicitudDetail>(`/api/solicitudes/${id}`);
}

export function getSolicitudPreparacionTraspaso(id: string | number) {
  return apiGet<SolicitudPreparacionTraspaso>(`/api/solicitudes/${id}/preparacion-traspaso`);
}

export function createSolicitud(input: SolicitudUpsertInput) {
  return apiPostJson<{ id: number }>("/api/solicitudes", input);
}

export function createSolicitudWithCompleteProcessing(input: { tipoTramiteId: number; matricula: string; archivo: File }) {
  const formData = new FormData();
  formData.append("tipoTramiteId", String(input.tipoTramiteId));
  formData.append("matricula", input.matricula);
  formData.append("archivo", input.archivo);
  return apiPostForm<CreacionConProcesamiento>("/api/solicitudes/creacion-multiple", formData);
}

export function updateSolicitud(id: string | number, input: SolicitudUpsertInput) {
  return apiPutJson(`/api/solicitudes/${id}`, input);
}

export function deleteSolicitud(id: string | number) {
  return apiDelete(`/api/solicitudes/${id}`);
}

export function convertirSolicitud(id: number) {
  return apiPostJson<{ id: number }>(`/api/solicitudes/${id}/convertir`, {});
}

export function procesarSolicitudDocumentacionIa(id: number, options?: { forzarRelectura?: boolean }) {
  const query = options?.forzarRelectura ? "?forzarRelectura=true" : "";
  return apiPostJson<SolicitudDocumentacionIaResponse>(`/api/solicitudes/${id}/documentacion-ia/procesar${query}`, {});
}

export function procesarSolicitudDocumentacionIaCliente(id: number) {
  return apiPostJson<SolicitudDocumentacionIaResponse>(`/api/solicitudes/${id}/documentacion-ia/cliente`, {});
}

export function getSolicitudInteresadoCoincidencias(id: number) {
  return apiGet<SolicitudInteresadoCoincidencia[]>(`/api/solicitudes/${id}/interesados/coincidencias`);
}

export function anadirIdentidadDetectadaSolicitud(id: number, input: SolicitudIdentidadDetectadaInput) {
  return apiPostJson<SolicitudDetail>(`/api/solicitudes/${id}/interesados/detectados`, input);
}

export function bulkConvertSolicitudes(solicitudIds: number[]) {
  return apiPostJson<SolicitudBulkConvertResponse>("/api/solicitudes/convertir-masivo", { solicitudIds });
}

export function checkInboundSolicitudEmail() {
  return apiPostJson<{ ejecutada: boolean; mensaje: string }>("/api/solicitudes/correo-entrante/comprobar", {});
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

export function getProductivity(filters: Pick<ListFilters, "periodo" | "fechaDesde" | "fechaHasta">) {
  const params = new URLSearchParams();
  params.set("periodo", filters.periodo || "ULTIMA_SEMANA");
  if (filters.fechaDesde) params.set("fechaDesde", filters.fechaDesde);
  if (filters.fechaHasta) params.set("fechaHasta", filters.fechaHasta);
  return apiGet<ProductivityData>(`/api/dashboard/productividad?${params.toString()}`);
}
