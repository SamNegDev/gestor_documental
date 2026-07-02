import { AlertTriangle, FileCheck2, FileSignature, Loader2, Printer, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ApiError } from "../../../shared/api/http";
import { uppercaseInput } from "../../../shared/utils/text";
import {
  getDocumentTemplates,
  getSolicitudDocumentTemplates,
  printDocumentTemplate,
  printSolicitudDocumentTemplate,
  previewDocumentTemplate,
  previewSolicitudDocumentTemplate,
} from "../services/documentosApi";
import type { PlantillaPreview, PlantillasExpediente } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  expedienteId?: number;
  solicitudId?: number;
  scope?: "expediente" | "solicitud";
  open: boolean;
  onClose: () => void;
};

function messageFor(error: unknown) {
  if (error instanceof ApiError) return error.details || "No se pudo completar la operacion.";
  return "No se pudo completar la operacion.";
}

export function DocumentTemplateDialog({ expedienteId, solicitudId, scope = "expediente", open, onClose }: Props) {
  const [catalogo, setCatalogo] = useState<PlantillasExpediente | null>(null);
  const [preview, setPreview] = useState<PlantillaPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const targetId = scope === "solicitud" ? solicitudId : expedienteId;
  const contenedor = scope === "solicitud" ? "solicitud" : "expediente";
  const actionLabel = scope === "solicitud" ? "Abrir PDF para subir firmado" : "Abrir PDF";

  useEffect(() => {
    if (!open || !targetId) return;
    let active = true;
    setLoading(true);
    setError(null);
    setCatalogo(null);
    setPreview(null);
    const loadTemplates = scope === "solicitud" ? getSolicitudDocumentTemplates : getDocumentTemplates;
    const loadPreview = scope === "solicitud" ? previewSolicitudDocumentTemplate : previewDocumentTemplate;
    loadTemplates(targetId)
      .then(async (data) => {
        if (!active) return;
        setCatalogo(data);
        const first = data.plantillas[0];
        if (first) setPreview(await loadPreview(targetId, first.codigo));
      })
      .catch((requestError) => active && setError(messageFor(requestError)))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [open, scope, targetId]);

  const values = useMemo(
    () => Object.fromEntries((preview?.campos ?? []).map((campo) => [campo.codigo, campo.valor])),
    [preview],
  );

  if (!open) return null;

  const selectTemplate = async (codigo: string) => {
    if (!targetId) return;
    setLoading(true);
    setError(null);
    try {
      const loadPreview = scope === "solicitud" ? previewSolicitudDocumentTemplate : previewDocumentTemplate;
      setPreview(await loadPreview(targetId, codigo));
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setLoading(false);
    }
  };

  const updateField = (codigo: string, value: string) => {
    setPreview((current) => current
      ? { ...current, campos: current.campos.map((campo) => campo.codigo === codigo ? { ...campo, valor: uppercaseInput(value) } : campo) }
      : current);
  };

  const generate = async () => {
    if (!preview || !targetId) return;
    setGenerating(true);
    setError(null);
    const pdfWindow = window.open("about:blank", "_blank");
    try {
      const printTemplate = scope === "solicitud" ? printSolicitudDocumentTemplate : printDocumentTemplate;
      const { blob } = await printTemplate(targetId, preview.codigo, values);
      const url = URL.createObjectURL(blob);
      if (pdfWindow) {
        pdfWindow.opener = null;
        pdfWindow.location.href = url;
      } else {
        const opened = window.open(url, "_blank", "noopener,noreferrer");
        if (!opened) {
          setError("El navegador ha bloqueado la ventana del PDF. Permite ventanas emergentes para abrirlo.");
          URL.revokeObjectURL(url);
          return;
        }
      }
      onClose();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (requestError) {
      if (pdfWindow && !pdfWindow.closed) pdfWindow.close();
      setError(messageFor(requestError));
    } finally {
      setGenerating(false);
    }
  };

  const incomplete = preview?.campos.some((campo) => campo.requerido && !campo.valor.trim()) ?? true;

  return (
    <div className="exp-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar generador de documentos" />
      <section aria-labelledby="template-dialog-title" aria-modal="true" className="exp-modal__panel template-dialog" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p className="eyebrow">Modelos documentales</p>
            <h3 id="template-dialog-title">Generar PDF para imprimir</h3>
          </div>
          <button aria-label="Cerrar" className="icon-button" onClick={onClose} type="button">
            <X size={16} />
          </button>
        </div>

        {loading && !preview ? (
          <div className="template-dialog__status"><Loader2 className="spin" size={24} /> Cargando modelos...</div>
        ) : null}
        {error ? <div className="template-dialog__error" role="alert"><AlertTriangle size={17} /> {error}</div> : null}

        {catalogo && preview ? (
          <div className="template-dialog__body">
            <nav className="template-dialog__templates" aria-label="Modelos disponibles">
              {catalogo.plantillas.map((plantilla) => (
                <button
                  className={preview.codigo === plantilla.codigo ? "template-option template-option--active" : "template-option"}
                  key={plantilla.codigo}
                  onClick={() => selectTemplate(plantilla.codigo)}
                  type="button"
                >
                  <FileSignature size={18} />
                  <span><strong>{plantilla.nombre}</strong><small>{plantilla.descripcion}</small></span>
                </button>
              ))}
            </nav>

            <div className="template-dialog__form">
              <div className="template-dialog__context">
                <span>{catalogo.referencia}</span>
                <strong>{catalogo.matricula}</strong>
                <span>{catalogo.tipoTramite}</span>
              </div>
              <div className="template-fields">
                {preview.campos.map((campo) => (
                  <label className={campo.tipo === "TEXTAREA" ? "template-field template-field--wide" : "template-field"} key={campo.codigo}>
                    <span>{campo.etiqueta}{campo.requerido ? " *" : ""}</span>
                    {campo.tipo === "INTERESADO" ? (
                      <select value={campo.valor} onChange={(event) => updateField(campo.codigo, event.target.value)}>
                        <option value="">SELECCIONAR</option>
                        {catalogo.interesados.map((interesado) => (
                          <option key={interesado.interesadoId} value={interesado.interesadoId}>
                            {humanizeEnum(interesado.rol || "INTERESADO")} · {interesado.dni} · {interesado.nombre}
                          </option>
                        ))}
                      </select>
                    ) : campo.tipo === "TEXTAREA" ? (
                      <textarea value={campo.valor} onChange={(event) => updateField(campo.codigo, event.target.value)} />
                    ) : (
                      <input
                        inputMode={campo.tipo === "NUMBER" ? "decimal" : undefined}
                        placeholder={campo.tipo === "DATE" ? "DD/MM/AAAA" : undefined}
                        value={campo.valor}
                        onChange={(event) => updateField(campo.codigo, event.target.value)}
                      />
                    )}
                    {campo.ayuda ? <small>{campo.ayuda}</small> : null}
                  </label>
                ))}
              </div>
            </div>

            <aside className="template-dialog__summary">
              <div className="template-document-mark"><FileCheck2 size={24} /></div>
              <p className="eyebrow">Resultado</p>
              <h4>{preview.nombre}</h4>
              <strong className="template-dialog__filename">{preview.nombreArchivo}</strong>
              <dl>
                <div><dt>Matricula</dt><dd>{catalogo.matricula}</dd></div>
                <div><dt>Cliente</dt><dd>{catalogo.cliente}</dd></div>
                <div><dt>Formato</dt><dd>PDF oficial</dd></div>
              </dl>
              <p>El PDF se abrira en una pestana nueva para imprimirlo o enviarlo. No se guardara dentro del {contenedor} hasta que se suba firmado.</p>
              {incomplete ? <div className="template-dialog__notice"><AlertTriangle size={15} /> Completa los campos obligatorios.</div> : null}
            </aside>
          </div>
        ) : null}

        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">Cancelar</button>
          <button className="primary-button" disabled={!preview || incomplete || generating || loading} onClick={generate} type="button">
            {generating ? <Loader2 className="spin" size={16} /> : <Printer size={16} />}
            {generating ? "Generando..." : actionLabel}
          </button>
        </footer>
      </section>
    </div>
  );
}
