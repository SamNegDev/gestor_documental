import { apiDelete, apiGet, apiPatchForm, apiPostForm } from "../../../shared/api/http";

export function uploadExpedienteDocument(expedienteId: number, tipoDocumento: string, archivo: File, operacionId?: number | null): Promise<void> {
  const formData = new FormData();
  formData.append("tipoDocumento", tipoDocumento);
  formData.append("archivo", archivo);
  if (operacionId) formData.append("operacionId", String(operacionId));
  return apiPostForm(`/api/expedientes/${expedienteId}/documentos`, formData);
}

export function uploadSolicitudDocument(solicitudId: number, tipoDocumento: string, archivo: File): Promise<void> {
  const formData = new FormData();
  formData.append("tipoDocumento", tipoDocumento);
  formData.append("archivo", archivo);
  return apiPostForm(`/api/solicitudes/${solicitudId}/documentos`, formData);
}

export function updateDocument(
  documentoId: number,
  tipoDocumento?: string,
  nombreArchivo?: string,
  operacionId?: number | null,
  nombreAutomatico = false,
): Promise<void> {
  const formData = new FormData();
  if (tipoDocumento) formData.append("tipoDocumento", tipoDocumento);
  if (nombreArchivo) formData.append("nombreArchivo", nombreArchivo);
  if (operacionId !== undefined) {
    formData.append("actualizarOperacion", "true");
    if (operacionId !== null) formData.append("operacionId", String(operacionId));
  }
  if (nombreAutomatico) formData.append("nombreAutomatico", "true");
  return apiPatchForm(`/api/documentos/${documentoId}`, formData);
}

export function deleteDocument(documentoId: number): Promise<void> {
  return apiDelete(`/api/documentos/${documentoId}`);
}

export function getDocumentPageInfo(documentoId: number): Promise<{ totalPaginas: number }> {
  return apiGet<{ totalPaginas: number }>(`/api/documentos/${documentoId}/paginas`);
}

export function deleteDocumentPages(documentoId: number, rangoPaginas: string): Promise<void> {
  const formData = new FormData();
  formData.append("rangoPaginas", rangoPaginas);
  return apiPatchForm(`/api/documentos/${documentoId}/paginas`, formData);
}

export function extractDocumentPages(
  documentoId: number,
  rangoPaginas: string,
  tipoDocumento: string,
  nombreArchivo?: string,
  operacionId?: number | null,
): Promise<void> {
  const formData = new FormData();
  formData.append("rangoPaginas", rangoPaginas);
  formData.append("tipoDocumento", tipoDocumento);
  if (nombreArchivo) formData.append("nombreArchivo", nombreArchivo);
  if (operacionId) formData.append("operacionId", String(operacionId));
  return apiPostForm(`/api/documentos/${documentoId}/extraer`, formData);
}

export function mergeDocuments(
  documentoPrincipalId: number,
  documentoIds: number[],
  tipoDocumento?: string,
  nombreArchivo?: string,
  operacionId?: number | null,
): Promise<void> {
  const formData = new FormData();
  formData.append("documentoIds", documentoIds.join(","));
  if (tipoDocumento) formData.append("tipoDocumento", tipoDocumento);
  if (nombreArchivo) formData.append("nombreArchivo", nombreArchivo);
  if (operacionId) formData.append("operacionId", String(operacionId));
  return apiPostForm(`/api/documentos/${documentoPrincipalId}/unir`, formData);
}
