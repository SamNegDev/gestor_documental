import { Search } from "lucide-react";
import type { ReactNode } from "react";
import type { ListCatalogs, ListFilters } from "../types";

type ListFiltersBarProps = {
  catalogs?: ListCatalogs;
  filters: ListFilters;
  showClientFilter: boolean;
  compact?: boolean;
  additionalFilter?: ReactNode;
  onChange: (filters: ListFilters) => void;
  onSubmit: () => void;
  onClear: () => void;
};

export function ListFiltersBar({
  filters,
  compact = false,
  additionalFilter,
  onChange,
}: ListFiltersBarProps) {
  if (compact) {
    return (
      <div className="list-period-toolbar">
        <label>
          <span>Periodo</span>
          <select value={filters.periodo || "ESTE_MES"} onChange={(event) => onChange({ ...filters, periodo: event.target.value })}>
            <option value="ULTIMA_SEMANA">Ultima semana</option>
            <option value="ESTE_MES">Este mes</option>
            <option value="ULTIMOS_3_MESES">Ultimos 3 meses</option>
            <option value="ESTE_ANIO">Este año</option>
            <option value="TODO">Todo el historico</option>
            <option value="PERSONALIZADO">Rango personalizado</option>
          </select>
        </label>
        {filters.periodo === "PERSONALIZADO" ? <CustomDateRange filters={filters} onChange={onChange} /> : null}
        {additionalFilter}
      </div>
    );
  }

  return (
    <div className="list-filters-panel list-filters-panel--period">
      <div className="list-filters-panel__title">
        <Search size={16} />
        <span>Periodo de trabajo</span>
      </div>

      <div className="list-filters-grid">
        <label>
          <span>Periodo</span>
          <select value={filters.periodo || "ESTE_MES"} onChange={(event) => onChange({ ...filters, periodo: event.target.value })}>
            <option value="ULTIMA_SEMANA">Ultima semana</option>
            <option value="ESTE_MES">Este mes</option>
            <option value="ULTIMOS_3_MESES">Ultimos 3 meses</option>
            <option value="ESTE_ANIO">Este año</option>
            <option value="TODO">Todo el historico</option>
            <option value="PERSONALIZADO">Rango personalizado</option>
          </select>
        </label>
      </div>
      {filters.periodo === "PERSONALIZADO" ? <CustomDateRange filters={filters} onChange={onChange} /> : null}
    </div>
  );
}

function CustomDateRange({ filters, onChange }: { filters: ListFilters; onChange: (filters: ListFilters) => void }) {
  return <div className="custom-date-range"><label><span>Desde</span><input type="date" value={filters.fechaDesde || ""} onChange={(event) => onChange({ ...filters, fechaDesde: event.target.value })} /></label><label><span>Hasta</span><input type="date" value={filters.fechaHasta || ""} onChange={(event) => onChange({ ...filters, fechaHasta: event.target.value })} /></label></div>;
}
