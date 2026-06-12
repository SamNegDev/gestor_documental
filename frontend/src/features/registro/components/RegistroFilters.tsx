import { CalendarDays, Search } from "lucide-react";

export function RegistroFilters({ search, periodo, fechaDesde, fechaHasta, placeholder, onSearch, onPeriodo, onFechaDesde, onFechaHasta }: { search: string; periodo: string; fechaDesde: string; fechaHasta: string; placeholder: string; onSearch: (value: string) => void; onPeriodo: (value: string) => void; onFechaDesde: (value: string) => void; onFechaHasta: (value: string) => void }) {
  return <div className="registry-filters">
    <div className="registry-search"><Search size={18} /><input value={search} onChange={(event) => onSearch(event.target.value.toUpperCase())} placeholder={placeholder} /></div>
    <label className="registry-period-filter"><CalendarDays size={17} /><span>Periodo</span><select value={periodo} onChange={(event) => onPeriodo(event.target.value)}><option value="ULTIMA_SEMANA">Ultima semana</option><option value="ESTE_MES">Este mes</option><option value="ULTIMOS_3_MESES">Ultimos 3 meses</option><option value="ESTE_ANIO">Este año</option><option value="TODO">Todo el historico</option><option value="PERSONALIZADO">Rango personalizado</option></select></label>
    {periodo === "PERSONALIZADO" ? <div className="custom-date-range custom-date-range--registry"><label><span>Desde</span><input type="date" value={fechaDesde} onChange={(event) => onFechaDesde(event.target.value)} /></label><label><span>Hasta</span><input type="date" value={fechaHasta} onChange={(event) => onFechaHasta(event.target.value)} /></label></div> : null}
  </div>;
}
