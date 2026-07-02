import { useDeferredValue, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronRight, Plus, Save, UserRound, X } from "lucide-react";
import { Link, useOutletContext } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { createInteresadoHabitual, getInteresadosRegistro } from "../services/registroApi";
import { RegistroFilters } from "../components/RegistroFilters";
import type { InteresadoRegistroUpdateInput } from "../types";
import { uppercaseInput } from "../../../shared/utils/text";
import { AddressFields, type AddressValue } from "../../../shared/ui/AddressFields";
import { ApiError } from "../../../shared/api/http";
import "../../expedientes/styles/expedienteDetail.css";

export function InteresadosRegistroPage() {
  const [search, setSearch] = useState("");
  const [periodo, setPeriodo] = useState("ULTIMA_SEMANA");
  const [fechaDesde, setFechaDesde] = useState("");
  const [fechaHasta, setFechaHasta] = useState("");
  const [vista, setVista] = useState<"HABITUALES" | "RECIENTES">("HABITUALES");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<InteresadoRegistroUpdateInput>({ tipoPersona: "PARTICULAR" });
  const { user } = useOutletContext<AppOutletContext>();
  const queryClient = useQueryClient();
  const deferredSearch = useDeferredValue(search);
  const query = useQuery({
    queryKey: ["registro", "interesados", vista, deferredSearch, periodo, fechaDesde, fechaHasta],
    queryFn: () => getInteresadosRegistro(deferredSearch, periodo, fechaDesde, fechaHasta, vista),
  });
  const mutation = useMutation({
    mutationFn: (input: InteresadoRegistroUpdateInput) => createInteresadoHabitual(input),
    onSuccess: async () => {
      setCreating(false);
      setForm({ tipoPersona: "PARTICULAR" });
      await queryClient.invalidateQueries({ queryKey: ["registro", "interesados"] });
    },
  });
  const interesados = query.data ?? [];
  const canCreateHabitual = user?.rol === "CLIENTE";
  const showingHabituales = vista === "HABITUALES";
  const updateField = (field: keyof InteresadoRegistroUpdateInput, value: string) => setForm((current) => ({ ...current, [field]: uppercaseInput(value) }));
  const updateAddress = (value: AddressValue) => setForm((current) => ({ ...current, ...value, direccion: "" }));

  return (
    <main className="records-page registry-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">Cartera reutilizable</p>
          <h2>{showingHabituales ? "Clientes habituales" : "Interesados recientes"}</h2>
          <p>{showingHabituales ? "Personas y empresas guardadas para reutilizar datos y documentacion." : "Personas que han participado en tramites, sin quedar guardadas como habituales."}</p>
        </div>
        <div className="records-header__actions">
          {canCreateHabitual ? (
            <button className="primary-button primary-button--compact" type="button" onClick={() => setCreating(true)}>
              <Plus size={16} />
              Nuevo cliente habitual
            </button>
          ) : null}
          <span className="records-count">{interesados.length} registros</span>
        </div>
      </header>
      <div className="task-tabs registry-tabs" role="tablist" aria-label="Tipo de interesados">
        <button className={vista === "HABITUALES" ? "is-active" : ""} type="button" onClick={() => setVista("HABITUALES")}>
          Clientes habituales
        </button>
        <button className={vista === "RECIENTES" ? "is-active" : ""} type="button" onClick={() => setVista("RECIENTES")}>
          Interesados recientes
        </button>
      </div>
      <RegistroFilters
        search={search}
        periodo={periodo}
        fechaDesde={fechaDesde}
        fechaHasta={fechaHasta}
        placeholder="Buscar por DNI, CIF o nombre"
        onSearch={setSearch}
        onPeriodo={setPeriodo}
        onFechaDesde={setFechaDesde}
        onFechaHasta={setFechaHasta}
      />
      <section className="records-panel records-panel--ledger">
        {query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
        {query.error ? <div className="records-empty records-empty--danger">No se pudieron cargar los interesados.</div> : null}
        {!query.isLoading && !query.error ? (
          <div className="registry-list">
            {interesados.length === 0 ? <div className="records-empty">{showingHabituales ? "No hay clientes habituales que coincidan con la busqueda." : "No hay interesados recientes que coincidan con la busqueda."}</div> : null}
            {interesados.map((item) => (
              <Link className="registry-row" key={item.id} to={`/interesados/${item.id}`}>
                <span className="registry-row__icon"><UserRound size={19} /></span>
                <span className="registry-row__identity">
                  <strong>{item.nombre}</strong>
                  <small>{item.dni}{item.representanteLegal ? " - representante legal" : ""}</small>
                </span>
                <span>
                  <small>Tipo de ficha</small>
                  <strong>{item.habitual ? "Cliente habitual" : "Interesado puntual"}</strong>
                </span>
                <span>
                  <small>Tramites</small>
                  <strong>{item.totalTramites}</strong>
                </span>
                <span>
                  <small>Ultima actividad</small>
                  <strong>{item.ultimaActividad || "Sin actividad"}</strong>
                </span>
                <ChevronRight size={18} />
              </Link>
            ))}
          </div>
        ) : null}
      </section>
      {creating ? (
        <div className="exp-modal" role="presentation">
          <button className="exp-modal__backdrop" type="button" aria-label="Cerrar" onClick={() => setCreating(false)} />
          <section className="exp-modal__panel exp-modal__panel--narrow" role="dialog" aria-modal="true" aria-labelledby="habitual-title">
            <div className="exp-modal__header">
              <div>
                <p className="eyebrow">Cartera reutilizable</p>
                <h3 id="habitual-title">Nuevo cliente habitual</h3>
              </div>
              <button className="icon-button" type="button" aria-label="Cerrar" onClick={() => setCreating(false)}>
                <X size={16} />
              </button>
            </div>
            <form className="vehicle-edit-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate(form); }}>
              <label><span>DNI / CIF</span><input required value={form.dni || ""} onChange={(event) => updateField("dni", event.target.value)} /></label>
              <label><span>Nombre</span><input required value={form.nombre || ""} onChange={(event) => updateField("nombre", event.target.value)} /></label>
              <label><span>Telefono</span><input value={form.telefono || ""} onChange={(event) => updateField("telefono", event.target.value)} /></label>
              <label><span>Tipo</span><select value={form.tipoPersona || "PARTICULAR"} onChange={(event) => updateField("tipoPersona", event.target.value)}><option value="PARTICULAR">PARTICULAR</option><option value="EMPRESA">EMPRESA</option></select></label>
              <AddressFields idPrefix="interesado-habitual" value={form} onChange={updateAddress} wideClassName="vehicle-edit-form__wide" />
              {mutation.isError ? <p className="form-error">{mutation.error instanceof ApiError ? mutation.error.details || "No se pudo crear el cliente habitual." : "No se pudo crear el cliente habitual."}</p> : null}
              <div className="vehicle-edit-form__actions">
                <button className="soft-button" type="button" onClick={() => setCreating(false)}>Cancelar</button>
                <button className="primary-button" disabled={mutation.isPending} type="submit">
                  <Save size={16} />
                  Guardar cliente habitual
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </main>
  );
}
