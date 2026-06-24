import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AlertTriangle, ArrowLeft, CheckCircle2, FileText, FolderCheck, Info, Loader2, MessageSquare, Pencil, Scissors, Send, UserRound } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { ApiError } from "../../../shared/api/http";
import { uppercaseInput } from "../../../shared/utils/text";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { CompleteExpedienteUploadPanel } from "../../expedientes/components/CompleteExpedienteUploadPanel";
import { OcrReviewDialog } from "../../expedientes/components/OcrReviewDialog";
import {
  deleteDocument,
  deleteDocumentPages,
  extractDocumentPages,
  getCompleteExpedienteProcessing,
  mergeDocuments,
  startCompleteSolicitudProcessing,
  updateDocument,
} from "../../expedientes/services/documentosApi";
import type { DocumentoExpediente, ProcesamientoExpedienteCompleto } from "../../expedientes/types/expedienteDetail.types";
import { formatDocumentType } from "../../expedientes/utils/formatters";
import "../../expedientes/styles/expedienteDetail.css";
import {
  cambiarEstadoSolicitud,
  convertirSolicitud,
  enviarMensajeSolicitud,
  getSolicitudInteresadoCoincidencias,
  getSolicitudDetail,
  procesarSolicitudDocumentacionIa,
} from "../services/listadosApi";
import type { SolicitudDetail, SolicitudDocumentacionIaResponse } from "../types";

const COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX = "gestor.solicitudCompleta.job.";

export function SolicitudDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useOutletContext<AppOutletContext>();
  const [mensaje, setMensaje] = useState("");
  const [ocrReviewOpen, setOcrReviewOpen] = useState(false);
  const [ocrReviewDocuments, setOcrReviewDocuments] = useState<DocumentoExpediente[]>([]);
  const [completeSolicitudProcessing, setCompleteSolicitudProcessing] = useState(false);
  const [completeSolicitudJob, setCompleteSolicitudJob] = useState<ProcesamientoExpedienteCompleto | null>(null);
  const [completeSolicitudMinimized, setCompleteSolicitudMinimized] = useState(false);
  const [checkingInteresados, setCheckingInteresados] = useState(false);
  const [iaResult, setIaResult] = useState<SolicitudDocumentacionIaResponse | null>(null);
  const { confirm, dialog } = useConfirmDialog();
  const isAdmin = user?.rol === "ADMIN";

  const solicitudQuery = useQuery({
    queryKey: ["solicitudes", "detalle", id],
    queryFn: () => getSolicitudDetail(id!),
    enabled: Boolean(id),
  });

  const convertirMutation = useMutation({
    mutationFn: (solicitudId: number) => convertirSolicitud(solicitudId),
    onSuccess: async (expediente) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["expedientes"] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
        queryClient.invalidateQueries({ queryKey: ["registro"] }),
      ]);
      navigate(`/expedientes/${expediente.id}`);
    },
  });

  const estadoMutation = useMutation({
    mutationFn: ({ solicitudId, estado }: { solicitudId: number; estado: string }) => cambiarEstadoSolicitud(solicitudId, estado),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
    },
  });

  const mensajeMutation = useMutation({
    mutationFn: ({ solicitudId, contenido }: { solicitudId: number; contenido: string }) => enviarMensajeSolicitud(solicitudId, contenido),
    onSuccess: () => {
      setMensaje("");
      queryClient.invalidateQueries({ queryKey: ["solicitudes", "detalle", id] });
    },
  });

  const procesarDocumentacionMutation = useMutation({
    mutationFn: (solicitudId: number) => procesarSolicitudDocumentacionIa(solicitudId),
  });

  const refreshSolicitud = async () => {
    const result = await solicitudQuery.refetch();
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
      queryClient.invalidateQueries({ queryKey: ["tareas"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
    ]);
    return result.data;
  };

  useEffect(() => {
    if (!id) return;
    const storedJobId = window.localStorage.getItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
    if (!storedJobId) return;
    getCompleteExpedienteProcessing(storedJobId)
      .then((job) => {
        setCompleteSolicitudJob(job);
        setCompleteSolicitudProcessing(job.estado === "PENDIENTE" || job.estado === "PROCESANDO");
        if (job.estado === "COMPLETADO" || job.estado === "ERROR") {
          window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
        }
      })
      .catch(() => window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`));
  }, [id]);

  useEffect(() => {
    if (!completeSolicitudJob || !id) return;
    if (completeSolicitudJob.estado !== "PENDIENTE" && completeSolicitudJob.estado !== "PROCESANDO") return;
    const intervalId = window.setInterval(() => {
      getCompleteExpedienteProcessing(completeSolicitudJob.jobId)
        .then(async (job) => {
          setCompleteSolicitudJob(job);
          const active = job.estado === "PENDIENTE" || job.estado === "PROCESANDO";
          setCompleteSolicitudProcessing(active);
          if (!active) {
            window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
            await refreshSolicitud();
          }
        })
        .catch(() => {
          setCompleteSolicitudProcessing(false);
          window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
        });
    }, 2500);
    return () => window.clearInterval(intervalId);
  }, [completeSolicitudJob, id]);

  const handleUploadCompleteSolicitud = async (archivo: File) => {
    const solicitud = solicitudQuery.data;
    if (!solicitud) return;
    setCompleteSolicitudProcessing(true);
    setCompleteSolicitudMinimized(false);
    try {
      const job = await startCompleteSolicitudProcessing(solicitud.id, archivo);
      setCompleteSolicitudJob(job);
      window.localStorage.setItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${solicitud.id}`, job.jobId);
      await refreshSolicitud();
    } catch {
      setCompleteSolicitudProcessing(false);
      alert("No se pudo iniciar la separacion del PDF completo de la solicitud.");
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
      await refreshSolicitud();
      setOcrReviewDocuments((current) => current.filter((item) => item.id !== documento.id));
    } catch {
      alert("No se pudo borrar el documento.");
    }
  };

  const handleSaveOcrDocument = async (documento: DocumentoExpediente, tipoDocumento: string) => {
    if (!documento.id) return;
    try {
      await updateDocument(documento.id, tipoDocumento, undefined, undefined, true);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizada.documentos.find((updated) => updated.id === item.id) ?? item),
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
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizada.documentos.find((updated) => updated.id === item.id) ?? item),
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
  ) => {
    if (!documento.id) return;
    try {
      await mergeDocuments(documento.id, documentoIds, tipoDocumento, nombreSinExtension);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) =>
          current
            .map((item) => (item.id ? actualizada.documentos.find((updated) => updated.id === item.id) : item))
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
  ) => {
    if (!documento.id) return;
    try {
      const previousIds = new Set(solicitudQuery.data?.documentos.map((item) => item.id).filter(Boolean));
      await extractDocumentPages(documento.id, rangoPaginas, tipoDocumento);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) => {
          const updatedCurrent = current
            .map((item) => (item.id ? actualizada.documentos.find((updated) => updated.id === item.id) : item))
            .filter((item): item is DocumentoExpediente => Boolean(item));
          const nuevos = actualizada.documentos.filter((item) => item.id && !previousIds.has(item.id));
          return [...updatedCurrent, ...nuevos];
        });
      }
    } catch {
      alert("No se pudieron separar las paginas.");
    }
  };

  const handleOpenDocumentReview = () => {
    setOcrReviewDocuments(solicitudQuery.data?.documentos.filter((documento) => documento.id) ?? []);
    setOcrReviewOpen(true);
  };

  const handleConvertSolicitud = async () => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    setCheckingInteresados(true);
    try {
      const coincidencias = await getSolicitudInteresadoCoincidencias(solicitudActual.id);
      if (coincidencias.length > 0) {
        const confirmed = await confirm({
          title: "Interesado ya registrado",
          description: buildCoincidenciasDescription(coincidencias),
          confirmLabel: "Usar datos registrados",
          cancelLabel: "Revisar solicitud",
          tone: "default",
        });
        if (!confirmed) return;
      }
      convertirMutation.mutate(solicitudActual.id);
    } catch {
      alert("No se pudo comprobar si los interesados ya estaban registrados.");
    } finally {
      setCheckingInteresados(false);
    }
  };

  const handleProcessDocumentacionIa = async () => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    const confirmed = await confirm({
      title: "Procesar documentacion",
      description: solicitudActual.tipoTramite === "BATECOM"
        ? "Se leeran DNI/CIF y contratos/facturas. El sistema buscara la compraventa que aparece como comprador en una operacion y vendedor en otra."
        : "Se leeran solo los DNI/CIF y factura/contrato que no tengan lectura previa. Si ya hay una lectura correcta se reutilizara para actualizar comprador y vendedor.",
      confirmLabel: "Procesar",
      cancelLabel: "Cancelar",
      tone: "default",
    });
    if (!confirmed) return;
    try {
      setIaResult(null);
      const response = await procesarDocumentacionMutation.mutateAsync(solicitudActual.id);
      await refreshSolicitud();
      setIaResult(response);
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudo procesar la documentacion." : "No se pudo procesar la documentacion.");
    }
  };

  if (solicitudQuery.isLoading) {
    return <div className="records-empty">Cargando solicitud...</div>;
  }

  if (solicitudQuery.error || !solicitudQuery.data) {
    return <div className="records-empty records-empty--danger">No se ha podido cargar la solicitud.</div>;
  }

  const solicitud = solicitudQuery.data;
  const isClosed = solicitud.estado === "CONVERTIDA" || solicitud.estado === "RECHAZADO";
  const interesadosVisibles = solicitud.interesados.filter(hasInteresadoData);
  const preparationItems = buildSolicitudPreparationItems(solicitud);

  return (
    <section className="request-page">
      <div className="request-hero">
        <div>
          <p className="eyebrow">{isAdmin ? "Revision de solicitud" : "Seguimiento de solicitud"}</p>
          <div className="case-title-row">
            <MiniPlate value={solicitud.matricula} />
            <div>
              <h2>Solicitud #{solicitud.id}</h2>
              <p>{solicitud.tipoTramite || "Sin tipo de tramite"}</p>
            </div>
          </div>
        </div>
        <div className="request-hero__actions">
          <StatusBadge tone={statusTone(solicitud.estado)}>{formatEnum(solicitud.estado)}</StatusBadge>
          {isAdmin && solicitud.expedienteId ? (
            <Link className="soft-button soft-button--compact" to={`/expedientes/${solicitud.expedienteId}`}>
              <FolderCheck size={16} />
              Ver expediente
            </Link>
          ) : null}
          <Link className="soft-button soft-button--compact" to="/solicitudes">
            <ArrowLeft size={16} />
            Volver
          </Link>
          {!isAdmin && !isClosed ? (
            <Link className="soft-button soft-button--compact" to={`/cliente/solicitudes/${solicitud.id}/editar`}>
              <Pencil size={16} />
              Editar
            </Link>
          ) : null}
        </div>
      </div>

      {isAdmin ? (
        <AdminActions
          solicitud={solicitud}
          isClosed={isClosed}
          onConvert={handleConvertSolicitud}
          onStateChange={(estado) => estadoMutation.mutate({ solicitudId: solicitud.id, estado })}
          pending={checkingInteresados || convertirMutation.isPending || estadoMutation.isPending || procesarDocumentacionMutation.isPending}
        />
      ) : null}

      {iaResult ? <SolicitudIaResultPanel response={iaResult} onDismiss={() => setIaResult(null)} /> : null}

      {!isAdmin ? <ClientStatusCallout solicitud={solicitud} expedientePath={isAdmin ? undefined : "/cliente/expedientes"} /> : null}

      {!isClosed ? (
        <>
          <CompleteExpedienteUploadPanel
            onUploadCompleteExpediente={handleUploadCompleteSolicitud}
            processing={completeSolicitudProcessing}
            processingJob={completeSolicitudJob}
            minimized={completeSolicitudMinimized}
            onToggleMinimized={() => setCompleteSolicitudMinimized((current) => !current)}
            title="Aportar documentacion completa"
            description="Sube el PDF completo de la solicitud y el sistema intentara separar automaticamente los documentos detectados."
          />
          <SolicitudPreparationPanel items={preparationItems} />
        </>
      ) : null}

      <div className="request-grid">
        <section className="panel">
          <h2>Informacion general</h2>
          <dl className="facts facts--compact">
            <div>
              <dt>Cliente</dt>
              <dd>{solicitud.cliente?.nombre || "Sin cliente"}</dd>
            </div>
            <div>
              <dt>Fecha de creacion</dt>
              <dd>{solicitud.fechaCreacion || "Sin fecha"}</dd>
            </div>
            <div>
              <dt>Ultimo cambio</dt>
              <dd>{solicitud.fechaUltimaModificacion || "Sin cambios"}</dd>
            </div>
            <div>
              <dt>Documentacion</dt>
              <dd>{solicitud.situacionDocumental ? formatEnum(solicitud.situacionDocumental) : "Sin revisar"}</dd>
            </div>
          </dl>
          {solicitud.observaciones ? <p className="note">{solicitud.observaciones}</p> : null}
        </section>

        <section className="panel">
          <h2>Interesados</h2>
          <div className="entity-list">
            {interesadosVisibles.length === 0 ? <p className="rail-muted">No hay interesados registrados.</p> : null}
            {interesadosVisibles.map((interesado, index) => (
              <div className="entity-row" key={`${interesado.dni}-${index}`}>
                <div className="row-icon" aria-hidden="true">
                  <UserRound size={18} />
                </div>
                <div>
                  <strong>{interesado.nombre || "Interesado"}</strong>
                  <span>{interesado.rol ? formatEnum(interesado.rol) : "Sin rol"}</span>
                </div>
                <small>{interesado.dni || interesado.telefono || ""}</small>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="request-grid request-grid--wide">
        <section className="panel">
          <div className="panel-heading">
            <h2>Documentos</h2>
            <div className="button-group">
              {isAdmin && !isClosed ? (
                <button
                  className="primary-button primary-button--compact"
                  disabled={procesarDocumentacionMutation.isPending || !solicitud.documentos.some((documento) => documento.id)}
                  onClick={handleProcessDocumentacionIa}
                  type="button"
                >
                  {procesarDocumentacionMutation.isPending ? <Loader2 size={16} /> : <FileText size={16} />}
                  {procesarDocumentacionMutation.isPending ? "Procesando" : "Leer y actualizar"}
                </button>
              ) : null}
              <button
                className="soft-button soft-button--compact"
                disabled={!solicitud.documentos.some((documento) => documento.id)}
                onClick={handleOpenDocumentReview}
                type="button"
              >
                <Scissors size={16} />
                Revisar documentos
              </button>
            </div>
          </div>
          <div className="document-table">
            {solicitud.documentos.length === 0 ? <div className="document-table__empty">No hay documentos asociados.</div> : null}
            {solicitud.documentos.map((documento) => (
              <div className="document-table__row" key={documento.id}>
                <FileText size={20} />
                <div className="document-table__main">
                  <strong>{documento.nombreOriginal || documento.nombre}</strong>
                  <span>{formatDocumentType(documento.tipo)}{documento.interesadoNombre ? ` - ${documento.interesadoNombre}` : ""}</span>
                  <SolicitudDocumentReading documento={documento} />
                </div>
                <small>{documento.fechaSubida || "Sin fecha"}</small>
                <a className="soft-button soft-button--compact" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer">
                  Ver
                </a>
              </div>
            ))}
          </div>
        </section>

        <section className="panel panel--messages">
          <h2>
            <MessageSquare size={18} /> Mensajes
          </h2>
          <div className="message-list">
            {solicitud.mensajes.length === 0 ? <p className="rail-muted">Aun no hay mensajes en esta solicitud.</p> : null}
            {solicitud.mensajes.map((item) => (
              <article className={item.rolAutor === "ADMIN" ? "message message--admin" : "message message--cliente"} key={item.id}>
                <div>
                  <strong>{item.autor}</strong>
                  <span>{item.fechaCreacion}</span>
                </div>
                <p>{item.contenido}</p>
              </article>
            ))}
          </div>
          <form
            className="message-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (mensaje.trim()) {
                mensajeMutation.mutate({ solicitudId: solicitud.id, contenido: mensaje.trim() });
              }
            }}
          >
            <textarea value={mensaje} onChange={(event) => setMensaje(uppercaseInput(event.target.value))} placeholder="Escribe un mensaje..." rows={3} />
            <button className="primary-button primary-button--compact" disabled={mensajeMutation.isPending || !mensaje.trim()}>
              <Send size={16} />
              Enviar
            </button>
          </form>
        </section>
      </div>

      {solicitud.incidencias.length > 0 ? (
        <section className="panel">
          <h2>Incidencias</h2>
          <div className="incident-list">
            {solicitud.incidencias.map((incidencia) => (
              <div className="incident-row" key={incidencia.id}>
                {incidencia.resuelta ? <CheckCircle2 size={20} /> : <AlertTriangle size={20} />}
                <div>
                  <strong>{incidencia.tipo || "Incidencia"}</strong>
                  <p>{incidencia.observaciones}</p>
                  <small>{incidencia.fechaCreacion}</small>
                </div>
                <StatusBadge tone={incidencia.resuelta ? "success" : "danger"}>{incidencia.resuelta ? "Resuelta" : "Abierta"}</StatusBadge>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <OcrReviewDialog
        documentos={ocrReviewDocuments}
        onDeleteDocument={handleDeleteDocument}
        onDeletePages={handleDeleteOcrPages}
        onExtractPages={handleExtractOcrPages}
        onMergeDocuments={handleMergeOcrDocuments}
        onSaveDocument={handleSaveOcrDocument}
        onClose={() => setOcrReviewOpen(false)}
        open={ocrReviewOpen}
      />
      {procesarDocumentacionMutation.isPending ? <SolicitudIaProgressModal /> : null}
      {dialog}
    </section>
  );
}

function SolicitudDocumentReading({ documento }: { documento: DocumentoExpediente }) {
  const identidad = documento.lecturaIdentidad;
  const roles = documento.lecturaRoles;
  if (!identidad && !roles) {
    return null;
  }
  if (identidad) {
    return (
      <div className={identidad.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
        <strong>DNI/CIF detectado</strong>
        <span>{[identidad.identificador, identidad.nombreCompleto].filter(Boolean).join(" - ") || "Sin identificacion clara"}</span>
        {identidad.fechaNacimiento ? <small>Nacimiento: {identidad.fechaNacimiento}</small> : null}
        {identidad.direccionTexto ? <small>{identidad.direccionTexto}</small> : null}
        <em>{confidenceLabel(identidad.confianzaGlobal)} · {identidad.mensaje || (identidad.requiereRevision ? "Revisar lectura" : "Lectura valida")}</em>
      </div>
    );
  }
  return (
    <div className={roles?.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
      <strong>Roles detectados</strong>
      <span>Vendedor: {[roles?.vendedorIdentificador, roles?.vendedorNombre].filter(Boolean).join(" - ") || "Sin dato"}</span>
      <span>Comprador: {[roles?.compradorIdentificador, roles?.compradorNombre].filter(Boolean).join(" - ") || "Sin dato"}</span>
      {[roles?.matricula, roles?.bastidor].filter(Boolean).length ? <small>{[roles?.matricula, roles?.bastidor].filter(Boolean).join(" · ")}</small> : null}
      <em>{confidenceLabel(roles?.confianzaGlobal)} · {roles?.mensaje || roles?.motivoAplicacion || "Lectura registrada"}</em>
    </div>
  );
}

function confidenceLabel(value?: number | null) {
  return typeof value === "number" ? `${Math.round(value * 100)}% confianza` : "Sin confianza";
}

type SolicitudPreparationItem = {
  key: string;
  label: string;
  detail: string;
  missing?: string | null;
  ready: boolean;
};

function SolicitudPreparationPanel({ items }: { items: SolicitudPreparationItem[] }) {
  const pendingItems = items.filter((item) => !item.ready);
  const pending = pendingItems.length;
  return (
    <section className="request-document-guide request-preparation-panel" aria-label="Preparacion documental de la solicitud">
      <div className="request-document-guide__heading">
        {pending > 0 ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}
        <div>
          <strong>Preparacion antes de convertir</strong>
          <span>{pending > 0 ? `Falta completar: ${formatReadableList(pendingItems.map((item) => item.label))}.` : "Interesados, vehiculo y documentos base listos para convertir."}</span>
        </div>
      </div>
      <ul className="request-preparation-list">
        {items.map((item) => (
          <li className={item.ready ? "is-ready" : "is-pending"} key={item.key}>
            {item.ready ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
            <span>
              <strong>{item.label}</strong>
              <small>{item.detail}</small>
              {!item.ready && item.missing ? <em>{item.missing}</em> : null}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function SolicitudIaResultPanel({ response, onDismiss }: { response: SolicitudDocumentacionIaResponse; onDismiss: () => void }) {
  const tone = response.requiereRevision ? "warning" : response.datosAplicados || response.yaEstabaCorrecta ? "success" : "info";
  const title = response.requiereRevision
    ? "Lectura completada con revision pendiente"
    : response.yaEstabaCorrecta
      ? "La solicitud ya estaba correcta"
      : response.datosAplicados
        ? "Datos aplicados a la solicitud"
        : "Lectura completada";
  return (
    <section className={`solicitud-ia-result solicitud-ia-result--${tone}`} role="status" aria-live="polite">
      <div className="solicitud-ia-result__heading">
        {tone === "warning" ? <AlertTriangle size={20} /> : tone === "success" ? <CheckCircle2 size={20} /> : <Info size={20} />}
        <div>
          <strong>{title}</strong>
          <span>{response.mensaje || "Proceso finalizado."}</span>
        </div>
        <button className="soft-button soft-button--compact" type="button" onClick={onDismiss}>Cerrar</button>
      </div>
      <div className="solicitud-ia-result__metrics">
        <span>Identidades: {response.lecturasIdentidadNuevas} nuevas / {response.lecturasIdentidadReutilizadas} reutilizadas</span>
        <span>Roles: {response.lecturasRolesNuevas} nuevas / {response.lecturasRolesReutilizadas} reutilizadas</span>
      </div>
      {response.detalles?.length ? (
        <ul>
          {response.detalles.slice(0, 5).map((detalle) => <li key={detalle}>{detalle}</li>)}
        </ul>
      ) : null}
    </section>
  );
}

function AdminActions({
  solicitud,
  isClosed,
  pending,
  onConvert,
  onStateChange,
}: {
  solicitud: SolicitudDetail;
  isClosed: boolean;
  pending: boolean;
  onConvert: () => void;
  onStateChange: (estado: string) => void;
}) {
  return (
    <section className="rail-card rail-card--action">
      <div className="rail-card__heading">
        <h3>Acciones administrativas</h3>
        <StatusBadge tone={statusTone(solicitud.estado)}>{formatEnum(solicitud.estado)}</StatusBadge>
      </div>
      {isClosed ? <p>Esta solicitud ya no admite conversion directa.</p> : null}
      <div className="button-group">
        {!isClosed ? (
          <>
            <button className="primary-button primary-button--compact" disabled={pending} onClick={onConvert}>
              <FolderCheck size={16} />
              Convertir
            </button>
            <button className="soft-button soft-button--compact" disabled={pending} onClick={() => onStateChange("PENDIENTE_DOCUMENTACION")}>
              Pedir documentacion
            </button>
            <button className="soft-button soft-button--compact" disabled={pending} onClick={() => onStateChange("RECHAZADO")}>
              Rechazar
            </button>
          </>
        ) : null}
        {solicitud.estado === "PENDIENTE_DOCUMENTACION" ? (
          <button className="soft-button soft-button--compact" disabled={pending} onClick={() => onStateChange("PENDIENTE_REVISION")}>
            Volver a revision
          </button>
        ) : null}
      </div>
    </section>
  );
}

function SolicitudIaProgressModal() {
  return (
    <div className="ga-progress-modal" role="status" aria-live="polite">
      <div className="ga-progress-modal__panel">
        <div className="ga-progress-modal__heading">
          <span className="ga-progress-modal__spinner">
            <Loader2 size={22} />
          </span>
          <div>
            <p className="eyebrow">Procesando documentacion</p>
            <h3>Leyendo DNI/CIF y factura</h3>
          </div>
          <strong>IA</strong>
        </div>
        <div className="ga-progress-modal__bar">
          <span style={{ width: "72%" }} />
        </div>
        <div className="ga-progress-modal__status">
          <strong>Actualizando solicitud</strong>
          <span>Reutilizando lecturas validas cuando ya existen</span>
        </div>
        <ul className="ga-progress-modal__steps">
          <li className="is-active">
            <span />
            Localizar identidades
          </li>
          <li className="is-active">
            <span />
            Leer factura o contrato
          </li>
          <li className="is-active">
            <span />
            Aplicar comprador y vendedor
          </li>
        </ul>
      </div>
    </div>
  );
}

function ClientStatusCallout({ solicitud, expedientePath = "/expedientes" }: { solicitud: SolicitudDetail; expedientePath?: string }) {
  if (solicitud.estado === "CONVERTIDA" && solicitud.expedienteId) {
    return (
      <section className="request-status-callout request-status-callout--success">
        <CheckCircle2 size={24} />
        <div>
          <strong>Tu solicitud ya se ha convertido en expediente.</strong>
          <p>Desde ahora puedes seguir el avance del tramite en la ficha del expediente.</p>
        </div>
        <Link className="primary-button primary-button--compact" to={`${expedientePath}/${solicitud.expedienteId}`}>
          Ver expediente
        </Link>
      </section>
    );
  }

  const statusCopy = clientStatusCopy(solicitud.estado);
  return (
    <section className={`request-status-callout request-status-callout--${statusCopy.tone}`}>
      {statusCopy.tone === "warning" ? <AlertTriangle size={24} /> : <MessageSquare size={24} />}
      <div>
        <strong>{statusCopy.title}</strong>
        <p>{statusCopy.copy}</p>
      </div>
    </section>
  );
}

function clientStatusCopy(status?: string | null) {
  if (status === "PENDIENTE_DOCUMENTACION") {
    return {
      tone: "warning",
      title: "Necesitamos que completes la documentacion.",
      copy: "Revisa los mensajes y documentos asociados. Cuando lo tengas listo podremos volver a revisar la solicitud.",
    };
  }
  if (status === "REVISANDO_INCIDENCIAS") {
    return {
      tone: "info",
      title: "Estamos revisando la incidencia.",
      copy: "La gestoria esta comprobando la informacion aportada antes de continuar con el tramite.",
    };
  }
  if (status === "RECHAZADO") {
    return {
      tone: "danger",
      title: "Esta solicitud ha sido rechazada.",
      copy: "Puedes consultar el historial y los mensajes para ver el motivo registrado.",
    };
  }
  return {
    tone: "info",
    title: "Tu solicitud esta pendiente de revision.",
    copy: "La gestoria revisara los datos y la documentacion antes de convertirla en expediente.",
  };
}

function MiniPlate({ value }: { value?: string | null }) {
  if (!value) {
    return <span className="muted-text">Sin matricula</span>;
  }
  return (
    <span className="plate plate--large">
      <span>E</span>
      <strong>{value}</strong>
    </span>
  );
}

function statusTone(status?: string | null) {
  if (status === "CONVERTIDA") return "success";
  if (status === "RECHAZADO" || status === "PENDIENTE_DOCUMENTACION") return "danger";
  if (status === "REVISANDO_INCIDENCIAS") return "info";
  if (status === "PENDIENTE_REVISION") return "warning";
  return "neutral";
}

function formatEnum(value?: string | null) {
  return value ? value.replaceAll("_", " ") : "Sin estado";
}

function hasInteresadoData(interesado: { nombre?: string | null; rol?: string | null; dni?: string | null; telefono?: string | null; direccion?: string | null }) {
  return [interesado.nombre, interesado.rol, interesado.dni, interesado.telefono, interesado.direccion].some(
    (value) => value && value.trim() !== "",
  );
}

function buildSolicitudPreparationItems(solicitud: SolicitudDetail): SolicitudPreparationItem[] {
  const uploadedTypes = new Set(solicitud.documentos.map((documento) => documento.tipo));
  const expectedIdentityCount = expectedIdentities(solicitud.tipoTramite);
  const interesados = solicitud.interesados.filter(hasInteresadoData);
  const expectedRoleKeys = expectedRoles(solicitud.tipoTramite);
  const expectedRoleLabels = expectedRoleKeys.map(roleLabel);
  const missingRoleLabels = expectedRoleKeys
    .filter((rol) => !interesados.some((item) => item.rol === rol && item.nombre && item.dni))
    .map(roleLabel);
  const incompleteLabels = interesados
    .filter((item) => !item.nombre || !item.dni || !item.rol)
    .map(interesadoLabel);
  const missingIdentities = missingIdentityTargets(expectedRoleKeys, interesados);
  const identityCoveredCount = Math.max(0, expectedIdentityCount - missingIdentities.length);
  const representativesMissing = interesados.filter((item) => item.requiereRepresentanteLegal && !item.representanteLegalAportado);
  const representativesCovered = interesados.filter((item) => item.requiereRepresentanteLegal && item.representanteLegalAportado);
  const identityReady = missingIdentities.length === 0 && representativesMissing.length === 0;
  const roleDocsCount = solicitud.documentos.filter((documento) => documento.tipo === "CONTRATO_COMPRAVENTA" || documento.tipo === "FACTURA").length;
  const roleDocsReady = solicitud.tipoTramite === "BATECOM"
    ? roleDocsCount >= 2
    : roleDocsCount >= 1;
  const mandateReady = uploadedTypes.has("MANDATO") || uploadedTypes.has("MANDATO_REPRESENTACION");
  const vehicleMissingDocs = missingVehicleDocs(uploadedTypes);
  const vehicleReady = vehicleMissingDocs.length === 0;
  const interesadosReady = missingRoleLabels.length === 0 && incompleteLabels.length === 0;
  return [
    {
      key: "interesados",
      label: "Interesados y roles",
      detail: interesadosReady
        ? `${formatReadableList(expectedRoleLabels)} identificados con nombre, DNI/CIF y rol.`
        : `${interesados.length}/${expectedIdentityCount} bloque(s) informados.`,
      missing: missingRoleLabels.length > 0
        ? `Falta bloque de ${formatReadableList(missingRoleLabels)}.`
        : incompleteLabels.length > 0
          ? `Completa nombre, DNI/CIF y rol de ${formatReadableList(incompleteLabels)}.`
          : null,
      ready: interesadosReady,
    },
    {
      key: "identidades",
      label: representativesMissing.length > 0 || representativesCovered.length > 0 ? "DNI/CIF y administrador" : "DNI/CIF",
      detail: identityReady
        ? identityReadyDetail(identityCoveredCount, expectedIdentityCount, representativesCovered)
        : `${identityCoveredCount}/${expectedIdentityCount} identidad(es) principales cubiertas.`,
      missing: identityMissingDetail(missingIdentities, representativesMissing),
      ready: identityReady,
    },
    {
      key: "contrato",
      label: solicitud.tipoTramite === "BATECOM" ? "Contratos BATE/COM" : "Factura o contrato",
      detail: roleDocsReady
        ? solicitud.tipoTramite === "BATECOM"
          ? "Dos operaciones disponibles para detectar la compraventa comun."
          : "Disponible para leer comprador y vendedor."
        : solicitud.tipoTramite === "BATECOM"
          ? `${roleDocsCount}/2 contrato(s) o factura(s) disponibles.`
          : "Necesario para fijar roles con seguridad.",
      missing: roleDocsReady || solicitud.tipoTramite === "CAMBIO_DOMICILIO"
        ? null
        : solicitud.tipoTramite === "BATECOM"
          ? "Faltan contratos/facturas de entrega a compraventa y venta final."
          : "Falta factura o contrato para confirmar comprador y vendedor.",
      ready: roleDocsReady || solicitud.tipoTramite === "CAMBIO_DOMICILIO",
    },
    {
      key: "mandato",
      label: "Mandato",
      detail: mandateReady ? "Autorizacion aportada." : "Falta mandato o representacion.",
      missing: mandateReady ? null : "Sube mandato o mandato de representacion.",
      ready: mandateReady,
    },
    {
      key: "vehiculo",
      label: "Vehiculo",
      detail: vehicleReady ? vehicleReadyDetail(uploadedTypes) : vehiclePendingDetail(uploadedTypes, vehicleMissingDocs),
      missing: vehicleReady ? null : vehicleMissingDetail(vehicleMissingDocs),
      ready: vehicleReady,
    },
  ];
}

function expectedIdentities(tipoTramite?: string | null) {
  if (tipoTramite === "BATECOM") return 3;
  if (tipoTramite === "TRASPASO" || tipoTramite === "NOTIFICACION_VENTA") return 2;
  return 1;
}

function expectedRoles(tipoTramite?: string | null) {
  if (tipoTramite === "BATECOM") return ["VENDEDOR", "COMPRAVENTA", "COMPRADOR"];
  if (tipoTramite === "TRASPASO" || tipoTramite === "NOTIFICACION_VENTA") return ["VENDEDOR", "COMPRADOR"];
  return ["TITULAR"];
}

function roleLabel(rol?: string | null) {
  const labels: Record<string, string> = {
    VENDEDOR: "vendedor",
    COMPRADOR: "comprador",
    COMPRAVENTA: "compraventa",
    TITULAR: "titular",
  };
  return rol ? labels[rol] || formatEnum(rol).toLowerCase() : "interesado";
}

function interesadoLabel(interesado: { rol?: string | null; nombre?: string | null }) {
  return roleLabel(interesado.rol) || interesado.nombre || "interesado";
}

type IdentityTarget = {
  documentLabel: string;
  ownerLabel: string;
};

function missingIdentityTargets(
  expectedRolesList: string[],
  interesados: Array<{
    rol?: string | null;
    nombre?: string | null;
    dni?: string | null;
    personaJuridica?: boolean;
    documentoIdentidadAportado?: boolean;
  }>,
): IdentityTarget[] {
  return expectedRolesList
    .map((rol) => {
      const interesado = interesados.find((item) => item.rol === rol);
      if (!interesado) {
        return { documentLabel: "DNI/CIF", ownerLabel: roleLabel(rol) };
      }
      if (interesado.documentoIdentidadAportado) {
        return null;
      }
      return {
        documentLabel: interesado.personaJuridica ? "CIF" : "DNI/NIE",
        ownerLabel: identityOwnerLabel(interesado),
      };
    })
    .filter(Boolean) as IdentityTarget[];
}

function identityOwnerLabel(interesado: { rol?: string | null; nombre?: string | null; dni?: string | null }) {
  const rol = roleLabel(interesado.rol);
  const nombre = interesado.nombre?.trim();
  const dni = interesado.dni?.trim();
  if (nombre && dni) return `${rol} ${nombre} (${dni})`;
  if (nombre) return `${rol} ${nombre}`;
  if (dni) return `${rol} ${dni}`;
  return rol;
}

function identityReadyDetail(
  identityCoveredCount: number,
  expectedIdentityCount: number,
  representativesCovered: Array<{ representanteLegalNombre?: string | null }>,
) {
  if (representativesCovered.length > 0) {
    const names = representativesCovered.map((item) => item.representanteLegalNombre).filter(Boolean) as string[];
    return names.length > 0
      ? `Identidades cubiertas y administrador detectado: ${formatReadableList(names)}.`
      : "Identidades y administrador cubiertos.";
  }
  return `${identityCoveredCount}/${expectedIdentityCount} identidad(es) cubiertas.`;
}

function identityMissingDetail(
  missingIdentities: IdentityTarget[],
  representativesMissing: Array<{
    nombre?: string | null;
    rol?: string | null;
    representanteLegalNombre?: string | null;
    representanteLegalDni?: string | null;
  }>,
) {
  const parts: string[] = [];
  missingIdentities.forEach((item) => parts.push(`Falta ${item.documentLabel} de ${item.ownerLabel}.`));
  representativesMissing.forEach((item) => {
    const representante = item.representanteLegalNombre
      ? `${item.representanteLegalNombre}${item.representanteLegalDni ? ` (${item.representanteLegalDni})` : ""}`
      : `la empresa ${item.nombre || roleLabel(item.rol)}`;
    parts.push(`Falta DNI del administrador ${representante}.`);
  });
  return parts.length > 0 ? parts.join(" ") : null;
}

function missingVehicleDocs(uploadedTypes: Set<string | null | undefined>) {
  if (uploadedTypes.has("INFORME_DGT")) return [];
  return [
    !uploadedTypes.has("PERMISO_CIRCULACION") ? "permiso de circulacion" : null,
    !uploadedTypes.has("FICHA_TECNICA") ? "ficha tecnica" : null,
  ].filter(Boolean) as string[];
}

function vehicleReadyDetail(uploadedTypes: Set<string | null | undefined>) {
  if (uploadedTypes.has("INFORME_DGT")) return "Informe DGT disponible para permiso y ficha.";
  return "Permiso de circulacion y ficha tecnica disponibles.";
}

function vehiclePendingDetail(uploadedTypes: Set<string | null | undefined>, missingDocs: string[]) {
  const availableDocs = [
    uploadedTypes.has("PERMISO_CIRCULACION") ? "permiso de circulacion" : null,
    uploadedTypes.has("FICHA_TECNICA") ? "ficha tecnica" : null,
  ].filter(Boolean) as string[];
  if (availableDocs.length === 0) return "Sin documentacion tecnica del vehiculo.";
  return `${formatReadableList(availableDocs)} disponible; falta ${formatReadableList(missingDocs)}.`;
}

function vehicleMissingDetail(missingDocs: string[]) {
  if (missingDocs.length === 0) return null;
  const missing = formatReadableList(missingDocs);
  return missingDocs.length > 1
    ? `Faltan ${missing}, o sube Informe DGT.`
    : `Falta ${missing}. Tambien puede cubrirse con Informe DGT.`;
}

function formatReadableList(values: string[]) {
  const clean = values.map((value) => value.trim()).filter(Boolean);
  if (clean.length === 0) return "";
  if (clean.length === 1) return clean[0];
  if (clean.length === 2) return `${clean[0]} y ${clean[1]}`;
  return `${clean.slice(0, -1).join(", ")} y ${clean[clean.length - 1]}`;
}

function buildCoincidenciasDescription(coincidencias: Array<{
  rol?: string | null;
  dni?: string | null;
  camposDiferentes: string[];
  nombreRegistrado?: string | null;
  nombreDeclarado?: string | null;
  telefonoRegistrado?: string | null;
  telefonoDeclarado?: string | null;
  direccionRegistrada?: string | null;
  direccionDeclarada?: string | null;
}>) {
  return coincidencias
    .map((item) => {
      const diferencias = item.camposDiferentes.join(", ");
      const registrado = [item.nombreRegistrado, item.telefonoRegistrado, item.direccionRegistrada].filter(Boolean).join(" / ");
      const declarado = [item.nombreDeclarado, item.telefonoDeclarado, item.direccionDeclarada].filter(Boolean).join(" / ");
      return `${item.rol ? formatEnum(item.rol) + " - " : ""}${item.dni}: cambian ${diferencias}. Registro: ${registrado || "sin datos"}. Solicitud: ${declarado || "sin datos"}.`;
    })
    .join("\n");
}
