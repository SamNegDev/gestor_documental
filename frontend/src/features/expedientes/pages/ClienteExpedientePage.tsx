import { useCallback, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import * as Tabs from "@radix-ui/react-tabs";
import { Link, useParams } from "react-router-dom";
import { AlertCircle, ArrowLeft, CheckCircle2, Clock3, Download, Eye, FileText, History, Loader2, MessageCircle, Upload } from "lucide-react";
import { answerAdditionalInfo, getClienteExpediente, markClienteExpedienteMessagesRead, sendClienteExpedienteMessage, type ExpedienteCliente } from "../services/clienteExpedienteApi";
import { uploadIncidentDocument } from "../services/expedienteDetailApi";
import { uploadExpedienteDocument } from "../services/documentosApi";
import { uploadRequirementDocument } from "../services/requisitosApi";
import { DocumentUploadDialog, type DocumentUploadSubmit } from "../components/DocumentUploadDialog";
import type { DocumentoExpediente, IncidenciaExpediente, RequisitoDocumental } from "../types/expedienteDetail.types";
import { formatDateTime, formatDocumentType, humanizeEnum } from "../utils/formatters";
import { uppercaseInputPreservingCursor } from "../../../shared/utils/text";
import { clientInitials } from "../../../shared/utils/clientBranding";
import { ApiError } from "../../../shared/api/http";
import "../styles/expedienteDetail.css";

const CLIENT_CLOSING_DOCUMENTS = [
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

function clienteTone(estado: string) {
  if (estado === "FINALIZADO") return "success";
  if (estado === "INCIDENCIA" || estado === "SOLICITADA_INFORMACION_ADICIONAL" || estado === "PENDIENTE_DOCUMENTACION") return "danger";
  if (estado === "REVISANDO_INCIDENCIAS") return "warning";
  return "info";
}

function friendlyPhase(expediente: ExpedienteCliente) {
  if (expediente.estado === "FINALIZADO") return "Expediente finalizado";
  if (expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL") return "Informacion solicitada";
  if (expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA") return "Informacion en revision";
  if (expediente.estado === "PENDIENTE_DOCUMENTACION") return "Pendiente de documentacion";
  if (expediente.estado === "INCIDENCIA") return "Pendiente de documentacion";
  if (expediente.estado === "REVISANDO_INCIDENCIAS") return "Documentacion en revision";
  if (expediente.estado === "ENVIADO_DGT") return "Enviado a DGT";
  if (expediente.faseActual === "Cierre del expediente") return "Tramite listo para firmar";
  return expediente.faseActual || "En tramitacion";
}

function timelineSteps(expediente: ExpedienteCliente) {
  const phase = friendlyPhase(expediente);
  const currentIndex =
    expediente.estado === "FINALIZADO" ? 4
      : expediente.estado === "ENVIADO_DGT" || phase.includes("firmar") ? 2
        : expediente.estado === "INCIDENCIA" || expediente.estado === "REVISANDO_INCIDENCIAS"
          || expediente.estado === "PENDIENTE_DOCUMENTACION"
          || expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL" || expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA" ? 1
          : 1;

  return [
    { title: "Expediente abierto", description: "Hemos recibido la informacion inicial." },
    { title: expediente.estado === "INCIDENCIA" ? "Incidencia" : expediente.estado === "REVISANDO_INCIDENCIAS" ? "Revision" : expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL" || expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA" ? "Informacion" : "Documentacion", description: expediente.estado === "REVISANDO_INCIDENCIAS" ? "Tu documento esta pendiente de revision." : expediente.estado === "PENDIENTE_DOCUMENTACION" ? "Necesitamos que aportes los documentos solicitados." : expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL" ? "Necesitamos una respuesta para continuar." : expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA" ? "Tu respuesta esta pendiente de revision." : "Revisamos o completamos la documentacion." },
    { title: "Listo para firmar", description: "El tramite queda preparado para el siguiente paso." },
    { title: "Finalizado", description: "El expediente queda cerrado." },
  ].map((step, index) => ({
    ...step,
    state: index < currentIndex ? "done" : index === currentIndex ? "current" : "pending",
  }));
}

function ClientClosingDocumentsPanel({ documentos }: { documentos: DocumentoExpediente[] }) {
  const documentByType = new Map(
    CLIENT_CLOSING_DOCUMENTS
      .map((item) => [
        item.tipo,
        documentos.find((documento) => item.aliases.some((alias) => alias === documento.tipo) && documento.subido && documento.id),
      ] as const)
      .filter((entry): entry is readonly [typeof CLIENT_CLOSING_DOCUMENTS[number]["tipo"], DocumentoExpediente] => Boolean(entry[1])),
  );
  const available = CLIENT_CLOSING_DOCUMENTS.filter((item) => documentByType.has(item.tipo)).length;

  return (
    <section className="closure-docs-panel closure-docs-panel--client" aria-label="Documentos finales del expediente">
      <div className="closure-docs-panel__heading">
        <div>
          <p className="eyebrow">Tramite finalizado</p>
          <h3>Justificantes finales</h3>
          <p>Acceso directo a los comprobantes oficiales del expediente.</p>
        </div>
        <span className={`closure-docs-status ${available === CLIENT_CLOSING_DOCUMENTS.length ? "closure-docs-status--ready" : "closure-docs-status--warning"}`}>
          {available} de {CLIENT_CLOSING_DOCUMENTS.length} disponibles
        </span>
      </div>

      <div className="closure-docs-grid">
        {CLIENT_CLOSING_DOCUMENTS.map((item) => {
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
                <small>{ready ? "Documento disponible" : "Pendiente de publicacion"}</small>
              </div>
              <div className="closure-doc-card__actions">
                {ready && documento?.id ? (
                  <>
                    <a className="soft-button soft-button--compact" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer">
                      <Eye size={15} />
                      Ver
                    </a>
                    <a className="primary-button primary-button--compact" href={`/documentos/descargar/${documento.id}`}>
                      <Download size={15} />
                      Descargar
                    </a>
                  </>
                ) : (
                  <span className="closure-doc-card__pending">Aun no disponible</span>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function ClientDocumentRequirementsPanel({
  requisitos,
  onUpload,
}: {
  requisitos: RequisitoDocumental[];
  onUpload: (requisito: RequisitoDocumental, archivo: File) => void;
}) {
  if (requisitos.length === 0) return null;

  return (
    <section className="client-requirements-panel" aria-label="Documentacion solicitada">
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Accion requerida</p>
          <h3>Documentacion pendiente</h3>
        </div>
        <span className="exp-panel__counter">{requisitos.length}</span>
      </div>
      <div className="client-requirements-list">
        {requisitos.map((requisito) => (
          <article className="client-requirement-row" key={requisito.id}>
            <span className="client-requirement-row__icon">
              <FileText size={18} />
            </span>
            <div>
              <strong>{requisito.descripcion}</strong>
              <small>
                {formatDocumentType(requisito.tipoDocumento)}
                {requisito.interesadoNombre ? ` · ${requisito.interesadoNombre}` : ""}
              </small>
            </div>
            <label className="primary-button primary-button--compact">
              <Upload size={15} />
              Aportar solicitado
              <input
                hidden
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={(event) => {
                  const file = event.currentTarget.files?.[0];
                  event.currentTarget.value = "";
                  if (file) onUpload(requisito, file);
                }}
              />
            </label>
          </article>
        ))}
      </div>
    </section>
  );
}

export function ClienteExpedientePage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [expediente, setExpediente] = useState<ExpedienteCliente | null>(null);
  const [message, setMessage] = useState("");
  const [additionalInfoResponse, setAdditionalInfoResponse] = useState("");
  const [uploadedIncidentIds, setUploadedIncidentIds] = useState<Set<number>>(new Set());
  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [uploadingStandaloneDocument, setUploadingStandaloneDocument] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refreshRelatedData = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["expedientes"] }),
      queryClient.invalidateQueries({ queryKey: ["tareas"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      queryClient.invalidateQueries({ queryKey: ["registro"] }),
    ]);
  }, [queryClient]);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      setExpediente(await getClienteExpediente(id));
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        setError("Sesión caducada. Inicia sesión para consultar el expediente.");
      } else if (cause instanceof ApiError && cause.status === 403) {
        setError("No tienes permiso para consultar este expediente.");
      } else if (cause instanceof ApiError && cause.status === 404) {
        setError("No hemos encontrado este expediente.");
      } else if (cause instanceof ApiError && cause.details) {
        setError(cause.details);
      } else {
        setError("No se pudo cargar el expediente.");
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleUploadIncidentDocument = async (incidencia: IncidenciaExpediente, archivo: File) => {
    try {
      await uploadIncidentDocument(incidencia.id, archivo);
      setUploadedIncidentIds((current) => new Set(current).add(incidencia.id));
      await load();
      await refreshRelatedData();
    } catch {
      alert("No se pudo subir el documento.");
    }
  };

  const handleUploadRequirement = async (requisito: RequisitoDocumental, archivo: File) => {
    try {
      await uploadRequirementDocument(requisito.id, archivo);
      await load();
      await refreshRelatedData();
    } catch {
      alert("No se pudo aportar el documento solicitado.");
    }
  };

  const handleUploadStandaloneDocument = async (input: DocumentUploadSubmit) => {
    if (!id || uploadingStandaloneDocument) return;
    setUploadingStandaloneDocument(true);
    try {
      await uploadExpedienteDocument(Number(id), input.tipoDocumento, input.archivo, input.operacionId);
      setUploadDialogOpen(false);
      await load();
      await refreshRelatedData();
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudo subir el documento." : "No se pudo subir el documento.");
    } finally {
      setUploadingStandaloneDocument(false);
    }
  };

  const handleAnswerAdditionalInfo = async () => {
    const contenido = additionalInfoResponse.trim();
    if (!id || !contenido) return;
    try {
      await answerAdditionalInfo(id, contenido);
      setAdditionalInfoResponse("");
      await load();
      await refreshRelatedData();
    } catch {
      alert("No se pudo enviar la respuesta.");
    }
  };

  const handleSendMessage = async () => {
    const contenido = message.trim();
    if (!id || !contenido) return;
    try {
      await sendClienteExpedienteMessage(id, contenido);
      setMessage("");
      await load();
      await refreshRelatedData();
    } catch {
      alert("No se pudo enviar el mensaje.");
    }
  };

  const handleOpenMessages = async () => {
    if (!id || !expediente?.mensajesNoLeidos) return;
    try {
      await markClienteExpedienteMessagesRead(id);
      setExpediente((current) => current ? {
        ...current,
        mensajesNoLeidos: 0,
        mensajes: current.mensajes.map((mensaje) => ({ ...mensaje, noLeidoParaUsuario: false })),
      } : current);
    } catch {
      // Se refrescara en la siguiente carga.
    }
  };

  if (loading) {
    return (
      <div className="exp-detail-state">
        <Loader2 className="exp-detail-state__spinner" size={28} />
        <strong>Cargando expediente</strong>
        <span>Estamos recuperando el estado del expediente.</span>
      </div>
    );
  }

  if (error || !expediente) {
    return (
      <div className="exp-detail-state exp-detail-state--error">
        <AlertCircle size={28} />
        <strong>{error || "Expediente no encontrado"}</strong>
        <div className="exp-detail-state__actions">
          <button className="soft-button" onClick={() => void load()} type="button">
            Reintentar
          </button>
          {error?.includes("Sesión") ? (
            <a className="primary-button" href="/login">
              Iniciar sesion
            </a>
          ) : null}
        </div>
      </div>
    );
  }

  const documentos = expediente.documentos ?? [];
  const requisitos = expediente.requisitosDocumentales ?? [];
  const incidencias = expediente.incidencias ?? [];
  const mensajes = expediente.mensajes ?? [];
  const historial = expediente.historial ?? [];
  const incidenciasActivas = incidencias.filter((incidencia) => !incidencia.resuelta);
  const tone = clienteTone(expediente.estado);
  const phase = friendlyPhase(expediente);
  const steps = timelineSteps(expediente);
  const informationRequested = expediente.estado === "SOLICITADA_INFORMACION_ADICIONAL";
  const informationReceived = expediente.estado === "INFORMACION_ADICIONAL_RECIBIDA";
  const latestAdminMessage = [...mensajes].reverse().find((mensaje) => mensaje.rolAutor === "ADMIN");
  const canUploadStandaloneDocument = expediente.estado !== "FINALIZADO" && expediente.estado !== "RECHAZADO";

  return (
    <main className="client-expediente-page">
      <div className="client-expediente-nav">
        <Link className="registry-back" to="/expedientes">
          <ArrowLeft size={16} />
          Volver a expedientes
        </Link>
        {expediente.solicitudId ? (
          <Link className="soft-button soft-button--compact" to={`/solicitudes/${expediente.solicitudId}`}>
            <FileText size={15} />
            Solicitud de origen
          </Link>
        ) : null}
      </div>
      <section className={`client-status-hero client-status-hero--${tone}`}>
        <div className="client-status-hero__identity">
          <div className="client-status-hero__brand" aria-hidden="true">
            {expediente.cliente?.logoPrincipalUrl ? (
              <img src={expediente.cliente.logoPrincipalUrl} alt="" />
            ) : expediente.cliente?.logoCompactoUrl ? (
              <img src={expediente.cliente.logoCompactoUrl} alt="" />
            ) : (
              clientInitials(expediente.cliente?.nombre)
            )}
          </div>
          <div>
            <p className="eyebrow">{expediente.referencia}</p>
            <h2>{expediente.matricula || "Expediente sin matricula"}</h2>
            <p>{expediente.tipoTramiteDescripcion || "Tramite en curso"}</p>
          </div>
        </div>
        <div className="client-status-hero__state">
          {tone === "success" ? <CheckCircle2 size={28} /> : <Clock3 size={28} />}
          <strong>{humanizeEnum(expediente.estado)}</strong>
          <span>{phase}</span>
        </div>
      </section>

      <section className="client-state-panel">
        <strong>{expediente.siguienteMensaje || "Estamos tramitando el expediente."}</strong>
        <span>Inicio: {formatDateTime(expediente.fechaInicio)}</span>
      </section>

      <ClientDocumentRequirementsPanel requisitos={requisitos} onUpload={handleUploadRequirement} />

      {expediente.estado === "FINALIZADO" ? <ClientClosingDocumentsPanel documentos={documentos} /> : null}

      <section className="client-timeline-panel">
        <div className="exp-panel__heading">
          <div>
            <p className="eyebrow">Seguimiento</p>
            <h3>{phase}</h3>
          </div>
        </div>
        <ol className="client-timeline">
          {steps.map((step, index) => (
            <li className={`client-timeline-step client-timeline-step--${step.state}`} key={step.title}>
              <span>{step.state === "done" ? <CheckCircle2 size={16} /> : index + 1}</span>
              <div>
                <strong>{step.title}</strong>
                <small>{step.description}</small>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {informationRequested || informationReceived ? (
        <section className="client-incident-panel">
          <div className="exp-panel__heading">
            <div>
              <p className="eyebrow">Accion requerida</p>
              <h3>{informationReceived ? "Informacion recibida" : "Informacion solicitada"}</h3>
            </div>
          </div>
          <article className="client-incident-card">
            <div>
              <strong>{informationReceived ? "Respuesta enviada" : "Necesitamos tu respuesta"}</strong>
              <p>{latestAdminMessage?.contenido || "Revisa la informacion solicitada para que podamos continuar con el expediente."}</p>
              {informationReceived ? (
                <small className="client-upload-feedback">
                  <CheckCircle2 size={15} />
                  Respuesta recibida. Lo estamos revisando.
                </small>
              ) : null}
            </div>
            {informationRequested ? (
              <div className="client-incident-response">
                <textarea
                  onChange={(event) => uppercaseInputPreservingCursor(event, setAdditionalInfoResponse)}
                  placeholder="Escribe tu respuesta"
                  rows={3}
                  value={additionalInfoResponse}
                />
                <button
                  className="primary-button"
                  disabled={!additionalInfoResponse.trim()}
                  onClick={() => void handleAnswerAdditionalInfo()}
                  type="button"
                >
                  <MessageCircle size={16} />
                  Enviar respuesta
                </button>
              </div>
            ) : null}
          </article>
        </section>
      ) : null}

      {incidenciasActivas.length > 0 ? (
        <section className="client-incident-panel">
          <div className="exp-panel__heading">
            <div>
              <p className="eyebrow">Accion requerida</p>
              <h3>Incidencia pendiente</h3>
            </div>
          </div>
          {incidenciasActivas.map((incidencia) => (
            <article className="client-incident-card" key={incidencia.id}>
              <div>
                <strong>{humanizeEnum(incidencia.tipo)}</strong>
                <p>{incidencia.observaciones || "Revisa la documentacion solicitada."}</p>
                {expediente.estado === "REVISANDO_INCIDENCIAS" || uploadedIncidentIds.has(incidencia.id) || incidencia.pendienteRevisionCliente ? (
                  <small className="client-upload-feedback">
                    <CheckCircle2 size={15} />
                    Respuesta recibida. Lo estamos revisando.
                  </small>
                ) : null}
              </div>
              {expediente.estado === "REVISANDO_INCIDENCIAS" || uploadedIncidentIds.has(incidencia.id) || incidencia.pendienteRevisionCliente ? null : (
                <label className="primary-button primary-button--danger">
                  <Upload size={16} />
                  Responder incidencia
                  <input
                    hidden
                    type="file"
                    accept=".pdf,.jpg,.jpeg,.png"
                    onChange={(event) => {
                      const file = event.currentTarget.files?.[0];
                      event.currentTarget.value = "";
                      if (file) void handleUploadIncidentDocument(incidencia, file);
                    }}
                  />
                </label>
              )}
            </article>
          ))}
        </section>
      ) : null}

      <section className="exp-panel">
        <div className="exp-panel__heading">
          <div>
            <p className="eyebrow">Documentos</p>
            <h3>Documentacion del expediente</h3>
          </div>
          {canUploadStandaloneDocument ? (
            <button className="soft-button" onClick={() => setUploadDialogOpen(true)} type="button">
              <Upload size={16} />
              Subir documento suelto
            </button>
          ) : null}
        </div>
        <div className="documents-list">
          {documentos.length === 0 ? (
            <p className="exp-empty">Todavia no hay documentos disponibles.</p>
          ) : (
            documentos.map((documento) => (
              <article className="document-row" key={documento.id || documento.nombre}>
                <div className="pdf-icon">
                  <FileText size={18} />
                  <strong>PDF</strong>
                </div>
                <div className="document-row__body">
                  <strong>{documento.nombreOriginal || documento.nombre}</strong>
                  <span>{formatDocumentType(documento.tipo)}</span>
                  <small>{formatDateTime(documento.fechaSubida)}</small>
                </div>
                <div className="document-row__actions">
                  <button
                    className="soft-button soft-button--compact"
                    disabled={!documento.id}
                    onClick={() => documento.id && window.open(`/documentos/ver/${documento.id}`, "_blank", "noopener,noreferrer")}
                    type="button"
                  >
                    <Eye size={15} />
                    Ver
                  </button>
                </div>
              </article>
            ))
          )}
        </div>
      </section>

      <section className="exp-panel exp-panel--secondary">
        <Tabs.Root
          defaultValue="historial"
          className="secondary-tabs"
          onValueChange={(value) => {
            if (value === "mensajes") void handleOpenMessages();
          }}
        >
          <Tabs.List className="secondary-tabs__list" aria-label="Comunicación y seguimiento del expediente">
            <Tabs.Trigger value="mensajes">
              <MessageCircle size={16} />
              Mensajes
              {(expediente.mensajesNoLeidos ?? 0) > 0 ? (
                <span className="tab-unread-badge">{(expediente.mensajesNoLeidos ?? 0) > 99 ? "99+" : expediente.mensajesNoLeidos}</span>
              ) : null}
            </Tabs.Trigger>
            <Tabs.Trigger value="historial">
              <History size={16} />
              Historial
            </Tabs.Trigger>
          </Tabs.List>

          <Tabs.Content value="mensajes" className="secondary-tabs__content">
            <div className="client-message-box">
              <textarea
                onChange={(event) => uppercaseInputPreservingCursor(event, setMessage)}
                placeholder="Escribe un mensaje"
                rows={3}
                value={message}
              />
              <button className="primary-button" disabled={!message.trim()} onClick={handleSendMessage} type="button">
                <MessageCircle size={16} />
                Enviar
              </button>
            </div>
            <div className="timeline-list">
              {mensajes.length === 0 ? (
                <p className="exp-empty">No hay mensajes todavia.</p>
              ) : (
                mensajes.map((mensaje) => (
                  <article className={`timeline-item ${mensaje.noLeidoParaUsuario ? "timeline-item--unread" : ""}`} key={mensaje.id}>
                    <strong>{mensaje.autor || "Usuario"}</strong>
                    <p>{mensaje.contenido}</p>
                    <small>{formatDateTime(mensaje.fechaCreacion)}</small>
                  </article>
                ))
              )}
            </div>
          </Tabs.Content>

          <Tabs.Content value="historial" className="secondary-tabs__content">
            <div className="timeline-list">
              {historial.length === 0 ? (
                <p className="exp-empty">No hay movimientos todavia.</p>
              ) : (
                historial.map((item) => (
                  <article className="timeline-item" key={item.id}>
                    <strong>{humanizeEnum(item.accion)}</strong>
                    <p>{item.descripcion || "Movimiento registrado en el expediente."}</p>
                    <small>{formatDateTime(item.fechaCambio)}</small>
                  </article>
                ))
              )}
            </div>
          </Tabs.Content>
        </Tabs.Root>
      </section>
      <DocumentUploadDialog
        open={uploadDialogOpen}
        saving={uploadingStandaloneDocument}
        onClose={() => setUploadDialogOpen(false)}
        onSubmit={handleUploadStandaloneDocument}
      />
    </main>
  );
}
