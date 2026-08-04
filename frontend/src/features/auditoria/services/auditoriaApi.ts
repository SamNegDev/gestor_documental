import { apiGet } from "../../../shared/api/http";
import type { AuditoriaCatalogos, AuditoriaFiltros, AuditoriaPage } from "../types";

function queryString(filters: AuditoriaFiltros) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
}

export function getAuditoria(filters: AuditoriaFiltros) {
  return apiGet<AuditoriaPage>(`/api/admin/auditoria?${queryString(filters)}`);
}

export function getAuditoriaCatalogos() {
  return apiGet<AuditoriaCatalogos>("/api/admin/auditoria/catalogos");
}
