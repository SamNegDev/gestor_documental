import { Eye, FileText, Pencil, Trash2, Upload } from "lucide-react";
import type { DocumentoExpediente } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";

type Props = {
  documentos: DocumentoExpediente[];
  onOpenChecklist: () => void;
  onUploadDocument: (documento: DocumentoExpediente, archivo: File) => void;
  onEditDocument: (documento: DocumentoExpediente) => void;
  onDeleteDocument: (documento: DocumentoExpediente) => void;
};

export function DocumentsPanel({ documentos, onOpenChecklist, onUploadDocument, onEditDocument, onDeleteDocument }: Props) {
  const pendientesActuales = documentos.filter((documento) => documento.estado === "PENDIENTE" && documento.requeridoAhora);

  return (
    <section className="exp-panel">
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Documentación</p>
          <h3>Documentos del expediente</h3>
        </div>
        <button className="soft-button" onClick={onOpenChecklist} type="button">
          Checklist
        </button>
      </div>

      {pendientesActuales.length > 0 ? (
        <div className="documents-warning">
          Faltan {pendientesActuales.length} documento(s) requerido(s) para completar la fase actual.
        </div>
      ) : null}

      <div className="documents-list">
        {documentos.map((documento, index) => (
          <article className={`document-row document-row--${documento.estado.toLowerCase()}`} key={`${documento.tipo}-${documento.id ?? index}`}>
            <div className="pdf-icon">
              <FileText size={18} />
              <strong>PDF</strong>
            </div>

            <div className="document-row__body">
              <strong>{documento.nombreOriginal || documento.nombre}</strong>
              <span>
                {humanizeEnum(documento.tipo)}
                {documento.operacionLabel ? ` · ${documento.operacionLabel}` : ""}
              </span>
              <small>
                {documento.subido
                  ? `Subido ${formatDateTime(documento.fechaSubida)}${documento.subidoPor ? ` por ${documento.subidoPor}` : ""}`
                  : documento.descripcion || "Documento pendiente"}
              </small>
            </div>

            <span className="document-state">{humanizeEnum(documento.estado)}</span>

            <div className="document-row__actions">
              <button
                className="icon-button"
                disabled={!documento.id}
                onClick={() => documento.id && window.open(`/documentos/ver/${documento.id}`, "_blank", "noopener,noreferrer")}
                title="Ver documento"
                type="button"
              >
                <Eye size={16} />
              </button>
              <label className="icon-button" title="Subir documento">
                <Upload size={16} />
                <input
                  hidden
                  type="file"
                  accept=".pdf,.jpg,.jpeg,.png"
                  onChange={(event) => {
                    const file = event.currentTarget.files?.[0];
                    event.currentTarget.value = "";
                    if (file) onUploadDocument(documento, file);
                  }}
                />
              </label>
              <button className="icon-button" disabled={!documento.id} onClick={() => onEditDocument(documento)} title="Editar documento" type="button">
                <Pencil size={16} />
              </button>
              <button
                className="icon-button icon-button--danger"
                disabled={!documento.id}
                onClick={() => onDeleteDocument(documento)}
                title="Borrar documento"
                type="button"
              >
                <Trash2 size={16} />
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
