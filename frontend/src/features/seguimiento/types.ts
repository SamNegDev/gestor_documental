import type { PagedResponse } from "../listados/types";
export interface Seguimiento { incidenciaId:number; expedienteId:number; matricula?:string|null; clienteId?:number|null; cliente?:string|null; tipoIncidencia?:string|null; observaciones?:string|null; avisosEnviados:number; fechaPrimerAviso?:string|null; fechaUltimoAviso?:string|null; proximoAviso?:string|null; pendienteNotificacion:boolean; archivada:boolean; fechaArchivo?:string|null; anioExpediente:number; }
export type SeguimientoPage = PagedResponse<Seguimiento>;
export interface NotificacionPreview { incidenciaId:number; destinatario:string; asunto:string; mensaje:string; numeroAviso:number; envioReal:boolean; }
export interface NotificacionResultado { exito:boolean; simulado:boolean; mensaje:string; }
