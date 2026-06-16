import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw, Save, Settings2 } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import { getSeguimientoConfig, updateSeguimientoConfig } from "../services/seguimientoApi";
import type { SeguimientoConfig } from "../types";

const DEFAULT_CONFIG: SeguimientoConfig = {
  diasAviso1: 7,
  diasAviso2: 7,
  diasAviso3: 7,
  diasAviso4: 30,
  diasAviso5: 60,
  maxAvisos: 5,
  diasExpedienteEstancado: 7,
};

export function SeguimientoConfigPage() {
  const qc = useQueryClient();
  const [form, setForm] = useState<SeguimientoConfig>(DEFAULT_CONFIG);
  const query = useQuery({ queryKey: ["seguimiento-config"], queryFn: getSeguimientoConfig });
  const mutation = useMutation({
    mutationFn: updateSeguimientoConfig,
    onSuccess: (data) => {
      setForm(data);
      qc.invalidateQueries({ queryKey: ["seguimiento-config"] });
      qc.invalidateQueries({ queryKey: ["seguimiento"] });
      qc.invalidateQueries({ queryKey: ["tareas"] });
    },
  });

  useEffect(() => {
    if (query.data) setForm(query.data);
  }, [query.data]);

  const error = query.error instanceof ApiError ? query.error.details || query.error.message
    : mutation.error instanceof ApiError ? mutation.error.details || mutation.error.message
      : null;

  function update(key: keyof SeguimientoConfig, value: string) {
    setForm((current) => ({ ...current, [key]: Number(value) }));
  }

  return (
    <main className="records-page followup-config-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">Ajustes internos</p>
          <h2>Periodos de seguimiento</h2>
          <p>Valores por defecto para nuevos vencimientos y alertas operativas.</p>
        </div>
      </header>

      <section className="followup-config">
        <div className="followup-config__intro">
          <span><Settings2 size={20} /></span>
          <div>
            <strong>Calendario global de avisos</strong>
            <p>Estos cambios afectan a los próximos cálculos. Los seguimientos ya aplazados mantienen su fecha específica hasta que se envíe otro aviso o se cambie manualmente.</p>
          </div>
        </div>

        <form className="followup-config__form" onSubmit={(event) => { event.preventDefault(); mutation.mutate(form); }}>
          <div className="followup-config__grid">
            <NumberField label="Tras aviso 1" value={form.diasAviso1} onChange={(value) => update("diasAviso1", value)} />
            <NumberField label="Tras aviso 2" value={form.diasAviso2} onChange={(value) => update("diasAviso2", value)} />
            <NumberField label="Tras aviso 3" value={form.diasAviso3} onChange={(value) => update("diasAviso3", value)} />
            <NumberField label="Tras aviso 4" value={form.diasAviso4} onChange={(value) => update("diasAviso4", value)} />
            <NumberField label="Tras aviso 5" value={form.diasAviso5} onChange={(value) => update("diasAviso5", value)} />
            <NumberField label="Máximo avisos" max={5} value={form.maxAvisos} onChange={(value) => update("maxAvisos", value)} />
            <NumberField label="Expediente estancado" value={form.diasExpedienteEstancado} onChange={(value) => update("diasExpedienteEstancado", value)} />
          </div>

          {error ? <div className="mail-dialog__error followup-config__error">{error}</div> : null}
          {mutation.isSuccess ? <div className="followup-config__success">Configuración guardada.</div> : null}

          <footer className="followup-config__actions">
            <button className="soft-button" type="button" onClick={() => setForm(DEFAULT_CONFIG)}>
              <RotateCcw size={16} />
              Restaurar valores por defecto
            </button>
            <button className="primary-button" disabled={mutation.isPending || query.isLoading} type="submit">
              <Save size={16} />
              {mutation.isPending ? "Guardando..." : "Guardar cambios"}
            </button>
          </footer>
        </form>
      </section>
    </main>
  );
}

function NumberField({ label, value, onChange, max = 365 }: { label: string; value: number; onChange: (value: string) => void; max?: number }) {
  return (
    <label className="followup-config__field">
      <span>{label}</span>
      <input min={1} max={max} type="number" value={Number.isFinite(value) ? value : ""} onChange={(event) => onChange(event.target.value)} />
      <small>{label === "Máximo avisos" ? "avisos" : "días"}</small>
    </label>
  );
}
