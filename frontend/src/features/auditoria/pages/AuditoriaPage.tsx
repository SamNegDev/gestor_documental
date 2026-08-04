import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, ChevronDown, FileClock, FilterX, Search, ShieldAlert, UserRound } from "lucide-react";
import { PaginationBar } from "../../listados/components/PaginationBar";
import { getAuditoria, getAuditoriaCatalogos } from "../services/auditoriaApi";
import type { AuditoriaEvento, AuditoriaFiltros } from "../types";
import "./AuditoriaPage.css";

const initialFilters: AuditoriaFiltros = { pagina: 0, tamanio: 25 };

export function AuditoriaPage() {
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);
  const catalogs = useQuery({ queryKey: ["auditoria", "catalogos"], queryFn: getAuditoriaCatalogos });
  const query = useQuery({
    queryKey: ["auditoria", filters],
    queryFn: () => getAuditoria(filters),
  });
  const page = query.data;
  const summary = useMemo(() => {
    const items = page?.contenido ?? [];
    return {
      correctos: items.filter((item) => item.resultado === "CORRECTO").length,
      denegados: items.filter((item) => item.resultado === "DENEGADO").length,
      errores: items.filter((item) => item.resultado === "ERROR").length,
    };
  }, [page]);

  const applyFilters = () => {
    const next = { ...draft, pagina: 0 };
    setDraft(next);
    setFilters(next);
  };
  const clearFilters = () => {
    setDraft(initialFilters);
    setFilters(initialFilters);
  };

  return (
    <main className="records-page audit-page">
      <header className="records-header audit-header">
        <div>
          <p className="eyebrow">Seguridad y trazabilidad</p>
          <h2>Auditoría interna</h2>
          <p>Accesos documentales, exportaciones y cambios de autorización. Este registro no forma parte del historial del cliente.</p>
        </div>
        <div className="audit-header__seal" aria-label="Registro de solo lectura">
          <ShieldAlert size={18} />
          <span><strong>Solo lectura</strong><small>Eventos ordenados por fecha</small></span>
        </div>
      </header>

      <section className="audit-summary" aria-label="Resumen de la página actual">
        <SummaryCard icon={<FileClock size={18} />} label="Eventos mostrados" value={page?.contenido.length ?? 0} tone="neutral" />
        <SummaryCard icon={<CheckCircle2 size={18} />} label="Correctos" value={summary.correctos} tone="success" />
        <SummaryCard icon={<ShieldAlert size={18} />} label="Denegados" value={summary.denegados} tone="warning" />
        <SummaryCard icon={<AlertTriangle size={18} />} label="Errores" value={summary.errores} tone="danger" />
      </section>

      <section className="list-filters-panel audit-filters">
        <div className="list-filters-panel__title"><Search size={17} /><strong>Localizar eventos</strong></div>
        <div className="list-filters-grid audit-filter-grid">
          <FilterSelect label="Acción" value={draft.accion} options={catalogs.data?.acciones ?? []} onChange={(accion) => setDraft({ ...draft, accion })} />
          <FilterSelect label="Resultado" value={draft.resultado} options={catalogs.data?.resultados ?? []} onChange={(resultado) => setDraft({ ...draft, resultado })} />
          <FilterSelect label="Recurso" value={draft.recursoTipo} options={catalogs.data?.recursos ?? []} onChange={(recursoTipo) => setDraft({ ...draft, recursoTipo })} />
          <FilterInput label="ID recurso" inputMode="numeric" value={draft.recursoId} onChange={(recursoId) => setDraft({ ...draft, recursoId })} />
          <FilterInput label="Expediente" inputMode="numeric" value={draft.expedienteId} onChange={(expedienteId) => setDraft({ ...draft, expedienteId })} />
          <FilterInput label="Usuario" inputMode="numeric" value={draft.usuarioId} onChange={(usuarioId) => setDraft({ ...draft, usuarioId })} />
          <FilterInput label="Desde" type="datetime-local" value={draft.desde} onChange={(desde) => setDraft({ ...draft, desde })} />
          <FilterInput label="Hasta" type="datetime-local" value={draft.hasta} onChange={(hasta) => setDraft({ ...draft, hasta })} />
        </div>
        <div className="list-filters-actions">
          <button className="soft-button" type="button" onClick={clearFilters}><FilterX size={15} />Limpiar</button>
          <button className="primary-button primary-button--compact" type="button" onClick={applyFilters}><Search size={15} />Aplicar filtros</button>
        </div>
      </section>

      <section className="records-panel audit-ledger">
        <div className="records-panel__heading">
          <div><h3>Libro de eventos</h3><span>{query.isFetching ? "Actualizando" : `${page?.totalElementos ?? 0} registros encontrados`}</span></div>
        </div>
        {query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        {query.isError ? <div className="records-empty records-empty--danger">No se pudo cargar la auditoría.</div> : null}
        {!query.isLoading && !query.isError && page?.contenido.length === 0 ? (
          <div className="records-empty"><FileClock size={24} /><strong>No hay eventos para estos filtros.</strong></div>
        ) : null}
        <div className="audit-event-list">
          {page?.contenido.map((event) => <AuditEvent event={event} key={event.id} />)}
        </div>
        <PaginationBar
          page={page?.pagina ?? 0}
          totalPages={page?.totalPaginas ?? 0}
          totalItems={page?.totalElementos ?? 0}
          pageSize={page?.tamanio ?? filters.tamanio}
          onPageChange={(pagina) => setFilters((current) => ({ ...current, pagina }))}
          onPageSizeChange={(tamanio) => {
            setDraft((current) => ({ ...current, tamanio, pagina: 0 }));
            setFilters((current) => ({ ...current, tamanio, pagina: 0 }));
          }}
        />
      </section>
    </main>
  );
}

function SummaryCard({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: number; tone: string }) {
  return <article className={`audit-summary-card audit-summary-card--${tone}`}><span>{icon}</span><div><strong>{value}</strong><small>{label}</small></div></article>;
}

function AuditEvent({ event }: { event: AuditoriaEvento }) {
  return (
    <details className={`audit-event audit-event--${event.resultado.toLowerCase()}`}>
      <summary>
        <span className="audit-event__time">{formatDate(event.fechaEvento)}</span>
        <span className="audit-event__action"><strong>{actionLabel(event.accion)}</strong><small>{resourceLabel(event)}</small></span>
        <span className="audit-event__actor"><UserRound size={14} /><span>{event.usuarioEmail || "Usuario no identificado"}<small>{event.usuarioRol || "Sin rol"}</small></span></span>
        <ResultBadge result={event.resultado} />
        <ChevronDown className="audit-event__chevron" size={17} />
      </summary>
      <div className="audit-event__detail">
        <Detail label="Contexto" value={contextLabel(event)} />
        <Detail label="Petición" value={[event.metodoHttp, event.ruta].filter(Boolean).join(" ")} mono />
        <Detail label="Origen" value={[event.direccionIp, browserLabel(event.agenteUsuario)].filter(Boolean).join(" · ")} mono />
        <Detail label="Detalle" value={event.detalle} wide />
      </div>
    </details>
  );
}

function ResultBadge({ result }: { result: AuditoriaEvento["resultado"] }) {
  return <span className={`audit-result audit-result--${result.toLowerCase()}`}>{result === "CORRECTO" ? "Correcto" : result === "DENEGADO" ? "Denegado" : "Error"}</span>;
}

function Detail({ label, value, mono, wide }: { label: string; value?: string | null; mono?: boolean; wide?: boolean }) {
  return <div className={`audit-detail-field ${wide ? "audit-detail-field--wide" : ""}`}><span>{label}</span><strong className={mono ? "audit-mono" : ""}>{value || "—"}</strong></div>;
}

function FilterSelect({ label, value, options, onChange }: { label: string; value?: string; options: string[]; onChange: (value: string) => void }) {
  return <label><span>{label}</span><select value={value ?? ""} onChange={(event) => onChange(event.target.value)}><option value="">Todos</option>{options.map((option) => <option key={option} value={option}>{enumLabel(option)}</option>)}</select></label>;
}

function FilterInput({ label, value, onChange, type = "text", inputMode }: { label: string; value?: string; onChange: (value: string) => void; type?: string; inputMode?: "numeric" }) {
  return <label><span>{label}</span><input type={type} inputMode={inputMode} value={value ?? ""} onChange={(event) => onChange(event.target.value)} /></label>;
}

function actionLabel(value: string) {
  const labels: Record<string, string> = {
    VISUALIZAR: "Documento visualizado", DESCARGAR: "Documento descargado", ELIMINAR: "Documento eliminado",
    ELIMINAR_PAGINAS: "Páginas eliminadas", EXPORTAR_HISTORIAL: "Historial exportado", EXPORTAR_GA: "Lote GA exportado",
    USUARIO_CREAR: "Usuario creado", USUARIO_ACTUALIZAR: "Usuario actualizado", USUARIO_ELIMINAR: "Usuario eliminado",
    ADMINISTRADOR_CLIENTE_CREAR: "Administrador vinculado", ADMINISTRADOR_CLIENTE_ACTUALIZAR: "Administrador actualizado",
    ADMINISTRADOR_CLIENTE_DESVINCULAR: "Administrador desvinculado",
  };
  return labels[value] ?? enumLabel(value);
}

function enumLabel(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

function resourceLabel(event: AuditoriaEvento) {
  return event.recursoNombre || [enumLabel(event.recursoTipo || "RECURSO"), event.recursoId].filter(Boolean).join(" #");
}

function contextLabel(event: AuditoriaEvento) {
  return [event.expedienteId ? `EXP-${event.expedienteId}` : null, event.solicitudId ? `SOL-${event.solicitudId}` : null, event.clienteId ? `Cliente ${event.clienteId}` : null].filter(Boolean).join(" · ");
}

function browserLabel(agent?: string | null) {
  if (!agent) return null;
  if (agent.includes("Edg/")) return "Microsoft Edge";
  if (agent.includes("Chrome/")) return "Google Chrome";
  if (agent.includes("Firefox/")) return "Firefox";
  if (agent.includes("Safari/") && !agent.includes("Chrome/")) return "Safari";
  return agent.length > 80 ? `${agent.slice(0, 80)}…` : agent;
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("es-ES", { dateStyle: "short", timeStyle: "medium" }).format(date);
}
