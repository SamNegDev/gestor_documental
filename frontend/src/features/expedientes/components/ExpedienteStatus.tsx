import type { ExpedienteStatus as ExpedienteStatusType } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  status: ExpedienteStatusType;
};

const statusClass: Record<ExpedienteStatusType, string> = {
  EN_TRAMITE: "exp-status--info",
  INCIDENCIA: "exp-status--warning",
  FINALIZADO: "exp-status--success",
  RECHAZADO: "exp-status--danger",
  ENVIADO_DGT: "exp-status--info",
  REVISANDO_INCIDENCIAS: "exp-status--warning",
  SOLICITADA_INFORMACION_ADICIONAL: "exp-status--warning",
  INFORMACION_ADICIONAL_RECIBIDA: "exp-status--info",
};

export function ExpedienteStatus({ status }: Props) {
  return <span className={`exp-status ${statusClass[status]}`}>{humanizeEnum(status)}</span>;
}
