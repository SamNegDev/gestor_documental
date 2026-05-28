import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Building2, Save } from "lucide-react";
import { createCliente, getCliente, updateCliente } from "../services/adminApi";
import type { ClienteInput } from "../types";

function emptyCliente(): ClienteInput {
  return { nif: "", nombre: "", email: "", telefono: "", direccion: "" };
}

function clean(input: ClienteInput): ClienteInput {
  const cleanText = (value?: string | null) => {
    const trimmed = value?.trim();
    return trimmed ? trimmed : null;
  };
  return {
    nif: input.nif.trim().toUpperCase(),
    nombre: input.nombre.trim(),
    email: input.email.trim(),
    telefono: cleanText(input.telefono),
    direccion: cleanText(input.direccion),
  };
}

export function ClienteFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<ClienteInput>(emptyCliente);

  const clienteQuery = useQuery({
    queryKey: ["admin", "clientes", id],
    queryFn: () => getCliente(id!),
    enabled: isEdit,
  });

  useEffect(() => {
    if (clienteQuery.data) setForm(clienteQuery.data);
  }, [clienteQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!form.nif.trim() || !form.nombre.trim() || !form.email.trim()) throw new Error("NIF, nombre y email son obligatorios.");
      if (isEdit && id) {
        await updateCliente(id, clean(form));
        return { id: Number(id) };
      }
      return createCliente(clean(form));
    },
    onSuccess: () => navigate("/admin/clientes"),
    onError: (cause) => alert(cause instanceof Error ? cause.message : "No se pudo guardar el cliente."),
  });

  return (
    <main className="request-form-page admin-page">
      <section className="request-hero">
        <div>
          <p className="eyebrow">{isEdit ? "Editar cliente" : "Nuevo cliente"}</p>
          <div className="case-title-row">
            <div className="row-icon"><Building2 size={18} /></div>
            <div>
              <h2>{isEdit ? form.nombre || `Cliente #${id}` : "Alta de cliente"}</h2>
              <p>Datos fiscales y de contacto para asociar usuarios y expedientes.</p>
            </div>
          </div>
        </div>
        <Link className="soft-button soft-button--compact" to="/admin/clientes">
          <ArrowLeft size={16} />
          Volver
        </Link>
      </section>

      <form className="request-form-grid" onSubmit={(event) => { event.preventDefault(); saveMutation.mutate(); }}>
        <section className="panel request-form-main">
          <div className="edit-form-grid">
            <label>
              NIF/CIF
              <input value={form.nif} maxLength={20} required onChange={(event) => setForm({ ...form, nif: event.target.value })} />
            </label>
            <label>
              Nombre / razon social
              <input value={form.nombre} maxLength={120} required onChange={(event) => setForm({ ...form, nombre: event.target.value })} />
            </label>
            <label>
              Email
              <input value={form.email} maxLength={250} required type="email" onChange={(event) => setForm({ ...form, email: event.target.value })} />
            </label>
            <label>
              Telefono
              <input value={form.telefono || ""} maxLength={20} onChange={(event) => setForm({ ...form, telefono: event.target.value })} />
            </label>
            <label className="edit-form-grid__wide">
              Direccion
              <input value={form.direccion || ""} maxLength={200} onChange={(event) => setForm({ ...form, direccion: event.target.value })} />
            </label>
          </div>
        </section>
        <div className="request-form-actions">
          <Link className="soft-button" to="/admin/clientes">Cancelar</Link>
          <button className="primary-button" disabled={saveMutation.isPending} type="submit">
            <Save size={16} />
            {saveMutation.isPending ? "Guardando" : "Guardar cliente"}
          </button>
        </div>
      </form>
    </main>
  );
}
