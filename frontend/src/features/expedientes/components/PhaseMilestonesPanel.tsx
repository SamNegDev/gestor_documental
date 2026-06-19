import { CheckCircle2, CircleDashed, Lock, TimerReset } from "lucide-react";
import type { HitoAccion, HitoExpediente } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";

type Props = {
  closingDocumentsReady?: boolean;
  expedienteEstado?: string;
  hitos: HitoExpediente[];
  rollbackLocked?: boolean;
  onRunMilestoneAction: (hito: HitoExpediente, accion?: HitoAccion) => void;
};

function MilestoneIcon({ hito }: { hito: HitoExpediente }) {
  if (hito.completado) return <CheckCircle2 size={16} />;
  if (hito.bloqueado) return <Lock size={16} />;
  if (hito.estado === "ACTUAL") return <TimerReset size={16} />;
  return <CircleDashed size={16} />;
}

function isClosingMilestone(hito: HitoExpediente, index: number, total: number) {
  const text = `${hito.id} ${hito.titulo}`.toLowerCase();
  return index === total - 1 || text.includes("final") || text.includes("cierre");
}

function getDisplayHito(hito: HitoExpediente, index: number, total: number, expedienteEstado?: string, closingDocumentsReady?: boolean) {
  if (expedienteEstado !== "FINALIZADO" || !isClosingMilestone(hito, index, total)) return hito;
  return {
    ...hito,
    estado: closingDocumentsReady ? "COMPLETADO" : "ACTUAL",
    completado: Boolean(closingDocumentsReady),
    bloqueado: false,
  } satisfies HitoExpediente;
}

function isRollbackAction(accion: HitoAccion) {
  return accion.tipo === "RETROCEDER_HITO" || accion.tipo === "RETROCEDER_FINALIZACION";
}

export function PhaseMilestonesPanel({ closingDocumentsReady, expedienteEstado, hitos, rollbackLocked, onRunMilestoneAction }: Props) {
  const displayHitos = hitos.map((hito, index) => getDisplayHito(hito, index, hitos.length, expedienteEstado, closingDocumentsReady));

  return (
    <section className="exp-panel exp-panel--compact">
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Proceso</p>
          <h3>Hitos del expediente</h3>
        </div>
        <span className="exp-panel__counter">
          {displayHitos.filter((hito) => hito.completado).length}/{displayHitos.length}
        </span>
      </div>

      <ol className="milestones-list milestones-list--compact">
        {displayHitos.map((hito, index) => {
          const waitingForClosingDocs = expedienteEstado === "FINALIZADO" && isClosingMilestone(hito, index, displayHitos.length) && !closingDocumentsReady;
          const acciones = (hito.acciones || []).filter((accion) => !(rollbackLocked && isRollbackAction(accion)));
          const showActions = hito.estado === "ACTUAL" && !hito.bloqueado && (acciones.length > 0 || (!hito.completado && hito.accion));

          return (
          <li className={`milestone milestone--${hito.estado.toLowerCase()}`} key={hito.id}>
            <div className="milestone__icon">
              <MilestoneIcon hito={hito} />
            </div>
            <div className="milestone__content">
              <div className="milestone__title-row">
                <strong>{hito.titulo}</strong>
                <span>{waitingForClosingDocs ? "Pendiente comprobantes" : hito.estado === "ACTUAL" ? "Hito actual" : humanizeEnum(hito.estado)}</span>
              </div>
              <small>
                {hito.tipo ? humanizeEnum(hito.tipo) : "Tipo sin definir"}
                {hito.fecha ? ` · ${formatDateTime(hito.fecha)}` : ""}
                {hito.usuario ? ` · ${hito.usuario}` : ""}
                {hito.nota ? ` · ${hito.nota}` : ""}
              </small>
            </div>
            {showActions ? (
              <div className="milestone__actions">
                {acciones.length > 0
                  ? acciones.map((accion) => (
                      <button
                        className={`soft-button soft-button--compact milestone-action--${accion.tono || "default"}`}
                        key={`${hito.id}-${accion.tipo}-${accion.codigoHito || accion.label}`}
                        onClick={() => onRunMilestoneAction(hito, accion)}
                        type="button"
                      >
                        {accion.label}
                      </button>
                    ))
                  : hito.accion ? (
                      <button className="soft-button soft-button--compact" onClick={() => onRunMilestoneAction(hito)} type="button">
                        {hito.accionLabel || "Confirmar"}
                      </button>
                    ) : null}
              </div>
            ) : null}
          </li>
          );
        })}
      </ol>
    </section>
  );
}
