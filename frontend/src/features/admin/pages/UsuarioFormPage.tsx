import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Save, UserRoundCheck } from "lucide-react";
import { createUsuario, getUsuario, getUsuarioCatalogs, updateUsuario } from "../services/adminApi";
import type { UsuarioInput } from "../types";

function emptyUsuario(): UsuarioInput {
  return {
    nombre: "",
    apellidos: "",
    email: "",
    password: "",
    rolUsuario: "CLIENTE",
    activo: true,
    clienteId: null,
  };
}

function clean(input: UsuarioInput, isEdit: boolean): UsuarioInput {
  const cleanText = (value?: string | null) => {
    const trimmed = value?.trim();
    return trimmed ? trimmed : null;
  };
  return {
    nombre: input.nombre.trim(),
    apellidos: input.apellidos.trim(),
    email: input.email.trim(),
    password: isEdit ? cleanText(input.password) : input.password?.trim() || "",
    rolUsuario: input.rolUsuario,
    activo: input.activo,
    clienteId: input.rolUsuario === "CLIENTE" ? input.clienteId || null : null,
  };
}

export function UsuarioFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<UsuarioInput>(emptyUsuario);

  const catalogsQuery = useQuery({
    queryKey: ["admin", "usuarios", "catalogos"],
    queryFn: getUsuarioCatalogs,
  });

  const usuarioQuery = useQuery({
    queryKey: ["admin", "usuarios", id],
    queryFn: () => getUsuario(id!),
    enabled: isEdit,
  });

  useEffect(() => {
    if (usuarioQuery.data) {
      setForm({
        nombre: usuarioQuery.data.nombre || "",
        apellidos: usuarioQuery.data.apellidos || "",
        email: usuarioQuery.data.email || "",
        password: "",
        rolUsuario: usuarioQuery.data.rol || "CLIENTE",
        activo: usuarioQuery.data.activo,
        clienteId: usuarioQuery.data.cliente?.id || null,
      });
    }
  }, [usuarioQuery.data]);

  useEffect(() => {
    if (!isEdit && form.rolUsuario === "CLIENTE" && !form.clienteId && catalogsQuery.data?.clientes[0]) {
      setForm((current) => ({ ...current, clienteId: catalogsQuery.data!.clientes[0].id }));
    }
  }, [catalogsQuery.data, form.clienteId, form.rolUsuario, isEdit]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!form.nombre.trim() || !form.apellidos.trim() || !form.email.trim() || !form.rolUsuario) {
        throw new Error("Nombre, apellidos, email y rol son obligatorios.");
      }
      if (!isEdit && !form.password?.trim()) throw new Error("La contrasena es obligatoria al crear un usuario.");
      if (form.rolUsuario === "CLIENTE" && !form.clienteId) throw new Error("Selecciona un cliente para el usuario.");
      if (isEdit && id) {
        await updateUsuario(id, clean(form, true));
        return { id: Number(id) };
      }
      return createUsuario(clean(form, false));
    },
    onSuccess: () => navigate("/admin/usuarios"),
    onError: (cause) => alert(cause instanceof Error ? cause.message : "No se pudo guardar el usuario."),
  });

  return (
    <main className="request-form-page admin-page">
      <section className="request-hero">
        <div>
          <p className="eyebrow">{isEdit ? "Editar usuario" : "Nuevo usuario"}</p>
          <div className="case-title-row">
            <div className="row-icon"><UserRoundCheck size={18} /></div>
            <div>
              <h2>{isEdit ? form.email || `Usuario #${id}` : "Alta de usuario"}</h2>
              <p>Acceso, rol y asociacion con cliente cuando corresponda.</p>
            </div>
          </div>
        </div>
        <Link className="soft-button soft-button--compact" to="/admin/usuarios">
          <ArrowLeft size={16} />
          Volver
        </Link>
      </section>

      <form className="request-form-grid" onSubmit={(event) => { event.preventDefault(); saveMutation.mutate(); }}>
        <section className="panel request-form-main">
          <div className="edit-form-grid">
            <label>
              Nombre
              <input value={form.nombre} required onChange={(event) => setForm({ ...form, nombre: event.target.value })} />
            </label>
            <label>
              Apellidos
              <input value={form.apellidos} required onChange={(event) => setForm({ ...form, apellidos: event.target.value })} />
            </label>
            <label>
              Email
              <input value={form.email} required type="email" onChange={(event) => setForm({ ...form, email: event.target.value })} />
            </label>
            <label>
              Contrasena
              <input
                value={form.password || ""}
                required={!isEdit}
                type="password"
                placeholder={isEdit ? "Dejar en blanco para mantener actual" : "Introduce la contrasena"}
                onChange={(event) => setForm({ ...form, password: event.target.value })}
              />
            </label>
            <label>
              Rol
              <select value={form.rolUsuario} required onChange={(event) => setForm({ ...form, rolUsuario: event.target.value, clienteId: event.target.value === "CLIENTE" ? form.clienteId : null })}>
                {catalogsQuery.data?.roles.map((rol) => (
                  <option key={rol} value={rol}>{rol}</option>
                ))}
              </select>
            </label>
            {form.rolUsuario === "CLIENTE" ? (
              <label>
                Cliente asignado
                <select value={form.clienteId || ""} required onChange={(event) => setForm({ ...form, clienteId: Number(event.target.value) })}>
                  <option value="">Selecciona cliente</option>
                  {catalogsQuery.data?.clientes.map((cliente) => (
                    <option key={cliente.id} value={cliente.id}>{cliente.nombre}</option>
                  ))}
                </select>
              </label>
            ) : null}
            <label className="toggle-field">
              <input checked={form.activo} type="checkbox" onChange={(event) => setForm({ ...form, activo: event.target.checked })} />
              <span>Usuario activo</span>
              <small>Los usuarios inactivos no pueden iniciar sesion y pueden eliminarse.</small>
            </label>
          </div>
        </section>
        <div className="request-form-actions">
          <Link className="soft-button" to="/admin/usuarios">Cancelar</Link>
          <button className="primary-button" disabled={saveMutation.isPending} type="submit">
            <Save size={16} />
            {saveMutation.isPending ? "Guardando" : "Guardar usuario"}
          </button>
        </div>
      </form>
    </main>
  );
}
