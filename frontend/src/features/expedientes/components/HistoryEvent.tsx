import type { LucideIcon } from "lucide-react";
import type { HistorialExpediente } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  item: HistorialExpediente;
  showUser?: boolean;
  last?: boolean;
  icon: LucideIcon;
};

const labels = {
  ESTADO: "Estado",
  DOCUMENTO: "Documento",
  INCIDENCIA: "Incidencia",
  COMUNICACION: "Comunicaci\u00f3n",
  TRAMITE: "Tr\u00e1mite",
  SISTEMA: "Sistema",
};

export function HistoryEvent({ item, showUser = true, last = false, icon: Icon }: Props) {
  const category = item.categoria ?? "SISTEMA";
  const time = item.fechaCambio
    ? new Intl.DateTimeFormat("es-ES", { hour: "2-digit", minute: "2-digit" }).format(new Date(item.fechaCambio))
    : "Hora no disponible";

  return (
    <article className={`history-event history-event--${category.toLowerCase()} ${last ? "is-last" : ""}`}>
      <div className="history-event__rail" aria-hidden="true">
        <span><Icon size={13} /></span>
      </div>
      <div className="history-event__content">
        <div className="history-event__title">
          <strong>{humanizeEnum(item.accion)}</strong>
          <span>{labels[category]}</span>
        </div>
        <p>{item.descripcion || "Movimiento registrado en el expediente."}</p>
        <time dateTime={item.fechaCambio}>
          {time}{showUser && item.usuario ? ` \u00b7 ${item.usuario}` : ""}
        </time>
      </div>
    </article>
  );
}
