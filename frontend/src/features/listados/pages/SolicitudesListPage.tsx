import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import { ClipboardCheck, Eye, Plus } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { ApiError } from "../../../shared/api/http";
import { getSolicitudListCatalogs, getSolicitudes } from "../services/listadosApi";
import { ListFiltersBar } from "../components/ListFiltersBar";
import { ListPageChrome } from "../components/ListPageChrome";
import type { ListCatalogs, ListFilters, SolicitudListItem } from "../types";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { uppercaseInput } from "../../../shared/utils/text";

export function SolicitudesListPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const [searchParams] = useSearchParams();
  const [appliedFilters, setAppliedFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const [draftFilters, setDraftFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const isAdmin = user?.rol === "ADMIN";

  const catalogsQuery = useQuery({
    queryKey: ["solicitudes", "catalogos-listado"],
    queryFn: getSolicitudListCatalogs,
  });

  const solicitudesQuery = useQuery({
    queryKey: ["solicitudes", "listado", appliedFilters],
    queryFn: () => getSolicitudes(appliedFilters),
    placeholderData: (previousData) => previousData,
  });

  function applyFilters(filters: ListFilters) {
    setAppliedFilters(filters);
  }

  const solicitudes = solicitudesQuery.data ?? [];

  return (
    <ListPageChrome
      eyebrow={isAdmin ? "Gestion interna" : "Portal cliente"}
      title={isAdmin ? "Solicitudes" : "Mis solicitudes"}
      summary={
        isAdmin
          ? "Revisa peticiones de clientes, valida documentacion y convierte solicitudes listas."
          : "Consulta tus peticiones y crea nuevas solicitudes cuando necesites iniciar un tramite."
      }
      count={`${solicitudes.length} ${solicitudes.length === 1 ? "solicitud" : "solicitudes"}`}
      action={
        !isAdmin ? (
          <Link className="primary-button primary-button--compact" to="/cliente/solicitudes/nuevo">
            <Plus size={16} />
            Nueva solicitud
          </Link>
        ) : null
      }
    >
      <ListFiltersBar
        catalogs={catalogsQuery.data}
        filters={draftFilters}
        showClientFilter={isAdmin}
        onChange={(nextFilters) => {
          setDraftFilters(nextFilters);
          applyFilters(nextFilters);
        }}
        onSubmit={() => applyFilters(draftFilters)}
        onClear={() => {
          setDraftFilters({});
          setAppliedFilters({});
        }}
      />

      <div className="records-panel">
        <div className="records-panel__heading">
          <div>
            <h3>Solicitudes encontradas</h3>
            <span className="records-loading-state">{solicitudesQuery.isFetching ? "Actualizando" : "\u00A0"}</span>
          </div>
        </div>

        {solicitudesQuery.error ? <ErrorState error={solicitudesQuery.error} /> : null}
        {solicitudesQuery.isLoading ? <ListSkeleton /> : null}
        {!solicitudesQuery.isLoading && !solicitudesQuery.error ? (
          <SolicitudesTable
            solicitudes={solicitudes}
            catalogs={catalogsQuery.data}
            filters={draftFilters}
            isAdmin={isAdmin}
            showClient={isAdmin}
            onFilterChange={(nextFilters) => {
              setDraftFilters(nextFilters);
              applyFilters(nextFilters);
            }}
          />
        ) : null}
      </div>
    </ListPageChrome>
  );
}

function SolicitudesTable({
  solicitudes,
  catalogs,
  filters,
  isAdmin,
  showClient,
  onFilterChange,
}: {
  solicitudes: SolicitudListItem[];
  catalogs?: ListCatalogs;
  filters: ListFilters;
  isAdmin: boolean;
  showClient: boolean;
  onFilterChange: (filters: ListFilters) => void;
}) {
  function nextFilter(key: keyof ListFilters, value: string) {
    onFilterChange({ ...filters, [key]: value });
  }

  return (
    <div className="records-table-scroll">
      <table className="records-table">
        <thead>
          <tr>
            <th className="records-col-id">
              <span>N.</span>
            </th>
            <th className="records-col-kind">
              <span>Solicitud</span>
              <select
                className="records-table-filter"
                value={filters.tipoTramiteId || ""}
                onChange={(event) => nextFilter("tipoTramiteId", event.target.value)}
              >
                <option value="">Todos los tipos</option>
                {catalogs?.tiposTramite.map((tipo) => (
                  <option key={tipo.id} value={tipo.id}>
                    {tipo.nombre}
                  </option>
                ))}
              </select>
            </th>
            {showClient ? (
              <th className="records-col-client">
                <span>Cliente</span>
                <select
                  className="records-table-filter"
                  value={filters.clienteId || ""}
                  onChange={(event) => nextFilter("clienteId", event.target.value)}
                >
                  <option value="">Todos los clientes</option>
                  {catalogs?.clientes.map((cliente) => (
                    <option key={cliente.id} value={cliente.id}>
                      {cliente.nombre}
                    </option>
                  ))}
                </select>
              </th>
            ) : null}
            <th className="records-col-plate">
              <span>Matricula</span>
              <input
                className="records-table-filter"
                value={filters.matricula || ""}
                onChange={(event) => nextFilter("matricula", uppercaseInput(event.target.value))}
                placeholder="Buscar"
              />
            </th>
            <th className="records-col-status">
              <span>Estado</span>
              <select className="records-table-filter" value={filters.estado || ""} onChange={(event) => nextFilter("estado", event.target.value)}>
                <option value="">Todos los estados</option>
                {catalogs?.estados.map((estado) => (
                  <option key={estado} value={estado}>
                    {formatEnum(estado)}
                  </option>
                ))}
              </select>
            </th>
            <th className="records-col-date">Ultima actividad</th>
            {showClient ? <th className="records-col-change">Ultimo cambio</th> : null}
            <th className="records-col-actions">Acciones</th>
          </tr>
        </thead>
        <tbody>
          {solicitudes.length === 0 ? (
            <tr>
              <td colSpan={showClient ? 8 : 6}>
                <EmptyState title="No hay solicitudes con estos filtros" copy="Cambia el periodo o borra parte de la matricula para ampliar la busqueda." />
              </td>
            </tr>
          ) : null}
          {solicitudes.map((solicitud) => (
            <tr key={solicitud.id}>
              <td className="records-col-id">
                <span className="record-id">{solicitud.id}</span>
              </td>
              <td className="records-col-kind">
                <strong>{solicitud.tipoTramite || "Sin tipo"}</strong>
                <small>{solicitud.expedienteId ? "Convertida en expediente" : "Pendiente de gestion"}</small>
              </td>
              {showClient ? (
                <td className="records-col-client">
                  <strong>{solicitud.cliente?.nombre || "Sin cliente"}</strong>
                  <small>{solicitud.cliente?.email || solicitud.cliente?.nif || ""}</small>
                </td>
              ) : null}
              <td className="records-col-plate">
                <MiniPlate value={solicitud.matricula} />
              </td>
              <td className="records-col-status">
                <StatusBadge tone={statusTone(solicitud.estado)}>{formatEnum(solicitud.estado)}</StatusBadge>
              </td>
              <td className="records-col-date">{fechaReferencia(solicitud) || "Sin fecha"}</td>
              {showClient ? (
                <td className="records-col-change">
                  <span>{solicitud.fechaUltimaModificacion || "Sin cambios"}</span>
                  <small>{solicitud.modificadoPor?.nombreCompleto || "Sin cambios"}</small>
                </td>
              ) : null}
              <td className="records-col-actions">
                {solicitud.expedienteId ? (
                  <Link className="soft-button soft-button--compact" to={isAdmin ? `/expedientes/${solicitud.expedienteId}` : `/cliente/expedientes/${solicitud.expedienteId}`}>
                    <Eye size={16} />
                    Ver expediente
                  </Link>
                ) : (
                  <Link className="soft-button soft-button--compact" to={`/solicitudes/${solicitud.id}`}>
                    <Eye size={16} />
                    Ver detalle
                  </Link>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function readFilters(searchParams: URLSearchParams): ListFilters {
  return {
    periodo: searchParams.get("periodo") || "ESTE_MES",
    estado: searchParams.get("estado") || "",
    tipoTramiteId: searchParams.get("tipoTramiteId") || "",
    clienteId: searchParams.get("clienteId") || "",
    matricula: searchParams.get("matricula") || "",
  };
}

function MiniPlate({ value }: { value?: string | null }) {
  if (!value) {
    return <span className="muted-text">Sin matricula</span>;
  }
  return (
    <span className="mini-plate-react">
      <span>E</span>
      <strong>{value}</strong>
    </span>
  );
}

function ErrorState({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.message : "No se ha podido cargar el listado.";
  return <div className="records-empty records-empty--danger">{message}</div>;
}

function EmptyState({ title, copy }: { title: string; copy: string }) {
  return (
    <div className="records-empty">
      <ClipboardCheck size={28} />
      <div>
        <strong>{title}</strong>
        <p>{copy}</p>
      </div>
    </div>
  );
}

function ListSkeleton() {
  return (
    <div className="records-skeleton">
      <span />
      <span />
      <span />
    </div>
  );
}

function statusTone(status?: string | null) {
  if (status === "FINALIZADO" || status === "CONVERTIDA") return "success";
  if (status === "INCIDENCIA" || status === "PENDIENTE_DOCUMENTACION") return "danger";
  if (status === "REVISANDO_INCIDENCIAS" || status === "ENVIADO_DGT") return "info";
  if (status === "EN_TRAMITE" || status === "PENDIENTE_REVISION") return "warning";
  return "neutral";
}

function fechaReferencia(item: { fechaCreacion?: string | null; fechaUltimaModificacion?: string | null }) {
  return item.fechaUltimaModificacion || item.fechaCreacion;
}

function formatEnum(value?: string | null) {
  return value ? value.replaceAll("_", " ") : "Sin estado";
}
