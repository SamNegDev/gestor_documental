import { apiDelete, apiGet, apiPostForm, apiPostJson, apiPutJson } from "../../../shared/api/http";
import type { InteresadoRegistro, InteresadoRegistroUpdateInput, VehiculoRegistro, VehiculoRegistroUpdateInput } from "../types";

function registroQuery(value: string, periodo: string, fechaDesde = "", fechaHasta = "") {
  const params = new URLSearchParams({ periodo });
  if (value.trim()) params.set("q", value.trim());
  if (fechaDesde) params.set("fechaDesde", fechaDesde);
  if (fechaHasta) params.set("fechaHasta", fechaHasta);
  return `?${params.toString()}`;
}

function interesadosQuery(value: string, periodo: string, fechaDesde = "", fechaHasta = "", vista = "HABITUALES") {
  const params = new URLSearchParams(registroQuery(value, periodo, fechaDesde, fechaHasta).slice(1));
  params.set("vista", vista);
  return `?${params.toString()}`;
}

export const getInteresadosRegistro = (search = "", periodo = "ULTIMA_SEMANA", fechaDesde = "", fechaHasta = "", vista = "HABITUALES") => apiGet<InteresadoRegistro[]>(`/api/registro/interesados${interesadosQuery(search, periodo, fechaDesde, fechaHasta, vista)}`);
export const getInteresadoRegistro = (id: string | number) => apiGet<InteresadoRegistro>(`/api/registro/interesados/${id}`);
export const createInteresadoHabitual = (input: InteresadoRegistroUpdateInput) => apiPostJson<InteresadoRegistro>("/api/registro/interesados", input);
export const markInteresadoAsHabitual = (id: string | number) => apiPostJson<InteresadoRegistro>(`/api/registro/interesados/${id}/habitual`, {});
export const updateInteresadoRegistro = (id: string | number, input: InteresadoRegistroUpdateInput) => apiPutJson(`/api/registro/interesados/${id}`, input);
export const uploadInteresadoDocumento = (id: string | number, tipoDocumento: string, archivo: File) => {
  const formData = new FormData();
  formData.append("tipoDocumento", tipoDocumento);
  formData.append("archivo", archivo);
  return apiPostForm<InteresadoRegistro>(`/api/registro/interesados/${id}/documentos`, formData);
};
export const deleteInteresadoDocumento = (documentoId: number) => apiDelete(`/api/documentos/${documentoId}`);
export const getVehiculosRegistro = (search = "", periodo = "ULTIMA_SEMANA", fechaDesde = "", fechaHasta = "") => apiGet<VehiculoRegistro[]>(`/api/registro/vehiculos${registroQuery(search, periodo, fechaDesde, fechaHasta)}`);
export const getVehiculoRegistro = (matricula: string) => apiGet<VehiculoRegistro>(`/api/registro/vehiculos/${encodeURIComponent(matricula)}`);
export const updateVehiculoRegistro = (matricula: string, input: VehiculoRegistroUpdateInput) => apiPutJson(`/api/registro/vehiculos/${encodeURIComponent(matricula)}`, input);
