import { CheckCircle2, CircleDashed, FileText, Link2, Plus, Slash, Upload, X } from "lucide-react";
import { useState } from "react";
import type { DocumentoExpediente, InteresadoExpediente, RequisitoDocumental } from "../types/expedienteDetail.types";
import { formatDocumentType, humanizeEnum } from "../utils/formatters";
import { uppercaseInput } from "../../../shared/utils/text";

type Props = {
  requisitos: RequisitoDocumental[];
  documentos: DocumentoExpediente[];
  interesados: InteresadoExpediente[];
  onAddRequirement: (input: {
    tipoDocumento: string;
    descripcion: string;
    interesadoId?: number | null;
    rolInteresado?: string | null;
    estadoInicial: "REQUERIDO" | "POSTERIOR";
  }) => void;
  onLinkRequirementDocument: (requisito: RequisitoDocumental, documentoId: number) => void;
  onOmitRequirement: (requisito: RequisitoDocumental, motivo: string) => void;
  onUploadRequirement: (requisito: RequisitoDocumental, archivo: File) => void;
};

const DOCUMENT_TYPES = [
  "DNI",
  "CIF",
  "CONTRATO_COMPRAVENTA",
  "PERMISO_CIRCULACION",
  "FICHA_TECNICA",
  "MANDATO",
  "FACTURA",
  "EXPEDIENTE_COMPLETO",
  "COMPROBANTE_DGT",
  "MODELO_620",
  "OTROS",
];

const OMIT_REASONS = ["No aplica para este tramite", "Ya cubierto por otro documento"];

function RequirementIcon({ estado }: { estado: RequisitoDocumental["estado"] }) {
  if (estado === "APORTADO") return <CheckCircle2 size={16} />;
  if (estado === "OMITIDO") return <Slash size={16} />;
  return <CircleDashed size={16} />;
}

export function DocumentRequirementsPanel({
  requisitos,
  documentos,
  interesados,
  onAddRequirement,
  onLinkRequirementDocument,
  onOmitRequirement,
  onUploadRequirement,
}: Props) {
  const [adding, setAdding] = useState(false);
  const [tipoDocumento, setTipoDocumento] = useState("DNI");
  const [descripcion, setDescripcion] = useState("");
  const [estadoInicial, setEstadoInicial] = useState<"REQUERIDO" | "POSTERIOR">("REQUERIDO");
  const [interesadoKey, setInteresadoKey] = useState("");
  const [linkingRequirement, setLinkingRequirement] = useState<RequisitoDocumental | null>(null);
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null);
  const [omittingRequirement, setOmittingRequirement] = useState<RequisitoDocumental | null>(null);
  const [omitReason, setOmitReason] = useState(OMIT_REASONS[0]);
  const requisitosPendientes = requisitos.filter((requisito) => requisito.estado === "REQUERIDO");
  const pendientes = requisitosPendientes.length;
  const documentosSubidos = documentos.filter((documento) => documento.id);

  const submitAdd = () => {
    const descripcionLimpia = uppercaseInput(descripcion.trim());
    if (!descripcionLimpia) return;
    const [interesadoId, rolInteresado] = interesadoKey ? interesadoKey.split(":") : [];
    onAddRequirement({
      tipoDocumento,
      descripcion: descripcionLimpia,
      interesadoId: interesadoId ? Number(interesadoId) : null,
      rolInteresado: rolInteresado || null,
      estadoInicial,
    });
    setDescripcion("");
    setInteresadoKey("");
    setEstadoInicial("REQUERIDO");
    setAdding(false);
  };

  const omitRequirement = (requisito: RequisitoDocumental) => {
    setOmitReason(OMIT_REASONS[0]);
    setOmittingRequirement(requisito);
  };

  const confirmOmitRequirement = () => {
    if (!omittingRequirement || !omitReason.trim()) return;
    onOmitRequirement(omittingRequirement, uppercaseInput(omitReason.trim()));
    setOmittingRequirement(null);
    setOmitReason(OMIT_REASONS[0]);
  };

  const linkDocument = (requisito: RequisitoDocumental) => {
    if (documentosSubidos.length === 0) {
      alert("No hay documentos subidos para vincular.");
      return;
    }
    setSelectedDocumentId(null);
    setLinkingRequirement(requisito);
  };

  const confirmLinkDocument = () => {
    if (!linkingRequirement || !selectedDocumentId) return;
    onLinkRequirementDocument(linkingRequirement, selectedDocumentId);
    setLinkingRequirement(null);
    setSelectedDocumentId(null);
  };

  return (
    <section className="exp-panel exp-panel--compact">
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Checklist</p>
          <h3>Requisitos documentales</h3>
        </div>
        <div className="requirement-heading-actions">
          <button className="soft-button soft-button--compact" type="button" onClick={() => setAdding((value) => !value)}>
            <Plus size={15} />
            Añadir
          </button>
          <span className="exp-panel__counter">{pendientes}</span>
        </div>
      </div>
      {adding ? (
        <div className="requirement-form">
          <select value={tipoDocumento} onChange={(event) => setTipoDocumento(event.target.value)}>
            {DOCUMENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {formatDocumentType(type)}
              </option>
            ))}
          </select>
          <input
            value={descripcion}
            placeholder="Descripcion"
            onChange={(event) => setDescripcion(uppercaseInput(event.target.value))}
          />
          <select value={interesadoKey} onChange={(event) => setInteresadoKey(event.target.value)}>
            <option value="">Sin interesado</option>
            {interesados.map((interesado) => (
              <option key={`${interesado.id}:${interesado.rol ?? ""}`} value={`${interesado.id}:${interesado.rol ?? ""}`}>
                {interesado.nombre} {interesado.rol ? `- ${humanizeEnum(interesado.rol)}` : ""}
              </option>
            ))}
          </select>
          <select value={estadoInicial} onChange={(event) => setEstadoInicial(event.target.value as "REQUERIDO" | "POSTERIOR")}>
            <option value="REQUERIDO">Requerido</option>
            <option value="POSTERIOR">Posterior</option>
          </select>
          <button className="primary-button" type="button" onClick={submitAdd}>
            Guardar
          </button>
        </div>
      ) : null}

      <div className="requirements-list">
        {requisitosPendientes.map((requisito) => (
          <article className={`requirement-row requirement-row--${requisito.estado.toLowerCase()}`} key={requisito.id}>
            <div className="requirement-row__icon">
              <RequirementIcon estado={requisito.estado} />
            </div>
            <div className="requirement-row__body">
              <strong>{requisito.descripcion}</strong>
              <small>
                {formatDocumentType(requisito.tipoDocumento)}
                {requisito.interesadoNombre ? ` · ${requisito.interesadoNombre}` : ""}
                {requisito.documentoNombre ? ` · ${requisito.documentoNombre}` : ""}
              </small>
            </div>
            <span className="document-state">{humanizeEnum(requisito.estado)}</span>
            <div className="requirement-row__actions">
              <button className="icon-button" type="button" title="Vincular documento" onClick={() => linkDocument(requisito)}>
                <Link2 size={16} />
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
                    if (file) onUploadRequirement(requisito, file);
                  }}
                />
              </label>
              <button className="icon-button" type="button" title="Omitir requisito" onClick={() => omitRequirement(requisito)}>
                <Slash size={16} />
              </button>
            </div>
          </article>
        ))}
      </div>

      {requisitosPendientes.length === 0 ? (
        <p className="exp-empty">
          <FileText size={16} /> No hay documentos pendientes de aportar.
        </p>
      ) : null}

      {linkingRequirement ? (
        <div className="exp-modal" role="presentation">
          <button
            className="exp-modal__backdrop"
            onClick={() => setLinkingRequirement(null)}
            type="button"
            aria-label="Cerrar vinculacion"
          />
          <section aria-labelledby="requirement-link-title" aria-modal="true" className="exp-modal__panel" role="dialog">
            <div className="exp-modal__header">
              <div>
                <p className="eyebrow">Vincular documento</p>
                <h3 id="requirement-link-title">{linkingRequirement.descripcion}</h3>
              </div>
              <button aria-label="Cerrar" className="icon-button" onClick={() => setLinkingRequirement(null)} type="button">
                <X size={16} />
              </button>
            </div>

            <div className="requirement-link-summary">
              <strong>{formatDocumentType(linkingRequirement.tipoDocumento)}</strong>
              <span>
                {linkingRequirement.interesadoNombre || "Sin interesado asociado"}
                {linkingRequirement.rolInteresado ? ` · ${humanizeEnum(linkingRequirement.rolInteresado)}` : ""}
              </span>
            </div>

            <div className="requirement-document-picker">
              {documentosSubidos.map((documento) => (
                <button
                  className={`requirement-document-option ${documento.id === selectedDocumentId ? "requirement-document-option--selected" : ""}`}
                  key={documento.id || documento.nombre}
                  onClick={() => documento.id && setSelectedDocumentId(documento.id)}
                  type="button"
                >
                  <FileText size={18} />
                  <span>{documento.nombreOriginal || documento.nombre}</span>
                  <small>{formatDocumentType(documento.tipo)}{documento.subidoPor ? ` · ${documento.subidoPor}` : ""}</small>
                </button>
              ))}
            </div>

            <footer className="exp-modal__footer">
              <button className="soft-button" onClick={() => setLinkingRequirement(null)} type="button">
                Cancelar
              </button>
              <button className="primary-button" disabled={!selectedDocumentId} onClick={confirmLinkDocument} type="button">
                <Link2 size={16} />
                Vincular documento
              </button>
            </footer>
          </section>
        </div>
      ) : null}

      {omittingRequirement ? (
        <div className="exp-modal" role="presentation">
          <button
            className="exp-modal__backdrop"
            onClick={() => setOmittingRequirement(null)}
            type="button"
            aria-label="Cerrar omision"
          />
          <section aria-labelledby="requirement-omit-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--narrow" role="dialog">
            <div className="exp-modal__header">
              <div>
                <p className="eyebrow">Omitir requisito</p>
                <h3 id="requirement-omit-title">{omittingRequirement.descripcion}</h3>
              </div>
              <button aria-label="Cerrar" className="icon-button" onClick={() => setOmittingRequirement(null)} type="button">
                <X size={16} />
              </button>
            </div>

            <div className="requirement-link-summary">
              <strong>{formatDocumentType(omittingRequirement.tipoDocumento)}</strong>
              <span>
                {omittingRequirement.interesadoNombre || "Sin interesado asociado"}
                {omittingRequirement.rolInteresado ? ` Â· ${humanizeEnum(omittingRequirement.rolInteresado)}` : ""}
              </span>
            </div>

            <div className="requirement-omit-form">
              <div className="requirement-omit-reasons" role="group" aria-label="Motivos frecuentes">
                {OMIT_REASONS.map((reason) => (
                  <button
                    className={`requirement-omit-reason ${omitReason === reason ? "requirement-omit-reason--selected" : ""}`}
                    key={reason}
                    onClick={() => setOmitReason(reason)}
                    type="button"
                  >
                    {reason}
                  </button>
                ))}
              </div>
              <label>
                Motivo
                <textarea
                  onChange={(event) => setOmitReason(uppercaseInput(event.target.value))}
                  rows={3}
                  value={omitReason}
                />
              </label>
            </div>

            <footer className="exp-modal__footer">
              <button className="soft-button" onClick={() => setOmittingRequirement(null)} type="button">
                Cancelar
              </button>
              <button className="primary-button primary-button--danger" disabled={!omitReason.trim()} onClick={confirmOmitRequirement} type="button">
                <Slash size={16} />
                Omitir requisito
              </button>
            </footer>
          </section>
        </div>
      ) : null}
    </section>
  );
}
