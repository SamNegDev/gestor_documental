import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { DocumentoExpediente, HistorialExpediente, IncidenciaExpediente, MensajeExpediente } from "../types/expedienteDetail.types";

export interface ExpedienteCliente {
  id: number;
  referencia: string;
  matricula?: string | null;
  tipoTramiteDescripcion?: string | null;
  estado: string;
  faseActual?: string | null;
  fechaInicio?: string | null;
  siguienteMensaje?: string | null;
  documentos: DocumentoExpediente[];
  incidencias: IncidenciaExpediente[];
  mensajes: MensajeExpediente[];
  historial: HistorialExpediente[];
}

export async function getClienteExpediente(id: string | number): Promise<ExpedienteCliente> {
  const expediente = await apiGet<ExpedienteCliente>(`/api/cliente/expedientes/${id}`);

  return {
    ...expediente,
    documentos: expediente.documentos ?? [],
    incidencias: expediente.incidencias ?? [],
    mensajes: expediente.mensajes ?? [],
    historial: expediente.historial ?? [],
  };
}

export function sendClienteExpedienteMessage(expedienteId: string | number, contenido: string): Promise<void> {
  return apiPostJson(`/api/cliente/expedientes/${expedienteId}/mensajes`, { contenido });
}
