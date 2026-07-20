import { useState, type ChangeEvent } from "react";
import { ExternalLink, Loader2, RefreshCw, UserPlus, X } from "lucide-react";
import { uppercaseInput, uppercaseInputPreservingCursor } from "../../../shared/utils/text";
import type { DocumentoExpediente, DocumentoIdentidadDetectada, DocumentoIdentidadLectura } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

export type DocumentReadingExistingIdentity = {
  identificador: string;
  rol?: string | null;
  nombre?: string | null;
};

const DEFAULT_ROLES = ["COMPRADOR", "VENDEDOR", "COMPRAVENTA", "TITULAR"];

type Props = {
  documento: DocumentoExpediente;
  canAddIdentity?: boolean;
  canRereadIdentity?: boolean;
  existingIdentities?: DocumentReadingExistingIdentity[];
  addingIdentity?: boolean;
  rereadingIdentity?: boolean;
  roles?: string[];
  onAddIdentity?: (documento: DocumentoExpediente, identidad: DocumentoIdentidadDetectada, rol: string, identificador: string, nombreCompleto: string) => void;
  onRereadIdentity?: (documento: DocumentoExpediente) => void;
};

type IdentityReviewState = {
  key: string;
  item: DocumentoIdentidadDetectada;
  role: string;
  identifier: string;
};

type IdentityReviewDraft = {
  identificador: string;
  nombre: string;
  apellido1: string;
  apellido2: string;
  razonSocial: string;
  tipoVia: string;
  nombreVia: string;
  numeroVia: string;
  bloque: string;
  portal: string;
  escalera: string;
  piso: string;
  puerta: string;
  codigoPostal: string;
  municipio: string;
  provincia: string;
};

type SelectedRolesState = {
  values: Record<string, string>;
  inferredFrom: Record<string, string>;
};

export function DocumentReadingPanel({
  documento,
  canAddIdentity = false,
  canRereadIdentity = false,
  existingIdentities = [],
  addingIdentity = false,
  rereadingIdentity = false,
  roles = DEFAULT_ROLES,
  onAddIdentity,
  onRereadIdentity,
}: Props) {
  const [selectedRoles, setSelectedRoles] = useState<SelectedRolesState>({ values: {}, inferredFrom: {} });
  const [identifiers, setIdentifiers] = useState<Record<string, string>>({});
  const [reviewing, setReviewing] = useState<IdentityReviewState | null>(null);
  const identidad = documento.lecturaIdentidad;
  const rolesLectura = documento.lecturaRoles;
  const vehiculo = documento.lecturaVehiculo;
  if (!identidad && !rolesLectura && !vehiculo) {
    return null;
  }

  if (identidad) {
    const detectadas = compactIdentityOptions(
      identidad.identidadesDetectadas?.length ? identidad.identidadesDetectadas : [lecturaPrincipalComoIdentidad(identidad)],
    );
    return (
      <div className={identidad.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
        <div className="solicitud-reading__header">
          <strong>{detectadas.length > 1 ? `${detectadas.length} DNI/CIF detectados` : "DNI/CIF detectado"}</strong>
          <div className="solicitud-reading__header-actions">
            <span>{confidenceLabel(identidad.confianzaGlobal)}</span>
            {canRereadIdentity ? (
              <button
                className="soft-button soft-button--compact"
                disabled={rereadingIdentity}
                onClick={() => onRereadIdentity?.(documento)}
                type="button"
              >
                {rereadingIdentity ? <Loader2 size={14} /> : <RefreshCw size={14} />}
                Releer
              </button>
            ) : null}
          </div>
        </div>
        <div className="solicitud-reading-identities">
          {detectadas.map((item, index) => {
            const key = identityKey(item, index);
            const normalizedId = normalizeIdentityIdentifier(item.identificador);
            const identifierValue = identifiers[key] ?? normalizedId ?? "";
            const normalizedEditedId = normalizeIdentityIdentifier(identifierValue);
            const matchedExisting = normalizedEditedId
              ? existingIdentities.find((existing) => existing.identificador === normalizedEditedId)
              : null;
            const selectedRole = matchedExisting?.rol || selectedRoles.values[key] || "";
            const roleLocked = Boolean(matchedExisting?.rol);
            const submitLabel = matchedExisting ? "Asignar" : "Anadir";
            return (
              <div className="solicitud-reading-identity" key={key}>
                <div className="solicitud-reading-identity__main">
                  <div className="solicitud-reading-identity__summary">
                    <div>
                      <span>{item.tipoDocumentoDetectado || "Identidad"}</span>
                      <strong>{identityDisplayName(item) || "Nombre no detectado"}</strong>
                    </div>
                    <code>{normalizedId || "Sin DNI/CIF"}</code>
                  </div>
                  <div className="solicitud-reading-identity__meta">
                    {item.fechaNacimiento ? <span>Nac. {item.fechaNacimiento}</span> : null}
                    {item.fechaCaducidad ? <span>Cad. {item.fechaCaducidad}</span> : null}
                    {item.direccionTexto ? <span>{item.direccionTexto}</span> : null}
                  </div>
                  {matchedExisting ? (
                    <p className="solicitud-reading-identity__match">Coincide con interesado: {[matchedExisting.nombre, matchedExisting.rol ? humanizeEnum(matchedExisting.rol) : null].filter(Boolean).join(" - ")}</p>
                  ) : null}
                  {item.observaciones ? (
                    <p className="solicitud-reading-identity__note">{item.observaciones}</p>
                  ) : null}
                </div>
                {canAddIdentity ? (
                  <div className="solicitud-reading-identity__actions">
                    <input
                      aria-label="DNI/NIE/CIF revisado"
                      className="solicitud-reading-identity__identifier"
                      disabled={addingIdentity}
                      onChange={(event) => uppercaseInputPreservingCursor(event, (value) => setIdentifiers((current) => ({ ...current, [key]: value })))}
                      placeholder="DNI/NIE/CIF"
                      value={identifierValue}
                    />
                    <select
                      aria-label="Rol del interesado"
                      disabled={addingIdentity || roleLocked}
                      onChange={(event) => setSelectedRoles((current) => inferComplementaryRole(
                        current,
                        detectadas,
                        existingIdentities,
                        key,
                        event.target.value,
                      ))}
                      value={selectedRole}
                    >
                      <option value="">Rol</option>
                      {roles.map((rol) => (
                        <option key={rol} value={rol}>{humanizeEnum(rol)}</option>
                      ))}
                    </select>
                    <button
                      className="soft-button soft-button--compact"
                      disabled={addingIdentity || !selectedRole || !normalizedEditedId}
                      onClick={() => setReviewing({ key, item, role: selectedRole, identifier: identifierValue })}
                      type="button"
                    >
                      {addingIdentity ? <Loader2 size={14} /> : <UserPlus size={14} />}
                      {matchedExisting ? "Revisar y asignar" : `Revisar y ${submitLabel.toLowerCase()}`}
                    </button>
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        <em>{identidad.mensaje || (identidad.requiereRevision ? "Revisar lectura" : "Lectura valida")}</em>
        {reviewing ? (
          <IdentityReviewDialog
            addingIdentity={addingIdentity}
            documento={documento}
            onClose={() => setReviewing(null)}
            onConfirm={(draft) => {
              const reviewed = identityFromDraft(reviewing.item, draft);
              const nombreCompleto = derivedIdentityName(draft);
              onAddIdentity?.(documento, reviewed, reviewing.role, draft.identificador, nombreCompleto);
              setReviewing(null);
            }}
            review={reviewing}
          />
        ) : null}
      </div>
    );
  }

  if (rolesLectura) {
    return (
      <div className={rolesLectura.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
        <strong>Roles detectados</strong>
        <span>Vendedor: {[rolesLectura.vendedorIdentificador, rolesLectura.vendedorNombre].filter(Boolean).join(" - ") || "Sin dato"}</span>
        <span>Comprador: {[rolesLectura.compradorIdentificador, rolesLectura.compradorNombre].filter(Boolean).join(" - ") || "Sin dato"}</span>
        {[rolesLectura.matricula, rolesLectura.bastidor].filter(Boolean).length ? <small>{[rolesLectura.matricula, rolesLectura.bastidor].filter(Boolean).join(" / ")}</small> : null}
        <em>{confidenceLabel(rolesLectura.confianzaGlobal)} / {rolesLectura.mensaje || rolesLectura.motivoAplicacion || "Lectura registrada"}</em>
      </div>
    );
  }

  return vehiculo ? (
    <div className={vehiculo.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
      <strong>Vehiculo detectado</strong>
      <span>{[vehiculo.marca, vehiculo.modeloVehiculo].filter(Boolean).join(" ") || "Sin marca/modelo"}</span>
      {[vehiculo.matricula, vehiculo.bastidor].filter(Boolean).length ? <small>{[vehiculo.matricula, vehiculo.bastidor].filter(Boolean).join(" / ")}</small> : null}
      <em>{confidenceLabel(vehiculo.confianzaGlobal)} / {vehiculo.mensaje || "Lectura registrada"}</em>
    </div>
  ) : null;
}

function inferComplementaryRole(
  current: SelectedRolesState,
  identities: DocumentoIdentidadDetectada[],
  existingIdentities: DocumentReadingExistingIdentity[],
  changedKey: string,
  role: string,
) {
  const next: SelectedRolesState = {
    values: { ...current.values, [changedKey]: role },
    inferredFrom: { ...current.inferredFrom },
  };
  delete next.inferredFrom[changedKey];
  if (identities.length !== 2 || (role !== "VENDEDOR" && role !== "COMPRADOR")) {
    Object.entries(current.inferredFrom)
      .filter(([, sourceKey]) => sourceKey === changedKey)
      .forEach(([inferredKey]) => {
        delete next.values[inferredKey];
        delete next.inferredFrom[inferredKey];
      });
    return next;
  }

  const otherIndex = identities.findIndex((item, index) => identityKey(item, index) !== changedKey);
  if (otherIndex < 0) return next;
  const other = identities[otherIndex];
  const otherKey = identityKey(other, otherIndex);
  const otherIdentifier = normalizeIdentityIdentifier(other.identificador);
  const otherAlreadyConfirmed = otherIdentifier
    ? existingIdentities.some((existing) => existing.identificador === otherIdentifier && existing.rol)
    : false;
  const otherWasInferredFromChanged = current.inferredFrom[otherKey] === changedKey;
  if (!otherAlreadyConfirmed && (!current.values[otherKey] || otherWasInferredFromChanged)) {
    next.values[otherKey] = role === "VENDEDOR" ? "COMPRADOR" : "VENDEDOR";
    next.inferredFrom[otherKey] = changedKey;
  }
  return next;
}

function IdentityReviewDialog({
  addingIdentity,
  documento,
  onClose,
  onConfirm,
  review,
}: {
  addingIdentity: boolean;
  documento: DocumentoExpediente;
  onClose: () => void;
  onConfirm: (draft: IdentityReviewDraft) => void;
  review: IdentityReviewState;
}) {
  const [draft, setDraft] = useState<IdentityReviewDraft>(() => draftFromIdentity(review.item, review.identifier));
  const nombreCompleto = derivedIdentityName(draft);
  const direccionCompleta = derivedAddress(draft) || uppercaseInput(review.item.direccionTexto || "");
  const update = (field: keyof IdentityReviewDraft, value: string) => {
    setDraft((current) => ({ ...current, [field]: uppercaseInput(value) }));
  };
  const updateInput = (field: keyof IdentityReviewDraft, event: ChangeEvent<HTMLInputElement>) => {
    uppercaseInputPreservingCursor(event, (value) => update(field, value));
  };
  const canConfirm = Boolean(normalizeIdentityIdentifier(draft.identificador) && nombreCompleto);
  return (
    <div className="exp-modal identity-review-modal" role="presentation">
      <button className="exp-modal__backdrop" onClick={onClose} type="button" aria-label="Cerrar revision de identidad" />
      <section aria-labelledby="identity-review-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--wide identity-review-modal__panel" role="dialog">
        <div className="exp-modal__header">
          <div>
            <p>Revision IA</p>
            <h3 id="identity-review-title">Validar identidad y direccion</h3>
          </div>
          <button className="icon-button" onClick={onClose} type="button" title="Cerrar"><X size={17} /></button>
        </div>
        <div className="identity-review-modal__body">
          <div className="identity-review-modal__preview">
            <iframe src={`/documentos/ver/${documento.id}`} title={`Documento ${documento.nombreOriginal || documento.nombre}`} />
            <a className="soft-button soft-button--compact" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer">
              <ExternalLink size={15} />
              Ver documento
            </a>
          </div>
          <div className="identity-review-modal__form">
            <div className="identity-review-modal__locked">
              <label>
                <span>Nombre completo</span>
                <input readOnly value={nombreCompleto} />
              </label>
              <label>
                <span>Direccion completa</span>
                <textarea readOnly value={direccionCompleta} />
              </label>
            </div>
            <div className="identity-review-modal__grid">
              <label>
                <span>DNI/NIE/CIF</span>
                <input value={draft.identificador} onChange={(event) => updateInput("identificador", event)} />
              </label>
              <label>
                <span>Razon social</span>
                <input value={draft.razonSocial} onChange={(event) => updateInput("razonSocial", event)} />
              </label>
              <label>
                <span>Nombre</span>
                <input value={draft.nombre} onChange={(event) => updateInput("nombre", event)} />
              </label>
              <label>
                <span>Apellido 1</span>
                <input value={draft.apellido1} onChange={(event) => updateInput("apellido1", event)} />
              </label>
              <label>
                <span>Apellido 2</span>
                <input value={draft.apellido2} onChange={(event) => updateInput("apellido2", event)} />
              </label>
              <label>
                <span>Tipo via</span>
                <input value={draft.tipoVia} onChange={(event) => updateInput("tipoVia", event)} />
              </label>
              <label className="identity-review-modal__wide-field">
                <span>Nombre via</span>
                <input value={draft.nombreVia} onChange={(event) => updateInput("nombreVia", event)} />
              </label>
              <label>
                <span>Numero</span>
                <input value={draft.numeroVia} onChange={(event) => updateInput("numeroVia", event)} />
              </label>
              <label>
                <span>Bloque</span>
                <input value={draft.bloque} onChange={(event) => updateInput("bloque", event)} />
              </label>
              <label>
                <span>Portal</span>
                <input value={draft.portal} onChange={(event) => updateInput("portal", event)} />
              </label>
              <label>
                <span>Escalera</span>
                <input value={draft.escalera} onChange={(event) => updateInput("escalera", event)} />
              </label>
              <label>
                <span>Piso</span>
                <input value={draft.piso} onChange={(event) => updateInput("piso", event)} />
              </label>
              <label>
                <span>Puerta</span>
                <input value={draft.puerta} onChange={(event) => updateInput("puerta", event)} />
              </label>
              <label>
                <span>Codigo postal</span>
                <input value={draft.codigoPostal} onChange={(event) => updateInput("codigoPostal", event)} />
              </label>
              <label>
                <span>Municipio</span>
                <input value={draft.municipio} onChange={(event) => updateInput("municipio", event)} />
              </label>
              <label>
                <span>Provincia</span>
                <input value={draft.provincia} onChange={(event) => updateInput("provincia", event)} />
              </label>
            </div>
          </div>
        </div>
        <footer className="exp-modal__footer">
          <button className="soft-button" onClick={onClose} type="button">Cancelar</button>
          <button className="primary-button" disabled={addingIdentity || !canConfirm} onClick={() => onConfirm({ ...draft, identificador: normalizeIdentityIdentifier(draft.identificador) || draft.identificador })} type="button">
            {addingIdentity ? <Loader2 size={16} /> : <UserPlus size={16} />}
            Confirmar datos
          </button>
        </footer>
      </section>
    </div>
  );
}

function draftFromIdentity(item: DocumentoIdentidadDetectada, identifier: string): IdentityReviewDraft {
  return {
    identificador: uppercaseInput(identifier || item.identificador || ""),
    nombre: uppercaseInput(item.nombre || ""),
    apellido1: uppercaseInput(item.apellido1 || ""),
    apellido2: uppercaseInput(item.apellido2 || ""),
    razonSocial: uppercaseInput(item.razonSocial || ""),
    tipoVia: uppercaseInput(item.tipoVia || ""),
    nombreVia: uppercaseInput(item.nombreVia || ""),
    numeroVia: uppercaseInput(item.numeroVia || ""),
    bloque: uppercaseInput(item.bloque || ""),
    portal: uppercaseInput(item.portal || ""),
    escalera: uppercaseInput(item.escalera || ""),
    piso: uppercaseInput(item.piso || ""),
    puerta: uppercaseInput(item.puerta || ""),
    codigoPostal: uppercaseInput(item.codigoPostal || ""),
    municipio: uppercaseInput(item.municipio || ""),
    provincia: uppercaseInput(item.provincia || ""),
  };
}

function identityFromDraft(item: DocumentoIdentidadDetectada, draft: IdentityReviewDraft): DocumentoIdentidadDetectada {
  return {
    ...item,
    identificador: draft.identificador,
    nombre: draft.nombre || null,
    apellido1: draft.apellido1 || null,
    apellido2: draft.apellido2 || null,
    razonSocial: draft.razonSocial || null,
    nombreCompleto: derivedIdentityName(draft),
    direccionTexto: derivedAddress(draft) || item.direccionTexto,
    tipoVia: draft.tipoVia || null,
    nombreVia: draft.nombreVia || null,
    numeroVia: draft.numeroVia || null,
    bloque: draft.bloque || null,
    portal: draft.portal || null,
    escalera: draft.escalera || null,
    piso: draft.piso || null,
    puerta: draft.puerta || null,
    codigoPostal: draft.codigoPostal || null,
    municipio: draft.municipio || null,
    provincia: draft.provincia || null,
  };
}

function derivedIdentityName(draft: IdentityReviewDraft) {
  return draft.razonSocial || [draft.nombre, draft.apellido1, draft.apellido2].filter(Boolean).join(" ").trim();
}

function derivedAddress(draft: IdentityReviewDraft) {
  const via = [
    draft.tipoVia,
    draft.nombreVia,
    draft.numeroVia,
    draft.bloque ? `BLOQ ${draft.bloque}` : "",
    draft.portal ? `PORTAL ${draft.portal}` : "",
    draft.escalera ? `ESC ${draft.escalera}` : "",
    draft.piso ? `PISO ${draft.piso}` : "",
    draft.puerta ? `PTA ${draft.puerta}` : "",
  ].filter(Boolean).join(" ");
  return [via, draft.codigoPostal, draft.municipio, draft.provincia].filter(Boolean).join(", ");
}

export function lecturaPrincipalComoIdentidad(lectura: DocumentoIdentidadLectura): DocumentoIdentidadDetectada {
  return {
    tipoDocumentoDetectado: lectura.tipoDocumentoDetectado,
    identificador: lectura.identificador,
    nombre: lectura.nombre,
    apellido1: lectura.apellido1,
    apellido2: lectura.apellido2,
    razonSocial: lectura.razonSocial,
    nombreCompleto: lectura.nombreCompleto,
    fechaNacimiento: lectura.fechaNacimiento,
    fechaCaducidad: lectura.fechaCaducidad,
    direccionTexto: lectura.direccionTexto,
    tipoVia: lectura.tipoVia,
    nombreVia: lectura.nombreVia,
    numeroVia: lectura.numeroVia,
    bloque: lectura.bloque,
    portal: lectura.portal,
    escalera: lectura.escalera,
    piso: lectura.piso,
    puerta: lectura.puerta,
    codigoPostal: lectura.codigoPostal,
    municipio: lectura.municipio,
    provincia: lectura.provincia,
    confianzaGlobal: lectura.confianzaGlobal,
    requiereRevision: lectura.requiereRevision,
    observaciones: lectura.mensaje,
  };
}

export function compactIdentityOptions(identidades: DocumentoIdentidadDetectada[]) {
  const result: DocumentoIdentidadDetectada[] = [];
  identidades.forEach((item) => {
    const normalizedId = normalizeIdentityIdentifier(item.identificador);
    const normalizedItem = {
      ...item,
      identificador: normalizedId?.startsWith("IDESP") ? null : normalizedId || item.identificador,
    };
    if (!normalizedItem.identificador && !identityDisplayName(normalizedItem)) {
      return;
    }
    const index = result.findIndex((existing) => sameIdentityOption(existing, normalizedItem));
    if (index >= 0) {
      result[index] = mergeIdentityOption(result[index], normalizedItem);
    } else {
      result.push(normalizedItem);
    }
  });
  return result;
}

function sameIdentityOption(first: DocumentoIdentidadDetectada, second: DocumentoIdentidadDetectada) {
  const firstId = normalizeIdentityIdentifier(first.identificador);
  const secondId = normalizeIdentityIdentifier(second.identificador);
  if (firstId && secondId && firstId === secondId) {
    return true;
  }
  const firstName = normalizeIdentityName(first);
  const secondName = normalizeIdentityName(second);
  return Boolean(firstName && firstName === secondName && (!firstId || !secondId));
}

function mergeIdentityOption(first: DocumentoIdentidadDetectada, second: DocumentoIdentidadDetectada): DocumentoIdentidadDetectada {
  const firstId = normalizeIdentityIdentifier(first.identificador);
  const secondId = normalizeIdentityIdentifier(second.identificador);
  return {
    ...first,
    ...second,
    identificador: firstId || secondId || first.identificador || second.identificador,
    nombre: second.nombre || first.nombre,
    apellido1: second.apellido1 || first.apellido1,
    apellido2: second.apellido2 || first.apellido2,
    razonSocial: second.razonSocial || first.razonSocial,
    nombreCompleto: second.nombreCompleto || first.nombreCompleto,
    fechaNacimiento: second.fechaNacimiento || first.fechaNacimiento,
    fechaCaducidad: second.fechaCaducidad || first.fechaCaducidad,
    direccionTexto: second.direccionTexto || first.direccionTexto,
    tipoVia: second.tipoVia || first.tipoVia,
    nombreVia: second.nombreVia || first.nombreVia,
    numeroVia: second.numeroVia || first.numeroVia,
    bloque: second.bloque || first.bloque,
    portal: second.portal || first.portal,
    escalera: second.escalera || first.escalera,
    piso: second.piso || first.piso,
    puerta: second.puerta || first.puerta,
    codigoPostal: second.codigoPostal || first.codigoPostal,
    municipio: second.municipio || first.municipio,
    provincia: second.provincia || first.provincia,
    confianzaGlobal: Math.max(first.confianzaGlobal ?? 0, second.confianzaGlobal ?? 0) || first.confianzaGlobal || second.confianzaGlobal,
    requiereRevision: first.requiereRevision && second.requiereRevision,
    observaciones: second.observaciones || first.observaciones,
  };
}

function identityKey(item: DocumentoIdentidadDetectada, index: number) {
  return normalizeIdentityIdentifier(item.identificador) || `${normalizeIdentityName(item) || "identidad"}-${index}`;
}

export function identityDisplayName(item: DocumentoIdentidadDetectada) {
  return item.nombreCompleto
    || item.razonSocial
    || [item.nombre, item.apellido1, item.apellido2].filter(Boolean).join(" ").trim()
    || null;
}

function normalizeIdentityName(item: DocumentoIdentidadDetectada) {
  return identityDisplayName(item)?.toUpperCase().replace(/[^A-Z0-9]/g, "") || null;
}

export function normalizeIdentityIdentifier(value?: string | null) {
  const normalized = value?.toUpperCase().replace(/[^A-Z0-9]/g, "") || "";
  return normalized || null;
}

function confidenceLabel(value?: number | null) {
  return typeof value === "number" ? `${Math.round(value * 100)}% confianza` : "Sin confianza";
}
