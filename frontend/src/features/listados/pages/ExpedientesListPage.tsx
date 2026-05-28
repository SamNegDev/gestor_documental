import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import { Eye, FilePlus2, FolderOpen } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { ApiError } from "../../../shared/api/http";
import { getExpedienteListCatalogs, getExpedientes } from "../services/listadosApi";
import { ListFiltersBar } from "../components/ListFiltersBar";
import { ListPageChrome } from "../components/ListPageChrome";
import type { ExpedienteListItem, ListCatalogs, ListFilters } from "../types";
import type { AppOutletContext } from "../../../app/shell/AppLayout";

export function ExpedientesListPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const [searchParams] = useSearchParams();
  const [appliedFilters, setAppliedFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const [draftFilters, setDraftFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const isAdmin = user?.rol === "ADMIN";

  const catalogsQuery = useQuery({
    queryKey: ["expedientes", "catalogos-listado"],
    queryFn: getExpedienteListCatalogs,
  });

  const expedientesQuery = useQuery({
    queryKey: ["expedientes", "listado", appliedFilters],
    queryFn: () => getExpedientes(appliedFilters),
    placeholderData: (previousData) => previousData,
  });

  function applyFilters(filters: ListFilters) {
    setAppliedFilters(filters);
  }

  const expedientes = expedientesQuery.data ?? [];

  return (
    <ListPageChrome
      eyebrow={isAdmin ? "Gestion interna" : "Portal cliente"}
      title={isAdmin ? "Expedientes" : "Mis expedientes"}
      summary={isAdmin ? "Supervisa expedientes por cliente, estado, tramite y matricula." : "Consulta el avance de tus tramites y accede al detalle documental."}
      count={`${expedientes.length} ${expedientes.length === 1 ? "expediente" : "expedientes"}`}
      action={
        isAdmin ? (
          <Link className="primary-button primary-button--compact" to="/expedientes/nuevo">
            <FilePlus2 size={16} />
            Nuevo expediente
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
            <h3>Expedientes encontrados</h3>
            <span className="records-loading-state">{expedientesQuery.isFetching ? "Actualizando" : "\u00A0"}</span>
          </div>
        </div>

        {expedientesQuery.error ? <ErrorState error={expedientesQuery.error} /> : null}
        {expedientesQuery.isLoading ? <ListSkeleton /> : null}
        {!expedientesQuery.isLoading && !expedientesQuery.error ? (
          <ExpedientesTable
            expedientes={expedientes}
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

function ExpedientesTable({
  expedientes,
  catalogs,
  filters,
  isAdmin,
  showClient,
  onFilterChange,
}: {
  expedientes: ExpedienteListItem[];
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
              <span>Expediente</span>
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
                onChange={(event) => nextFilter("matricula", event.target.value)}
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
            <th className="records-col-date">Creacion</th>
            {showClient ? <th className="records-col-change">Ultimo cambio</th> : null}
            <th className="records-col-actions">Acciones</th>
          </tr>
        </thead>
        <tbody>
          {expedientes.length === 0 ? (
            <tr>
              <td colSpan={showClient ? 8 : 6}>
                <EmptyState title="No hay expedientes con estos filtros" copy="Cambia el periodo o borra parte de la matricula para ampliar la busqueda." />
              </td>
            </tr>
          ) : null}
          {expedientes.map((expediente) => (
            <tr key={expediente.id}>
              <td className="records-col-id">
                <span className="record-id">{expediente.id}</span>
              </td>
              <td className="records-col-kind">
                <strong>{expediente.tipoTramite || "Sin tipo"}</strong>
                <small>Expediente administrativo</small>
              </td>
              {showClient ? (
                <td className="records-col-client">
                  <strong>{expediente.cliente?.nombre || "Sin cliente"}</strong>
                  <small>{expediente.cliente?.email || expediente.cliente?.nif || ""}</small>
                </td>
              ) : null}
              <td className="records-col-plate">
                <MiniPlate value={expediente.matricula} />
              </td>
              <td className="records-col-status">
                <StatusBadge tone={statusTone(expediente.estado)}>{formatEnum(expediente.estado)}</StatusBadge>
              </td>
              <td className="records-col-date">{expediente.fechaCreacion || "Sin fecha"}</td>
              {showClient ? (
                <td className="records-col-change">
                  <span>{expediente.fechaUltimaModificacion || "Sin cambios"}</span>
                  <small>{expediente.modificadoPor?.nombreCompleto || "Sin cambios"}</small>
                </td>
              ) : null}
              <td className="records-col-actions">
                <Link className="soft-button soft-button--compact" to={isAdmin ? `/expedientes/${expediente.id}` : `/cliente/expedientes/${expediente.id}`}>
                  <Eye size={16} />
                  {showClient ? "Ver detalle" : "Ver estado"}
                </Link>
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
      <FolderOpen size={28} />
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

function formatEnum(value?: string | null) {
  return value ? value.replaceAll("_", " ") : "Sin estado";
}
