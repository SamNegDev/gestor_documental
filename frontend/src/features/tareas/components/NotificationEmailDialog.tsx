import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Mail, Send, X } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import { getNotificacionPreview, notificarSeguimiento } from "../../seguimiento/services/seguimientoApi";

type Props = { incidenciaId: number | null; onClose: () => void; onSent: () => void };

export function NotificationEmailDialog({ incidenciaId, onClose, onSent }: Props) {
  const [asunto, setAsunto] = useState("");
  const [mensaje, setMensaje] = useState("");
  const preview = useQuery({ queryKey: ["notificacion-preview", incidenciaId], queryFn: () => getNotificacionPreview(incidenciaId!), enabled: incidenciaId !== null });
  const send = useMutation({ mutationFn: () => notificarSeguimiento(incidenciaId!, { asunto, mensaje }), onSuccess: () => { onSent(); onClose(); } });

  useEffect(() => {
    if (preview.data) { setAsunto(preview.data.asunto); setMensaje(preview.data.mensaje); }
  }, [preview.data]);

  if (incidenciaId === null) return null;
  const error = preview.error instanceof ApiError ? preview.error.details || preview.error.message : send.error instanceof ApiError ? send.error.details || send.error.message : null;

  return <div className="mail-dialog" role="dialog" aria-modal="true" aria-labelledby="mail-dialog-title">
    <button className="mail-dialog__backdrop" type="button" aria-label="Cerrar previsualizacion" onClick={onClose} />
    <section className="mail-dialog__panel">
      <header className="mail-dialog__header"><div className="mail-dialog__mark"><Mail size={20} /></div><div><p>Notificacion al cliente</p><h3 id="mail-dialog-title">Revisar correo antes de enviar</h3></div><button className="icon-button" title="Cerrar" type="button" onClick={onClose}><X size={17} /></button></header>
      {preview.isLoading ? <div className="mail-dialog__loading">Preparando el mensaje...</div> : null}
      {preview.data ? <div className="mail-dialog__body">
        <div className="mail-dialog__route"><span>Para</span><strong>{preview.data.destinatario}</strong><small>Aviso {preview.data.numeroAviso} de {preview.data.maxAvisos}</small></div>
        {!preview.data.envioReal ? <div className="mail-dialog__simulation">Modo simulacion: se registrara el aviso sin enviar un correo real.</div> : null}
        {preview.data.envioReal ? <div className="mail-dialog__delivery">Envio real mediante {providerLabel(preview.data.proveedor)}.</div> : null}
        <label><span>Asunto</span><input maxLength={250} value={asunto} onChange={event => setAsunto(event.target.value)} /></label>
        <label><span>Mensaje</span><textarea rows={12} value={mensaje} onChange={event => setMensaje(event.target.value)} /></label>
      </div> : null}
      {error ? <div className="mail-dialog__error">{error}</div> : null}
      <footer className="mail-dialog__actions"><button className="soft-button" type="button" onClick={onClose}>Cancelar</button><button className="primary-button" disabled={!preview.data || !asunto.trim() || !mensaje.trim() || send.isPending} type="button" onClick={() => send.mutate()}><Send size={16} /> {send.isPending ? "Enviando..." : preview.data?.envioReal ? "Enviar correo" : "Registrar simulacion"}</button></footer>
    </section>
  </div>;
}

function providerLabel(provider?: string | null) {
  return provider?.toLowerCase() === "graph" ? "Microsoft Graph" : "SMTP";
}
