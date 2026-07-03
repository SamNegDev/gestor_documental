import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, FilePlus2, Loader2, Save } from "lucide-react";
import { InteresadoAutocomplete } from "../../expedientes/components/InteresadoAutocomplete";
import { humanizeEnum } from "../../expedientes/utils/formatters";
import { cleanUpperText, uppercaseInput } from "../../../shared/utils/text";
import { AddressFields, type AddressValue } from "../../../shared/ui/AddressFields";
import { createSolicitud, getSolicitudDetail, getSolicitudListCatalogs, updateSolicitud } from "../services/listadosApi";
import type { InteresadoSearchResult } from "../../expedientes/types/expedienteDetail.types";
import type { SolicitudDetail, SolicitudUpsertInput } from "../types";
import "../../expedientes/styles/expedienteDetail.css";

const ROLES = ["COMPRADOR", "VENDEDOR", "COMPRAVENTA", "TITULAR"];
const BATECOM_ROLE_ORDER = ["VENDEDOR", "COMPRAVENTA", "COMPRADOR"];
const BATECOM_LABELS = ["Vendedor inicial", "Compraventa", "Comprador final"];

function emptyForm(): SolicitudUpsertInput {
  return {
    tipoTramiteId: 0,
    matricula: "",
    vehiculoMarca: "",
    vehiculoModelo: "",
    vehiculoBastidor: "",
    operacionPrecioVenta: "",
    observaciones: "",
    interesado1Rol: "",
    interesado1Nombre: "",
    interesado1Dni: "",
    interesado1Telefono: "",
    interesado1Direccion: "",
    interesado1TipoVia: "",
    interesado1NombreVia: "",
    interesado1NumeroVia: "",
    interesado1Bloque: "",
    interesado1Portal: "",
    interesado1Escalera: "",
    interesado1Piso: "",
    interesado1Puerta: "",
    interesado1CodigoPostal: "",
    interesado1Municipio: "",
    interesado1Provincia: "",
    interesado2Rol: "",
    interesado2Nombre: "",
    interesado2Dni: "",
    interesado2Telefono: "",
    interesado2Direccion: "",
    interesado2TipoVia: "",
    interesado2NombreVia: "",
    interesado2NumeroVia: "",
    interesado2Bloque: "",
    interesado2Portal: "",
    interesado2Escalera: "",
    interesado2Piso: "",
    interesado2Puerta: "",
    interesado2CodigoPostal: "",
    interesado2Municipio: "",
    interesado2Provincia: "",
    interesado3Rol: "",
    interesado3Nombre: "",
    interesado3Dni: "",
    interesado3Telefono: "",
    interesado3Direccion: "",
    interesado3TipoVia: "",
    interesado3NombreVia: "",
    interesado3NumeroVia: "",
    interesado3Bloque: "",
    interesado3Portal: "",
    interesado3Escalera: "",
    interesado3Piso: "",
    interesado3Puerta: "",
    interesado3CodigoPostal: "",
    interesado3Municipio: "",
    interesado3Provincia: "",
  };
}

function fromSolicitud(solicitud: SolicitudDetail): SolicitudUpsertInput {
  const interesados = isBatecomType(solicitud.tipoTramite)
    ? BATECOM_ROLE_ORDER.map((rol) => solicitud.interesados.find((interesado) => interesado.rol === rol))
    : solicitud.interesados;
  const interesado1 = interesados[0];
  const interesado2 = interesados[1];
  const interesado3 = interesados[2];
  return {
    tipoTramiteId: 0,
    matricula: uppercaseInput(solicitud.matricula || ""),
    vehiculoMarca: uppercaseInput(solicitud.vehiculo?.marca || ""),
    vehiculoModelo: uppercaseInput(solicitud.vehiculo?.modelo || ""),
    vehiculoBastidor: uppercaseInput(solicitud.vehiculo?.bastidor || ""),
    operacionPrecioVenta: uppercaseInput(solicitud.operacionPrecioVenta || ""),
    observaciones: uppercaseInput(solicitud.observaciones || ""),
    interesado1Rol: interesado1?.rol || "",
    interesado1Nombre: uppercaseInput(interesado1?.nombre || ""),
    interesado1Dni: uppercaseInput(interesado1?.dni || ""),
    interesado1Telefono: uppercaseInput(interesado1?.telefono || ""),
    interesado1Direccion: uppercaseInput(interesado1?.direccion || ""),
    interesado1TipoVia: uppercaseInput(interesado1?.tipoVia || ""),
    interesado1NombreVia: uppercaseInput(interesado1?.nombreVia || ""),
    interesado1NumeroVia: uppercaseInput(interesado1?.numeroVia || ""),
    interesado1Bloque: uppercaseInput(interesado1?.bloque || ""),
    interesado1Portal: uppercaseInput(interesado1?.portal || ""),
    interesado1Escalera: uppercaseInput(interesado1?.escalera || ""),
    interesado1Piso: uppercaseInput(interesado1?.piso || ""),
    interesado1Puerta: uppercaseInput(interesado1?.puerta || ""),
    interesado1CodigoPostal: uppercaseInput(interesado1?.codigoPostal || postalCodeFromAddress(interesado1?.direccion) || ""),
    interesado1Municipio: uppercaseInput(interesado1?.municipio || ""),
    interesado1Provincia: uppercaseInput(interesado1?.provincia || ""),
    interesado2Rol: interesado2?.rol || "",
    interesado2Nombre: uppercaseInput(interesado2?.nombre || ""),
    interesado2Dni: uppercaseInput(interesado2?.dni || ""),
    interesado2Telefono: uppercaseInput(interesado2?.telefono || ""),
    interesado2Direccion: uppercaseInput(interesado2?.direccion || ""),
    interesado2TipoVia: uppercaseInput(interesado2?.tipoVia || ""),
    interesado2NombreVia: uppercaseInput(interesado2?.nombreVia || ""),
    interesado2NumeroVia: uppercaseInput(interesado2?.numeroVia || ""),
    interesado2Bloque: uppercaseInput(interesado2?.bloque || ""),
    interesado2Portal: uppercaseInput(interesado2?.portal || ""),
    interesado2Escalera: uppercaseInput(interesado2?.escalera || ""),
    interesado2Piso: uppercaseInput(interesado2?.piso || ""),
    interesado2Puerta: uppercaseInput(interesado2?.puerta || ""),
    interesado2CodigoPostal: uppercaseInput(interesado2?.codigoPostal || postalCodeFromAddress(interesado2?.direccion) || ""),
    interesado2Municipio: uppercaseInput(interesado2?.municipio || ""),
    interesado2Provincia: uppercaseInput(interesado2?.provincia || ""),
    interesado3Rol: interesado3?.rol || "",
    interesado3Nombre: uppercaseInput(interesado3?.nombre || ""),
    interesado3Dni: uppercaseInput(interesado3?.dni || ""),
    interesado3Telefono: uppercaseInput(interesado3?.telefono || ""),
    interesado3Direccion: uppercaseInput(interesado3?.direccion || ""),
    interesado3TipoVia: uppercaseInput(interesado3?.tipoVia || ""),
    interesado3NombreVia: uppercaseInput(interesado3?.nombreVia || ""),
    interesado3NumeroVia: uppercaseInput(interesado3?.numeroVia || ""),
    interesado3Bloque: uppercaseInput(interesado3?.bloque || ""),
    interesado3Portal: uppercaseInput(interesado3?.portal || ""),
    interesado3Escalera: uppercaseInput(interesado3?.escalera || ""),
    interesado3Piso: uppercaseInput(interesado3?.piso || ""),
    interesado3Puerta: uppercaseInput(interesado3?.puerta || ""),
    interesado3CodigoPostal: uppercaseInput(interesado3?.codigoPostal || postalCodeFromAddress(interesado3?.direccion) || ""),
    interesado3Municipio: uppercaseInput(interesado3?.municipio || ""),
    interesado3Provincia: uppercaseInput(interesado3?.provincia || ""),
  };
}

function postalCodeFromAddress(address?: string | null) {
  return address?.match(/\b\d{5}\b/)?.[0] || "";
}

function cleanPayload(form: SolicitudUpsertInput): SolicitudUpsertInput {
  return {
    ...form,
    matricula: cleanUpperText(form.matricula) || "",
    vehiculoMarca: cleanUpperText(form.vehiculoMarca),
    vehiculoModelo: cleanUpperText(form.vehiculoModelo),
    vehiculoBastidor: cleanUpperText(form.vehiculoBastidor),
    operacionPrecioVenta: cleanUpperText(form.operacionPrecioVenta),
    observaciones: cleanUpperText(form.observaciones),
    interesado1Rol: cleanUpperText(form.interesado1Rol),
    interesado1Nombre: cleanUpperText(form.interesado1Nombre),
    interesado1Dni: cleanUpperText(form.interesado1Dni),
    interesado1Telefono: cleanUpperText(form.interesado1Telefono),
    interesado1Direccion: cleanUpperText(form.interesado1Direccion),
    interesado1TipoVia: cleanUpperText(form.interesado1TipoVia),
    interesado1NombreVia: cleanUpperText(form.interesado1NombreVia),
    interesado1NumeroVia: cleanUpperText(form.interesado1NumeroVia),
    interesado1Bloque: cleanUpperText(form.interesado1Bloque),
    interesado1Portal: cleanUpperText(form.interesado1Portal),
    interesado1Escalera: cleanUpperText(form.interesado1Escalera),
    interesado1Piso: cleanUpperText(form.interesado1Piso),
    interesado1Puerta: cleanUpperText(form.interesado1Puerta),
    interesado1CodigoPostal: cleanUpperText(form.interesado1CodigoPostal),
    interesado1Municipio: cleanUpperText(form.interesado1Municipio),
    interesado1Provincia: cleanUpperText(form.interesado1Provincia),
    interesado2Rol: cleanUpperText(form.interesado2Rol),
    interesado2Nombre: cleanUpperText(form.interesado2Nombre),
    interesado2Dni: cleanUpperText(form.interesado2Dni),
    interesado2Telefono: cleanUpperText(form.interesado2Telefono),
    interesado2Direccion: cleanUpperText(form.interesado2Direccion),
    interesado2TipoVia: cleanUpperText(form.interesado2TipoVia),
    interesado2NombreVia: cleanUpperText(form.interesado2NombreVia),
    interesado2NumeroVia: cleanUpperText(form.interesado2NumeroVia),
    interesado2Bloque: cleanUpperText(form.interesado2Bloque),
    interesado2Portal: cleanUpperText(form.interesado2Portal),
    interesado2Escalera: cleanUpperText(form.interesado2Escalera),
    interesado2Piso: cleanUpperText(form.interesado2Piso),
    interesado2Puerta: cleanUpperText(form.interesado2Puerta),
    interesado2CodigoPostal: cleanUpperText(form.interesado2CodigoPostal),
    interesado2Municipio: cleanUpperText(form.interesado2Municipio),
    interesado2Provincia: cleanUpperText(form.interesado2Provincia),
    interesado3Rol: cleanUpperText(form.interesado3Rol),
    interesado3Nombre: cleanUpperText(form.interesado3Nombre),
    interesado3Dni: cleanUpperText(form.interesado3Dni),
    interesado3Telefono: cleanUpperText(form.interesado3Telefono),
    interesado3Direccion: cleanUpperText(form.interesado3Direccion),
    interesado3TipoVia: cleanUpperText(form.interesado3TipoVia),
    interesado3NombreVia: cleanUpperText(form.interesado3NombreVia),
    interesado3NumeroVia: cleanUpperText(form.interesado3NumeroVia),
    interesado3Bloque: cleanUpperText(form.interesado3Bloque),
    interesado3Portal: cleanUpperText(form.interesado3Portal),
    interesado3Escalera: cleanUpperText(form.interesado3Escalera),
    interesado3Piso: cleanUpperText(form.interesado3Piso),
    interesado3Puerta: cleanUpperText(form.interesado3Puerta),
    interesado3CodigoPostal: cleanUpperText(form.interesado3CodigoPostal),
    interesado3Municipio: cleanUpperText(form.interesado3Municipio),
    interesado3Provincia: cleanUpperText(form.interesado3Provincia),
  };
}

function isBatecomType(typeName?: string | null) {
  return typeName === "BATECOM";
}

function applyBatecomRoles(form: SolicitudUpsertInput): SolicitudUpsertInput {
  return BATECOM_ROLE_ORDER.reduce(
    (current, rol, index) => ({
      ...current,
      [`interesado${index + 1}Rol`]: rol,
    }),
    form,
  );
}

function clearThirdInteresado(form: SolicitudUpsertInput): SolicitudUpsertInput {
  return {
    ...form,
    interesado3Rol: "",
    interesado3Nombre: "",
    interesado3Dni: "",
    interesado3Telefono: "",
    interesado3Direccion: "",
    interesado3TipoVia: "",
    interesado3NombreVia: "",
    interesado3NumeroVia: "",
    interesado3Bloque: "",
    interesado3Portal: "",
    interesado3Escalera: "",
    interesado3Piso: "",
    interesado3Puerta: "",
    interesado3CodigoPostal: "",
    interesado3Municipio: "",
    interesado3Provincia: "",
  };
}

function interestedIndexes(batecom: boolean) {
  return batecom ? [1, 2, 3] : [1, 2];
}

function interestedBlockLabel(index: number, batecom: boolean) {
  return batecom ? BATECOM_LABELS[index - 1] || `Interesado ${index}` : `Interesado ${index}`;
}

export function SolicitudFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const isEdit = Boolean(id);
  const [form, setForm] = useState<SolicitudUpsertInput>(emptyForm);
  const focusField = searchParams.get("focus") || searchParams.get("missing") || "";

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
      const formToSave = isBatecom ? applyBatecomRoles(form) : clearThirdInteresado(form);
      if (isEdit && id) {
        await updateSolicitud(id, cleanPayload(formToSave));
        return { id: Number(id) };
      }
      const creada = await createSolicitud(cleanPayload(formToSave));
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
  const isBatecom = isBatecomType(selectedType?.nombre);
  const fieldClassName = (field: string, baseClassName?: string) =>
    [baseClassName, focusField === field ? "edit-field--missing" : null].filter(Boolean).join(" ") || undefined;

  useEffect(() => {
    if (!focusField || loading) return;
    const timeoutId = window.setTimeout(() => {
      const target = document.querySelector<HTMLElement>(`[data-field="${focusField}"]`);
      if (!target) return;
      target.scrollIntoView({ behavior: "smooth", block: "center" });
      const input = target.matches("input, select, textarea")
        ? target
        : target.querySelector<HTMLElement>("input, select, textarea");
      input?.focus({ preventScroll: true });
    }, 120);
    return () => window.clearTimeout(timeoutId);
  }, [focusField, loading]);

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
            <label className={fieldClassName("tipoTramiteId")} data-field="tipoTramiteId">
              Tipo de tramite
              <select
                value={form.tipoTramiteId || ""}
                onChange={(event) => {
                  const tipoTramiteId = Number(event.target.value);
                  const nextType = catalogsQuery.data?.tiposTramite.find((tipo) => tipo.id === tipoTramiteId);
                  const nextForm = { ...form, tipoTramiteId };
                  setForm(isBatecomType(nextType?.nombre) ? applyBatecomRoles(nextForm) : clearThirdInteresado(nextForm));
                }}
                required
              >
                <option value="">Selecciona un tipo</option>
                {catalogsQuery.data?.tiposTramite.map((tipo) => (
                  <option key={tipo.id} value={tipo.id}>
                    {tipo.descripcion || humanizeEnum(tipo.nombre)}
                  </option>
                ))}
              </select>
            </label>
            <label className={fieldClassName("matricula")} data-field="matricula">
              Matricula
              <input value={form.matricula} onChange={(event) => setForm({ ...form, matricula: uppercaseInput(event.target.value) })} required />
            </label>
            <label className={fieldClassName("vehiculoMarca")} data-field="vehiculoMarca">
              Marca
              <input value={form.vehiculoMarca || ""} onChange={(event) => setForm({ ...form, vehiculoMarca: uppercaseInput(event.target.value) })} />
            </label>
            <label className={fieldClassName("vehiculoModelo")} data-field="vehiculoModelo">
              Modelo
              <input value={form.vehiculoModelo || ""} onChange={(event) => setForm({ ...form, vehiculoModelo: uppercaseInput(event.target.value) })} />
            </label>
            <label className={fieldClassName("vehiculoBastidor")} data-field="vehiculoBastidor">
              Bastidor
              <input value={form.vehiculoBastidor || ""} onChange={(event) => setForm({ ...form, vehiculoBastidor: uppercaseInput(event.target.value) })} />
            </label>
            <label className={fieldClassName("operacionPrecioVenta")} data-field="operacionPrecioVenta">
              Precio de venta
              <input inputMode="decimal" value={form.operacionPrecioVenta || ""} onChange={(event) => setForm({ ...form, operacionPrecioVenta: uppercaseInput(event.target.value) })} />
            </label>
            <label className={fieldClassName("observaciones", "edit-form-grid__wide")} data-field="observaciones">
              Observaciones
              <textarea rows={4} value={form.observaciones || ""} onChange={(event) => setForm({ ...form, observaciones: uppercaseInput(event.target.value) })} />
            </label>
          </div>
        </section>

        {interestedIndexes(isBatecom).map((index) => (
          <section className="panel" key={index}>
            <div className="panel-heading">
              <div>
                <p className="eyebrow">{interestedBlockLabel(index, isBatecom)}</p>
                <h2>Datos opcionales</h2>
              </div>
            </div>
            <InteresadoFields
              focusField={focusField}
              form={form}
              index={index}
              onChange={setForm}
              lockedRole={isBatecom ? BATECOM_ROLE_ORDER[index - 1] : undefined}
            />
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
  lockedRole,
  focusField,
}: {
  form: SolicitudUpsertInput;
  index: number;
  onChange: (form: SolicitudUpsertInput) => void;
  lockedRole?: string;
  focusField?: string;
}) {
  const prefix = `interesado${index}` as "interesado1" | "interesado2" | "interesado3";
  const field = (name: string) => `${prefix}${name}` as keyof SolicitudUpsertInput;
  const fieldClassName = (fieldName: string, baseClassName?: string) =>
    [baseClassName, focusField === fieldName ? "edit-field--missing" : null].filter(Boolean).join(" ") || undefined;
  const addressValue: AddressValue = {
    direccion: form[field("Direccion")] as string,
    tipoVia: form[field("TipoVia")] as string,
    nombreVia: form[field("NombreVia")] as string,
    numeroVia: form[field("NumeroVia")] as string,
    bloque: form[field("Bloque")] as string,
    portal: form[field("Portal")] as string,
    escalera: form[field("Escalera")] as string,
    piso: form[field("Piso")] as string,
    puerta: form[field("Puerta")] as string,
    codigoPostal: form[field("CodigoPostal")] as string,
    municipio: form[field("Municipio")] as string,
    provincia: form[field("Provincia")] as string,
  };
  const updateAddress = (value: AddressValue) => {
    onChange({
      ...form,
      [field("Direccion")]: "",
      [field("TipoVia")]: value.tipoVia,
      [field("NombreVia")]: value.nombreVia,
      [field("NumeroVia")]: value.numeroVia,
      [field("Bloque")]: value.bloque,
      [field("Portal")]: value.portal,
      [field("Escalera")]: value.escalera,
      [field("Piso")]: value.piso,
      [field("Puerta")]: value.puerta,
      [field("CodigoPostal")]: value.codigoPostal,
      [field("Municipio")]: value.municipio,
      [field("Provincia")]: value.provincia,
    });
  };
  const applyInteresado = (interesado: InteresadoSearchResult) => {
    onChange({
      ...form,
      [field("Nombre")]: uppercaseInput(interesado.nombre || ""),
      [field("Dni")]: uppercaseInput(interesado.dni || ""),
      [field("Telefono")]: uppercaseInput(interesado.telefono || ""),
      [field("Direccion")]: uppercaseInput(interesado.direccion || ""),
      [field("TipoVia")]: uppercaseInput(interesado.tipoVia || ""),
      [field("NombreVia")]: uppercaseInput(interesado.nombreVia || ""),
      [field("NumeroVia")]: uppercaseInput(interesado.numeroVia || ""),
      [field("Bloque")]: uppercaseInput(interesado.bloque || ""),
      [field("Portal")]: uppercaseInput(interesado.portal || ""),
      [field("Escalera")]: uppercaseInput(interesado.escalera || ""),
      [field("Piso")]: uppercaseInput(interesado.piso || ""),
      [field("Puerta")]: uppercaseInput(interesado.puerta || ""),
      [field("CodigoPostal")]: uppercaseInput(interesado.codigoPostal || ""),
      [field("Municipio")]: uppercaseInput(interesado.municipio || ""),
      [field("Provincia")]: uppercaseInput(interesado.provincia || ""),
    });
  };

  return (
    <div className="edit-form-grid">
      <InteresadoAutocomplete
        className={fieldClassName(field("Dni") as string)}
        dataField={field("Dni") as string}
        label="DNI/CIF"
        value={(form[field("Dni")] as string) || ""}
        placeholder="Buscar por DNI/CIF"
        onChange={(value) => onChange({ ...form, [field("Dni")]: value })}
        onSelect={applyInteresado}
      />
      {form[field("Dni")] ? (
        <label className={fieldClassName(field("Nombre") as string)} data-field={field("Nombre") as string}>
          Nombre completo/Razon social
          <input value={(form[field("Nombre")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Nombre")]: uppercaseInput(event.target.value) })} />
        </label>
      ) : (
        <InteresadoAutocomplete
          className={fieldClassName(field("Nombre") as string)}
          dataField={field("Nombre") as string}
          label="Nombre completo/Razon social"
          value={(form[field("Nombre")] as string) || ""}
          placeholder="Buscar por nombre o razon social"
          onChange={(value) => onChange({ ...form, [field("Nombre")]: value })}
          onSelect={applyInteresado}
        />
      )}
      <label className={fieldClassName(field("Rol") as string)} data-field={field("Rol") as string}>
        Rol
        <select
          value={lockedRole || (form[field("Rol")] as string) || ""}
          onChange={(event) => onChange({ ...form, [field("Rol")]: event.target.value })}
          disabled={Boolean(lockedRole)}
        >
          <option value="">Sin rol</option>
          {ROLES.map((rol) => (
            <option key={rol} value={rol}>
              {humanizeEnum(rol)}
            </option>
          ))}
        </select>
      </label>
      <label className={fieldClassName(field("Telefono") as string)} data-field={field("Telefono") as string}>
        Telefono
        <input value={(form[field("Telefono")] as string) || ""} onChange={(event) => onChange({ ...form, [field("Telefono")]: uppercaseInput(event.target.value) })} />
      </label>
      <label className={fieldClassName(field("Direccion") as string, "edit-form-grid__wide")} data-field={field("Direccion") as string}>
        Direccion libre
        <textarea
          rows={1}
          value={(form[field("Direccion")] as string) || ""}
          onChange={(event) => onChange({ ...form, [field("Direccion")]: uppercaseInput(event.target.value) })}
        />
      </label>
      <AddressFields
        fieldNamePrefix={prefix}
        highlightField={focusField}
        idPrefix={`solicitud-${prefix}`}
        value={addressValue}
        onChange={updateAddress}
        wideClassName="edit-form-grid__wide"
      />
    </div>
  );
}
