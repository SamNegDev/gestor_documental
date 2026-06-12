import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CarFront, UsersRound } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { getVehiculoRegistro } from "../services/registroApi";
import { TramitesRegistroTable } from "../components/TramitesRegistroTable";
import { RegistroSummary } from "../components/RegistroSummary";

export function VehiculoRegistroDetailPage() {
  const { matricula = "" } = useParams();
  const query = useQuery({ queryKey: ["registro", "vehiculo", matricula], queryFn: () => getVehiculoRegistro(matricula), enabled: Boolean(matricula) });
  if (query.isLoading) return <div className="records-skeleton"><span /><span /><span /></div>;
  if (!query.data) return <div className="records-empty records-empty--danger">No se pudo cargar el vehiculo.</div>;
  const item = query.data;
  return <main className="records-page registry-detail-page"><Link className="registry-back" to="/vehiculos"><ArrowLeft size={16} /> Volver a vehiculos</Link>
    <section className="registry-profile registry-profile--vehicle"><span className="registry-profile__icon"><CarFront size={28} /></span><div><p className="eyebrow">Vehiculo registrado</p><h2 className="registry-plate registry-plate--large">{item.matricula}</h2><strong>{item.totalTramites} tramites asociados</strong></div><div className="registry-profile__people"><span><UsersRound size={16} /> Interesados relacionados</span>{item.interesados.map((interesado) => <strong key={interesado}>{interesado}</strong>)}</div></section>
    <RegistroSummary tramites={item.tramites} />
    <section className="records-panel"><div className="records-panel__heading"><div><h3>Historial del vehiculo</h3><span>Todos los expedientes de esta matricula</span></div></div><TramitesRegistroTable tramites={item.tramites} showPlate={false} /></section>
  </main>;
}
