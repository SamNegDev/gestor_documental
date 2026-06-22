export interface JustificanteThempusRequest {
  despacho?: string;
  nif?: string;
  version?: string;
  jefatura?: string;
  diasValidez?: string;
  sucursal?: string;
  tipoTramite?: string;
  documentos?: string;
  expedientePlataforma?: string;
  motivo?: string;
  adquirente: JustificanteThempusAdquirente;
  vehiculo: JustificanteThempusVehiculo;
}

export interface JustificanteThempusAdquirente {
  razonSocial?: string;
  nombre?: string;
  apellido1?: string;
  apellido2?: string;
  dni?: string;
  sexo?: string;
  siglasDireccion?: string;
  nombreViaDireccion?: string;
  kmDireccion?: string;
  hectometroDireccion?: string;
  numeroDireccion?: string;
  letraDireccion?: string;
  escaleraDireccion?: string;
  pisoDireccion?: string;
  puertaDireccion?: string;
  bloqueDireccion?: string;
  municipio?: string;
  pueblo?: string;
  provincia?: string;
  cp?: string;
  ifa?: string;
}

export interface JustificanteThempusVehiculo {
  tipoVehiculo?: string;
  matricula?: string;
  marca?: string;
  modelo?: string;
  numeroBastidor?: string;
}

export interface JustificanteThempusPreviewResponse {
  method: string;
  urlRedacted: string;
  headers: string[];
  bodyBytes: number;
  xml: string;
}

export interface JustificanteThempusSendResponse {
  enabled: boolean;
  enviado: boolean;
  statusCode: number;
  responseBody?: string | null;
  urlRedacted: string;
}
