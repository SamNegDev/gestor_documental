import { CarFront, Eye, FilePlus2, FileText, IdCard, Loader2, Pencil, Scissors, Trash2, Upload, UserPlus, UsersRound } from "lucide-react";
import type { DocumentoExpediente, DocumentoIdentidadDetectada } from "../types/expedienteDetail.types";
import { formatDateTime, formatDocumentType, humanizeEnum } from "../utils/formatters";

type Props = {
  documentos: DocumentoExpediente[];
  onOpenReview: () => void;
  onOpenTemplates: () => void;
  onOpenUpload: () => void;
  onUploadDocument: (documento: DocumentoExpediente, archivo: File) => void;
  onEditDocument: (documento: DocumentoExpediente) => void;
  onDeleteDocument: (documento: DocumentoExpediente) => void;
  onReadIdentity?: (documento: DocumentoExpediente) => void;
  onReadRoles?: (documento: DocumentoExpediente) => void;
  onUseDetectedIdentity?: (documento: DocumentoExpediente, identidad: DocumentoIdentidadDetectada) => void;
  readingIdentityId?: number | null;
  readingRolesId?: number | null;
};

export function DocumentsPanel({
  documentos,
  onOpenReview,
  onOpenTemplates,
  onOpenUpload,
  onUploadDocument,
  onEditDocument,
  onDeleteDocument,
  onReadIdentity,
  onReadRoles,
  onUseDetectedIdentity,
  readingIdentityId,
  readingRolesId,
}: Props) {
  const pendientesActuales = documentos.filter((documento) => documento.estado === "PENDIENTE" && documento.requeridoAhora);
  const hasEditableDocuments = documentos.some((documento) => documento.id);

  return (
    <section className="exp-panel">
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

      <div className="documents-list">
        {documentos.map((documento, index) => {
          const canReadIdentity = Boolean(documento.id && (documento.tipo === "DNI" || documento.tipo === "CIF"));
          const canReadRoles = Boolean(documento.id && (documento.tipo === "CONTRATO_COMPRAVENTA" || documento.tipo === "FACTURA"));
          const readingIdentity = Boolean(documento.id && readingIdentityId === documento.id);
          const readingRoles = Boolean(documento.id && readingRolesId === documento.id);
          const lectura = documento.lecturaIdentidad;
          const identidadesDetectadas = lectura?.identidadesDetectadas?.length ? lectura.identidadesDetectadas : [];
          const lecturaRoles = documento.lecturaRoles;
          const lecturaVehiculo = documento.lecturaVehiculo;

          return (
            <article className={`document-row document-row--${documento.estado.toLowerCase()}`} key={`${documento.tipo}-${documento.id ?? index}`}>
              <div className="pdf-icon">
                <FileText size={18} />
                <strong>PDF</strong>
              </div>

              <div className="document-row__body">
                <strong>{documento.nombreOriginal || documento.nombre}</strong>
                <span>
                  {formatDocumentType(documento.tipo)}
                  {documento.operacionLabel ? ` · ${documento.operacionLabel}` : ""}
                </span>
                <small>
                  {documento.subido
                    ? `Subido ${formatDateTime(documento.fechaSubida)}${documento.subidoPor ? ` por ${documento.subidoPor}` : ""}`
                    : documento.descripcion || "Documento pendiente"}
                </small>
                {lectura ? (
                  <>
                    <div className={`document-identity ${lectura.requiereRevision ? "document-identity--review" : "document-identity--linked"}`}>
                      <IdCard size={14} />
                      <span>{lectura.identificador || "Sin DNI/CIF"}</span>
                      {lectura.nombreCompleto ? <span>{lectura.nombreCompleto}</span> : null}
                      <em>{lectura.interesadoVinculadoNombre ? `vinculado a ${lectura.interesadoVinculadoNombre}` : "sin vincular"}</em>
                    </div>
                    {identidadesDetectadas.length > 0 ? (
                      <div className="document-detected-identities" aria-label="Identidades detectadas">
                        {identidadesDetectadas.map((identidad, identityIndex) => {
                          const nombre = identidad.razonSocial
                            || identidad.nombreCompleto
                            || [identidad.nombre, identidad.apellido1, identidad.apellido2].filter(Boolean).join(" ");
                          const key = `${identidad.identificador || nombre || "identidad"}-${identityIndex}`;
                          return (
                            <div className="document-detected-identity" key={key}>
                              <div>
                                <strong>{identidad.identificador || "Sin DNI/CIF"}</strong>
                                {nombre ? <span>{nombre}</span> : null}
                              </div>
                              <button
                                className="soft-button soft-button--compact"
                                onClick={() => onUseDetectedIdentity?.(documento, identidad)}
                                type="button"
                              >
                                <UserPlus size={14} />
                                Usar en interesados
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    ) : null}
                  </>
                ) : null}
                {lecturaRoles ? (
                  <div className={`document-roles ${lecturaRoles.requiereRevision ? "document-roles--review" : "document-roles--linked"}`}>
                    <UsersRound size={14} />
                    <span>Vendedor: {lecturaRoles.vendedorNombre || lecturaRoles.vendedorIdentificador || "sin leer"}</span>
                    <span>Comprador: {lecturaRoles.compradorNombre || lecturaRoles.compradorIdentificador || "sin leer"}</span>
                    <em>{lecturaRoles.aplicadoExpediente ? "aplicado al expediente" : lecturaRoles.mensaje || lecturaRoles.motivoAplicacion || "roles leidos"}</em>
                  </div>
                ) : null}
                {lecturaVehiculo ? (
                  <div className={`document-roles ${lecturaVehiculo.requiereRevision ? "document-roles--review" : "document-roles--linked"}`}>
                    <CarFront size={14} />
                    <span>{[lecturaVehiculo.marca, lecturaVehiculo.modeloVehiculo].filter(Boolean).join(" ") || "Vehiculo sin marca/modelo"}</span>
                    <span>{[lecturaVehiculo.matricula, lecturaVehiculo.bastidor].filter(Boolean).join(" - ") || "Sin matricula/bastidor"}</span>
                    <em>{lecturaVehiculo.mensaje || "vehiculo leido"}</em>
                  </div>
                ) : null}
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
                {canReadIdentity ? (
                  <button
                    className="icon-button"
                    disabled={readingIdentity}
                    onClick={() => onReadIdentity?.(documento)}
                    title={lectura ? "Releer identidad" : "Leer identidad"}
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
                    title={lecturaRoles ? "Releer roles" : "Leer roles"}
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
