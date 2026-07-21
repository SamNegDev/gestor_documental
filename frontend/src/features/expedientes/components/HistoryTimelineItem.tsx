import { Send } from "lucide-react";
import type { HistorialExpediente } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";

type Props = {
  item: HistorialExpediente;
  showUser?: boolean;
};

export function HistoryTimelineItem({ item, showUser = true }: Props) {
  const isCommunication = item.tipoActividad === "COMUNICACION";

  return (
    <article className={`timeline-item ${isCommunication ? "timeline-item--communication" : ""}`}>
      {isCommunication ? (
        <div className="timeline-item__category">
          <Send aria-hidden="true" size={14} />
          <span>Comunicación</span>
        </div>
      ) : null}
      <strong>{humanizeEnum(item.accion)}</strong>
      <p>{item.descripcion || "Movimiento registrado en el expediente."}</p>
      <small>{formatDateTime(item.fechaCambio)}{showUser && item.usuario ? ` · ${item.usuario}` : ""}</small>
      {isCommunication ? <small className="timeline-item__note">No modifica el trámite ni su fecha de modificación.</small> : null}
    </article>
  );
}
