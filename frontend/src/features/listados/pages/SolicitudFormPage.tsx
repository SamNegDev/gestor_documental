import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, FilePlus2, Loader2, Save } from "lucide-react";
import { InteresadoAutocomplete } from "../../expedientes/components/InteresadoAutocomplete";
import { humanizeEnum } from "../../expedientes/utils/formatters";
import { cleanUpperText, uppercaseInput } from "../../../shared/utils/text";
import { createSolicitud, getSolicitudDetail, getSolicitudListCatalogs, updateSolicitud } from "../services/listadosApi";
import type { InteresadoSearchResult } from "../../expedientes/types/expedienteDetail.types";
import type { SolicitudDetail, SolicitudUpsertInput } from "../types";
import "../../expedientes/styles/expedienteDetail.css";

const ROLES = ["COMPRADOR", "VENDEDOR", "TITULAR"];
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
    matricula: uppercaseInput(solicitud.matricula || ""),
    observaciones: uppercaseInput(solicitud.observaciones || ""),
    interesado1Rol: interesado1?.rol || "",
    interesado1Nombre: uppercaseInput(interesado1?.nombre || ""),
    interesado1Apellidos: uppercaseInput(interesado1?.apellidos || ""),
    interesado1Dni: uppercaseInput(interesado1?.dni || ""),
    interesado1Telefono: uppercaseInput(interesado1?.telefono || ""),
    interesado1Direccion: uppercaseInput(interesado1?.direccion || ""),
    interesado2Rol: interesado2?.rol || "",
    interesado2Nombre: uppercaseInput(interesado2?.nombre || ""),
    interesado2Apellidos: uppercaseInput(interesado2?.apellidos || ""),
    interesado2Dni: uppercaseInput(interesado2?.dni || ""),
    interesado2Telefono: uppercaseInput(interesado2?.telefono || ""),
    interesado2Direccion: uppercaseInput(interesado2?.direccion || ""),
  };
}

function cleanPayload(form: SolicitudUpsertInput): SolicitudUpsertInput {
  return {
    ...form,
    matricula: cleanUpperText(form.matricula) || "",
    observaciones: cleanUpperText(form.observaciones),
    interesado1Rol: cleanUpperText(form.interesado1Rol),
    interesado1Nombre: cleanUpperText(form.interesado1Nombre),
    interesado1Apellidos: cleanUpperText(form.interesado1Apellidos),
    interesado1Dni: cleanUpperText(form.interesado1Dni),
    interesado1Telefono: cleanUpperText(form.interesado1Telefono),
    interesado1Direccion: cleanUpperText(form.interesado1Direccion),
    interesado2Rol: cleanUpperText(form.interesado2Rol),
    interesado2Nombre: cleanUpperText(form.interesado2Nombre),
    interesado2Apellidos: cleanUpperText(form.interesado2Apellidos),
    interesado2Dni: cleanUpperText(form.interesado2Dni),
    interesado2Telefono: cleanUpperText(form.interesado2Telefono),
    interesado2Direccion: cleanUpperText(form.interesado2Direccion),
  };
}

export function SolicitudFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<SolicitudUpsertInput>(emptyForm);

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
              <input value={form.matricula} onChange={(event) => setForm({ ...form, matricula: uppercaseInput(event.target.value) })} required />
            </label>
            <label className="edit-form-grid__wide">
              Observaciones
              <textarea rows={4} value={form.observaciones || ""} onChange={(event) => setForm({ ...form, observaciones: uppercaseInput(event.target.value) })} />
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

        <div className="request-form-actions">
          <Link className="soft-button" to={isEdit && id ? `/solicitudes/${id}` : "/solicitudes"}>
            Cancelar
          </Link>
          <button className="primary-button" disabled={submitMutation.isPending} type="submit">
            <Save size={16} />
            {submitMutation.isPending ? "Guardando" : isEdit ? "Guardar cambios" : "Crear solicitud"}
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
  const applyInteresado = (interesado: InteresadoSearchResult) => {
    onChange({
      ...form,
      [field("Nombre")]: uppercaseInput(interesado.nombre || ""),
      [field("Dni")]: uppercaseInput(interesado.dni || ""),
      [field("Telefono")]: uppercaseInput(interesado.telefono || ""),
      [field("Direccion")]: uppercaseInput(interesado.direccion || ""),
    });
  };

  return (
    <div className="edit-form-grid">
      <InteresadoAutocomplete
        label="DNI/CIF"
        value={(form[field("Dni")] as string) || ""}
        placeholder="Buscar por DNI/CIF"
        onChange={(value) => onChange({ ...form, [field("Dni")]: value })}
        onSelect={applyInteresado}
      />
      {form[field("Dni")] ? (
        <label>
          Nombre/Razon social
          <input value={(form[field("Nombre")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Nombre")]: uppercaseInput(event.target.value) })} />
        </label>
      ) : (
        <InteresadoAutocomplete
          label="Nombre/Razon social"
          value={(form[field("Nombre")] as string) || ""}
          placeholder="Buscar por nombre o razon social"
          onChange={(value) => onChange({ ...form, [field("Nombre")]: value })}
          onSelect={applyInteresado}
        />
      )}
      <label>
        Apellidos
        <input value={(form[field("Apellidos")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Apellidos")]: uppercaseInput(event.target.value) })} />
      </label>
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
        Telefono
        <input value={(form[field("Telefono")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Telefono")]: uppercaseInput(event.target.value) })} />
      </label>
      <label className="edit-form-grid__wide">
        Direccion
        <input value={(form[field("Direccion")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Direccion")]: uppercaseInput(event.target.value) })} />
      </label>
    </div>
  );
}
