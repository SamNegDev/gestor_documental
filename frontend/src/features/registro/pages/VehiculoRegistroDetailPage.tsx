import { useEffect, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CalendarDays, CarFront, Pencil, Save, UsersRound, X } from "lucide-react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { getVehiculoRegistro, updateVehiculoRegistro } from "../services/registroApi";
import { TramitesRegistroTable } from "../components/TramitesRegistroTable";
import { RegistroSummary } from "../components/RegistroSummary";
import type { VehiculoRegistroUpdateInput } from "../types";

export function VehiculoRegistroDetailPage() {
  const { matricula = "" } = useParams();
  const { user } = useOutletContext<AppOutletContext>();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<VehiculoRegistroUpdateInput>({});
  const query = useQuery({ queryKey: ["registro", "vehiculo", matricula], queryFn: () => getVehiculoRegistro(matricula), enabled: Boolean(matricula) });
  const mutation = useMutation({
    mutationFn: (input: VehiculoRegistroUpdateInput) => updateVehiculoRegistro(matricula, input),
    onSuccess: async () => {
      setEditing(false);
      await queryClient.invalidateQueries({ queryKey: ["registro", "vehiculo", matricula] });
      await queryClient.invalidateQueries({ queryKey: ["registro", "vehiculos"] });
    },
  });

  useEffect(() => {
    if (!query.data) return;
    setForm({
      bastidor: query.data.bastidor || "",
      marca: query.data.marca || "",
      modelo: query.data.modelo || "",
      fechaPrimeraMatriculacion: query.data.fechaPrimeraMatriculacion || "",
      observaciones: query.data.observaciones || "",
    });
  }, [query.data]);

  if (query.isLoading) return <div className="records-skeleton"><span /><span /><span /></div>;
  if (!query.data) return <div className="records-empty records-empty--danger">No se pudo cargar el vehículo.</div>;

  const item = query.data;
  const canEdit = user?.rol === "ADMIN";
  const marcaModelo = [item.marca, item.modelo].filter(Boolean).join(" ");

  const updateField = (field: keyof VehiculoRegistroUpdateInput, value: string) => {
    setForm((current) => ({ ...current, [field]: field === "fechaPrimeraMatriculacion" ? value : value.toUpperCase() }));
  };

  return (
    <main className="records-page registry-detail-page">
      <Link className="registry-back" to="/vehiculos"><ArrowLeft size={16} /> Volver a vehículos</Link>

      <section className="registry-profile registry-profile--vehicle registry-profile--vehicle-entity">
        <span className="registry-profile__icon"><CarFront size={28} /></span>
        <div>
          <p className="eyebrow">Ficha de vehículo</p>
          <h2 className="registry-plate registry-plate--large">{item.matricula}</h2>
          <strong>{marcaModelo || "Datos técnicos pendientes"}</strong>
          <small>{item.totalTramites} trámites asociados</small>
        </div>
        <div className="registry-profile__facts registry-profile__facts--vehicle">
          <VehicleFact label="Bastidor" value={item.bastidor} />
          <VehicleFact label="Primera matriculación" value={item.fechaPrimeraMatriculacion} icon={<CalendarDays size={15} />} />
          <VehicleFact label="Observaciones" value={item.observaciones} />
        </div>
        <div className="registry-profile__people">
          <span><UsersRound size={16} /> Interesados relacionados</span>
          {item.interesados.length ? item.interesados.map((interesado) => <strong key={interesado}>{interesado}</strong>) : <strong>Sin interesados informados</strong>}
        </div>
        {canEdit ? (
          <button className="soft-button soft-button--compact registry-profile__edit" onClick={() => setEditing(true)} type="button">
            <Pencil size={15} />
            Editar ficha
          </button>
        ) : null}
      </section>

      {editing ? (
        <section className="records-panel vehicle-edit-panel">
          <div className="records-panel__heading">
            <div>
              <h3>Datos del vehículo</h3>
              <span>Información interna asociada a la matrícula</span>
            </div>
            <button className="icon-button" onClick={() => setEditing(false)} title="Cerrar edición" type="button"><X size={16} /></button>
          </div>
          <form className="vehicle-edit-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate(form); }}>
            <label><span>Bastidor</span><input value={form.bastidor || ""} onChange={(event) => updateField("bastidor", event.target.value)} /></label>
            <label><span>Marca</span><input value={form.marca || ""} onChange={(event) => updateField("marca", event.target.value)} /></label>
            <label><span>Modelo</span><input value={form.modelo || ""} onChange={(event) => updateField("modelo", event.target.value)} /></label>
            <label><span>Primera matriculación</span><input type="date" value={form.fechaPrimeraMatriculacion || ""} onChange={(event) => updateField("fechaPrimeraMatriculacion", event.target.value)} /></label>
            <label className="vehicle-edit-form__wide"><span>Observaciones</span><textarea value={form.observaciones || ""} onChange={(event) => updateField("observaciones", event.target.value)} /></label>
            {mutation.isError ? <p className="form-error">No se pudo guardar la ficha del vehículo.</p> : null}
            <div className="vehicle-edit-form__actions">
              <button className="soft-button" onClick={() => setEditing(false)} type="button">Cancelar</button>
              <button className="primary-button" disabled={mutation.isPending} type="submit"><Save size={16} /> Guardar ficha</button>
            </div>
          </form>
        </section>
      ) : null}

      <RegistroSummary tramites={item.tramites} />
      <section className="records-panel">
        <div className="records-panel__heading">
          <div>
            <h3>Historial del vehículo</h3>
            <span>Todos los expedientes de esta matrícula</span>
          </div>
        </div>
        <TramitesRegistroTable tramites={item.tramites} showPlate={false} />
      </section>
    </main>
  );
}

function VehicleFact({ label, value, icon }: { label: string; value?: string | null; icon?: ReactNode }) {
  return (
    <span className={!value ? "is-muted" : ""}>
      {icon}
      <small>{label}</small>
      <strong>{value || "Sin informar"}</strong>
    </span>
  );
}
