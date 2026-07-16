import { ArrowLeft, Ban, Building2, CalendarDays, CarFront, Eye, FileText, Loader2, Pencil, UserRound } from "lucide-react";
import { Link } from "react-router-dom";
import type { ExpedienteDetail } from "../types/expedienteDetail.types";
import { formatDateTime, humanizeEnum } from "../utils/formatters";
import { ExpedienteStatus } from "./ExpedienteStatus";
import { LicensePlate } from "./LicensePlate";
import { clientInitials } from "../../../shared/utils/clientBranding";

type Props = {
  expediente: ExpedienteDetail;
  cancelling?: boolean;
  onCancel?: () => void;
};

export function ExpedienteHeader({ expediente, cancelling = false, onCancel }: Props) {
  const comprador = expediente.interesados.find((interesado) => interesado.rol === "COMPRADOR");
  const titular = expediente.interesados.find((interesado) => interesado.rol === "TITULAR");
  const interesadoPrincipal = comprador || titular || expediente.interesados[0];
  const rolPrincipal = comprador ? "Comprador" : titular ? "Titular" : interesadoPrincipal ? "Interesado" : "";
  const interesadoTexto = interesadoPrincipal
    ? `${rolPrincipal}: ${interesadoPrincipal.dni || "SIN DNI"} - ${interesadoPrincipal.nombre || "SIN NOMBRE"}`
    : "Sin comprador asignado";

  return (
    <section className="exp-header">
      <div className="exp-header__main">
        <Link aria-label="Volver al listado de expedientes" className="exp-header__back" title="Volver a expedientes" to="/expedientes">
          <ArrowLeft size={18} />
        </Link>
        <div className="exp-header__vehicle">
          <LicensePlate value={expediente.matricula} />
          {expediente.matricula ? (
            <Link
              aria-label="Ver ficha del vehículo"
              className="exp-header__vehicle-button"
              title="Ver ficha del vehículo"
              to={`/vehiculos/${encodeURIComponent(expediente.matricula)}`}
            >
              <CarFront size={17} />
            </Link>
          ) : null}
        </div>

        <div className="exp-header__title">
          <p className="eyebrow">{expediente.referencia}</p>
          <h2>{humanizeEnum(expediente.tipoTramiteDescripcion || expediente.tipoTramite)}</h2>
          <div className="exp-header__meta">
            <span className="exp-header__client">
              <span className="exp-header__client-mark" aria-hidden="true">
                {expediente.cliente?.logoCompactoUrl ? (
                  <img src={expediente.cliente.logoCompactoUrl} alt="" />
                ) : expediente.cliente ? clientInitials(expediente.cliente.nombre) : <Building2 size={16} />}
              </span>
              <strong>Cliente:</strong>
              {expediente.cliente?.nombre || "Sin cliente asignado"}
              {expediente.cliente?.nif ? <small>· {expediente.cliente.nif}</small> : null}
            </span>
            <span>
              <FileText size={16} />
              {expediente.tipoTramite ? humanizeEnum(expediente.tipoTramite) : "Trámite sin definir"}
            </span>
            <span>
              <CalendarDays size={16} />
              Inicio: {formatDateTime(expediente.fechaInicio)}
            </span>
            <span>
              <UserRound size={16} />
              {interesadoTexto}
            </span>
          </div>
        </div>
      </div>

      <div className="exp-header__status">
        <div className="exp-header__status-summary">
          <span>Estado actual</span>
          <ExpedienteStatus status={expediente.estado} />
          <small>{expediente.faseActual || "Fase sin definir"}</small>
        </div>
        <div className="exp-header__actions">
          <Link className="soft-button soft-button--compact" to={`/expedientes/${expediente.id}/editar`}>
            <Pencil size={15} />
            Editar expediente
          </Link>
          {expediente.solicitudId ? (
            <Link className="soft-button soft-button--compact" to={`/solicitudes/${expediente.solicitudId}`}>
              <FileText size={15} />
              Solicitud de origen
            </Link>
          ) : null}
          <Link className="soft-button soft-button--compact" to={`/cliente/expedientes/${expediente.id}`}>
            <Eye size={15} />
            Vista cliente
          </Link>
          {onCancel ? (
            <button className="soft-button soft-button--compact soft-button--danger" disabled={cancelling} onClick={onCancel} type="button">
              {cancelling ? <Loader2 className="button-spinner" size={15} /> : <Ban size={15} />}
              {cancelling ? "Cancelando" : "Cancelar por el cliente"}
            </button>
          ) : null}
        </div>
      </div>
    </section>
  );
}
