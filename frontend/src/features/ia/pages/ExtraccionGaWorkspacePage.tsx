import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertCircle,
  CheckSquare,
  Download,
  FileSearch,
  FileText,
  Loader2,
  Play,
  RefreshCcw,
  Square,
} from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import {
  crearExtraccionGaJobs,
  exportarExtraccionGaPreparadas,
  getExtraccionGaJobsActivos,
  getExtraccionGaPendientesRevision,
  getExtraccionGaPreparadas,
} from "../services/extraccionGaApi";
import type { EstadoExtraccionGaJob, ExtraccionGaQueueItem, ExtraccionGaRevision } from "../types";

type Tab = "revision" | "importacion";

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

function isActiveJob(estado?: EstadoExtraccionGaJob | null) {
  return estado === "PENDIENTE" || estado === "PROCESANDO";
}

function queueStatus(item: ExtraccionGaQueueItem) {
  if (item.jobEstado === "PENDIENTE") return "En cola";
  if (item.jobEstado === "PROCESANDO") return item.jobFaseActual || "Procesando";
  if (item.jobEstado === "ERROR") return "Error";
  if (item.revisionEstado === "BORRADOR") return "Pendiente revision";
  return "Sin analizar";
}

function queueStatusClass(item: ExtraccionGaQueueItem) {
  if (item.jobEstado === "ERROR") return "is-danger";
  if (isActiveJob(item.jobEstado)) return "is-info";
  if (item.revisionEstado === "BORRADOR") return item.requiereRevisionHumana ? "is-warning" : "is-success";
  return "is-muted";
}

export function ExtraccionGaWorkspacePage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("revision");
  const [selectedRevision, setSelectedRevision] = useState<number[]>([]);
  const [selectedImportacion, setSelectedImportacion] = useState<number[]>([]);

  const pendientesQuery = useQuery({
    queryKey: ["extraccion-ga", "pendientes-revision"],
    queryFn: getExtraccionGaPendientesRevision,
    refetchInterval: 5000,
  });
  const jobsQuery = useQuery({
    queryKey: ["extraccion-ga", "jobs-activos"],
    queryFn: getExtraccionGaJobsActivos,
    refetchInterval: 5000,
  });
  const preparadasQuery = useQuery({
    queryKey: ["extraccion-ga", "preparadas"],
    queryFn: getExtraccionGaPreparadas,
  });

  const crearJobsMutation = useMutation({
    mutationFn: (expedienteIds?: number[]) => crearExtraccionGaJobs(expedienteIds || selectedRevision),
    onSuccess: async () => {
      setSelectedRevision([]);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "pendientes-revision"] }),
        queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "jobs-activos"] }),
      ]);
    },
  });

  const exportMutation = useMutation({
    mutationFn: () => exportarExtraccionGaPreparadas(selectedImportacion),
    onSuccess: async ({ blob, filename }) => {
      downloadBlob(blob, filename || `FORMATO_GA_LOTE_${Date.now()}.GA.XML`);
      setSelectedImportacion([]);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "preparadas"] }),
        queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "pendientes-revision"] }),
      ]);
    },
  });

  const pendientes = pendientesQuery.data || [];
  const preparadas = preparadasQuery.data || [];
  const jobsActivos = jobsQuery.data || [];
  const selectedRevisionSet = useMemo(() => new Set(selectedRevision), [selectedRevision]);
  const selectedImportacionSet = useMemo(() => new Set(selectedImportacion), [selectedImportacion]);
  const seleccionablesRevision = pendientes.filter((item) => !isActiveJob(item.jobEstado));
  const allRevisionSelected = seleccionablesRevision.length > 0
    && seleccionablesRevision.every((item) => selectedRevisionSet.has(item.expedienteId));
  const allImportacionSelected = preparadas.length > 0 && selectedImportacion.length === preparadas.length;
  const queueError = crearJobsMutation.error instanceof ApiError ? crearJobsMutation.error.details : undefined;
  const exportError = exportMutation.error instanceof ApiError ? exportMutation.error.details : undefined;

  const toggleRevision = (item: ExtraccionGaQueueItem) => {
    if (isActiveJob(item.jobEstado)) return;
    setSelectedRevision((current) => current.includes(item.expedienteId)
      ? current.filter((id) => id !== item.expedienteId)
      : [...current, item.expedienteId]);
  };

  const toggleImportacion = (revision: ExtraccionGaRevision) => {
    setSelectedImportacion((current) => current.includes(revision.expedienteId)
      ? current.filter((id) => id !== revision.expedienteId)
      : [...current, revision.expedienteId]);
  };

  return (
    <main className="records-page ga-workspace-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">FORMATO_GA</p>
          <h2>Extraccion y exportacion</h2>
          <p>Analiza expedientes por cola, revisa los campos clave y genera XML por lote.</p>
        </div>
        <div className="records-header__actions">
          <button className="soft-button soft-button--compact" type="button" onClick={() => {
            pendientesQuery.refetch();
            jobsQuery.refetch();
            preparadasQuery.refetch();
          }}>
            <RefreshCcw size={15} />
            Refrescar
          </button>
          <button
            className="primary-button primary-button--compact"
            type="button"
            disabled={tab === "revision" ? !selectedRevision.length || crearJobsMutation.isPending : !selectedImportacion.length || exportMutation.isPending}
            onClick={() => tab === "revision" ? crearJobsMutation.mutate(undefined) : exportMutation.mutate()}
          >
            {tab === "revision"
              ? crearJobsMutation.isPending ? <Loader2 size={16} /> : <Play size={16} />
              : exportMutation.isPending ? <Loader2 size={16} /> : <Download size={16} />}
            {tab === "revision" ? "Extraer seleccionados" : "Exportar XML"}
          </button>
        </div>
      </header>

      <section className="ga-workspace-tabs" aria-label="Extraccion GA">
        <button className={tab === "revision" ? "is-active" : ""} type="button" onClick={() => setTab("revision")}>
          <FileSearch size={16} />
          Pendientes de revision
          <span>{pendientes.length}</span>
        </button>
        <button className={tab === "importacion" ? "is-active" : ""} type="button" onClick={() => setTab("importacion")}>
          <Download size={16} />
          Pendientes de importacion
          <span>{preparadas.length}</span>
        </button>
      </section>

      {jobsActivos.length ? (
        <section className="ia-alert ia-alert--info">
          <Loader2 size={18} />
          <div>
            <strong>{jobsActivos.length} extracciones en curso</strong>
            <p>La cola se actualiza automaticamente mientras el backend procesa.</p>
          </div>
        </section>
      ) : null}

      {crearJobsMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo lanzar la extraccion.</strong>
            <p>{queueError || "Revisa la seleccion e intenta de nuevo."}</p>
          </div>
        </section>
      ) : null}

      {exportMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo generar el XML.</strong>
            <p>{exportError || "Revisa la seleccion e intenta de nuevo."}</p>
          </div>
        </section>
      ) : null}

      {tab === "revision" ? (
        <section className="records-panel">
          <div className="records-panel__heading">
            <div>
              <h3>{pendientes.length} expedientes pendientes</h3>
              <span>{selectedRevision.length} seleccionados para extraccion.</span>
            </div>
            <button
              className="soft-button soft-button--compact"
              type="button"
              disabled={!seleccionablesRevision.length}
              onClick={() => setSelectedRevision(allRevisionSelected ? [] : seleccionablesRevision.map((item) => item.expedienteId))}
            >
              {allRevisionSelected ? <CheckSquare size={16} /> : <Square size={16} />}
              {allRevisionSelected ? "Quitar seleccion" : "Seleccionar todos"}
            </button>
          </div>

          {pendientesQuery.isLoading ? (
            <div className="records-empty records-empty--compact"><Loader2 size={18} /> Cargando expedientes...</div>
          ) : !pendientes.length ? (
            <div className="records-empty records-empty--compact">No hay expedientes pendientes de revision.</div>
          ) : (
            <div className="ga-work-list">
              {pendientes.map((item) => {
                const active = isActiveJob(item.jobEstado);
                return (
                  <article className={selectedRevisionSet.has(item.expedienteId) ? "ga-work-row is-selected" : "ga-work-row"} key={item.expedienteId}>
                    <button className="ga-export-row__check" disabled={active} type="button" onClick={() => toggleRevision(item)}>
                      {selectedRevisionSet.has(item.expedienteId) ? <CheckSquare size={18} /> : <Square size={18} />}
                    </button>
                    <span>
                      <strong>{item.matricula || `Expediente ${item.expedienteId}`}</strong>
                      <small>{item.clienteNombre || "Sin cliente"} - {item.tipoTramite || "Sin tipo"}</small>
                    </span>
                    <span>
                      <small>Estado</small>
                      <strong className={`ga-status-pill ${queueStatusClass(item)}`}>{queueStatus(item)}</strong>
                      {active ? (
                        <span className="ga-job-progress"><i style={{ width: `${item.jobProgreso || 0}%` }} /></span>
                      ) : null}
                    </span>
                    <span>
                      <small>Confianza</small>
                      <strong>{confidence(item.confianzaGlobal)}</strong>
                    </span>
                    <span>
                      <small>Ultima lectura</small>
                      <strong>{formatDate(item.fechaRevision || item.jobFechaCreacion)}</strong>
                    </span>
                    {item.revisionId ? (
                      <Link className="soft-button soft-button--compact" to={`/expedientes/${item.expedienteId}/extraccion-ga`}>
                        <FileText size={15} />
                        Revisar
                      </Link>
                    ) : (
                      <button className="soft-button soft-button--compact" disabled={active} type="button" onClick={() => {
                        crearJobsMutation.mutate([item.expedienteId]);
                      }}>
                        <Play size={15} />
                        Extraer
                      </button>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>
      ) : (
        <section className="records-panel">
          <div className="records-panel__heading">
            <div>
              <h3>{preparadas.length} tramites preparados</h3>
              <span>{selectedImportacion.length} seleccionados para el lote.</span>
            </div>
            <button
              className="soft-button soft-button--compact"
              type="button"
              disabled={!preparadas.length}
              onClick={() => setSelectedImportacion(allImportacionSelected ? [] : preparadas.map((revision) => revision.expedienteId))}
            >
              {allImportacionSelected ? <CheckSquare size={16} /> : <Square size={16} />}
              {allImportacionSelected ? "Quitar seleccion" : "Seleccionar todos"}
            </button>
          </div>

          {preparadasQuery.isLoading ? (
            <div className="records-empty records-empty--compact"><Loader2 size={18} /> Cargando preparados...</div>
          ) : !preparadas.length ? (
            <div className="records-empty records-empty--compact">No hay tramites preparados para importacion.</div>
          ) : (
            <div className="ga-export-list">
              {preparadas.map((revision) => (
                <button className={selectedImportacionSet.has(revision.expedienteId) ? "ga-export-row is-selected" : "ga-export-row"} key={revision.id} type="button" onClick={() => toggleImportacion(revision)}>
                  <span className="ga-export-row__check">{selectedImportacionSet.has(revision.expedienteId) ? <CheckSquare size={18} /> : <Square size={18} />}</span>
                  <span>
                    <strong>{revision.matricula || `Expediente ${revision.expedienteId}`}</strong>
                    <small>{revision.clienteNombre || "Sin cliente"} - {revision.tipoTramite || "Sin tipo"}</small>
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
      )}
    </main>
  );
}
