import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { AvisosAdminResumen } from "../types";

export const getAvisosResumen = () => apiGet<AvisosAdminResumen>("/api/admin/avisos/resumen");

export const marcarAvisoLeido = (id: number) => apiPostJson(`/api/admin/avisos/${id}/leer`, {});

export const marcarAvisosLeidos = () => apiPostJson("/api/admin/avisos/leer-todos", {});
