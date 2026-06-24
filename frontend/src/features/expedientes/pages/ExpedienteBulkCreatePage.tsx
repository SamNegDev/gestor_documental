import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AlertCircle, ArrowLeft, FileCheck2, FilePlus2, Loader2, Plus, Trash2, Upload } from "lucide-react";
import { createExpediente, getExpedienteEditCatalogs } from "../services/expedienteDetailApi";
import { getCompleteExpedienteProcessing, startCompleteExpedienteProcessing } from "../services/documentosApi";
import type { ExpedienteEditCatalogs, ProcesamientoExpedienteCompleto } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";
import { ApiError } from "../../../shared/api/http";
import { uppercaseInput } from "../../../shared/utils/text";
import "../styles/expedienteDetail.css";

type BulkRowStatus = "PENDIENTE" | "CREANDO" | "EN_COLA" | "PROCESANDO" | "COMPLETADO" | "ERROR";

type BulkRow = {
  id: string;
  tipoTramiteId: number;
  matricula: string;
  archivo: File | null;
  expedienteId?: number;
  job?: ProcesamientoExpedienteCompleto | null;
  status: BulkRowStatus;
  message?: string;
};

function newRow(catalogs: ExpedienteEditCatalogs | null): BulkRow {
  return {
    id: crypto.randomUUID(),
    tipoTramiteId: catalogs?.tiposTramite[0]?.id || 0,
    matricula: "",
    archivo: null,
    status: "PENDIENTE",
  };
}

function statusLabel(row: BulkRow) {
  if (row.job?.estado === "COMPLETADO") return `${row.job.documentosGenerados} docs`;
  if (row.job?.estado === "ERROR") return "Error OCR";
  return row.status === "PENDIENTE"
    ? "Pendiente"
    : row.status === "CREANDO"
      ? "Creando"
      : row.status === "EN_COLA"
        ? "En cola"
        : row.status === "PROCESANDO"
          ? "Procesando"
          : row.status === "COMPLETADO"
            ? "Completado"
            : "Error";
}

export function ExpedienteBulkCreatePage() {
  const [catalogs, setCatalogs] = useState<ExpedienteEditCatalogs | null>(null);
  const [clienteId, setClienteId] = useState(0);
  const [rows, setRows] = useState<BulkRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getExpedienteEditCatalogs();
      setCatalogs(data);
      setClienteId(data.clientes[0]?.id || 0);
      setRows([newRow(data), newRow(data), newRow(data)]);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 403 ? "No tienes permiso para crear expedientes." : "No se pudo preparar la creacion multiple.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const activeJobs = rows.filter((row) => row.job && (row.job.estado === "PENDIENTE" || row.job.estado === "PROCESANDO"));
    if (!activeJobs.length) return;
    const intervalId = window.setInterval(() => {
      activeJobs.forEach((row) => {
        if (!row.job) return;
        getCompleteExpedienteProcessing(row.job.jobId)
          .then((job) => {
            setRows((current) => current.map((item) => item.id === row.id
              ? { ...item, job, status: job.estado === "COMPLETADO" ? "COMPLETADO" : job.estado === "ERROR" ? "ERROR" : "PROCESANDO", message: job.mensaje || item.message }
              : item));
          })
          .catch(() => {
            setRows((current) => current.map((item) => item.id === row.id ? { ...item, status: "ERROR", message: "No se pudo consultar el procesamiento." } : item));
          });
      });
    }, 3000);
    return () => window.clearInterval(intervalId);
  }, [rows]);

  const readyRows = useMemo(() => rows.filter((row) => row.tipoTramiteId && row.matricula.trim() && row.archivo), [rows]);

  const updateRow = (rowId: string, changes: Partial<BulkRow>) => {
    setRows((current) => current.map((row) => row.id === rowId ? { ...row, ...changes } : row));
  };

  const addRow = () => setRows((current) => [...current, newRow(catalogs)]);
  const removeRow = (rowId: string) => setRows((current) => current.length > 1 ? current.filter((row) => row.id !== rowId) : current);

  const submit = async () => {
    if (!catalogs || !clienteId || saving) return;
    if (!readyRows.length) {
      alert("Completa al menos una fila con tramite, matricula y PDF.");
      return;
    }
    setSaving(true);
    for (const row of readyRows) {
      updateRow(row.id, { status: "CREANDO", message: "Creando expediente." });
      try {
        const creado = await createExpediente({
          clienteId,
          tipoTramiteId: row.tipoTramiteId,
          matricula: uppercaseInput(row.matricula),
          observaciones: "CREACION MULTIPLE",
          interesados: [],
        });
        updateRow(row.id, { expedienteId: creado.id, status: "EN_COLA", message: "Expediente creado. Separacion en cola." });
        const job = await startCompleteExpedienteProcessing(creado.id, row.archivo as File);
        updateRow(row.id, { job, status: "PROCESANDO", message: job.mensaje || "Separando PDF." });
      } catch (cause) {
        updateRow(row.id, {
          status: "ERROR",
          message: cause instanceof ApiError && cause.details ? cause.details : "No se pudo crear o procesar esta fila.",
        });
      }
    }
    setSaving(false);
  };

  if (loading) {
    return (
      <div className="exp-detail-state">
        <Loader2 className="exp-detail-state__spinner" size={28} />
        <strong>Cargando matriz</strong>
        <span>Preparando clientes y tipos de tramite.</span>
      </div>
    );
  }

  if (error || !catalogs) {
    return (
      <div className="exp-detail-state exp-detail-state--error">
        <AlertCircle size={28} />
        <strong>{error || "Matriz no disponible"}</strong>
      </div>
    );
  }

  return (
    <main className="exp-detail-page">
      <section className="exp-panel edit-header-panel">
        <div>
          <p className="eyebrow">Alta en lote</p>
          <h2>Creacion multiple de expedientes</h2>
        </div>
        <Link className="soft-button" to="/expedientes">
          <ArrowLeft size={16} />
          Volver al listado
        </Link>
      </section>

      <section className="exp-panel bulk-create-panel">
        <div className="bulk-create-toolbar">
          <label>
            Cliente
            <select value={clienteId} onChange={(event) => setClienteId(Number(event.target.value))}>
              {catalogs.clientes.map((cliente) => (
                <option key={cliente.id} value={cliente.id}>
                  {cliente.nombre} {cliente.nif ? `- ${cliente.nif}` : ""}
                </option>
              ))}
            </select>
          </label>
          <div className="bulk-create-toolbar__actions">
            <button className="soft-button" onClick={addRow} type="button">
              <Plus size={16} />
              Fila
            </button>
            <button className="primary-button" disabled={saving || readyRows.length === 0} onClick={submit} type="button">
              {saving ? <Loader2 className="button-spinner" size={16} /> : <FilePlus2 size={16} />}
              Crear y separar
            </button>
          </div>
        </div>

        <div className="bulk-create-table" role="table" aria-label="Creacion multiple de expedientes">
          <div className="bulk-create-row bulk-create-row--head" role="row">
            <span>Tipo de tramite</span>
            <span>Matricula</span>
            <span>PDF completo</span>
            <span>Estado</span>
            <span />
          </div>
          {rows.map((row) => (
            <div className={`bulk-create-row bulk-create-row--${row.status.toLowerCase()}`} key={row.id} role="row">
              <label>
                <span>Tipo</span>
                <select disabled={saving} value={row.tipoTramiteId} onChange={(event) => updateRow(row.id, { tipoTramiteId: Number(event.target.value) })}>
                  {catalogs.tiposTramite.map((tipo) => (
                    <option key={tipo.id} value={tipo.id}>
                      {tipo.descripcion || humanizeEnum(tipo.nombre)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Matricula</span>
                <input disabled={saving} value={row.matricula} onChange={(event) => updateRow(row.id, { matricula: uppercaseInput(event.target.value) })} />
              </label>
              <label className="bulk-file-input">
                <Upload size={15} />
                <span>{row.archivo?.name || "Seleccionar PDF"}</span>
                <input
                  accept=".pdf"
                  disabled={saving}
                  hidden
                  type="file"
                  onChange={(event) => {
                    const file = event.currentTarget.files?.[0] || null;
                    event.currentTarget.value = "";
                    updateRow(row.id, { archivo: file });
                  }}
                />
              </label>
              <div className="bulk-create-status">
                {row.status === "PROCESANDO" || row.status === "CREANDO" || row.status === "EN_COLA" ? <Loader2 size={15} /> : <FileCheck2 size={15} />}
                <strong>{statusLabel(row)}</strong>
                <span>{row.message || row.job?.mensaje || (row.expedienteId ? `Expediente ${row.expedienteId}` : "Sin iniciar")}</span>
              </div>
              <button className="icon-button" disabled={saving || rows.length <= 1} onClick={() => removeRow(row.id)} type="button" aria-label="Eliminar fila">
                <Trash2 size={16} />
              </button>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
