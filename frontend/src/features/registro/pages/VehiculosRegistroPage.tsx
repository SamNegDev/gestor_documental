import { useDeferredValue, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CarFront, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { getVehiculosRegistro } from "../services/registroApi";
import { RegistroFilters } from "../components/RegistroFilters";

export function VehiculosRegistroPage() {
  const [search, setSearch] = useState(""); const [periodo, setPeriodo] = useState("ESTE_MES"); const [fechaDesde, setFechaDesde] = useState(""); const [fechaHasta, setFechaHasta] = useState(""); const deferredSearch = useDeferredValue(search);
  const query = useQuery({ queryKey: ["registro", "vehiculos", deferredSearch, periodo, fechaDesde, fechaHasta], queryFn: () => getVehiculosRegistro(deferredSearch, periodo, fechaDesde, fechaHasta) });
  const vehiculos = query.data ?? [];
  return <main className="records-page registry-page"><header className="records-header"><div><p className="eyebrow">Registro relacionado</p><h2>Vehiculos</h2><p>Historial consolidado por matricula.</p></div><span className="records-count">{vehiculos.length} vehiculos</span></header>
    <RegistroFilters search={search} periodo={periodo} fechaDesde={fechaDesde} fechaHasta={fechaHasta} placeholder="Buscar por matricula" onSearch={setSearch} onPeriodo={setPeriodo} onFechaDesde={setFechaDesde} onFechaHasta={setFechaHasta} />
    <section className="records-panel records-panel--ledger">{query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}{query.error ? <div className="records-empty records-empty--danger">No se pudieron cargar los vehiculos.</div> : null}
      {!query.isLoading && !query.error ? <div className="registry-list">{vehiculos.length === 0 ? <div className="records-empty">No hay vehiculos que coincidan con la busqueda.</div> : null}{vehiculos.map((item) => <Link className="registry-row registry-row--vehicle" key={item.matricula} to={`/vehiculos/${encodeURIComponent(item.matricula)}`}><span className="registry-row__icon"><CarFront size={19} /></span><span className="registry-row__identity"><strong className="registry-plate">{item.matricula}</strong><small>{item.interesados[0] || "Sin interesados"}</small></span><span><small>Personas relacionadas</small><strong>{item.interesados.length}</strong></span><span><small>Tramites</small><strong>{item.totalTramites}</strong></span><span><small>Ultima actividad</small><strong>{item.ultimaActividad || "Sin actividad"}</strong></span><ChevronRight size={18} /></Link>)}</div> : null}
    </section></main>;
}
