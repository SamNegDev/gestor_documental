import { municipiosParaProvincia, PROVINCIAS, TIPOS_VIA } from "../utils/addressCatalog";
import { uppercaseInput } from "../utils/text";

export type AddressValue = {
  tipoVia?: string | null;
  nombreVia?: string | null;
  codigoPostal?: string | null;
  municipio?: string | null;
  provincia?: string | null;
};

type Props = {
  idPrefix: string;
  value: AddressValue;
  onChange: (value: AddressValue) => void;
  wideClassName?: string;
};

export function AddressFields({ idPrefix, value, onChange, wideClassName }: Props) {
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

  return (
    <>
      <label>
        Tipo de via
        <input list={`${idPrefix}-tipos-via`} value={value.tipoVia || ""} onChange={(event) => update("tipoVia", event.target.value)} />
        <datalist id={`${idPrefix}-tipos-via`}>
          {TIPOS_VIA.map((tipo) => <option key={tipo} value={tipo} />)}
        </datalist>
      </label>
      <label className={wideClassName}>
        Nombre de la via
        <input value={value.nombreVia || ""} onChange={(event) => update("nombreVia", event.target.value)} />
      </label>
      <label>
        Codigo postal
        <input inputMode="numeric" value={value.codigoPostal || ""} onChange={(event) => update("codigoPostal", event.target.value)} />
      </label>
      <label>
        Provincia
        <select value={provinciaActual} onChange={(event) => updateProvincia(event.target.value)}>
          <option value="">Selecciona provincia</option>
          {PROVINCIAS.map((provincia) => <option key={provincia} value={provincia}>{provincia}</option>)}
        </select>
      </label>
      <label>
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
    </>
  );
}
