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
  const update = (field: keyof AddressValue, rawValue: string) => {
    onChange({ ...value, [field]: uppercaseInput(rawValue) });
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
        <input list={`${idPrefix}-provincias`} value={value.provincia || ""} onChange={(event) => update("provincia", event.target.value)} />
        <datalist id={`${idPrefix}-provincias`}>
          {PROVINCIAS.map((provincia) => <option key={provincia} value={provincia} />)}
        </datalist>
      </label>
      <label>
        Municipio
        <input
          list={municipios.length ? `${idPrefix}-municipios` : undefined}
          value={value.municipio || ""}
          onChange={(event) => update("municipio", event.target.value)}
        />
        {municipios.length ? (
          <datalist id={`${idPrefix}-municipios`}>
            {municipios.map((municipio) => <option key={municipio} value={municipio} />)}
          </datalist>
        ) : null}
      </label>
    </>
  );
}
