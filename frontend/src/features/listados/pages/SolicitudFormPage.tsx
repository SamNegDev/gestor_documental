import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, FilePlus2, Loader2, Plus, Save, Trash2, Upload } from "lucide-react";
import { uploadSolicitudDocument } from "../../expedientes/services/documentosApi";
import { humanizeEnum } from "../../expedientes/utils/formatters";
import { createSolicitud, getSolicitudDetail, getSolicitudListCatalogs, updateSolicitud } from "../services/listadosApi";
import type { SolicitudDetail, SolicitudUpsertInput } from "../types";
import "../../expedientes/styles/expedienteDetail.css";

const ROLES = ["COMPRADOR", "VENDEDOR", "TITULAR"];
const DOCUMENT_TYPES = [
  "DNI",
  "CIF",
  "CONTRATO_COMPRAVENTA",
  "PERMISO_CIRCULACION",
  "FICHA_TECNICA",
  "MANDATO",
  "FACTURA",
  "EXPEDIENTE_COMPLETO",
  "OTROS",
  "CAMBIO_TITULARIDAD",
  "AUTORIZACION_SERAFIN",
  "HUELLA_TRAMITE",
  "COMPROBANTE_DGT",
  "MODELO_620",
  "DOCUMENTO_INCIDENCIA",
];

type DocumentDraft = {
  id: string;
  tipoDocumento: string;
  archivo: File | null;
};

function emptyForm(): SolicitudUpsertInput {
  return {
    tipoTramiteId: 0,
    matricula: "",
    observaciones: "",
    interesado1Rol: "",
    interesado1Nombre: "",
    interesado1Apellidos: "",
    interesado1Dni: "",
    interesado1Telefono: "",
    interesado1Direccion: "",
    interesado2Rol: "",
    interesado2Nombre: "",
    interesado2Apellidos: "",
    interesado2Dni: "",
    interesado2Telefono: "",
    interesado2Direccion: "",
  };
}

function fromSolicitud(solicitud: SolicitudDetail): SolicitudUpsertInput {
  const interesado1 = solicitud.interesados[0];
  const interesado2 = solicitud.interesados[1];
  return {
    tipoTramiteId: 0,
    matricula: solicitud.matricula || "",
    observaciones: solicitud.observaciones || "",
    interesado1Rol: interesado1?.rol || "",
    interesado1Nombre: interesado1?.nombre || "",
    interesado1Apellidos: interesado1?.apellidos || "",
    interesado1Dni: interesado1?.dni || "",
    interesado1Telefono: interesado1?.telefono || "",
    interesado1Direccion: interesado1?.direccion || "",
    interesado2Rol: interesado2?.rol || "",
    interesado2Nombre: interesado2?.nombre || "",
    interesado2Apellidos: interesado2?.apellidos || "",
    interesado2Dni: interesado2?.dni || "",
    interesado2Telefono: interesado2?.telefono || "",
    interesado2Direccion: interesado2?.direccion || "",
  };
}

function cleanPayload(form: SolicitudUpsertInput): SolicitudUpsertInput {
  const clean = (value?: string | null) => {
    const trimmed = value?.trim();
    return trimmed ? trimmed : null;
  };
  return {
    ...form,
    matricula: form.matricula.trim().toUpperCase(),
    observaciones: clean(form.observaciones),
    interesado1Rol: clean(form.interesado1Rol),
    interesado1Nombre: clean(form.interesado1Nombre),
    interesado1Apellidos: clean(form.interesado1Apellidos),
    interesado1Dni: clean(form.interesado1Dni),
    interesado1Telefono: clean(form.interesado1Telefono),
    interesado1Direccion: clean(form.interesado1Direccion),
    interesado2Rol: clean(form.interesado2Rol),
    interesado2Nombre: clean(form.interesado2Nombre),
    interesado2Apellidos: clean(form.interesado2Apellidos),
    interesado2Dni: clean(form.interesado2Dni),
    interesado2Telefono: clean(form.interesado2Telefono),
    interesado2Direccion: clean(form.interesado2Direccion),
  };
}

export function SolicitudFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<SolicitudUpsertInput>(emptyForm);
  const [documents, setDocuments] = useState<DocumentDraft[]>([{ id: crypto.randomUUID(), tipoDocumento: "OTROS", archivo: null }]);

  const catalogsQuery = useQuery({
    queryKey: ["solicitudes", "catalogos-listado"],
    queryFn: getSolicitudListCatalogs,
  });

  const detailQuery = useQuery({
    queryKey: ["solicitudes", "detalle", id],
    queryFn: () => getSolicitudDetail(id!),
    enabled: isEdit,
  });

  useEffect(() => {
    if (!catalogsQuery.data || form.tipoTramiteId) return;
    setForm((current) => ({ ...current, tipoTramiteId: catalogsQuery.data.tiposTramite[0]?.id || 0 }));
  }, [catalogsQuery.data, form.tipoTramiteId]);

  useEffect(() => {
    if (!detailQuery.data || !catalogsQuery.data) return;
    const tipo = catalogsQuery.data.tiposTramite.find((item) => item.nombre === detailQuery.data?.tipoTramite);
    setForm({ ...fromSolicitud(detailQuery.data), tipoTramiteId: tipo?.id || catalogsQuery.data.tiposTramite[0]?.id || 0 });
  }, [catalogsQuery.data, detailQuery.data]);

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!form.tipoTramiteId) throw new Error("Selecciona un tipo de tramite.");
      if (!form.matricula.trim()) throw new Error("La matricula es obligatoria.");
      if (isEdit && id) {
        await updateSolicitud(id, cleanPayload(form));
        return { id: Number(id) };
      }
      const creada = await createSolicitud(cleanPayload(form));
      const validDocuments = documents.filter((documento) => documento.archivo);
      for (const documento of validDocuments) {
        await uploadSolicitudDocument(creada.id, documento.tipoDocumento || "OTROS", documento.archivo!);
      }
      return creada;
    },
    onSuccess: (solicitud) => navigate(`/solicitudes/${solicitud.id}`),
    onError: (cause) => alert(cause instanceof Error ? cause.message : "No se pudo guardar la solicitud."),
  });

  const loading = catalogsQuery.isLoading || detailQuery.isLoading;
  const selectedType = useMemo(
    () => catalogsQuery.data?.tiposTramite.find((tipo) => tipo.id === form.tipoTramiteId),
    [catalogsQuery.data, form.tipoTramiteId],
  );

  if (loading) {
    return (
      <div className="records-empty">
        <Loader2 size={22} />
        Preparando formulario...
      </div>
    );
  }

  return (
    <main className="request-form-page">
      <section className="request-hero">
        <div>
          <p className="eyebrow">{isEdit ? "Editar solicitud" : "Nueva solicitud"}</p>
          <div className="case-title-row">
            <div className="row-icon">
              <FilePlus2 size={18} />
            </div>
            <div>
              <h2>{isEdit ? `Solicitud #${id}` : "Abrir una solicitud"}</h2>
              <p>{selectedType?.descripcion || selectedType?.nombre || "Selecciona el tramite que quieres iniciar."}</p>
            </div>
          </div>
        </div>
        <Link className="soft-button soft-button--compact" to={isEdit && id ? `/solicitudes/${id}` : "/solicitudes"}>
          <ArrowLeft size={16} />
          Volver
        </Link>
      </section>

      <form
        className="request-form-grid"
        onSubmit={(event) => {
          event.preventDefault();
          submitMutation.mutate();
        }}
      >
        <section className="panel request-form-main">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Datos base</p>
              <h2>Tramite y vehiculo</h2>
            </div>
          </div>
          <div className="edit-form-grid">
            <label>
              Tipo de tramite
              <select value={form.tipoTramiteId || ""} onChange={(event) => setForm({ ...form, tipoTramiteId: Number(event.target.value) })} required>
                <option value="">Selecciona un tipo</option>
                {catalogsQuery.data?.tiposTramite.map((tipo) => (
                  <option key={tipo.id} value={tipo.id}>
                    {tipo.descripcion || humanizeEnum(tipo.nombre)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Matricula
              <input value={form.matricula} onChange={(event) => setForm({ ...form, matricula: event.target.value })} required />
            </label>
            <label className="edit-form-grid__wide">
              Observaciones
              <textarea rows={4} value={form.observaciones || ""} onChange={(event) => setForm({ ...form, observaciones: event.target.value })} />
            </label>
          </div>
        </section>

        {[1, 2].map((index) => (
          <section className="panel" key={index}>
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Interesado {index}</p>
                <h2>Datos opcionales</h2>
              </div>
            </div>
            <InteresadoFields form={form} index={index} onChange={setForm} />
          </section>
        ))}

        {!isEdit ? (
          <section className="panel request-form-documents">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Documentacion</p>
                <h2>Archivos iniciales</h2>
              </div>
              <button
                className="soft-button soft-button--compact"
                type="button"
                onClick={() => setDocuments((current) => [...current, { id: crypto.randomUUID(), tipoDocumento: "OTROS", archivo: null }])}
              >
                <Plus size={16} />
                Anadir
              </button>
            </div>
            <div className="request-document-drafts">
              {documents.map((documento) => (
                <article className="request-document-draft" key={documento.id}>
                  <select
                    value={documento.tipoDocumento}
                    onChange={(event) =>
                      setDocuments((current) => current.map((item) => (item.id === documento.id ? { ...item, tipoDocumento: event.target.value } : item)))
                    }
                  >
                    {DOCUMENT_TYPES.map((type) => (
                      <option key={type} value={type}>
                        {humanizeEnum(type)}
                      </option>
                    ))}
                  </select>
                  <label className="document-file-picker">
                    <Upload size={16} />
                    <span>{documento.archivo?.name || "Seleccionar archivo"}</span>
                    <input
                      hidden
                      type="file"
                      accept=".pdf,.jpg,.jpeg,.png"
                      onChange={(event) => {
                        const file = event.currentTarget.files?.[0] || null;
                        setDocuments((current) => current.map((item) => (item.id === documento.id ? { ...item, archivo: file } : item)));
                      }}
                    />
                  </label>
                  <button className="icon-button icon-button--danger" type="button" onClick={() => setDocuments((current) => current.filter((item) => item.id !== documento.id))}>
                    <Trash2 size={16} />
                  </button>
                </article>
              ))}
            </div>
          </section>
        ) : null}

        <div className="request-form-actions">
          <Link className="soft-button" to={isEdit && id ? `/solicitudes/${id}` : "/solicitudes"}>
            Cancelar
          </Link>
          <button className="primary-button" disabled={submitMutation.isPending} type="submit">
            <Save size={16} />
            {submitMutation.isPending ? "Guardando" : "Guardar solicitud"}
          </button>
        </div>
      </form>
    </main>
  );
}

function InteresadoFields({
  form,
  index,
  onChange,
}: {
  form: SolicitudUpsertInput;
  index: number;
  onChange: (form: SolicitudUpsertInput) => void;
}) {
  const prefix = `interesado${index}` as "interesado1" | "interesado2";
  const field = (name: string) => `${prefix}${name}` as keyof SolicitudUpsertInput;
  return (
    <div className="edit-form-grid">
      <label>
        Rol
        <select value={(form[field("Rol")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Rol")]: event.target.value })}>
          <option value="">Sin rol</option>
          {ROLES.map((rol) => (
            <option key={rol} value={rol}>
              {humanizeEnum(rol)}
            </option>
          ))}
        </select>
      </label>
      <label>
        Nombre
        <input value={(form[field("Nombre")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Nombre")]: event.target.value })} />
      </label>
      <label>
        Apellidos
        <input value={(form[field("Apellidos")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Apellidos")]: event.target.value })} />
      </label>
      <label>
        DNI/NIF
        <input value={(form[field("Dni")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Dni")]: event.target.value })} />
      </label>
      <label>
        Telefono
        <input value={(form[field("Telefono")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Telefono")]: event.target.value })} />
      </label>
      <label className="edit-form-grid__wide">
        Direccion
        <input value={(form[field("Direccion")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Direccion")]: event.target.value })} />
      </label>
    </div>
  );
}
