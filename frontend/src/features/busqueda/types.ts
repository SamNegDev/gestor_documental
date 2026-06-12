export interface BusquedaGlobalItem { id:string; titulo:string; detalle:string; meta:string; enlace:string; }
export interface BusquedaGlobalResultado {
  expedientes: BusquedaGlobalItem[];
  solicitudes: BusquedaGlobalItem[];
  interesados: BusquedaGlobalItem[];
  vehiculos: BusquedaGlobalItem[];
}
