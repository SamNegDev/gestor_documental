import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Mail, Pencil, Plus, Trash2, UserRoundCheck, UsersRound } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { deleteUsuario, getUsuarios } from "../services/adminApi";
import type { UsuarioAdmin } from "../types";

export function UsuariosListPage() {
  const queryClient = useQueryClient();
  const usuariosQuery = useQuery({
    queryKey: ["admin", "usuarios"],
    queryFn: getUsuarios,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUsuario,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "usuarios"] }),
    onError: () => alert("Solo se pueden eliminar usuarios inactivos y sin bloqueos asociados."),
  });

  const usuarios = usuariosQuery.data ?? [];

  return (
    <main className="records-page admin-page">
      <div className="records-header">
        <div>
          <p className="eyebrow">Gestion interna</p>
          <h2>Usuarios</h2>
          <p>Accesos del equipo interno y usuarios cliente asociados a cada cuenta.</p>
        </div>
        <div className="records-header__actions">
          <span className="records-count">{usuarios.length} usuarios</span>
          <Link className="primary-button primary-button--compact" to="/admin/usuarios/nuevo">
            <Plus size={16} />
            Nuevo usuario
          </Link>
        </div>
      </div>

      <section className="records-panel">
        <div className="records-panel__heading">
          <div>
            <h3>Usuarios registrados</h3>
            <span>{usuariosQuery.isFetching ? "Actualizando" : "\u00A0"}</span>
          </div>
        </div>
        {usuariosQuery.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        {usuariosQuery.error ? <div className="records-empty records-empty--danger">No se pudieron cargar los usuarios.</div> : null}
        {!usuariosQuery.isLoading && !usuariosQuery.error ? (
          <div className="admin-card-grid admin-card-grid--users">
            {usuarios.length === 0 ? <EmptyState /> : null}
            {usuarios.map((usuario) => (
              <UsuarioCard
                key={usuario.id}
                usuario={usuario}
                onDelete={() => {
                  if (window.confirm(`Eliminar ${usuario.nombreCompleto}?`)) deleteMutation.mutate(usuario.id);
                }}
              />
            ))}
          </div>
        ) : null}
      </section>
    </main>
  );
}

function UsuarioCard({ usuario, onDelete }: { usuario: UsuarioAdmin; onDelete: () => void }) {
  return (
    <article className="admin-entity-card">
      <div className="admin-entity-card__head">
        <span className="row-icon">
          <UserRoundCheck size={18} />
        </span>
        <div>
          <strong>{usuario.nombreCompleto}</strong>
          <small>{usuario.cliente?.nombre || (usuario.rol === "ADMIN" ? "Equipo interno" : "Sin cliente")}</small>
        </div>
      </div>
      <div className="admin-entity-card__body">
        <span><Mail size={15} /> {usuario.email}</span>
        <div className="admin-entity-card__badges">
          <StatusBadge tone={usuario.rol === "ADMIN" ? "danger" : "info"}>{usuario.rol}</StatusBadge>
          <StatusBadge tone={usuario.activo ? "success" : "neutral"}>{usuario.activo ? "Activo" : "Inactivo"}</StatusBadge>
        </div>
      </div>
      <div className="admin-entity-card__actions">
        <Link className="soft-button soft-button--compact" to={`/admin/usuarios/${usuario.id}/editar`}>
          <Pencil size={15} />
          Editar
        </Link>
        {!usuario.activo ? (
          <button className="icon-button icon-button--danger" type="button" onClick={onDelete} title="Eliminar usuario">
            <Trash2 size={16} />
          </button>
        ) : null}
      </div>
    </article>
  );
}

function EmptyState() {
  return (
    <div className="records-empty">
      <UsersRound size={24} />
      <strong>No hay usuarios registrados.</strong>
    </div>
  );
}
