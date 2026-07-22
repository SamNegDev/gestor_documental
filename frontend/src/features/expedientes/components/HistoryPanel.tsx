import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, FileText, Flag, MessageCircle, Settings2, Waypoints } from "lucide-react";
import type { CategoriaHistorial, HistorialExpediente, HistorialPage } from "../types/expedienteDetail.types";
import { getExpedienteHistory } from "../services/expedienteDetailApi";
import { getClienteExpedienteHistory } from "../services/clienteExpedienteApi";
import { HistoryEvent } from "./HistoryEvent";
import "./HistoryPanel.css";

type Props = {
  expedienteId: string | number;
  initialItems?: HistorialExpediente[];
  clientView?: boolean;
};

const filters: Array<{ value: CategoriaHistorial | "TODOS"; label: string }> = [
  { value: "TODOS", label: "Todos" },
  { value: "ESTADO", label: "Estados" },
  { value: "DOCUMENTO", label: "Documentos" },
  { value: "INCIDENCIA", label: "Incidencias" },
  { value: "COMUNICACION", label: "Comunicaciones" },
  { value: "TRAMITE", label: "Tr\u00e1mites" },
];

function dateKey(value?: string) {
  if (!value) return "SIN_FECHA";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "SIN_FECHA" : date.toISOString().slice(0, 10);
}

function dateLabel(key: string) {
  if (key === "SIN_FECHA") return "Fecha no disponible";
  return new Intl.DateTimeFormat("es-ES", { day: "2-digit", month: "long", year: "numeric" })
    .format(new Date(key + "T12:00:00"))
    .replace(" de ", " ")
    .replace(" de ", " ")
    .toUpperCase();
}

export function HistoryPanel({ expedienteId, initialItems = [], clientView = false }: Props) {
  const [category, setCategory] = useState<CategoriaHistorial | "TODOS">("TODOS");
  const [items, setItems] = useState<HistorialExpediente[]>(initialItems);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(initialItems.length);
  const [totalPages, setTotalPages] = useState(initialItems.length ? 1 : 0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async (targetPage: number, append: boolean) => {
    setLoading(true);
    setError(null);
    try {
      const request = clientView ? getClienteExpedienteHistory : getExpedienteHistory;
      const result: HistorialPage = await request(expedienteId, {
        pagina: targetPage,
        tamanio: 20,
        categoria: category === "TODOS" ? undefined : category,
      });
      setItems((current) => append ? [...current, ...result.contenido] : result.contenido);
      setPage(result.pagina);
      setTotal(result.totalElementos);
      setTotalPages(result.totalPaginas);
    } catch {
      setError("No se pudo cargar el historial.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(0, false);
  }, [expedienteId, category, clientView]);

  const groups = useMemo(() => {
    const map = new Map<string, HistorialExpediente[]>();
    items.forEach((item) => {
      const key = dateKey(item.fechaCambio);
      map.set(key, [...(map.get(key) ?? []), item]);
    });
    return [...map.entries()];
  }, [items]);

  const categoryIcon = {
    ESTADO: Flag,
    DOCUMENTO: FileText,
    INCIDENCIA: AlertTriangle,
    COMUNICACION: MessageCircle,
    TRAMITE: Waypoints,
    SISTEMA: Settings2,
  };

  return (
    <section className="history-panel" aria-labelledby="history-title">
      <header className="history-panel__header">
        <div>
          <h3 id="history-title">Historial</h3>
          <p>{total} {total === 1 ? "movimiento" : "movimientos"} &middot; M&aacute;s recientes primero</p>
        </div>
      </header>

      <div className="history-filters" aria-label="Filtrar historial">
        {filters.map((filter) => (
          <button
            aria-pressed={category === filter.value}
            className={category === filter.value ? "is-active" : ""}
            key={filter.value}
            onClick={() => setCategory(filter.value)}
            type="button"
          >
            {filter.label}
          </button>
        ))}
      </div>

      {error ? <p className="history-panel__error" role="alert">{error}</p> : null}
      {!loading && items.length === 0 ? <p className="exp-empty">No hay movimientos en esta categor&iacute;a.</p> : null}

      <div className="history-groups" aria-busy={loading}>
        {groups.map(([key, group]) => (
          <section className="history-group" key={key}>
            <h4><span>{dateLabel(key)}</span></h4>
            <div className="history-timeline">
              {group.map((item, index) => {
                const Icon = categoryIcon[item.categoria ?? "SISTEMA"];
                return <HistoryEvent icon={Icon} item={item} key={item.id} last={index === group.length - 1} showUser={!clientView} />;
              })}
            </div>
          </section>
        ))}
      </div>

      {page + 1 < totalPages ? (
        <button className="history-load-more" disabled={loading} onClick={() => void load(page + 1, true)} type="button">
          {loading ? "Cargando\u2026" : "Ver movimientos anteriores"}
        </button>
      ) : null}
      {loading && items.length === 0 ? <p className="exp-empty">Cargando historial&hellip;</p> : null}
    </section>
  );
}
