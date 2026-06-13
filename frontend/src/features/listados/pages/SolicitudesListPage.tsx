import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import { ArrowRight, ClipboardCheck, Plus } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { ApiError } from "../../../shared/api/http";
import { getSolicitudListCatalogs, getSolicitudes } from "../services/listadosApi";
import { ListFiltersBar } from "../components/ListFiltersBar";
import { ListPageChrome } from "../components/ListPageChrome";
import type { ListCatalogs, ListFilters, SolicitudListItem } from "../types";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { uppercaseInput } from "../../../shared/utils/text";
import { PaginationBar } from "../components/PaginationBar";

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
    setAppliedFilters({ ...filters, pagina: filters.pagina || "0", tamanio: filters.tamanio || "25" });
  }

  const pageData = solicitudesQuery.data;
  const solicitudes = pageData?.contenido ?? [];

  return (
    <ListPageChrome
      eyebrow={isAdmin ? "Gestión interna" : "Portal cliente"}
      title={isAdmin ? "Solicitudes" : "Mis solicitudes"}
      summary={
        isAdmin
          ? "Revisa peticiones de clientes, valida documentacion y convierte solicitudes listas."
          : "Consulta tus peticiones y crea nuevas solicitudes cuando necesites iniciar un tramite."
      }
      count={`${pageData?.totalElementos ?? 0} ${(pageData?.totalElementos ?? 0) === 1 ? "solicitud" : "solicitudes"}`}
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
        compact
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

      <div className="records-panel records-panel--ledger">
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
        <PaginationBar page={pageData?.pagina ?? 0} totalPages={pageData?.totalPaginas ?? 0} totalItems={pageData?.totalElementos ?? 0} pageSize={pageData?.tamanio ?? 25} onPageChange={(pagina) => applyFilters({ ...draftFilters, pagina: String(pagina) })} onPageSizeChange={(tamanio) => applyFilters({ ...draftFilters, pagina: "0", tamanio: String(tamanio) })} />
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

  const columnCount = 8 + (showClient ? 1 : 0);

  return (
    <div className="records-table-scroll">
      <table className="records-table records-table--solicitudes">
        <thead>
          <tr>
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
            <th className="records-col-interested">Interesados</th>
            <th className="records-col-document-status">Situacion documental</th>
            <th className="records-col-next-action">Siguiente actuacion</th>
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
            {showClient ? <th className="records-col-change">Ultima modificacion</th> : <th className="records-col-date">Ultima actividad</th>}
            <th aria-label="Acciones" className="records-col-actions" />
          </tr>
        </thead>
        <tbody>
          {solicitudes.length === 0 ? (
            <tr>
              <td colSpan={columnCount}>
                <EmptyState title="No hay solicitudes con estos filtros" copy="Cambia el periodo o borra parte de la matricula para ampliar la busqueda." />
              </td>
            </tr>
          ) : null}
          {solicitudes.map((solicitud) => (
            <tr key={solicitud.id}>
              <td className="records-col-kind">
                <strong className="records-solicitud-id">SOL-{solicitud.id}</strong>
                <small>{solicitud.tipoTramite || "Sin tipo"}</small>
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
              <td className="records-col-interested">
                <SolicitudInterestedParties interesados={solicitud.interesados} />
              </td>
              <td className="records-col-document-status">
                <span className={`records-document-status records-document-status--${documentStatusTone(solicitud.situacionDocumental)}`}>
                  {solicitud.situacionDocumental || "SIN INFORMACION"}
                </span>
              </td>
              <td className="records-col-next-action">
                <span className="records-next-action">{nextSolicitudAction(solicitud, isAdmin)}</span>
              </td>
              <td className="records-col-status">
                <StatusBadge tone={statusTone(solicitud.estado)}>{formatSolicitudStatus(solicitud.estado)}</StatusBadge>
              </td>
              {showClient ? (
                <td className="records-col-change">
                  <span>{fechaReferencia(solicitud) || "Sin cambios"}</span>
                  <small>{solicitud.modificadoPor?.nombreCompleto || "Sin cambios"}</small>
                </td>
              ) : (
                <td className="records-col-date">{fechaReferencia(solicitud) || "Sin fecha"}</td>
              )}
              <td className="records-col-actions">
                <Link aria-label="Ver solicitud" className="icon-button" title="Ver solicitud" to={`/solicitudes/${solicitud.id}`}>
                  <ArrowRight size={16} />
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
    fechaDesde: searchParams.get("fechaDesde") || "",
    fechaHasta: searchParams.get("fechaHasta") || "",
    pagina: searchParams.get("pagina") || "0",
    tamanio: searchParams.get("tamanio") || "25",
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
  return <strong className="records-plate-value">{value}</strong>;
}

function SolicitudInterestedParties({ interesados }: { interesados?: SolicitudListItem["interesados"] }) {
  if (!interesados?.length) return <span className="muted-text">Sin interesados</span>;
  const ordered = interesados
    .map((interesado, index) => ({ interesado, index }))
    .sort((a, b) => interestedRolePriority(a.interesado.rol) - interestedRolePriority(b.interesado.rol) || a.index - b.index)
    .map(({ interesado }) => interesado);
  const visible = ordered.slice(0, 2);
  return (
    <div className="records-interested-list">
      {visible.map((interesado, index) => (
        <span key={`${interesado.dni || interesado.nombre || "interesado"}-${index}`}>
          <strong>{[interesado.nombre, interesado.apellidos].filter(Boolean).join(" ") || "Sin nombre"}</strong>
          <small>{[interesado.dni, interesado.rol ? formatEnum(interesado.rol) : null].filter(Boolean).join(" · ")}</small>
        </span>
      ))}
      {ordered.length > visible.length ? <em>+{ordered.length - visible.length} mas</em> : null}
    </div>
  );
}

function interestedRolePriority(role?: string | null) {
  if (role === "COMPRAVENTA") return 0;
  if (role === "COMPRADOR" || role === "TITULAR") return 1;
  if (role === "VENDEDOR") return 2;
  return 3;
}

function nextSolicitudAction(solicitud: SolicitudListItem, isAdmin: boolean) {
  if (solicitud.expedienteId || solicitud.estado === "CONVERTIDA") return "Sin acciones pendientes";
  if (solicitud.situacionDocumental === "SIN DOCUMENTACION" || solicitud.situacionDocumental?.startsWith("FALTA")) {
    return isAdmin ? "Esperar documentacion" : "Aportar documentacion";
  }
  switch (solicitud.estado) {
    case "PENDIENTE_DOCUMENTACION":
      return isAdmin ? "Esperar documentacion" : "Aportar documentacion";
    case "REVISANDO_INCIDENCIAS":
      return isAdmin ? "Revisar documentacion" : "Documentacion en revision";
    case "PENDIENTE_REVISION":
      return isAdmin ? "Revisar solicitud" : "Esperar revision";
    case "RECHAZADO":
      return "Consultar resolucion";
    default:
      return "Consultar solicitud";
  }
}

function documentStatusTone(value?: string | null) {
  if (value === "SIN DOCUMENTACION" || value?.startsWith("FALTA")) return "danger";
  if (value === "DOCUMENTACION COMPLETA") return "success";
  return "neutral";
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
  if (status === "INCIDENCIA" || status === "PENDIENTE_DOCUMENTACION" || status === "RECHAZADO") return "danger";
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

function formatSolicitudStatus(value?: string | null) {
  switch (value) {
    case "PENDIENTE_REVISION":
      return "PENDIENTE DE REVISION";
    case "PENDIENTE_DOCUMENTACION":
      return "PENDIENTE DOCUMENTACION";
    case "REVISANDO_INCIDENCIAS":
      return "REVISANDO DOCUMENTACION";
    case "CONVERTIDA":
      return "CONVERTIDA";
    case "RECHAZADO":
      return "RECHAZADA";
    default:
      return formatEnum(value);
  }
}
