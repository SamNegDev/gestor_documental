import { Eye, FilePlus2, FileText, IdCard, Loader2, Pencil, Scissors, Trash2, Upload, UsersRound } from "lucide-react";
import { DocumentReadingPanel, type DocumentReadingExistingIdentity } from "./DocumentReadingPanel";
import { useDocumentDropZone } from "./useDocumentDropZone";
import type { DocumentoExpediente, DocumentoIdentidadDetectada } from "../types/expedienteDetail.types";
import { formatDateTime, formatDocumentType, humanizeEnum } from "../utils/formatters";

type Props = {
  documentos: DocumentoExpediente[];
  onOpenReview: () => void;
  onOpenTemplates: () => void;
  onOpenUpload: () => void;
  onDropStandaloneDocument?: (archivo: File) => void;
  onUploadDocument: (documento: DocumentoExpediente, archivo: File) => void;
  onEditDocument: (documento: DocumentoExpediente) => void;
  onDeleteDocument: (documento: DocumentoExpediente) => void;
  onReadIdentity?: (documento: DocumentoExpediente) => void;
  onReadRoles?: (documento: DocumentoExpediente) => void;
  onUseDetectedIdentity?: (documento: DocumentoExpediente, identidad: DocumentoIdentidadDetectada, rol: string, identificador: string, nombreCompleto: string) => void;
  existingIdentities?: DocumentReadingExistingIdentity[];
  addingIdentityDocumentId?: number | null;
  readingIdentityId?: number | null;
  readingRolesId?: number | null;
};

export function DocumentsPanel({
  documentos,
  onOpenReview,
  onOpenTemplates,
  onOpenUpload,
  onDropStandaloneDocument,
  onUploadDocument,
  onEditDocument,
  onDeleteDocument,
  onReadIdentity,
  onReadRoles,
  onUseDetectedIdentity,
  existingIdentities = [],
  addingIdentityDocumentId,
  readingIdentityId,
  readingRolesId,
}: Props) {
  const pendientesActuales = documentos.filter((documento) => documento.estado === "PENDIENTE" && documento.requeridoAhora);
  const hasEditableDocuments = documentos.some((documento) => documento.id);
  const { draggingDocument, dropZoneHandlers } = useDocumentDropZone({
    enabled: Boolean(onDropStandaloneDocument),
    onDropFile: (archivo) => onDropStandaloneDocument?.(archivo),
  });

  return (
    <section
      className={`exp-panel exp-panel--documents-drop${draggingDocument ? " is-dragging-document" : ""}`}
      {...dropZoneHandlers}
    >
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Documentación</p>
          <h3>Documentos del expediente</h3>
        </div>
        <div className="exp-panel__heading-actions">
          <button className="soft-button" onClick={onOpenUpload} type="button">
            <Upload size={16} />
            Subir documento suelto
          </button>
          <button className="soft-button" disabled={!hasEditableDocuments} onClick={onOpenReview} type="button">
            <Scissors size={16} />
            Revisar documentos
          </button>
          <button className="primary-button" onClick={onOpenTemplates} type="button">
            <FilePlus2 size={16} />
            Preparar PDF
          </button>
        </div>
      </div>

      {pendientesActuales.length > 0 ? (
        <div className="documents-warning">
          Faltan {pendientesActuales.length} documento(s) requerido(s) para completar la fase actual.
        </div>
      ) : null}

      <div className="documents-drop-hint" aria-hidden={!draggingDocument}>
        <Upload size={18} />
        <span>Suelta el archivo para elegir el tipo documental</span>
      </div>

      <div className="documents-list">
        {documentos.map((documento, index) => {
          const canReadIdentity = Boolean(documento.id && (documento.tipo === "DNI" || documento.tipo === "CIF"));
          const canReadRoles = Boolean(documento.id && (documento.tipo === "CONTRATO_COMPRAVENTA" || documento.tipo === "FACTURA"));
          const readingIdentity = Boolean(documento.id && readingIdentityId === documento.id);
          const addingIdentity = Boolean(documento.id && addingIdentityDocumentId === documento.id);
          const readingRoles = Boolean(documento.id && readingRolesId === documento.id);
          return (
            <article className={`document-row document-row--${documento.estado.toLowerCase()}`} key={`${documento.tipo}-${documento.id ?? index}`}>
              <div className="pdf-icon">
                <FileText size={18} />
                <strong>PDF</strong>
              </div>

              <div className="document-row__body">
                <div className="document-row__heading">
                  <strong>{documento.nombreOriginal || documento.nombre}</strong>
                  <span className="document-state">{humanizeEnum(documento.estado)}</span>
                </div>
                <span>
                  {formatDocumentType(documento.tipo)}
                  {documento.operacionLabel ? ` · ${documento.operacionLabel}` : ""}
                </span>
                <small>
                  {documento.subido
                    ? `Subido ${formatDateTime(documento.fechaSubida)}${documento.subidoPor ? ` por ${documento.subidoPor}` : ""}`
                    : documento.descripcion || "Documento pendiente"}
                </small>
                <DocumentReadingPanel
                  addingIdentity={addingIdentity}
                  canAddIdentity={Boolean(documento.lecturaIdentidad)}
                  canRereadIdentity={canReadIdentity}
                  documento={documento}
                  existingIdentities={existingIdentities}
                  rereadingIdentity={readingIdentity}
                  onAddIdentity={onUseDetectedIdentity}
                  onRereadIdentity={onReadIdentity}
                />
              </div>

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
                {canReadIdentity ? (
                  <button
                    className="icon-button"
                    disabled={readingIdentity}
                    onClick={() => onReadIdentity?.(documento)}
                    title={documento.lecturaIdentidad ? "Releer identidad" : "Leer identidad"}
                    type="button"
                  >
                    {readingIdentity ? <Loader2 className="document-row__identity-spinner" size={16} /> : <IdCard size={16} />}
                  </button>
                ) : null}
                {canReadRoles ? (
                  <button
                    className="icon-button"
                    disabled={readingRoles}
                    onClick={() => onReadRoles?.(documento)}
                    title={documento.lecturaRoles ? "Releer roles" : "Leer roles"}
                    type="button"
                  >
                    {readingRoles ? <Loader2 className="document-row__identity-spinner" size={16} /> : <UsersRound size={16} />}
                  </button>
                ) : null}
                <label className="icon-button" title="Subir este documento">
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
          );
        })}
      </div>
    </section>
  );
}
