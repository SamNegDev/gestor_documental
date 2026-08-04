import { useRef, useState } from "react";
import { CalendarRange, Download, LoaderCircle } from "lucide-react";
import type { CategoriaHistorial } from "../types/expedienteDetail.types";
import { downloadExpedienteHistoryCsv } from "../services/historyExportApi";
import "./HistoryExportControls.css";

type Props = {
  expedienteId: string | number;
  category: CategoriaHistorial | "TODOS";
  clientView: boolean;
};

const categoryLabels: Record<CategoriaHistorial | "TODOS", string> = {
  TODOS: "todos los movimientos visibles",
  ESTADO: "cambios de estado",
  DOCUMENTO: "movimientos de documentos",
  INCIDENCIA: "incidencias",
  COMUNICACION: "comunicaciones",
  TRAMITE: "hitos de tramitación",
  SISTEMA: "movimientos del sistema",
};

export function HistoryExportControls({ expedienteId, category, clientView }: Props) {
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const invalidRange = Boolean(desde && hasta && hasta < desde);

  async function exportHistory() {
    if (invalidRange) return;
    setExporting(true);
    setError(null);
    try {
      await downloadExpedienteHistoryCsv(expedienteId, {
        categoria: category === "TODOS" ? undefined : category,
        desde: desde || undefined,
        hasta: hasta || undefined,
        clientView,
      });
      if (detailsRef.current) detailsRef.current.open = false;
    } catch {
      setError("No se pudo preparar el CSV.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <details className="history-export" ref={detailsRef}>
      <summary><Download aria-hidden="true" size={15} /> Exportar</summary>
      <div className="history-export__popover">
        <div className="history-export__title">
          <CalendarRange aria-hidden="true" size={17} />
          <div><strong>Exportar historial</strong><span>{categoryLabels[category]}</span></div>
        </div>
        <div className="history-export__dates">
          <label>Desde<input max={hasta || undefined} onChange={(event) => setDesde(event.target.value)} type="date" value={desde} /></label>
          <label>Hasta<input min={desde || undefined} onChange={(event) => setHasta(event.target.value)} type="date" value={hasta} /></label>
        </div>
        {invalidRange ? <p role="alert">La fecha final debe ser posterior a la inicial.</p> : null}
        {error ? <p role="alert">{error}</p> : null}
        <button disabled={exporting || invalidRange} onClick={() => void exportHistory()} type="button">
          {exporting ? <LoaderCircle aria-hidden="true" className="is-spinning" size={15} /> : <Download aria-hidden="true" size={15} />}
          {exporting ? "Preparando…" : "Descargar CSV"}
        </button>
      </div>
    </details>
  );
}
