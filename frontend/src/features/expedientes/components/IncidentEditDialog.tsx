import { useEffect, useState } from "react";
import { Save, X } from "lucide-react";
import type { IncidenciaExpediente } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";
import { uppercaseInputPreservingCursor } from "../../../shared/utils/text";

type Props = {
  incidencia: IncidenciaExpediente | null;
  saving: boolean;
  onClose: () => void;
  onSubmit: (incidencia: IncidenciaExpediente, observaciones: string) => void;
};

export function IncidentEditDialog({ incidencia, saving, onClose, onSubmit }: Props) {
  const [observaciones, setObservaciones] = useState("");

  useEffect(() => {
    setObservaciones(incidencia?.observaciones || "");
  }, [incidencia]);

  if (!incidencia) return null;
  const detalle = observaciones.trim();

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" disabled={saving} onClick={onClose} type="button" aria-label="Cerrar edicion" />
      <section aria-labelledby="incident-edit-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--narrow" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Editar incidencia</p>
            <h3 id="incident-edit-title">{humanizeEnum(incidencia.tipo)}</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" disabled={saving} onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>
        <div className="incident-form">
          <label>
            Observaciones
            <textarea
              autoFocus
              maxLength={500}
              onChange={(event) => uppercaseInputPreservingCursor(event, setObservaciones)}
              placeholder="Anade los detalles necesarios para el cliente"
              rows={6}
              value={observaciones}
            />
            <small>{observaciones.length}/500 caracteres</small>
          </label>
        </div>
        <footer className="exp-modal__footer">
          <button className="soft-button" disabled={saving} onClick={onClose} type="button">Cancelar</button>
          <button
            className="primary-button"
            disabled={saving || !detalle || detalle === (incidencia.observaciones || "").trim()}
            onClick={() => onSubmit(incidencia, detalle)}
            type="button"
          >
            <Save size={16} />
            {saving ? "Guardando" : "Guardar cambios"}
          </button>
        </footer>
      </section>
    </div>
  );
}