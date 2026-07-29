import { apiDelete, apiGet, apiPost, apiPostForm, apiPutJsonResponse } from "../../../shared/api/http";
import type { AnalisisFactura, EstadoComprobante, FacturaDetalle, FacturasPage, ModalidadFacturacion } from "../types";
export function listarFacturas(params:URLSearchParams){return apiGet<FacturasPage>(`/api/facturas?${params}`)}
export function sincronizarFacturas(){return apiPost("/api/facturas/sincronizar")}
export function aportarComprobante(id:number,archivo:File){const form=new FormData();form.append("archivo",archivo);return apiPostForm(`/api/facturas/${id}/comprobantes`,form)}
export function revisarComprobante(id:number,estado:EstadoComprobante){return apiPutJsonResponse(`/api/facturas/comprobantes/${id}`,{estado})}
export function abrirPdf(id:number){window.open(`/api/facturas/${id}/pdf`,"_blank","noopener,noreferrer")}
export function abrirComprobante(id:number){window.open(`/api/facturas/comprobantes/${id}/archivo`,"_blank","noopener,noreferrer")}
export async function descargarZip(ids:number[]){const response=await fetch("/api/facturas/zip",{method:"POST",credentials:"include",headers:{"Content-Type":"application/json"},body:JSON.stringify(ids)});if(!response.ok)throw new Error("No se pudo preparar el ZIP");const url=URL.createObjectURL(await response.blob());const a=document.createElement("a");a.href=url;a.download="facturas.zip";a.click();URL.revokeObjectURL(url)}

export function analizarFacturas(archivos:File[]){const form=new FormData();archivos.forEach(a=>form.append("archivos",a));return apiPostForm<AnalisisFactura[]>("/api/facturas/analizar",form)}
export function confirmarFactura(input:{facturaId?:number;modalidad:ModalidadFacturacion;periodoDesde?:string;periodoHasta?:string;expedienteIds:number[];expedienteIdsManuales?:number[];lineasAsignadasManualmente?:number[];archivo:File}){const form=new FormData();if(input.facturaId)form.append("facturaId",String(input.facturaId));form.append("modalidad",input.modalidad);if(input.periodoDesde)form.append("periodoDesde",input.periodoDesde);if(input.periodoHasta)form.append("periodoHasta",input.periodoHasta);input.expedienteIds.forEach(id=>form.append("expedienteIds",String(id)));input.expedienteIdsManuales?.forEach(id=>form.append("expedienteIdsManuales",String(id)));input.lineasAsignadasManualmente?.forEach(index=>form.append("lineasAsignadasManualmente",String(index)));form.append("archivo",input.archivo);return apiPostForm<AnalisisFactura>("/api/facturas/confirmar",form)}
export function obtenerFacturaDetalle(id:number){return apiGet<FacturaDetalle>(`/api/facturas/${id}`)}
export function corregirVinculacionFactura(facturaId:number,vinculacionId:number,expedienteId:number){return apiPutJsonResponse<FacturaDetalle>(`/api/facturas/${facturaId}/vinculaciones/${vinculacionId}`,{expedienteId})}
export function asignarLineaPendienteFactura(facturaId:number,indice:number,expedienteId:number){return apiPutJsonResponse<FacturaDetalle>(`/api/facturas/${facturaId}/lineas-pendientes/${indice}`,{expedienteId})}

export function eliminarFactura(id:number){return apiDelete(`/api/facturas/${id}`)}
