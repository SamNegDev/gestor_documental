import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ArrowRight, CheckCircle2, Link2, MessageCircle, RefreshCw, Search, Smartphone, X } from "lucide-react";
import { Link } from "react-router-dom";
import { ApiError } from "../../../shared/api/http";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { PaginationBar } from "../../listados/components/PaginationBar";
import { getExpedienteListCatalogs } from "../../listados/services/listadosApi";
import { archivarWhatsappEvento, asociarWhatsappEvento, getWhatsappEventos, revisarWhatsappEvento } from "../services/whatsappApi";
import type { WhatsappEvento } from "../types";

const estados = [
  { value: "PENDIENTES", label: "Pendientes" },
  { value: "NO_ASOCIADOS", label: "Sin asociar" },
  { value: "ASOCIADOS", label: "Asociados" },
  { value: "REVISADOS", label: "Revisados" },
  { value: "ARCHIVADOS", label: "Archivados" },
  { value: "ERRORES", label: "Errores" },
  { value: "TODOS", label: "Todos" },
];

export function WhatsappInboxPage() {
  const [estado, setEstado] = useState("PENDIENTES");
  const [telefono, setTelefono] = useState("");
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(25);
  const [eventoParaAsociar, setEventoParaAsociar] = useState<WhatsappEvento | null>(null);
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ["whatsapp-eventos", estado, telefono, pagina, tamanio],
    queryFn: () => getWhatsappEventos({ estado, telefono, pagina, tamanio }),
  });
  const reviewMutation = useMutation({ mutationFn: revisarWhatsappEvento, onSuccess: () => qc.invalidateQueries({ queryKey: ["whatsapp-eventos"] }) });
  const archiveMutation = useMutation({ mutationFn: archivarWhatsappEvento, onSuccess: () => qc.invalidateQueries({ queryKey: ["whatsapp-eventos"] }) });
  const data = query.data;

  return (
    <main className="records-page whatsapp-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">Canal WhatsApp</p>
          <h2>Bandeja de mensajes</h2>
          <p>Mensajes entrantes recibidos por Cloud API, con asociacion a cliente y expediente cuando el telefono sea concluyente.</p>
        </div>
        <button className="soft-button" type="button" onClick={() => query.refetch()}>
          <RefreshCw size={16} />
          Actualizar
        </button>
      </header>

      <div className="task-tabs">
        {estados.map((item) => (
          <button className={estado === item.value ? "is-active" : ""} key={item.value} onClick={() => { setEstado(item.value); setPagina(0); }} type="button">
            <MessageCircle size={15} />
            {item.label}
          </button>
        ))}
      </div>

      <div className="task-filters">
        <label>
          <span>Telefono</span>
          <div className="filter-input">
            <Search size={15} />
            <input value={telefono} placeholder="Buscar por telefono" onChange={(event) => { setTelefono(event.target.value); setPagina(0); }} />
          </div>
        </label>
      </div>

      <section className="records-panel records-panel--ledger">
        {query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        <div className="whatsapp-list">
          {data?.contenido.length === 0 ? (
            <div className="records-empty">
              <MessageCircle size={24} />
              <strong>No hay mensajes en esta vista.</strong>
            </div>
          ) : null}
          {data?.contenido.map((evento) => (
            <WhatsappRow
              archivePending={archiveMutation.isPending}
              evento={evento}
              key={evento.id}
              onArchive={(id) => archiveMutation.mutate(id)}
              onAssociate={setEventoParaAsociar}
              onReview={(id) => reviewMutation.mutate(id)}
              reviewPending={reviewMutation.isPending}
            />
          ))}
        </div>
        <PaginationBar
          page={data?.pagina ?? 0}
          totalPages={data?.totalPaginas ?? 0}
          totalItems={data?.totalElementos ?? 0}
          pageSize={data?.tamanio ?? tamanio}
          onPageChange={setPagina}
          onPageSizeChange={(size) => { setTamanio(size); setPagina(0); }}
        />
      </section>
      <AssociateDialog
        evento={eventoParaAsociar}
        onClose={() => setEventoParaAsociar(null)}
        onDone={() => {
          setEventoParaAsociar(null);
          qc.invalidateQueries({ queryKey: ["whatsapp-eventos"] });
        }}
      />
    </main>
  );
}

function WhatsappRow({
  evento,
  onAssociate,
  onReview,
  onArchive,
  reviewPending,
  archivePending,
}: {
  evento: WhatsappEvento;
  onAssociate: (evento: WhatsappEvento) => void;
  onReview: (id: number) => void;
  onArchive: (id: number) => void;
  reviewPending: boolean;
  archivePending: boolean;
}) {
  const associated = Boolean(evento.clienteId);
  const pendiente = evento.estado !== "REVISADO" && evento.estado !== "ARCHIVADO";
  return (
    <article className={`whatsapp-row ${associated ? "whatsapp-row--linked" : "whatsapp-row--pending"} ${pendiente ? "" : "whatsapp-row--closed"}`}>
      <span className="whatsapp-row__icon"><Smartphone size={18} /></span>
      <div className="whatsapp-row__contact">
        <div className="whatsapp-row__badges">
          <StatusBadge tone={evento.estado === "PENDIENTE" ? "warning" : evento.estado === "REVISADO" ? "success" : "neutral"}>{evento.estado || "PENDIENTE"}</StatusBadge>
          <StatusBadge tone={associated ? "success" : "warning"}>{associated ? "ASOCIADO" : "SIN ASOCIAR"}</StatusBadge>
          <StatusBadge tone={evento.procesado ? "info" : "danger"}>{evento.tipo || "EVENTO"}</StatusBadge>
        </div>
        <strong>{evento.nombrePerfil || "Perfil sin nombre"}</strong>
        <small>{evento.telefono || "Sin telefono"} · {evento.fechaRecepcion || "Sin fecha"}</small>
      </div>
      <div className="whatsapp-row__message">
        <span>{evento.texto || evento.errorProcesado || "Mensaje sin texto visible"}</span>
      </div>
      <div className="whatsapp-row__links">
        <small>Cliente</small>
        <strong>{evento.cliente || "Pendiente"}</strong>
        {evento.expedienteId ? <span>{evento.matricula || `EXP-${evento.expedienteId}`}</span> : <span>Sin expediente</span>}
      </div>
      <div className="whatsapp-row__actions">
        {pendiente ? (
          <button className="icon-button" disabled={reviewPending} onClick={() => onReview(evento.id)} title="Marcar revisado" type="button"><CheckCircle2 size={16} /></button>
        ) : null}
        <button className="soft-button soft-button--compact" onClick={() => onAssociate(evento)} type="button">
          <Link2 size={15} />
          Asociar
        </button>
        {pendiente ? (
          <button className="icon-button" disabled={archivePending} onClick={() => onArchive(evento.id)} title="Archivar" type="button"><Archive size={16} /></button>
        ) : null}
        {evento.expedienteId ? <Link className="icon-button" title="Ver expediente" to={`/expedientes/${evento.expedienteId}`}><ArrowRight size={16} /></Link> : null}
      </div>
    </article>
  );
}

function AssociateDialog({ evento, onClose, onDone }: { evento: WhatsappEvento | null; onClose: () => void; onDone: () => void }) {
  const catalogs = useQuery({ queryKey: ["expedientes", "catalogos-listado"], queryFn: getExpedienteListCatalogs, enabled: Boolean(evento) });
  const [clienteId, setClienteId] = useState("");
  const [expedienteId, setExpedienteId] = useState("");
  const mutation = useMutation({
    mutationFn: () => asociarWhatsappEvento(evento!.id, { clienteId: Number(clienteId), expedienteId: expedienteId ? Number(expedienteId) : null }),
    onSuccess: onDone,
  });

  useEffect(() => {
    if (!evento) return;
    setClienteId(evento.clienteId ? String(evento.clienteId) : "");
    setExpedienteId(evento.expedienteId ? String(evento.expedienteId) : "");
  }, [evento]);

  if (!evento) return null;
  const message = mutation.error instanceof ApiError ? mutation.error.details || mutation.error.message : null;

  return (
    <div className="mail-dialog whatsapp-associate-dialog" role="dialog" aria-modal="true" aria-labelledby="whatsapp-associate-title">
      <button className="mail-dialog__backdrop" type="button" aria-label="Cerrar asociacion" onClick={onClose} />
      <section className="mail-dialog__panel whatsapp-associate-dialog__panel">
        <header className="mail-dialog__header">
          <div className="mail-dialog__mark"><Link2 size={20} /></div>
          <div><p>WhatsApp</p><h3 id="whatsapp-associate-title">Asociar mensaje</h3></div>
          <button className="icon-button" title="Cerrar" type="button" onClick={onClose}><X size={17} /></button>
        </header>
        <div className="mail-dialog__body">
          <div className="mail-dialog__route">
            <span>Origen</span>
            <strong>{evento.nombrePerfil || evento.telefono || "Mensaje entrante"}</strong>
            <small>{evento.texto || "Sin texto visible"}</small>
          </div>
          <label>
            <span>Cliente</span>
            <select value={clienteId} onChange={(event) => setClienteId(event.target.value)}>
              <option value="">Selecciona cliente</option>
              {catalogs.data?.clientes.map((cliente) => <option key={cliente.id} value={cliente.id}>{cliente.nombre}</option>)}
            </select>
          </label>
          <label>
            <span>ID expediente opcional</span>
            <input inputMode="numeric" min="1" placeholder="Ej. 124" type="number" value={expedienteId} onChange={(event) => setExpedienteId(event.target.value)} />
          </label>
          {message ? <div className="mail-dialog__error">{message}</div> : null}
        </div>
        <footer className="mail-dialog__actions">
          <button className="soft-button" type="button" onClick={onClose}>Cancelar</button>
          <button className="primary-button" disabled={!clienteId || mutation.isPending} type="button" onClick={() => mutation.mutate()}>
            <Link2 size={16} />
            {mutation.isPending ? "Asociando..." : "Asociar"}
          </button>
        </footer>
      </section>
    </div>
  );
}
