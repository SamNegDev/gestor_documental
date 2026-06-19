import { apiGet, apiPostJson, apiPostJsonBlob } from "../../../shared/api/http";
import type {
  ExtraccionGaJob,
  ExtraccionGaPreview,
  ExtraccionGaQueueItem,
  ExtraccionGaRequest,
  ExtraccionGaResponse,
  ExtraccionGaRevision,
  ExtraccionGaRevisionRequest,
  ExtraccionGaSincronizacion,
} from "../types";

export function getExtraccionGaPreview(expedienteId: string | number) {
  return apiGet<ExtraccionGaPreview>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/preview`);
}

export function probarExtraccionGa(expedienteId: string | number, input: ExtraccionGaRequest) {
  return apiPostJson<ExtraccionGaResponse>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/probar`, input);
}

export function probarExtraccionGaMultiple(expedienteId: string | number, input: ExtraccionGaRequest) {
  return apiPostJson<ExtraccionGaResponse>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/probar-multiple`, input);
}

export function getExtraccionGaRevision(expedienteId: string | number) {
  return apiGet<ExtraccionGaRevision | null>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/revision`);
}

export function guardarExtraccionGaRevision(expedienteId: string | number, input: ExtraccionGaRevisionRequest) {
  return apiPostJson<ExtraccionGaRevision>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/revision`, input);
}

export function prepararExtraccionGaRevision(expedienteId: string | number) {
  return apiPostJson<ExtraccionGaRevision>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/revision/preparar`, {});
}

export function sincronizarExtraccionGaRevision(expedienteId: string | number) {
  return apiPostJson<ExtraccionGaSincronizacion>(`/api/admin/ia/extraccion-ga/expedientes/${expedienteId}/revision/sincronizar`, {});
}

export function getExtraccionGaPreparadas() {
  return apiGet<ExtraccionGaRevision[]>("/api/admin/ia/extraccion-ga/revisiones/preparadas");
}

export function getExtraccionGaPendientesRevision() {
  return apiGet<ExtraccionGaQueueItem[]>("/api/admin/ia/extraccion-ga/revision/pendientes");
}

export function crearExtraccionGaJobs(expedienteIds: number[], modelo?: string | null) {
  return apiPostJson<ExtraccionGaJob[]>("/api/admin/ia/extraccion-ga/jobs", { expedienteIds, modelo });
}

export function getExtraccionGaJobsActivos() {
  return apiGet<ExtraccionGaJob[]>("/api/admin/ia/extraccion-ga/jobs/activos");
}

export function exportarExtraccionGaPreparadas(expedienteIds: number[]) {
  return apiPostJsonBlob("/api/admin/ia/extraccion-ga/revisiones/exportar", { expedienteIds });
}
