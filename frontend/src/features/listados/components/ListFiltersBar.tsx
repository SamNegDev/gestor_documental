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
            <option value="ESTE_MES">Este mes</option>
            <option value="ULTIMOS_3_MESES">Ultimos 3 meses</option>
            <option value="ESTE_ANIO">Este año</option>
            <option value="TODO">Todo el historico</option>
          </select>
        </label>
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
            <option value="ESTE_MES">Este mes</option>
            <option value="ULTIMOS_3_MESES">Ultimos 3 meses</option>
            <option value="ESTE_ANIO">Este año</option>
            <option value="TODO">Todo el historico</option>
          </select>
        </label>
      </div>
    </div>
  );
}
