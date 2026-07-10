import { ChevronDown, ChevronUp, FileSearch, Loader2, Upload } from "lucide-react";
import type { ProcesamientoExpedienteCompleto } from "../types/expedienteDetail.types";

type Props = {
  onUploadCompleteExpediente: (archivo: File) => void;
  processing?: boolean;
  minimized?: boolean;
  processingJob?: ProcesamientoExpedienteCompleto | null;
  onToggleMinimized?: () => void;
  title?: string;
  description?: string;
};

export function CompleteExpedienteUploadPanel({
  onUploadCompleteExpediente,
  processing = false,
  minimized = false,
  processingJob = null,
  onToggleMinimized,
  title = "Subir expediente completo",
  description = "Sube un PDF completo y el sistema intentara separar automaticamente los documentos detectados.",
}: Props) {
  const active = processingJob && processingJob.estado !== "COMPLETADO" && processingJob.estado !== "ERROR";
  const done = processingJob?.estado === "COMPLETADO";
  const failed = processingJob?.estado === "ERROR";

  return (
    <section className={`exp-panel exp-panel--compact complete-upload-panel ${active ? "complete-upload-panel--active" : ""}`}>
      <div className={`complete-upload-panel__icon ${active ? "complete-upload-panel__icon--loading" : ""}`}>
        {active ? <Loader2 size={18} /> : <FileSearch size={18} />}
      </div>
      <div className="complete-upload-panel__body">
        <p className="eyebrow">OCR</p>
        <h3>{title}</h3>
        {!minimized ? <p>{processingJob?.mensaje || description}</p> : null}
        {processingJob && !minimized ? (
          <div className={`complete-upload-status ${done ? "complete-upload-status--done" : failed ? "complete-upload-status--error" : ""}`}>
            <strong>{processingJob.archivo}</strong>
            <span>
              {done
                ? `${processingJob.documentosGenerados} documento${processingJob.documentosGenerados === 1 ? "" : "s"} generado${processingJob.documentosGenerados === 1 ? "" : "s"}`
                : failed
                  ? "Revisar incidencia del procesamiento"
                  : "Puedes salir del expediente; el trabajo seguira en segundo plano."}
            </span>
          </div>
        ) : null}
      </div>
      {processingJob && onToggleMinimized ? (
        <button className="soft-button soft-button--compact" onClick={onToggleMinimized} type="button">
          {minimized ? <ChevronDown size={15} /> : <ChevronUp size={15} />}
          {minimized ? "Ver" : "Minimizar"}
        </button>
      ) : null}
      <label className={`primary-button ${processing ? "primary-button--disabled" : ""}`}>
        <Upload size={16} />
        {processing ? "En cola" : "Subir PDF completo"}
        <input
          hidden
          disabled={processing}
          type="file"
          accept=".pdf"
          onChange={(event) => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = "";
            if (!file) return;
            if (!file.name.toLowerCase().endsWith(".pdf")) {
              alert("El expediente completo debe subirse en formato PDF.");
              return;
            }
            onUploadCompleteExpediente(file);
          }}
        />
      </label>
    </section>
  );
}
