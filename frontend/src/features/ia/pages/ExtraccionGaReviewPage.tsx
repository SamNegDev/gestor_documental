import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  Building2,
  CalendarDays,
  CarFront,
  CheckCircle2,
  CircleAlert,
  DatabaseZap,
  FileSearch,
  FileText,
  IdCard,
  Loader2,
  MapPin,
  Plus,
  ReceiptText,
  RefreshCcw,
  Save,
  Send,
  Sparkles,
  UserRound,
} from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import {
  getExtraccionGaPreview,
  getExtraccionGaRevision,
  guardarExtraccionGaRevision,
  probarExtraccionGaMultiple,
  sincronizarExtraccionGaRevision,
} from "../services/extraccionGaApi";
import type { EstadoRevisionGa, ExtraccionGaPreview } from "../types";

type JsonRecord = Record<string, unknown>;

const MODEL = "gpt-5.4-mini";

const PERSON_ROLES = [
  {
    key: "transmitente",
    title: "Transmitente",
    subtitle: "Vendedor o titular anterior",
    icon: Building2,
  },
  {
    key: "adquirente",
    title: "Adquirente",
    subtitle: "Comprador o nuevo titular",
    icon: UserRound,
  },
] as const;

const ADDRESS_FIELDS = [
  { label: "Tipo via", path: ["direccion", "siglas"], compact: true },
  { label: "Via", path: ["direccion", "nombreVia"] },
  { label: "Numero", path: ["direccion", "numero"], compact: true },
  { label: "Piso", path: ["direccion", "piso"], compact: true },
  { label: "Puerta", path: ["direccion", "puerta"], compact: true },
  { label: "C.P.", path: ["direccion", "codigoPostal"], compact: true },
  { label: "Municipio", path: ["direccion", "municipio"] },
  { label: "Localidad", path: ["direccion", "pueblo"] },
  { label: "Provincia", path: ["direccion", "provincia"], compact: true },
  { label: "Pais", path: ["direccion", "pais"], compact: true },
];

const VEHICLE_FIELDS = [
  { label: "Matricula", path: ["matricula"], required: true, compact: true },
  { label: "Bastidor", path: ["numeroBastidor"], required: true },
  { label: "Marca", path: ["marca"], required: true, compact: true },
  { label: "Modelo", path: ["modelo"], required: true },
  { label: "Fecha matriculacion", path: ["fechaMatriculacion"], required: true, compact: true },
  { label: "1a matriculacion", path: ["fechaPrimeraMatriculacion"], compact: true },
  { label: "Cilindrada", path: ["cilindrada"], compact: true },
  { label: "Potencia", path: ["potencia"], compact: true },
  { label: "Carburante", path: ["carburante"], compact: true },
  { label: "ITV", path: ["fechaItv"], compact: true },
  { label: "Clase", path: ["claseVehiculo"], compact: true },
  { label: "Tipo", path: ["tipoVehiculo"], compact: true },
];

const TAX_FIELDS = [
  { label: "Fecha contrato", section: "acreditacion", path: ["fechaContrato"], compact: true },
  { label: "Contrato compraventa", section: "acreditacion", path: ["contratoCompraventa"], compact: true },
  { label: "Modelo 620", section: "acreditacion", path: ["modeloItp"], compact: true },
  { label: "Fecha devengo", section: "impuestos", path: ["fechaDevengo"], compact: true },
  { label: "Valor declarado", section: "impuestos", path: ["valorDeclarado"], compact: true },
  { label: "Base imponible", section: "impuestos", path: ["baseImponible"], compact: true },
  { label: "Cuota", section: "impuestos", path: ["cuotaTributaria"], compact: true },
  { label: "Total", section: "impuestos", path: ["totalIngresar"], compact: true },
] as const;

const EXTRACTION_STEPS = [
  { at: 0, label: "Preparando documentos relevantes" },
  { at: 18, label: "Leyendo contrato y factura" },
  { at: 45, label: "Procesando DNI y representantes" },
  { at: 80, label: "Revisando permiso y ficha tecnica" },
  { at: 120, label: "Cotejando modelo 620 e impuestos" },
  { at: 160, label: "Consolidando interesados y vehiculo" },
];

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("es-ES", { minimumFractionDigits: 3, maximumFractionDigits: 3 }).format(value);
}

function formatBytes(value: number) {
  if (!value) return "0 KB";
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
  return `${(value / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}

function parseResultado(resultadoJson?: string | null) {
  if (!resultadoJson) return null;
  try {
    return JSON.parse(resultadoJson) as JsonRecord;
  } catch {
    return null;
  }
}

function asRecord(value: unknown): JsonRecord | null {
  return Boolean(value && typeof value === "object" && !Array.isArray(value)) ? value as JsonRecord : null;
}

function cloneRecord(value: JsonRecord) {
  return JSON.parse(JSON.stringify(value)) as JsonRecord;
}

function isExtractionField(value: unknown): value is JsonRecord {
  return Boolean(value && typeof value === "object" && !Array.isArray(value) && "valor" in value);
}

function getPathValue(data: JsonRecord | null, path: string[]) {
  let cursor: unknown = data;
  for (const part of path) {
    cursor = asRecord(cursor)?.[part];
  }
  return cursor;
}

function valueToInput(value: unknown) {
  if (isExtractionField(value)) return value.valor == null ? "" : String(value.valor);
  if (value === null || value === undefined) return "";
  if (typeof value === "object") return "";
  return String(value);
}

function setExtractedValue(data: JsonRecord, path: string[], value: string) {
  const next = cloneRecord(data);
  let cursor: JsonRecord = next;
  path.slice(0, -1).forEach((part) => {
    const current = cursor[part];
    if (!current || typeof current !== "object" || Array.isArray(current)) {
      cursor[part] = {};
    }
    cursor = cursor[part] as JsonRecord;
  });
  const key = path[path.length - 1];
  const current = cursor[key];
  if (isExtractionField(current)) {
    cursor[key] = { ...current, valor: value, fuente: current.fuente || "MANUAL", observacion: current.observacion || null };
  } else {
    cursor[key] = { valor: value, confianza: null, fuente: "MANUAL", observacion: "Editado manualmente" };
  }
  return next;
}

function getConsolidatedPrefix(data: JsonRecord | null) {
  const bloque = asRecord(data?.bloqueConsolidacion);
  const resultado = asRecord(bloque?.resultado);
  return resultado ? ["bloqueConsolidacion", "resultado"] : [];
}

function getConsolidatedData(data: JsonRecord | null) {
  const prefix = getConsolidatedPrefix(data);
  return prefix.length ? asRecord(getPathValue(data, prefix)) : data;
}

function readValue(data: JsonRecord | null, section: string, path: string[]) {
  const consolidado = getConsolidatedData(data);
  return valueToInput(getPathValue(consolidado, [section, ...path]));
}

function updateValue(data: JsonRecord, section: string, path: string[], value: string) {
  return setExtractedValue(data, [...getConsolidatedPrefix(data), section, ...path], value);
}

function normalizeDocument(value: string) {
  return value.trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function isCompanyDocument(value: string) {
  return /^[ABCDEFGHJKLMNPQRSUVW]/.test(normalizeDocument(value));
}

function isCompanySection(data: JsonRecord | null, section: string) {
  return isCompanyDocument(readValue(data, section, ["dni"]));
}

function personDisplayName(data: JsonRecord | null, section: string) {
  const documentValue = readValue(data, section, ["dni"]);
  const company = isCompanyDocument(documentValue);
  const razonSocial = readValue(data, section, ["razonSocial"]);
  const nombreCompleto = readValue(data, section, ["nombreCompleto"]);
  const nombre = readValue(data, section, ["nombre"]);
  const apellido1 = readValue(data, section, ["apellido1"]);
  const apellido2 = readValue(data, section, ["apellido2"]);
  if (company) return razonSocial || nombreCompleto || nombre;
  return [nombre, apellido1, apellido2].filter(Boolean).join(" ") || nombreCompleto;
}

function statusLabel(status?: EstadoRevisionGa | null) {
  if (status === "PREPARADO_EXPORTACION") return "Preparado";
  if (status === "EXPORTADO") return "Exportado";
  return "Borrador";
}

function getResultControl(data: JsonRecord | null) {
  const directControl = asRecord(data?.control);
  if (directControl) return directControl;
  const consolidation = asRecord(data?.bloqueConsolidacion);
  const consolidationResult = asRecord(consolidation?.resultado);
  return asRecord(consolidationResult?.control);
}

function confidenceLabel(value: unknown) {
  if (typeof value !== "number") return "Sin confianza";
  return `${Math.round(value * 100)}%`;
}

function confidenceClass(value: unknown) {
  if (typeof value !== "number") return "ga-confidence--unknown";
  if (value >= 0.85) return "ga-confidence--high";
  if (value >= 0.65) return "ga-confidence--medium";
  return "ga-confidence--low";
}

function mutationErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.details || `La API devolvio HTTP ${error.status}.`;
  if (error instanceof Error) return error.message || fallback;
  return fallback;
}

function estimateProgress(seconds: number) {
  if (seconds <= 0) return 8;
  return Math.min(92, Math.round(8 + Math.log10(seconds + 1) * 44));
}

function currentExtractionStep(seconds: number) {
  return EXTRACTION_STEPS.reduce((current, step) => seconds >= step.at ? step : current, EXTRACTION_STEPS[0]);
}

function formatElapsed(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function hasSectionValues(data: JsonRecord | null, section: string) {
  const sectionData = asRecord(getConsolidatedData(data)?.[section]);
  if (!sectionData) return false;
  return JSON.stringify(sectionData).replace(/"valor":null/g, "").replace(/"valor":""/g, "").length > 40;
}

function missingKeyFields(data: JsonRecord | null) {
  if (!data) return [];
  const missing: string[] = [];
  const required = [
    { label: "Documento del transmitente", section: "transmitente", path: ["dni"] },
    { label: "Nombre/razon del transmitente", section: "transmitente", path: ["nombre"] },
    { label: "Documento del adquirente", section: "adquirente", path: ["dni"] },
    { label: "Nombre/razon del adquirente", section: "adquirente", path: ["nombre"] },
    { label: "Domicilio del adquirente", section: "adquirente", path: ["direccion", "nombreVia"] },
    { label: "Matricula", section: "vehiculo", path: ["matricula"] },
    { label: "Bastidor", section: "vehiculo", path: ["numeroBastidor"] },
    { label: "Marca", section: "vehiculo", path: ["marca"] },
    { label: "Modelo", section: "vehiculo", path: ["modelo"] },
    { label: "Fecha de matriculacion", section: "vehiculo", path: ["fechaMatriculacion"] },
  ];
  required.forEach((item) => {
    const value = item.path[0] === "nombre"
      ? personDisplayName(data, item.section)
      : readValue(data, item.section, item.path);
    if (!value.trim()) missing.push(item.label);
  });
  if (!isCompanySection(data, "adquirente") && !readValue(data, "adquirente", ["fechaNacimiento"]).trim()) {
    missing.push("Nacimiento del adquirente");
  }
  return missing;
}

function FieldInput({
  data,
  disabled,
  label,
  onChange,
  path,
  required,
  section,
  compact,
}: {
  data: JsonRecord | null;
  disabled?: boolean;
  label: string;
  onChange: (section: string, path: string[], value: string) => void;
  path: string[];
  required?: boolean;
  section: string;
  compact?: boolean;
}) {
  const value = readValue(data, section, path);
  const emptyRequired = required && !value.trim();
  return (
    <label className={`ga-field ${compact ? "ga-field--compact" : ""} ${emptyRequired ? "ga-field--missing" : ""}`}>
      <span>{label}</span>
      <input disabled={disabled} value={value} onChange={(event) => onChange(section, path, event.target.value)} />
    </label>
  );
}

function PersonEditor({
  data,
  disabled,
  icon: Icon,
  onChange,
  section,
  subtitle,
  title,
}: {
  data: JsonRecord | null;
  disabled?: boolean;
  icon: typeof UserRound;
  onChange: (section: string, path: string[], value: string) => void;
  section: string;
  subtitle: string;
  title: string;
}) {
  const documentValue = readValue(data, section, ["dni"]);
  const company = isCompanyDocument(documentValue);
  const displayName = personDisplayName(data, section);
  const name = company
    ? readValue(data, section, ["razonSocial"]) || readValue(data, section, ["nombreCompleto"]) || readValue(data, section, ["nombre"])
    : readValue(data, section, ["nombre"]);

  const updateName = (value: string) => {
    onChange(section, [company ? "razonSocial" : "nombre"], value);
    if (company) onChange(section, ["nombreCompleto"], value);
  };

  return (
    <section className="ga-card ga-person-card">
      <div className="ga-card__heading">
        <div>
          <span className="ga-card__icon"><Icon size={18} /></span>
          <p className="eyebrow">{subtitle}</p>
          <h3>{title}</h3>
        </div>
        <strong>{displayName || "Pendiente"}</strong>
      </div>

      <div className="ga-form-grid">
        <FieldInput compact data={data} disabled={disabled} label="DNI / CIF" onChange={onChange} path={["dni"]} required section={section} />
        <label className={`ga-field ${!name.trim() ? "ga-field--missing" : ""}`}>
          <span>{company ? "Razon social" : "Nombre"}</span>
          <input disabled={disabled} value={name} onChange={(event) => updateName(event.target.value)} />
        </label>
        {!company ? (
          <>
            <FieldInput compact data={data} disabled={disabled} label="Apellido 1" onChange={onChange} path={["apellido1"]} section={section} />
            <FieldInput compact data={data} disabled={disabled} label="Apellido 2" onChange={onChange} path={["apellido2"]} section={section} />
          </>
        ) : null}
        {!company ? (
          <>
            <FieldInput compact data={data} disabled={disabled} label="Nacimiento" onChange={onChange} path={["fechaNacimiento"]} required={section === "adquirente"} section={section} />
            <label className="ga-field ga-field--compact">
              <span>Sexo</span>
              <select disabled={disabled} value={readValue(data, section, ["sexo"])} onChange={(event) => onChange(section, ["sexo"], event.target.value)}>
                <option value="">Sin dato</option>
                <option value="H">Hombre</option>
                <option value="M">Mujer</option>
              </select>
            </label>
          </>
        ) : null}
      </div>

      <div className="ga-subsection">
        <div className="ga-subsection__title">
          <MapPin size={16} />
          <span>Domicilio</span>
        </div>
        <div className="ga-form-grid ga-form-grid--address">
          {ADDRESS_FIELDS.map((field) => (
            <FieldInput
              compact={field.compact}
              data={data}
              disabled={disabled}
              key={`${section}-${field.path.join(".")}`}
              label={field.label}
              onChange={onChange}
              path={field.path}
              required={section === "adquirente" && ["nombreVia", "codigoPostal", "municipio"].includes(field.path[field.path.length - 1])}
              section={section}
            />
          ))}
        </div>
      </div>
    </section>
  );
}

function VehicleEditor({
  data,
  disabled,
  onChange,
}: {
  data: JsonRecord | null;
  disabled?: boolean;
  onChange: (section: string, path: string[], value: string) => void;
}) {
  const fecha = readValue(data, "vehiculo", ["fechaPrimeraMatriculacion"]) || readValue(data, "vehiculo", ["fechaMatriculacion"]);
  const year = fecha.match(/\d{4}/)?.[0] || "";
  return (
    <section className="ga-card">
      <div className="ga-card__heading">
        <div>
          <span className="ga-card__icon"><CarFront size={18} /></span>
          <p className="eyebrow">Vehiculo</p>
          <h3>{readValue(data, "vehiculo", ["matricula"]) || "Datos del coche"}</h3>
        </div>
        <strong>{year ? `Ano fabricacion ${year}` : "Ano pendiente"}</strong>
      </div>
      <div className="ga-form-grid">
        {VEHICLE_FIELDS.map((field) => (
          <FieldInput
            compact={field.compact}
            data={data}
            disabled={disabled}
            key={field.path.join(".")}
            label={field.label}
            onChange={onChange}
            path={field.path}
            required={field.required}
            section="vehiculo"
          />
        ))}
      </div>
    </section>
  );
}

function TaxEditor({
  data,
  disabled,
  onChange,
}: {
  data: JsonRecord | null;
  disabled?: boolean;
  onChange: (section: string, path: string[], value: string) => void;
}) {
  return (
    <section className="ga-card">
      <div className="ga-card__heading">
        <div>
          <span className="ga-card__icon"><ReceiptText size={18} /></span>
          <p className="eyebrow">Contrato e impuesto</p>
          <h3>Datos auxiliares del tramite</h3>
        </div>
        <strong>Sin tasa manual</strong>
      </div>
      <div className="ga-form-grid">
        {TAX_FIELDS.map((field) => (
          <FieldInput
            compact={field.compact}
            data={data}
            disabled={disabled}
            key={`${field.section}-${field.path.join(".")}`}
            label={field.label}
            onChange={onChange}
            path={[...field.path]}
            section={field.section}
          />
        ))}
      </div>
    </section>
  );
}

function PreviewSummary({ preview }: { preview: ExtraccionGaPreview }) {
  return (
    <section className="ga-review-summary">
      <article>
        <FileText size={17} />
        <span>Documentos</span>
        <strong>{preview.documentosRelevantes}</strong>
      </article>
      <article>
        <CalendarDays size={17} />
        <span>Paginas leidas</span>
        <strong>{preview.paginasRelevantes}</strong>
      </article>
      <article>
        <ReceiptText size={17} />
        <span>Coste estimado</span>
        <strong>${formatMoney(preview.costeEstimadoMinUsd)} - ${formatMoney(preview.costeEstimadoMaxUsd)}</strong>
      </article>
      <article className={preview.apiKeyConfigurada ? "is-ok" : "is-danger"}>
        {preview.apiKeyConfigurada ? <BadgeCheck size={17} /> : <AlertCircle size={17} />}
        <span>OpenAI</span>
        <strong>{preview.apiKeyConfigurada ? "Lista" : "Sin clave"}</strong>
      </article>
    </section>
  );
}

function ExtractionProgressModal({ elapsedSeconds }: { elapsedSeconds: number }) {
  const progress = estimateProgress(elapsedSeconds);
  const currentStep = currentExtractionStep(elapsedSeconds);
  return (
    <div className="ga-progress-modal" role="alertdialog" aria-modal="true" aria-labelledby="ga-progress-title">
      <section className="ga-progress-modal__panel">
        <div className="ga-progress-modal__heading">
          <span className="ga-progress-modal__spinner"><Loader2 size={22} /></span>
          <div>
            <p className="eyebrow">Extraccion IA</p>
            <h3 id="ga-progress-title">Procesando documentacion</h3>
          </div>
          <strong>{formatElapsed(elapsedSeconds)}</strong>
        </div>

        <div className="ga-progress-modal__bar" aria-label={`Progreso estimado ${progress}%`}>
          <span style={{ width: `${progress}%` }} />
        </div>

        <div className="ga-progress-modal__status">
          <strong>{currentStep.label}</strong>
          <span>{progress}% estimado</span>
        </div>

        <ol className="ga-progress-modal__steps">
          {EXTRACTION_STEPS.map((step) => (
            <li className={elapsedSeconds >= step.at ? "is-active" : ""} key={step.label}>
              <span />
              {step.label}
            </li>
          ))}
        </ol>

        <p>Puede tardar varios minutos. Deja esta pantalla abierta hasta que aparezcan los campos para revisar.</p>
      </section>
    </div>
  );
}

export function ExtraccionGaReviewPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [editableResult, setEditableResult] = useState<JsonRecord | null>(null);
  const [hasLocalChanges, setHasLocalChanges] = useState(false);
  const [showRepresentative, setShowRepresentative] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const previewQuery = useQuery({
    queryKey: ["extraccion-ga", "preview", id],
    queryFn: () => getExtraccionGaPreview(id || ""),
    enabled: Boolean(id),
  });
  const revisionQuery = useQuery({
    queryKey: ["extraccion-ga", "revision", id],
    queryFn: () => getExtraccionGaRevision(id || ""),
    enabled: Boolean(id),
  });
  const extractionMutation = useMutation({
    mutationFn: () => probarExtraccionGaMultiple(id || "", { modelo: MODEL, ejecutar: true }),
  });
  const saveRevisionMutation = useMutation({
    mutationFn: (estado: EstadoRevisionGa) => {
      if (!editableResult) throw new Error("No hay datos para guardar");
      return guardarExtraccionGaRevision(id || "", {
        resultadoIaJson: extractionMutation.data?.resultadoJson || revisionQuery.data?.resultadoIaJson || null,
        datosValidadosJson: JSON.stringify(editableResult),
        modelo: MODEL,
        estado,
      });
    },
    onSuccess: async () => {
      setHasLocalChanges(false);
      await queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "revision", id] });
      await queryClient.invalidateQueries({ queryKey: ["extraccion-ga", "preparadas"] });
    },
  });
  const syncMutation = useMutation({
    mutationFn: () => sincronizarExtraccionGaRevision(id || ""),
  });

  const parsedResult = useMemo(() => parseResultado(extractionMutation.data?.resultadoJson), [extractionMutation.data?.resultadoJson]);
  const savedResult = useMemo(() => parseResultado(revisionQuery.data?.datosValidadosJson), [revisionQuery.data?.datosValidadosJson]);
  useEffect(() => {
    if (parsedResult) {
      setEditableResult(parsedResult);
      setHasLocalChanges(false);
      setShowRepresentative(hasSectionValues(parsedResult, "representanteTransmitente"));
      return;
    }
    if (savedResult) {
      setEditableResult(savedResult);
      setHasLocalChanges(false);
      setShowRepresentative(hasSectionValues(savedResult, "representanteTransmitente"));
    }
  }, [parsedResult, savedResult]);

  useEffect(() => {
    if (!extractionMutation.isPending) {
      setElapsedSeconds(0);
      return;
    }
    const interval = window.setInterval(() => setElapsedSeconds((current) => current + 1), 1000);
    return () => window.clearInterval(interval);
  }, [extractionMutation.isPending]);

  const control = getResultControl(editableResult);
  const missing = missingKeyFields(editableResult);
  const revision = revisionQuery.data || null;
  const effectivePreview = extractionMutation.data?.preview || previewQuery.data;
  const extractionError = mutationErrorMessage(
    extractionMutation.error,
    "Revisa la configuracion de OpenAI o intenta de nuevo.",
  );
  const saveError = mutationErrorMessage(saveRevisionMutation.error, "Revisa los datos editados e intenta de nuevo.");
  const syncError = mutationErrorMessage(syncMutation.error, "Guarda primero la revision validada e intenta de nuevo.");
  const isSaving = saveRevisionMutation.isPending;
  const disabled = isSaving || syncMutation.isPending || revision?.estado === "EXPORTADO";
  const canPersist = Boolean(editableResult);
  const canSync = Boolean(revision && !hasLocalChanges && !isSaving);
  const bloqueosDocumentales = effectivePreview?.bloqueosDocumentales ?? [];
  const extraccionDisponible = effectivePreview?.extraccionDisponible ?? true;

  const updateFieldValue = (section: string, path: string[], value: string) => {
    setEditableResult((current) => current ? updateValue(current, section, path, value) : current);
    setHasLocalChanges(true);
  };

  if (previewQuery.isLoading) {
    return (
      <div className="records-empty">
        <Loader2 size={22} />
        Cargando preparacion de extraccion...
      </div>
    );
  }

  if (previewQuery.error || !effectivePreview) {
    return (
      <div className="records-empty records-empty--danger">
        <AlertCircle size={22} />
        No se pudo cargar la preparacion de extraccion.
      </div>
    );
  }

  return (
    <main className="ga-review-page">
      <header className="ga-review-header">
        <div>
          <Link className="soft-button soft-button--compact" to={`/expedientes/${effectivePreview.expedienteId}`}>
            <ArrowLeft size={15} />
            Expediente
          </Link>
          <p className="eyebrow">Revision GA</p>
          <h2>{effectivePreview.matricula || `Expediente ${effectivePreview.expedienteId}`}</h2>
          <p>Valida interesados y vehiculo. El sistema prepara el XML y los datos tecnicos por detras.</p>
        </div>
        <div className="ga-review-header__actions">
          <button className="soft-button soft-button--compact" onClick={() => previewQuery.refetch()} type="button">
            <RefreshCcw size={15} />
            Refrescar
          </button>
          <button
            className="primary-button primary-button--compact"
            disabled={!effectivePreview.apiKeyConfigurada || !extraccionDisponible || extractionMutation.isPending}
            onClick={() => extractionMutation.mutate()}
            type="button"
          >
            {extractionMutation.isPending ? <Loader2 size={16} /> : <Sparkles size={16} />}
            {extractionMutation.isPending ? "Extrayendo..." : "Extraer datos"}
          </button>
        </div>
      </header>

      <PreviewSummary preview={effectivePreview} />

      {!extraccionDisponible ? (
        <section className="ia-alert ia-alert--warning">
          <CircleAlert size={18} />
          <div>
            <strong>Extraccion bloqueada por documentacion pendiente.</strong>
            <p>Completa los requisitos del expediente y refresca esta pantalla antes de lanzar IA.</p>
            <ul className="ia-alert__list">
              {bloqueosDocumentales.slice(0, 6).map((bloqueo) => (
                <li key={bloqueo}>{bloqueo}</li>
              ))}
              {bloqueosDocumentales.length > 6 ? <li>Y {bloqueosDocumentales.length - 6} mas</li> : null}
            </ul>
          </div>
        </section>
      ) : null}

      {extractionMutation.isPending ? <ExtractionProgressModal elapsedSeconds={elapsedSeconds} /> : null}

      {extractionMutation.isPending ? (
        <section className="ia-alert ia-alert--info">
          <Loader2 size={18} />
          <div>
            <strong>Extrayendo datos con IA.</strong>
            <p>Puede tardar varios minutos porque se leen contrato, DNI, permiso, ficha tecnica y modelo 620 si existe. Deja esta pantalla abierta hasta que termine.</p>
          </div>
        </section>
      ) : null}

      {extractionMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo extraer la informacion.</strong>
            <p>{extractionError}</p>
          </div>
        </section>
      ) : null}

      {editableResult ? (
        <section className="ga-review-state">
          <div>
            <span className={`ia-status ia-status--${revision?.estado || "BORRADOR"}`}>{statusLabel(revision?.estado)}</span>
            <strong>{hasLocalChanges ? "Cambios pendientes de guardar" : revision ? "Datos guardados" : "Pendiente de guardar"}</strong>
          </div>
          <div className={`ga-confidence ${confidenceClass(control?.confianzaGlobal)}`}>
            <CheckCircle2 size={16} />
            <span>Confianza {confidenceLabel(control?.confianzaGlobal)}</span>
          </div>
          {missing.length ? (
            <div className="ga-missing-summary">
              <CircleAlert size={16} />
              <span>Faltan {missing.length}: {missing.slice(0, 4).join(", ")}{missing.length > 4 ? "..." : ""}</span>
            </div>
          ) : (
            <div className="ga-ready-summary">
              <BadgeCheck size={16} />
              <span>Campos clave completos</span>
            </div>
          )}
        </section>
      ) : (
        <section className="ga-empty-review">
          <FileSearch size={22} />
          <div>
            <strong>Sin lectura guardada</strong>
            <p>Ejecuta la extraccion para cargar una ficha editable con interesados y vehiculo.</p>
          </div>
        </section>
      )}

      {saveRevisionMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo guardar.</strong>
            <p>{saveError}</p>
          </div>
        </section>
      ) : null}

      {syncMutation.error ? (
        <section className="ia-alert ia-alert--danger">
          <AlertCircle size={18} />
          <div>
            <strong>No se pudo sincronizar con el expediente.</strong>
            <p>{syncError}</p>
          </div>
        </section>
      ) : null}

      {syncMutation.data ? (
        <section className="ia-alert ia-alert--success">
          <DatabaseZap size={18} />
          <div>
            <strong>{syncMutation.data.mensaje}</strong>
            <p>
              Interesados: {syncMutation.data.interesadosCreados} creados, {syncMutation.data.interesadosActualizados} completados, {syncMutation.data.relacionesCreadas} vinculados.
              Vehiculos: {syncMutation.data.vehiculosCreados} creados, {syncMutation.data.vehiculosActualizados} completados.
            </p>
          </div>
        </section>
      ) : null}

      <section className="ga-review-main">
        <div className="ga-review-main__left">
          {PERSON_ROLES.map((role) => (
            <PersonEditor
              data={editableResult}
              disabled={disabled || !editableResult}
              icon={role.icon}
              key={role.key}
              onChange={updateFieldValue}
              section={role.key}
              subtitle={role.subtitle}
              title={role.title}
            />
          ))}

          {showRepresentative ? (
            <PersonEditor
              data={editableResult}
              disabled={disabled || !editableResult}
              icon={IdCard}
              onChange={updateFieldValue}
              section="representanteTransmitente"
              subtitle="Empresa transmitente"
              title="Administrador / representante"
            />
          ) : editableResult ? (
            <button className="ga-add-representative" type="button" onClick={() => setShowRepresentative(true)}>
              <Plus size={16} />
              Anadir representante del transmitente
            </button>
          ) : null}
        </div>

        <div className="ga-review-main__right">
          <VehicleEditor data={editableResult} disabled={disabled || !editableResult} onChange={updateFieldValue} />
          <TaxEditor data={editableResult} disabled={disabled || !editableResult} onChange={updateFieldValue} />

          <details className="ga-documents-panel">
            <summary>
              <FileText size={16} />
              Documentacion usada
            </summary>
            <div className="ga-documents-panel__list">
              {effectivePreview.documentos.map((documento) => (
                <article key={documento.id}>
                  <span>{documento.tipoDocumento}</span>
                  <strong>{documento.nombreArchivoOriginal}</strong>
                  <small>{documento.paginas} pag. · {formatBytes(documento.tamanoBytes)}</small>
                </article>
              ))}
            </div>
          </details>
        </div>
      </section>

      <section className="ga-review-savebar">
        <div>
          <strong>{revision?.fechaPreparado ? `Preparado el ${new Date(revision.fechaPreparado).toLocaleString("es-ES")}` : "Guardar validacion"}</strong>
          <small>La tasa queda vacia en el XML y la fecha de presentacion se genera al exportar.</small>
        </div>
        <div>
          <button className="soft-button soft-button--compact" disabled={!canPersist || isSaving} onClick={() => saveRevisionMutation.mutate("BORRADOR")} type="button">
            {isSaving && saveRevisionMutation.variables === "BORRADOR" ? <Loader2 size={16} /> : <Save size={16} />}
            Guardar
          </button>
          <button className="primary-button primary-button--compact" disabled={!canPersist || isSaving} onClick={() => saveRevisionMutation.mutate("PREPARADO_EXPORTACION")} type="button">
            {isSaving && saveRevisionMutation.variables === "PREPARADO_EXPORTACION" ? <Loader2 size={16} /> : <Send size={16} />}
            Preparar exportacion
          </button>
          <button className="soft-button soft-button--compact" disabled={!canSync || syncMutation.isPending} onClick={() => syncMutation.mutate()} type="button">
            {syncMutation.isPending ? <Loader2 size={16} /> : <DatabaseZap size={16} />}
            Sincronizar expediente
          </button>
        </div>
      </section>
    </main>
  );
}
