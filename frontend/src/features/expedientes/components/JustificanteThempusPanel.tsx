import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, Loader2, Send, ShieldCheck, Wand2 } from "lucide-react";
import { ApiError } from "../../../shared/api/http";
import { enviarJustificanteThempus, previewJustificanteThempus } from "../services/justificanteThempusApi";
import type { ExpedienteDetail, InteresadoExpediente } from "../types/expedienteDetail.types";
import type {
  JustificanteThempusAdquirente,
  JustificanteThempusPreviewResponse,
  JustificanteThempusRequest,
  JustificanteThempusSendResponse,
  JustificanteThempusVehiculo,
} from "../types/justificanteThempus.types";

type FieldPath =
  | keyof Omit<JustificanteThempusRequest, "adquirente" | "vehiculo">
  | `adquirente.${keyof JustificanteThempusAdquirente}`
  | `vehiculo.${keyof JustificanteThempusVehiculo}`;

interface Props {
  expediente: ExpedienteDetail;
  onConfirmSend: () => Promise<boolean>;
}

function splitName(value?: string | null) {
  const parts = (value || "").trim().split(/\s+/).filter(Boolean);
  if (parts.length <= 1) return { nombre: parts[0] || "", apellido1: "", apellido2: "" };
  if (parts.length === 2) return { nombre: parts[0], apellido1: parts[1], apellido2: "" };
  return {
    nombre: parts.slice(0, -2).join(" "),
    apellido1: parts[parts.length - 2],
    apellido2: parts[parts.length - 1],
  };
}

function selectAdquirente(expediente: ExpedienteDetail): InteresadoExpediente | undefined {
  return expediente.interesados.find((item) => item.rol === "COMPRADOR")
    || expediente.interesados.find((item) => item.rol === "TITULAR")
    || expediente.interesados[0];
}

function documentSummary(expediente: ExpedienteDetail) {
  const uploaded = expediente.documentos
    .filter((documento) => documento.subido)
    .map((documento) => documento.tipo)
    .filter(Boolean);
  return Array.from(new Set(uploaded)).slice(0, 8).join(",");
}

function buildInitialRequest(expediente: ExpedienteDetail): JustificanteThempusRequest {
  const adquirente = selectAdquirente(expediente);
  const name = splitName(adquirente?.nombre);
  return {
    jefatura: "",
    diasValidez: "15",
    sucursal: "",
    tipoTramite: expediente.tipoTramite || expediente.tipoTramiteDescripcion || "",
    documentos: documentSummary(expediente),
    expedientePlataforma: expediente.referencia || `EXP-${expediente.id}`,
    motivo: "",
    adquirente: {
      razonSocial: "",
      nombre: name.nombre,
      apellido1: name.apellido1,
      apellido2: name.apellido2,
      dni: adquirente?.dni || "",
      sexo: "",
      siglasDireccion: adquirente?.tipoVia || "",
      nombreViaDireccion: adquirente?.nombreVia || adquirente?.direccion || "",
      numeroDireccion: "",
      pisoDireccion: "",
      puertaDireccion: "",
      municipio: adquirente?.municipio || "",
      pueblo: adquirente?.municipio || "",
      provincia: adquirente?.provincia || "",
      cp: adquirente?.codigoPostal || "",
      kmDireccion: "",
      hectometroDireccion: "",
      letraDireccion: "",
      escaleraDireccion: "",
      bloqueDireccion: "",
      ifa: "",
    },
    vehiculo: {
      tipoVehiculo: "T",
      matricula: expediente.matricula || "",
      marca: "",
      modelo: "",
      numeroBastidor: "",
    },
  };
}

function setFieldValue(request: JustificanteThempusRequest, path: FieldPath, value: string): JustificanteThempusRequest {
  if (path.startsWith("adquirente.")) {
    const key = path.replace("adquirente.", "") as keyof JustificanteThempusAdquirente;
    return { ...request, adquirente: { ...request.adquirente, [key]: value } };
  }
  if (path.startsWith("vehiculo.")) {
    const key = path.replace("vehiculo.", "") as keyof JustificanteThempusVehiculo;
    return { ...request, vehiculo: { ...request.vehiculo, [key]: value } };
  }
  return { ...request, [path]: value };
}

function getFieldValue(request: JustificanteThempusRequest, path: FieldPath) {
  if (path.startsWith("adquirente.")) {
    return request.adquirente[path.replace("adquirente.", "") as keyof JustificanteThempusAdquirente] || "";
  }
  if (path.startsWith("vehiculo.")) {
    return request.vehiculo[path.replace("vehiculo.", "") as keyof JustificanteThempusVehiculo] || "";
  }
  return request[path] || "";
}

function CompactField({
  label,
  path,
  request,
  onChange,
}: {
  label: string;
  path: FieldPath;
  request: JustificanteThempusRequest;
  onChange: (path: FieldPath, value: string) => void;
}) {
  return (
    <label className="thempus-field">
      <span>{label}</span>
      <input
        value={getFieldValue(request, path)}
        onChange={(event) => onChange(path, event.target.value.toUpperCase())}
      />
    </label>
  );
}

export function JustificanteThempusPanel({ expediente, onConfirmSend }: Props) {
  const initialRequest = useMemo(() => buildInitialRequest(expediente), [expediente]);
  const [request, setRequest] = useState<JustificanteThempusRequest>(initialRequest);
  const [preview, setPreview] = useState<JustificanteThempusPreviewResponse | null>(null);
  const [sendResult, setSendResult] = useState<JustificanteThempusSendResponse | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setRequest(initialRequest);
    setPreview(null);
    setSendResult(null);
    setError(null);
  }, [initialRequest]);

  const missing = [
    request.jefatura ? null : "Jefatura",
    request.adquirente.dni ? null : "DNI adquirente",
    request.vehiculo.matricula ? null : "Matricula",
    request.vehiculo.numeroBastidor ? null : "Bastidor",
  ].filter(Boolean) as string[];

  const updateField = (path: FieldPath, value: string) => {
    setRequest((current) => setFieldValue(current, path, value));
    setSendResult(null);
  };

  const handlePreview = async () => {
    setLoadingPreview(true);
    setError(null);
    setSendResult(null);
    try {
      setPreview(await previewJustificanteThempus(request));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.details || "No se pudo generar la previsualizacion." : "No se pudo generar la previsualizacion.");
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleSend = async () => {
    const confirmed = await onConfirmSend();
    if (!confirmed) return;
    setSending(true);
    setError(null);
    try {
      const result = await enviarJustificanteThempus(request);
      setSendResult(result);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.details || "No se pudo enviar el justificante." : "No se pudo enviar el justificante.");
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="thempus-panel" aria-label="Justificante Thempus">
      <div className="thempus-panel__heading">
        <div>
          <p className="eyebrow">Conexion externa</p>
          <h3>Justificante Thempus</h3>
          <p>Prepara la peticion capturada del programa de gestion y revisa el XML antes de enviarlo.</p>
        </div>
        <span className={`thempus-panel__status ${missing.length ? "thempus-panel__status--warning" : "thempus-panel__status--ready"}`}>
          {missing.length ? `${missing.length} dato${missing.length === 1 ? "" : "s"} pendiente${missing.length === 1 ? "" : "s"}` : "Listo para revisar"}
        </span>
      </div>

      {missing.length ? (
        <div className="thempus-warning" role="alert">
          <AlertTriangle size={18} />
          <span>Completa: {missing.join(", ")}.</span>
        </div>
      ) : null}

      <div className="thempus-grid">
        <CompactField label="Jefatura" path="jefatura" request={request} onChange={updateField} />
        <CompactField label="Dias validez" path="diasValidez" request={request} onChange={updateField} />
        <CompactField label="Tipo tramite" path="tipoTramite" request={request} onChange={updateField} />
        <CompactField label="Expediente plataforma" path="expedientePlataforma" request={request} onChange={updateField} />
        <CompactField label="Documentos" path="documentos" request={request} onChange={updateField} />
        <CompactField label="Nombre" path="adquirente.nombre" request={request} onChange={updateField} />
        <CompactField label="Apellido 1" path="adquirente.apellido1" request={request} onChange={updateField} />
        <CompactField label="Apellido 2" path="adquirente.apellido2" request={request} onChange={updateField} />
        <CompactField label="DNI" path="adquirente.dni" request={request} onChange={updateField} />
        <CompactField label="Tipo via" path="adquirente.siglasDireccion" request={request} onChange={updateField} />
        <CompactField label="Via" path="adquirente.nombreViaDireccion" request={request} onChange={updateField} />
        <CompactField label="Numero" path="adquirente.numeroDireccion" request={request} onChange={updateField} />
        <CompactField label="Municipio" path="adquirente.municipio" request={request} onChange={updateField} />
        <CompactField label="Provincia" path="adquirente.provincia" request={request} onChange={updateField} />
        <CompactField label="CP" path="adquirente.cp" request={request} onChange={updateField} />
        <CompactField label="Matricula" path="vehiculo.matricula" request={request} onChange={updateField} />
        <CompactField label="Marca" path="vehiculo.marca" request={request} onChange={updateField} />
        <CompactField label="Modelo" path="vehiculo.modelo" request={request} onChange={updateField} />
        <CompactField label="Bastidor" path="vehiculo.numeroBastidor" request={request} onChange={updateField} />
      </div>

      <div className="thempus-actions">
        <button className="soft-button" disabled={loadingPreview} onClick={handlePreview} type="button">
          {loadingPreview ? <Loader2 className="button-spinner" size={16} /> : <Wand2 size={16} />}
          Previsualizar
        </button>
        <button className="primary-button" disabled={sending || missing.length > 0} onClick={handleSend} type="button">
          {sending ? <Loader2 className="button-spinner" size={16} /> : <Send size={16} />}
          Enviar justificante
        </button>
      </div>

      {error ? <p className="thempus-message thempus-message--error">{error}</p> : null}

      {sendResult ? (
        <div className={`thempus-message ${sendResult.enviado ? "thempus-message--success" : "thempus-message--muted"}`}>
          {sendResult.enviado ? <CheckCircle2 size={16} /> : <ShieldCheck size={16} />}
          <span>
            {sendResult.enviado
              ? `Enviado. Codigo ${sendResult.statusCode}.`
              : sendResult.responseBody || "Integracion desactivada: no se ha enviado nada externo."}
          </span>
        </div>
      ) : null}

      {preview ? (
        <div className="thempus-preview">
          <div>
            <strong>{preview.method}</strong>
            <span>{preview.urlRedacted}</span>
            <small>{preview.bodyBytes} bytes</small>
          </div>
          <pre>{preview.xml}</pre>
        </div>
      ) : null}
    </section>
  );
}
