import { FileSearch, Upload } from "lucide-react";

type Props = {
  onUploadCompleteExpediente: (archivo: File) => void;
  processing?: boolean;
  title?: string;
  description?: string;
};

export function CompleteExpedienteUploadPanel({
  onUploadCompleteExpediente,
  processing = false,
  title = "Subir expediente completo",
  description = "Sube un PDF completo y el sistema intentara separar automaticamente los documentos detectados.",
}: Props) {
  return (
    <section className="exp-panel exp-panel--compact complete-upload-panel">
      <div className="complete-upload-panel__icon">
        <FileSearch size={18} />
      </div>
      <div className="complete-upload-panel__body">
        <p className="eyebrow">OCR</p>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
      <label className={`primary-button ${processing ? "primary-button--disabled" : ""}`}>
        <Upload size={16} />
        {processing ? "Procesando" : "Subir PDF"}
        <input
          hidden
          disabled={processing}
          type="file"
          accept=".pdf"
          onChange={(event) => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = "";
            if (file) onUploadCompleteExpediente(file);
          }}
        />
      </label>
    </section>
  );
}
