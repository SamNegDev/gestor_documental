import { apiGet, apiPostJson } from "../../../shared/api/http";
import type { NotificacionPreview, NotificacionResultado, SeguimientoPage } from "../types";
export function getSeguimientos(filters:{vista:string;clienteId?:string;anio?:string;pagina:number;tamanio:number}) { const p=new URLSearchParams(); Object.entries(filters).forEach(([k,v])=>{if(v!==undefined&&v!=="")p.set(k,String(v));}); return apiGet<SeguimientoPage>(`/api/seguimiento-clientes?${p}`); }
export const getNotificacionPreview=(id:number)=>apiGet<NotificacionPreview>(`/api/seguimiento-clientes/${id}/notificacion-preview`);
export const notificarSeguimiento=(id:number,body:{asunto:string;mensaje:string})=>apiPostJson<NotificacionResultado>(`/api/seguimiento-clientes/${id}/notificar`,body);
export const archivarSeguimiento=(id:number)=>apiPostJson<void>(`/api/seguimiento-clientes/${id}/archivar`,{});
export const reactivarSeguimiento=(id:number)=>apiPostJson<void>(`/api/seguimiento-clientes/${id}/reactivar`,{});
