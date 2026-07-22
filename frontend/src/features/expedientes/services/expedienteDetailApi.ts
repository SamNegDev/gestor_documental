import { apiGet, apiPost, apiPostForm, apiPostJson, apiPutJson } from "../../../shared/api/http";
import type {
  ActualizacionDocumentalExpediente,
  CategoriaHistorial,
  CreacionConProcesamiento,
  ExpedienteDetail,
  ExpedienteEditCatalogs,
  ExpedienteEditInput,
  HistorialPage,
  InteresadoSearchResult,
  TipoIncidencia,
} from "../types/expedienteDetail.types";

export function getExpedienteDetail(id: string | number): Promise<ExpedienteDetail> {
  return apiGet<ExpedienteDetail>(`/api/expedientes/${id}`);
}

export function getExpedienteHistory(
  id: string | number,
  options: { pagina?: number; tamanio?: number; categoria?: CategoriaHistorial } = {},
): Promise<HistorialPage> {
  const params = new URLSearchParams({
    pagina: String(options.pagina ?? 0),
    tamanio: String(options.tamanio ?? 20),
  });
  if (options.categoria) params.set("categoria", options.categoria);
  return apiGet<HistorialPage>(`/api/expedientes/${id}/historial?${params}`);
}

export function getExpedienteEditCatalogs(): Promise<ExpedienteEditCatalogs> {
  return apiGet<ExpedienteEditCatalogs>("/api/expedientes/catalogos-edicion");
}

export function updateExpediente(id: string | number, input: ExpedienteEditInput): Promise<void> {
  return apiPutJson(`/api/expedientes/${id}`, input);
}

export function updateExpedienteInteresados(id: string | number, interesados: ExpedienteEditInput["interesados"]): Promise<void> {
  return apiPutJson(`/api/expedientes/${id}/interesados`, { interesados });
}

export function linkDependentExpediente(id: string | number, origenId: string, motivo: string): Promise<void> {
  return apiPostJson(`/api/expedientes/${id}/vinculo-tramite`, { origenId, motivo });
}

export function unlinkDependentExpediente(id: string | number): Promise<void> {
  return apiPost(`/api/expedientes/${id}/vinculo-tramite/desvincular`);
}

export function updateExpedienteFromExistingDocuments(
  id: string | number,
  options?: { forzarRelectura?: boolean },
): Promise<ActualizacionDocumentalExpediente> {
  const query = options?.forzarRelectura ? "?forzarRelectura=true" : "";
  return apiPostJson<ActualizacionDocumentalExpediente>(`/api/expedientes/${id}/documentacion/actualizar${query}`, {});
}

export function createExpediente(input: ExpedienteEditInput): Promise<{ id: number }> {
  return apiPostJson<{ id: number }>("/api/expedientes", input);
}

export function createExpedienteWithCompleteProcessing(input: {
  clienteId: number;
  tipoTramiteId: number;
  matricula: string;
  observaciones?: string;
  archivo: File;
}): Promise<CreacionConProcesamiento> {
  const formData = new FormData();
  formData.append("clienteId", String(input.clienteId));
  formData.append("tipoTramiteId", String(input.tipoTramiteId));
  formData.append("matricula", input.matricula);
  if (input.observaciones?.trim()) formData.append("observaciones", input.observaciones.trim());
  formData.append("archivo", input.archivo);
  return apiPostForm<CreacionConProcesamiento>("/api/expedientes/creacion-multiple", formData);
}

export function searchInteresados(query: string): Promise<InteresadoSearchResult[]> {
  return apiGet<InteresadoSearchResult[]>(`/api/expedientes/interesados/buscar?q=${encodeURIComponent(query)}`);
}

const hitoApiCodes: Record<string, string> = {
  "tramite-programa-gestion": "TRAMITE_PROGRAMA_GESTION",
  "modelo-620-presentado": "MODELO_620_PRESENTADO",
  "enviado-dgt": "ENVIADO_DGT",
};

export function completeExpedienteMilestone(expedienteId: string | number, hitoId: string): Promise<void> {
  const codigo = hitoApiCodes[hitoId] ?? hitoId;
  if (!codigo) return Promise.reject(new Error("Hito no soportado"));
  return apiPost(`/api/expedientes/${expedienteId}/hitos/${codigo}/completar`);
}

export function rollbackExpedienteMilestone(expedienteId: string | number, hitoId: string): Promise<void> {
  const codigo = hitoApiCodes[hitoId] ?? hitoId;
  if (!codigo) return Promise.reject(new Error("Hito no soportado"));
  return apiPost(`/api/expedientes/${expedienteId}/hitos/${codigo}/retroceder`);
}

export function finishExpediente(expedienteId: string | number): Promise<void> {
  return apiPost(`/api/expedientes/${expedienteId}/finalizar`);
}

export function cancelExpediente(expedienteId: string | number): Promise<void> {
  return apiPost(`/api/expedientes/${expedienteId}/cancelar`);
}

export function rollbackExpedienteFinalization(expedienteId: string | number): Promise<void> {
  return apiPost(`/api/expedientes/${expedienteId}/finalizar/retroceder`);
}

export function getIncidentTypes(): Promise<TipoIncidencia[]> {
  return apiGet<TipoIncidencia[]>("/api/expedientes/tipos-incidencia");
}

export function openExpedienteIncident(expedienteId: string | number, tipoIncidenciaId: number, observaciones: string): Promise<void> {
  const formData = new FormData();
  formData.append("tipoIncidenciaId", String(tipoIncidenciaId));
  formData.append("observaciones", observaciones);
  return apiPostForm(`/api/expedientes/${expedienteId}/incidencia`, formData);
}

export function requestAdditionalInfo(expedienteId: string | number, contenido: string): Promise<void> {
  return apiPostJson(`/api/expedientes/${expedienteId}/informacion-adicional`, { contenido });
}

export function resolveAdditionalInfo(expedienteId: string | number): Promise<void> {
  return apiPost(`/api/expedientes/${expedienteId}/informacion-adicional/revisar`);
}

export function resolveIncident(incidenciaId: number): Promise<void> {
  return apiPost(`/api/incidencias/${incidenciaId}/resolver`);
}

export function reclaimIncident(incidenciaId: number, observaciones: string): Promise<void> {
  const formData = new FormData();
  formData.append("observaciones", observaciones);
  return apiPostForm(`/api/incidencias/${incidenciaId}/reclamar`, formData);
}

export function answerIncident(incidenciaId: number, contenido: string): Promise<void> {
  return apiPostJson(`/api/incidencias/${incidenciaId}/respuesta`, { contenido });
}

export function notifyIncidentResolvedByClient(incidenciaId: number, comentario?: string): Promise<void> {
  return apiPostJson(`/api/incidencias/${incidenciaId}/resuelta-cliente`, { comentario: comentario || "" });
}

export function uploadIncidentDocument(incidenciaId: number, archivo: File, tipoDocumento = "DOCUMENTO_INCIDENCIA"): Promise<void> {
  const formData = new FormData();
  formData.append("archivo", archivo);
  formData.append("tipoDocumento", tipoDocumento);
  return apiPostForm(`/api/incidencias/${incidenciaId}/documento`, formData);
}

export function linkIncidentDocument(incidenciaId: number, documentoId: number): Promise<void> {
  return apiPost(`/api/incidencias/${incidenciaId}/documentos/${documentoId}/vincular`);
}

export function sendExpedienteMessage(expedienteId: string | number, contenido: string): Promise<void> {
  return apiPostJson(`/api/expedientes/${expedienteId}/mensajes`, { contenido });
}

export function markExpedienteMessagesRead(expedienteId: string | number): Promise<void> {
  return apiPostJson(`/api/expedientes/${expedienteId}/mensajes/leidos`, {});
}
