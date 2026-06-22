import { useCallback, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { AlertCircle, AlertTriangle, CalendarClock, ClipboardCheck, Download, FilePlus2, FileText, Loader2, MessageCircle, Plus, RefreshCw, Route, Save, ShieldAlert, ShieldCheck, Trash2, Upload, UserRound, X } from "lucide-react";
import { CompleteExpedienteUploadPanel } from "../components/CompleteExpedienteUploadPanel";
import { DocumentChecklistDialog } from "../components/DocumentChecklistDialog";
import { DocumentEditDialog, type DocumentEditSubmit } from "../components/DocumentEditDialog";
import { DocumentRequirementsPanel } from "../components/DocumentRequirementsPanel";
import { DocumentTemplateDialog } from "../components/DocumentTemplateDialog";
import { DocumentsPanel } from "../components/DocumentsPanel";
import { ExpedienteHeader } from "../components/ExpedienteHeader";
import { IncidentAlertPanel } from "../components/IncidentAlertPanel";
import { IncidentCreateDialog } from "../components/IncidentCreateDialog";
import { IncidentResolutionDialog } from "../components/IncidentResolutionDialog";
import { InteresadosPanel } from "../components/InteresadosPanel";
import { InteresadoAutocomplete } from "../components/InteresadoAutocomplete";
import { NextActionPanel } from "../components/NextActionPanel";
import { OcrReviewDialog } from "../components/OcrReviewDialog";
import { PhaseMilestonesPanel } from "../components/PhaseMilestonesPanel";
import { SecondaryExpedienteTabs } from "../components/SecondaryExpedienteTabs";
import { applyDocumentRoles, deleteDocument, deleteDocumentPages, extractDocumentPages, mergeDocuments, readDocumentIdentity, readDocumentRoles, updateDocument, uploadExpedienteDocument } from "../services/documentosApi";
import {
  completeExpedienteMilestone,
  finishExpediente,
  getExpedienteDetail,
  getIncidentTypes,
  linkIncidentDocument,
  markExpedienteMessagesRead,
  openExpedienteIncident,
  reclaimIncident,
  requestAdditionalInfo,
  resolveAdditionalInfo,
  resolveIncident,
  rollbackExpedienteFinalization,
  rollbackExpedienteMilestone,
  sendExpedienteMessage,
  updateExpedienteFromExistingDocuments,
  updateExpedienteInteresados,
  uploadIncidentDocument,
} from "../services/expedienteDetailApi";
import {
  createRequirement,
  linkRequirementDocument,
  omitRequirement,
  uploadRequirementDocument,
  type CreateRequirementInput,
} from "../services/requisitosApi";
import { ApiError } from "../../../shared/api/http";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { AddressFields, type AddressValue } from "../../../shared/ui/AddressFields";
import { uppercaseInput } from "../../../shared/utils/text";
import type {
  DocumentoExpediente,
  ExpedienteDetail,
  ExpedienteEditInput,
  HitoAccion,
  HitoExpediente,
  IncidenciaExpediente,
  InteresadoSearchResult,
  OperacionExpediente,
  RequisitoDocumental,
  TipoIncidencia,
} from "../types/expedienteDetail.types";
import "../styles/expedienteDetail.css";

const CLOSING_DOCUMENTS = [
  {
    tipo: "COMPROBANTE_DGT",
    aliases: ["COMPROBANTE_DGT", "HUELLA_TRAMITE"],
    title: "Comprobante DGT",
    description: "Justificante final emitido por la DGT.",
    agency: "DGT",
    logoSrc: "/assets/logos/logo-dgt.png",
    logoAlt: "Logotipo DGT",
  },
  {
    tipo: "MODELO_620",
    aliases: ["MODELO_620"],
    title: "Modelo 620",
    description: "Modelo 620 presentado.",
    agency: "Agencia Tributaria Canaria",
    logoSrc: "/assets/logos/logo-atc.svg",
    logoAlt: "Logotipo Agencia Tributaria Canaria",
  },
] as const;

const INTERESADO_ROLES = ["COMPRADOR", "VENDEDOR", "COMPRAVENTA", "TITULAR"];

type InteresadoCorrection = ExpedienteEditInput["interesados"][number];

function emptyInteresadoCorrection(): InteresadoCorrection {
  return { nombre: "", dni: "", telefono: "", direccion: "", tipoVia: "", nombreVia: "", codigoPostal: "", municipio: "", provincia: "", rol: "" };
}

function hasInteresadoCorrectionData(interesado: InteresadoCorrection) {
  return Boolean(
    interesado.nombre?.trim()
      || interesado.dni?.trim()
      || interesado.telefono?.trim()
      || interesado.direccion?.trim()
      || interesado.tipoVia?.trim()
      || interesado.nombreVia?.trim()
      || interesado.codigoPostal?.trim()
      || interesado.municipio?.trim()
      || interesado.provincia?.trim()
      || interesado.rol?.trim(),
  );
}

function formatShortDate(value?: string | null) {
  if (!value) return "Sin fecha";
  return new Intl.DateTimeFormat("es-ES", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(value));
}

function hasClosingDocuments(documentos: DocumentoExpediente[]) {
  return CLOSING_DOCUMENTS.every((item) =>
    documentos.some((documento) => item.aliases.some((alias) => alias === documento.tipo) && documento.subido && documento.id),
  );
}

function getPhaseSummary(expediente: ExpedienteDetail) {
  const completed = expediente.hitos.filter((hito) => hito.completado).length;
  const active = expediente.hitos.filter((hito) => hito.estado === "ACTUAL").length;
  const blocked = expediente.hitos.filter((hito) => hito.bloqueado).length;
  const uploaded = expediente.documentos.filter((documento) => documento.subido).length;
  const requiredNow = expediente.requisitosDocumentales.filter((requisito) => requisito.estado === "REQUERIDO").length;
  const activeIncidents = expediente.incidencias.filter((incidencia) => !incidencia.resuelta).length;

  return { completed, active, blocked, uploaded, requiredNow, activeIncidents };
}

function ProcessFlowPanel({ expediente }: { expediente: ExpedienteDetail }) {
  const isClosed = expediente.estado === "FINALIZADO" || expediente.estado === "RECHAZADO";
  const closingDocumentsReady = hasClosingDocuments(expediente.documentos);
  const hasIncident = expediente.estado === "INCIDENCIA" || expediente.estado === "REVISANDO_INCIDENCIAS";
  const hasAdditionalInfo = expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL" || expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA";
  const hasPendingDocumentation = expediente.estado === "PENDIENTE_DOCUMENTACION";
  const docsReady = expediente.hitos.some((hito) => hito.id === "documentacion-completa" && hito.completado);
  const managementReady = expediente.hitos.some((hito) => hito.id === "tramite-programa-gestion" && hito.completado);
  const model620Ready = expediente.hitos.some((hito) => hito.id === "modelo-620-presentado" && hito.completado);
  const sentDgt = expediente.estado === "ENVIADO_DGT" || expediente.hitos.some((hito) => hito.id === "finalizado-incidencia" && hito.titulo === "Enviado a DGT");
  const processPaused = hasIncident || hasAdditionalInfo || hasPendingDocumentation;

  const stages = [
    {
      id: "inicio",
      title: "Tramite creado",
      description: "Solicitud revisada y tramite abierto.",
      state: "Completado",
      tone: "done",
    },
    {
      id: "preparacion",
      title: hasIncident ? "Incidencia" : hasAdditionalInfo ? "Informacion" : hasPendingDocumentation ? "Documentacion solicitada" : docsReady ? "Documentacion comprobada" : "Comprobacion de documentacion",
      description: hasIncident ? "Subsanacion o revision pendiente." : hasAdditionalInfo ? "Pendiente de respuesta o revision." : hasPendingDocumentation ? "Esperando los documentos requeridos." : docsReady ? "Documentacion base comprobada." : "Revisando documentos base.",
      state: processPaused ? "Fase actual" : docsReady || isClosed ? "Completado" : "Fase actual",
      tone: processPaused ? "active" : docsReady || isClosed ? "done" : "active",
    },
    {
      id: "programa-gestion",
      title: managementReady ? "Tramite subido" : "Pendiente de subir",
      description: managementReady ? "Cargado en el programa de gestion." : "Pendiente de subir al programa de gestion.",
      state: managementReady || isClosed ? "Completado" : docsReady && !processPaused ? "Fase actual" : "Pendiente",
      tone: managementReady || isClosed ? "done" : docsReady && !processPaused ? "active" : "pending",
    },
    {
      id: "impuesto-620",
      title: model620Ready ? "Impuesto 620 presentado" : "Pendiente de pasar impuesto 620",
      description: model620Ready ? "Presentacion fiscal completada." : "A la espera de pasar el impuesto 620.",
      state: model620Ready || isClosed ? "Completado" : managementReady && !processPaused ? "Fase actual" : "Pendiente",
      tone: model620Ready || isClosed ? "done" : managementReady && !processPaused ? "active" : "pending",
    },
    {
      id: "envio-dgt",
      title: sentDgt ? "Tramite enviado a DGT" : "Pendiente de enviar a DGT",
      description: sentDgt ? "Envio a la DGT completado." : "El envio se habilita al tener el tramite subido.",
      state: sentDgt || isClosed ? "Completado" : managementReady && !processPaused ? "Fase actual" : "Pendiente",
      tone: sentDgt || isClosed ? "done" : managementReady && !processPaused ? "active" : "pending",
    },
    {
      id: "cierre",
      title: isClosed ? "Tramite finalizado" : "Pendiente de cierre",
      description: isClosed && !closingDocumentsReady ? "Pendiente de comprobantes finales." : "Cierre administrativo del tramite.",
      state: isClosed ? (closingDocumentsReady ? "Completado" : "Pendiente comprobantes") : sentDgt && !processPaused ? "Fase actual" : "Pendiente",
      tone: isClosed ? (closingDocumentsReady ? "done" : "active") : sentDgt && !processPaused ? "active" : "pending",
    },
  ];

  return (
    <section className="exp-process-flow" aria-label="Fases del expediente">
      <div className="exp-process-heading">
        <div>
          <p className="eyebrow">Fase de tramitacion</p>
          <h3>{expediente.faseActual || "Fase sin definir"}</h3>
        </div>
        <span className="exp-phase-chip">
          <Route size={15} />
          {isClosed ? "Cerrado" : processPaused ? "Pausado" : "En curso"}
        </span>
      </div>

      <ol className="exp-process-stages">
        {stages.map((stage, index) => (
          <li className={`exp-process-stage exp-process-stage--${stage.tone}`} key={stage.id}>
            <span className="exp-process-stage__marker">{stage.tone === "done" ? <ClipboardCheck size={16} /> : index + 1}</span>
            <div>
              <strong>{stage.title}</strong>
              <p>{stage.description}</p>
              <small>{stage.state}</small>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

function OperationalAside({ expediente }: { expediente: ExpedienteDetail }) {
  const summary = getPhaseSummary(expediente);
  const nextStep = expediente.siguientePaso;

  return (
    <aside className="exp-process-side" aria-label="Resumen operativo">
      <ProcessFlowPanel expediente={expediente} />

      <section className="exp-side-panel exp-side-panel--status">
        <div className="exp-side-panel__heading">
          <ShieldCheck size={18} />
          <h3>Estado operativo</h3>
        </div>
        <p>{nextStep?.nota || nextStep?.descripcion || "Sin acciones pendientes detectadas."}</p>
      </section>

      <section className="exp-side-panel">
        <div className="exp-metric">
          <ClipboardCheck size={18} />
          <span>Hitos completados</span>
          <strong>
            {summary.completed} de {expediente.hitos.length}
          </strong>
        </div>
        <div className="exp-metric">
          <FileText size={18} />
          <span>Documentos subidos</span>
          <strong>{summary.uploaded}</strong>
        </div>
        <div className="exp-metric">
          <AlertCircle size={18} />
          <span>Pendientes ahora</span>
          <strong>{summary.requiredNow}</strong>
        </div>
        <div className="exp-metric">
          <CalendarClock size={18} />
          <span>Incidencias activas</span>
          <strong>{summary.activeIncidents}</strong>
        </div>
      </section>

      <section className="exp-side-panel exp-side-panel--quiet">
        <div className="exp-side-panel__heading">
          <UserRound size={18} />
          <h3>Responsables</h3>
        </div>
        <dl className="summary-list">
          <div>
            <dt>Cliente</dt>
            <dd>{expediente.cliente?.nombre || "Sin cliente"}</dd>
          </div>
          <div>
            <dt>Creado por</dt>
            <dd>{expediente.creadoPor?.nombreCompleto || "Sin usuario"}</dd>
          </div>
        </dl>
      </section>
    </aside>
  );
}

function ClosingDocumentsPanel({
  disabledDocumentTypes = new Set(),
  documentos,
  hiddenDocumentTypes = new Set(),
  operationId = null,
  onUploadClosingDocument,
}: {
  disabledDocumentTypes?: Set<string>;
  documentos: DocumentoExpediente[];
  hiddenDocumentTypes?: Set<string>;
  operationId?: number | null;
  onUploadClosingDocument: (tipoDocumento: string, archivo: File) => void;
}) {
  const closingDocuments = CLOSING_DOCUMENTS.filter((item) => !hiddenDocumentTypes.has(item.tipo));
  const documentByType = new Map(
    closingDocuments
      .map((item) => [
        item.tipo,
        documentos.find((documento) =>
          item.aliases.some((alias) => alias === documento.tipo)
          && documento.subido
          && documento.id
          && (operationId == null || documento.operacionId === operationId)
        ),
      ] as const)
      .filter((entry): entry is readonly [typeof CLOSING_DOCUMENTS[number]["tipo"], DocumentoExpediente] => Boolean(entry[1])),
  );
  const missingDocuments = closingDocuments.filter((item) => !documentByType.has(item.tipo) && !disabledDocumentTypes.has(item.tipo));

  return (
    <section className="closure-docs-panel" aria-label="Documentos de cierre">
      <div className="closure-docs-panel__heading">
        <div>
          <p className="eyebrow">Finalizacion</p>
          <h3>Justificantes finales</h3>
          <p>Acceso directo a los comprobantes oficiales del expediente.</p>
        </div>
        <span className={`closure-docs-status ${missingDocuments.length ? "closure-docs-status--warning" : "closure-docs-status--ready"}`}>
          {missingDocuments.length ? `${missingDocuments.length} pendiente${missingDocuments.length > 1 ? "s" : ""}` : "Completo"}
        </span>
      </div>

      {missingDocuments.length ? (
        <div className="closure-docs-warning" role="alert">
          <AlertTriangle size={18} />
          <div>
            <strong>Faltan documentos de cierre.</strong>
            <span>Adjunta {missingDocuments.map((item) => item.title).join(" y ")} para dejar el expediente listo.</span>
          </div>
        </div>
      ) : null}

      <div className="closure-docs-grid">
        {closingDocuments.map((item) => {
          const documento = documentByType.get(item.tipo);
          const ready = Boolean(documento?.id);
          const fileName = documento?.nombreOriginal || documento?.nombre;
          const disabled = disabledDocumentTypes.has(item.tipo);

          return (
            <article className={`closure-doc-card ${ready ? "closure-doc-card--ready" : disabled ? "closure-doc-card--disabled" : "closure-doc-card--missing"}`} key={item.tipo}>
              <div className="closure-doc-card__identity">
                <span className="closure-doc-card__logo">
                  <img src={item.logoSrc} alt={item.logoAlt} loading="lazy" />
                </span>
                <span className="closure-doc-card__agency">{item.agency}</span>
              </div>
              <div className="closure-doc-card__body">
                <strong>
                  <FileText size={16} />
                  {item.title}
                </strong>
                <p>{ready ? fileName : item.description}</p>
                {ready ? (
                  <small>
                    Subido {formatShortDate(documento?.fechaSubida)}
                    {documento?.subidoPor ? ` por ${documento.subidoPor}` : ""}
                  </small>
                ) : disabled ? (
                  <small>Disponible cuando finalice el tramite</small>
                ) : (
                  <small>Pendiente de adjuntar</small>
                )}
              </div>
              <div className="closure-doc-card__actions">
                {ready && documento?.id ? (
                  <>
                    <a className="soft-button soft-button--compact" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer">
                      Ver
                    </a>
                    <a className="primary-button primary-button--compact" href={`/documentos/descargar/${documento.id}`}>
                      <Download size={15} />
                      Descargar
                    </a>
                  </>
                ) : disabled ? (
                  <span className="soft-button soft-button--compact primary-button--disabled">
                    <Upload size={15} />
                    Adjuntar
                  </span>
                ) : (
                  <label className="primary-button primary-button--compact">
                    <Upload size={15} />
                    Adjuntar
                    <input
                      accept=".pdf,.jpg,.jpeg,.png"
                      hidden
                      type="file"
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        event.target.value = "";
                        if (file) onUploadClosingDocument(item.tipo, file);
                      }}
                    />
                  </label>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function OperationTabs({
  activeOperationId,
  operaciones,
  onSelect,
}: {
  activeOperationId: number | null;
  operaciones: OperacionExpediente[];
  onSelect: (id: number) => void;
}) {
  if (operaciones.length <= 1) return null;

  return (
    <section className="operation-tabs-panel" aria-label="Operaciones del expediente">
      <div className="operation-tabs-panel__heading">
        <div>
          <p className="eyebrow">Operaciones</p>
          <h3>BATECOM</h3>
        </div>
        <span>{operaciones.filter((operacion) => operacion.estado === "FINALIZADA").length}/{operaciones.length} finalizadas</span>
      </div>
      <div className="operation-tabs" role="tablist" aria-label="Seleccionar operacion">
        {operaciones.map((operacion) => (
          <button
            aria-selected={activeOperationId === operacion.id}
            className={`operation-tab ${activeOperationId === operacion.id ? "operation-tab--active" : ""}`}
            key={operacion.id}
            onClick={() => onSelect(operacion.id)}
            role="tab"
            type="button"
          >
            <span className={`operation-tab__status operation-tab__status--${operacion.estado.toLowerCase()}`} />
            <strong>{operacion.label}</strong>
            <small>{operacion.bloqueada ? operacion.motivoBloqueo : operacion.descripcion}</small>
          </button>
        ))}
      </div>
    </section>
  );
}

function AdditionalInfoDialog({
  open,
  onClose,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (contenido: string) => void;
}) {
  const [contenido, setContenido] = useState("");

  useEffect(() => {
    if (open) setContenido("");
  }, [open]);

  if (!open) return null;

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar solicitud" />
      <section aria-labelledby="additional-info-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--narrow" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Aviso al cliente</p>
            <h3 id="additional-info-title">Solicitar informacion adicional</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        <div className="incident-form">
          <label>
            Informacion solicitada
            <textarea
              onChange={(event) => setContenido(uppercaseInput(event.target.value))}
              placeholder="Ej. CONFIRMAR PRECIO DE VENTA DEL CONTRATO"
              rows={4}
              value={contenido}
            />
          </label>
        </div>

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">
            Cancelar
          </button>
          <button className="primary-button" disabled={!contenido.trim()} onClick={() => onSubmit(contenido.trim())} type="button">
            <MessageCircle size={16} />
            Enviar solicitud
          </button>
        </footer>
      </section>
    </div>
  );
}

function InterestedPartiesCorrectionDialog({
  expediente,
  saving,
  onClose,
  onSubmit,
}: {
  expediente: ExpedienteDetail;
  saving: boolean;
  onClose: () => void;
  onSubmit: (interesados: InteresadoCorrection[]) => void;
}) {
  const minimumRows = expediente.tipoTramite === "BATECOM" ? 3 : 2;
  const initialRows = Math.max(minimumRows, expediente.interesados.length);
  const [rows, setRows] = useState<InteresadoCorrection[]>(
    Array.from({ length: initialRows }).map((_, index) => {
      const interesado = expediente.interesados[index];
      return interesado
        ? {
            nombre: uppercaseInput(interesado.nombre || ""),
            dni: uppercaseInput(interesado.dni || ""),
            telefono: uppercaseInput(interesado.telefono || ""),
            direccion: uppercaseInput(interesado.direccion || ""),
            tipoVia: uppercaseInput(interesado.tipoVia || ""),
            nombreVia: uppercaseInput(interesado.nombreVia || ""),
            codigoPostal: uppercaseInput(interesado.codigoPostal || ""),
            municipio: uppercaseInput(interesado.municipio || ""),
            provincia: uppercaseInput(interesado.provincia || ""),
            rol: interesado.rol || "",
          }
        : emptyInteresadoCorrection();
    }),
  );

  const updateRow = (index: number, field: keyof InteresadoCorrection, value: string) => {
    setRows((current) => current.map((row, rowIndex) => (rowIndex === index ? { ...row, [field]: uppercaseInput(value) } : row)));
  };

  const selectInteresado = (index: number, interesado: InteresadoSearchResult) => {
    setRows((current) => current.map((row, rowIndex) => (rowIndex === index ? {
      ...row,
      nombre: uppercaseInput(interesado.nombre || ""),
      dni: uppercaseInput(interesado.dni || ""),
      telefono: uppercaseInput(interesado.telefono || ""),
      direccion: uppercaseInput(interesado.direccion || ""),
      tipoVia: uppercaseInput(interesado.tipoVia || ""),
      nombreVia: uppercaseInput(interesado.nombreVia || ""),
      codigoPostal: uppercaseInput(interesado.codigoPostal || ""),
      municipio: uppercaseInput(interesado.municipio || ""),
      provincia: uppercaseInput(interesado.provincia || ""),
    } : row)));
  };

  const updateAddress = (index: number, value: AddressValue) => {
    setRows((current) => current.map((row, rowIndex) => (rowIndex === index ? { ...row, ...value, direccion: "" } : row)));
  };

  const removeRow = (index: number) => {
    if (rows.length <= minimumRows) {
      setRows((current) => current.map((row, rowIndex) => (rowIndex === index ? emptyInteresadoCorrection() : row)));
      return;
    }
    setRows((current) => current.filter((_, rowIndex) => rowIndex !== index));
  };

  return (
    <div className="exp-modal" role="dialog" aria-modal="true" aria-label="Corregir interesados">
      <button className="exp-modal__backdrop" type="button" onClick={onClose} />
      <section className="exp-modal__panel exp-modal__panel--wide">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Correccion administrativa</p>
            <h3>Interesados del expediente</h3>
          </div>
          <button className="icon-button" type="button" onClick={onClose} title="Cerrar">
            <X size={16} />
          </button>
        </div>

        <div className="interesados-correction-note">
          <ShieldCheck size={18} />
          <span>El expediente seguira cerrado. El historial guardara los interesados anteriores y los nuevos.</span>
        </div>

        <div className="interesados-correction-list">
          {rows.map((row, index) => (
            <article className="interesados-correction-row" key={index}>
              <div className="interesados-correction-row__title">
                <strong>Interesado {index + 1}</strong>
                <button className="icon-button icon-button--danger" onClick={() => removeRow(index)} title="Quitar interesado" type="button">
                  <Trash2 size={15} />
                </button>
              </div>
              <InteresadoAutocomplete
                label="Nombre completo/Razon social"
                value={row.nombre || ""}
                placeholder="Buscar o escribir nombre o razon social"
                onChange={(value) => updateRow(index, "nombre", value)}
                onSelect={(interesado) => selectInteresado(index, interesado)}
              />
              <label>
                <span>DNI / CIF</span>
                <input value={row.dni || ""} onChange={(event) => updateRow(index, "dni", event.target.value)} />
              </label>
              <label>
                <span>Rol</span>
                <select value={row.rol || ""} onChange={(event) => updateRow(index, "rol", event.target.value)}>
                  <option value="">Seleccionar rol</option>
                  {INTERESADO_ROLES.map((role) => (
                    <option key={role} value={role}>
                      {role.replaceAll("_", " ")}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Telefono</span>
                <input value={row.telefono || ""} onChange={(event) => updateRow(index, "telefono", event.target.value)} />
              </label>
              <AddressFields
                idPrefix={`expediente-correction-${index}`}
                value={row}
                onChange={(value) => updateAddress(index, value)}
                wideClassName="interesados-correction-row__wide"
              />
            </article>
          ))}
        </div>

        <button className="soft-button soft-button--compact" onClick={() => setRows((current) => [...current, emptyInteresadoCorrection()])} type="button">
          <Plus size={15} />
          Añadir interesado
        </button>

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">
            Cancelar
          </button>
          <button className="primary-button" disabled={saving} onClick={() => onSubmit(rows.filter(hasInteresadoCorrectionData))} type="button">
            <Save size={16} />
            Guardar correccion
          </button>
        </footer>
      </section>
    </div>
  );
}

export function ExpedienteDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [expediente, setExpediente] = useState<ExpedienteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [checklistOpen, setChecklistOpen] = useState(false);
  const [ocrReviewOpen, setOcrReviewOpen] = useState(false);
  const [ocrReviewDocuments, setOcrReviewDocuments] = useState<DocumentoExpediente[]>([]);
  const [completeExpedienteProcessing, setCompleteExpedienteProcessing] = useState(false);
  const [incidentDialogOpen, setIncidentDialogOpen] = useState(false);
  const [additionalInfoDialogOpen, setAdditionalInfoDialogOpen] = useState(false);
  const [interesadosCorrectionOpen, setInteresadosCorrectionOpen] = useState(false);
  const [savingInteresadosCorrection, setSavingInteresadosCorrection] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [requirementOpenSignal, setRequirementOpenSignal] = useState(0);
  const [incidentTypes, setIncidentTypes] = useState<TipoIncidencia[]>([]);
  const [incidentTypesLoading, setIncidentTypesLoading] = useState(false);
  const [resolvingIncident, setResolvingIncident] = useState<IncidenciaExpediente | null>(null);
  const [activeOperationId, setActiveOperationId] = useState<number | null>(null);
  const [editingDocument, setEditingDocument] = useState<DocumentoExpediente | null>(null);
  const [readingIdentityId, setReadingIdentityId] = useState<number | null>(null);
  const [readingRolesId, setReadingRolesId] = useState<number | null>(null);
  const [applyingRolesId, setApplyingRolesId] = useState<number | null>(null);
  const [updatingDocuments, setUpdatingDocuments] = useState(false);
  const { confirm, dialog } = useConfirmDialog();

  const refreshRelatedData = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["expedientes"] }),
      queryClient.invalidateQueries({ queryKey: ["tareas"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      queryClient.invalidateQueries({ queryKey: ["registro"] }),
    ]);
  }, [queryClient]);

  const loadExpediente = useCallback(() => {
    if (!id) return;

    let mounted = true;
    setLoading(true);
    setError(null);

    getExpedienteDetail(id)
      .then((data) => {
        if (!mounted) return;
        setExpediente(data);
        if (!activeOperationId && data.operaciones?.length) {
          setActiveOperationId(data.operaciones[0].id);
        }
      })
      .catch((cause) => {
        if (!mounted) return;
        if (cause instanceof ApiError && cause.status === 401) {
          setError("Sesión caducada. Inicia sesión para cargar el expediente.");
          return;
        }
        if (cause instanceof ApiError && cause.status === 403) {
          setError("No tienes permiso para consultar este expediente.");
          return;
        }
        setError("No se pudo cargar el expediente.");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [activeOperationId, id]);

  useEffect(() => loadExpediente(), [loadExpediente]);

  const refreshExpediente = useCallback(() => {
    if (!id) return Promise.resolve();
    return getExpedienteDetail(id).then(async (data) => {
      setExpediente(data);
      if (!activeOperationId && data.operaciones?.length) {
        setActiveOperationId(data.operaciones[0].id);
      }
      await refreshRelatedData();
      return data;
    });
  }, [activeOperationId, id, refreshRelatedData]);

  const openIncidentDialog = () => {
    setIncidentDialogOpen(true);
    if (incidentTypes.length === 0) {
      setIncidentTypesLoading(true);
      getIncidentTypes()
        .then(setIncidentTypes)
        .catch(() => alert("No se pudieron cargar los tipos de incidencia."))
        .finally(() => setIncidentTypesLoading(false));
    }
  };

  const handleUploadDocument = async (documento: DocumentoExpediente, archivo: File) => {
    if (!expediente) return;
    try {
      await uploadExpedienteDocument(expediente.id, documento.tipo, archivo, activeOperationId);
      await refreshExpediente();
    } catch {
      alert("No se pudo subir el documento.");
    }
  };

  const handleUploadClosingDocument = async (tipoDocumento: string, archivo: File) => {
    if (!expediente) return;
    try {
      await uploadExpedienteDocument(expediente.id, tipoDocumento, archivo, activeOperationId);
      await refreshExpediente();
    } catch {
      alert("No se pudo subir el documento de cierre.");
    }
  };

  const handleUploadRequirement = async (requisito: RequisitoDocumental, archivo: File) => {
    try {
      await uploadRequirementDocument(requisito.id, archivo);
      await refreshExpediente();
    } catch {
      alert("No se pudo subir el documento.");
    }
  };

  const handleReadDocumentIdentity = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    setReadingIdentityId(documento.id);
    try {
      await readDocumentIdentity(documento.id, Boolean(documento.lecturaIdentidad));
      await refreshExpediente();
    } catch (cause) {
      if (cause instanceof ApiError && cause.details) {
        alert(cause.details);
        return;
      }
      alert("No se pudo leer la identidad del documento.");
    } finally {
      setReadingIdentityId(null);
    }
  };

  const handleReadDocumentRoles = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    setReadingRolesId(documento.id);
    try {
      await readDocumentRoles(documento.id, Boolean(documento.lecturaRoles));
      await refreshExpediente();
    } catch (cause) {
      if (cause instanceof ApiError && cause.details) {
        alert(cause.details);
        return;
      }
      alert("No se pudieron leer los roles del documento.");
    } finally {
      setReadingRolesId(null);
    }
  };

  const handleApplyDocumentRoles = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    const confirmed = await confirm({
      title: "Aplicar datos al expediente",
      description: "Se vincularan comprador y vendedor, se guardara la direccion completa leida y se sincronizaran los requisitos documentales.",
      confirmLabel: "Aplicar datos",
      tone: "success",
    });
    if (!confirmed) return;

    setApplyingRolesId(documento.id);
    try {
      await applyDocumentRoles(documento.id);
      await refreshExpediente();
    } catch (cause) {
      if (cause instanceof ApiError && cause.details) {
        alert(cause.details);
        return;
      }
      alert("No se pudieron aplicar los datos al expediente.");
    } finally {
      setApplyingRolesId(null);
    }
  };

  const handleUpdateFromExistingDocuments = async () => {
    if (!expediente || updatingDocuments) return;
    const confirmed = await confirm({
      title: "Actualizar con documentos existentes",
      description: "Se leeran DNI/CIF y contratos/facturas ya subidos, se aplicaran los datos seguros y se sincronizara el checklist documental.",
      confirmLabel: "Actualizar",
    });
    if (!confirmed) return;
    setUpdatingDocuments(true);
    try {
      const result = await updateExpedienteFromExistingDocuments(expediente.id);
      await refreshExpediente();
      const resumen = [
        `${result.identidadesLeidas} identidad${result.identidadesLeidas === 1 ? "" : "es"} leida${result.identidadesLeidas === 1 ? "" : "s"}`,
        `${result.operacionesLeidas} contrato/factura revisado${result.operacionesLeidas === 1 ? "" : "s"}`,
        `${result.datosAplicados} aplicacion${result.datosAplicados === 1 ? "" : "es"} realizada${result.datosAplicados === 1 ? "" : "s"}`,
      ].join(", ");
      const avisos = result.avisos?.length ? `\n\nAvisos:\n${result.avisos.slice(0, 6).join("\n")}` : "";
      alert(`Actualizacion completada: ${resumen}.${avisos}`);
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudo actualizar con los documentos existentes." : "No se pudo actualizar con los documentos existentes.");
    } finally {
      setUpdatingDocuments(false);
    }
  };

  const handleUploadCompleteExpediente = async (archivo: File) => {
    if (!expediente) return;
    const documentosPrevios = new Set(expediente.documentos.map((documento) => documento.id).filter(Boolean));
    setCompleteExpedienteProcessing(true);
    try {
      await uploadExpedienteDocument(expediente.id, "EXPEDIENTE_COMPLETO", archivo);
      const actualizado = await refreshExpediente();
      if (!actualizado) return;
      const nuevos = actualizado.documentos.filter(
        (documento) => documento.id && !documentosPrevios.has(documento.id) && documento.tipo !== "EXPEDIENTE_COMPLETO",
      );
      setOcrReviewDocuments(nuevos.length > 0 ? nuevos : actualizado.documentos.filter((documento) => documento.id));
      setOcrReviewOpen(true);
    } catch {
      alert("No se pudo procesar el expediente completo.");
    } finally {
      setCompleteExpedienteProcessing(false);
    }
  };

  const handleAddRequirement = async (input: CreateRequirementInput) => {
    if (!expediente) return;
    try {
      await createRequirement(expediente.id, input);
      await refreshExpediente();
    } catch {
      alert("No se pudo crear el requisito.");
    }
  };

  const handleOpenDocumentReview = () => {
    if (!expediente) return;
    setOcrReviewDocuments(expediente.documentos.filter((documento) => documento.id));
    setOcrReviewOpen(true);
  };

  const handleCorrectInteresados = async (interesados: InteresadoCorrection[]) => {
    if (!expediente) return;
    setSavingInteresadosCorrection(true);
    try {
      await updateExpedienteInteresados(expediente.id, interesados);
      setInteresadosCorrectionOpen(false);
      await refreshExpediente();
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudieron corregir los interesados." : "No se pudieron corregir los interesados.");
    } finally {
      setSavingInteresadosCorrection(false);
    }
  };

  const handleOmitRequirement = async (requisito: RequisitoDocumental, motivo: string) => {
    try {
      await omitRequirement(requisito.id, motivo);
      await refreshExpediente();
    } catch {
      alert("No se pudo omitir el requisito.");
    }
  };

  const handleLinkRequirementDocument = async (requisito: RequisitoDocumental, documentoId: number) => {
    try {
      await linkRequirementDocument(requisito.id, documentoId);
      await refreshExpediente();
    } catch {
      alert("No se pudo vincular el documento.");
    }
  };

  const handleEditDocument = async (input: DocumentEditSubmit) => {
    if (!editingDocument?.id) return;
    try {
      await updateDocument(
        editingDocument.id,
        input.tipoDocumento,
        input.nombreArchivo,
        input.operacionId,
        input.nombreAutomatico,
      );
      setEditingDocument(null);
      const actualizado = await refreshExpediente();
      if (actualizado) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizado.documentos.find((updated) => updated.id === item.id) ?? item),
        );
      }
    } catch {
      alert("No se pudo editar el documento.");
    }
  };

  const handleSaveOcrDocument = async (
    documento: DocumentoExpediente,
    tipoDocumento: string,
    operacionId?: number | null,
  ) => {
    if (!documento.id) return;
    try {
      await updateDocument(documento.id, tipoDocumento, undefined, operacionId, true);
      const actualizado = await refreshExpediente();
      if (actualizado) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizado.documentos.find((updated) => updated.id === item.id) ?? item),
        );
      }
    } catch {
      alert("No se pudo editar el documento.");
    }
  };

  const handleDeleteOcrPages = async (documento: DocumentoExpediente, rangoPaginas: string) => {
    if (!documento.id) return;
    try {
      await deleteDocumentPages(documento.id, rangoPaginas);
      const actualizado = await refreshExpediente();
      if (actualizado) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizado.documentos.find((updated) => updated.id === item.id) ?? item),
        );
      }
    } catch {
      alert("No se pudieron borrar las paginas.");
    }
  };

  const handleMergeOcrDocuments = async (
    documento: DocumentoExpediente,
    documentoIds: number[],
    tipoDocumento: string,
    nombreSinExtension: string,
    operacionId?: number | null,
  ) => {
    if (!documento.id) return;
    try {
      await mergeDocuments(documento.id, documentoIds, tipoDocumento, nombreSinExtension, operacionId);
      const actualizado = await refreshExpediente();
      if (actualizado) {
        setOcrReviewDocuments((current) =>
          current
            .map((item) => (item.id ? actualizado.documentos.find((updated) => updated.id === item.id) : item))
            .filter((item): item is DocumentoExpediente => Boolean(item)),
        );
      }
    } catch {
      alert("No se pudieron juntar los documentos.");
    }
  };

  const handleExtractOcrPages = async (
    documento: DocumentoExpediente,
    rangoPaginas: string,
    tipoDocumento: string,
    operacionId?: number | null,
  ) => {
    if (!documento.id) return;
    try {
      const previousIds = new Set(expediente?.documentos.map((item) => item.id).filter(Boolean));
      await extractDocumentPages(documento.id, rangoPaginas, tipoDocumento, undefined, operacionId);
      const actualizado = await refreshExpediente();
      if (actualizado) {
        setOcrReviewDocuments((current) => {
          const updatedCurrent = current
            .map((item) => (item.id ? actualizado.documentos.find((updated) => updated.id === item.id) : item))
            .filter((item): item is DocumentoExpediente => Boolean(item));
          const nuevos = actualizado.documentos.filter((item) => item.id && !previousIds.has(item.id));
          return [...updatedCurrent, ...nuevos];
        });
      }
    } catch {
      alert("No se pudieron separar las paginas.");
    }
  };

  const handleRunMilestoneAction = async (hito: HitoExpediente, accion?: HitoAccion) => {
    if (!expediente) return;
    const actionType = accion?.tipo || hito.accion;
    const actionLabel = accion?.label || hito.accionLabel || hito.titulo;
    if (
      (expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL" || expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA")
      && actionType !== "RESOLVER_INFORMACION_ADICIONAL"
    ) {
      alert("Primero debe resolverse la solicitud de informacion adicional.");
      return;
    }
    if (actionType === "ABRIR_INCIDENCIA") {
      openIncidentDialog();
      return;
    }
    if (actionType === "RESOLVER_INFORMACION_ADICIONAL") {
      const awaitingClient = expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL";
      const confirmed = await confirm({
        title: awaitingClient ? "Resolver solicitud de informacion" : "Marcar informacion revisada",
        description: awaitingClient
          ? "Confirma que la informacion se ha obtenido por otra via. El expediente retomara el punto anterior."
          : "La informacion quedara revisada y el expediente retomara el punto anterior.",
        confirmLabel: awaitingClient ? "Resolver solicitud" : "Marcar revisada",
      });
      if (!confirmed) return;
      try {
        await resolveAdditionalInfo(expediente.id);
        await refreshExpediente();
      } catch {
        alert("No se pudo marcar la informacion como revisada.");
      }
      return;
    }
    if (actionType === "RETROCEDER_HITO" || actionType === "RETROCEDER_FINALIZACION") {
      const confirmed = await confirm({
        title: "Retroceder fase",
        description: "Se deshara esta fase y las posteriores que dependan de ella. La documentacion subida no se eliminara.",
        confirmLabel: "Retroceder",
        tone: "danger",
      });
      if (!confirmed) return;
      try {
        if (actionType === "RETROCEDER_FINALIZACION") {
          await rollbackExpedienteFinalization(expediente.id);
        } else {
          await rollbackExpedienteMilestone(expediente.id, accion?.codigoHito || hito.id);
        }
        await refreshExpediente();
      } catch (cause) {
        alert(cause instanceof ApiError ? cause.details || "No se pudo retroceder la fase." : "No se pudo retroceder la fase.");
      }
      return;
    }
    const confirmed = await confirm({
      title: "Confirmar avance",
      description: `Se ejecutara la accion: ${actionLabel}.`,
      confirmLabel: "Avanzar",
      tone: actionType === "FINALIZAR" ? "success" : "default",
    });
    if (!confirmed) return;
    try {
      if (actionType === "FINALIZAR") {
        await finishExpediente(expediente.id);
      } else {
        await completeExpedienteMilestone(expediente.id, accion?.codigoHito || hito.id);
      }
      await refreshExpediente();
    } catch {
      alert("No se pudo avanzar el expediente.");
    }
  };

  const handleCreateIncident = async (tipoIncidenciaId: number, observaciones: string) => {
    if (!expediente) return;
    try {
      await openExpedienteIncident(expediente.id, tipoIncidenciaId, observaciones.trim());
      setIncidentDialogOpen(false);
      await refreshExpediente();
    } catch {
      alert("No se pudo abrir la incidencia.");
    }
  };

  const handleRequestAdditionalInfo = async (contenido: string) => {
    if (!expediente) return;
    try {
      await requestAdditionalInfo(expediente.id, contenido);
      setAdditionalInfoDialogOpen(false);
      await refreshExpediente();
    } catch {
      alert("No se pudo solicitar la informacion adicional.");
    }
  };

  const handleAcceptIncidentResolution = async (incidencia: IncidenciaExpediente) => {
    try {
      await resolveIncident(incidencia.id);
      setResolvingIncident(null);
      await refreshExpediente();
    } catch {
      alert("No se pudo resolver la incidencia.");
    }
  };

  const handleReclaimIncident = async (incidencia: IncidenciaExpediente, observaciones: string) => {
    try {
      await reclaimIncident(incidencia.id, observaciones.trim());
      setResolvingIncident(null);
      await refreshExpediente();
    } catch {
      alert("No se pudo reclamar de nuevo la incidencia.");
    }
  };

  const handleUploadIncidentDocument = async (incidencia: IncidenciaExpediente, archivo: File) => {
    try {
      await uploadIncidentDocument(incidencia.id, archivo);
      const actualizado = await refreshExpediente();
      const incidenciaActualizada = actualizado && "incidencias" in actualizado
        ? actualizado.incidencias.find((item) => item.id === incidencia.id) || null
        : null;
      setResolvingIncident(incidenciaActualizada);
    } catch {
      alert("No se pudo subir el documento de la incidencia.");
    }
  };

  const handleLinkIncidentDocument = async (incidencia: IncidenciaExpediente, documentoId: number) => {
    try {
      await linkIncidentDocument(incidencia.id, documentoId);
      const actualizado = await refreshExpediente();
      const incidenciaActualizada = actualizado && "incidencias" in actualizado
        ? actualizado.incidencias.find((item) => item.id === incidencia.id) || null
        : null;
      setResolvingIncident(incidenciaActualizada);
    } catch {
      alert("No se pudo vincular el documento a la incidencia.");
    }
  };

  const handleSendMessage = async (contenido: string) => {
    if (!expediente) return;
    try {
      await sendExpedienteMessage(expediente.id, contenido);
      await refreshExpediente();
    } catch (cause) {
      if (cause instanceof ApiError) {
        alert(`No se pudo enviar el mensaje. Codigo ${cause.status}.`);
        return;
      }
      alert("No se pudo enviar el mensaje.");
    }
  };

  const handleOpenMessages = async () => {
    if (!expediente || !expediente.mensajesNoLeidos) return;
    try {
      await markExpedienteMessagesRead(expediente.id);
      setExpediente((current) => current ? {
        ...current,
        mensajesNoLeidos: 0,
        mensajes: current.mensajes.map((mensaje) => ({ ...mensaje, noLeidoParaUsuario: false })),
      } : current);
    } catch {
      // El contador se refrescara en la siguiente carga del expediente.
    }
  };

  const handleDeleteDocument = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    const confirmed = await confirm({
      title: "Borrar documento",
      description: `Se eliminara ${documento.nombreOriginal || documento.nombre}. Esta operacion no se puede deshacer.`,
      confirmLabel: "Borrar",
      tone: "danger",
    });
    if (!confirmed) return;

    try {
      await deleteDocument(documento.id);
      await refreshExpediente();
      setOcrReviewDocuments((current) => current.filter((item) => item.id !== documento.id));
    } catch {
      alert("No se pudo borrar el documento.");
    }
  };

  if (loading) {
    return (
      <div className="exp-detail-state">
        <Loader2 className="exp-detail-state__spinner" size={28} />
        <strong>Cargando expediente</strong>
        <span>Estamos recuperando los datos del expediente.</span>
      </div>
    );
  }

  if (error || !expediente) {
    return (
      <div className="exp-detail-state exp-detail-state--error">
        <AlertCircle size={28} />
        <strong>{error || "Expediente no encontrado"}</strong>
        <span>Revisa que el expediente exista y que tu usuario tenga permisos.</span>
      </div>
    );
  }

  const operaciones = expediente.operaciones ?? [];
  const activeOperation = operaciones.find((operacion) => operacion.id === activeOperationId) ?? operaciones[0] ?? null;
  const operationalHitos = activeOperation?.hitos?.length ? activeOperation.hitos : expediente.hitos;
  const hasAdditionalInfoFlow =
    expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL" || expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA";
  const hasDocumentationRequest = expediente.estado === "PENDIENTE_DOCUMENTACION";
  const nextOperationalStep =
    hasAdditionalInfoFlow
      ? expediente.siguientePaso
      : operationalHitos.find((hito) => hito.accion && !hito.completado && !hito.bloqueado) ?? expediente.siguientePaso;
  const hasActiveIncidents = expediente.incidencias.some((incidencia) => !incidencia.resuelta);
  const canRequestAdditionalInfo = expediente.estado !== "FINALIZADO" && expediente.estado !== "RECHAZADO";
  const canOpenIncident = expediente.estado !== "FINALIZADO" && expediente.estado !== "RECHAZADO";
  const model620PhaseCompleted = operationalHitos.some((hito) => hito.id.toLowerCase().includes("modelo-620") && hito.completado);
  const showClosingDocumentsPanel = expediente.estado === "FINALIZADO" || model620PhaseCompleted;
  const disabledClosingDocumentTypes = new Set<string>();
  if (!model620PhaseCompleted && expediente.estado !== "FINALIZADO") {
    disabledClosingDocumentTypes.add("MODELO_620");
  }
  if (expediente.estado !== "FINALIZADO") {
    disabledClosingDocumentTypes.add("COMPROBANTE_DGT");
  }
  const hiddenClosingDocumentTypes = new Set<string>();
  if (expediente.tipoTramite === "NOTIFICACION_VENTA") {
    hiddenClosingDocumentTypes.add("MODELO_620");
  }
  const closingOperationId = operaciones.length > 1 ? activeOperation?.id ?? null : null;

  return (
    <main className="exp-detail-page">
      <ExpedienteHeader expediente={expediente} />
      <OperationTabs
        activeOperationId={activeOperation?.id ?? null}
        operaciones={operaciones}
        onSelect={setActiveOperationId}
      />
      {showClosingDocumentsPanel ? (
        <ClosingDocumentsPanel
          disabledDocumentTypes={disabledClosingDocumentTypes}
          documentos={expediente.documentos}
          hiddenDocumentTypes={hiddenClosingDocumentTypes}
          operationId={closingOperationId}
          onUploadClosingDocument={handleUploadClosingDocument}
        />
      ) : null}
      <IncidentAlertPanel
        incidencias={expediente.incidencias}
        onCreateIncident={canOpenIncident ? openIncidentDialog : undefined}
        onResolveIncident={setResolvingIncident}
      />
      {canRequestAdditionalInfo ? (
        <section className="exp-quick-actions" aria-label="Acciones rapidas del expediente">
          <div>
            <p className="eyebrow">Acciones del expediente</p>
            <strong>Actualizar datos, incidencias o solicitudes</strong>
            <span>Las incidencias pueden acumularse y el expediente seguira bloqueado hasta resolverlas todas.</span>
          </div>
          <div className="exp-quick-actions__buttons">
            <button
              className="soft-button"
              disabled={updatingDocuments}
              onClick={handleUpdateFromExistingDocuments}
              type="button"
            >
              {updatingDocuments ? <Loader2 className="button-spinner" size={16} /> : <RefreshCw size={16} />}
              Actualizar datos
            </button>
            <button
              className="soft-button milestone-action--warning"
              onClick={openIncidentDialog}
              type="button"
            >
              <ShieldAlert size={16} />
              Abrir incidencia
            </button>
            <button
              className="soft-button"
              disabled={hasActiveIncidents || hasAdditionalInfoFlow}
              onClick={() => setRequirementOpenSignal((value) => value + 1)}
              type="button"
            >
              <FilePlus2 size={16} />
              Solicitar documentacion
            </button>
            <button
              className="primary-button"
              disabled={hasActiveIncidents || hasAdditionalInfoFlow || hasDocumentationRequest}
              onClick={() => setAdditionalInfoDialogOpen(true)}
              type="button"
            >
              <MessageCircle size={16} />
              Solicitar informacion
            </button>
          </div>
        </section>
      ) : null}

      <div className="exp-process-layout">
        <div className="exp-process-main">
          <InteresadosPanel interesados={expediente.interesados} onEditInteresados={() => setInteresadosCorrectionOpen(true)} />
          <NextActionPanel
            documentos={expediente.documentos}
            hitos={operationalHitos}
            siguientePaso={nextOperationalStep}
            onOpenChecklist={() => setChecklistOpen(true)}
            onRunMilestoneAction={handleRunMilestoneAction}
          />
          <PhaseMilestonesPanel
            closingDocumentsReady={operaciones.length <= 1 ? hasClosingDocuments(expediente.documentos) : undefined}
            expedienteEstado={operaciones.length <= 1 ? expediente.estado : undefined}
            hitos={operationalHitos}
            rollbackLocked={expediente.estado === "FINALIZADO" && hasClosingDocuments(expediente.documentos)}
            onRunMilestoneAction={handleRunMilestoneAction}
          />
          <CompleteExpedienteUploadPanel
            title="Extraer documentacion del expediente"
            description="Sube un PDF completo para separar automaticamente los documentos que ya estan en nuestro poder."
            onUploadCompleteExpediente={handleUploadCompleteExpediente}
            processing={completeExpedienteProcessing}
          />
          <DocumentRequirementsPanel
            documentos={expediente.documentos}
            inconsistencias={expediente.inconsistenciasDocumentales}
            interesados={expediente.interesados}
            openRequestSignal={requirementOpenSignal}
            requisitos={expediente.requisitosDocumentales}
            onAddRequirement={handleAddRequirement}
            onLinkRequirementDocument={handleLinkRequirementDocument}
            onOmitRequirement={handleOmitRequirement}
            onUploadRequirement={handleUploadRequirement}
          />
          <DocumentsPanel
            applyingRolesId={applyingRolesId}
            documentos={expediente.documentos}
            onDeleteDocument={handleDeleteDocument}
            onEditDocument={setEditingDocument}
            onApplyRoles={handleApplyDocumentRoles}
            onOpenChecklist={() => setChecklistOpen(true)}
            onOpenReview={handleOpenDocumentReview}
            onOpenTemplates={() => setTemplateDialogOpen(true)}
            onReadIdentity={handleReadDocumentIdentity}
            onReadRoles={handleReadDocumentRoles}
            onUploadDocument={handleUploadDocument}
            readingIdentityId={readingIdentityId}
            readingRolesId={readingRolesId}
          />
        </div>

        <OperationalAside expediente={expediente} />
      </div>

      <SecondaryExpedienteTabs expediente={expediente} onOpenMessages={handleOpenMessages} onSendMessage={handleSendMessage} />

      <DocumentChecklistDialog
        documentos={expediente.documentos}
        onClose={() => setChecklistOpen(false)}
        open={checklistOpen}
      />
      <DocumentTemplateDialog
        expedienteId={expediente.id}
        onClose={() => setTemplateDialogOpen(false)}
        onGenerated={() => refreshExpediente()}
        open={templateDialogOpen}
      />
      <OcrReviewDialog
        documentos={ocrReviewDocuments}
        operaciones={operaciones}
        onDeleteDocument={handleDeleteDocument}
        onDeletePages={handleDeleteOcrPages}
        onExtractPages={handleExtractOcrPages}
        onMergeDocuments={handleMergeOcrDocuments}
        onSaveDocument={handleSaveOcrDocument}
        onClose={() => setOcrReviewOpen(false)}
        open={ocrReviewOpen}
      />
      {completeExpedienteProcessing ? (
        <div className="exp-processing-overlay" role="status" aria-live="polite">
          <div className="exp-processing-overlay__panel">
            <Loader2 className="exp-processing-overlay__spinner" size={34} />
            <div>
              <p className="eyebrow">Procesando OCR</p>
              <h3>Separando expediente completo</h3>
              <p>Estamos leyendo el PDF, detectando documentos y preparando la revision.</p>
            </div>
          </div>
        </div>
      ) : null}
      <AdditionalInfoDialog
        onClose={() => setAdditionalInfoDialogOpen(false)}
        onSubmit={handleRequestAdditionalInfo}
        open={additionalInfoDialogOpen}
      />
      {interesadosCorrectionOpen ? (
        <InterestedPartiesCorrectionDialog
          expediente={expediente}
          saving={savingInteresadosCorrection}
          onClose={() => setInteresadosCorrectionOpen(false)}
          onSubmit={handleCorrectInteresados}
        />
      ) : null}
      <IncidentCreateDialog
        loading={incidentTypesLoading}
        onClose={() => setIncidentDialogOpen(false)}
        onSubmit={handleCreateIncident}
        open={incidentDialogOpen}
        tipos={incidentTypes}
      />
      <IncidentResolutionDialog
        documentos={expediente.documentos}
        incidencia={resolvingIncident}
        onAccept={handleAcceptIncidentResolution}
        onClose={() => setResolvingIncident(null)}
        onLinkDocument={handleLinkIncidentDocument}
        onReclaim={handleReclaimIncident}
        onUploadDocument={handleUploadIncidentDocument}
      />
      <DocumentEditDialog
        documento={editingDocument}
        operaciones={operaciones}
        onClose={() => setEditingDocument(null)}
        onSubmit={handleEditDocument}
      />
      {dialog}
    </main>
  );
}
