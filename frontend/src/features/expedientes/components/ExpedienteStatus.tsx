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
};

export function ExpedienteStatus({ status }: Props) {
  return <span className={`exp-status ${statusClass[status]}`}>{humanizeEnum(status)}</span>;
}
