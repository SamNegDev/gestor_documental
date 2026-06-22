import { apiPostJson } from "../../../shared/api/http";
import type {
  JustificanteThempusPreviewResponse,
  JustificanteThempusRequest,
  JustificanteThempusSendResponse,
} from "../types/justificanteThempus.types";

const BASE = "/api/admin/justificantes-thempus";

export function previewJustificanteThempus(body: JustificanteThempusRequest) {
  return apiPostJson<JustificanteThempusPreviewResponse>(`${BASE}/preview`, body);
}

export function enviarJustificanteThempus(body: JustificanteThempusRequest) {
  return apiPostJson<JustificanteThempusSendResponse>(`${BASE}/enviar`, body);
}
