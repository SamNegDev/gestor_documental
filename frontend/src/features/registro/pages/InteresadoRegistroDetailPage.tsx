import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ExternalLink, FileText, MapPin, Pencil, Phone, Save, Trash2, Upload, UserCheck, UserRound, X } from "lucide-react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { deleteInteresadoDocumento, getInteresadoRegistro, markInteresadoAsHabitual, updateInteresadoRegistro, uploadInteresadoDocumento } from "../services/registroApi";
import { TramitesRegistroTable } from "../components/TramitesRegistroTable";
import { RegistroSummary } from "../components/RegistroSummary";
import type { DocumentoExpediente } from "../../expedientes/types/expedienteDetail.types";
import type { InteresadoRegistroUpdateInput } from "../types";
import { uppercaseInput } from "../../../shared/utils/text";
import { AddressFields, type AddressValue } from "../../../shared/ui/AddressFields";
import { ApiError } from "../../../shared/api/http";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import "../../expedientes/styles/expedienteDetail.css";

const INTERESADO_DOCUMENT_TYPES = [
  { value: "DNI", label: "DNI / NIE" },
  { value: "CIF", label: "CIF" },
  { value: "MANDATO", label: "Mandato" },
  { value: "MANDATO_REPRESENTACION", label: "Mandato representacion" },
  { value: "OTROS", label: "Otros" },
];

export function InteresadoRegistroDetailPage() {
  const { id = "" } = useParams();
  const { user } = useOutletContext<AppOutletContext>();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<InteresadoRegistroUpdateInput>({});
  const [documentType, setDocumentType] = useState("DNI");
  const [documentFile, setDocumentFile] = useState<File | null>(null);
  const [documentError, setDocumentError] = useState<string | null>(null);
  const { confirm, dialog } = useConfirmDialog();
  const query = useQuery({ queryKey: ["registro", "interesado", id], queryFn: () => getInteresadoRegistro(id), enabled: Boolean(id) });
  const mutation = useMutation({
    mutationFn: (input: InteresadoRegistroUpdateInput) => updateInteresadoRegistro(id, input),
    onSuccess: async () => {
      setEditing(false);
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesado", id] });
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesados"] });
    },
  });
  const uploadDocumentMutation = useMutation({
    mutationFn: () => {
      if (!documentFile) throw new Error("Selecciona un PDF.");
      return uploadInteresadoDocumento(id, documentType, documentFile);
    },
    onSuccess: async () => {
      setDocumentFile(null);
      setDocumentError(null);
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesado", id] });
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesados"] });
    },
    onError: (cause) => setDocumentError(cause instanceof ApiError ? cause.details || "No se pudo subir el documento." : "No se pudo subir el documento."),
  });
  const deleteDocumentMutation = useMutation({
    mutationFn: (documentoId: number) => deleteInteresadoDocumento(documentoId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesado", id] });
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesados"] });
    },
    onError: (cause) => setDocumentError(cause instanceof ApiError ? cause.details || "No se pudo eliminar el documento." : "No se pudo eliminar el documento."),
  });
  const markHabitualMutation = useMutation({
    mutationFn: () => markInteresadoAsHabitual(id),
    onSuccess: async (actualizado) => {
      queryClient.setQueryData(["registro", "interesado", id], actualizado);
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesados"] });
    },
    onError: (cause) => alert(cause instanceof ApiError ? cause.details || "No se pudo marcar como cliente habitual." : "No se pudo marcar como cliente habitual."),
  });

  useEffect(() => {
    if (!query.data) return;
    setForm({
      dni: query.data.dni || "",
      nombre: query.data.nombre || "",
      telefono: query.data.telefono || "",
      direccion: query.data.direccion || "",
      tipoVia: query.data.tipoVia || "",
      nombreVia: query.data.nombreVia || "",
      codigoPostal: query.data.codigoPostal || "",
      municipio: query.data.municipio || "",
      provincia: query.data.provincia || "",
      tipoPersona: query.data.tipoPersona || "PARTICULAR",
    });
  }, [query.data]);

  if (query.isLoading) return <div className="records-skeleton"><span /><span /><span /></div>;
  if (!query.data) return <div className="records-empty records-empty--danger">No se pudo cargar el interesado.</div>;
  const item = query.data;
  const canEdit = user?.rol === "ADMIN";
  const canManageHabitualDocs = user?.rol === "CLIENTE" && item.habitual;
  const canMarkAsHabitual = user?.rol === "CLIENTE" && !item.habitual;

  const updateField = (field: keyof InteresadoRegistroUpdateInput, value: string) => {
    setForm((current) => ({ ...current, [field]: uppercaseInput(value) }));
  };
  const updateAddress = (value: AddressValue) => {
    setForm((current) => ({ ...current, ...value, direccion: "" }));
  };
  const handleDocumentFile = (archivo?: File) => {
    setDocumentError(null);
    if (!archivo) {
      setDocumentFile(null);
      return;
    }
    if (!archivo.name.toLowerCase().endsWith(".pdf")) {
      setDocumentError("El documento debe ser un PDF.");
      return;
    }
    setDocumentFile(archivo);
  };
  const handleDeleteDocument = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    const confirmed = await confirm({
      title: "Eliminar documento",
      description: "Los expedientes que lo reutilicen volveran a mostrar el requisito como pendiente.",
      confirmLabel: "Eliminar documento",
      tone: "danger",
    });
    if (confirmed) deleteDocumentMutation.mutate(documento.id);
  };

  return <main className="records-page registry-detail-page">
    <Link className="registry-back" to="/interesados"><ArrowLeft size={16} /> Volver al registro</Link>
    <section className="registry-profile"><span className="registry-profile__icon"><UserRound size={28} /></span><div><p className="eyebrow">{item.habitual ? "CLIENTE HABITUAL" : "INTERESADO PUNTUAL"} - {item.representanteLegal ? "REPRESENTANTE LEGAL" : item.tipoPersona || "INTERESADO"}</p><h2>{item.nombre}</h2><strong>{item.dni}</strong></div><div className="registry-profile__facts"><span><Phone size={16} />{item.telefono || "Sin telefono"}</span><span><MapPin size={16} />{item.direccion || "Sin direccion"}</span><span><strong>{item.totalTramites}</strong> tramites asociados</span></div>{canMarkAsHabitual ? <button className="primary-button primary-button--compact registry-profile__edit" disabled={markHabitualMutation.isPending} onClick={() => markHabitualMutation.mutate()} type="button"><UserCheck size={15} />Marcar habitual</button> : null}{canEdit ? <button className="soft-button soft-button--compact registry-profile__edit" onClick={() => setEditing(true)} type="button"><Pencil size={15} />Editar ficha</button> : null}</section>
    {editing ? (
      <section className="records-panel vehicle-edit-panel">
        <div className="records-panel__heading">
          <div>
            <h3>{item.habitual ? "Datos del cliente habitual" : "Datos del interesado puntual"}</h3>
            <span>Ficha reutilizable editable desde este registro</span>
          </div>
          <button className="icon-button" onClick={() => setEditing(false)} title="Cerrar edicion" type="button"><X size={16} /></button>
        </div>
        <form className="vehicle-edit-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate(form); }}>
          <label><span>DNI / CIF</span><input required value={form.dni || ""} onChange={(event) => updateField("dni", event.target.value)} /></label>
          <label><span>Nombre</span><input required value={form.nombre || ""} onChange={(event) => updateField("nombre", event.target.value)} /></label>
          <label><span>Telefono</span><input value={form.telefono || ""} onChange={(event) => updateField("telefono", event.target.value)} /></label>
          <label><span>Tipo</span><select value={form.tipoPersona || "PARTICULAR"} onChange={(event) => updateField("tipoPersona", event.target.value)}><option value="PARTICULAR">PARTICULAR</option><option value="EMPRESA">EMPRESA</option></select></label>
          <AddressFields idPrefix="interesado-registro" value={form} onChange={updateAddress} wideClassName="vehicle-edit-form__wide" />
          {mutation.isError ? <p className="form-error">{mutation.error instanceof ApiError ? mutation.error.details || "No se pudo guardar la ficha." : "No se pudo guardar la ficha."}</p> : null}
          <div className="vehicle-edit-form__actions">
            <button className="soft-button" onClick={() => setEditing(false)} type="button">Cancelar</button>
            <button className="primary-button" disabled={mutation.isPending} type="submit"><Save size={16} /> Guardar ficha</button>
          </div>
        </form>
      </section>
    ) : null}
    {canManageHabitualDocs ? (
      <section className="records-panel client-documents-panel">
        <div className="records-panel__heading">
          <div><h3>Documentacion recurrente</h3><span>Estos PDFs se reutilizan cuando este cliente habitual participa en tus expedientes.</span></div>
        </div>
        <div className="client-document-uploader">
          <label>Tipo<select value={documentType} onChange={(event) => setDocumentType(event.target.value)}>{INTERESADO_DOCUMENT_TYPES.map((tipo) => <option key={tipo.value} value={tipo.value}>{tipo.label}</option>)}</select></label>
          <label className="client-document-uploader__file">PDF<span className="document-file-picker soft-button"><Upload size={15} /><span>{documentFile?.name || "Seleccionar PDF"}</span><input accept="application/pdf,.pdf" type="file" onChange={(event) => { handleDocumentFile(event.target.files?.[0]); event.target.value = ""; }} /></span></label>
          <button className="primary-button" disabled={!documentFile || uploadDocumentMutation.isPending} type="button" onClick={() => uploadDocumentMutation.mutate()}><Upload size={16} />{uploadDocumentMutation.isPending ? "Subiendo" : "Subir PDF"}</button>
        </div>
        {documentError ? <p className="client-branding-feedback client-branding-feedback--danger">{documentError}</p> : null}
        <InteresadoDocuments documentos={item.documentos || []} busyId={deleteDocumentMutation.variables} onDelete={handleDeleteDocument} />
      </section>
    ) : null}
    <RegistroSummary tramites={item.tramites} roles={item.tramites.map((tramite) => tramite.rol || "")} />
    <section className="records-panel"><div className="records-panel__heading"><div><h3>Historial de tramites</h3><span>Participacion y estado actual</span></div></div><TramitesRegistroTable tramites={item.tramites} /></section>
    {dialog}
  </main>;
}

function InteresadoDocuments({ documentos, busyId, onDelete }: { documentos: DocumentoExpediente[]; busyId?: number; onDelete: (documento: DocumentoExpediente) => void }) {
  if (!documentos.length) {
    return <div className="client-documents-empty"><FileText size={18} /><span>No hay PDFs recurrentes registrados para este cliente habitual.</span></div>;
  }
  return <div className="client-documents-list">{documentos.map((documento) => <article className="client-document-row" key={documento.id}>
    <div className="client-document-row__icon"><FileText size={17} /></div>
    <div className="client-document-row__main"><strong>{documento.nombreOriginal || documento.nombre}</strong><span>{documento.tipo.replaceAll("_", " ")} · {documento.fechaSubida || "Sin fecha"}</span></div>
    {documento.id ? <div className="client-document-row__actions"><a className="icon-button" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer" title="Ver PDF"><ExternalLink size={15} /></a><a className="icon-button" href={`/documentos/descargar/${documento.id}`} title="Descargar PDF"><FileText size={15} /></a><button className="icon-button icon-button--danger" disabled={busyId === documento.id} type="button" title="Eliminar PDF" onClick={() => onDelete(documento)}><Trash2 size={15} /></button></div> : null}
  </article>)}</div>;
}
