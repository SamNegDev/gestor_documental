import { Eye } from "lucide-react";
import { Link, useOutletContext } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import type { TramiteRegistro } from "../types";

export function TramitesRegistroTable({ tramites, showPlate = true }: { tramites: TramiteRegistro[]; showPlate?: boolean }) {
  const { user } = useOutletContext<AppOutletContext>();
  return (
    <div className="records-table-wrap">
      <table className="records-table registry-history-table">
        <thead><tr><th>Expediente</th>{showPlate ? <th>Matricula</th> : null}<th>Tipo / rol</th><th>Cliente</th><th>Estado</th><th>Ultima actividad</th><th aria-label="Acciones" /></tr></thead>
        <tbody>
          {tramites.map((tramite) => (
            <tr key={tramite.id}>
              <td><strong>EXP-{tramite.id}</strong></td>
              {showPlate ? <td><strong className="registry-plate-inline">{tramite.matricula || "SIN MATRICULA"}</strong></td> : null}
              <td><strong>{formatEnum(tramite.tipoTramite)}</strong>{tramite.rol ? <small>{formatEnum(tramite.rol)}</small> : null}</td>
              <td>{tramite.cliente || "Sin cliente"}</td>
              <td><StatusBadge tone={statusTone(tramite.estado)}>{formatEnum(tramite.estado)}</StatusBadge></td>
              <td>{tramite.fechaUltimaModificacion || "Sin fecha"}</td>
              <td><Link className="icon-button" title="Ver expediente" to={user?.rol === "ADMIN" ? `/expedientes/${tramite.id}` : `/cliente/expedientes/${tramite.id}`}><Eye size={16} /></Link></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function formatEnum(value?: string | null) { return value ? value.replaceAll("_", " ") : "Sin dato"; }
function statusTone(value?: string | null): "neutral" | "warning" | "success" | "danger" | "info" {
  if (value === "FINALIZADO") return "success";
  if (value === "INCIDENCIA" || value === "PENDIENTE_DOCUMENTACION" || value === "SOLICITADA_INFORMACION_ADICIONAL") return "danger";
  if (value === "ENVIADO_DGT" || value === "REVISANDO_INCIDENCIAS") return "info";
  return "neutral";
}
