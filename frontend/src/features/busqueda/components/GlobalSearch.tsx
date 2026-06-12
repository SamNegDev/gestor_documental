import { useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CarFront, FileInput, FolderOpen, Search, UserRound, X } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { buscarGlobal } from "../services/busquedaGlobalApi";
import type { BusquedaGlobalItem } from "../types";

const groups = [
  { key: "expedientes", label: "Expedientes", icon: FolderOpen },
  { key: "solicitudes", label: "Solicitudes", icon: FileInput },
  { key: "interesados", label: "Interesados", icon: UserRound },
  { key: "vehiculos", label: "Vehiculos", icon: CarFront },
] as const;

export function GlobalSearch() {
  const [query, setQuery] = useState(""); const [open, setOpen] = useState(false);
  const deferredQuery = useDeferredValue(query.trim()); const root = useRef<HTMLDivElement>(null); const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate(); const location = useLocation();
  const search = useQuery({ queryKey: ["busqueda-global", deferredQuery], queryFn: () => buscarGlobal(deferredQuery), enabled: deferredQuery.length >= 2, staleTime: 30_000 });
  const total = useMemo(() => search.data ? groups.reduce((sum, group) => sum + search.data[group.key].length, 0) : 0, [search.data]);

  useEffect(() => { setOpen(false); setQuery(""); }, [location.pathname]);
  useEffect(() => {
    const close = (event: MouseEvent) => { if (root.current && !root.current.contains(event.target as Node)) setOpen(false); };
    const shortcut = (event: KeyboardEvent) => { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); setOpen(true); input.current?.focus(); } if (event.key === "Escape") { setOpen(false); input.current?.blur(); } };
    document.addEventListener("mousedown", close); document.addEventListener("keydown", shortcut);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", shortcut); };
  }, []);

  function select(item: BusquedaGlobalItem) { setOpen(false); setQuery(""); navigate(item.enlace); }

  return <div className={`global-search ${open ? "is-open" : ""}`} ref={root}>
    <Search size={17} className="global-search__leading" />
    <input ref={input} value={query} onFocus={() => setOpen(true)} onChange={event => { setQuery(event.target.value.toUpperCase()); setOpen(true); }} placeholder="Buscar matricula, DNI, nombre..." aria-label="Busqueda global" />
    {query ? <button className="global-search__clear" aria-label="Limpiar busqueda" type="button" onClick={() => { setQuery(""); input.current?.focus(); }}><X size={15} /></button> : null}
    {open && query.trim().length >= 2 ? <section className="global-search__results" aria-label="Resultados de busqueda">
      {search.isLoading ? <div className="global-search__status">Buscando...</div> : null}
      {search.isError ? <div className="global-search__status global-search__status--error">No se pudo completar la busqueda.</div> : null}
      {!search.isLoading && search.data && total === 0 ? <div className="global-search__status">No hay coincidencias accesibles.</div> : null}
      {search.data && total > 0 ? <div className="global-search__groups">{groups.map(group => { const items = search.data[group.key]; const Icon = group.icon; return items.length ? <div className="global-search__group" key={group.key}><header><Icon size={14} /><span>{group.label}</span><small>{items.length}</small></header>{items.map(item => <button type="button" key={item.id} onClick={() => select(item)}><span><strong>{item.titulo}</strong><small>{item.detalle}</small></span><em>{item.meta}</em></button>)}</div> : null; })}</div> : null}
    </section> : null}
  </div>;
}
