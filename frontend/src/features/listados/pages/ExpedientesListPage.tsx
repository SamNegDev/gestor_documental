import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import { CheckCircle2, Download, Eye, FilePlus2, FolderOpen, Search, UserRoundCheck } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { ApiError } from "../../../shared/api/http";
import { bulkAdvanceExpedientes, bulkFinalDocumentsUrl, getExpedienteListCatalogs, getExpedientes } from "../services/listadosApi";
import { ListFiltersBar } from "../components/ListFiltersBar";
import { ListPageChrome } from "../components/ListPageChrome";
import type { ExpedienteListItem, ListCatalogs, ListFilters } from "../types";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { uppercaseInput } from "../../../shared/utils/text";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { searchInteresados } from "../../expedientes/services/expedienteDetailApi";
import type { InteresadoSearchResult } from "../../expedientes/types/expedienteDetail.types";

export function ExpedientesListPage() {
  const { user } = useOutletContext<AppOutletContext>();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [appliedFilters, setAppliedFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const [draftFilters, setDraftFilters] = useState<ListFilters>(() => readFilters(searchParams));
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const { confirm, dialog } = useConfirmDialog();
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
    setSelectedIds(new Set());
  }

  const expedientes = expedientesQuery.data ?? [];
  const selectedExpedientes = expedientes.filter((expediente) => selectedIds.has(expediente.id));

  async function handleBulkAdvance() {
    const firstAction = selectedExpedientes[0]?.siguienteAccion;
    if (!firstAction) return;
    const samePoint = selectedExpedientes.every((expediente) => sameBulkAction(expediente.siguienteAccion, firstAction));
    if (!samePoint) {
      alert("Selecciona expedientes que esten exactamente en el mismo punto.");
      return;
    }
    const confirmed = await confirm({
      title: "Avance masivo",
      description: `Se aplicara "${firstAction.label || formatEnum(firstAction.tipo)}" a ${selectedExpedientes.length} expedientes.`,
      confirmLabel: "Avanzar",
      tone: "success",
    });
    if (!confirmed) return;
    try {
      await bulkAdvanceExpedientes({
        expedienteIds: selectedExpedientes.map((expediente) => expediente.id),
        accion: firstAction.tipo,
        codigoHito: firstAction.codigoHito,
      });
      setSelectedIds(new Set());
      await queryClient.invalidateQueries({ queryKey: ["expedientes"] });
    } catch (cause) {
      alert(cause instanceof Error ? cause.message : "No se pudo aplicar el avance masivo.");
    }
  }

  async function handleDownloadFinalDocuments() {
    const invalid = selectedExpedientes.some((expediente) => !expediente.justificantesFinalesDisponibles);
    if (invalid) {
      alert("Todos los expedientes seleccionados deben estar finalizados y tener justificante DGT y MODELO 620.");
      return;
    }
    const confirmed = await confirm({
      title: "Descargar justificantes",
      description: `Se descargara un ZIP con justificante DGT y MODELO 620 de ${selectedExpedientes.length} expedientes.`,
      confirmLabel: "Descargar ZIP",
      tone: "default",
    });
    if (!confirmed) return;
    window.location.href = bulkFinalDocumentsUrl(selectedExpedientes.map((expediente) => expediente.id));
  }

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
        compact
        additionalFilter={
          <label className="list-period-toolbar__search">
            <span>Interesado</span>
            <InteresadoListFilter
              value={draftFilters.interesado || ""}
              onChange={(value) => {
                const nextFilters = { ...draftFilters, interesado: value };
                setDraftFilters(nextFilters);
                applyFilters(nextFilters);
              }}
            />
          </label>
        }
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

      <div className="records-panel records-panel--ledger">
        <div className="records-panel__heading">
          <div>
            <h3>Expedientes encontrados</h3>
            <span className="records-loading-state">{expedientesQuery.isFetching ? "Actualizando" : "\u00A0"}</span>
          </div>
        </div>

        {isAdmin && selectedExpedientes.length > 0 ? (
          <BulkActionsBar
            selected={selectedExpedientes}
            onAdvance={handleBulkAdvance}
            onClear={() => setSelectedIds(new Set())}
            onDownloadFinalDocuments={handleDownloadFinalDocuments}
          />
        ) : null}

        {expedientesQuery.error ? <ErrorState error={expedientesQuery.error} /> : null}
        {expedientesQuery.isLoading ? <ListSkeleton /> : null}
        {!expedientesQuery.isLoading && !expedientesQuery.error ? (
          <ExpedientesTable
            expedientes={expedientes}
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
          />
        ) : null}
      </div>
      {dialog}
    </ListPageChrome>
  );
}

function ExpedientesTable({
  expedientes,
  catalogs,
  filters,
  isAdmin,
  selectedIds,
  showClient,
  onFilterChange,
  onSelectionChange,
}: {
  expedientes: ExpedienteListItem[];
  catalogs?: ListCatalogs;
  filters: ListFilters;
  isAdmin: boolean;
  selectedIds: Set<number>;
  showClient: boolean;
  onFilterChange: (filters: ListFilters) => void;
  onSelectionChange: (ids: Set<number>) => void;
}) {
  function nextFilter(key: keyof ListFilters, value: string) {
    onFilterChange({ ...filters, [key]: value });
  }

  const allVisibleSelected = expedientes.length > 0 && expedientes.every((expediente) => selectedIds.has(expediente.id));
  const columnCount = 6 + (showClient ? 1 : 0) + (isAdmin ? 1 : 0) + (!showClient ? 1 : 0);

  function toggleAllVisible(checked: boolean) {
    const next = new Set(selectedIds);
    expedientes.forEach((expediente) => {
      if (checked) {
        next.add(expediente.id);
      } else {
        next.delete(expediente.id);
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
      <table className={`records-table records-table--expedientes${!showClient ? " records-table--client-expedientes" : ""}`}>
        <thead>
          <tr>
            {isAdmin ? (
              <th className="records-col-select">
                <input
                  aria-label="Seleccionar expedientes visibles"
                  checked={allVisibleSelected}
                  onChange={(event) => toggleAllVisible(event.target.checked)}
                  type="checkbox"
                />
              </th>
            ) : null}
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
                onChange={(event) => nextFilter("matricula", uppercaseInput(event.target.value))}
                placeholder="Buscar"
              />
            </th>
            {!showClient ? <th className="records-col-interested">Interesados</th> : null}
            <th className="records-col-phase">Fase actual</th>
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
          {expedientes.length === 0 ? (
            <tr>
              <td colSpan={columnCount}>
                <EmptyState title="No hay expedientes con estos filtros" copy="Cambia el periodo o revisa la matricula o el interesado buscado." />
              </td>
            </tr>
          ) : null}
          {expedientes.map((expediente) => (
            <tr className={selectedIds.has(expediente.id) ? "records-row--selected" : ""} key={expediente.id}>
              {isAdmin ? (
                <td className="records-col-select">
                  <input
                    aria-label={`Seleccionar expediente ${expediente.id}`}
                    checked={selectedIds.has(expediente.id)}
                    onChange={(event) => toggleOne(expediente.id, event.target.checked)}
                    type="checkbox"
                  />
                </td>
              ) : null}
              <td className="records-col-kind">
                <strong className="records-expediente-id">EXP-{expediente.id}</strong>
                <small>{expediente.tipoTramite || "Sin tipo"}</small>
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
              {!showClient ? (
                <td className="records-col-interested">
                  <InterestedParties interesados={expediente.interesados} />
                </td>
              ) : null}
              <td className="records-col-phase">
                <PhaseSummary expediente={expediente} />
              </td>
              <td className="records-col-status" title={formatEnum(expediente.estado)}>
                <StatusBadge tone={statusTone(expediente.estado)}>{formatStatusLabel(expediente.estado)}</StatusBadge>
              </td>
              {showClient ? (
                <td className="records-col-change">
                  <span>{fechaReferencia(expediente) || "Sin cambios"}</span>
                  <small>{expediente.modificadoPor?.nombreCompleto || "Sin cambios"}</small>
                </td>
              ) : (
                <td className="records-col-date">{fechaReferencia(expediente) || "Sin fecha"}</td>
              )}
              <td className="records-col-actions">
                <Link
                  aria-label={`Ver detalle del expediente ${expediente.id}`}
                  className="icon-button"
                  title={showClient ? "Ver detalle" : "Ver estado"}
                  to={isAdmin ? `/expedientes/${expediente.id}` : `/cliente/expedientes/${expediente.id}`}
                >
                  <Eye size={16} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BulkActionsBar({
  selected,
  onAdvance,
  onClear,
  onDownloadFinalDocuments,
}: {
  selected: ExpedienteListItem[];
  onAdvance: () => void;
  onClear: () => void;
  onDownloadFinalDocuments: () => void;
}) {
  const firstAction = selected[0]?.siguienteAccion;
  const samePoint = Boolean(firstAction) && selected.every((expediente) => sameBulkAction(expediente.siguienteAccion, firstAction));
  const allFinalDocs = selected.length > 0 && selected.every((expediente) => expediente.justificantesFinalesDisponibles);

  return (
    <div className="bulk-actions-bar">
      <div>
        <strong>{selected.length} seleccionados</strong>
        <span>
          {samePoint
            ? `Siguiente accion: ${firstAction?.label || formatEnum(firstAction?.tipo)}`
            : "Selecciona expedientes en el mismo punto para avanzar en lote"}
        </span>
      </div>
      <div className="bulk-actions-bar__actions">
        <button className="soft-button soft-button--compact" onClick={onClear} type="button">
          Limpiar
        </button>
        <button className="soft-button soft-button--compact" disabled={!allFinalDocs} onClick={onDownloadFinalDocuments} type="button">
          <Download size={15} />
          Justificantes
        </button>
        <button className="primary-button primary-button--compact" disabled={!samePoint} onClick={onAdvance} type="button">
          <CheckCircle2 size={15} />
          Avanzar lote
        </button>
      </div>
    </div>
  );
}

function sameBulkAction(
  current?: ExpedienteListItem["siguienteAccion"],
  expected?: ExpedienteListItem["siguienteAccion"],
) {
  if (!current || !expected) return false;
  return current.tipo === expected.tipo && (current.codigoHito || "") === (expected.codigoHito || "");
}

function readFilters(searchParams: URLSearchParams): ListFilters {
  return {
    periodo: searchParams.get("periodo") || "ESTE_MES",
    estado: searchParams.get("estado") || "",
    tipoTramiteId: searchParams.get("tipoTramiteId") || "",
    clienteId: searchParams.get("clienteId") || "",
    matricula: searchParams.get("matricula") || "",
    interesado: searchParams.get("interesado") || "",
  };
}

function InteresadoListFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<InteresadoSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const query = value.trim();

  useEffect(() => {
    if (!open || query.length < 2) {
      setResults([]);
      return;
    }
    let active = true;
    const timeout = window.setTimeout(() => {
      setLoading(true);
      searchInteresados(query)
        .then((items) => active && setResults(items))
        .catch(() => active && setResults([]))
        .finally(() => active && setLoading(false));
    }, 220);
    return () => {
      active = false;
      window.clearTimeout(timeout);
    };
  }, [open, query]);

  useEffect(() => {
    const close = (event: PointerEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, []);

  return (
    <div className="records-interested-filter" ref={wrapperRef}>
      <Search size={14} />
      <input
        className="records-table-filter"
        onChange={(event) => {
          onChange(uppercaseInput(event.target.value));
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        placeholder="DNI o nombre"
        value={value}
      />
      {open && query.length >= 2 ? (
        <div className="records-interested-filter__menu">
          {loading ? <span>Buscando...</span> : null}
          {!loading && results.length === 0 ? <span>Sin coincidencias</span> : null}
          {results.map((interesado) => (
            <button
              key={interesado.id}
              onClick={() => {
                onChange(interesado.dni || interesado.nombre || "");
                setOpen(false);
              }}
              type="button"
            >
              <UserRoundCheck size={15} />
              <span>
                <strong>{interesado.nombre || "SIN NOMBRE"}</strong>
                <small>{interesado.dni || "SIN DNI"}</small>
              </span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function PhaseSummary({ expediente }: { expediente: ExpedienteListItem }) {
  return <span className="records-phase-title">{expediente.faseActual || "Fase sin definir"}</span>;
}

function InterestedParties({ interesados }: { interesados?: ExpedienteListItem["interesados"] }) {
  if (!interesados?.length) return <span className="muted-text">Sin interesados</span>;
  const ordered = interesados
    .map((interesado, index) => ({ interesado, index }))
    .sort((a, b) => interestedRolePriority(a.interesado.rol) - interestedRolePriority(b.interesado.rol) || a.index - b.index)
    .map(({ interesado }) => interesado);
  const visible = ordered.slice(0, 2);
  return (
    <div className="records-interested-list">
      {visible.map((interesado) => (
        <span key={`${interesado.id}-${interesado.rol || "sin-rol"}`}>
          <strong>{interesado.nombre || "Sin nombre"}</strong>
          <small>{[interesado.dni, interesado.rol ? formatEnum(interesado.rol) : null].filter(Boolean).join(" · ")}</small>
        </span>
      ))}
      {ordered.length > visible.length ? <em>+{ordered.length - visible.length} mas</em> : null}
    </div>
  );
}

function interestedRolePriority(role?: string | null) {
  switch (role) {
    case "COMPRAVENTA":
      return 0;
    case "COMPRADOR":
    case "TITULAR":
      return 1;
    case "VENDEDOR":
      return 2;
    default:
      return 3;
  }
}

function MiniPlate({ value }: { value?: string | null }) {
  if (!value) {
    return <span className="muted-text">Sin matricula</span>;
  }
  return <strong className="records-plate-value">{value}</strong>;
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
  if (status === "INCIDENCIA" || status === "PENDIENTE_DOCUMENTACION" || status === "SOLICITADA_INFORMACION_ADICIONAL") return "danger";
  if (status === "REVISANDO_INCIDENCIAS" || status === "ENVIADO_DGT" || status === "INFORMACION_ADICIONAL_RECIBIDA") return "info";
  if (status === "EN_TRAMITE" || status === "PENDIENTE_REVISION") return "warning";
  return "neutral";
}

function fechaReferencia(item: { fechaCreacion?: string | null; fechaUltimaModificacion?: string | null }) {
  return item.fechaUltimaModificacion || item.fechaCreacion;
}

function formatEnum(value?: string | null) {
  return value ? value.replaceAll("_", " ") : "Sin estado";
}

function formatStatusLabel(value?: string | null) {
  switch (value) {
    case "EN_TRAMITE":
      return "EN TRAMITE";
    case "SOLICITADA_INFORMACION_ADICIONAL":
      return "PENDIENTE DE RESPUESTA";
    case "PENDIENTE_DOCUMENTACION":
      return "PENDIENTE DOCUMENTACION";
    case "INFORMACION_ADICIONAL_RECIBIDA":
      return "INFORMACION RECIBIDA";
    case "REVISANDO_INCIDENCIAS":
      return "REVISANDO INCIDENCIAS";
    case "ENVIADO_DGT":
      return "ENVIADO DGT";
    case "FINALIZADO":
      return "FINALIZADO";
    default:
      return formatEnum(value);
  }
}
