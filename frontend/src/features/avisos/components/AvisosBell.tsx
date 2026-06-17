import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck, ExternalLink } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { getAvisosResumen, marcarAvisoLeido, marcarAvisosLeidos } from "../services/avisosApi";

export function AvisosBell() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const qc = useQueryClient();
  const resumen = useQuery({
    queryKey: ["avisos-admin", "resumen"],
    queryFn: getAvisosResumen,
    refetchInterval: 30000,
    refetchOnMount: "always",
    refetchOnWindowFocus: "always",
  });
  const markOne = useMutation({
    mutationFn: marcarAvisoLeido,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["avisos-admin"] }),
  });
  const markAll = useMutation({
    mutationFn: marcarAvisosLeidos,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["avisos-admin"] }),
  });

  useEffect(() => {
    function close(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const avisos = resumen.data?.avisos ?? [];
  const pendientes = resumen.data?.pendientes ?? 0;

  return (
    <div className="notification-bell" ref={rootRef}>
      <button
        aria-expanded={open}
        aria-label={`Avisos pendientes: ${pendientes}`}
        className={`notification-bell__trigger ${pendientes > 0 ? "notification-bell__trigger--active" : ""}`}
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        <Bell size={18} />
        {pendientes > 0 ? <span>{pendientes > 99 ? "99+" : pendientes}</span> : null}
      </button>
      {open ? (
        <section className="notification-bell__panel">
          <header>
            <div>
              <strong>Avisos</strong>
              <span>{pendientes === 1 ? "1 pendiente" : `${pendientes} pendientes`}</span>
            </div>
            {pendientes > 0 ? (
              <button disabled={markAll.isPending} onClick={() => markAll.mutate()} type="button">
                <CheckCheck size={15} />
                Leidos
              </button>
            ) : null}
          </header>
          <div className="notification-bell__list">
            {resumen.isLoading ? (
              <p className="notification-bell__empty">Cargando avisos...</p>
            ) : avisos.length === 0 ? (
              <p className="notification-bell__empty">No hay avisos pendientes.</p>
            ) : (
              avisos.map((aviso) => (
                <article className="notification-bell__item" key={aviso.id}>
                  <div>
                    <small>{aviso.origen || aviso.tipo || "Sistema"} · {aviso.fechaCreacion || "Sin fecha"}</small>
                    <strong>{aviso.titulo || "Aviso"}</strong>
                    <p>{aviso.detalle || "Aviso pendiente de confirmar."}</p>
                    <span>{aviso.matricula || aviso.cliente || "Sin expediente vinculado"}</span>
                  </div>
                  <div className="notification-bell__actions">
                    {aviso.expedienteId ? (
                      <Link to={`/expedientes/${aviso.expedienteId}`} onClick={() => setOpen(false)} title="Abrir expediente">
                        <ExternalLink size={15} />
                      </Link>
                    ) : null}
                    <button disabled={markOne.isPending} onClick={() => markOne.mutate(aviso.id)} title="Marcar leido" type="button">
                      <CheckCheck size={15} />
                    </button>
                  </div>
                </article>
              ))
            )}
          </div>
        </section>
      ) : null}
    </div>
  );
}
