import { useState } from "react";
import { Loader2, RefreshCw, UserPlus } from "lucide-react";
import { uppercaseInput } from "../../../shared/utils/text";
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
  onAddIdentity?: (documento: DocumentoExpediente, identidad: DocumentoIdentidadDetectada, rol: string, identificador: string) => void;
  onRereadIdentity?: (documento: DocumentoExpediente) => void;
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
  const [selectedRoles, setSelectedRoles] = useState<Record<string, string>>({});
  const [identifiers, setIdentifiers] = useState<Record<string, string>>({});
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
            const selectedRole = matchedExisting?.rol || selectedRoles[key] || "";
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
                      onChange={(event) => setIdentifiers((current) => ({ ...current, [key]: uppercaseInput(event.target.value) }))}
                      placeholder="DNI/NIE/CIF"
                      value={identifierValue}
                    />
                    <select
                      aria-label="Rol del interesado"
                      disabled={addingIdentity || roleLocked}
                      onChange={(event) => setSelectedRoles((current) => ({ ...current, [key]: event.target.value }))}
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
                      onClick={() => onAddIdentity?.(documento, item, selectedRole, identifierValue)}
                      type="button"
                    >
                      {addingIdentity ? <Loader2 size={14} /> : <UserPlus size={14} />}
                      {submitLabel}
                    </button>
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        <em>{identidad.mensaje || (identidad.requiereRevision ? "Revisar lectura" : "Lectura valida")}</em>
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
