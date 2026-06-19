import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CheckSquare, Download, FileText, Loader2, RefreshCcw, Square } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import { exportarExtraccionGaPreparadas, getExtraccionGaPreparadas } from "../services/extraccionGaApi";
import type { ExtraccionGaRevision } from "../types";

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("es-ES") : "-";
}

function confidence(value?: number | null) {
  return typeof value === "number" ? `${Math.round(value * 100)}%` : "-";
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function ExtraccionGaExportQueuePage() {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<number[]>([]);
  const query = useQuery({
    queryKey: ["extraccion-ga", "preparadas"],
    queryFn: getExtraccionGaPreparadas,
  });
  const exportMutation = useMutation({
    mutationFn: () => exportarExtraccionGaPreparadas(selected),
    onSuccess: async ({ blob, filename }) => {
      downloadBlob(blob, filename || `FORMATO_GA_LOTE_${Date.now()}.GA.XML`);
      setSelected([]);
      await queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "preparadas"] });
    },
  });
  const revisiones = query.data || [];
  const allSelected = revisiones.length > 0 && selected.length === revisiones.length;
  const errorDetails = exportMutation.error instanceof ApiError ? exportMutation.error.details : undefined;
  const selectedSet = useMemo(() => new Set(selected), [selected]);

  const toggle = (revision: ExtraccionGaRevision) => {
    setSelected((current) => current.includes(revision.expedienteId)
      ? current.filter((id) => id !== revision.expedienteId)
      : [...current, revision.expedienteId]);
  };

  return (
    <main className="records-page ga-export-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">FORMATO_GA</p>
          <h2>Preparados para exportacion</h2>
          <p>Selecciona revisiones validadas para generar un unico XML de importacion.</p>
        </div>
        <div className="records-header__actions">
          <button className="soft-button soft-button--compact" type="button" onClick={() => query.refetch()}>
            <RefreshCcw size={15} />
            Refrescar
          </button>
          <button className="primary-button primary-button--compact" type="button" disabled={!selected.length || exportMutation.isPending} onClick={() => exportMutation.mutate()}>
            {exportMutation.isPending ? <Loader2 size={16} /> : <Download size={16} />}
            Exportar XML
          </button>
        </div>
      </header>

      {exportMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo generar el XML.</strong>
            <p>{errorDetails || "Revisa la seleccion e intenta de nuevo."}</p>
          </div>
        </section>
      ) : null}

      <section className="records-panel">
        <div className="records-panel__heading">
          <div>
            <h3>{revisiones.length} tramites preparados</h3>
            <span>{selected.length} seleccionados para el lote.</span>
          </div>
          <button
            className="soft-button soft-button--compact"
            type="button"
            disabled={!revisiones.length}
            onClick={() => setSelected(allSelected ? [] : revisiones.map((revision) => revision.expedienteId))}
          >
            {allSelected ? <CheckSquare size={16} /> : <Square size={16} />}
            {allSelected ? "Quitar seleccion" : "Seleccionar todos"}
          </button>
        </div>

        {query.isLoading ? (
          <div className="records-empty records-empty--compact"><Loader2 size={18} /> Cargando preparados...</div>
        ) : !revisiones.length ? (
          <div className="records-empty records-empty--compact">No hay tramites preparados para exportacion.</div>
        ) : (
          <div className="ga-export-list">
            {revisiones.map((revision) => (
              <button className={selectedSet.has(revision.expedienteId) ? "ga-export-row is-selected" : "ga-export-row"} key={revision.id} type="button" onClick={() => toggle(revision)}>
                <span className="ga-export-row__check">{selectedSet.has(revision.expedienteId) ? <CheckSquare size={18} /> : <Square size={18} />}</span>
                <span>
                  <strong>{revision.matricula || `Expediente ${revision.expedienteId}`}</strong>
                  <small>{revision.clienteNombre || "Sin cliente"} · {revision.tipoTramite || "Sin tipo"}</small>
                </span>
                <span>
                  <small>Confianza</small>
                  <strong>{confidence(revision.confianzaGlobal)}</strong>
                </span>
                <span>
                  <small>Preparado</small>
                  <strong>{formatDate(revision.fechaPreparado)}</strong>
                </span>
                <FileText size={18} />
              </button>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
