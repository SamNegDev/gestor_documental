import * as Tabs from "@radix-ui/react-tabs";
import type { FormEvent } from "react";
import { useState } from "react";
import { History, MessageCircle, MessageSquareWarning, MessagesSquare } from "lucide-react";
import type { ExpedienteDetail } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";
import { uppercaseInput } from "../../../shared/utils/text";

type Props = {
  expediente: ExpedienteDetail;
  onSendMessage: (contenido: string) => Promise<void>;
};

export function SecondaryExpedienteTabs({ expediente, onSendMessage }: Props) {
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const contenido = message.trim();
    if (!contenido) return;
    setSending(true);
    try {
      await onSendMessage(contenido);
      setMessage("");
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="exp-panel exp-panel--secondary">
      <Tabs.Root defaultValue="incidencias" className="secondary-tabs">
        <Tabs.List className="secondary-tabs__list" aria-label="Información secundaria del expediente">
          <Tabs.Trigger value="incidencias">
            <MessageSquareWarning size={16} /> Incidencias
          </Tabs.Trigger>
          <Tabs.Trigger value="historial">
            <History size={16} /> Historial
          </Tabs.Trigger>
          <Tabs.Trigger value="mensajes">
            <MessagesSquare size={16} /> Mensajes
          </Tabs.Trigger>
          <Tabs.Trigger value="whatsapp">
            <MessageCircle size={16} /> WhatsApp
          </Tabs.Trigger>
        </Tabs.List>

        <Tabs.Content value="incidencias" className="secondary-tabs__content">
          {expediente.incidencias.length === 0 ? (
            <p className="exp-empty">No hay incidencias registradas.</p>
          ) : (
            <div className="timeline-list">
              {expediente.incidencias.map((incidencia) => (
                <article className="timeline-item" key={incidencia.id}>
                  <strong>{humanizeEnum(incidencia.tipo)}</strong>
                  <span>{incidencia.resuelta ? "Resuelta" : "Activa"}</span>
                  <p>{incidencia.observaciones || "Sin observaciones"}</p>
                  <small>{formatDateTime(incidencia.fechaCreacion)}</small>
                </article>
              ))}
            </div>
          )}
        </Tabs.Content>

        <Tabs.Content value="historial" className="secondary-tabs__content">
          {expediente.historial.length === 0 ? (
            <p className="exp-empty">No hay movimientos en el historial.</p>
          ) : (
            <div className="timeline-list">
              {expediente.historial.map((item) => (
                <article className="timeline-item" key={item.id}>
                  <strong>{item.accion}</strong>
                  <p>{item.descripcion || "Sin descripción"}</p>
                  <small>{formatDateTime(item.fechaCambio)}{item.usuario ? ` · ${item.usuario}` : ""}</small>
                </article>
              ))}
            </div>
          )}
        </Tabs.Content>

        <Tabs.Content value="mensajes" className="secondary-tabs__content">
          <form className="message-form" onSubmit={handleSubmit}>
            <textarea
              onChange={(event) => setMessage(uppercaseInput(event.target.value))}
              placeholder="Escribe un mensaje para este expediente"
              rows={3}
              value={message}
            />
            <button className="primary-button" disabled={sending || !message.trim()} type="submit">
              Enviar mensaje
            </button>
          </form>

          {expediente.mensajes.length === 0 ? (
            <p className="exp-empty">No hay mensajes asociados.</p>
          ) : (
            <div className="timeline-list">
              {expediente.mensajes.map((mensaje) => (
                <article className="timeline-item" key={mensaje.id}>
                  <strong>{mensaje.autor || "Usuario"}</strong>
                  <p>{mensaje.contenido}</p>
                  <small>{formatDateTime(mensaje.fechaCreacion)}</small>
                </article>
              ))}
            </div>
          )}
        </Tabs.Content>

        <Tabs.Content value="whatsapp" className="secondary-tabs__content">
          {expediente.whatsappMensajes.length === 0 ? (
            <p className="exp-empty">No hay mensajes de WhatsApp asociados.</p>
          ) : (
            <div className="timeline-list">
              {expediente.whatsappMensajes.map((mensaje) => (
                <article className={`timeline-item timeline-item--whatsapp timeline-item--${(mensaje.estado || "PENDIENTE").toLowerCase()}`} key={mensaje.id}>
                  <strong>{mensaje.nombrePerfil || mensaje.telefono || "WhatsApp"}</strong>
                  <span>{mensaje.estado || "PENDIENTE"}</span>
                  <p>{mensaje.texto || "Mensaje sin texto visible"}</p>
                  <small>
                    {formatDateTime(mensaje.fechaRecepcion)}
                    {mensaje.revisadoPor ? ` · Revisado por ${mensaje.revisadoPor}` : ""}
                  </small>
                </article>
              ))}
            </div>
          )}
        </Tabs.Content>
      </Tabs.Root>
    </section>
  );
}
