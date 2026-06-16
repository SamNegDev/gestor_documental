import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AlertCircle, ArrowLeft, Loader2, Save } from "lucide-react";
import {
  getExpedienteDetail,
  getExpedienteEditCatalogs,
  updateExpediente,
} from "../services/expedienteDetailApi";
import type { ExpedienteDetail, ExpedienteEditCatalogs, ExpedienteEditInput, InteresadoSearchResult } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";
import { cleanUpperText, uppercaseInput } from "../../../shared/utils/text";
import { InteresadoAutocomplete } from "../components/InteresadoAutocomplete";
import "../styles/expedienteDetail.css";

const ROLES = ["COMPRADOR", "VENDEDOR", "COMPRAVENTA", "TITULAR"];
const BATECOM_ROLE_ORDER = ["VENDEDOR", "COMPRADOR", "COMPRAVENTA"];
const BATECOM_LABELS = ["Vendedor inicial", "Comprador final", "Compraventa"];

type InteresadoForm = ExpedienteEditInput["interesados"][number];

function emptyInteresado(): InteresadoForm {
  return { nombre: "", dni: "", telefono: "", direccion: "", rol: "" };
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
      || interesado.direccion?.trim(),
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

function buildInitialForm(expediente: ExpedienteDetail): ExpedienteEditInput {
  const totalInteresados = expediente.tipoTramite === "BATECOM" ? 3 : 2;
  const interesados = Array.from({ length: totalInteresados }).map((_, index) => {
    const interesado = expediente.interesados[index];
    return interesado
      ? {
          nombre: uppercaseInput(interesado.nombre || ""),
          dni: uppercaseInput(interesado.dni || ""),
          telefono: uppercaseInput(interesado.telefono || ""),
          direccion: uppercaseInput(interesado.direccion || ""),
          rol: interesado.rol || "",
        }
      : emptyInteresado();
  });

  return {
    clienteId: expediente.cliente?.id || 0,
    tipoTramiteId: 0,
    matricula: uppercaseInput(expediente.matricula || ""),
    observaciones: uppercaseInput(expediente.observaciones || ""),
    interesados,
  };
}

function cleanText(value?: string | null) {
  return cleanUpperText(value);
}

function buildSavePayload(form: ExpedienteEditInput): ExpedienteEditInput {
  return {
    ...form,
    matricula: cleanText(form.matricula),
    observaciones: cleanText(form.observaciones),
    interesados: form.interesados.map((interesado) => ({
      nombre: cleanText(interesado.nombre),
      dni: cleanText(interesado.dni),
      telefono: cleanText(interesado.telefono),
      direccion: cleanText(interesado.direccion),
      rol: cleanText(interesado.rol),
    })),
  };
}

export function ExpedienteEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [expediente, setExpediente] = useState<ExpedienteDetail | null>(null);
  const [catalogs, setCatalogs] = useState<ExpedienteEditCatalogs | null>(null);
  const [form, setForm] = useState<ExpedienteEditInput | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    if (!/^\d+$/.test(id)) {
      setError("La ruta de edicion no contiene un expediente valido.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [detalle, catalogos] = await Promise.all([getExpedienteDetail(id), getExpedienteEditCatalogs()]);
      const initial = buildInitialForm(detalle);
      const tipoActual = catalogos.tiposTramite.find((tipo) => tipo.nombre === detalle.tipoTramite);
      initial.tipoTramiteId = tipoActual?.id || catalogos.tiposTramite[0]?.id || 0;
      setExpediente(detalle);
      setCatalogs(catalogos);
      setForm(initial);
    } catch {
      setError("No se pudo cargar el formulario de edicion.");
    } finally {
      setLoading(false);
    }
  }, [id]);

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
      };
      return { ...current, interesados };
    });
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!id || !form) return;
    if (!/^\d+$/.test(id)) {
      alert("No se puede guardar porque la ruta no contiene un expediente valido.");
      return;
    }
    if (!form.clienteId || !form.tipoTramiteId) {
      alert("Selecciona cliente y tipo de tramite antes de guardar.");
      return;
    }
    setSaving(true);
    try {
      const batecom = Boolean(isBatecom(form.tipoTramiteId));
      const payload = batecom
        ? buildSavePayload(buildBatecomPayload(form))
        : buildSavePayload({ ...form, interesados: prepareInteresadosForSave(form, false) });
      await updateExpediente(id, payload);
      navigate(`/expedientes/${id}`);
    } catch {
      alert("No se pudo guardar el expediente.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="exp-detail-state">
        <Loader2 className="exp-detail-state__spinner" size={28} />
        <strong>Cargando edicion</strong>
        <span>Estamos preparando los datos del expediente.</span>
      </div>
    );
  }

  if (error || !expediente || !catalogs || !form) {
    return (
      <div className="exp-detail-state exp-detail-state--error">
        <AlertCircle size={28} />
        <strong>{error || "Formulario no disponible"}</strong>
      </div>
    );
  }

  return (
    <main className="exp-detail-page">
      <section className="exp-panel edit-header-panel">
        <div>
          <p className="eyebrow">{expediente.referencia}</p>
          <h2>Editar expediente</h2>
        </div>
        <Link className="soft-button" to={`/expedientes/${expediente.id}`}>
          <ArrowLeft size={16} />
          Volver
        </Link>
      </section>

      <form className="exp-edit-form" onSubmit={submit}>
        <section className="exp-panel">
          <div className="exp-panel__heading">
            <div>
              <p className="eyebrow">Datos base</p>
              <h3>Informacion del expediente</h3>
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
                <label>
                  Direccion
                  <input value={interesado.direccion || ""} onChange={(event) => updateInteresado(index, "direccion", event.target.value)} />
                </label>
              </article>
            ))}
          </div>
        </section>

        <div className="edit-form-actions">
          <Link className="soft-button" to={`/expedientes/${expediente.id}`}>
            Cancelar
          </Link>
          <button className="primary-button" disabled={saving} type="submit">
            <Save size={16} />
            Guardar expediente
          </button>
        </div>
      </form>
    </main>
  );
}
