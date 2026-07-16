import { FileUp, Loader2, X } from "lucide-react";
import { useEffect, useState } from "react";
import type { OperacionExpediente } from "../types/expedienteDetail.types";
import { formatDocumentType } from "../utils/formatters";
import { DOCUMENT_TYPES } from "./DocumentEditDialog";

export type DocumentUploadSubmit = {
  tipoDocumento: string;
  archivo: File;
  operacionId?: number | null;
};

type Props = {
  activeOperationId?: number | null;
  initialFile?: File | null;
  operaciones?: OperacionExpediente[];
  open: boolean;
  saving?: boolean;
  onClose: () => void;
  onSubmit: (input: DocumentUploadSubmit) => void;
};

export function DocumentUploadDialog({
  activeOperationId = null,
  initialFile = null,
  operaciones = [],
  open,
  saving = false,
  onClose,
  onSubmit,
}: Props) {
  const [tipoDocumento, setTipoDocumento] = useState("OTROS");
  const [operacionId, setOperacionId] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);

  useEffect(() => {
    if (!open) return;
    setTipoDocumento("OTROS");
    setOperacionId(activeOperationId ? String(activeOperationId) : "");
    setArchivo(initialFile);
  }, [activeOperationId, initialFile, open]);

  if (!open) return null;

  const submit = () => {
    if (!archivo || saving) return;
    onSubmit({
      tipoDocumento,
      archivo,
      operacionId: operacionId ? Number(operacionId) : null,
    });
  };

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar subida de documento" />
      <section aria-labelledby="document-upload-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--narrow" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Documento</p>
            <h3 id="document-upload-title">Subir documento suelto</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" disabled={saving} onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        <div className="document-edit-form">
          <label>
            Tipo documental
            <select value={tipoDocumento} onChange={(event) => setTipoDocumento(event.target.value)}>
              {DOCUMENT_TYPES.filter((type) => type !== "EXPEDIENTE_COMPLETO").map((type) => (
                <option key={type} value={type}>
                  {formatDocumentType(type)}
                </option>
              ))}
            </select>
          </label>

          {operaciones.length > 1 ? (
            <label>
              Operacion
              <select value={operacionId} onChange={(event) => setOperacionId(event.target.value)}>
                <option value="">Documento general</option>
                {operaciones.map((operacion) => (
                  <option key={operacion.id} value={operacion.id}>
                    {operacion.label}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          <label>
            Archivo
            <span className="document-upload-file">
              <FileUp size={17} />
              <span>{archivo ? archivo.name : "Seleccionar archivo"}</span>
              <input
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={(event) => setArchivo(event.currentTarget.files?.[0] ?? null)}
              />
            </span>
          </label>
        </div>

        <footer className="exp-modal__footer">
          <button className="soft-button" disabled={saving} onClick={onClose} type="button">
            Cancelar
          </button>
          <button className="primary-button" disabled={!archivo || saving} onClick={submit} type="button">
            {saving ? <Loader2 className="button-spinner" size={16} /> : <FileUp size={16} />}
            {saving ? "Subiendo..." : "Subir documento suelto"}
          </button>
        </footer>
      </section>
    </div>
  );
}
