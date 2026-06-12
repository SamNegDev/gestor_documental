import { useDeferredValue, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, UserRound } from "lucide-react";
import { Link } from "react-router-dom";
import { getInteresadosRegistro } from "../services/registroApi";
import { RegistroFilters } from "../components/RegistroFilters";

export function InteresadosRegistroPage() {
  const [search, setSearch] = useState("");
  const [periodo, setPeriodo] = useState("ESTE_MES");
  const [fechaDesde, setFechaDesde] = useState("");
  const [fechaHasta, setFechaHasta] = useState("");
  const deferredSearch = useDeferredValue(search);
  const query = useQuery({ queryKey: ["registro", "interesados", deferredSearch, periodo, fechaDesde, fechaHasta], queryFn: () => getInteresadosRegistro(deferredSearch, periodo, fechaDesde, fechaHasta) });
  const interesados = query.data ?? [];
  return <main className="records-page registry-page">
    <header className="records-header"><div><p className="eyebrow">Registro relacionado</p><h2>Interesados</h2><p>Personas y empresas vinculadas a los tramites accesibles.</p></div><span className="records-count">{interesados.length} registros</span></header>
    <RegistroFilters search={search} periodo={periodo} fechaDesde={fechaDesde} fechaHasta={fechaHasta} placeholder="Buscar por DNI, CIF o nombre" onSearch={setSearch} onPeriodo={setPeriodo} onFechaDesde={setFechaDesde} onFechaHasta={setFechaHasta} />
    <section className="records-panel records-panel--ledger">
      {query.isLoading ? <div className="records-skeleton"><span /><span /><span /></div> : null}
      {query.error ? <div className="records-empty records-empty--danger">No se pudieron cargar los interesados.</div> : null}
      {!query.isLoading && !query.error ? <div className="registry-list">
        {interesados.length === 0 ? <div className="records-empty">No hay interesados que coincidan con la busqueda.</div> : null}
        {interesados.map((item) => <Link className="registry-row" key={item.id} to={`/interesados/${item.id}`}>
          <span className="registry-row__icon"><UserRound size={19} /></span><span className="registry-row__identity"><strong>{item.nombre}</strong><small>{item.dni}</small></span>
          <span><small>Contacto</small><strong>{item.telefono || "Sin telefono"}</strong></span><span><small>Tramites</small><strong>{item.totalTramites}</strong></span>
          <span><small>Ultima actividad</small><strong>{item.ultimaActividad || "Sin actividad"}</strong></span><ChevronRight size={18} />
        </Link>)}
      </div> : null}
    </section>
  </main>;
}
