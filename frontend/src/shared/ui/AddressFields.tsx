import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, MapPin } from "lucide-react";
import {
  getCodigosPostales,
  getDireccionesSugeridas,
  getMunicipiosCatalogo,
  getProvinciasCatalogo,
  type DireccionSugerencia,
} from "../services/geografiaApi";
import { PROVINCIAS, TIPOS_VIA } from "../utils/addressCatalog";
import { uppercaseInput, uppercaseInputPreservingCursor } from "../utils/text";

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
  localidad?: string | null;
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
  const provinciaActual = uppercaseInput(value.provincia || "");
  const municipioActual = uppercaseInput(value.municipio || "");
  const codigoPostalActual = String(value.codigoPostal || "").replace(/\D/g, "").slice(0, 5);
  const [municipioQuery, setMunicipioQuery] = useState(municipioActual);
  const debouncedMunicipio = useDebouncedValue(municipioQuery, 250);
  const debouncedPostal = useDebouncedValue(codigoPostalActual, 300);

  useEffect(() => setMunicipioQuery(municipioActual), [municipioActual]);

  const provinciasQuery = useQuery({
    queryKey: ["catalogos", "geografia", "provincias"],
    queryFn: getProvinciasCatalogo,
    staleTime: 24 * 60 * 60 * 1000,
  });
  const municipiosQuery = useQuery({
    queryKey: ["catalogos", "geografia", "municipios", provinciaActual, debouncedMunicipio],
    queryFn: () => getMunicipiosCatalogo(provinciaActual, debouncedMunicipio),
    enabled: Boolean(provinciaActual),
    staleTime: 10 * 60 * 1000,
  });
  const codigosPostalesQuery = useQuery({
    queryKey: ["catalogos", "geografia", "codigos-postales", provinciaActual, municipioActual],
    queryFn: () => getCodigosPostales(provinciaActual, municipioActual),
    enabled: Boolean(provinciaActual && municipioActual),
    staleTime: 30 * 60 * 1000,
  });  const codigoPostalQuery = useQuery({
    queryKey: ["catalogos", "geografia", "codigo-postal", debouncedPostal],
    queryFn: () => getDireccionesSugeridas(debouncedPostal, 100),
    enabled: debouncedPostal.length === 5,
    staleTime: 30 * 60 * 1000,
  });

  const provincias = useMemo(() => {
    const oficiales = provinciasQuery.data?.map((item) => uppercaseInput(item.nombre)) || [];
    const todas = oficiales.length ? oficiales : PROVINCIAS;
    return provinciaActual && !todas.some((item) => normalizeKey(item) === normalizeKey(provinciaActual))
      ? [provinciaActual, ...todas]
      : todas;
  }, [provinciaActual, provinciasQuery.data]);
  const municipios = municipiosQuery.data?.contenido || [];
  const opcionesPostales = useMemo(() => {
    const grouped = new Map<string, Set<string>>();
    (codigosPostalesQuery.data || []).forEach((item) => {
      const localidades = grouped.get(item.codigoPostal) || new Set<string>();
      if (item.localidad) localidades.add(item.localidad);
      grouped.set(item.codigoPostal, localidades);
    });
    return [...grouped.entries()].map(([codigoPostal, localidades]) => ({
      codigoPostal,
      localidades: [...localidades].sort((left, right) => left.localeCompare(right, "es")),
    }));
  }, [codigosPostalesQuery.data]);

  const destinosPostales = useMemo(() => {
    const unique = new Map<string, DireccionSugerencia>();
    (codigoPostalQuery.data || []).forEach((item) => unique.set(
      `${normalizeKey(item.provincia)}|${normalizeKey(item.municipio)}`,
      item,
    ));
    return [...unique.values()];
  }, [codigoPostalQuery.data]);
  const destinosLocalidadActual = useMemo(() => {
    const localidadNormalizada = normalizeKey(value.localidad || "");
    if (!localidadNormalizada) return [];
    const unique = new Map<string, DireccionSugerencia>();
    (codigoPostalQuery.data || [])
      .filter((item) => normalizeKey(item.localidad || "") === localidadNormalizada)
      .forEach((item) => unique.set(`${normalizeKey(item.provincia)}|${normalizeKey(item.municipio)}`, item));
    return [...unique.values()];
  }, [codigoPostalQuery.data, value.localidad]);
  const destinoPostalUnico = destinosPostales.length === 1
    ? destinosPostales[0]
    : (destinosLocalidadActual.length === 1 ? destinosLocalidadActual[0] : null);
  const localidadesCodigoActual = useMemo(() => [...new Set(
    (codigoPostalQuery.data || []).map((item) => uppercaseInput(item.localidad || "")).filter(Boolean),
  )].sort((left, right) => left.localeCompare(right, "es")), [codigoPostalQuery.data]);

  useEffect(() => {
    if (!destinoPostalUnico || codigoPostalActual.length !== 5) return;
    const provincia = uppercaseInput(destinoPostalUnico.provincia || "");
    const municipio = uppercaseInput(destinoPostalUnico.municipio || destinoPostalUnico.localidad || "");
    const localidadHistorica = localidadesCodigoActual.find(
      (localidad) => normalizeKey(localidad) === normalizeKey(municipioActual),
    );
    const localidad = value.localidad
      || localidadHistorica
      || (localidadesCodigoActual.length === 1 ? localidadesCodigoActual[0] : "");
    const direccionYaNormalizada = normalizeKey(provinciaActual) === normalizeKey(provincia)
      && normalizeKey(municipioActual) === normalizeKey(municipio)
      && normalizeKey(value.localidad || "") === normalizeKey(localidad);
    if (direccionYaNormalizada) return;
    onChange({ ...value, codigoPostal: codigoPostalActual, provincia, municipio, localidad });
  }, [
    codigoPostalActual,
    destinoPostalUnico?.municipio,
    destinoPostalUnico?.provincia,
    localidadesCodigoActual.join("|"),
    municipioActual,
    provinciaActual,
    value.localidad,
  ]);
  const updateInput = (field: keyof AddressValue, event: ChangeEvent<HTMLInputElement>) => {
    uppercaseInputPreservingCursor(event, (nextValue) => onChange({ ...value, [field]: nextValue }));
  };
  const updateProvincia = (rawValue: string) => {
    const provincia = uppercaseInput(rawValue);
    onChange({ ...value, provincia, municipio: "" });
    setMunicipioQuery("");
  };
  const updateMunicipio = (rawValue: string) => {
    const municipio = uppercaseInput(rawValue);
    setMunicipioQuery(municipio);
    onChange({ ...value, municipio });
  };
  const updateCodigoPostal = (event: ChangeEvent<HTMLInputElement>) => {
    onChange({ ...value, codigoPostal: event.target.value.replace(/\D/g, "").slice(0, 5) });
  };


  const hasStructuredAddress = [
    value.tipoVia, value.nombreVia, value.numeroVia, value.bloque, value.portal, value.escalera,
    value.piso, value.puerta, value.codigoPostal, value.localidad, value.provincia, value.municipio,
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
        <input list={`${idPrefix}-tipos-via`} value={value.tipoVia || ""} onChange={(event) => updateInput("tipoVia", event)} />
        <datalist id={`${idPrefix}-tipos-via`}>
          {TIPOS_VIA.map((tipo) => <option key={tipo} value={tipo} />)}
        </datalist>
      </label>
      <label className={fieldClassName("NombreVia", "address-field address-field--street")} data-field={fieldName("NombreVia")}>
        Via
        <input value={value.nombreVia || ""} onChange={(event) => updateInput("nombreVia", event)} />
      </label>
      <label className={fieldClassName("NumeroVia", "address-field address-field--xs")} data-field={fieldName("NumeroVia")}>
        Num.
        <input maxLength={20} value={value.numeroVia || ""} onChange={(event) => updateInput("numeroVia", event)} />
      </label>
      <label className={fieldClassName("Bloque", "address-field address-field--xs")} data-field={fieldName("Bloque")}>
        Bloq.
        <input maxLength={20} value={value.bloque || ""} onChange={(event) => updateInput("bloque", event)} />
      </label>
      <label className={fieldClassName("Portal", "address-field address-field--xs")} data-field={fieldName("Portal")}>
        Portal
        <input maxLength={20} value={value.portal || ""} onChange={(event) => updateInput("portal", event)} />
      </label>
      <label className={fieldClassName("Escalera", "address-field address-field--xs")} data-field={fieldName("Escalera")}>
        Esc.
        <input maxLength={20} value={value.escalera || ""} onChange={(event) => updateInput("escalera", event)} />
      </label>
      <label className={fieldClassName("Piso", "address-field address-field--xs")} data-field={fieldName("Piso")}>
        Piso
        <input maxLength={20} value={value.piso || ""} onChange={(event) => updateInput("piso", event)} />
      </label>
      <label className={fieldClassName("Puerta", "address-field address-field--xs")} data-field={fieldName("Puerta")}>
        Pta.
        <input maxLength={20} value={value.puerta || ""} onChange={(event) => updateInput("puerta", event)} />
      </label>
      <label className={fieldClassName("Provincia", "address-field address-field--province")} data-field={fieldName("Provincia")}>
        Provincia
        <select value={provinciaActual} onChange={(event) => updateProvincia(event.target.value)}>
          <option value="">Selecciona provincia</option>
          {provincias.map((provincia) => <option key={provincia} value={provincia}>{provincia}</option>)}
        </select>
      </label>
      <label className={fieldClassName("Municipio", "address-field address-field--municipality")} data-field={fieldName("Municipio")}>
        Municipio
        <div className="address-input-status">
          <input
            list={`${idPrefix}-municipios`}
            value={municipioActual}
            onChange={(event) => updateMunicipio(event.target.value)}
            placeholder={provinciaActual ? "Busca y selecciona municipio" : "Selecciona provincia"}
            disabled={!provinciaActual}
            role="combobox"
            aria-autocomplete="list"
          />
          {municipiosQuery.isFetching ? <Loader2 aria-hidden="true" className="spin" size={15} /> : null}
        </div>
        <datalist id={`${idPrefix}-municipios`}>
          {municipios.map((municipio) => <option key={municipio.codigo} value={uppercaseInput(municipio.nombre)} />)}
        </datalist>
        <small>
          {provinciaActual
            ? `${municipiosQuery.data?.totalElementos ?? 0} municipios coinciden.`
            : "Selecciona primero una provincia."}
        </small>
      </label>
      <div className={fieldClassName("CodigoPostal", "address-field address-field--postal address-postal-picker")} data-field={fieldName("CodigoPostal")}>
        <label htmlFor={`${idPrefix}-codigo-postal`}>C.P.</label>
        <div className="address-input-status">
          <input
            id={`${idPrefix}-codigo-postal`}
            list={`${idPrefix}-codigos-postales`}
            inputMode="numeric"
            maxLength={5}
            value={codigoPostalActual}
            onChange={updateCodigoPostal}
            placeholder="Escribe o selecciona CP"
            aria-describedby={`${idPrefix}-codigo-postal-ayuda`}
          />
          {codigosPostalesQuery.isFetching || codigoPostalQuery.isFetching ? <Loader2 aria-hidden="true" className="spin" size={15} /> : null}
        </div>
        <datalist id={`${idPrefix}-codigos-postales`}>
          {opcionesPostales.map((option) => (
            <option key={option.codigoPostal} value={option.codigoPostal}>
              {option.localidades.join(" · ")}
            </option>
          ))}
        </datalist>
        <small id={`${idPrefix}-codigo-postal-ayuda`}>
          {codigoPostalActual.length === 5 && destinoPostalUnico
            ? `${destinoPostalUnico.municipio} · ${destinoPostalUnico.provincia}`
            : codigoPostalActual.length === 5 && destinosPostales.length > 1
              ? `${destinosPostales.length} municipios posibles; selecciona una localidad.`
              : "Escritura libre; provincia y municipio añaden sugerencias."}
        </small>
      </div>
      <label className="address-field address-field--locality">
        Localidad <span className="address-field__optional">(opcional)</span>
        <input
          list={`${idPrefix}-localidades`}
          value={value.localidad || ""}
          onChange={(event) => updateInput("localidad", event)}
          placeholder={codigoPostalActual.length === 5 ? "Selecciona o escribe localidad" : "Completa el CP"}
        />
        <datalist id={`${idPrefix}-localidades`}>
          {localidadesCodigoActual.map((localidad) => <option key={localidad} value={localidad} />)}
        </datalist>
        <small>{localidadesCodigoActual.length ? `${localidadesCodigoActual.length} localidades disponibles.` : "Dato opcional."}</small>
      </label>
      <div className="address-catalog-note">
        <MapPin size={14} />
        <span>Municipios INE 2026 · CP y localidades Gestión Tráfico</span>
      </div>
    </div>
  );
}

function useDebouncedValue<T>(value: T, delay: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [delay, value]);
  return debounced;
}

function normalizeKey(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Z0-9]+/gi, " ")
    .trim()
    .toUpperCase();
}
