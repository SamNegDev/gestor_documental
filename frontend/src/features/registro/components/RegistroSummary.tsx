import { AlertCircle, CheckCircle2, Clock3, History, Tags } from "lucide-react";
import type { TramiteRegistro } from "../types";
import { formatEnum } from "./TramitesRegistroTable";

const attentionStates = new Set(["INCIDENCIA", "PENDIENTE_DOCUMENTACION", "PENDIENTE_TRAMITE_VINCULADO", "SOLICITADA_INFORMACION_ADICIONAL", "REVISANDO_INCIDENCIAS"]);

export function RegistroSummary({ tramites, roles = [] }: { tramites: TramiteRegistro[]; roles?: string[] }) {
  const finalizados = tramites.filter((tramite) => tramite.estado === "FINALIZADO").length;
  const pendientes = tramites.filter((tramite) => attentionStates.has(tramite.estado || "")).length;
  const activos = Math.max(tramites.length - finalizados, 0);
  const rolesUnicos = [...new Set(roles.filter(Boolean))];

  return <section className="registry-summary" aria-label="Resumen del historial">
    <SummaryItem icon={History} label="Tramites" value={String(tramites.length)} />
    <SummaryItem icon={Clock3} label="En curso" value={String(activos)} />
    <SummaryItem icon={CheckCircle2} label="Finalizados" value={String(finalizados)} tone="success" />
    <SummaryItem icon={AlertCircle} label="Requieren atencion" value={String(pendientes)} tone={pendientes ? "danger" : "muted"} />
    <SummaryItem icon={Tags} label={rolesUnicos.length ? "Roles" : "Ultima actividad"} value={rolesUnicos.length ? rolesUnicos.map(formatEnum).join(" · ") : tramites[0]?.fechaUltimaModificacion || "Sin actividad"} wide />
  </section>;
}

function SummaryItem({ icon: Icon, label, value, tone = "default", wide = false }: { icon: typeof History; label: string; value: string; tone?: "default" | "success" | "danger" | "muted"; wide?: boolean }) {
  return <div className={`registry-summary__item registry-summary__item--${tone} ${wide ? "registry-summary__item--wide" : ""}`}>
    <Icon size={17} /><span><small>{label}</small><strong>{value}</strong></span>
  </div>;
}
