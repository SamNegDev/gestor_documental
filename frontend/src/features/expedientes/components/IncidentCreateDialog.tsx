import { useEffect, useState } from "react";
import { AlertTriangle, X } from "lucide-react";
import type { TipoIncidencia } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  open: boolean;
  tipos: TipoIncidencia[];
  loading: boolean;
  onClose: () => void;
  onSubmit: (tipoIncidenciaId: number, observaciones: string) => void;
};

export function IncidentCreateDialog({ open, tipos, loading, onClose, onSubmit }: Props) {
  const [tipoId, setTipoId] = useState("");
  const [observaciones, setObservaciones] = useState("");

  useEffect(() => {
    if (open) {
      setTipoId(tipos[0]?.id ? String(tipos[0].id) : "");
      setObservaciones("");
    }
  }, [open, tipos]);

  if (!open) return null;

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar incidencia" />
      <section aria-labelledby="incident-create-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--narrow" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Nueva incidencia</p>
            <h3 id="incident-create-title">Reclamar incidencia</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        <div className="incident-form">
          <label>
            Tipo de incidencia
            <select disabled={loading || tipos.length === 0} onChange={(event) => setTipoId(event.target.value)} value={tipoId}>
              {tipos.map((tipo) => (
                <option key={tipo.id} value={tipo.id}>
                  {humanizeEnum(tipo.nombre)}
                </option>
              ))}
            </select>
          </label>

          <label>
            Observaciones
            <textarea
              onChange={(event) => setObservaciones(event.target.value)}
              placeholder="Detalle opcional para el cliente"
              rows={4}
              value={observaciones}
            />
          </label>
        </div>

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">
            Cancelar
          </button>
          <button
            className="primary-button primary-button--danger"
            disabled={!tipoId || loading}
            onClick={() => onSubmit(Number(tipoId), observaciones)}
            type="button"
          >
            <AlertTriangle size={16} />
            Abrir incidencia
          </button>
        </footer>
      </section>
    </div>
  );
}
