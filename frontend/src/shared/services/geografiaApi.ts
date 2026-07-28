import { apiGet } from "../api/http";

export type ProvinciaCatalogo = {
  codigo: string;
  nombre: string;
};

export type MunicipioCatalogo = {
  codigo: string;
  nombre: string;
  provinciaCodigo: string;
  provincia: string;
};

export type DireccionSugerencia = {
  codigoPostal: string;
  municipio: string;
  localidad?: string | null;
  provincia: string;
  direccion?: string | null;
};

type PagedResponse<T> = {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
};

export function getProvinciasCatalogo() {
  return apiGet<ProvinciaCatalogo[]>("/api/catalogos/geografia/provincias");
}

export function getMunicipiosCatalogo(provincia: string, query: string) {
  const params = new URLSearchParams({ provincia, q: query, pagina: "0", tamanio: "50" });
  return apiGet<PagedResponse<MunicipioCatalogo>>(`/api/catalogos/geografia/municipios?${params.toString()}`);
}

export function getDireccionesSugeridas(query: string, limite = 12) {
  const params = new URLSearchParams({ q: query, limite: String(limite) });
  return apiGet<DireccionSugerencia[]>(`/api/catalogos/geografia/direcciones?${params.toString()}`);
}
export function getCodigosPostales(provincia: string, municipio: string) {
  const params = new URLSearchParams({ provincia, municipio });
  return apiGet<DireccionSugerencia[]>(`/api/catalogos/geografia/codigos-postales?${params.toString()}`);
}