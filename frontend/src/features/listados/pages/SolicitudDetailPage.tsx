import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AlertTriangle, ArrowLeft, CheckCircle2, Circle, FileText, FolderCheck, Info, Loader2, MessageSquare, Pencil, Scissors, Send, UserRound } from "lucide-react";
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
  mergeDocuments,
  updateDocument,
  uploadSolicitudDocument,
} from "../../expedientes/services/documentosApi";
import type { DocumentoExpediente } from "../../expedientes/types/expedienteDetail.types";
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

export function SolicitudDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useOutletContext<AppOutletContext>();
  const [mensaje, setMensaje] = useState("");
  const [ocrReviewOpen, setOcrReviewOpen] = useState(false);
  const [ocrReviewDocuments, setOcrReviewDocuments] = useState<DocumentoExpediente[]>([]);
  const [completeSolicitudProcessing, setCompleteSolicitudProcessing] = useState(false);
  const [checkingInteresados, setCheckingInteresados] = useState(false);
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

  const handleUploadCompleteSolicitud = async (archivo: File) => {
    const solicitud = solicitudQuery.data;
    if (!solicitud) return;
    const documentosPrevios = new Set(solicitud.documentos.map((documento) => documento.id).filter(Boolean));
    setCompleteSolicitudProcessing(true);
    try {
      await uploadSolicitudDocument(solicitud.id, "EXPEDIENTE_COMPLETO", archivo);
      const actualizada = await refreshSolicitud();
      if (!actualizada) return;
      const nuevos = actualizada.documentos.filter(
        (documento) => documento.id && !documentosPrevios.has(documento.id) && documento.tipo !== "EXPEDIENTE_COMPLETO",
      );
      setOcrReviewDocuments(nuevos.length > 0 ? nuevos : actualizada.documentos.filter((documento) => documento.id));
      setOcrReviewOpen(true);
    } catch {
      alert("No se pudo procesar el PDF completo de la solicitud.");
    } finally {
      setCompleteSolicitudProcessing(false);
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
      description:
        "Se leeran solo los DNI/CIF y factura/contrato que no tengan lectura previa. Si ya hay una lectura correcta se reutilizara para actualizar comprador y vendedor.",
      confirmLabel: "Procesar",
      cancelLabel: "Cancelar",
      tone: "default",
    });
    if (!confirmed) return;
    try {
      const response = await procesarDocumentacionMutation.mutateAsync(solicitudActual.id);
      await refreshSolicitud();
      alert(buildSolicitudIaResultMessage(response));
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
  const documentosOrientativosPendientes = getInformativeMissingDocuments(solicitud);

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

      {!isAdmin ? <ClientStatusCallout solicitud={solicitud} expedientePath={isAdmin ? undefined : "/cliente/expedientes"} /> : null}

      {!isClosed ? (
        <>
          <CompleteExpedienteUploadPanel
            onUploadCompleteExpediente={handleUploadCompleteSolicitud}
            processing={completeSolicitudProcessing}
            title="Aportar documentacion completa"
            description="Sube el PDF completo de la solicitud y el sistema intentara separar automaticamente los documentos detectados."
          />
          <section className="request-document-guide" aria-label="Documentacion orientativa pendiente">
            <div className="request-document-guide__heading">
              <Info size={18} />
              <div>
                <strong>Documentacion orientativa</strong>
                <span>Esta lista es informativa y no bloquea la solicitud.</span>
              </div>
            </div>
            {documentosOrientativosPendientes.length > 0 ? (
              <ul>
                {documentosOrientativosPendientes.map((documento) => (
                  <li key={documento}>
                    <Circle size={9} />
                    <span>{documento}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="request-document-guide__complete">
                <CheckCircle2 size={16} />
                No se detectan documentos basicos pendientes.
              </p>
            )}
          </section>
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
                <div>
                  <strong>{documento.nombreOriginal || documento.nombre}</strong>
                  <span>{formatDocumentType(documento.tipo)}</span>
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
      {completeSolicitudProcessing ? (
        <div className="exp-processing-overlay" role="status" aria-live="polite">
          <div className="exp-processing-overlay__panel">
            <Loader2 className="exp-processing-overlay__spinner" size={34} />
            <div>
              <p className="eyebrow">Procesando OCR</p>
              <h3>Separando PDF completo</h3>
              <p>Estamos leyendo el archivo, detectando documentos y preparando la revision.</p>
            </div>
          </div>
        </div>
      ) : null}
      {procesarDocumentacionMutation.isPending ? <SolicitudIaProgressModal /> : null}
      {dialog}
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

function buildSolicitudIaResultMessage(response: SolicitudDocumentacionIaResponse) {
  const consumo = `Lecturas nuevas: identidad ${response.lecturasIdentidadNuevas}, roles ${response.lecturasRolesNuevas}. Reutilizadas: identidad ${response.lecturasIdentidadReutilizadas}, roles ${response.lecturasRolesReutilizadas}.`;
  const estado = response.requiereRevision
    ? "Requiere revision manual."
    : response.yaEstabaCorrecta
      ? "No se han hecho cambios."
      : response.datosAplicados
        ? "Datos aplicados."
        : "Proceso completado.";
  const detalles = response.detalles?.length ? `\n\n${response.detalles.slice(0, 6).join("\n")}` : "";
  return `${response.mensaje || "Proceso completado."}\n${estado}\n${consumo}${detalles}`;
}

function getInformativeMissingDocuments(solicitud: SolicitudDetail) {
  const uploadedTypes = new Set(solicitud.documentos.map((documento) => documento.tipo));
  const missing: string[] = [];
  const expectedIdentityCount =
    solicitud.tipoTramite === "BATECOM"
      ? 3
      : solicitud.tipoTramite === "TRASPASO" || solicitud.tipoTramite === "NOTIFICACION_VENTA"
        ? 2
        : 1;
  const uploadedIdentityCount = solicitud.documentos.filter((documento) => documento.tipo === "DNI" || documento.tipo === "CIF").length;

  if (uploadedIdentityCount < expectedIdentityCount) {
    missing.push(expectedIdentityCount === 1 ? "DNI o CIF del interesado" : "DNI o CIF de los interesados");
  }
  if (
    (solicitud.tipoTramite === "TRASPASO" || solicitud.tipoTramite === "BATECOM" || solicitud.tipoTramite === "NOTIFICACION_VENTA")
    && !uploadedTypes.has("CONTRATO_COMPRAVENTA")
    && !uploadedTypes.has("FACTURA")
  ) {
    missing.push("Contrato de compraventa o factura de venta");
  }
  if (!uploadedTypes.has("MANDATO") && !uploadedTypes.has("MANDATO_REPRESENTACION")) {
    missing.push("Mandato o autorizacion de gestion");
  }
  if (!uploadedTypes.has("INFORME_DGT") && !uploadedTypes.has("PERMISO_CIRCULACION")) {
    missing.push("Permiso de circulacion o Informe DGT");
  }
  if (!uploadedTypes.has("INFORME_DGT") && !uploadedTypes.has("FICHA_TECNICA")) {
    missing.push("Ficha tecnica o Informe DGT");
  }
  return missing;
}
