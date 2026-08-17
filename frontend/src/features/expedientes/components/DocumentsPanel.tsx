import { AlertTriangle, CheckCircle2, ExternalLink, FilePlus2, FileText, IdCard, Loader2, Pencil, Scissors, Sparkles, Trash2, Upload, UsersRound } from "lucide-react";
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
          <button className="soft-button soft-button--compact" onClick={onOpenUpload} type="button">
            <Upload size={16} />
            Subir documento suelto
          </button>
          <button className="soft-button soft-button--compact" disabled={!hasEditableDocuments} onClick={onOpenReview} type="button">
            <Scissors size={16} />
            Revisar documentos
          </button>
          <button className="soft-button soft-button--compact" onClick={onOpenTemplates} type="button">
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

      <div className="document-table document-table--expediente">
        {documentos.length === 0 ? <div className="document-table__empty">No hay documentos asociados.</div> : null}
        {documentos.map((documento, index) => {
          const canReadIdentity = Boolean(documento.id && (documento.tipo === "DNI" || documento.tipo === "CIF"));
          const canReadRoles = Boolean(documento.id && (documento.tipo === "CONTRATO_COMPRAVENTA" || documento.tipo === "FACTURA"));
          const readingIdentity = Boolean(documento.id && readingIdentityId === documento.id);
          const addingIdentity = Boolean(documento.id && addingIdentityDocumentId === documento.id);
          const readingRoles = Boolean(documento.id && readingRolesId === documento.id);
          return (
            <article className="document-table__row document-table__row--expediente" key={`${documento.tipo}-${documento.id ?? index}`}>
              <FileText aria-hidden="true" size={20} />

              <div className="document-table__main">
                <strong>{documento.nombreOriginal || documento.nombre}</strong>
                <div className="document-table__meta">
                  <span>
                    {formatDocumentType(documento.tipo)}
                    {documento.operacionLabel ? ` · ${documento.operacionLabel}` : ""}
                    {documento.interesadoNombre ? ` · ${documento.interesadoNombre}` : ""}
                  </span>
                  <DocumentIaStatus status={documento.lecturaIa} />
                </div>
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

              <small className="document-table__uploaded">
                {documento.subido ? formatDateTime(documento.fechaSubida) : documento.descripcion || "Pendiente"}
                {documento.subidoPor ? <span>{documento.subidoPor}</span> : null}
              </small>

              <div className="document-table__actions">
                {documento.id ? (
                  <a className="soft-button soft-button--compact" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer">
                    <ExternalLink size={15} />
                    Ver
                  </a>
                ) : null}
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
                <label className="icon-button" title="Subir una nueva version del documento">
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

function DocumentIaStatus({ status }: { status?: DocumentoExpediente["lecturaIa"] }) {
  if (!status) return null;
  const labels: Record<string, string> = {
    SIN_LEER: "Pendiente IA",
    PENDIENTE: "En cola",
    PROCESANDO: "Leyendo",
    COMPLETADO: "Leido",
    REQUIERE_REVISION: "Revisar lectura",
    ERROR: "Error de lectura",
  };
  const active = status.estado === "PENDIENTE" || status.estado === "PROCESANDO";
  const Icon = active ? Loader2 : status.estado === "COMPLETADO" ? CheckCircle2 : status.estado === "ERROR" || status.estado === "REQUIERE_REVISION" ? AlertTriangle : Sparkles;
  return (
    <span className={`document-ia-status document-ia-status--${status.estado.toLowerCase()}`} title={status.mensaje || undefined}>
      <Icon className={active ? "is-spinning" : ""} size={13} aria-hidden="true" />
      {labels[status.estado] || humanizeEnum(status.estado)}
    </span>
  );
}
