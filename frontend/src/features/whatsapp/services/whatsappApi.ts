import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { WhatsappEvento, WhatsappEventosPage } from "../types";

type Filters = {
  estado?: string;
  telefono?: string;
  pagina?: number;
  tamanio?: number;
};

function query(filters: Filters) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      params.set(key, String(value).trim());
    }
  });
  const suffix = params.toString();
  return suffix ? `?${suffix}` : "";
}

export function getWhatsappEventos(filters: Filters) {
  return apiGet<WhatsappEventosPage>(`/api/whatsapp/eventos${query(filters)}`);
}

export function asociarWhatsappEvento(id: number, body: { clienteId: number; expedienteId?: number | null }) {
  return apiPostJson<WhatsappEvento>(`/api/whatsapp/eventos/${id}/asociar`, body);
}
