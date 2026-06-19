export type CatalogoGestionResumen = {
  personas: number;
  representantes: number;
  vehiculos: number;
  personasDisponibles: boolean;
  representantesDisponibles: boolean;
  vehiculosDisponibles: boolean;
};

export type ImportacionCatalogo = {
  tipo: string;
  registrosLeidos: number;
  registrosImportados: number;
  registrosOmitidos: number;
  reemplazoCompleto: boolean;
  mensaje: string;
};

export type GestionPersonaCatalogo = {
  id: number;
  nifNormalizado?: string | null;
  nif?: string | null;
  tipoPersonaSugerido?: string | null;
  apellido1RazonSocial?: string | null;
  apellido2?: string | null;
  nombre?: string | null;
  sexo?: string | null;
  fechaNacimiento?: string | null;
  telefono?: string | null;
  telefonoMovil?: string | null;
  email?: string | null;
  dirSiglas?: string | null;
  dirCalle?: string | null;
  dirNumero?: string | null;
  dirPiso?: string | null;
  dirPuerta?: string | null;
  dirMunicipio?: string | null;
  dirPueblo?: string | null;
  dirProvincia?: string | null;
  dirCp?: string | null;
  dirPais?: string | null;
};

export type GestionRepresentanteCatalogo = {
  id: number;
  empresaNifNormalizado?: string | null;
  empresaNif?: string | null;
  empresaTipoPersonaSugerido?: string | null;
  empresaApellido1RazonSocial?: string | null;
  representanteNifNormalizado?: string | null;
  representanteNif?: string | null;
  representanteTipoPersonaSugerido?: string | null;
  representanteApellido1RazonSocial?: string | null;
  representanteApellido2?: string | null;
  representanteNombre?: string | null;
  representanteSexo?: string | null;
  representanteFechaNacimiento?: string | null;
  reprConcepto?: string | null;
  reprDocAcreditacion?: string | null;
  representanteDirSiglas?: string | null;
  representanteDirCalle?: string | null;
  representanteDirNumero?: string | null;
  representanteDirPiso?: string | null;
  representanteDirPuerta?: string | null;
  representanteDirMunicipio?: string | null;
  representanteDirPueblo?: string | null;
  representanteDirProvincia?: string | null;
  representanteDirCp?: string | null;
  representanteDirPais?: string | null;
};

export type GestionVehiculoCatalogo = {
  id: number;
  matriculaNormalizada?: string | null;
  matricula?: string | null;
  bastidor?: string | null;
  bastidorNormalizado?: string | null;
  marca?: string | null;
  modeloSugerido?: string | null;
  modeloTransmision?: string | null;
  modeloMatriculacion?: string | null;
  fechaMatriculacion?: string | null;
  fechaPrimeraMatriculacion?: string | null;
  anyoFabricacion?: string | null;
  carburanteCodigo?: string | null;
  carburanteDescripcion?: string | null;
  clasificacionItv?: string | null;
  codigoItv?: string | null;
  codigo620TipoVehiculo?: string | null;
  tipo620Descripcion?: string | null;
  potencia?: string | null;
  cilindrada?: string | null;
  fechaItv?: string | null;
  completitudScore?: string | null;
};

export type CatalogoGestionKind = "personas" | "representantes" | "vehiculos";
