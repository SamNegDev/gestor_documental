import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Mail, MessageCircle, Send, X } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import { getNotificacionPreview, getNotificacionWhatsappPreview, notificarSeguimiento, notificarSeguimientoWhatsapp } from "../../seguimiento/services/seguimientoApi";

type Props = { canal?: "email" | "whatsapp"; incidenciaId: number | null; onClose: () => void; onSent: () => void };

export function NotificationEmailDialog({ canal = "email", incidenciaId, onClose, onSent }: Props) {
  const [asunto, setAsunto] = useState("");
  const [mensaje, setMensaje] = useState("");
  const panelRef = useRef<HTMLElement>(null);
  const preview = useQuery({
    queryKey: ["notificacion-preview", canal, incidenciaId],
    queryFn: () => canal === "whatsapp" ? getNotificacionWhatsappPreview(incidenciaId!) : getNotificacionPreview(incidenciaId!),
    enabled: incidenciaId !== null,
  });
  const send = useMutation({
    mutationFn: () => canal === "whatsapp" ? notificarSeguimientoWhatsapp(incidenciaId!, { mensaje }) : notificarSeguimiento(incidenciaId!, { asunto, mensaje }),
    onSuccess: () => { onSent(); onClose(); },
  });

  useEffect(() => {
    if (preview.data) { setAsunto(preview.data.asunto); setMensaje(preview.data.mensaje); }
  }, [preview.data]);

  useEffect(() => {
    if (incidenciaId === null) return;
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
  }, [incidenciaId, onClose, send.isPending]);

  if (incidenciaId === null) return null;
  const error = preview.error instanceof ApiError ? preview.error.details || preview.error.message : send.error instanceof ApiError ? send.error.details || send.error.message : null;
  const isWhatsapp = canal === "whatsapp";
  const Icon = isWhatsapp ? MessageCircle : Mail;
  const messageLimit = isWhatsapp ? 4096 : null;

  return <div className="mail-dialog" role="dialog" aria-modal="true" aria-labelledby="mail-dialog-title">
    <button className="mail-dialog__backdrop" type="button" aria-label="Cerrar previsualización" onClick={onClose} />
    <section aria-busy={preview.isLoading || send.isPending} className="mail-dialog__panel" ref={panelRef} tabIndex={-1}>
      <header className="mail-dialog__header">
        <div className="mail-dialog__mark"><Icon aria-hidden="true" size={20} /></div>
        <div><p>Notificación al cliente</p><h3 id="mail-dialog-title">{isWhatsapp ? "Revisar WhatsApp antes de enviar" : "Revisar correo antes de enviar"}</h3></div>
        <button aria-label="Cerrar" className="icon-button" disabled={send.isPending} title="Cerrar" type="button" onClick={onClose}><X aria-hidden="true" size={17} /></button>
      </header>
      {preview.isLoading ? <div aria-live="polite" className="mail-dialog__loading" role="status">Preparando el mensaje…</div> : null}
      {preview.data ? <div className="mail-dialog__body">
        <div className="mail-dialog__route"><span>Para</span><strong>{preview.data.destinatario}</strong><small>Aviso {preview.data.numeroAviso} de {preview.data.maxAvisos}</small></div>
        {!preview.data.envioReal ? <div className="mail-dialog__simulation" role="status"><strong>Simulación</strong><span>Se registrará el aviso, pero no se enviará ningún {isWhatsapp ? "WhatsApp" : "correo"} real.</span></div> : null}
        {preview.data.envioReal ? <div className="mail-dialog__delivery" role="status"><strong>Envío real</strong><span>Se utilizará {providerLabel(preview.data.proveedor)}.</span></div> : null}
        {isWhatsapp ? <div className="mail-dialog__delivery"><strong>Condición de WhatsApp</strong><span>Puede requerir una plantilla aprobada si no existe una conversación reciente.</span></div> : null}
        {!isWhatsapp ? <label><span>Asunto</span><input autoComplete="off" maxLength={250} name="notification-subject" value={asunto} onChange={event => setAsunto(event.target.value)} /></label> : null}
        <label>
          <span>Mensaje</span>
          <textarea maxLength={messageLimit ?? undefined} name="notification-message" rows={isWhatsapp ? 9 : 12} value={mensaje} onChange={event => setMensaje(event.target.value)} />
          <small className="mail-dialog__counter">{messageLimit ? `${mensaje.length.toLocaleString("es-ES")} / ${messageLimit.toLocaleString("es-ES")} caracteres` : `${mensaje.length.toLocaleString("es-ES")} caracteres`}</small>
        </label>
      </div> : null}
      {error ? <div aria-live="assertive" className="mail-dialog__error" role="alert">{error} Revisa el destinatario y la configuración del canal antes de intentarlo de nuevo.</div> : null}
      <footer className="mail-dialog__actions">
        <button className="soft-button" disabled={send.isPending} type="button" onClick={onClose}>Cancelar</button>
        <button className="primary-button" disabled={!preview.data || (!isWhatsapp && !asunto.trim()) || !mensaje.trim() || send.isPending} type="button" onClick={() => send.mutate()}>
          <Send aria-hidden="true" size={16} />
          {send.isPending ? "Enviando…" : preview.data?.envioReal ? isWhatsapp ? "Enviar WhatsApp" : "Enviar correo" : "Registrar simulación"}
        </button>
      </footer>
    </section>
  </div>;
}

function providerLabel(provider?: string | null) {
  if (provider?.toLowerCase() === "whatsapp") return "WhatsApp Cloud API";
  return provider?.toLowerCase() === "graph" ? "Microsoft Graph" : "SMTP";
}
