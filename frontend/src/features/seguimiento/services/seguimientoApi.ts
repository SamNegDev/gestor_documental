import { apiGet, apiPostJson, apiPutJsonResponse } from "../../../shared/api/http";
import type { NotificacionPreview, NotificacionResultado, SeguimientoConfig, SeguimientoPage } from "../types";
export function getSeguimientos(filters:{vista:string;accion?:string;clienteId?:string;anio?:string;pagina:number;tamanio:number}) { const p=new URLSearchParams(); Object.entries(filters).forEach(([k,v])=>{if(v!==undefined&&v!=="")p.set(k,String(v));}); return apiGet<SeguimientoPage>(`/api/seguimiento-clientes?${p}`); }
export const getNotificacionPreview=(id:number)=>apiGet<NotificacionPreview>(`/api/seguimiento-clientes/${id}/notificacion-preview`);
export const prepararNotificacionExpediente=(id:number)=>apiPostJson<NotificacionPreview>(`/api/seguimiento-clientes/expedientes/${id}/preparar-notificacion`,{});
export const notificarSeguimiento=(id:number,body:{asunto:string;mensaje:string})=>apiPostJson<NotificacionResultado>(`/api/seguimiento-clientes/${id}/notificar`,body);
export const posponerSeguimiento=(id:number,body:{proximoAviso:string})=>apiPostJson<void>(`/api/seguimiento-clientes/${id}/posponer`,body);
export const archivarSeguimiento=(id:number)=>apiPostJson<void>(`/api/seguimiento-clientes/${id}/archivar`,{});
export const reactivarSeguimiento=(id:number)=>apiPostJson<void>(`/api/seguimiento-clientes/${id}/reactivar`,{});
export const getSeguimientoConfig=()=>apiGet<SeguimientoConfig>("/api/admin/seguimiento-config");
export const updateSeguimientoConfig=(body:SeguimientoConfig)=>apiPutJsonResponse<SeguimientoConfig>("/api/admin/seguimiento-config",body);
