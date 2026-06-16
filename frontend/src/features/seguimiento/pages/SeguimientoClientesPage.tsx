import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ArrowRight, BellRing, CalendarClock, Clock3, History, Mail, MessageCircle, RotateCcw, X } from "lucide-react";
import { Link } from "react-router-dom";
import { ApiError } from "../../../shared/api/http";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { PaginationBar } from "../../listados/components/PaginationBar";
import { getExpedienteListCatalogs } from "../../listados/services/listadosApi";
import { NotificationEmailDialog } from "../../tareas/components/NotificationEmailDialog";
import { archivarSeguimiento, getSeguimientos, posponerSeguimiento, reactivarSeguimiento } from "../services/seguimientoApi";
import type { Seguimiento } from "../types";

export function SeguimientoClientesPage() {
  const [vista, setVista] = useState("PENDIENTES");
  const [clienteId, setClienteId] = useState("");
  const [anio, setAnio] = useState("");
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(25);
  const [notificacion, setNotificacion] = useState<{ incidenciaId: number; canal: "email" | "whatsapp" } | null>(null);
  const [seguimientoParaPosponer, setSeguimientoParaPosponer] = useState<Seguimiento | null>(null);
  const qc = useQueryClient();
  const { confirm, dialog } = useConfirmDialog();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["seguimiento"] });
    qc.invalidateQueries({ queryKey: ["tareas"] });
  };
  const query = useQuery({
    queryKey: ["seguimiento", vista, clienteId, anio, pagina, tamanio],
    queryFn: () => getSeguimientos({ vista, clienteId, anio, pagina, tamanio }),
  });
  const catalogs = useQuery({ queryKey: ["expedientes", "catalogos-listado"], queryFn: getExpedienteListCatalogs });
  const action = useMutation({
    mutationFn: async ({ type, item }: { type: string; item: Seguimiento }) => type === "archivar" ? archivarSeguimiento(item.incidenciaId) : reactivarSeguimiento(item.incidenciaId),
    onSuccess: refresh,
  });
  const postpone = useMutation({
    mutationFn: async ({ id, proximoAviso }: { id: number; proximoAviso: string }) => posponerSeguimiento(id, { proximoAviso }),
    onSuccess: () => {
      setSeguimientoParaPosponer(null);
      refresh();
    },
  });
  const data = query.data;
  const years = Array.from({ length: 8 }, (_, i) => new Date().getFullYear() - i);

  async function run(type: string, item: Seguimiento) {
    const ok = await confirm({
      title: type === "archivar" ? "Archivar seguimiento" : "Reactivar seguimiento",
      description: type === "archivar" ? "Dejara de generar recordatorios mientras el expediente siga abierto." : "Volvera a la bandeja de tareas para poder notificarlo.",
      confirmLabel: type === "archivar" ? "Archivar" : "Reactivar",
      tone: type === "archivar" ? "danger" : "default",
    });
    if (ok) action.mutate({ type, item });
  }

  return (
    <main className="records-page followup-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">Control de esperas</p>
          <h2>Seguimiento de clientes</h2>
          <p>Solicitudes al cliente ya notificadas pendientes de respuesta y seguimientos archivados que siguen vigentes.</p>
        </div>
        <span className="records-count">{data?.totalElementos ?? 0} casos</span>
      </header>

      <div className="task-tabs">
        <button className={vista === "PENDIENTES" ? "is-active" : ""} onClick={() => { setVista("PENDIENTES"); setPagina(0); }} type="button"><BellRing size={15} /> En seguimiento</button>
        <button className={vista === "ARCHIVADAS" ? "is-active" : ""} onClick={() => { setVista("ARCHIVADAS"); setPagina(0); }} type="button"><Archive size={15} /> Archivadas</button>
      </div>

      <div className="task-filters">
        <label>
          <span>Cliente</span>
          <select value={clienteId} onChange={(event) => { setClienteId(event.target.value); setPagina(0); }}>
            <option value="">Todos los clientes</option>
            {catalogs.data?.clientes.map((cliente) => <option key={cliente.id} value={cliente.id}>{cliente.nombre}</option>)}
          </select>
        </label>
        <label>
          <span>Año expediente</span>
          <select value={anio} onChange={(event) => { setAnio(event.target.value); setPagina(0); }}>
            <option value="">Todos los años</option>
            {years.map((year) => <option key={year}>{year}</option>)}
          </select>
        </label>
      </div>

      <section className="records-panel records-panel--ledger">
        {query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        <div className="followup-list">
          {data?.contenido.length === 0 ? (
            <div className="records-empty">
              <History size={24} />
              <strong>No hay seguimientos en esta vista.</strong>
            </div>
          ) : null}
          {data?.contenido.map((item) => (
            <FollowupRow item={item} key={item.incidenciaId} onAction={run} onNotify={(incidenciaId, canal) => setNotificacion({ incidenciaId, canal })} onPostpone={setSeguimientoParaPosponer} vista={vista} />
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
      <PostponeDialog item={seguimientoParaPosponer} error={postpone.error} pending={postpone.isPending} onClose={() => setSeguimientoParaPosponer(null)} onSubmit={(proximoAviso) => seguimientoParaPosponer && postpone.mutate({ id: seguimientoParaPosponer.incidenciaId, proximoAviso })} />
      {dialog}
    </main>
  );
}

function FollowupRow({
  item,
  vista,
  onAction,
  onNotify,
  onPostpone,
}: {
  item: Seguimiento;
  vista: string;
  onAction: (type: string, item: Seguimiento) => void;
  onNotify: (incidenciaId: number, canal: "email" | "whatsapp") => void;
  onPostpone: (item: Seguimiento) => void;
}) {
  const kind = followupKind(item.tipoIncidencia);
  const status = vista === "ARCHIVADAS"
    ? { label: "ARCHIVADA", tone: "neutral" as const, icon: <Archive size={18} /> }
    : { label: item.pendienteNotificacion ? "RECORDATORIO VENCIDO" : "EN SEGUIMIENTO", tone: item.pendienteNotificacion ? "warning" as const : "info" as const, icon: item.pendienteNotificacion ? <BellRing size={18} /> : <Clock3 size={18} /> };

  return (
    <article className={`followup-row followup-row--${status.tone}`}>
      <span className={`followup-row__icon followup-row__icon--${status.tone}`}>{status.icon}</span>
      <div className="followup-row__main">
        <div className="followup-row__badges">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          <StatusBadge tone={kind.tone}>{kind.label}</StatusBadge>
        </div>
        <strong className="followup-row__plate">{item.matricula || `EXP-${item.expedienteId}`}</strong>
        <small>{item.cliente || "Sin cliente"}</small>
      </div>
      <div className="followup-row__detail">
        <strong>{format(item.tipoIncidencia)}</strong>
        <span>{item.observaciones || "Sin observaciones"}</span>
      </div>
      <div>
        <small>Avisos enviados</small>
        <strong>{item.avisosEnviados} / {item.maxAvisos}</strong>
        <span>{item.fechaUltimoAviso || "Sin fecha"}</span>
      </div>
      <div>
        <small>{vista === "ARCHIVADAS" ? "Archivada" : "Proximo recordatorio"}</small>
        <strong>{vista === "ARCHIVADAS" ? item.fechaArchivo || "Sin fecha" : item.proximoAviso || "Sin fecha"}</strong>
      </div>
      <div className="followup-row__actions">
        {vista === "PENDIENTES" ? (
          <>
            <button className="icon-button" disabled={item.avisosEnviados >= item.maxAvisos} onClick={() => onNotify(item.incidenciaId, "email")} title={item.avisosEnviados >= item.maxAvisos ? "Maximo de avisos alcanzado" : "Enviar correo"} type="button">
              <Mail size={16} />
            </button>
            <button className="soft-button soft-button--compact" disabled={item.avisosEnviados >= item.maxAvisos} onClick={() => onNotify(item.incidenciaId, "whatsapp")} title={item.avisosEnviados >= item.maxAvisos ? "Maximo de avisos alcanzado" : "Enviar WhatsApp"} type="button">
              <MessageCircle size={15} />
              WhatsApp
            </button>
            <button className="icon-button" title="Posponer vencimiento" onClick={() => onPostpone(item)} type="button"><CalendarClock size={16} /></button>
            <button className="icon-button" title="Archivar" onClick={() => onAction("archivar", item)} type="button"><Archive size={16} /></button>
          </>
        ) : (
          <button className="soft-button soft-button--compact" onClick={() => onAction("reactivar", item)} type="button"><RotateCcw size={15} /> Reactivar</button>
        )}
        <Link className="icon-button" title="Ver expediente" to={`/expedientes/${item.expedienteId}`}><ArrowRight size={16} /></Link>
      </div>
    </article>
  );
}

function PostponeDialog({
  item,
  pending,
  error,
  onClose,
  onSubmit,
}: {
  item: Seguimiento | null;
  pending: boolean;
  error: unknown;
  onClose: () => void;
  onSubmit: (proximoAviso: string) => void;
}) {
  const [value, setValue] = useState(defaultDateTimeLocal());

  useEffect(() => {
    if (item) setValue(defaultDateTimeLocal());
  }, [item]);

  if (!item) return null;
  const message = error instanceof ApiError ? error.details || error.message : null;

  return (
    <div className="mail-dialog postpone-dialog" role="dialog" aria-modal="true" aria-labelledby="postpone-dialog-title">
      <button className="mail-dialog__backdrop" type="button" aria-label="Cerrar aplazamiento" onClick={onClose} />
      <section className="mail-dialog__panel postpone-dialog__panel">
        <header className="mail-dialog__header">
          <div className="mail-dialog__mark"><CalendarClock size={20} /></div>
          <div><p>Seguimiento</p><h3 id="postpone-dialog-title">Posponer vencimiento</h3></div>
          <button className="icon-button" title="Cerrar" type="button" onClick={onClose}><X size={17} /></button>
        </header>
        <div className="mail-dialog__body">
          <div className="mail-dialog__route"><span>Caso</span><strong>{item.matricula || `EXP-${item.expedienteId}`}</strong><small>{item.cliente || "Sin cliente"}</small></div>
          <label><span>Nueva fecha de recordatorio</span><input min={defaultDateTimeLocal()} type="datetime-local" value={value} onChange={(event) => setValue(event.target.value)} /></label>
          {message ? <div className="mail-dialog__error">{message}</div> : null}
        </div>
        <footer className="mail-dialog__actions">
          <button className="soft-button" type="button" onClick={onClose}>Cancelar</button>
          <button className="primary-button" disabled={pending || !value} type="button" onClick={() => onSubmit(value)}>
            <CalendarClock size={16} />
            {pending ? "Posponiendo..." : "Posponer"}
          </button>
        </footer>
      </section>
    </div>
  );
}

function defaultDateTimeLocal() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(9, 0, 0, 0);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function followupKind(tipo?: string | null): { label: string; tone: "neutral" | "warning" | "success" | "danger" | "info" } {
  if (tipo === "PENDIENTE_DOCUMENTACION") return { label: "FALTA DOCUMENTACION", tone: "warning" };
  if (tipo === "SOLICITADA_INFORMACION_ADICIONAL") return { label: "REQUIERE CONTESTACION", tone: "info" };
  return { label: "INCIDENCIA", tone: "danger" };
}

const format = (value?: string | null) => value ? value.replaceAll("_", " ") : "INCIDENCIA";
