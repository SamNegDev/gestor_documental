import { useEffect, useRef } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Eye, Mail, Send, X } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import { enviarAvisosSeleccionados, previsualizarAvisosSeleccionados } from "../services/tareasApi";
import type { ResumenDiarioResponse } from "../types";

type Props = {
  incidenciaIds: number[];
  open: boolean;
  onClose: () => void;
  onSent: (result: ResumenDiarioResponse) => void;
};

export function SelectedEmailPreviewDialog({ incidenciaIds, open, onClose, onSent }: Props) {
  const panelRef = useRef<HTMLElement>(null);
  const preview = useQuery({
    queryKey: ["avisos-seleccionados-preview", incidenciaIds],
    queryFn: () => previsualizarAvisosSeleccionados(incidenciaIds),
    enabled: open && incidenciaIds.length > 0,
    staleTime: 0,
  });
  const send = useMutation({
    mutationFn: () => enviarAvisosSeleccionados(incidenciaIds),
    onSuccess: onSent,
  });

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panelRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !send.isPending) onClose();
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("keydown", closeOnEscape);
      previousFocus?.focus();
    };
  }, [open, onClose, send.isPending]);

  if (!open) return null;
  const error = preview.error instanceof ApiError
    ? preview.error.details || preview.error.message
    : send.error instanceof ApiError
      ? send.error.details || send.error.message
      : null;

  return <div aria-labelledby="selected-mail-preview-title" aria-modal="true" className="selected-mail-preview" role="dialog">
    <button aria-label="Cerrar previsualización" className="selected-mail-preview__backdrop" disabled={send.isPending} onClick={onClose} type="button" />
    <section aria-busy={preview.isLoading || send.isPending} className="selected-mail-preview__panel" ref={panelRef} tabIndex={-1}>
      <header className="selected-mail-preview__header">
        <span className="selected-mail-preview__mark"><Mail aria-hidden="true" size={20} /></span>
        <div>
          <p>Correo conjunto</p>
          <h3 id="selected-mail-preview-title">Previsualización antes de enviar</h3>
        </div>
        <button aria-label="Cerrar" className="icon-button" disabled={send.isPending} onClick={onClose} title="Cerrar" type="button"><X aria-hidden="true" size={18} /></button>
      </header>

      {preview.isLoading ? <div aria-live="polite" className="selected-mail-preview__loading" role="status"><Eye aria-hidden="true" size={20} /> Preparando el correo real…</div> : null}
      {preview.data ? <>
        <div className="selected-mail-preview__meta">
          <div><span>Para</span><strong>{preview.data.destinatario}</strong></div>
          <div><span>Asunto</span><strong>{preview.data.asunto}</strong></div>
          <small>{preview.data.incidencias} {preview.data.incidencias === 1 ? "incidencia" : "incidencias"} · {preview.data.expedientes} {preview.data.expedientes === 1 ? "expediente" : "expedientes"}</small>
        </div>
        <div className={preview.data.envioReal ? "selected-mail-preview__delivery" : "selected-mail-preview__simulation"} role="status">
          <strong>{preview.data.envioReal ? "Envío real" : "Simulación"}</strong>
          <span>{preview.data.envioReal ? "Este es el contenido exacto que recibirá el cliente." : "El aviso se registrará, pero no se enviará un correo real."}</span>
        </div>
        <div className="selected-mail-preview__frame">
          <iframe sandbox="allow-popups" srcDoc={preview.data.html} title="Contenido del correo que recibirá el cliente" />
        </div>
      </> : null}

      {error ? <div aria-live="assertive" className="mail-dialog__error" role="alert">{error}</div> : null}
      <footer className="selected-mail-preview__actions">
        <button className="soft-button" disabled={send.isPending} onClick={onClose} type="button">Volver</button>
        <button className="primary-button" disabled={!preview.data || send.isPending} onClick={() => send.mutate()} type="button">
          <Send aria-hidden="true" size={16} />
          {send.isPending ? "Enviando…" : preview.data?.envioReal ? "Enviar este correo" : "Registrar simulación"}
        </button>
      </footer>
    </section>
  </div>;
}