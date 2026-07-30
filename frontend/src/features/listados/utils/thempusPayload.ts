import type { SolicitudDetail } from "../types";

export const THEMPUS_JUSTIFICANTES_URL = "https://thempus.com/despachos/2839/justificantes?#gestor-documental=nuevo";
export const THEMPUS_PAYLOAD_KIND = "GESTOR_DOCUMENTAL_JUSTIFICANTE_V1";

export interface ThempusJustificantePayload {
  kind: typeof THEMPUS_PAYLOAD_KIND;
  createdAt: string;
  solicitudId: number;
  adquirente: {
    nif: string; nombre: string; apellido1: string; apellido2: string; razonSocial: string;
    provincia: string; municipio: string; localidad: string; codigoPostal: string;
    tipoVia: string; nombreVia: string; numeroVia: string; bloque: string;
    escalera: string; piso: string; puerta: string;
  };
  vehiculo: { matricula: string; bastidor: string; marca: string; modelo: string };
}

const clean = (value?: string | null): string => value?.trim() ?? "";

export function buildThempusPayload(solicitud: SolicitudDetail): ThempusJustificantePayload {
  const compradores = solicitud.interesados.filter((interesado) => interesado.rol?.toUpperCase() === "COMPRADOR");
  if (compradores.length === 0) throw new Error("La solicitud no tiene un comprador final identificado.");
  if (compradores.length > 1) throw new Error("La solicitud tiene varios compradores; revisa cuál es el comprador final.");
  const comprador = compradores[0];
  const nif = clean(comprador.dni).toUpperCase().replace(/[\s.-]/g, "");
  const esEmpresa = Boolean(comprador.personaJuridica || comprador.razonSocial || /^[ABCDEFGHJNPQRSUVW]/.test(nif));
  const razonSocial = esEmpresa ? clean(comprador.razonSocial || comprador.nombre) : "";
  return {
    kind: THEMPUS_PAYLOAD_KIND, createdAt: new Date().toISOString(), solicitudId: solicitud.id,
    adquirente: {
      nif, nombre: esEmpresa ? "" : clean(comprador.nombrePila), apellido1: esEmpresa ? "" : clean(comprador.apellido1),
      apellido2: esEmpresa ? "" : clean(comprador.apellido2), razonSocial, provincia: clean(comprador.provincia),
      municipio: clean(comprador.municipio), localidad: clean(comprador.localidad), codigoPostal: clean(comprador.codigoPostal),
      tipoVia: clean(comprador.tipoVia), nombreVia: clean(comprador.nombreVia || comprador.direccion), numeroVia: clean(comprador.numeroVia),
      bloque: clean(comprador.bloque), escalera: clean(comprador.escalera), piso: clean(comprador.piso), puerta: clean(comprador.puerta),
    },
    vehiculo: {
      matricula: clean(solicitud.vehiculo?.matricula || solicitud.matricula), bastidor: clean(solicitud.vehiculo?.bastidor),
      marca: clean(solicitud.vehiculo?.marca), modelo: clean(solicitud.vehiculo?.modelo),
    },
  };
}

export function getMissingThempusFields(payload: ThempusJustificantePayload): string[] {
  const namePresent = payload.adquirente.razonSocial || (payload.adquirente.nombre && payload.adquirente.apellido1);
  const fields: Array<[string, string | boolean]> = [
    ["NIF/CIF/NIE del comprador", payload.adquirente.nif], ["nombre y primer apellido o razón social", namePresent],
    ["provincia", payload.adquirente.provincia], ["municipio", payload.adquirente.municipio], ["código postal", payload.adquirente.codigoPostal],
    ["tipo de vía", payload.adquirente.tipoVia], ["vía o dirección", payload.adquirente.nombreVia], ["número de vía", payload.adquirente.numeroVia],
    ["matrícula", payload.vehiculo.matricula], ["bastidor", payload.vehiculo.bastidor], ["marca", payload.vehiculo.marca], ["modelo", payload.vehiculo.modelo],
  ];
  return fields.filter(([, value]) => !value).map(([label]) => label);
}
