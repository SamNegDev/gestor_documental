import { CalendarDays, Eye, FileText, Pencil, UserRound } from "lucide-react";
import { Link } from "react-router-dom";
import type { ExpedienteDetail } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";
import { ExpedienteStatus } from "./ExpedienteStatus";
import { LicensePlate } from "./LicensePlate";

type Props = {
  expediente: ExpedienteDetail;
};

export function ExpedienteHeader({ expediente }: Props) {
  const interesadoPrincipal = expediente.interesados[0];

  return (
    <section className="exp-header">
      <div className="exp-header__main">
        <LicensePlate value={expediente.matricula} />

        <div className="exp-header__title">
          <p className="eyebrow">{expediente.referencia}</p>
          <h2>{humanizeEnum(expediente.tipoTramiteDescripcion || expediente.tipoTramite)}</h2>
          <div className="exp-header__meta">
            <span>
              <FileText size={16} />
              {expediente.tipoTramite ? humanizeEnum(expediente.tipoTramite) : "Trámite sin definir"}
            </span>
            <span>
              <CalendarDays size={16} />
              Inicio: {formatDateTime(expediente.fechaInicio)}
            </span>
            <span>
              <UserRound size={16} />
              {interesadoPrincipal?.nombre || expediente.cliente?.nombre || "Sin interesado principal"}
            </span>
          </div>
        </div>
      </div>

      <div className="exp-header__status">
        <span>Estado actual</span>
        <ExpedienteStatus status={expediente.estado} />
        <small>{expediente.faseActual || "Fase sin definir"}</small>
        <Link className="soft-button soft-button--compact" to={`/expedientes/${expediente.id}/editar`}>
          <Pencil size={15} />
          Editar expediente
        </Link>
        <Link className="soft-button soft-button--compact" to={`/cliente/expedientes/${expediente.id}`}>
          <Eye size={15} />
          Vista cliente
        </Link>
      </div>
    </section>
  );
}
