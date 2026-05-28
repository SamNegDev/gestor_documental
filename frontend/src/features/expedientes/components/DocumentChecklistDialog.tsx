import { CheckCircle2, CircleDashed, FileText, X } from "lucide-react";
import type { DocumentoExpediente } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  open: boolean;
  documentos: DocumentoExpediente[];
  onClose: () => void;
};

export function DocumentChecklistDialog({ open, documentos, onClose }: Props) {
  if (!open) return null;

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar checklist" />
      <section aria-labelledby="checklist-title" aria-modal="true" className="exp-modal__panel" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Checklist documental</p>
            <h3 id="checklist-title">Documentos requeridos</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        <div className="checklist-list">
          {documentos.map((documento, index) => (
            <article className={`checklist-row checklist-row--${documento.estado.toLowerCase()}`} key={`${documento.tipo}-${documento.id ?? index}`}>
              <div className="pdf-icon">
                <FileText size={17} />
                <strong>PDF</strong>
              </div>
              <div>
                <strong>{documento.nombreOriginal || documento.nombre}</strong>
                <span>{humanizeEnum(documento.tipo)}</span>
              </div>
              <small>
                {documento.subido ? <CheckCircle2 size={16} /> : <CircleDashed size={16} />}
                {humanizeEnum(documento.estado)}
              </small>
            </article>
          ))}
        </div>

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">
            Cerrar
          </button>
        </footer>
      </section>
    </div>
  );
}
