import { apiPatchForm, apiPostForm } from "../../../shared/api/http";

export type CreateRequirementInput = {
  tipoDocumento: string;
  descripcion?: string;
  interesadoId?: number | null;
  rolInteresado?: string | null;
  estadoInicial: "REQUERIDO" | "POSTERIOR";
};

export function createRequirement(expedienteId: number, input: CreateRequirementInput): Promise<void> {
  const formData = new FormData();
  formData.append("tipoDocumento", input.tipoDocumento);
  if (input.descripcion?.trim()) formData.append("descripcion", input.descripcion.trim());
  formData.append("estadoInicial", input.estadoInicial);
  if (input.interesadoId) formData.append("interesadoId", String(input.interesadoId));
  if (input.rolInteresado) formData.append("rolInteresado", input.rolInteresado);
  return apiPostForm(`/api/expedientes/${expedienteId}/requisitos-documentales`, formData);
}

export function omitRequirement(requisitoId: number, motivo: string): Promise<void> {
  const formData = new FormData();
  formData.append("motivo", motivo);
  return apiPatchForm(`/api/requisitos-documentales/${requisitoId}/omitir`, formData);
}

export function reopenRequirement(requisitoId: number): Promise<void> {
  return apiPatchForm(`/api/requisitos-documentales/${requisitoId}/reabrir`, new FormData());
}

export function linkRequirementDocument(requisitoId: number, documentoId: number): Promise<void> {
  const formData = new FormData();
  formData.append("documentoId", String(documentoId));
  return apiPatchForm(`/api/requisitos-documentales/${requisitoId}/vincular-documento`, formData);
}

export function uploadRequirementDocument(requisitoId: number, archivo: File): Promise<void> {
  const formData = new FormData();
  formData.append("archivo", archivo);
  return apiPostForm(`/api/requisitos-documentales/${requisitoId}/documento`, formData);
}
