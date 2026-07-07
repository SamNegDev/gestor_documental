import { CheckCircle2, ClipboardCheck, FileWarning } from "lucide-react";
import type { DocumentoExpediente, HitoAccion, HitoExpediente } from "../types/expedienteDetail.types";

type Props = {
  hitos: HitoExpediente[];
  documentos: DocumentoExpediente[];
  siguientePaso?: HitoExpediente | null;
  onOpenChecklist: () => void;
  onRunMilestoneAction: (hito: HitoExpediente, accion?: HitoAccion) => void;
};

function isRollbackAction(accion?: HitoAccion | null) {
  return accion?.tipo === "RETROCEDER_HITO" || accion?.tipo === "RETROCEDER_FINALIZACION";
}

export function NextActionPanel({ hitos, documentos, siguientePaso, onOpenChecklist, onRunMilestoneAction }: Props) {
  const siguienteHito =
    siguientePaso ?? hitos.find((hito) => !hito.completado && !hito.bloqueado) ?? hitos.find((hito) => !hito.completado);
  const documentosPendientes = documentos.filter((documento) => documento.estado === "PENDIENTE" && documento.requeridoAhora);
  const checklistAction = documentosPendientes.length > 0 || siguienteHito?.tipo === "MANUAL_DOCUMENTO";
  const primaryAction = siguienteHito?.acciones?.find((accion) => !isRollbackAction(accion));
  const fallbackActionAvailable = Boolean(siguienteHito?.accion && !isRollbackAction({ tipo: siguienteHito.accion, label: "" }));
  const actionAvailable = Boolean(primaryAction || fallbackActionAvailable);
  const primaryDisabled = Boolean(siguienteHito?.bloqueado || siguienteHito?.completado || (!checklistAction && !actionAvailable));
  const primaryLabel = primaryAction?.label || (actionAvailable ? siguienteHito?.accionLabel || "Confirmar" : checklistAction ? "Aportar documento" : "Pendiente");
  const handlePrimary = () => {
    if (!siguienteHito) return;
    if (actionAvailable) {
      onRunMilestoneAction(siguienteHito, primaryAction);
      return;
    }
    onOpenChecklist();
  };

  return (
    <section className="next-action-panel">
      <div className="next-action-panel__icon">
        {documentosPendientes.length > 0 ? <FileWarning size={28} /> : <ClipboardCheck size={28} />}
      </div>

      <div className="next-action-panel__body">
        <p className="eyebrow">Siguiente paso</p>
        <h3>{siguienteHito?.titulo || "Expediente sin acciones pendientes"}</h3>
        <p>{siguienteHito?.nota || siguienteHito?.descripcion || "No hay acciones pendientes detectadas."}</p>
      </div>

      <div className="next-action-panel__actions">
        <button className="soft-button" onClick={onOpenChecklist} type="button">
          <ClipboardCheck size={16} />
          Ver checklist
        </button>
        <button className="primary-button" disabled={primaryDisabled} onClick={handlePrimary} type="button">
          <CheckCircle2 size={16} />
          {primaryLabel}
        </button>
      </div>
    </section>
  );
}
