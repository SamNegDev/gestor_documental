import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { ExtraccionGaJob } from "../../ia/types";
import type { CategoriaHistorial, ClienteResumen, DocumentoExpediente, HistorialExpediente, HistorialPage, IncidenciaExpediente, MensajeExpediente, RequisitoDocumental } from "../types/expedienteDetail.types";

export interface LecturaIaCliente {
  expedienteId: number;
  apiKeyConfigurada: boolean;
  documentacionSuficiente: boolean;
  puedeSolicitar: boolean;
  jobCreado: boolean;
  bloqueosDocumentales: string[];
  usosConsumidos: number;
  usosMaximos: number;
  usosRestantes: number;
  mensaje?: string | null;
  ultimoJob?: ExtraccionGaJob | null;
}

export interface ExpedienteCliente {
  id: number;
  referencia: string;
  matricula?: string | null;
  tipoTramite?: string | null;
  tipoTramiteDescripcion?: string | null;
  estado: string;
  faseActual?: string | null;
  fechaInicio?: string | null;
  solicitudId?: number | null;
  siguienteMensaje?: string | null;
  cliente?: ClienteResumen | null;
  mensajesNoLeidos?: number;
  lecturaIa?: LecturaIaCliente | null;
  documentos: DocumentoExpediente[];
  requisitosDocumentales: RequisitoDocumental[];
  incidencias: IncidenciaExpediente[];
  mensajes: MensajeExpediente[];
  historial: HistorialExpediente[];
}

export async function getClienteExpediente(id: string | number): Promise<ExpedienteCliente> {
  const expediente = await apiGet<ExpedienteCliente>(`/api/cliente/expedientes/${id}`);

  return {
    ...expediente,
    documentos: expediente.documentos ?? [],
    requisitosDocumentales: expediente.requisitosDocumentales ?? [],
    incidencias: expediente.incidencias ?? [],
    mensajes: expediente.mensajes ?? [],
    historial: expediente.historial ?? [],
    lecturaIa: expediente.lecturaIa ?? null,
  };
}

export function getClienteExpedienteHistory(
  id: string | number,
  options: { pagina?: number; tamanio?: number; categoria?: CategoriaHistorial } = {},
): Promise<HistorialPage> {
  const params = new URLSearchParams({
    pagina: String(options.pagina ?? 0),
    tamanio: String(options.tamanio ?? 20),
  });
  if (options.categoria) params.set("categoria", options.categoria);
  return apiGet<HistorialPage>(`/api/cliente/expedientes/${id}/historial?${params}`);
}

export function sendClienteExpedienteMessage(expedienteId: string | number, contenido: string): Promise<void> {
  return apiPostJson(`/api/cliente/expedientes/${expedienteId}/mensajes`, { contenido });
}

export function markClienteExpedienteMessagesRead(expedienteId: string | number): Promise<void> {
  return apiPostJson(`/api/cliente/expedientes/${expedienteId}/mensajes/leidos`, {});
}

export function answerAdditionalInfo(expedienteId: string | number, contenido: string): Promise<void> {
  return apiPostJson(`/api/cliente/expedientes/${expedienteId}/informacion-adicional/respuesta`, { contenido });
}

export function requestClienteExpedienteIaReading(expedienteId: string | number): Promise<LecturaIaCliente> {
  return apiPostJson(`/api/cliente/expedientes/${expedienteId}/lectura-ia`, {});
}
