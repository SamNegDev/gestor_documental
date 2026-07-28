import { Home, IdCard, Pencil, Phone, UserRound } from "lucide-react";
import type { InteresadoExpediente } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  interesados: InteresadoExpediente[];
  onEditInteresados?: () => void;
};

export function InteresadosPanel({ interesados, onEditInteresados }: Props) {
  return (
    <section className="exp-panel">
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Datos principales</p>
          <h3>Interesados</h3>
        </div>
        <div className="exp-panel__heading-actions">
          {onEditInteresados ? (
            <button className="soft-button soft-button--compact" onClick={onEditInteresados} type="button">
              <Pencil size={15} />
              Corregir interesados
            </button>
          ) : null}
          <span className="exp-panel__counter">{interesados.length}</span>
        </div>
      </div>

      {interesados.length === 0 ? (
        <p className="exp-empty">Todavía no hay interesados asociados a este expediente.</p>
      ) : (
        <div className="interesados-grid">
          {interesados.map((interesado) => (
            <article className="interesado-card" key={interesado.id}>
              <div className="interesado-card__identity">
                <div className="interesado-card__avatar">
                  <UserRound size={22} />
                </div>
                <div>
                  <strong>{interesado.nombre}</strong>
                  <span>{humanizeEnum(interesado.rol)}</span>
                </div>
              </div>
              <dl>
                <div>
                  <dt>
                    <IdCard size={15} /> DNI/NIE
                  </dt>
                  <dd>{interesado.dni || "No informado"}</dd>
                </div>
                <div>
                  <dt>
                    <Phone size={15} /> Teléfono
                  </dt>
                  <dd>{interesado.telefono || "No informado"}</dd>
                </div>
                <div>
                  <dt>
                    <Home size={15} /> Dirección
                  </dt>
                  <dd>{formatInteresadoAddress(interesado) || "No informada"}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
function formatInteresadoAddress(interesado: InteresadoExpediente) {
  const structuredValues = [
    interesado.tipoVia,
    interesado.nombreVia,
    interesado.numeroVia,
    interesado.bloque,
    interesado.portal,
    interesado.escalera,
    interesado.piso,
    interesado.puerta,
    interesado.codigoPostal,
    interesado.municipio,
    interesado.localidad,
    interesado.provincia,
  ];
  const hasStructuredAddress = structuredValues.some((value) => value?.trim());

  if (!hasStructuredAddress) {
    return interesado.direccion?.trim() || "";
  }

  const via = [
    interesado.tipoVia,
    interesado.nombreVia,
    interesado.numeroVia,
    withAddressLabel("BLOQ", interesado.bloque),
    withAddressLabel("PORTAL", interesado.portal),
    withAddressLabel("ESC", interesado.escalera),
    withAddressLabel("PISO", interesado.piso),
    withAddressLabel("PTA", interesado.puerta),
  ]
    .map(cleanAddressPart)
    .filter(Boolean)
    .join(" ");

  return uniqueAddressParts([
    via,
    interesado.codigoPostal,
    interesado.localidad,
    interesado.municipio,
    interesado.provincia,
  ]).join(", ");
}

function withAddressLabel(label: string, value?: string | null) {
  const clean = value?.trim();
  return clean ? `${label} ${clean}` : "";
}

function cleanAddressPart(value?: string | null) {
  return value?.trim() || "";
}

function uniqueAddressParts(values: Array<string | null | undefined>) {
  const seen = new Set<string>();
  return values
    .map(cleanAddressPart)
    .filter((value) => {
      if (!value) return false;
      const key = value.toLocaleUpperCase("es");
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}
