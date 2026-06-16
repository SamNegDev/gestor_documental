import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Building2, ExternalLink, FileText, Image, Save, Trash2, Upload } from "lucide-react";
import {
  createCliente,
  deleteClienteDocumento,
  deleteClienteLogo,
  getCliente,
  updateCliente,
  uploadClienteDocumento,
  uploadClienteLogo,
} from "../services/adminApi";
import type { ClienteInput } from "../types";
import type { DocumentoExpediente } from "../../expedientes/types/expedienteDetail.types";
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

const CLIENT_DOCUMENT_TYPES = [
  { value: "DNI", label: "DNI / NIE" },
  { value: "CIF", label: "CIF" },
  { value: "MANDATO", label: "Mandato" },
  { value: "MANDATO_REPRESENTACION", label: "Mandato representacion" },
  { value: "OTROS", label: "Otros" },
];

function emptyCliente(): ClienteInput {
  return { nif: "", nombre: "", email: "", telefono: "", direccion: "", preferenciaCanal: "AMBOS" };
}

function clean(input: ClienteInput): ClienteInput {
  return {
    nif: cleanUpperText(input.nif) || "",
    nombre: cleanUpperText(input.nombre) || "",
    email: cleanLowerText(input.email) || "",
    telefono: cleanUpperText(input.telefono),
    direccion: cleanUpperText(input.direccion),
    preferenciaCanal: input.preferenciaCanal || "AMBOS",
  };
}

export function ClienteFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<ClienteInput>(emptyCliente);
  const [branding, setBranding] = useState<BrandingState>({ principal: null, compacto: null });
  const [brandingFeedback, setBrandingFeedback] = useState<BrandingFeedback>(null);
  const [documentos, setDocumentos] = useState<DocumentoExpediente[]>([]);
  const [documentType, setDocumentType] = useState("DNI");
  const [documentFile, setDocumentFile] = useState<File | null>(null);
  const [documentFeedback, setDocumentFeedback] = useState<BrandingFeedback>(null);
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
        preferenciaCanal: clienteQuery.data.preferenciaCanal || "AMBOS",
      });
      setBranding({
        principal: clienteQuery.data.logoPrincipalUrl || null,
        compacto: clienteQuery.data.logoCompactoUrl || null,
      });
      setDocumentos(clienteQuery.data.documentos || []);
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

  const uploadDocumentMutation = useMutation({
    mutationFn: () => {
      if (!documentFile) throw new Error("Selecciona un PDF del cliente.");
      return uploadClienteDocumento(id!, documentType, documentFile);
    },
    onSuccess: (cliente) => {
      setDocumentos(cliente.documentos || []);
      setDocumentFile(null);
      setDocumentFeedback({ tone: "success", text: "Documento del cliente incorporado." });
    },
    onError: (cause) => setDocumentFeedback({ tone: "danger", text: errorMessage(cause, "No se pudo subir el documento.") }),
  });

  const deleteDocumentMutation = useMutation({
    mutationFn: (documentoId: number) => deleteClienteDocumento(documentoId),
    onSuccess: (_, documentoId) => {
      setDocumentos((current) => current.filter((documento) => documento.id !== documentoId));
      setDocumentFeedback({ tone: "success", text: "Documento del cliente eliminado." });
    },
    onError: (cause) => setDocumentFeedback({ tone: "danger", text: errorMessage(cause, "No se pudo eliminar el documento.") }),
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

  function handleDocumentFile(archivo?: File) {
    setDocumentFeedback(null);
    if (!archivo) {
      setDocumentFile(null);
      return;
    }
    if (archivo.type && archivo.type !== "application/pdf") {
      setDocumentFeedback({ tone: "danger", text: "El documento debe ser un PDF." });
      return;
    }
    if (!archivo.name.toLowerCase().endsWith(".pdf")) {
      setDocumentFeedback({ tone: "danger", text: "El documento debe tener extension PDF." });
      return;
    }
    if (archivo.size > 15 * 1024 * 1024) {
      setDocumentFeedback({ tone: "danger", text: "El PDF no puede superar 15 MB." });
      return;
    }
    setDocumentFile(archivo);
  }

  async function handleDeleteDocument(documento: DocumentoExpediente) {
    if (!documento.id) return;
    const confirmed = await confirm({
      title: "Eliminar documento del cliente",
      description: "Los expedientes que lo estuvieran reutilizando volveran a mostrar ese requisito como pendiente.",
      confirmLabel: "Eliminar documento",
      tone: "danger",
    });
    if (confirmed) deleteDocumentMutation.mutate(documento.id);
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
            <label>
              Canal de avisos
              <select value={form.preferenciaCanal || "AMBOS"} onChange={(event) => setForm({ ...form, preferenciaCanal: event.target.value as ClienteInput["preferenciaCanal"] })}>
                <option value="AMBOS">Email y WhatsApp</option>
                <option value="EMAIL">Solo email</option>
                <option value="WHATSAPP">Solo WhatsApp</option>
                <option value="SIN_AVISOS">Sin avisos automaticos</option>
              </select>
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

        <section className="client-documents-panel" aria-labelledby="client-documents-title">
          <div className="client-branding-panel__heading">
            <div className="row-icon"><FileText size={18} /></div>
            <div>
              <p className="eyebrow">Documentacion recurrente</p>
              <h3 id="client-documents-title">PDFs del cliente</h3>
              <p>DNI, CIF, mandato y otros documentos reutilizables cuando el cliente figura como interesado.</p>
            </div>
          </div>

          {isEdit ? (
            <>
              <div className="client-document-uploader">
                <label>
                  Tipo
                  <select value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
                    {CLIENT_DOCUMENT_TYPES.map((tipo) => (
                      <option key={tipo.value} value={tipo.value}>{tipo.label}</option>
                    ))}
                  </select>
                </label>
                <label className="client-document-uploader__file">
                  PDF
                  <span className="document-file-picker soft-button">
                    <Upload size={15} />
                    <span>{documentFile?.name || "Seleccionar PDF"}</span>
                    <input
                      accept="application/pdf,.pdf"
                      type="file"
                      onChange={(event) => {
                        handleDocumentFile(event.target.files?.[0]);
                        event.target.value = "";
                      }}
                    />
                  </span>
                </label>
                <button
                  className="primary-button"
                  disabled={!documentFile || uploadDocumentMutation.isPending}
                  type="button"
                  onClick={() => uploadDocumentMutation.mutate()}
                >
                  <Upload size={16} />
                  {uploadDocumentMutation.isPending ? "Subiendo" : "Subir PDF"}
                </button>
              </div>

              <ClientDocumentsList
                busyId={deleteDocumentMutation.variables}
                documentos={documentos}
                onDelete={handleDeleteDocument}
              />
            </>
          ) : (
            <div className="client-branding-panel__locked">
              <FileText size={20} />
              <div>
                <strong>Guarda primero los datos del cliente</strong>
                <span>Despues podras incorporar sus PDFs recurrentes.</span>
              </div>
            </div>
          )}

          {documentFeedback ? (
            <p className={`client-branding-feedback client-branding-feedback--${documentFeedback.tone}`} role="status">
              {documentFeedback.text}
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

function ClientDocumentsList({
  documentos,
  busyId,
  onDelete,
}: {
  documentos: DocumentoExpediente[];
  busyId?: number;
  onDelete: (documento: DocumentoExpediente) => void;
}) {
  if (!documentos.length) {
    return (
      <div className="client-documents-empty">
        <FileText size={18} />
        <span>No hay PDFs recurrentes registrados para este cliente.</span>
      </div>
    );
  }

  return (
    <div className="client-documents-list">
      {documentos.map((documento) => (
        <article className="client-document-row" key={documento.id}>
          <div className="client-document-row__icon"><FileText size={17} /></div>
          <div className="client-document-row__main">
            <strong>{documento.nombreOriginal || documento.nombre}</strong>
            <span>{documentTypeLabel(documento.tipo)} · {formatDate(documento.fechaSubida)}</span>
          </div>
          {documento.id ? (
            <div className="client-document-row__actions">
              <a className="icon-button" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer" title="Ver PDF">
                <ExternalLink size={15} />
              </a>
              <a className="icon-button" href={`/documentos/descargar/${documento.id}`} title="Descargar PDF">
                <FileText size={15} />
              </a>
              <button
                className="icon-button icon-button--danger"
                disabled={busyId === documento.id}
                type="button"
                title="Eliminar PDF"
                onClick={() => onDelete(documento)}
              >
                <Trash2 size={15} />
              </button>
            </div>
          ) : null}
        </article>
      ))}
    </div>
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

function documentTypeLabel(tipo: string) {
  return CLIENT_DOCUMENT_TYPES.find((item) => item.value === tipo)?.label || tipo.replaceAll("_", " ");
}

function formatDate(value?: string | null) {
  if (!value) return "Sin fecha";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("es-ES", { dateStyle: "short", timeStyle: "short" }).format(date);
}
