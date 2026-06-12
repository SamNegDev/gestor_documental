import { apiGet } from "../../../shared/api/http";
import type { BusquedaGlobalResultado } from "../types";

export function buscarGlobal(query: string) {
  return apiGet<BusquedaGlobalResultado>(`/api/busqueda-global?q=${encodeURIComponent(query)}`);
}
