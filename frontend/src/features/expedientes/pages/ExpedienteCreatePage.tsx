import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AlertCircle, ArrowLeft, FilePlus2, Loader2 } from "lucide-react";
import { createExpediente, getExpedienteEditCatalogs } from "../services/expedienteDetailApi";
import type { ExpedienteEditCatalogs, ExpedienteEditInput, InteresadoSearchResult } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";
import { ApiError } from "../../../shared/api/http";
import { cleanUpperText, uppercaseInput } from "../../../shared/utils/text";
import { AddressFields, type AddressValue } from "../../../shared/ui/AddressFields";
import { InteresadoAutocomplete } from "../components/InteresadoAutocomplete";
import "../styles/expedienteDetail.css";

const ROLES = ["COMPRADOR", "VENDEDOR", "COMPRAVENTA", "TITULAR"];
const BATECOM_ROLE_ORDER = ["VENDEDOR", "COMPRAVENTA", "COMPRADOR"];
const BATECOM_LABELS = ["Vendedor inicial", "Compraventa", "Comprador final"];

type InteresadoForm = ExpedienteEditInput["interesados"][number];

function emptyInteresado(): InteresadoForm {
  return { nombre: "", dni: "", telefono: "", direccion: "", tipoVia: "", nombreVia: "", numeroVia: "", bloque: "", portal: "", escalera: "", piso: "", puerta: "", codigoPostal: "", municipio: "", provincia: "", rol: "" };
}

function ensureBatecomInteresados(interesados: InteresadoForm[]) {
  return BATECOM_ROLE_ORDER.map((rol, index) => ({
    ...(interesados[index] ?? emptyInteresado()),
    rol,
  }));
}

function hasInteresadoData(interesado: InteresadoForm) {
  return Boolean(
    interesado.nombre?.trim()
      || interesado.dni?.trim()
      || interesado.telefono?.trim()
      || interesado.direccion?.trim()
      || interesado.tipoVia?.trim()
      || interesado.nombreVia?.trim()
      || interesado.numeroVia?.trim()
      || interesado.bloque?.trim()
      || interesado.portal?.trim()
      || interesado.escalera?.trim()
      || interesado.piso?.trim()
      || interesado.puerta?.trim()
      || interesado.codigoPostal?.trim()
      || interesado.municipio?.trim()
      || interesado.provincia?.trim(),
  );
}

function prepareInteresadosForSave(form: ExpedienteEditInput, batecom: boolean) {
  const interesados = batecom ? ensureBatecomInteresados(form.interesados) : form.interesados;
  return interesados.filter(hasInteresadoData);
}

function buildBatecomPayload(form: ExpedienteEditInput): ExpedienteEditInput {
  return {
    ...form,
    interesados: prepareInteresadosForSave(form, true),
  };
}

function cleanText(value?: string | null) {
  return cleanUpperText(value);
}

function buildPayload(form: ExpedienteEditInput): ExpedienteEditInput {
  return {
    ...form,
    matricula: cleanText(form.matricula),
    observaciones: cleanText(form.observaciones),
    interesados: form.interesados.map((interesado) => ({
      nombre: cleanText(interesado.nombre),
      dni: cleanText(interesado.dni),
      telefono: cleanText(interesado.telefono),
      direccion: cleanText(interesado.direccion),
      tipoVia: cleanText(interesado.tipoVia),
      nombreVia: cleanText(interesado.nombreVia),
      numeroVia: cleanText(interesado.numeroVia),
      bloque: cleanText(interesado.bloque),
      portal: cleanText(interesado.portal),
      escalera: cleanText(interesado.escalera),
      piso: cleanText(interesado.piso),
      puerta: cleanText(interesado.puerta),
      codigoPostal: cleanText(interesado.codigoPostal),
      municipio: cleanText(interesado.municipio),
      provincia: cleanText(interesado.provincia),
      rol: cleanText(interesado.rol),
    })),
  };
}

function emptyForm(catalogs: ExpedienteEditCatalogs): ExpedienteEditInput {
  return {
    clienteId: catalogs.clientes[0]?.id || 0,
    tipoTramiteId: catalogs.tiposTramite[0]?.id || 0,
    matricula: "",
    observaciones: "",
    interesados: [emptyInteresado(), emptyInteresado()],
  };
}

export function ExpedienteCreatePage() {
  const navigate = useNavigate();
  const [catalogs, setCatalogs] = useState<ExpedienteEditCatalogs | null>(null);
  const [form, setForm] = useState<ExpedienteEditInput | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const catalogos = await getExpedienteEditCatalogs();
      setCatalogs(catalogos);
      setForm(emptyForm(catalogos));
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        setError("Sesión caducada. Inicia sesión para crear expedientes.");
      } else if (cause instanceof ApiError && cause.status === 403) {
        setError("No tienes permiso para crear expedientes.");
      } else {
        setError("No se pudo preparar el formulario.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const isBatecom = (tipoTramiteId: number) =>
    catalogs?.tiposTramite.find((tipo) => tipo.id === tipoTramiteId)?.nombre === "BATECOM";

  const updateField = (field: keyof ExpedienteEditInput, value: string | number) => {
    setForm((current) => {
      if (!current) return current;
      if (field === "tipoTramiteId" && typeof value === "number" && isBatecom(value) && current.interesados.length < 3) {
        return { ...current, [field]: value, interesados: ensureBatecomInteresados(current.interesados) };
      }
      if (field === "tipoTramiteId" && typeof value === "number" && !isBatecom(value) && current.interesados.length > 2) {
        return { ...current, [field]: value, interesados: current.interesados.slice(0, 2) };
      }
      return { ...current, [field]: typeof value === "string" ? uppercaseInput(value) : value };
    });
  };

  const updateInteresado = (index: number, field: keyof InteresadoForm, value: string) => {
    setForm((current) => {
      if (!current) return current;
      const interesados = [...current.interesados];
      interesados[index] = { ...interesados[index], [field]: uppercaseInput(value) };
      return { ...current, interesados };
    });
  };

  const updateInteresadoAddress = (index: number, value: AddressValue) => {
    setForm((current) => {
      if (!current) return current;
      const interesados = [...current.interesados];
      interesados[index] = { ...interesados[index], ...value, direccion: "" };
      return { ...current, interesados };
    });
  };

  const selectInteresado = (index: number, interesado: InteresadoSearchResult) => {
    setForm((current) => {
      if (!current) return current;
      const interesados = [...current.interesados];
      interesados[index] = {
        ...interesados[index],
        nombre: uppercaseInput(interesado.nombre || ""),
        dni: uppercaseInput(interesado.dni || ""),
        telefono: uppercaseInput(interesado.telefono || ""),
        direccion: uppercaseInput(interesado.direccion || ""),
        tipoVia: uppercaseInput(interesado.tipoVia || ""),
        nombreVia: uppercaseInput(interesado.nombreVia || ""),
        numeroVia: uppercaseInput(interesado.numeroVia || ""),
        bloque: uppercaseInput(interesado.bloque || ""),
        portal: uppercaseInput(interesado.portal || ""),
        escalera: uppercaseInput(interesado.escalera || ""),
        piso: uppercaseInput(interesado.piso || ""),
        puerta: uppercaseInput(interesado.puerta || ""),
        codigoPostal: uppercaseInput(interesado.codigoPostal || ""),
        municipio: uppercaseInput(interesado.municipio || ""),
        provincia: uppercaseInput(interesado.provincia || ""),
      };
      return { ...current, interesados };
    });
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form) return;
    if (!form.clienteId || !form.tipoTramiteId) {
      alert("Selecciona cliente y tipo de tramite antes de crear el expediente.");
      return;
    }
    setSaving(true);
    try {
      const batecom = Boolean(isBatecom(form.tipoTramiteId));
      const payload = batecom
        ? buildPayload(buildBatecomPayload(form))
        : buildPayload({ ...form, interesados: prepareInteresadosForSave(form, false) });
      const creado = await createExpediente(payload);
      navigate(`/expedientes/${creado.id}`);
    } catch (cause) {
      if (cause instanceof ApiError && cause.details) {
        alert(cause.details);
      } else {
        alert("No se pudo crear el expediente.");
      }
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="exp-detail-state">
        <Loader2 className="exp-detail-state__spinner" size={28} />
        <strong>Cargando formulario</strong>
        <span>Estamos preparando clientes y tipos de tramite.</span>
      </div>
    );
  }

  if (error || !catalogs || !form) {
    return (
      <div className="exp-detail-state exp-detail-state--error">
        <AlertCircle size={28} />
        <strong>{error || "Formulario no disponible"}</strong>
        <div className="exp-detail-state__actions">
          <button className="soft-button" onClick={() => void load()} type="button">
            Reintentar
          </button>
          {error?.includes("Sesión") ? (
            <a className="primary-button" href="/login">
              Iniciar sesion
            </a>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <main className="exp-detail-page">
      <section className="exp-panel edit-header-panel">
        <div>
          <p className="eyebrow">Alta de expediente</p>
          <h2>Nuevo expediente</h2>
        </div>
        <Link className="soft-button" to="/expedientes">
          <ArrowLeft size={16} />
          Volver al listado
        </Link>
      </section>

      <form className="exp-edit-form" onSubmit={submit}>
        <section className="exp-panel">
          <div className="exp-panel__heading">
            <div>
              <p className="eyebrow">Datos base</p>
              <h3>Informacion inicial</h3>
            </div>
          </div>

          <div className="edit-form-grid">
            <label>
              Cliente
              <select value={form.clienteId} onChange={(event) => updateField("clienteId", Number(event.target.value))}>
                {catalogs.clientes.map((cliente) => (
                  <option key={cliente.id} value={cliente.id}>
                    {cliente.nombre} {cliente.nif ? `- ${cliente.nif}` : ""}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Tipo de tramite
              <select value={form.tipoTramiteId} onChange={(event) => updateField("tipoTramiteId", Number(event.target.value))}>
                {catalogs.tiposTramite.map((tipo) => (
                  <option key={tipo.id} value={tipo.id}>
                    {tipo.descripcion || humanizeEnum(tipo.nombre)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Matricula
              <input value={form.matricula || ""} onChange={(event) => updateField("matricula", event.target.value)} />
            </label>
            <label className="edit-form-grid__wide">
              Observaciones
              <textarea
                rows={4}
                value={form.observaciones || ""}
                onChange={(event) => updateField("observaciones", event.target.value)}
              />
            </label>
          </div>
        </section>

        <section className="exp-panel">
          <div className="exp-panel__heading">
            <div>
              <p className="eyebrow">Interesados</p>
              <h3>Personas asociadas</h3>
            </div>
          </div>

          <div className="edit-interesados-grid">
            {form.interesados.map((interesado, index) => (
              <article className="edit-interesado-card" key={index}>
                <strong>{isBatecom(form.tipoTramiteId) ? BATECOM_LABELS[index] : `Interesado ${index + 1}`}</strong>
                <InteresadoAutocomplete
                  label="DNI/CIF"
                  value={interesado.dni || ""}
                  placeholder="Buscar por DNI/CIF"
                  onChange={(value) => updateInteresado(index, "dni", value)}
                  onSelect={(seleccionado) => selectInteresado(index, seleccionado)}
                />
                <InteresadoAutocomplete
                  label="Nombre completo/Razon social"
                  value={interesado.nombre || ""}
                  placeholder="Buscar por nombre o razon social"
                  onChange={(value) => updateInteresado(index, "nombre", value)}
                  onSelect={(seleccionado) => selectInteresado(index, seleccionado)}
                />
                <label>
                  Rol
                  <select
                    disabled={isBatecom(form.tipoTramiteId) && index < 3}
                    value={interesado.rol || ""}
                    onChange={(event) => updateInteresado(index, "rol", event.target.value)}
                  >
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
                  <input value={interesado.telefono || ""} onChange={(event) => updateInteresado(index, "telefono", event.target.value)} />
                </label>
                <AddressFields
                  idPrefix={`expediente-create-${index}`}
                  value={interesado}
                  onChange={(value) => updateInteresadoAddress(index, value)}
                />
              </article>
            ))}
          </div>
        </section>

        <div className="edit-form-actions">
          <Link className="soft-button" to="/expedientes">
            Cancelar
          </Link>
          <button className="primary-button" disabled={saving} type="submit">
            <FilePlus2 size={16} />
            Crear expediente
          </button>
        </div>
      </form>
    </main>
  );
}
