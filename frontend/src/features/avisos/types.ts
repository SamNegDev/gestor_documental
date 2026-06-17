export interface AvisoAdmin {
  id: number;
  tipo?: string | null;
  titulo?: string | null;
  detalle?: string | null;
  origen?: string | null;
  fechaCreacion?: string | null;
  expedienteId?: number | null;
  matricula?: string | null;
  clienteId?: number | null;
  cliente?: string | null;
}

export interface AvisosAdminResumen {
  pendientes: number;
  avisos: AvisoAdmin[];
}
