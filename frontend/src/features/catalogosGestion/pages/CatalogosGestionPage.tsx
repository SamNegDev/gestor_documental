import { useDeferredValue, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CarFront, CheckCircle2, DatabaseSearch, FileUp, Loader2, RefreshCcw, Search, ShieldCheck, UserRound, UsersRound } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import {
  buscarPersonasCatalogo,
  buscarRepresentantesCatalogo,
  buscarVehiculosCatalogo,
  getCatalogoGestionResumen,
  importarCatalogoGestion,
} from "../services/catalogosGestionApi";
import type { CatalogoGestionKind, GestionPersonaCatalogo, GestionRepresentanteCatalogo, GestionVehiculoCatalogo, ImportacionCatalogo } from "../types";

const IMPORTERS: Array<{
  key: CatalogoGestionKind;
  title: string;
  file: string;
  icon: typeof UserRound;
  hint: string;
}> = [
  { key: "personas", title: "Personas", file: "02_PERSONAS_NORMALIZADAS.csv", icon: UserRound, hint: "Identidad, domicilios, nacimiento y contacto." },
  { key: "representantes", title: "Representantes", file: "05_PERSONAS_REPRESENTANTES.csv", icon: UsersRound, hint: "Relaciones empresa-representante ya cruzadas." },
  { key: "vehiculos", title: "Vehiculos", file: "06_VEHICULOS_CATALOGO_IMPORT.csv", icon: CarFront, hint: "Ficha tecnica historica por matricula." },
];

const SEARCH_TABS: Array<{ key: CatalogoGestionKind; label: string; placeholder: string }> = [
  { key: "personas", label: "Personas", placeholder: "Buscar NIF, nombre o razon social" },
  { key: "representantes", label: "Representantes", placeholder: "Buscar empresa, representante o NIF" },
  { key: "vehiculos", label: "Vehiculos", placeholder: "Buscar matricula, bastidor, marca o modelo" },
];

function formatNumber(value?: number | null) {
  return new Intl.NumberFormat("es-ES").format(value ?? 0);
}

function errorText(error: unknown) {
  return error instanceof ApiError ? error.details || "No se pudo completar la operacion." : "No se pudo completar la operacion.";
}

function compactName(persona: Pick<GestionPersonaCatalogo, "apellido1RazonSocial" | "apellido2" | "nombre">) {
  return [persona.apellido1RazonSocial, persona.apellido2, persona.nombre].filter(Boolean).join(" ") || "Sin nombre";
}

function compactAddress(parts: Array<string | null | undefined>) {
  return parts.filter(Boolean).join(", ") || "Sin domicilio";
}

function useCatalogSearch(kind: CatalogoGestionKind, query: string) {
  return useQuery({
    queryKey: ["catalogos-gestion", "buscar", kind, query],
    queryFn: () => {
      if (kind === "personas") return buscarPersonasCatalogo(query);
      if (kind === "representantes") return buscarRepresentantesCatalogo(query);
      return buscarVehiculosCatalogo(query);
    },
  });
}

export function CatalogosGestionPage() {
  const queryClient = useQueryClient();
  const [files, setFiles] = useState<Partial<Record<CatalogoGestionKind, File>>>({});
  const [lastImport, setLastImport] = useState<ImportacionCatalogo | null>(null);
  const [activeTab, setActiveTab] = useState<CatalogoGestionKind>("personas");
  const [search, setSearch] = useState("");
  const deferredSearch = useDeferredValue(search);

  const resumenQuery = useQuery({
    queryKey: ["catalogos-gestion", "resumen"],
    queryFn: getCatalogoGestionResumen,
  });

  const importMutation = useMutation({
    mutationFn: ({ tipo, archivo }: { tipo: CatalogoGestionKind; archivo: File }) => importarCatalogoGestion(tipo, archivo),
    onSuccess: async (response) => {
      setLastImport(response);
      setFiles((current) => ({ ...current, [response.tipo as CatalogoGestionKind]: undefined }));
      await queryClient.invalidateQueries({ queryKey: ["catalogos-gestion"] });
    },
  });

  const searchQuery = useCatalogSearch(activeTab, deferredSearch);
  const activeMeta = SEARCH_TABS.find((tab) => tab.key === activeTab) || SEARCH_TABS[0];
  const resumen = resumenQuery.data;
  const isImporting = importMutation.isPending;

  const totals = useMemo(() => [
    { label: "Personas", value: resumen?.personas, ready: resumen?.personasDisponibles, icon: UserRound },
    { label: "Representantes", value: resumen?.representantes, ready: resumen?.representantesDisponibles, icon: UsersRound },
    { label: "Vehiculos", value: resumen?.vehiculos, ready: resumen?.vehiculosDisponibles, icon: CarFront },
  ], [resumen]);

  const handleImport = (tipo: CatalogoGestionKind) => {
    const archivo = files[tipo];
    if (!archivo || isImporting) return;
    importMutation.mutate({ tipo, archivo });
  };

  return (
    <main className="records-page catalog-page">
      <header className="records-header catalog-header">
        <div>
          <p className="eyebrow">Fuentes auxiliares IA</p>
          <h2>Catalogos Gestion Trafico</h2>
          <p>Importa historicos como fuente de consulta para la extraccion GA. Estos datos no modifican clientes, interesados ni vehiculos operativos.</p>
        </div>
        <div className="records-header__actions">
          <button className="soft-button soft-button--compact" type="button" onClick={() => resumenQuery.refetch()}>
            <RefreshCcw size={15} />
            Refrescar
          </button>
        </div>
      </header>

      <section className="catalog-summary" aria-label="Estado de catalogos">
        {totals.map((item) => {
          const Icon = item.icon;
          return (
            <article className={item.ready ? "catalog-summary__item is-ready" : "catalog-summary__item"} key={item.label}>
              <Icon size={19} />
              <span>{item.label}</span>
              <strong>{resumenQuery.isLoading ? "..." : formatNumber(item.value)}</strong>
              <small>{item.ready ? "Disponible" : "Pendiente"}</small>
            </article>
          );
        })}
      </section>

      <section className="catalog-upload-grid" aria-label="Importacion de catalogos">
        {IMPORTERS.map((item) => {
          const Icon = item.icon;
          const selected = files[item.key];
          const pendingThis = isImporting && importMutation.variables?.tipo === item.key;
          return (
            <article className="catalog-import" key={item.key}>
              <div className="catalog-import__icon"><Icon size={19} /></div>
              <div className="catalog-import__body">
                <div>
                  <h3>{item.title}</h3>
                  <p>{item.hint}</p>
                  <code>{item.file}</code>
                </div>
                <label className="catalog-file">
                  <FileUp size={16} />
                  <span>{selected ? selected.name : "Seleccionar CSV"}</span>
                  <input
                    accept=".csv,text/csv"
                    type="file"
                    onChange={(event) => setFiles((current) => ({ ...current, [item.key]: event.target.files?.[0] }))}
                  />
                </label>
              </div>
              <button
                className="primary-button primary-button--compact"
                type="button"
                disabled={!selected || isImporting}
                onClick={() => handleImport(item.key)}
              >
                {pendingThis ? <Loader2 size={16} /> : <DatabaseSearch size={16} />}
                Importar
              </button>
            </article>
          );
        })}
      </section>

      {importMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo importar el catalogo.</strong>
            <p>{errorText(importMutation.error)}</p>
          </div>
        </section>
      ) : null}

      {lastImport ? (
        <section className="ia-alert ia-alert--success">
          <CheckCircle2 size={18} />
          <div>
            <strong>{lastImport.mensaje}</strong>
            <p>{formatNumber(lastImport.registrosImportados)} importados, {formatNumber(lastImport.registrosOmitidos)} omitidos. La importacion reemplaza el catalogo anterior de ese tipo.</p>
          </div>
        </section>
      ) : null}

      <section className="records-panel catalog-search-panel">
        <div className="records-panel__heading">
          <div>
            <h3>Consulta de fuente auxiliar</h3>
            <span>Resultados limitados para revisar coincidencias antes de usarlas en IA.</span>
          </div>
          <ShieldCheck size={20} />
        </div>
        <div className="catalog-toolbar">
          <div className="task-tabs" role="tablist" aria-label="Tipo de catalogo">
            {SEARCH_TABS.map((tab) => (
              <button
                className={activeTab === tab.key ? "is-active" : ""}
                key={tab.key}
                type="button"
                onClick={() => {
                  setActiveTab(tab.key);
                  setSearch("");
                }}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <label className="registry-search catalog-search">
            <Search size={16} />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={activeMeta.placeholder} />
          </label>
        </div>
        <CatalogResults kind={activeTab} data={searchQuery.data || []} loading={searchQuery.isLoading || searchQuery.isFetching} error={searchQuery.error} />
      </section>
    </main>
  );
}

function CatalogResults({ kind, data, loading, error }: { kind: CatalogoGestionKind; data: unknown[]; loading: boolean; error: unknown }) {
  if (loading) {
    return <div className="records-empty records-empty--compact"><Loader2 size={18} /> Buscando en catalogo...</div>;
  }
  if (error) {
    return <div className="records-empty records-empty--danger records-empty--compact"><AlertCircle size={18} /> No se pudo consultar el catalogo.</div>;
  }
  if (!data.length) {
    return <div className="records-empty records-empty--compact">Sin resultados en la fuente auxiliar.</div>;
  }
  if (kind === "personas") return <PersonasResults data={data as GestionPersonaCatalogo[]} />;
  if (kind === "representantes") return <RepresentantesResults data={data as GestionRepresentanteCatalogo[]} />;
  return <VehiculosResults data={data as GestionVehiculoCatalogo[]} />;
}

function PersonasResults({ data }: { data: GestionPersonaCatalogo[] }) {
  return (
    <div className="catalog-result-list">
      {data.map((item) => (
        <article className="catalog-result-row" key={item.id}>
          <span className="catalog-result-row__mark"><UserRound size={16} /></span>
          <div>
            <strong>{compactName(item)}</strong>
            <small>{item.nifNormalizado || item.nif || "Sin NIF"} · {item.tipoPersonaSugerido || "Sin tipo"}</small>
          </div>
          <div>
            <span>{compactAddress([item.dirSiglas, item.dirCalle, item.dirNumero, item.dirPiso, item.dirPuerta])}</span>
            <small>{[item.dirCp, item.dirMunicipio, item.dirProvincia].filter(Boolean).join(" · ") || "Sin localidad"}</small>
          </div>
          <div>
            <span>{item.fechaNacimiento || "Sin nacimiento"}</span>
            <small>{item.telefonoMovil || item.telefono || item.email || "Sin contacto"}</small>
          </div>
        </article>
      ))}
    </div>
  );
}

function RepresentantesResults({ data }: { data: GestionRepresentanteCatalogo[] }) {
  return (
    <div className="catalog-result-list">
      {data.map((item) => (
        <article className="catalog-result-row catalog-result-row--representative" key={item.id}>
          <span className="catalog-result-row__mark"><UsersRound size={16} /></span>
          <div>
            <strong>{item.empresaApellido1RazonSocial || "Empresa sin nombre"}</strong>
            <small>{item.empresaNifNormalizado || item.empresaNif || "Sin CIF"}</small>
          </div>
          <div>
            <span>{[item.representanteApellido1RazonSocial, item.representanteApellido2, item.representanteNombre].filter(Boolean).join(" ") || "Representante sin nombre"}</span>
            <small>{item.representanteNifNormalizado || item.representanteNif || "Sin DNI"} · {item.reprConcepto || "Representante"}</small>
          </div>
          <div>
            <span>{compactAddress([item.representanteDirSiglas, item.representanteDirCalle, item.representanteDirNumero, item.representanteDirPiso, item.representanteDirPuerta])}</span>
            <small>{[item.representanteDirCp, item.representanteDirMunicipio, item.representanteDirProvincia].filter(Boolean).join(" · ") || "Sin localidad"}</small>
          </div>
        </article>
      ))}
    </div>
  );
}

function VehiculosResults({ data }: { data: GestionVehiculoCatalogo[] }) {
  return (
    <div className="catalog-result-list">
      {data.map((item) => (
        <article className="catalog-result-row catalog-result-row--vehicle" key={item.id}>
          <span className="catalog-result-row__mark"><CarFront size={16} /></span>
          <div>
            <strong className="registry-plate-inline">{item.matriculaNormalizada || item.matricula || "SIN MATRICULA"}</strong>
            <small>{item.bastidor || "Sin bastidor"}</small>
          </div>
          <div>
            <span>{[item.marca, item.modeloSugerido].filter(Boolean).join(" ") || "Sin modelo"}</span>
            <small>{item.tipo620Descripcion || "Sin tipo 620"} · {item.carburanteDescripcion || item.carburanteCodigo || "Sin carburante"}</small>
          </div>
          <div>
            <span>1ª matricula: {item.fechaPrimeraMatriculacion || "Sin dato"}</span>
            <small>{item.anyoFabricacion ? `Fabricacion ${item.anyoFabricacion}` : "Sin año"} · ITV {item.fechaItv || "sin dato"}</small>
          </div>
          <div>
            <span>{item.cilindrada ? `${item.cilindrada} cc` : "Sin cc"}</span>
            <small>{item.potencia ? `${item.potencia} kW` : "Sin potencia"}</small>
          </div>
        </article>
      ))}
    </div>
  );
}
