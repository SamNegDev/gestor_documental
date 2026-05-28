import { useState } from "react";
import { CheckCircle2, Eye, RotateCcw, Upload, X } from "lucide-react";
import type { DocumentoExpediente, IncidenciaExpediente } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";

type Props = {
  incidencia: IncidenciaExpediente | null;
  documentos: DocumentoExpediente[];
  onAccept: (incidencia: IncidenciaExpediente) => void;
  onClose: () => void;
  onLinkDocument: (incidencia: IncidenciaExpediente, documentoId: number) => void;
  onReclaim: (incidencia: IncidenciaExpediente, observaciones: string) => void;
  onUploadDocument: (incidencia: IncidenciaExpediente, archivo: File) => void;
};

export function IncidentResolutionDialog({
  incidencia,
  documentos,
  onAccept,
  onClose,
  onLinkDocument,
  onReclaim,
  onUploadDocument,
}: Props) {
  const [observaciones, setObservaciones] = useState("");
  const [documentoSeleccionado, setDocumentoSeleccionado] = useState("");
  if (!incidencia) return null;

  const documentosRevision = incidencia.documentosRevision || [];
  const documentosVinculables = documentos.filter((documento) => {
    if (!documento.id) return false;
    return !documentosRevision.some((vinculado) => vinculado.id === documento.id);
  });

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar resolucion" />
      <section aria-labelledby="incident-resolution-title" aria-modal="true" className="exp-modal__panel" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Resolucion de incidencia</p>
            <h3 id="incident-resolution-title">{humanizeEnum(incidencia.tipo)}</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        <div className="incident-resolution">
          <p>{incidencia.observaciones || "Sin observaciones"}</p>
          <small>Abierta {formatDateTime(incidencia.fechaCreacion)}</small>

          <div className="incident-documents">
            <div className="incident-documents__heading">
              <strong>Documentos vinculados a la incidencia</strong>
              <label className="soft-button soft-button--compact">
                <Upload size={15} />
                Subir documento
                <input
                  hidden
                  type="file"
                  accept=".pdf,.jpg,.jpeg,.png"
                  onChange={(event) => {
                    const file = event.currentTarget.files?.[0];
                    event.currentTarget.value = "";
                    if (file) onUploadDocument(incidencia, file);
                  }}
                />
              </label>
            </div>
            {documentosRevision.length === 0 ? (
              <span>No hay documentos vinculados todavia.</span>
            ) : (
              documentosRevision.map((documento) => (
                <article className="incident-document" key={documento.id || documento.nombre}>
                  <div>
                    <strong>{documento.nombreOriginal || documento.nombre}</strong>
                    <small>{humanizeEnum(documento.tipo)} · {formatDateTime(documento.fechaSubida)}</small>
                  </div>
                  <button
                    className="icon-button"
                    disabled={!documento.id}
                    onClick={() => documento.id && window.open(`/documentos/ver/${documento.id}`, "_blank", "noopener,noreferrer")}
                    title="Ver documento"
                    type="button"
                  >
                    <Eye size={16} />
                  </button>
                </article>
              ))
            )}
            {documentosVinculables.length > 0 ? (
              <div className="incident-link-row">
                <select onChange={(event) => setDocumentoSeleccionado(event.target.value)} value={documentoSeleccionado}>
                  <option value="">Vincular documento existente</option>
                  {documentosVinculables.map((documento) => (
                    <option key={documento.id || documento.nombre} value={documento.id || ""}>
                      {documento.nombreOriginal || documento.nombre} · {humanizeEnum(documento.tipo)}
                    </option>
                  ))}
                </select>
                <button
                  className="soft-button soft-button--compact"
                  disabled={!documentoSeleccionado}
                  onClick={() => {
                    onLinkDocument(incidencia, Number(documentoSeleccionado));
                    setDocumentoSeleccionado("");
                  }}
                  type="button"
                >
                  Vincular
                </button>
              </div>
            ) : null}
          </div>

          <label>
            Motivo si se vuelve a reclamar
            <textarea
              onChange={(event) => setObservaciones(event.target.value)}
              placeholder="Indica por que la documentacion no resuelve la incidencia"
              rows={3}
              value={observaciones}
            />
          </label>
        </div>

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">
            Cerrar
          </button>
          <button className="soft-button milestone-action--warning" onClick={() => onReclaim(incidencia, observaciones)} type="button">
            <RotateCcw size={16} />
            Volver a reclamar
          </button>
          <button className="primary-button" onClick={() => onAccept(incidencia)} type="button">
            <CheckCircle2 size={16} />
            Aceptar resolucion
          </button>
        </footer>
      </section>
    </div>
  );
}
