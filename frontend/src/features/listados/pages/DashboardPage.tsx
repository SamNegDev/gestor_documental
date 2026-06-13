import { useQuery } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Link, useLocation, useOutletContext } from "react-router-dom";
import { Activity, AlertCircle, ArrowRight, BriefcaseBusiness, CalendarRange, CircleCheck, ClipboardCheck, Clock3, FilePlus2, FileWarning, FolderOpen, Inbox, Loader2, Plus, ShieldAlert, TimerReset } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { getDashboard, getProductivity } from "../services/listadosApi";
import type { DashboardData, ExpedienteListItem, ListFilters, ProductivityBreakdownItem, ProductivityData, SolicitudListItem } from "../types";

export function DashboardPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const location = useLocation();
  const isClientView = location.pathname.startsWith("/cliente/");
  const isAdmin = user?.rol === "ADMIN" && !isClientView;
  const [productivityFilters, setProductivityFilters] = useState<ListFilters>({ periodo: "ESTE_MES" });
  const dashboardQuery = useQuery({
    queryKey: ["dashboard"],
    queryFn: getDashboard,
  });
  const customRangeReady = productivityFilters.periodo !== "PERSONALIZADO" || Boolean(productivityFilters.fechaDesde && productivityFilters.fechaHasta);
  const productivityQuery = useQuery({
    queryKey: ["dashboard-productivity", productivityFilters.periodo, productivityFilters.fechaDesde, productivityFilters.fechaHasta],
    queryFn: () => getProductivity(productivityFilters),
    enabled: isAdmin && customRangeReady,
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

      {isAdmin ? (
        <ProductivitySection
          data={productivityQuery.data}
          error={Boolean(productivityQuery.error)}
          filters={productivityFilters}
          loading={productivityQuery.isLoading || productivityQuery.isFetching}
          onChange={setProductivityFilters}
        />
      ) : null}

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

function ProductivitySection({
  data,
  filters,
  loading,
  error,
  onChange,
}: {
  data?: ProductivityData;
  filters: ListFilters;
  loading: boolean;
  error: boolean;
  onChange: (filters: ListFilters) => void;
}) {
  return (
    <section className="productivity-section">
      <div className="productivity-heading">
        <div>
          <p className="eyebrow">Rendimiento de la gestoria</p>
          <h2>Productividad y carga de trabajo</h2>
          <p>{data ? `${data.periodo}: ${formatShortDate(data.fechaDesde)} - ${formatShortDate(data.fechaHasta)}` : "Analisis por periodo de cierres, tiempos y carga activa."}</p>
        </div>
        <ProductivityPeriodFilter filters={filters} onChange={onChange} />
      </div>

      {loading && !data ? <div className="productivity-loading"><Loader2 size={18} /> Calculando indicadores...</div> : null}
      {error ? <div className="records-empty records-empty--danger"><AlertCircle size={20} /> No se pudo calcular la productividad.</div> : null}
      {data ? (
        <>
          <div className={`productivity-kpis${loading ? " productivity-kpis--loading" : ""}`}>
            <ProductivityKpi icon={<BriefcaseBusiness size={17} />} label="Creados" value={data.expedientesCreados} detail="en el periodo" />
            <ProductivityKpi icon={<CircleCheck size={17} />} label="Finalizados" value={data.expedientesFinalizados} detail="cierres registrados" tone="success" />
            <ProductivityKpi icon={<TimerReset size={17} />} label="Tiempo medio" value={formatDays(data.tiempoMedioDias)} detail="hasta finalizacion" />
            <ProductivityKpi icon={<Activity size={17} />} label="En curso" value={data.expedientesEnCurso} detail="carga actual" tone="info" />
            <ProductivityKpi icon={<ShieldAlert size={17} />} label="Incidencias" value={data.incidenciasActivas} detail="activas ahora" tone="danger" />
            <ProductivityKpi icon={<FileWarning size={17} />} label="Falta documentacion" value={data.expedientesConDocumentacionPendiente} detail="expedientes afectados" tone="warning" />
          </div>

          <div className="productivity-grid">
            <EvolutionPanel data={data} />
            <BreakdownPanel data={data.cuellosBotella} empty="No hay expedientes activos." mode="bottleneck" title="Cuellos de botella" />
            <BreakdownPanel data={data.tiemposPorTramite} empty="Todavia no hay cierres en este periodo." mode="duration" title="Tiempo medio por tramite" />
            <BreakdownPanel data={data.volumenPorCliente} empty="No hay expedientes creados en este periodo." mode="client" title="Volumen por cliente" />
          </div>
        </>
      ) : null}
    </section>
  );
}

function ProductivityPeriodFilter({ filters, onChange }: { filters: ListFilters; onChange: (filters: ListFilters) => void }) {
  const selectPeriod = (periodo: string) => {
    if (periodo !== "PERSONALIZADO") {
      onChange({ periodo, fechaDesde: "", fechaHasta: "" });
      return;
    }
    const now = new Date();
    const first = new Date(now.getFullYear(), now.getMonth(), 1);
    onChange({ periodo, fechaDesde: toInputDate(first), fechaHasta: toInputDate(now) });
  };
  return (
    <div className="productivity-filter">
      <CalendarRange size={16} />
      <label>
        <span>Periodo</span>
        <select value={filters.periodo || "ESTE_MES"} onChange={(event) => selectPeriod(event.target.value)}>
          <option value="ULTIMA_SEMANA">Ultima semana</option>
          <option value="ESTE_MES">Este mes</option>
          <option value="ULTIMOS_3_MESES">Ultimos 3 meses</option>
          <option value="ESTE_ANIO">Este ano</option>
          <option value="TODO">Todo el historico</option>
          <option value="PERSONALIZADO">Rango personalizado</option>
        </select>
      </label>
      {filters.periodo === "PERSONALIZADO" ? (
        <div className="productivity-filter__dates">
          <input aria-label="Fecha desde" type="date" value={filters.fechaDesde || ""} onChange={(event) => onChange({ ...filters, fechaDesde: event.target.value })} />
          <span>hasta</span>
          <input aria-label="Fecha hasta" type="date" value={filters.fechaHasta || ""} onChange={(event) => onChange({ ...filters, fechaHasta: event.target.value })} />
        </div>
      ) : null}
    </div>
  );
}

function ProductivityKpi({ icon, label, value, detail, tone = "neutral" }: { icon: ReactNode; label: string; value: ReactNode; detail: string; tone?: string }) {
  return (
    <article className={`productivity-kpi productivity-kpi--${tone}`}>
      <span className="productivity-kpi__icon">{icon}</span>
      <div><span>{label}</span><small>{detail}</small></div>
      <strong>{value}</strong>
    </article>
  );
}

function EvolutionPanel({ data }: { data: ProductivityData }) {
  const max = Math.max(1, ...data.evolucion.flatMap((item) => [item.creados, item.finalizados]));
  return (
    <article className="productivity-panel productivity-panel--evolution">
      <div className="productivity-panel__heading">
        <div><h3>Ritmo de entrada y cierre</h3><span>Expedientes por intervalo</span></div>
        <div className="productivity-legend"><span><i className="is-created" />Creados</span><span><i className="is-finished" />Finalizados</span></div>
      </div>
      {data.evolucion.length === 0 ? <ProductivityEmpty text="No hay actividad en este periodo." /> : (
        <div className="productivity-chart" style={{ gridTemplateColumns: `repeat(${data.evolucion.length}, minmax(12px, 1fr))` }}>
          {data.evolucion.map((item, index) => (
            <div className="productivity-chart__column" key={`${item.etiqueta}-${index}`} title={`${item.etiqueta}: ${item.creados} creados, ${item.finalizados} finalizados`}>
              <div className="productivity-chart__bars">
                <span className="is-created" style={{ height: `${Math.max(item.creados ? 8 : 0, (item.creados / max) * 100)}%` }} />
                <span className="is-finished" style={{ height: `${Math.max(item.finalizados ? 8 : 0, (item.finalizados / max) * 100)}%` }} />
              </div>
              <small>{showChartLabel(data.evolucion.length, index) ? item.etiqueta : ""}</small>
            </div>
          ))}
        </div>
      )}
    </article>
  );
}

function BreakdownPanel({ data, title, mode, empty }: { data: ProductivityBreakdownItem[]; title: string; mode: "bottleneck" | "duration" | "client"; empty: string }) {
  const max = Math.max(1, ...data.map((item) => mode === "duration" ? item.valorMedio : item.total));
  return (
    <article className={`productivity-panel productivity-panel--${mode}`}>
      <div className="productivity-panel__heading"><div><h3>{title}</h3><span>{breakdownSubtitle(mode)}</span></div></div>
      {data.length === 0 ? <ProductivityEmpty text={empty} /> : (
        <div className="productivity-ranking">
          {data.map((item) => {
            const primary = mode === "duration" ? item.valorMedio : item.total;
            return (
              <div className="productivity-ranking__row" key={item.codigo}>
                <div><strong>{item.etiqueta}</strong><span>{breakdownDetail(item, mode)}</span></div>
                <b>{mode === "duration" ? formatDays(item.valorMedio) : item.total}</b>
                <div className="productivity-ranking__track"><span style={{ width: `${Math.max(4, (primary / max) * 100)}%` }} /></div>
              </div>
            );
          })}
        </div>
      )}
    </article>
  );
}

function ProductivityEmpty({ text }: { text: string }) {
  return <div className="productivity-empty"><ClipboardCheck size={20} /><span>{text}</span></div>;
}

function breakdownSubtitle(mode: "bottleneck" | "duration" | "client") {
  if (mode === "bottleneck") return "Carga actual y dias sin actividad";
  if (mode === "duration") return "Solo expedientes finalizados";
  return "Altas del periodo y cierres asociados";
}

function breakdownDetail(item: ProductivityBreakdownItem, mode: "bottleneck" | "duration" | "client") {
  if (mode === "bottleneck") return `${formatDays(item.valorMedio)} sin actividad media`;
  if (mode === "duration") return `${item.total} ${item.total === 1 ? "expediente" : "expedientes"}`;
  return `${Math.round(item.valorMedio)} finalizados`;
}

function formatDays(value: number) {
  return `${new Intl.NumberFormat("es-ES", { maximumFractionDigits: 1 }).format(value)} d`;
}

function formatShortDate(value: string) {
  const [year, month, day] = value.split("-");
  return `${day}/${month}/${year}`;
}

function toInputDate(value: Date) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function showChartLabel(total: number, index: number) {
  if (total <= 12) return true;
  const step = Math.ceil(total / 8);
  return index % step === 0 || index === total - 1;
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
