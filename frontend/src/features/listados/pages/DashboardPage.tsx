import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link, useLocation, useOutletContext } from "react-router-dom";
import { AlertCircle, ArrowRight, CircleCheck, ClipboardCheck, Clock3, FilePlus2, FolderOpen, Inbox, Loader2, Plus, ShieldAlert } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { getDashboard } from "../services/listadosApi";
import type { DashboardData, ExpedienteListItem, SolicitudListItem } from "../types";

export function DashboardPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const location = useLocation();
  const isClientView = location.pathname.startsWith("/cliente/");
  const isAdmin = user?.rol === "ADMIN" && !isClientView;
  const dashboardQuery = useQuery({
    queryKey: ["dashboard"],
    queryFn: getDashboard,
  });

  if (dashboardQuery.isLoading) {
    return (
      <div className="records-empty">
        <Loader2 size={22} />
        Cargando dashboard...
      </div>
    );
  }

  if (dashboardQuery.error || !dashboardQuery.data) {
    return (
      <div className="records-empty records-empty--danger">
        <AlertCircle size={22} />
        No se pudo cargar el dashboard.
      </div>
    );
  }

  const data = dashboardQuery.data;
  return (
    <main className="dashboard-page">
      <section className="dashboard-hero dashboard-hero--ledger">
        <div>
          <p className="eyebrow">{isAdmin ? "Gestion interna" : "Portal cliente"}</p>
          <h2>{isAdmin ? "Resumen operativo" : "Tu actividad"}</h2>
          <p>{isAdmin ? "Actividad reciente, bloqueos y accesos rapidos para la gestoria." : "Estado de tus solicitudes y expedientes abiertos."}</p>
        </div>
        <div className="dashboard-hero__actions">
          {isAdmin ? (
            <Link className="primary-button primary-button--compact" to="/expedientes/nuevo">
              <FilePlus2 size={16} />
              Nuevo expediente
            </Link>
          ) : (
            <Link className="primary-button primary-button--compact" to="/cliente/solicitudes/nuevo">
              <Plus size={16} />
              Nueva solicitud
            </Link>
          )}
        </div>
      </section>

      <section className="dashboard-metrics">
        <MetricCard icon={<Clock3 size={17} />} title="En tramite" value={data.metrics.enTramite} tone="warning" />
        <MetricCard icon={<ClipboardCheck size={17} />} title="Pendiente de revision" value={data.metrics.pendienteRevision} tone="info" />
        <MetricCard icon={<ShieldAlert size={17} />} title="Con incidencias" value={data.metrics.totalIncidencias} tone="danger" />
        <MetricCard icon={<CircleCheck size={17} />} title="Finalizados" value={data.metrics.finalizados} tone="success" />
      </section>

      <section className="dashboard-grid">
        <ProgressPanel
          title="Estado de expedientes"
          total={data.metrics.totalExpedientes}
          items={[
            { label: "En tramite", value: data.metrics.enTramite, tone: "warning" },
            { label: "Finalizados", value: data.metrics.finalizados, tone: "success" },
            { label: "Incidencias", value: data.metrics.incidenciasExpedientes, tone: "danger" },
          ]}
        />
        <ProgressPanel
          title="Estado de solicitudes"
          total={data.metrics.totalSolicitudes}
          items={[
            { label: "Pendiente revision", value: data.metrics.pendienteRevision, tone: "info" },
            { label: "Convertidas", value: data.metrics.convertidas, tone: "success" },
            { label: "Incidencias", value: data.metrics.incidenciasSolicitudes, tone: "danger" },
          ]}
        />
      </section>

      <section className="dashboard-grid dashboard-grid--tables">
        <LatestExpedientes data={data} isAdmin={isAdmin} />
        <LatestSolicitudes data={data} isAdmin={isAdmin} />
      </section>
    </main>
  );
}

function MetricCard({ title, value, tone, icon }: { title: string; value: number; tone: string; icon: ReactNode }) {
  return (
    <article className={`dashboard-metric dashboard-metric--${tone}`}>
      <span className="dashboard-metric__icon">{icon}</span>
      <span className="dashboard-metric__label">{title}</span>
      <strong>{value}</strong>
    </article>
  );
}

function ProgressPanel({
  title,
  total,
  items,
}: {
  title: string;
  total: number;
  items: Array<{ label: string; value: number; tone: string }>;
}) {
  return (
    <section className="panel dashboard-progress-panel dashboard-progress-panel--ledger">
      <div className="panel-heading">
        <h2>{title}</h2>
        <span className="records-count">{total} total</span>
      </div>
      <div className="dashboard-progress-list">
        {items.map((item) => {
          const percent = total > 0 ? Math.round((item.value / total) * 100) : 0;
          return (
            <div className="dashboard-progress-row" key={item.label}>
              <div>
                <strong>{item.label}</strong>
                <span>{item.value}</span>
              </div>
              <div className={`dashboard-progress-track dashboard-progress-track--${item.tone}`}>
                <span style={{ width: `${percent}%` }} />
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function LatestExpedientes({ data, isAdmin }: { data: DashboardData; isAdmin: boolean }) {
  return (
    <section className="records-panel dashboard-records-panel">
      <div className="records-panel__heading">
        <div>
          <h3>{isAdmin ? "Ultimos expedientes" : "Tus ultimos expedientes"}</h3>
          <span>{data.ultimosExpedientes.length} recientes</span>
        </div>
        <Link className="soft-button soft-button--compact" to={isAdmin ? "/expedientes" : "/cliente/expedientes"}>
          <FolderOpen size={16} />
          Ver todos
        </Link>
      </div>
      <div className="dashboard-list__header" aria-hidden="true">
        <span>Expediente</span>
        <span>Matricula</span>
        <span>Estado</span>
        <span />
      </div>
      <div className="dashboard-list">
        {data.ultimosExpedientes.length === 0 ? <EmptyRow icon="expediente" text="No hay expedientes disponibles." /> : null}
        {data.ultimosExpedientes.map((expediente) => (
          <ExpedienteRow expediente={expediente} isAdmin={isAdmin} key={expediente.id} />
        ))}
      </div>
    </section>
  );
}

function LatestSolicitudes({ data, isAdmin }: { data: DashboardData; isAdmin: boolean }) {
  return (
    <section className="records-panel dashboard-records-panel">
      <div className="records-panel__heading">
        <div>
          <h3>{isAdmin ? "Ultimas solicitudes" : "Tus ultimas solicitudes"}</h3>
          <span>{data.ultimasSolicitudes.length} recientes</span>
        </div>
        <Link className="soft-button soft-button--compact" to={isAdmin ? "/solicitudes" : "/cliente/solicitudes"}>
          <Inbox size={16} />
          Ver todas
        </Link>
      </div>
      <div className="dashboard-list__header" aria-hidden="true">
        <span>Solicitud</span>
        <span>Matricula</span>
        <span>Estado</span>
        <span />
      </div>
      <div className="dashboard-list">
        {data.ultimasSolicitudes.length === 0 ? <EmptyRow icon="solicitud" text="No hay solicitudes disponibles." /> : null}
        {data.ultimasSolicitudes.map((solicitud) => (
          <SolicitudRow isAdmin={isAdmin} key={solicitud.id} solicitud={solicitud} />
        ))}
      </div>
    </section>
  );
}

function ExpedienteRow({ expediente, isAdmin }: { expediente: ExpedienteListItem; isAdmin: boolean }) {
  return (
    <article className="dashboard-row">
      <div>
        <strong>{isAdmin ? expediente.cliente?.nombre || "Sin cliente" : expediente.tipoTramite || "Expediente"}</strong>
        <span>{isAdmin ? expediente.tipoTramite || "Sin tipo de tramite" : expediente.fechaCreacion || "Sin fecha"}</span>
      </div>
      <MiniPlate value={expediente.matricula} />
      <StatusBadge tone={statusTone(expediente.estado)}>{formatDashboardStatus(expediente.estado)}</StatusBadge>
      <Link aria-label="Ver expediente" className="icon-button" title="Ver expediente" to={isAdmin ? `/expedientes/${expediente.id}` : `/cliente/expedientes/${expediente.id}`}>
        <ArrowRight size={16} />
      </Link>
    </article>
  );
}

function SolicitudRow({ solicitud, isAdmin }: { solicitud: SolicitudListItem; isAdmin: boolean }) {
  const target = solicitud.expedienteId
    ? isAdmin
      ? `/expedientes/${solicitud.expedienteId}`
      : `/cliente/expedientes/${solicitud.expedienteId}`
    : `/solicitudes/${solicitud.id}`;
  return (
    <article className="dashboard-row">
      <div>
        <strong>{isAdmin ? solicitud.cliente?.nombre || "Sin cliente" : solicitud.tipoTramite || "Solicitud"}</strong>
        <span>{isAdmin ? solicitud.tipoTramite || "Sin tipo de tramite" : solicitud.fechaCreacion || "Sin fecha"}</span>
      </div>
      <MiniPlate value={solicitud.matricula} />
      <StatusBadge tone={statusTone(solicitud.estado)}>{formatDashboardStatus(solicitud.estado)}</StatusBadge>
      <Link aria-label="Ver solicitud" className="icon-button" title="Ver solicitud" to={target}>
        <ArrowRight size={16} />
      </Link>
    </article>
  );
}

function MiniPlate({ value }: { value?: string | null }) {
  if (!value) {
    return <span className="muted-text">Sin matricula</span>;
  }
  return (
    <span className="mini-plate-react dashboard-mini-plate">
      <span>E</span>
      <strong>{value}</strong>
    </span>
  );
}

function EmptyRow({ text }: { icon: string; text: string }) {
  return (
    <div className="records-empty">
      <ClipboardCheck size={24} />
      <strong>{text}</strong>
    </div>
  );
}

function statusTone(status?: string | null) {
  if (status === "FINALIZADO" || status === "CONVERTIDA") return "success";
  if (status === "INCIDENCIA" || status === "RECHAZADO" || status === "PENDIENTE_DOCUMENTACION" || status === "SOLICITADA_INFORMACION_ADICIONAL") return "danger";
  if (status === "REVISANDO_INCIDENCIAS" || status === "ENVIADO_DGT" || status === "INFORMACION_ADICIONAL_RECIBIDA") return "info";
  if (status === "EN_TRAMITE" || status === "PENDIENTE_REVISION") return "warning";
  return "neutral";
}

function formatEnum(value?: string | null) {
  return value ? value.replaceAll("_", " ") : "Sin estado";
}

function formatDashboardStatus(value?: string | null) {
  switch (value) {
    case "SOLICITADA_INFORMACION_ADICIONAL":
      return "PENDIENTE DE RESPUESTA";
    case "PENDIENTE_DOCUMENTACION":
      return "PENDIENTE DOCUMENTACION";
    case "INFORMACION_ADICIONAL_RECIBIDA":
      return "INFORMACION RECIBIDA";
    case "REVISANDO_INCIDENCIAS":
      return "REVISANDO DOCUMENTACION";
    case "PENDIENTE_REVISION":
      return "PENDIENTE DE REVISION";
    case "ENVIADO_DGT":
      return "ENVIADO DGT";
    default:
      return formatEnum(value);
  }
}
