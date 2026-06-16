import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { BellRing, Mail, Pencil, Phone, Plus, Trash2, UsersRound } from "lucide-react";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { deleteCliente, getClientes } from "../services/adminApi";
import type { ClienteAdmin } from "../types";

export function ClientesListPage() {
  const queryClient = useQueryClient();
  const { confirm, dialog } = useConfirmDialog();
  const clientesQuery = useQuery({
    queryKey: ["admin", "clientes"],
    queryFn: getClientes,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteCliente,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "clientes"] }),
    onError: () => alert("No se pudo eliminar el cliente. Puede tener registros asociados."),
  });

  const clientes = clientesQuery.data ?? [];

  return (
    <main className="records-page admin-page">
      <div className="records-header">
        <div>
          <p className="eyebrow">Gestión interna</p>
          <h2>Clientes</h2>
          <p>Empresas y particulares vinculados a solicitudes, expedientes y usuarios.</p>
        </div>
        <div className="records-header__actions">
          <span className="records-count">{clientes.length} clientes</span>
          <Link className="primary-button primary-button--compact" to="/admin/clientes/nuevo">
            <Plus size={16} />
            Nuevo cliente
          </Link>
        </div>
      </div>

      <section className="records-panel">
        <div className="records-panel__heading">
          <div>
            <h3>Clientes registrados</h3>
            <span>{clientesQuery.isFetching ? "Actualizando" : "\u00A0"}</span>
          </div>
        </div>
        {clientesQuery.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        {clientesQuery.error ? <div className="records-empty records-empty--danger">No se pudieron cargar los clientes.</div> : null}
        {!clientesQuery.isLoading && !clientesQuery.error ? (
          <div className="admin-card-grid">
            {clientes.length === 0 ? <EmptyState text="No hay clientes registrados." /> : null}
            {clientes.map((cliente) => (
              <ClienteCard
                cliente={cliente}
                key={cliente.id}
                onDelete={async () => {
                  const confirmed = await confirm({
                    title: "Eliminar cliente",
                    description: `Se eliminara ${cliente.nombre}. Si tiene registros asociados, el sistema bloqueara la operacion.`,
                    confirmLabel: "Eliminar",
                    tone: "danger",
                  });
                  if (confirmed) deleteMutation.mutate(cliente.id);
                }}
              />
            ))}
          </div>
        ) : null}
      </section>
      {dialog}
    </main>
  );
}

function ClienteCard({ cliente, onDelete }: { cliente: ClienteAdmin; onDelete: () => void }) {
  return (
    <article className="admin-entity-card">
      <div className="admin-entity-card__head">
        <span className="row-icon">
          <UsersRound size={18} />
        </span>
        <div>
          <strong>{cliente.nombre}</strong>
          <small>{cliente.nif}</small>
        </div>
      </div>
      <div className="admin-entity-card__body">
        <span><Mail size={15} /> {cliente.email}</span>
        <span><Phone size={15} /> {cliente.telefono || "Sin telefono"}</span>
        <span><BellRing size={15} /> {canalLabel(cliente.preferenciaCanal)}</span>
        <small>{cliente.direccion || "Sin direccion"}</small>
      </div>
      <div className="admin-entity-card__actions">
        <Link className="soft-button soft-button--compact" to={`/admin/clientes/${cliente.id}/editar`}>
          <Pencil size={15} />
          Editar
        </Link>
        <button className="icon-button icon-button--danger" type="button" onClick={onDelete} title="Eliminar cliente">
          <Trash2 size={16} />
        </button>
      </div>
    </article>
  );
}

function canalLabel(value?: string | null) {
  if (value === "EMAIL") return "Solo email";
  if (value === "WHATSAPP") return "Solo WhatsApp";
  if (value === "SIN_AVISOS") return "Sin avisos";
  return "Email y WhatsApp";
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="records-empty">
      <UsersRound size={24} />
      <strong>{text}</strong>
    </div>
  );
}
