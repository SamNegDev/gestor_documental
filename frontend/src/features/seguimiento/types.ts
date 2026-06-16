import type { PagedResponse } from "../listados/types";
export interface Seguimiento { incidenciaId:number; expedienteId:number; matricula?:string|null; clienteId?:number|null; cliente?:string|null; tipoIncidencia?:string|null; observaciones?:string|null; avisosEnviados:number; maxAvisos:number; fechaPrimerAviso?:string|null; fechaUltimoAviso?:string|null; proximoAviso?:string|null; pendienteNotificacion:boolean; archivada:boolean; fechaArchivo?:string|null; anioExpediente:number; }
export type SeguimientoPage = PagedResponse<Seguimiento>;
export interface NotificacionPreview { incidenciaId:number; destinatario:string; asunto:string; mensaje:string; numeroAviso:number; maxAvisos:number; envioReal:boolean; proveedor?:string|null; }
export interface NotificacionResultado { exito:boolean; simulado:boolean; mensaje:string; }
export interface SeguimientoConfig { diasAviso1:number; diasAviso2:number; diasAviso3:number; diasAviso4:number; diasAviso5:number; maxAvisos:number; diasExpedienteEstancado:number; }
