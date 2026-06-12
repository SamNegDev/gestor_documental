import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, MapPin, Phone, UserRound } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { getInteresadoRegistro } from "../services/registroApi";
import { TramitesRegistroTable } from "../components/TramitesRegistroTable";
import { RegistroSummary } from "../components/RegistroSummary";

export function InteresadoRegistroDetailPage() {
  const { id = "" } = useParams();
  const query = useQuery({ queryKey: ["registro", "interesado", id], queryFn: () => getInteresadoRegistro(id), enabled: Boolean(id) });
  if (query.isLoading) return <div className="records-skeleton"><span /><span /><span /></div>;
  if (!query.data) return <div className="records-empty records-empty--danger">No se pudo cargar el interesado.</div>;
  const item = query.data;
  return <main className="records-page registry-detail-page">
    <Link className="registry-back" to="/interesados"><ArrowLeft size={16} /> Volver a interesados</Link>
    <section className="registry-profile"><span className="registry-profile__icon"><UserRound size={28} /></span><div><p className="eyebrow">{item.tipoPersona || "INTERESADO"}</p><h2>{item.nombre}</h2><strong>{item.dni}</strong></div><div className="registry-profile__facts"><span><Phone size={16} />{item.telefono || "Sin telefono"}</span><span><MapPin size={16} />{item.direccion || "Sin direccion"}</span><span><strong>{item.totalTramites}</strong> tramites asociados</span></div></section>
    <RegistroSummary tramites={item.tramites} roles={item.tramites.map((tramite) => tramite.rol || "")} />
    <section className="records-panel"><div className="records-panel__heading"><div><h3>Historial de tramites</h3><span>Participacion y estado actual</span></div></div><TramitesRegistroTable tramites={item.tramites} /></section>
  </main>;
}
