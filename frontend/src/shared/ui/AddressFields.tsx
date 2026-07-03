import { municipiosParaProvincia, PROVINCIAS, TIPOS_VIA } from "../utils/addressCatalog";
import { uppercaseInput } from "../utils/text";

export type AddressValue = {
  direccion?: string | null;
  tipoVia?: string | null;
  nombreVia?: string | null;
  numeroVia?: string | null;
  bloque?: string | null;
  portal?: string | null;
  escalera?: string | null;
  piso?: string | null;
  puerta?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
};

type Props = {
  idPrefix: string;
  value: AddressValue;
  onChange: (value: AddressValue) => void;
  fieldNamePrefix?: string;
  highlightField?: string;
  wideClassName?: string;
};

export function AddressFields({ idPrefix, value, onChange, fieldNamePrefix, highlightField, wideClassName }: Props) {
  const municipios = municipiosParaProvincia(value.provincia);
  const provinciaActual = uppercaseInput(value.provincia || "");
  const municipioActual = uppercaseInput(value.municipio || "");
  const municipioCatalogado = municipioActual ? municipios.includes(municipioActual) : true;
  const municipiosVisibles = municipioActual && municipios.length && !municipioCatalogado
    ? [municipioActual, ...municipios]
    : municipios;
  const update = (field: keyof AddressValue, rawValue: string) => {
    onChange({ ...value, [field]: uppercaseInput(rawValue) });
  };
  const updateProvincia = (rawValue: string) => {
    const provincia = uppercaseInput(rawValue);
    const siguientesMunicipios = municipiosParaProvincia(provincia);
    const municipio = siguientesMunicipios.includes(municipioActual) ? municipioActual : "";
    onChange({ ...value, provincia, municipio });
  };
  const hasStructuredAddress = [
    value.tipoVia,
    value.nombreVia,
    value.numeroVia,
    value.bloque,
    value.portal,
    value.escalera,
    value.piso,
    value.puerta,
    value.codigoPostal,
    value.provincia,
    value.municipio,
  ].some((part) => Boolean(part && String(part).trim()));
  const legacyAddress = hasStructuredAddress ? "" : (value.direccion || "").trim();
  const fieldName = (suffix: string) => fieldNamePrefix ? `${fieldNamePrefix}${suffix}` : undefined;
  const fieldClassName = (suffix: string, baseClassName: string) => {
    const name = fieldName(suffix);
    return [baseClassName, name && highlightField === name ? "edit-field--missing" : null].filter(Boolean).join(" ");
  };

  return (
    <div className={`address-fields ${wideClassName || ""}`}>
      {legacyAddress ? (
        <label className="address-field address-field--legacy">
          Direccion actual
          <input type="text" value={legacyAddress} readOnly />
        </label>
      ) : null}
      <label className={fieldClassName("TipoVia", "address-field address-field--type")} data-field={fieldName("TipoVia")}>
        Tipo
        <input list={`${idPrefix}-tipos-via`} value={value.tipoVia || ""} onChange={(event) => update("tipoVia", event.target.value)} />
        <datalist id={`${idPrefix}-tipos-via`}>
          {TIPOS_VIA.map((tipo) => <option key={tipo} value={tipo} />)}
        </datalist>
      </label>
      <label className={fieldClassName("NombreVia", "address-field address-field--street")} data-field={fieldName("NombreVia")}>
        Via
        <input value={value.nombreVia || ""} onChange={(event) => update("nombreVia", event.target.value)} />
      </label>
      <label className={fieldClassName("NumeroVia", "address-field address-field--xs")} data-field={fieldName("NumeroVia")}>
        Num.
        <input maxLength={20} value={value.numeroVia || ""} onChange={(event) => update("numeroVia", event.target.value)} />
      </label>
      <label className={fieldClassName("Bloque", "address-field address-field--xs")} data-field={fieldName("Bloque")}>
        Bloq.
        <input maxLength={20} value={value.bloque || ""} onChange={(event) => update("bloque", event.target.value)} />
      </label>
      <label className={fieldClassName("Portal", "address-field address-field--xs")} data-field={fieldName("Portal")}>
        Portal
        <input maxLength={20} value={value.portal || ""} onChange={(event) => update("portal", event.target.value)} />
      </label>
      <label className={fieldClassName("Escalera", "address-field address-field--xs")} data-field={fieldName("Escalera")}>
        Esc.
        <input maxLength={20} value={value.escalera || ""} onChange={(event) => update("escalera", event.target.value)} />
      </label>
      <label className={fieldClassName("Piso", "address-field address-field--xs")} data-field={fieldName("Piso")}>
        Piso
        <input maxLength={20} value={value.piso || ""} onChange={(event) => update("piso", event.target.value)} />
      </label>
      <label className={fieldClassName("Puerta", "address-field address-field--xs")} data-field={fieldName("Puerta")}>
        Pta.
        <input maxLength={20} value={value.puerta || ""} onChange={(event) => update("puerta", event.target.value)} />
      </label>
      <label className={fieldClassName("CodigoPostal", "address-field address-field--postal")} data-field={fieldName("CodigoPostal")}>
        C.P.
        <input inputMode="numeric" maxLength={10} value={value.codigoPostal || ""} onChange={(event) => update("codigoPostal", event.target.value)} />
      </label>
      <label className={fieldClassName("Provincia", "address-field address-field--province")} data-field={fieldName("Provincia")}>
        Provincia
        <select value={provinciaActual} onChange={(event) => updateProvincia(event.target.value)}>
          <option value="">Selecciona provincia</option>
          {PROVINCIAS.map((provincia) => <option key={provincia} value={provincia}>{provincia}</option>)}
        </select>
      </label>
      <label className={fieldClassName("Municipio", "address-field address-field--municipality")} data-field={fieldName("Municipio")}>
        Municipio
        {municipios.length ? (
          <select value={municipioActual} onChange={(event) => update("municipio", event.target.value)}>
            <option value="">Selecciona municipio</option>
            {municipiosVisibles.map((municipio) => <option key={municipio} value={municipio}>{municipio}</option>)}
          </select>
        ) : (
          <input
            value={municipioActual}
            onChange={(event) => update("municipio", event.target.value)}
            placeholder={provinciaActual ? "Escribe municipio" : "Selecciona provincia"}
          />
        )}
      </label>
    </div>
  );
}
