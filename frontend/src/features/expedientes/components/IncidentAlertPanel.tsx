import { AlertTriangle, CheckCircle2, Eye, Plus, ShieldAlert } from "lucide-react";
import type { IncidenciaExpediente } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";

type Props = {
  incidencias: IncidenciaExpediente[];
  onCreateIncident?: () => void;
  onResolveIncident: (incidencia: IncidenciaExpediente) => void;
};

export function IncidentAlertPanel({ incidencias, onCreateIncident, onResolveIncident }: Props) {
  const activas = incidencias.filter((incidencia) => !incidencia.resuelta);
  if (activas.length === 0) return null;
  const tieneRevisionCliente = activas.some((incidencia) => incidencia.pendienteRevisionCliente);
  const tieneComunicacionResuelta = activas.some((incidencia) => incidencia.revisionComunicadaPorCliente);

  return (
    <section
      className={`incident-alert-panel ${tieneRevisionCliente ? "incident-alert-panel--review" : ""}`}
      aria-label="Incidencias activas"
    >
      <div className="incident-alert-panel__icon">
        {tieneRevisionCliente ? <CheckCircle2 size={26} /> : <ShieldAlert size={26} />}
      </div>
      <div className="incident-alert-panel__content">
        <div className="incident-alert-panel__heading">
          <div>
            <p className="eyebrow">{tieneRevisionCliente ? "Revision pendiente" : "Bloqueo por incidencia"}</p>
            <h3>
              {tieneComunicacionResuelta
                ? "Incidencia comunicada como solucionada"
                : tieneRevisionCliente
                ? "Documento aportado por el cliente"
                : activas.length === 1
                  ? humanizeEnum(activas[0].tipo)
                  : `${activas.length} incidencias activas`}
            </h3>
          </div>
          {onCreateIncident ? (
            <button className="soft-button soft-button--compact" onClick={onCreateIncident} type="button">
              <Plus size={15} />
              Nueva incidencia
            </button>
          ) : null}
        </div>
        {activas.map((incidencia) => (
          <article className="incident-alert-item" key={incidencia.id}>
            <div>
              {incidencia.pendienteRevisionCliente ? (
                <strong className="incident-review-note">
                  <CheckCircle2 size={16} />
                  {incidencia.revisionComunicadaPorCliente
                    ? "El cliente indica que ha solucionado esta incidencia por su cuenta."
                    : "El cliente ha aportado documentacion para resolver esta incidencia."}
                </strong>
              ) : (
                <strong>
                  <AlertTriangle size={16} />
                  Pendiente de subsanacion
                </strong>
              )}
              <span className="incident-followup-badge">{incidentFollowupText(incidencia)}</span>
              <p>{incidencia.observaciones || "Sin observaciones"}</p>
              {incidencia.pendienteRevisionCliente ? (
                <div className="incident-review-callout">
                  <CheckCircle2 size={17} />
                  <div>
                    <strong>{incidencia.revisionComunicadaPorCliente ? "Intentar continuar el tramite" : "Documento pendiente de revision"}</strong>
                    <span>
                      {incidencia.revisionComunicadaPorCliente
                        ? "El cliente comunica que ya se puede intentar firmar o finalizar de nuevo."
                        : (incidencia.documentosRevision || []).length > 0
                        ? `${(incidencia.documentosRevision || []).length} documento(s) aportado(s) por el cliente.`
                        : "El cliente ha solicitado revision de la incidencia."}
                    </span>
                  </div>
                </div>
              ) : null}
              <small>Abierta {formatDateTime(incidencia.fechaCreacion)}</small>
            </div>
            <div className="incident-alert-item__actions">
              {(incidencia.documentosRevision || []).slice(0, 1).map((documento) => (
                <button
                  className="soft-button soft-button--compact"
                  disabled={!documento.id}
                  key={documento.id || documento.nombre}
                  onClick={() => documento.id && window.open(`/documentos/ver/${documento.id}`, "_blank", "noopener,noreferrer")}
                  type="button"
                >
                  <Eye size={15} />
                  Ver documento
                </button>
              ))}
              <button className="primary-button primary-button--danger" onClick={() => onResolveIncident(incidencia)} type="button">
                Resolver incidencia
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function incidentFollowupText(incidencia: IncidenciaExpediente) {
  const avisos = incidencia.contadorAvisos ?? 0;
  const etiquetaAvisos = avisos === 1 ? "1 aviso" : `${avisos} avisos`;
  const ultimoAviso = incidencia.fechaUltimoAviso
    ? `ultimo aviso ${formatDateTime(incidencia.fechaUltimoAviso)}`
    : "sin aviso enviado";
  return `${etiquetaAvisos} · ${ultimoAviso}`;
}
