import { apiGet } from "../../../shared/api/http";
import type { InteresadoRegistro, VehiculoRegistro } from "../types";

function query(value: string, periodo: string, fechaDesde = "", fechaHasta = "") {
  const params = new URLSearchParams({ periodo });
  if (value.trim()) params.set("q", value.trim());
  if (fechaDesde) params.set("fechaDesde", fechaDesde);
  if (fechaHasta) params.set("fechaHasta", fechaHasta);
  return `?${params.toString()}`;
}

export const getInteresadosRegistro = (search = "", periodo = "ESTE_MES", fechaDesde = "", fechaHasta = "") => apiGet<InteresadoRegistro[]>(`/api/registro/interesados${query(search, periodo, fechaDesde, fechaHasta)}`);
export const getInteresadoRegistro = (id: string | number) => apiGet<InteresadoRegistro>(`/api/registro/interesados/${id}`);
export const getVehiculosRegistro = (search = "", periodo = "ESTE_MES", fechaDesde = "", fechaHasta = "") => apiGet<VehiculoRegistro[]>(`/api/registro/vehiculos${query(search, periodo, fechaDesde, fechaHasta)}`);
export const getVehiculoRegistro = (matricula: string) => apiGet<VehiculoRegistro>(`/api/registro/vehiculos/${encodeURIComponent(matricula)}`);
