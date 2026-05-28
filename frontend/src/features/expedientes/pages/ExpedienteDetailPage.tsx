import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { AlertCircle, AlertTriangle, CalendarClock, ClipboardCheck, Download, FileText, Loader2, Route, ShieldCheck, Upload, UserRound } from "lucide-react";
import { CompleteExpedienteUploadPanel } from "../components/CompleteExpedienteUploadPanel";
import { DocumentChecklistDialog } from "../components/DocumentChecklistDialog";
import { DocumentRequirementsPanel } from "../components/DocumentRequirementsPanel";
import { DocumentsPanel } from "../components/DocumentsPanel";
import { ExpedienteHeader } from "../components/ExpedienteHeader";
import { IncidentAlertPanel } from "../components/IncidentAlertPanel";
import { IncidentCreateDialog } from "../components/IncidentCreateDialog";
import { IncidentResolutionDialog } from "../components/IncidentResolutionDialog";
import { InteresadosPanel } from "../components/InteresadosPanel";
import { NextActionPanel } from "../components/NextActionPanel";
import { OcrReviewDialog } from "../components/OcrReviewDialog";
import { PhaseMilestonesPanel } from "../components/PhaseMilestonesPanel";
import { SecondaryExpedienteTabs } from "../components/SecondaryExpedienteTabs";
import { deleteDocument, deleteDocumentPages, extractDocumentPages, mergeDocuments, updateDocument, uploadExpedienteDocument } from "../services/documentosApi";
import {
  completeExpedienteMilestone,
  finishExpediente,
  getExpedienteDetail,
  getIncidentTypes,
  linkIncidentDocument,
  openExpedienteIncident,
  reclaimIncident,
  resolveIncident,
  sendExpedienteMessage,
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
import type {
  DocumentoExpediente,
  ExpedienteDetail,
  HitoAccion,
  HitoExpediente,
  IncidenciaExpediente,
  OperacionExpediente,
  RequisitoDocumental,
  TipoIncidencia,
} from "../types/expedienteDetail.types";
import "../styles/expedienteDetail.css";

const CLOSING_DOCUMENTS = [
  {
    tipo: "COMPROBANTE_DGT",
    title: "Comprobante DGT",
    description: "Justificante final emitido por la DGT.",
    agency: "DGT",
    logoSrc: "/assets/logos/logo-dgt.png",
    logoAlt: "Logotipo DGT",
  },
  {
    tipo: "MODELO_620",
    title: "Modelo 620",
    description: "Modelo 620 presentado.",
    agency: "Agencia Tributaria Canaria",
    logoSrc: "/assets/logos/logo-atc.svg",
    logoAlt: "Logotipo Agencia Tributaria Canaria",
  },
] as const;

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
    documentos.some((documento) => documento.tipo === item.tipo && documento.subido && documento.id),
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
  const docsReady = expediente.hitos.some((hito) => hito.id === "documentacion-completa" && hito.completado);
  const managementReady = expediente.hitos.some((hito) => hito.id === "tramite-programa-gestion" && hito.completado);
  const model620Ready = expediente.hitos.some((hito) => hito.id === "modelo-620-presentado" && hito.completado);
  const sentDgt = expediente.estado === "ENVIADO_DGT" || expediente.hitos.some((hito) => hito.id === "finalizado-incidencia" && hito.titulo === "Enviado a DGT");

  const stages = [
    {
      id: "inicio",
      title: "Expediente creado",
      description: "Solicitud revisada y expediente abierto.",
      state: "Completado",
      tone: "done",
    },
    {
      id: "preparacion",
      title: hasIncident ? "Incidencia" : docsReady ? "Documentacion validada" : "Documentacion",
      description: hasIncident ? "Subsanacion o revision pendiente." : docsReady ? "Preparado para los pasos de gestion." : "Revisando documentos base.",
      state: docsReady || isClosed ? "Completado" : "Fase actual",
      tone: docsReady || isClosed ? "done" : "active",
    },
    {
      id: "gestion",
      title: model620Ready ? "Modelo 620 presentado" : managementReady ? "Modelo 620" : "Programa gestion",
      description: model620Ready ? "Preparado para el cierre." : managementReady ? "Pendiente de presentacion." : "Carga operativa del expediente.",
      state: model620Ready || isClosed ? "Completado" : docsReady ? "Fase actual" : "Pendiente",
      tone: model620Ready || isClosed ? "done" : docsReady ? "active" : "pending",
    },
    {
      id: "cierre",
      title: hasIncident ? "Incidencia" : sentDgt ? "Enviado a DGT" : "Finalizado",
      description: isClosed && !closingDocumentsReady ? "Pendiente de comprobantes finales." : "Enviado a DGT, incidencia o finalizado.",
      state: isClosed ? (closingDocumentsReady ? "Completado" : "Pendiente comprobantes") : model620Ready ? "Fase actual" : "Pendiente",
      tone: isClosed ? (closingDocumentsReady ? "done" : "active") : model620Ready ? "active" : "pending",
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
          {isClosed ? "Cerrado" : "En curso"}
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
  documentos,
  onUploadClosingDocument,
}: {
  documentos: DocumentoExpediente[];
  onUploadClosingDocument: (tipoDocumento: string, archivo: File) => void;
}) {
  const documentByType = new Map(
    documentos
      .filter((documento) => documento.subido && documento.id)
      .map((documento) => [documento.tipo, documento]),
  );
  const missingDocuments = CLOSING_DOCUMENTS.filter((item) => !documentByType.has(item.tipo));

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
        {CLOSING_DOCUMENTS.map((item) => {
          const documento = documentByType.get(item.tipo);
          const ready = Boolean(documento?.id);
          const fileName = documento?.nombreOriginal || documento?.nombre;

          return (
            <article className={`closure-doc-card ${ready ? "closure-doc-card--ready" : "closure-doc-card--missing"}`} key={item.tipo}>
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

export function ExpedienteDetailPage() {
  const { id } = useParams();
  const [expediente, setExpediente] = useState<ExpedienteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [checklistOpen, setChecklistOpen] = useState(false);
  const [ocrReviewOpen, setOcrReviewOpen] = useState(false);
  const [ocrReviewDocuments, setOcrReviewDocuments] = useState<DocumentoExpediente[]>([]);
  const [completeExpedienteProcessing, setCompleteExpedienteProcessing] = useState(false);
  const [incidentDialogOpen, setIncidentDialogOpen] = useState(false);
  const [incidentTypes, setIncidentTypes] = useState<TipoIncidencia[]>([]);
  const [incidentTypesLoading, setIncidentTypesLoading] = useState(false);
  const [resolvingIncident, setResolvingIncident] = useState<IncidenciaExpediente | null>(null);
  const [activeOperationId, setActiveOperationId] = useState<number | null>(null);

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
          setError("Sesion caducada. Inicia sesion para cargar el expediente.");
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
    return getExpedienteDetail(id).then((data) => {
      setExpediente(data);
      if (!activeOperationId && data.operaciones?.length) {
        setActiveOperationId(data.operaciones[0].id);
      }
      return data;
    });
  }, [activeOperationId, id]);

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
      await uploadExpedienteDocument(expediente.id, tipoDocumento, archivo);
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
      setOcrReviewDocuments(nuevos);
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

  const handleEditDocument = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    const nuevoNombre = window.prompt("Nombre del archivo", documento.nombreOriginal || documento.nombre);
    if (nuevoNombre === null) return;
    const nuevoTipo = window.prompt("Tipo documental", documento.tipo);
    if (nuevoTipo === null) return;

    try {
      await updateDocument(documento.id, nuevoTipo.trim() || documento.tipo, nuevoNombre.trim() || undefined, documento.operacionId);
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
    nombreSinExtension: string,
    operacionId?: number | null,
  ) => {
    if (!documento.id) return;
    try {
      await updateDocument(documento.id, tipoDocumento, nombreSinExtension, operacionId);
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
    nombreSinExtension: string,
    operacionId?: number | null,
  ) => {
    if (!documento.id) return;
    try {
      const previousIds = new Set(expediente?.documentos.map((item) => item.id).filter(Boolean));
      await extractDocumentPages(documento.id, rangoPaginas, tipoDocumento, nombreSinExtension, operacionId);
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
    if (actionType === "ABRIR_INCIDENCIA") {
      setIncidentDialogOpen(true);
      if (incidentTypes.length === 0) {
        setIncidentTypesLoading(true);
        getIncidentTypes()
          .then(setIncidentTypes)
          .catch(() => alert("No se pudieron cargar los tipos de incidencia."))
          .finally(() => setIncidentTypesLoading(false));
      }
      return;
    }
    const confirmed = window.confirm(`Confirmar: ${actionLabel}`);
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



  const handleDeleteDocument = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    const confirmed = window.confirm(`¿Borrar ${documento.nombreOriginal || documento.nombre}?`);
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
  const nextOperationalStep =
    operationalHitos.find((hito) => hito.accion && !hito.completado && !hito.bloqueado) ?? expediente.siguientePaso;

  return (
    <main className="exp-detail-page">
      <ExpedienteHeader expediente={expediente} />
      <OperationTabs
        activeOperationId={activeOperation?.id ?? null}
        operaciones={operaciones}
        onSelect={setActiveOperationId}
      />
      {expediente.estado === "FINALIZADO" ? (
        <ClosingDocumentsPanel documentos={expediente.documentos} onUploadClosingDocument={handleUploadClosingDocument} />
      ) : null}
      <IncidentAlertPanel incidencias={expediente.incidencias} onResolveIncident={setResolvingIncident} />

      <div className="exp-process-layout">
        <div className="exp-process-main">
          <InteresadosPanel interesados={expediente.interesados} />
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
            onRunMilestoneAction={handleRunMilestoneAction}
          />
          <CompleteExpedienteUploadPanel
            onUploadCompleteExpediente={handleUploadCompleteExpediente}
            processing={completeExpedienteProcessing}
          />
          <DocumentRequirementsPanel
            documentos={expediente.documentos}
            interesados={expediente.interesados}
            requisitos={expediente.requisitosDocumentales}
            onAddRequirement={handleAddRequirement}
            onLinkRequirementDocument={handleLinkRequirementDocument}
            onOmitRequirement={handleOmitRequirement}
            onUploadRequirement={handleUploadRequirement}
          />
          <DocumentsPanel
            documentos={expediente.documentos}
            onDeleteDocument={handleDeleteDocument}
            onEditDocument={handleEditDocument}
            onOpenChecklist={() => setChecklistOpen(true)}
            onUploadDocument={handleUploadDocument}
          />
        </div>

        <OperationalAside expediente={expediente} />
      </div>

      <SecondaryExpedienteTabs expediente={expediente} onSendMessage={handleSendMessage} />

      <DocumentChecklistDialog
        documentos={expediente.documentos}
        onClose={() => setChecklistOpen(false)}
        open={checklistOpen}
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
    </main>
  );
}
