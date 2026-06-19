import { apiGet, apiPostForm } from "../../../shared/api/http";
import type {
  CatalogoGestionKind,
  CatalogoGestionResumen,
  GestionPersonaCatalogo,
  GestionRepresentanteCatalogo,
  GestionVehiculoCatalogo,
  ImportacionCatalogo,
} from "../types";

const BASE = "/api/admin/catalogos-gestion-trafico";

export function getCatalogoGestionResumen() {
  return apiGet<CatalogoGestionResumen>(`${BASE}/resumen`);
}

export function importarCatalogoGestion(tipo: CatalogoGestionKind, archivo: File) {
  const formData = new FormData();
  formData.append("archivo", archivo);
  return apiPostForm<ImportacionCatalogo>(`${BASE}/${tipo}/importar`, formData);
}

function searchQuery(q: string, limit = 25) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (q.trim()) params.set("q", q.trim());
  return `?${params.toString()}`;
}

export function buscarPersonasCatalogo(q: string, limit = 25) {
  return apiGet<GestionPersonaCatalogo[]>(`${BASE}/personas${searchQuery(q, limit)}`);
}

export function buscarRepresentantesCatalogo(q: string, limit = 25) {
  return apiGet<GestionRepresentanteCatalogo[]>(`${BASE}/representantes${searchQuery(q, limit)}`);
}

export function buscarVehiculosCatalogo(q: string, limit = 25) {
  return apiGet<GestionVehiculoCatalogo[]>(`${BASE}/vehiculos${searchQuery(q, limit)}`);
}
