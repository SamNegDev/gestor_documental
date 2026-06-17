import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { WhatsappAdjunto, WhatsappAdjuntosPage, WhatsappEvento, WhatsappEventosPage } from "../types";

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

export function asociarWhatsappEvento(id: number, body: { clienteId?: number | null; expedienteId?: number | null }) {
  return apiPostJson<WhatsappEvento>(`/api/whatsapp/eventos/${id}/asociar`, body);
}

export function revisarWhatsappEvento(id: number) {
  return apiPostJson<WhatsappEvento>(`/api/whatsapp/eventos/${id}/revisar`, {});
}

export function archivarWhatsappEvento(id: number) {
  return apiPostJson<WhatsappEvento>(`/api/whatsapp/eventos/${id}/archivar`, {});
}

export function getWhatsappAdjuntos(filters: { estado?: string; pagina?: number; tamanio?: number }) {
  return apiGet<WhatsappAdjuntosPage>(`/api/whatsapp/adjuntos${query(filters)}`);
}

export function clasificarWhatsappAdjunto(id: number, body: { expedienteId: number; tipoDocumento: string }) {
  return apiPostJson<WhatsappAdjunto>(`/api/whatsapp/adjuntos/${id}/clasificar`, body);
}

export function descartarWhatsappAdjunto(id: number) {
  return apiPostJson<WhatsappAdjunto>(`/api/whatsapp/adjuntos/${id}/descartar`, {});
}
