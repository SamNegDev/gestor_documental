import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Archive, ArrowRight, CheckSquare2, ClipboardList, Clock3, FileWarning, Inbox, Mail, MessageCircle, SearchCheck } from "lucide-react";
import { Link, useOutletContext } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { PaginationBar } from "../../listados/components/PaginationBar";
import { archivarSeguimiento, prepararNotificacionExpediente } from "../../seguimiento/services/seguimientoApi";
import { NotificationEmailDialog } from "../components/NotificationEmailDialog";
import { getTareas, getTareasResumen, revisarTareaWhatsapp } from "../services/tareasApi";
import type { Tarea } from "../types";

export function TareasPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const isAdmin = user?.rol === "ADMIN";
  const queryClient = useQueryClient();
  const ambito = isAdmin ? "GESTION" : "CLIENTE";
  const [tipo, setTipo] = useState("");
  const [prioridad, setPrioridad] = useState("");
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(25);
  const [notificacion, setNotificacion] = useState<{ incidenciaId: number; canal: "email" | "whatsapp" } | null>(null);
  const query = useQuery({
    queryKey: ["tareas", ambito, tipo, prioridad, pagina, tamanio],
    queryFn: () => getTareas({ ambito, tipo, prioridad, pagina, tamanio }),
  });
  const resumen = useQuery({ queryKey: ["tareas", "resumen"], queryFn: getTareasResumen });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["tareas"] });
    queryClient.invalidateQueries({ queryKey: ["seguimiento"] });
  };
  const archiveMutation = useMutation({ mutationFn: archivarSeguimiento, onSuccess: refresh });
  const whatsappReviewMutation = useMutation({ mutationFn: revisarTareaWhatsapp, onSuccess: refresh });
  async function notify(tarea: Tarea, canal: "email" | "whatsapp") {
    if (tarea.entidad === "INCIDENCIA") {
      setNotificacion({ incidenciaId: tarea.entidadId, canal });
      return;
    }
    try {
      const preview = await prepararNotificacionExpediente(tarea.entidadId);
      setNotificacion({ incidenciaId: preview.incidenciaId, canal });
      refresh();
    } catch {
      alert("No se pudo preparar la notificacion.");
    }
  }
  const data = query.data;

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

      <section className="task-summary">
        <Summary icon={Inbox} label="Total" value={resumen.data?.total ?? 0} />
        <Summary icon={AlertTriangle} label="Prioridad alta" value={resumen.data?.urgentes ?? 0} tone="danger" />
        <Summary icon={Clock3} label="Sin actividad" value={resumen.data?.estancados ?? 0} tone="warning" />
      </section>

      <div className="task-filters">
        <label>
          <span>Tipo</span>
          <select value={tipo} onChange={(event) => { setTipo(event.target.value); setPagina(0); }}>
            <option value="">Todas las tareas</option>
            <option value="SOLICITUD_PENDIENTE_REVISION">Solicitudes por revisar</option>
            <option value="APORTACION_PENDIENTE_REVISION">Aportaciones por revisar</option>
            <option value="INCIDENCIA_PENDIENTE_NOTIFICAR">Avisos al cliente</option>
            <option value="INCIDENCIA_PENDIENTE_ARCHIVAR">Seguimientos por archivar</option>
            <option value="INCIDENCIA_PENDIENTE_CLIENTE">Incidencias</option>
            <option value="DOCUMENTACION_PENDIENTE_CLIENTE">Documentacion pendiente</option>
            <option value="INFORMACION_PENDIENTE_CLIENTE">Informacion pendiente</option>
            <option value="JUSTIFICANTE_FINAL_PENDIENTE">Justificantes finales</option>
            <option value="WHATSAPP_PENDIENTE_REVISION">WhatsApp por revisar</option>
            <option value="WHATSAPP_PENDIENTE_ASOCIAR">WhatsApp sin asociar</option>
            <option value="WHATSAPP_ADJUNTO_CLASIFICAR">WhatsApp: adjuntos</option>
            <option value="WHATSAPP_APORTACION_INDICADA">WhatsApp: aportacion indicada</option>
            <option value="WHATSAPP_CONTACTO_SOLICITADO">WhatsApp: contacto solicitado</option>
            <option value="WHATSAPP_ESTADO_SOLICITADO">WhatsApp: estado solicitado</option>
            <option value="EXPEDIENTE_ESTANCADO">Sin actividad</option>
          </select>
        </label>
        <label>
          <span>Prioridad</span>
          <select value={prioridad} onChange={(event) => { setPrioridad(event.target.value); setPagina(0); }}>
            <option value="">Todas</option>
            <option value="ALTA">Alta</option>
            <option value="MEDIA">Media</option>
          </select>
        </label>
      </div>

      <section className="records-panel records-panel--ledger">
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
              onArchive={(id) => archiveMutation.mutate(id)}
              onWhatsappReview={(id) => whatsappReviewMutation.mutate(id)}
              onNotify={notify}
              showClient={isAdmin}
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
    </main>
  );
}

function TaskRow({
  tarea,
  archivePending,
  onArchive,
  onWhatsappReview,
  onNotify,
  showClient,
}: {
  tarea: Tarea;
  archivePending: boolean;
  onArchive: (id: number) => void;
  onWhatsappReview: (id: number) => void;
  onNotify: (tarea: Tarea, canal: "email" | "whatsapp") => void;
  showClient: boolean;
}) {
  const actionKind = taskActionKind(tarea.tipo);

  return (
    <article className={`task-row task-row--${showClient ? "admin" : "client"} task-row--${tarea.prioridad.toLowerCase()}${tarea.contexto ? " task-row--with-context" : ""}`}>
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
        {tarea.tipo === "INCIDENCIA_PENDIENTE_NOTIFICAR" ? (
          <>
            <button className="icon-button" onClick={() => onNotify(tarea, "email")} title="Enviar correo" type="button">
              <Mail size={16} />
            </button>
            <button className="soft-button soft-button--compact" onClick={() => onNotify(tarea, "whatsapp")} type="button">
              <MessageCircle size={15} />
              WhatsApp
            </button>
          </>
        ) : null}
        {tarea.tipo === "INCIDENCIA_PENDIENTE_ARCHIVAR" ? (
          <button className="soft-button soft-button--compact" disabled={archivePending} onClick={() => onArchive(tarea.entidadId)} type="button">
            <Archive size={15} />
            Archivar
          </button>
        ) : null}
        {tarea.tipo.startsWith("WHATSAPP_") ? (
          <button className="soft-button soft-button--compact" onClick={() => onWhatsappReview(tarea.entidadId)} type="button">
            <CheckSquare2 size={15} />
            Realizada
          </button>
        ) : null}
        <Link className="icon-button" title="Abrir" to={tarea.enlace}><ArrowRight size={17} /></Link>
      </div>
    </article>
  );
}

function Summary({ icon: Icon, label, value, tone = "default" }: { icon: typeof Inbox; label: string; value: number; tone?: string }) {
  return <div className={`task-summary__item task-summary__item--${tone}`}><Icon size={19} /><span><small>{label}</small><strong>{value}</strong></span></div>;
}

function taskIcon(tipo: string) {
  if (tipo === "EXPEDIENTE_ESTANCADO") return <Clock3 size={19} />;
  if (tipo === "DOCUMENTACION_PENDIENTE_CLIENTE" || tipo === "JUSTIFICANTE_FINAL_PENDIENTE") return <FileWarning size={19} />;
  if (tipo === "INFORMACION_PENDIENTE_CLIENTE") return <MessageCircle size={19} />;
  if (tipo === "WHATSAPP_PENDIENTE_REVISION" || tipo === "WHATSAPP_PENDIENTE_ASOCIAR" || tipo === "WHATSAPP_ADJUNTO_CLASIFICAR" || tipo === "WHATSAPP_APORTACION_INDICADA" || tipo === "WHATSAPP_CONTACTO_SOLICITADO" || tipo === "WHATSAPP_ESTADO_SOLICITADO") return <MessageCircle size={19} />;
  if (tipo === "APORTACION_PENDIENTE_REVISION") return <SearchCheck size={19} />;
  if (tipo === "SOLICITUD_PENDIENTE_REVISION") return <ClipboardList size={19} />;
  if (tipo.startsWith("INCIDENCIA_")) return <AlertTriangle size={19} />;
  return <AlertTriangle size={19} />;
}

function taskActionKind(tipo: string): { label: string; tone: "neutral" | "warning" | "success" | "danger" | "info" } | null {
  if (tipo === "INCIDENCIA_PENDIENTE_NOTIFICAR") return { label: "AVISO AL CLIENTE", tone: "warning" };
  if (tipo.startsWith("INCIDENCIA_")) return { label: "INCIDENCIA", tone: "danger" };
  if (tipo === "EXPEDIENTE_ESTANCADO") return { label: "SIN ACTIVIDAD", tone: "neutral" };
  if (tipo === "DOCUMENTACION_PENDIENTE_CLIENTE" || tipo === "JUSTIFICANTE_FINAL_PENDIENTE") return { label: "FALTA DOCUMENTACION", tone: "warning" };
  if (tipo === "INFORMACION_PENDIENTE_CLIENTE") return { label: "REQUIERE CONTESTACION", tone: "info" };
  if (tipo === "APORTACION_PENDIENTE_REVISION") return { label: "REVISION", tone: "info" };
  if (tipo === "WHATSAPP_APORTACION_INDICADA") return { label: "WHATSAPP APORTACION", tone: "warning" };
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
  if (tipo === "WHATSAPP_PENDIENTE_REVISION" || tipo === "WHATSAPP_PENDIENTE_ASOCIAR" || tipo === "WHATSAPP_APORTACION_INDICADA" || tipo === "WHATSAPP_CONTACTO_SOLICITADO" || tipo === "WHATSAPP_ESTADO_SOLICITADO") return "Mensaje";
  return "Detalle solicitado";
}
