import { apiDelete, apiGet, apiPostForm, apiPostJson, apiPutJson } from "../../../shared/api/http";
import type { InteresadoRegistro, InteresadoRegistroUpdateInput, VehiculoRegistro, VehiculoRegistroUpdateInput } from "../types";

function query(value: string, periodo: string, fechaDesde = "", fechaHasta = "") {
  const params = new URLSearchParams({ periodo });
  if (value.trim()) params.set("q", value.trim());
  if (fechaDesde) params.set("fechaDesde", fechaDesde);
  if (fechaHasta) params.set("fechaHasta", fechaHasta);
  return `?${params.toString()}`;
}

export const getInteresadosRegistro = (search = "", periodo = "ESTE_MES", fechaDesde = "", fechaHasta = "") => apiGet<InteresadoRegistro[]>(`/api/registro/interesados${query(search, periodo, fechaDesde, fechaHasta)}`);
export const getInteresadoRegistro = (id: string | number) => apiGet<InteresadoRegistro>(`/api/registro/interesados/${id}`);
export const createInteresadoHabitual = (input: InteresadoRegistroUpdateInput) => apiPostJson<InteresadoRegistro>("/api/registro/interesados", input);
export const updateInteresadoRegistro = (id: string | number, input: InteresadoRegistroUpdateInput) => apiPutJson(`/api/registro/interesados/${id}`, input);
export const uploadInteresadoDocumento = (id: string | number, tipoDocumento: string, archivo: File) => {
  const formData = new FormData();
  formData.append("tipoDocumento", tipoDocumento);
  formData.append("archivo", archivo);
  return apiPostForm<InteresadoRegistro>(`/api/registro/interesados/${id}/documentos`, formData);
};
export const deleteInteresadoDocumento = (documentoId: number) => apiDelete(`/api/documentos/${documentoId}`);
export const getVehiculosRegistro = (search = "", periodo = "ESTE_MES", fechaDesde = "", fechaHasta = "") => apiGet<VehiculoRegistro[]>(`/api/registro/vehiculos${query(search, periodo, fechaDesde, fechaHasta)}`);
export const getVehiculoRegistro = (matricula: string) => apiGet<VehiculoRegistro>(`/api/registro/vehiculos/${encodeURIComponent(matricula)}`);
export const updateVehiculoRegistro = (matricula: string, input: VehiculoRegistroUpdateInput) => apiPutJson(`/api/registro/vehiculos/${encodeURIComponent(matricula)}`, input);
