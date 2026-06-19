import { FilePenLine, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { DocumentoExpediente, OperacionExpediente } from "../types/expedienteDetail.types";
import { formatDocumentType } from "../utils/formatters";
import { uppercaseInput } from "../../../shared/utils/text";

const DOCUMENT_TYPES = [
  "DNI",
  "CIF",
  "CONTRATO_COMPRAVENTA",
  "PERMISO_CIRCULACION",
  "FICHA_TECNICA",
  "INFORME_DGT",
  "MANDATO",
  "FACTURA",
  "EXPEDIENTE_COMPLETO",
  "MANDATO_REPRESENTACION",
  "CAMBIO_TITULARIDAD",
  "AUTORIZACION_SERAFIN",
  "HUELLA_TRAMITE",
  "COMPROBANTE_DGT",
  "MODELO_620",
  "DOCUMENTO_INCIDENCIA",
  "OTROS",
];

export type DocumentEditSubmit = {
  tipoDocumento: string;
  nombreArchivo?: string;
  operacionId?: number | null;
  nombreAutomatico: boolean;
};

type Props = {
  documento: DocumentoExpediente | null;
  operaciones?: OperacionExpediente[];
  onClose: () => void;
  onSubmit: (input: DocumentEditSubmit) => void;
};

function nombreSinExtension(nombre: string) {
  const index = nombre.lastIndexOf(".");
  return index > 0 ? nombre.slice(0, index) : nombre;
}

export function DocumentEditDialog({ documento, operaciones = [], onClose, onSubmit }: Props) {
  const [tipoDocumento, setTipoDocumento] = useState("");
  const [nombreArchivo, setNombreArchivo] = useState("");
  const [operacionId, setOperacionId] = useState("");
  const [nombreAutomatico, setNombreAutomatico] = useState(true);

  useEffect(() => {
    if (!documento) return;
    setTipoDocumento(documento.tipo || "OTROS");
    setNombreArchivo(uppercaseInput(nombreSinExtension(documento.nombreOriginal || documento.nombre || "")));
    setOperacionId(documento.operacionId ? String(documento.operacionId) : "");
    setNombreAutomatico(documento.tipo === "OTROS");
  }, [documento]);

  const nombreAutomaticoPreview = useMemo(() => {
    if (!documento) return "";
    return formatDocumentType(tipoDocumento || documento.tipo);
  }, [documento, tipoDocumento]);

  if (!documento) return null;

  const submit = () => {
    if (!nombreAutomatico && !nombreArchivo.trim()) return;
    onSubmit({
      tipoDocumento,
      nombreArchivo: nombreAutomatico ? undefined : uppercaseInput(nombreArchivo.trim()),
      operacionId: operacionId ? Number(operacionId) : null,
      nombreAutomatico,
    });
  };

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar edicion de documento" />
      <section aria-labelledby="document-edit-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--narrow" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Documento</p>
            <h3 id="document-edit-title">Editar clasificacion</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        <div className="document-edit-form">
          <div className="document-edit-current">
            <FilePenLine size={18} />
            <span>{documento.nombreOriginal || documento.nombre}</span>
          </div>

          <label>
            Tipo documental
            <select
              value={tipoDocumento}
              onChange={(event) => {
                setTipoDocumento(event.target.value);
                if (event.target.value !== documento.tipo) setNombreAutomatico(true);
              }}
            >
              {DOCUMENT_TYPES.map((type) => (
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

          <label className="document-edit-toggle">
            <input checked={nombreAutomatico} type="checkbox" onChange={(event) => setNombreAutomatico(event.target.checked)} />
            <span>
              Generar nombre automatico
              <small>{nombreAutomaticoPreview}</small>
            </span>
          </label>

          {!nombreAutomatico ? (
            <label>
              Nombre personalizado
              <input value={nombreArchivo} onChange={(event) => setNombreArchivo(uppercaseInput(event.target.value))} />
            </label>
          ) : null}
        </div>

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">
            Cancelar
          </button>
          <button className="primary-button" disabled={!nombreAutomatico && !nombreArchivo.trim()} onClick={submit} type="button">
            Guardar documento
          </button>
        </footer>
      </section>
    </div>
  );
}
