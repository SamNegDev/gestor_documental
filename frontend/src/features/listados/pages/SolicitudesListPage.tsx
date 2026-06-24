import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import { ArrowRight, CheckCircle2, ClipboardCheck, MailCheck, Plus, RefreshCw, Trash2 } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { ApiError } from "../../../shared/api/http";
import { bulkConvertSolicitudes, checkInboundSolicitudEmail, deleteSolicitud, getSolicitudListCatalogs, getSolicitudes } from "../services/listadosApi";
import { ListFiltersBar } from "../components/ListFiltersBar";
import { ListPageChrome } from "../components/ListPageChrome";
import type { ListCatalogs, ListFilters, SolicitudListItem } from "../types";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { uppercaseInput } from "../../../shared/utils/text";
import { PaginationBar } from "../components/PaginationBar";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";

export function SolicitudesListPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [appliedFilters, setAppliedFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const [draftFilters, setDraftFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [checkingEmail, setCheckingEmail] = useState(false);
  const [deletingSolicitudId, setDeletingSolicitudId] = useState<number | null>(null);
  const { confirm, dialog } = useConfirmDialog();
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
    setSelectedIds(new Set());
  }

  const pageData = solicitudesQuery.data;
  const solicitudes = pageData?.contenido ?? [];
  const selectedSolicitudes = solicitudes.filter((solicitud) => selectedIds.has(solicitud.id));
  const convertibleSelected = selectedSolicitudes.filter(canConvertSolicitud);

  async function handleBulkConvert() {
    if (selectedSolicitudes.length === 0) return;
    const confirmed = await confirm({
      title: "Convertir solicitudes",
      description: `Se intentara convertir ${selectedSolicitudes.length} solicitudes seleccionadas. Las que ya no sean convertibles quedaran sin cambios.`,
      confirmLabel: "Convertir posibles",
      tone: "success",
    });
    if (!confirmed) return;
    try {
      const response = await bulkConvertSolicitudes(selectedSolicitudes.map((solicitud) => solicitud.id));
      setSelectedIds(new Set());
      await queryClient.invalidateQueries({ queryKey: ["solicitudes"] });
      await queryClient.invalidateQueries({ queryKey: ["expedientes"] });
      if (response.fallidas > 0) {
        const failed = response.resultados
          .filter((result) => !result.convertida)
          .slice(0, 4)
          .map((result) => `SOL-${result.solicitudId}: ${result.mensaje || "No convertida"}`)
          .join("\n");
        alert(`${response.convertidas} convertidas y ${response.fallidas} no convertidas.\n${failed}`);
      }
    } catch (cause) {
      alert(cause instanceof Error ? cause.message : "No se pudieron convertir las solicitudes seleccionadas.");
    }
  }

  async function handleCheckInboundEmail() {
    setCheckingEmail(true);
    try {
      const response = await checkInboundSolicitudEmail();
      await queryClient.invalidateQueries({ queryKey: ["solicitudes"] });
      await queryClient.invalidateQueries({ queryKey: ["tareas"] });
      if (!response.ejecutada) {
        alert(response.mensaje);
      }
    } catch (cause) {
      alert(cause instanceof Error ? cause.message : "No se pudo comprobar el buzon.");
    } finally {
      setCheckingEmail(false);
    }
  }

  async function handleDeleteSolicitud(solicitud: SolicitudListItem) {
    const confirmed = await confirm({
      title: "Eliminar solicitud",
      description: `Se eliminara la solicitud SOL-${solicitud.id}${solicitud.matricula ? ` (${solicitud.matricula})` : ""}. Esta operacion no se puede deshacer.`,
      confirmLabel: "Eliminar",
      tone: "danger",
    });
    if (!confirmed) return;
    setDeletingSolicitudId(solicitud.id);
    try {
      await deleteSolicitud(solicitud.id);
      setSelectedIds((current) => {
        const next = new Set(current);
        next.delete(solicitud.id);
        return next;
      });
      await queryClient.invalidateQueries({ queryKey: ["solicitudes"] });
      await queryClient.invalidateQueries({ queryKey: ["tareas"] });
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || cause.message : "No se pudo eliminar la solicitud.");
    } finally {
      setDeletingSolicitudId(null);
    }
  }

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
        isAdmin ? (
          <button className="soft-button soft-button--compact" disabled={checkingEmail} onClick={handleCheckInboundEmail} type="button">
            {checkingEmail ? <RefreshCw size={16} /> : <MailCheck size={16} />}
            {checkingEmail ? "Comprobando" : "Comprobar buzon"}
          </button>
        ) : (
          <>
            <Link className="soft-button soft-button--compact" to="/cliente/solicitudes/creacion-multiple">
              <Plus size={16} />
              Creacion multiple
            </Link>
            <Link className="primary-button primary-button--compact" to="/cliente/solicitudes/nuevo">
              <Plus size={16} />
              Nueva solicitud
            </Link>
          </>
        )
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
          const resetFilters = { periodo: "ESTE_MES", archivo: "ACTIVAS", pagina: "0", tamanio: "25" };
          setDraftFilters(resetFilters);
          setAppliedFilters(resetFilters);
        }}
        additionalFilter={
          <label>
            <span>Vista</span>
            <select
              value={draftFilters.archivo || "ACTIVAS"}
              onChange={(event) => {
                const nextFilters = { ...draftFilters, archivo: event.target.value, pagina: "0" };
                setDraftFilters(nextFilters);
                applyFilters(nextFilters);
              }}
            >
              <option value="ACTIVAS">Activas</option>
              <option value="ARCHIVADAS">Archivadas</option>
              <option value="TODAS">Todas</option>
            </select>
          </label>
        }
      />

      <div className="records-panel records-panel--ledger">
        <div className="records-panel__heading">
          <div>
            <h3>Solicitudes encontradas</h3>
            <span className="records-loading-state">{solicitudesQuery.isFetching ? "Actualizando" : "\u00A0"}</span>
          </div>
        </div>

        {solicitudesQuery.error ? <ErrorState error={solicitudesQuery.error} /> : null}
        {isAdmin && selectedSolicitudes.length > 0 ? (
          <SolicitudBulkActionsBar
            convertibleCount={convertibleSelected.length}
            selectedCount={selectedSolicitudes.length}
            onClear={() => setSelectedIds(new Set())}
            onConvert={handleBulkConvert}
          />
        ) : null}
        {solicitudesQuery.isLoading ? <ListSkeleton /> : null}
        {!solicitudesQuery.isLoading && !solicitudesQuery.error ? (
          <SolicitudesTable
            solicitudes={solicitudes}
            catalogs={catalogsQuery.data}
            filters={draftFilters}
            isAdmin={isAdmin}
            selectedIds={selectedIds}
            showClient={isAdmin}
            onFilterChange={(nextFilters) => {
              setDraftFilters(nextFilters);
              applyFilters(nextFilters);
            }}
            onSelectionChange={setSelectedIds}
            onDelete={handleDeleteSolicitud}
            deletingId={deletingSolicitudId}
          />
        ) : null}
        <PaginationBar page={pageData?.pagina ?? 0} totalPages={pageData?.totalPaginas ?? 0} totalItems={pageData?.totalElementos ?? 0} pageSize={pageData?.tamanio ?? 25} onPageChange={(pagina) => applyFilters({ ...draftFilters, pagina: String(pagina) })} onPageSizeChange={(tamanio) => applyFilters({ ...draftFilters, pagina: "0", tamanio: String(tamanio) })} />
      </div>
      {dialog}
    </ListPageChrome>
  );
}

function SolicitudesTable({
  solicitudes,
  catalogs,
  filters,
  isAdmin,
  selectedIds,
  showClient,
  onFilterChange,
  onSelectionChange,
  onDelete,
  deletingId,
}: {
  solicitudes: SolicitudListItem[];
  catalogs?: ListCatalogs;
  filters: ListFilters;
  isAdmin: boolean;
  selectedIds: Set<number>;
  showClient: boolean;
  onFilterChange: (filters: ListFilters) => void;
  onSelectionChange: (ids: Set<number>) => void;
  onDelete: (solicitud: SolicitudListItem) => void;
  deletingId: number | null;
}) {
  function nextFilter(key: keyof ListFilters, value: string) {
    onFilterChange({ ...filters, [key]: value });
  }

  const allVisibleSelected = solicitudes.length > 0 && solicitudes.every((solicitud) => selectedIds.has(solicitud.id));
  const columnCount = 8 + (showClient ? 1 : 0) + (isAdmin ? 1 : 0);

  function toggleAllVisible(checked: boolean) {
    const next = new Set(selectedIds);
    solicitudes.forEach((solicitud) => {
      if (checked) {
        next.add(solicitud.id);
      } else {
        next.delete(solicitud.id);
      }
    });
    onSelectionChange(next);
  }

  function toggleOne(id: number, checked: boolean) {
    const next = new Set(selectedIds);
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    onSelectionChange(next);
  }

  return (
    <div className="records-table-scroll">
      <table className="records-table records-table--solicitudes">
        <thead>
          <tr>
            {isAdmin ? (
              <th className="records-col-select">
                <input
                  aria-label="Seleccionar solicitudes visibles"
                  checked={allVisibleSelected}
                  onChange={(event) => toggleAllVisible(event.target.checked)}
                  type="checkbox"
                />
              </th>
            ) : null}
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
            <tr className={selectedIds.has(solicitud.id) ? "records-row--selected" : ""} key={solicitud.id}>
              {isAdmin ? (
                <td className="records-col-select">
                  <input
                    aria-label={`Seleccionar solicitud ${solicitud.id}`}
                    checked={selectedIds.has(solicitud.id)}
                    onChange={(event) => toggleOne(solicitud.id, event.target.checked)}
                    type="checkbox"
                  />
                </td>
              ) : null}
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
                {isAdmin ? (
                  <button
                    aria-label="Eliminar solicitud"
                    className="icon-button icon-button--danger"
                    disabled={deletingId === solicitud.id || solicitud.estado === "CONVERTIDA" || Boolean(solicitud.expedienteId)}
                    title="Eliminar solicitud"
                    type="button"
                    onClick={() => onDelete(solicitud)}
                  >
                    <Trash2 size={16} />
                  </button>
                ) : null}
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
    archivo: searchParams.get("archivo") || "ACTIVAS",
  };
}

function MiniPlate({ value }: { value?: string | null }) {
  if (!value) {
    return <span className="muted-text">Sin matricula</span>;
  }
  return <strong className="records-plate-value">{value}</strong>;
}

function SolicitudBulkActionsBar({
  convertibleCount,
  selectedCount,
  onClear,
  onConvert,
}: {
  convertibleCount: number;
  selectedCount: number;
  onClear: () => void;
  onConvert: () => void;
}) {
  return (
    <div className="bulk-actions-bar">
      <div>
        <strong>{selectedCount} seleccionadas</strong>
        <span>
          {convertibleCount > 0
            ? `${convertibleCount} parecen convertibles; el servidor validara cada solicitud`
            : "No hay solicitudes convertibles en la seleccion"}
        </span>
      </div>
      <div className="bulk-actions-bar__actions">
        <button className="soft-button soft-button--compact" onClick={onClear} type="button">
          Limpiar
        </button>
        <button className="primary-button primary-button--compact" disabled={convertibleCount === 0} onClick={onConvert} type="button">
          <CheckCircle2 size={15} />
          Convertir lote
        </button>
      </div>
    </div>
  );
}

function canConvertSolicitud(solicitud: SolicitudListItem) {
  return !solicitud.expedienteId && solicitud.estado !== "CONVERTIDA" && solicitud.estado !== "RECHAZADO";
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
          <strong>{interesado.nombre || "Sin nombre"}</strong>
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
  if (value === "ARCHIVADA") return "neutral";
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
