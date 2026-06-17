import { Combine, CopyPlus, Eye, Pencil, Scissors, Trash2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { uppercaseInput } from "../../../shared/utils/text";
import { getDocumentPageInfo } from "../services/documentosApi";
import type { DocumentoExpediente, OperacionExpediente } from "../types/expedienteDetail.types";
import { formatDocumentType } from "../utils/formatters";

const DOCUMENT_TYPES = [
  "DNI",
  "CIF",
  "CONTRATO_COMPRAVENTA",
  "PERMISO_CIRCULACION",
  "FICHA_TECNICA",
  "MANDATO",
  "FACTURA",
  "CAMBIO_TITULARIDAD",
  "AUTORIZACION_SERAFIN",
  "HUELLA_TRAMITE",
  "COMPROBANTE_DGT",
  "MODELO_620",
  "OTROS",
];

type Props = {
  documentos: DocumentoExpediente[];
  operaciones?: OperacionExpediente[];
  open: boolean;
  onClose: () => void;
  onDeleteDocument: (documento: DocumentoExpediente) => void;
  onDeletePages: (documento: DocumentoExpediente, rangoPaginas: string) => Promise<void>;
  onExtractPages: (documento: DocumentoExpediente, rangoPaginas: string, tipoDocumento: string, operacionId?: number | null) => Promise<void>;
  onMergeDocuments: (documento: DocumentoExpediente, documentoIds: number[], tipoDocumento: string, nombreSinExtension: string, operacionId?: number | null) => Promise<void>;
  onSaveDocument: (documento: DocumentoExpediente, tipoDocumento: string, operacionId?: number | null) => void;
  title?: string;
};

function nombreSinExtension(nombre: string) {
  const index = nombre.lastIndexOf(".");
  return index > 0 ? nombre.slice(0, index) : nombre;
}

export function OcrReviewDialog({ documentos, operaciones = [], open, onClose, onDeleteDocument, onDeletePages, onExtractPages, onMergeDocuments, onSaveDocument, title = "Editor documental" }: Props) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editType, setEditType] = useState("");
  const [editOperationId, setEditOperationId] = useState("");
  const [previewVersion, setPreviewVersion] = useState(0);
  const [pageCounts, setPageCounts] = useState<Record<number, number>>({});
  const [croppingId, setCroppingId] = useState<number | null>(null);
  const [selectedPages, setSelectedPages] = useState<number[]>([]);
  const [mergingId, setMergingId] = useState<number | null>(null);
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<number[]>([]);
  const [extractingId, setExtractingId] = useState<number | null>(null);
  const [selectedExtractPages, setSelectedExtractPages] = useState<number[]>([]);
  const [extractType, setExtractType] = useState("OTROS");
  const [extractOperationId, setExtractOperationId] = useState("");
  const { confirm, dialog } = useConfirmDialog();

  useEffect(() => {
    if (!open) return;
    const ids = documentos.map((documento) => documento.id).filter((id): id is number => Boolean(id));
    ids.forEach((documentoId) => {
      getDocumentPageInfo(documentoId)
        .then((info) => setPageCounts((current) => ({ ...current, [documentoId]: info.totalPaginas })))
        .catch(() => setPageCounts((current) => ({ ...current, [documentoId]: 0 })));
    });
  }, [documentos, open, previewVersion]);

  if (!open) return null;

  const startEdit = (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    setEditingId(documento.id);
    setEditType(documento.tipo);
    setEditOperationId(documento.operacionId ? String(documento.operacionId) : "");
  };

  const saveEdit = (documento: DocumentoExpediente) => {
    onSaveDocument(documento, editType || documento.tipo, editOperationId ? Number(editOperationId) : null);
    setEditingId(null);
  };

  const startCrop = (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    setCroppingId(documento.id);
    setSelectedPages([]);
  };

  const togglePage = (pagina: number) => {
    setSelectedPages((current) =>
      current.includes(pagina) ? current.filter((item) => item !== pagina) : [...current, pagina].sort((a, b) => a - b),
    );
  };

  const applyCrop = async (documento: DocumentoExpediente) => {
    const total = documento.id ? pageCounts[documento.id] ?? 0 : 0;
    if (selectedPages.length === 0) {
      alert("Selecciona al menos una pagina.");
      return;
    }
    if (selectedPages.length >= total) {
      alert("No se pueden eliminar todas las paginas del documento.");
      return;
    }
    const confirmed = await confirm({
      title: "Borrar paginas",
      description: `Se eliminaran las paginas ${selectedPages.join(", ")} de ${documento.nombreOriginal || documento.nombre}.`,
      confirmLabel: "Borrar paginas",
      tone: "danger",
    });
    if (!confirmed) return;
    await onDeletePages(documento, selectedPages.join(","));
    setCroppingId(null);
    setSelectedPages([]);
    setPreviewVersion((value) => value + 1);
  };

  const startMerge = (documento: DocumentoExpediente) => {
    const candidatos = documentos.filter((item) => item.id && item.id !== documento.id);
    if (candidatos.length === 0) {
      alert("No hay otros documentos del tramite para juntar.");
      return;
    }
    setMergingId(documento.id ?? null);
    setSelectedDocumentIds([]);
  };

  const toggleDocument = (documentoId: number) => {
    setSelectedDocumentIds((current) =>
      current.includes(documentoId) ? current.filter((id) => id !== documentoId) : [...current, documentoId],
    );
  };

  const applyMerge = async (documento: DocumentoExpediente) => {
    if (selectedDocumentIds.length === 0) {
      alert("Selecciona al menos un documento para juntar.");
      return;
    }
    const confirmed = await confirm({
      title: "Juntar documentos",
      description: `Se uniran ${selectedDocumentIds.length + 1} documentos en ${documento.nombreOriginal || documento.nombre}.`,
      confirmLabel: "Juntar",
    });
    if (!confirmed) return;
    await onMergeDocuments(
      documento,
      selectedDocumentIds,
      documento.tipo,
      uppercaseInput(nombreSinExtension(documento.nombreOriginal || documento.nombre)),
      documento.operacionId ?? null,
    );
    setMergingId(null);
    setSelectedDocumentIds([]);
    setPreviewVersion((value) => value + 1);
  };

  const startExtract = (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    setExtractingId(documento.id);
    setSelectedExtractPages([]);
    setExtractType(documento.tipo || "OTROS");
    setExtractOperationId(documento.operacionId ? String(documento.operacionId) : "");
  };

  const toggleExtractPage = (pagina: number) => {
    setSelectedExtractPages((current) =>
      current.includes(pagina) ? current.filter((item) => item !== pagina) : [...current, pagina].sort((a, b) => a - b),
    );
  };

  const applyExtract = async (documento: DocumentoExpediente) => {
    const total = documento.id ? pageCounts[documento.id] ?? 0 : 0;
    if (selectedExtractPages.length === 0) {
      alert("Selecciona al menos una pagina.");
      return;
    }
    if (selectedExtractPages.length >= total) {
      alert("No se pueden separar todas las paginas del documento.");
      return;
    }
    await onExtractPages(
      documento,
      selectedExtractPages.join(","),
      extractType,
      extractOperationId ? Number(extractOperationId) : null,
    );
    setExtractingId(null);
    setSelectedExtractPages([]);
    setPreviewVersion((value) => value + 1);
  };

  return (
    <div className="exp-modal" role="dialog" aria-modal="true" aria-label="Revision OCR">
      <button className="exp-modal__backdrop" type="button" onClick={onClose} />
      <section className="exp-modal__panel">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Revision documental</p>
            <h3>{title}</h3>
          </div>
          <button className="icon-button" type="button" onClick={onClose} title="Cerrar">
            <X size={16} />
          </button>
        </div>

        {documentos.length > 0 ? (
          <div className="ocr-review-list">
            {documentos.map((documento) => (
              <article className="ocr-review-card" key={documento.id ?? documento.nombre}>
                <div className="ocr-review-card__preview">
                  {documento.id ? (
                    <iframe
                      src={`/documentos/ver/${documento.id}?v=${previewVersion}#toolbar=0&navpanes=0&scrollbar=0`}
                      title={documento.nombreOriginal || documento.nombre}
                    />
                  ) : null}
                </div>
                <div className="ocr-review-card__body">
                  {editingId === documento.id ? (
                    <div className="ocr-edit-form">
                      <label>
                        Tipo documental
                        <select value={editType} onChange={(event) => setEditType(event.target.value)}>
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
                          <select value={editOperationId} onChange={(event) => setEditOperationId(event.target.value)}>
                            <option value="">Documento general</option>
                            {operaciones.map((operacion) => (
                              <option key={operacion.id} value={operacion.id}>
                                {operacion.label}
                              </option>
                            ))}
                          </select>
                        </label>
                      ) : null}
                      <small>El nombre se generara automaticamente con el formato MATRICULA - TIPO DOCUMENTO.</small>
                    </div>
                  ) : (
                    <div>
                      <strong>{documento.nombreOriginal || documento.nombre}</strong>
                      <span>{formatDocumentType(documento.tipo)}</span>
                      {documento.operacionLabel ? <small>{documento.operacionLabel}</small> : null}
                      <small>Revisa que el tipo documental y las paginas separadas sean correctas.</small>
                    </div>
                  )}

                  <div className="ocr-review-card__actions">
                    <button
                      className="icon-button"
                      disabled={!documento.id}
                      type="button"
                      title="Ver documento"
                      onClick={() => documento.id && window.open(`/documentos/ver/${documento.id}`, "_blank", "noopener,noreferrer")}
                    >
                      <Eye size={16} />
                    </button>
                    <button className="icon-button" disabled={!documento.id} type="button" title="Recortar paginas" onClick={() => startCrop(documento)}>
                      <Scissors size={16} />
                    </button>
                    <button className="icon-button" disabled={!documento.id} type="button" title="Separar paginas" onClick={() => startExtract(documento)}>
                      <CopyPlus size={16} />
                    </button>
                    <button className="icon-button" disabled={!documento.id} type="button" title="Juntar documentos" onClick={() => startMerge(documento)}>
                      <Combine size={16} />
                    </button>
                    {editingId === documento.id ? (
                      <>
                        <button className="soft-button soft-button--compact" type="button" onClick={() => setEditingId(null)}>
                          Cancelar
                        </button>
                        <button className="primary-button primary-button--compact" type="button" onClick={() => saveEdit(documento)}>
                          Guardar
                        </button>
                      </>
                    ) : (
                      <button className="icon-button" disabled={!documento.id} type="button" title="Editar documento" onClick={() => startEdit(documento)}>
                        <Pencil size={16} />
                      </button>
                    )}
                    <button
                      className="icon-button icon-button--danger"
                      disabled={!documento.id}
                      type="button"
                      title="Borrar documento"
                      onClick={() => onDeleteDocument(documento)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                {documento.id && croppingId === documento.id && pageCounts[documento.id] ? (
                  <div className="ocr-page-editor">
                    <div className="ocr-page-editor__heading">
                      <div>
                        <strong>Selecciona las paginas que quieres eliminar</strong>
                        <small>La vista previa se actualizara al aceptar.</small>
                      </div>
                      <div className="ocr-page-editor__actions">
                        <button className="soft-button soft-button--compact" type="button" onClick={() => setCroppingId(null)}>
                          Cancelar
                        </button>
                        <button className="primary-button primary-button--compact" type="button" onClick={() => applyCrop(documento)}>
                          Aceptar recorte
                        </button>
                      </div>
                    </div>
                    <div className="ocr-page-strip">
                      {Array.from({ length: pageCounts[documento.id] }, (_, index) => index + 1).map((pagina) => (
                        <button
                          className={`ocr-page-thumb ${selectedPages.includes(pagina) ? "ocr-page-thumb--selected" : ""}`}
                          key={pagina}
                          type="button"
                          onClick={() => togglePage(pagina)}
                        >
                          <img
                            alt={`Pagina ${pagina}`}
                            src={`/api/documentos/${documento.id}/paginas/${pagina}/preview?v=${previewVersion}`}
                          />
                          <span>Pag. {pagina}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}

                {documento.id && extractingId === documento.id && pageCounts[documento.id] ? (
                  <div className="ocr-page-editor">
                    <div className="ocr-page-editor__heading">
                      <div>
                        <strong>Selecciona las paginas que quieres separar</strong>
                        <small>Se creara un documento nuevo y se quitaran esas paginas del actual.</small>
                      </div>
                      <div className="ocr-page-editor__actions">
                        <button className="soft-button soft-button--compact" type="button" onClick={() => setExtractingId(null)}>
                          Cancelar
                        </button>
                        <button className="primary-button primary-button--compact" type="button" onClick={() => applyExtract(documento)}>
                          Aceptar separacion
                        </button>
                      </div>
                    </div>
                    <div className="ocr-edit-form ocr-edit-form--inline">
                      <label>
                        Tipo del nuevo documento
                        <select value={extractType} onChange={(event) => setExtractType(event.target.value)}>
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
                          <select value={extractOperationId} onChange={(event) => setExtractOperationId(event.target.value)}>
                            <option value="">Documento general</option>
                            {operaciones.map((operacion) => (
                              <option key={operacion.id} value={operacion.id}>
                                {operacion.label}
                              </option>
                            ))}
                          </select>
                        </label>
                      ) : null}
                      <small>El nombre se generara automaticamente con el formato MATRICULA - TIPO DOCUMENTO.</small>
                    </div>
                    <div className="ocr-page-strip">
                      {Array.from({ length: pageCounts[documento.id] }, (_, index) => index + 1).map((pagina) => (
                        <button
                          className={`ocr-page-thumb ${selectedExtractPages.includes(pagina) ? "ocr-page-thumb--selected" : ""}`}
                          key={pagina}
                          type="button"
                          onClick={() => toggleExtractPage(pagina)}
                        >
                          <img
                            alt={`Pagina ${pagina}`}
                            src={`/api/documentos/${documento.id}/paginas/${pagina}/preview?v=${previewVersion}`}
                          />
                          <span>Pag. {pagina}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}

                {documento.id && mergingId === documento.id ? (
                  <div className="ocr-merge-editor">
                    <div className="ocr-page-editor__heading">
                      <div>
                        <strong>Selecciona documentos para juntar con este</strong>
                        <small>El documento actual se conservara y los seleccionados se añadiran al final.</small>
                      </div>
                      <div className="ocr-page-editor__actions">
                        <button className="soft-button soft-button--compact" type="button" onClick={() => setMergingId(null)}>
                          Cancelar
                        </button>
                        <button className="primary-button primary-button--compact" type="button" onClick={() => applyMerge(documento)}>
                          Aceptar union
                        </button>
                      </div>
                    </div>
                    <div className="ocr-merge-list">
                      {documentos
                        .filter((candidate) => candidate.id && candidate.id !== documento.id)
                        .map((candidate) => (
                          <button
                            className={`ocr-merge-option ${candidate.id && selectedDocumentIds.includes(candidate.id) ? "ocr-merge-option--selected" : ""}`}
                            key={candidate.id ?? candidate.nombre}
                            type="button"
                            onClick={() => candidate.id && toggleDocument(candidate.id)}
                          >
                            <span>{candidate.nombreOriginal || candidate.nombre}</span>
                            <small>{formatDocumentType(candidate.tipo)}</small>
                          </button>
                        ))}
                    </div>
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        ) : (
          <p className="exp-empty">No se detectaron documentos separados. Se ha guardado el PDF completo para revision manual.</p>
        )}

        <div className="exp-modal__footer">
          <button className="primary-button" type="button" onClick={onClose}>
            Confirmar revision
          </button>
        </div>
      </section>
      {dialog}
    </div>
  );
}
