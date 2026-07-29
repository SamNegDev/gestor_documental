import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Archive, ArrowRight, CheckSquare2, ClipboardList, Clock3, FileWarning, Inbox, Mail, MessageCircle, SearchCheck, Send } from "lucide-react";
import { Link, useOutletContext } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { ApiError } from "../../../shared/api/http";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { PaginationBar } from "../../listados/components/PaginationBar";
import { getExpedienteListCatalogs } from "../../listados/services/listadosApi";
import { archivarSeguimiento, prepararNotificacionExpediente } from "../../seguimiento/services/seguimientoApi";
import { NotificationEmailDialog } from "../components/NotificationEmailDialog";
import { SelectedEmailPreviewDialog } from "../components/SelectedEmailPreviewDialog";
import { enviarAvisosConjuntos, getTareas, getTareasResumen, revisarTareaWhatsapp } from "../services/tareasApi";
import type { Tarea } from "../types";

const TASK_GROUPS = [
  { value: "", label: "Todas" },
  { value: "REVISION", label: "Por revisar" },
  { value: "AVISAR", label: "Avisar al cliente" },
  { value: "COMPLETAR", label: "Completar información" },
  { value: "SEGUIMIENTO", label: "Seguimiento" },
];

const TASK_TYPE_OPTIONS = [
  { value: "SOLICITUD_PENDIENTE_REVISION", label: "Solicitudes por revisar" },
  { value: "APORTACION_PENDIENTE_REVISION", label: "Aportaciones por revisar" },
  { value: "INFORMACION_ADICIONAL_RECIBIDA", label: "Información adicional recibida" },
  { value: "DOCUMENTO_HABITUAL_REVISION_ANUAL", label: "Documentos para revisión anual" },
  { value: "INCIDENCIA_PENDIENTE_NOTIFICAR", label: "Avisos al cliente" },
  { value: "INCIDENCIA_RECORDATORIO_PENDIENTE", label: "Recordatorios vencidos" },
  { value: "DOCUMENTACION_PENDIENTE_CLIENTE", label: "Documentación pendiente" },
  { value: "INFORMACION_PENDIENTE_CLIENTE", label: "Información pendiente" },
  { value: "JUSTIFICANTE_FINAL_PENDIENTE", label: "Justificantes finales" },
  { value: "JUSTIFICANTE_PROVISIONAL_PENDIENTE", label: "Justificantes provisionales" },
  { value: "COMPROBANTE_PAGO_PENDIENTE", label: "Comprobantes de pago" },
  { value: "INCIDENCIA_PENDIENTE_CLIENTE", label: "Incidencias" },
  { value: "INCIDENCIA_PENDIENTE_ARCHIVAR", label: "Seguimientos por archivar" },
  { value: "EXPEDIENTE_ESTANCADO", label: "Sin actividad" },
  { value: "WHATSAPP_PENDIENTE_REVISION", label: "Mensajes por revisar" },
  { value: "WHATSAPP_PENDIENTE_ASOCIAR", label: "Mensajes sin asociar" },
  { value: "WHATSAPP_ADJUNTO_CLASIFICAR", label: "Adjuntos por clasificar" },
  { value: "WHATSAPP_CONTACTO_SOLICITADO", label: "Contacto solicitado" },
  { value: "WHATSAPP_ESTADO_SOLICITADO", label: "Estado solicitado" },
  { value: "WHATSAPP_MENSAJE_CLIENTE", label: "Mensajes de cliente" },
];
export function TareasPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const isAdmin = user?.rol === "ADMIN";
  const queryClient = useQueryClient();
  const { confirm, dialog } = useConfirmDialog();
  const ambito = isAdmin ? "GESTION" : "CLIENTE";
  const [grupo, setGrupo] = useState("");
  const [tipo, setTipo] = useState("");
  const [prioridad, setPrioridad] = useState("");
  const [clienteId, setClienteId] = useState("");
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(25);
  const [bulkFeedback, setBulkFeedback] = useState<string | null>(null);
  const [selectedTasks, setSelectedTasks] = useState<Record<string, Tarea>>({});
  const [notificacion, setNotificacion] = useState<{ incidenciaId: number; canal: "email" | "whatsapp" } | null>(null);
  const [selectedPreviewOpen, setSelectedPreviewOpen] = useState(false);
  const query = useQuery({
    queryKey: ["tareas", ambito, grupo, tipo, prioridad, clienteId, pagina, tamanio],
    queryFn: () => getTareas({ ambito, grupo, tipo, prioridad, clienteId: isAdmin ? clienteId : "", pagina, tamanio }),
  });
  const catalogs = useQuery({ queryKey: ["expedientes", "catalogos-listado"], queryFn: getExpedienteListCatalogs, enabled: isAdmin });
  const resumen = useQuery({ queryKey: ["tareas", "resumen"], queryFn: getTareasResumen });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["tareas"] });
    queryClient.invalidateQueries({ queryKey: ["seguimiento"] });
  };
  const archiveMutation = useMutation({ mutationFn: archivarSeguimiento, onSuccess: refresh });
  const whatsappReviewMutation = useMutation({ mutationFn: revisarTareaWhatsapp, onSuccess: refresh });

  async function archiveFollowup(id: number) {
    const accepted = await confirm({
      title: "Archivar seguimiento",
      description: "Dejará de generar recordatorios mientras la incidencia siga abierta. Podrás reactivarlo desde Seguimiento de clientes.",
      confirmLabel: "Archivar seguimiento",
      tone: "danger",
    });
    if (accepted) archiveMutation.mutate(id);
  }

  async function markWhatsappReviewed(id: number) {
    const accepted = await confirm({
      title: "Marcar tarea como realizada",
      description: "La tarea desaparecerá de la bandeja. Confirma que ya has revisado y gestionado el mensaje.",
      confirmLabel: "Marcar realizada",
    });
    if (accepted) whatsappReviewMutation.mutate(id);
  }
  const bulkNotify = useMutation({
    mutationFn: () => enviarAvisosConjuntos(clienteId),
    onSuccess: (result) => {
      setBulkFeedback(`${result.clientesEnviados} clientes avisados · ${result.cambiosIncluidos} avisos incluidos${result.avisos.length ? ` · ${result.avisos[0]}` : ""}`);
      refresh();
    },
    onError: (error) => {
      const message = error instanceof ApiError ? error.details || error.message : "No se pudo enviar el aviso conjunto.";
      setBulkFeedback(message);
    },
  });
  async function notify(tarea: Tarea, canal: "email" | "whatsapp") {
    if (tarea.entidad === "INCIDENCIA") {
      setNotificacion({ incidenciaId: tarea.entidadId, canal });
      return;
    }
    try {
      const preview = await prepararNotificacionExpediente(tarea.entidadId);
      setNotificacion({ incidenciaId: preview.incidenciaId, canal });
      refresh();
    } catch (error) {
      const message = error instanceof ApiError ? error.details || error.message : "No se pudo preparar la notificación. Revisa el expediente e inténtalo de nuevo.";
      setBulkFeedback(message);
    }
  }
  useEffect(() => {
    setSelectedTasks({});
  }, [ambito, grupo, tipo, prioridad, clienteId, pagina, tamanio]);
  const data = query.data;
  function previewSelectedNotifications() {
    setSelectedPreviewOpen(true);
  }

  function completeSelectedSend(result: import("../types").ResumenDiarioResponse) {
    setBulkFeedback(`${result.clientesEnviados} cliente avisado · ${result.cambiosIncluidos} avisos incluidos${result.avisos.length ? ` · ${result.avisos[0]}` : ""}`);
    setSelectedTasks({});
    setSelectedPreviewOpen(false);
    refresh();
  }
  async function sendFilteredNotifications() {
    const clientName = catalogs.data?.clientes.find((cliente) => String(cliente.id) === clienteId)?.nombre || "el cliente seleccionado";
    const accepted = await confirm({
      title: "Enviar pendientes del cliente",
      description: `Se agruparán y enviarán todos los avisos pendientes de ${clientName}.`,
      confirmLabel: "Enviar pendientes",
    });
    if (accepted) bulkNotify.mutate();
  }
  const selectedList = Object.values(selectedTasks);
  const selectedClienteIds = Array.from(new Set(selectedList.map((tarea) => tarea.clienteId).filter((id): id is number => typeof id === "number")));
  const activeSelectionClientId = selectedClienteIds.length === 1 ? selectedClienteIds[0] : null;
  const selectableVisibleTasks = data?.contenido.filter((tarea) => isSelectableNotificationTask(tarea)
    && (activeSelectionClientId === null || tarea.clienteId === activeSelectionClientId)) ?? [];
  const selectedIncidentIds = Array.from(new Set(selectedList.flatMap((tarea) => tarea.incidenciaIdsAvisoConjunto ?? [])));
  const selectedWithoutClient = selectedList.some((tarea) => !tarea.clienteId);
  const selectedMixedClients = selectedClienteIds.length > 1;
  const selectedCanNotify = selectedList.length > 0 && selectedIncidentIds.length > 0 && selectedClienteIds.length === 1 && !selectedWithoutClient;
  const selectedClientName = selectedList.find((tarea) => tarea.clienteId === selectedClienteIds[0])?.cliente;
  const allVisibleSelected = selectableVisibleTasks.length > 0 && selectableVisibleTasks.every((tarea) => Boolean(selectedTasks[tarea.id]));
  const toggleTaskSelection = (tarea: Tarea, checked: boolean) => {
    setSelectedTasks((current) => {
      const next = { ...current };
      if (checked) {
        next[tarea.id] = tarea;
      } else {
        delete next[tarea.id];
      }
      return next;
    });
  };
  const toggleVisibleSelection = () => {
    setSelectedTasks((current) => {
      const next = { ...current };
      if (allVisibleSelected) {
        selectableVisibleTasks.forEach((tarea) => delete next[tarea.id]);
      } else {
        selectableVisibleTasks.forEach((tarea) => { next[tarea.id] = tarea; });
      }
      return next;
    });
  };

  return (
    <main className="records-page task-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">Trabajo pendiente</p>
          <h2>{isAdmin ? "Bandeja de tareas" : "Mis tareas"}</h2>
          <p>{isAdmin ? "Acciones de gestion que requieren intervencion." : "Acciones que necesitan tu atencion para continuar los tramites."}</p>
        </div>
        <span className="records-count">{data?.totalElementos ?? 0} pendientes</span>
      </header>

      {bulkFeedback ? <div aria-live="polite" className="form-feedback" role="status">{bulkFeedback}</div> : null}
      {isAdmin && selectedList.length > 0 ? (
        <div className={`task-selection-bar${selectedCanNotify ? "" : " task-selection-bar--blocked"}`}>
          <span>
            {selectedCanNotify
              ? `${selectedList.length} aviso(s) seleccionados para ${selectedClientName || "el cliente"}`
              : selectedMixedClients
                ? "La seleccion mezcla clientes. El aviso conjunto solo se puede crear para un unico cliente."
                : "La seleccion contiene tareas sin cliente asociado."}
          </span>
          <div className="task-selection-bar__actions">
            <button className="soft-button soft-button--compact" onClick={() => setSelectedTasks({})} type="button">Limpiar selección</button>
            <button className="primary-button primary-button--compact" disabled={!selectedCanNotify} onClick={previewSelectedNotifications} type="button">
              <Send size={15} /> Previsualizar aviso
            </button>
          </div>
        </div>
      ) : null}

      <section className="task-summary" aria-label="Accesos rápidos">
        <Summary active={!grupo && !tipo && !prioridad && !clienteId} icon={Inbox} label="Todas" onClick={() => { setGrupo(""); setTipo(""); setPrioridad(""); setClienteId(""); setPagina(0); }} value={resumen.data?.total ?? 0} />
        <Summary active={prioridad === "ALTA"} icon={AlertTriangle} label="Prioridad alta" onClick={() => { setPrioridad("ALTA"); setPagina(0); }} value={resumen.data?.urgentes ?? 0} tone="danger" />
        <Summary active={tipo === "EXPEDIENTE_ESTANCADO"} icon={Clock3} label="Sin actividad" onClick={() => { setGrupo("SEGUIMIENTO"); setTipo("EXPEDIENTE_ESTANCADO"); setPagina(0); }} value={resumen.data?.estancados ?? 0} tone="warning" />
      </section>

      <nav className="task-view-switcher" aria-label="Vista de trabajo">
        {TASK_GROUPS.map((option) => (
          <button className={grupo === option.value && !tipo ? "is-active" : ""} key={option.value || "TODAS"} onClick={() => { setGrupo(option.value); setTipo(""); setPagina(0); }} type="button">{option.label}</button>
        ))}
      </nav>
      <div className="task-filter-bar">
        <div className="task-filters">
          <label>
            <span>Prioridad</span>
            <select value={prioridad} onChange={(event) => { setPrioridad(event.target.value); setPagina(0); }}>
              <option value="">Todas</option><option value="ALTA">Alta</option><option value="MEDIA">Media</option>
            </select>
          </label>
          {isAdmin ? (
            <label className="task-filter--client">
              <span>Cliente</span>
              <select value={clienteId} onChange={(event) => { setClienteId(event.target.value); setPagina(0); }}>
                <option value="">Todos los clientes</option>
                {catalogs.data?.clientes.map((cliente) => <option key={cliente.id} value={cliente.id}>{cliente.nombre}</option>)}
              </select>
            </label>
          ) : null}
          <details className="task-more-filters" open={Boolean(tipo)}>
            <summary>Más filtros{tipo ? " · 1 activo" : ""}</summary>
            <label>
              <span>Tipo concreto</span>
              <select value={tipo} onChange={(event) => { setGrupo(""); setTipo(event.target.value); setPagina(0); }}>
                <option value="">Cualquier tipo</option>
                {TASK_TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </label>
          </details>
        </div>
        <div className="task-filter-bar__actions">
          {grupo || tipo || prioridad || clienteId ? <button className="link-button" onClick={() => { setGrupo(""); setTipo(""); setPrioridad(""); setClienteId(""); setPagina(0); }} type="button">Limpiar filtros</button> : null}
          {isAdmin && clienteId ? (
            <button className="soft-button soft-button--compact" disabled={bulkNotify.isPending} onClick={sendFilteredNotifications} type="button">
              <Send size={15} /> {bulkNotify.isPending ? "Enviando..." : "Enviar pendientes del cliente"}
            </button>
          ) : null}
        </div>
      </div>
      <section className="records-panel records-panel--ledger">
        {isAdmin && selectableVisibleTasks.length > 0 ? (
          <div className="task-selection-toolbar">
            <label>
              <input checked={allVisibleSelected} type="checkbox" onChange={toggleVisibleSelection} />
              <span>{allVisibleSelected ? "Quitar visibles" : "Seleccionar avisos visibles"}</span>
            </label>
            <small>Solo se pueden enviar juntos si pertenecen al mismo cliente.</small>
          </div>
        ) : null}
        {query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        {query.error ? <div className="records-empty records-empty--danger">No se pudo cargar la bandeja.</div> : null}
        <div className="task-list">
          {data?.contenido.length === 0 ? (
            <div className="records-empty">
              <CheckSquare2 size={25} />
              <strong>No hay tareas con estos filtros.</strong>
            </div>
          ) : null}
          {data?.contenido.map((tarea) => (
            <TaskRow
              archivePending={archiveMutation.isPending}
              key={tarea.id}
              onArchive={archiveFollowup}
              onWhatsappReview={markWhatsappReviewed}
              onNotify={notify}
              onSelect={toggleTaskSelection}
              selectable={isAdmin && isSelectableNotificationTask(tarea)}
              selected={Boolean(selectedTasks[tarea.id])}
              selectionDisabled={isAdmin && isSelectableNotificationTask(tarea) && activeSelectionClientId !== null && tarea.clienteId !== activeSelectionClientId && !selectedTasks[tarea.id]}
              selectionDisabledReason={tarea.motivoAvisoConjuntoNoDisponible || (activeSelectionClientId !== null && tarea.clienteId !== activeSelectionClientId ? "Selecciona únicamente avisos del mismo cliente." : null)}
              showClient={isAdmin}
              showSelection={isAdmin && isNotificationTask(tarea)}
              tarea={tarea}
            />
          ))}
        </div>
        <PaginationBar
          page={data?.pagina ?? 0}
          totalPages={data?.totalPaginas ?? 0}
          totalItems={data?.totalElementos ?? 0}
          pageSize={data?.tamanio ?? tamanio}
          onPageChange={setPagina}
          onPageSizeChange={(size) => { setTamanio(size); setPagina(0); }}
        />
      </section>

      <NotificationEmailDialog canal={notificacion?.canal} incidenciaId={notificacion?.incidenciaId ?? null} onClose={() => setNotificacion(null)} onSent={refresh} />
      <SelectedEmailPreviewDialog incidenciaIds={selectedIncidentIds} onClose={() => setSelectedPreviewOpen(false)} onSent={completeSelectedSend} open={selectedPreviewOpen} />
      {dialog}
    </main>
  );
}

function TaskRow({
  tarea,
  archivePending,
  onArchive,
  onWhatsappReview,
  onNotify,
  onSelect,
  selectable,
  selected,
  selectionDisabled,
  selectionDisabledReason,
  showClient,
  showSelection,
}: {
  tarea: Tarea;
  archivePending: boolean;
  onArchive: (id: number) => void;
  onWhatsappReview: (id: number) => void;
  onNotify: (tarea: Tarea, canal: "email" | "whatsapp") => void;
  onSelect: (tarea: Tarea, checked: boolean) => void;
  selectable: boolean;
  selected: boolean;
  selectionDisabled: boolean;
  selectionDisabledReason: string | null;
  showClient: boolean;
  showSelection: boolean;
}) {
  const actionKind = taskActionKind(tarea.tipo);

  return (
    <article className={`task-row task-row--${showClient ? "admin" : "client"} task-row--${tarea.prioridad.toLowerCase()}${tarea.contexto ? " task-row--with-context" : ""}${showSelection ? " task-row--selectable" : ""}${selected ? " is-selected" : ""}`}>
      {showSelection ? (
        <label className="task-row__select" title={selectionDisabledReason || "Seleccionar para aviso conjunto"}>
          <input
            aria-label={selectionDisabledReason ? `${tarea.titulo}: ${selectionDisabledReason}` : `Seleccionar ${tarea.titulo}`}
            checked={selected}
            disabled={!selectable || selectionDisabled}
            type="checkbox"
            onChange={(event) => onSelect(tarea, event.target.checked)}
          />
        </label>
      ) : null}
      <span className={`task-row__icon task-row__icon--${actionKind?.tone ?? "neutral"}`}>{taskIcon(tarea.tipo)}</span>
      <div className="task-row__main">
        <div className="task-row__headline">
          {actionKind ? (
            <div className="task-row__kind">
              <StatusBadge tone={actionKind.tone}>{actionKind.label}</StatusBadge>
            </div>
          ) : null}
          <strong className="task-row__plate">{tarea.matricula || "SIN MATRICULA"}</strong>
        </div>
        <strong>{tarea.titulo}</strong>
        <span>{tarea.detalle}</span>
      </div>
      {tarea.contexto ? (
        <div className="task-row__context">
          <small>{taskContextLabel(tarea.tipo)}</small>
          <p>{tarea.contexto}</p>
        </div>
      ) : null}
      {showClient ? (
        <div className="task-row__client">
          <small>Cliente</small>
          <strong>{tarea.cliente || "Sin cliente"}</strong>
        </div>
      ) : null}
      <div className="task-row__age">
        <small>Antiguedad</small>
        <strong>{tarea.diasPendiente === 0 ? "Hoy" : `${tarea.diasPendiente} dias`}</strong>
        <span>{tarea.fechaReferencia}</span>
      </div>
      <span className={`task-priority task-priority--${tarea.prioridad.toLowerCase()}`}>{tarea.prioridad}</span>
      <div className="task-row__actions">
        {canNotifyTask(tarea) ? (
          <>
            <button aria-label="Enviar correo" className="icon-button" onClick={() => onNotify(tarea, "email")} title="Enviar correo" type="button">
              <Mail size={16} />
            </button>
            <button aria-label="Enviar por WhatsApp" className="soft-button soft-button--compact" onClick={() => onNotify(tarea, "whatsapp")} title="Enviar por WhatsApp" type="button">
              <MessageCircle size={15} />
              WhatsApp
            </button>
          </>
        ) : null}
        {tarea.tipo === "INCIDENCIA_PENDIENTE_ARCHIVAR" ? (
          <button aria-label="Archivar incidencia" className="soft-button soft-button--compact" disabled={archivePending} onClick={() => onArchive(tarea.entidadId)} title="Archivar incidencia" type="button">
            <Archive size={15} />
            Archivar
          </button>
        ) : null}
        {tarea.tipo.startsWith("WHATSAPP_") ? (
          <button aria-label="Marcar realizada" className="soft-button soft-button--compact" onClick={() => onWhatsappReview(tarea.entidadId)} title="Marcar realizada" type="button">
            <CheckSquare2 size={15} />
            Realizada
          </button>
        ) : null}
        <Link aria-label={`Abrir ${tarea.titulo}`} className="icon-button" title="Abrir" to={tarea.enlace}><ArrowRight size={17} /></Link>
      </div>
    </article>
  );
}

function isNotificationTask(tarea: Tarea) {
  return tarea.tipo === "INCIDENCIA_PENDIENTE_NOTIFICAR" || tarea.tipo === "INCIDENCIA_RECORDATORIO_PENDIENTE";
}

function isSelectableNotificationTask(tarea: Tarea) {
  return isNotificationTask(tarea) && Boolean(tarea.incidenciaIdsAvisoConjunto?.length);
}

function canNotifyTask(tarea: Tarea) {
  return (tarea.entidad === "INCIDENCIA" || tarea.entidad === "EXPEDIENTE")
    && (tarea.tipo === "INCIDENCIA_PENDIENTE_NOTIFICAR" || tarea.tipo === "INCIDENCIA_RECORDATORIO_PENDIENTE");
}

function Summary({ active = false, icon: Icon, label, onClick, value, tone = "default" }: { active?: boolean; icon: typeof Inbox; label: string; onClick: () => void; value: number; tone?: string }) {
  return <button aria-pressed={active} className={`task-summary__item task-summary__item--${tone}${active ? " is-active" : ""}`} onClick={onClick} type="button"><Icon aria-hidden="true" size={19} /><span><small>{label}</small><strong>{value}</strong></span></button>;
}

function taskIcon(tipo: string) {
  if (tipo === "EXPEDIENTE_ESTANCADO") return <Clock3 size={19} />;
  if (tipo === "DOCUMENTACION_PENDIENTE_CLIENTE" || tipo === "JUSTIFICANTE_FINAL_PENDIENTE") return <FileWarning size={19} />;
  if (tipo === "INFORMACION_PENDIENTE_CLIENTE") return <MessageCircle size={19} />;
  if (tipo === "WHATSAPP_PENDIENTE_REVISION" || tipo === "WHATSAPP_PENDIENTE_ASOCIAR" || tipo === "WHATSAPP_ADJUNTO_CLASIFICAR" || tipo === "WHATSAPP_CONTACTO_SOLICITADO" || tipo === "WHATSAPP_ESTADO_SOLICITADO") return <MessageCircle size={19} />;
  if (tipo === "APORTACION_PENDIENTE_REVISION") return <SearchCheck size={19} />;
  if (tipo === "SOLICITUD_PENDIENTE_REVISION") return <ClipboardList size={19} />;
  if (tipo.startsWith("INCIDENCIA_")) return <AlertTriangle size={19} />;
  return <AlertTriangle size={19} />;
}

function taskActionKind(tipo: string): { label: string; tone: "neutral" | "warning" | "success" | "danger" | "info" } | null {
  if (tipo === "INCIDENCIA_PENDIENTE_NOTIFICAR") return { label: "AVISO AL CLIENTE", tone: "warning" };
  if (tipo === "INCIDENCIA_RECORDATORIO_PENDIENTE") return { label: "RECORDATORIO", tone: "warning" };
  if (tipo.startsWith("INCIDENCIA_")) return { label: "INCIDENCIA", tone: "danger" };
  if (tipo === "EXPEDIENTE_ESTANCADO") return { label: "SIN ACTIVIDAD", tone: "neutral" };
  if (tipo === "DOCUMENTACION_PENDIENTE_CLIENTE" || tipo === "JUSTIFICANTE_FINAL_PENDIENTE") return { label: "FALTA DOCUMENTACION", tone: "warning" };
  if (tipo === "INFORMACION_PENDIENTE_CLIENTE") return { label: "REQUIERE CONTESTACION", tone: "info" };
  if (tipo === "APORTACION_PENDIENTE_REVISION") return { label: "REVISION", tone: "info" };
  if (tipo === "WHATSAPP_PENDIENTE_ASOCIAR") return { label: "WHATSAPP SIN ASOCIAR", tone: "warning" };
  if (tipo === "WHATSAPP_ADJUNTO_CLASIFICAR") return { label: "WHATSAPP ADJUNTO", tone: "warning" };
  if (tipo === "WHATSAPP_CONTACTO_SOLICITADO") return { label: "WHATSAPP CONTACTO", tone: "danger" };
  if (tipo === "WHATSAPP_ESTADO_SOLICITADO") return { label: "WHATSAPP ESTADO", tone: "info" };
  if (tipo === "WHATSAPP_PENDIENTE_REVISION") return { label: "WHATSAPP", tone: "info" };
  if (tipo === "SOLICITUD_PENDIENTE_REVISION") return { label: "SOLICITUD", tone: "neutral" };
  return null;
}

function taskContextLabel(tipo: string) {
  if (tipo === "EXPEDIENTE_ESTANCADO") return "Motivo";
  if (tipo === "JUSTIFICANTE_FINAL_PENDIENTE") return "Pendiente";
  if (tipo === "WHATSAPP_ADJUNTO_CLASIFICAR") return "Archivo";
  if (tipo === "WHATSAPP_PENDIENTE_REVISION" || tipo === "WHATSAPP_PENDIENTE_ASOCIAR" || tipo === "WHATSAPP_CONTACTO_SOLICITADO" || tipo === "WHATSAPP_ESTADO_SOLICITADO") return "Mensaje";
  return "Detalle solicitado";
}
