import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Building2, Image, Save, Trash2, Upload } from "lucide-react";
import { createCliente, deleteClienteLogo, getCliente, updateCliente, uploadClienteLogo } from "../services/adminApi";
import type { ClienteInput } from "../types";
import { cleanLowerText, cleanUpperText, uppercaseInput } from "../../../shared/utils/text";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";

type LogoType = "principal" | "compacto";

type BrandingState = {
  principal: string | null;
  compacto: string | null;
};

type BrandingFeedback = {
  tone: "success" | "danger";
  text: string;
} | null;

function emptyCliente(): ClienteInput {
  return { nif: "", nombre: "", email: "", telefono: "", direccion: "" };
}

function clean(input: ClienteInput): ClienteInput {
  return {
    nif: cleanUpperText(input.nif) || "",
    nombre: cleanUpperText(input.nombre) || "",
    email: cleanLowerText(input.email) || "",
    telefono: cleanUpperText(input.telefono),
    direccion: cleanUpperText(input.direccion),
  };
}

export function ClienteFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<ClienteInput>(emptyCliente);
  const [branding, setBranding] = useState<BrandingState>({ principal: null, compacto: null });
  const [brandingFeedback, setBrandingFeedback] = useState<BrandingFeedback>(null);
  const { confirm, dialog } = useConfirmDialog();

  const clienteQuery = useQuery({
    queryKey: ["admin", "clientes", id],
    queryFn: () => getCliente(id!),
    enabled: isEdit,
  });

  useEffect(() => {
    if (clienteQuery.data) {
      setForm({
        ...clienteQuery.data,
        nif: uppercaseInput(clienteQuery.data.nif || ""),
        nombre: uppercaseInput(clienteQuery.data.nombre || ""),
        telefono: uppercaseInput(clienteQuery.data.telefono || ""),
        direccion: uppercaseInput(clienteQuery.data.direccion || ""),
      });
      setBranding({
        principal: clienteQuery.data.logoPrincipalUrl || null,
        compacto: clienteQuery.data.logoCompactoUrl || null,
      });
    }
  }, [clienteQuery.data]);

  const uploadLogoMutation = useMutation({
    mutationFn: ({ tipo, archivo }: { tipo: LogoType; archivo: File }) => uploadClienteLogo(id!, tipo, archivo),
    onSuccess: (_, { tipo }) => {
      setBranding((current) => ({
        ...current,
        [tipo]: `/api/clientes/${id}/logos/${tipo}?v=${Date.now()}`,
      }));
      setBrandingFeedback({ tone: "success", text: `Logo ${tipo === "principal" ? "completo" : "compacto"} actualizado.` });
    },
    onError: (cause) => setBrandingFeedback({ tone: "danger", text: errorMessage(cause, "No se pudo subir el logo.") }),
  });

  const deleteLogoMutation = useMutation({
    mutationFn: (tipo: LogoType) => deleteClienteLogo(id!, tipo),
    onSuccess: (_, tipo) => {
      setBranding((current) => ({ ...current, [tipo]: null }));
      setBrandingFeedback({ tone: "success", text: `Logo ${tipo === "principal" ? "completo" : "compacto"} eliminado.` });
    },
    onError: (cause) => setBrandingFeedback({ tone: "danger", text: errorMessage(cause, "No se pudo eliminar el logo.") }),
  });

  function handleLogoFile(tipo: LogoType, archivo?: File) {
    if (!archivo) return;
    setBrandingFeedback(null);
    if (!['image/png', 'image/jpeg'].includes(archivo.type)) {
      setBrandingFeedback({ tone: "danger", text: "El archivo debe ser una imagen PNG o JPEG." });
      return;
    }
    if (archivo.size > 5 * 1024 * 1024) {
      setBrandingFeedback({ tone: "danger", text: "El logo no puede superar 5 MB." });
      return;
    }
    uploadLogoMutation.mutate({ tipo, archivo });
  }

  async function handleDeleteLogo(tipo: LogoType) {
    const confirmed = await confirm({
      title: `Eliminar logo ${tipo === "principal" ? "completo" : "compacto"}`,
      description: "El cliente volvera a mostrarse con su nombre o sus iniciales hasta que se cargue otra imagen.",
      confirmLabel: "Eliminar logo",
      tone: "danger",
    });
    if (confirmed) deleteLogoMutation.mutate(tipo);
  }

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
              <input value={form.nif} maxLength={20} required onChange={(event) => setForm({ ...form, nif: uppercaseInput(event.target.value) })} />
            </label>
            <label>
              Nombre / razon social
              <input value={form.nombre} maxLength={120} required onChange={(event) => setForm({ ...form, nombre: uppercaseInput(event.target.value) })} />
            </label>
            <label>
              Email
              <input value={form.email} maxLength={250} required type="email" onChange={(event) => setForm({ ...form, email: event.target.value })} />
            </label>
            <label>
              Telefono
              <input value={form.telefono || ""} maxLength={20} onChange={(event) => setForm({ ...form, telefono: uppercaseInput(event.target.value) })} />
            </label>
            <label className="edit-form-grid__wide">
              Direccion
              <input value={form.direccion || ""} maxLength={200} onChange={(event) => setForm({ ...form, direccion: uppercaseInput(event.target.value) })} />
            </label>
          </div>
        </section>

        <section className="client-branding-panel" aria-labelledby="client-branding-title">
          <div className="client-branding-panel__heading">
            <div className="row-icon"><Image size={18} /></div>
            <div>
              <p className="eyebrow">Identidad visual</p>
              <h3 id="client-branding-title">Logos del cliente</h3>
              <p>Se utilizaran para identificar al cliente en su portal y en las cabeceras de sus expedientes.</p>
            </div>
          </div>

          {isEdit ? (
            <div className="client-branding-grid">
              <LogoEditor
                tipo="principal"
                title="Logo completo"
                description="Simbolo y nombre comercial. Recomendado para el portal del cliente."
                imageUrl={branding.principal}
                busy={isLogoBusy("principal", uploadLogoMutation, deleteLogoMutation)}
                onFile={handleLogoFile}
                onDelete={handleDeleteLogo}
              />
              <LogoEditor
                tipo="compacto"
                title="Icono compacto"
                description="Solo el simbolo. Se muestra en cabeceras y espacios reducidos."
                imageUrl={branding.compacto}
                busy={isLogoBusy("compacto", uploadLogoMutation, deleteLogoMutation)}
                onFile={handleLogoFile}
                onDelete={handleDeleteLogo}
              />
            </div>
          ) : (
            <div className="client-branding-panel__locked">
              <Image size={20} />
              <div>
                <strong>Guarda primero los datos del cliente</strong>
                <span>Después podrás añadir sus logos desde la pantalla de edición.</span>
              </div>
            </div>
          )}

          {brandingFeedback ? (
            <p className={`client-branding-feedback client-branding-feedback--${brandingFeedback.tone}`} role="status">
              {brandingFeedback.text}
            </p>
          ) : null}
        </section>

        <div className="request-form-actions">
          <Link className="soft-button" to="/admin/clientes">Cancelar</Link>
          <button className="primary-button" disabled={saveMutation.isPending} type="submit">
            <Save size={16} />
            {saveMutation.isPending ? "Guardando" : "Guardar cliente"}
          </button>
        </div>
      </form>
      {dialog}
    </main>
  );
}

function LogoEditor({
  tipo,
  title,
  description,
  imageUrl,
  busy,
  onFile,
  onDelete,
}: {
  tipo: LogoType;
  title: string;
  description: string;
  imageUrl: string | null;
  busy: boolean;
  onFile: (tipo: LogoType, archivo?: File) => void;
  onDelete: (tipo: LogoType) => void;
}) {
  return (
    <article className={`client-logo-editor client-logo-editor--${tipo}`}>
      <div className="client-logo-editor__preview">
        {imageUrl ? <img src={imageUrl} alt={`${title} del cliente`} /> : <Image size={28} aria-hidden="true" />}
      </div>
      <div className="client-logo-editor__content">
        <strong>{title}</strong>
        <p>{description}</p>
        <small>PNG o JPEG · Maximo 5 MB</small>
      </div>
      <div className="client-logo-editor__actions">
        <label className={`soft-button soft-button--compact ${busy ? "is-disabled" : ""}`}>
          <Upload size={15} />
          {imageUrl ? "Sustituir" : "Subir"}
          <input
            accept="image/png,image/jpeg"
            disabled={busy}
            type="file"
            onChange={(event) => {
              onFile(tipo, event.target.files?.[0]);
              event.target.value = "";
            }}
          />
        </label>
        {imageUrl ? (
          <button className="icon-button icon-button--danger" disabled={busy} type="button" title={`Eliminar ${title.toLowerCase()}`} onClick={() => onDelete(tipo)}>
            <Trash2 size={16} />
          </button>
        ) : null}
      </div>
    </article>
  );
}

function isLogoBusy(
  tipo: LogoType,
  uploadMutation: { isPending: boolean; variables?: { tipo: LogoType } },
  deleteMutation: { isPending: boolean; variables?: LogoType },
) {
  return (uploadMutation.isPending && uploadMutation.variables?.tipo === tipo)
    || (deleteMutation.isPending && deleteMutation.variables === tipo);
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause && typeof cause === "object" && "details" in cause && typeof cause.details === "string") {
    return cause.details;
  }
  return cause instanceof Error ? cause.message : fallback;
}
