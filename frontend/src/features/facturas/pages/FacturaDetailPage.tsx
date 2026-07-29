import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, Download, Search, ShieldCheck, TriangleAlert, X } from "lucide-react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { ApiError } from "../../../shared/api/http";
import { buscarGlobal } from "../../busqueda/services/busquedaGlobalApi";
import { abrirPdf, corregirVinculacionFactura, obtenerFacturaDetalle } from "../services/facturasApi";
import type { FacturaVinculacion } from "../types";
import "./facturas.css";

function CorregirExpediente({facturaId,vinculacion,onDone}:{facturaId:number;vinculacion:FacturaVinculacion;onDone:()=>void}){
  const [abierto,setAbierto]=useState(false); const [query,setQuery]=useState("");
  const search=useQuery({queryKey:["factura-correccion",query],queryFn:()=>buscarGlobal(query),enabled:abierto&&query.trim().length>=2,staleTime:30_000});
  const mutation=useMutation({mutationFn:(expedienteId:number)=>corregirVinculacionFactura(facturaId,vinculacion.id,expedienteId),onSuccess:()=>{setAbierto(false);setQuery("");onDone()}});
  if(!abierto)return <button className="link-button" type="button" onClick={()=>setAbierto(true)}>Cambiar expediente</button>;
  return <div className="invoice-correction"><div className="invoice-correction__input"><Search size={15}/><input autoFocus value={query} onChange={e=>setQuery(e.target.value.toUpperCase())} placeholder="Matrícula, interesado o EXP-ID"/><button type="button" onClick={()=>setAbierto(false)} aria-label="Cancelar"><X size={14}/></button></div>{query.trim().length>=2?<div className="invoice-correction__results">{search.isLoading?<small>Buscando…</small>:search.data?.expedientes.length?search.data.expedientes.map(item=>{const id=Number(item.id.replace(/\D/g,""));return <button key={item.id} type="button" disabled={mutation.isPending||id===vinculacion.expedienteId} onClick={()=>mutation.mutate(id)}><strong>{item.titulo} · {item.id}</strong><small>{item.detalle} · {item.meta}</small></button>}):<small>Sin coincidencias.</small>}</div>:null}{mutation.isError?<small className="invoice-correction__error">{mutation.error instanceof ApiError&&mutation.error.details?mutation.error.details:"No se pudo corregir la asignación."}</small>:null}</div>;
}

export function FacturaDetailPage(){
  const {id}=useParams(); const facturaId=Number(id); const {user}=useOutletContext<AppOutletContext>(); const qc=useQueryClient();
  const query=useQuery({queryKey:["factura-detalle",facturaId],queryFn:()=>obtenerFacturaDetalle(facturaId),enabled:Number.isFinite(facturaId)});
  if(query.isLoading)return <section className="invoice-detail"><div className="empty-state">Cargando factura…</div></section>;
  if(query.isError||!query.data)return <section className="invoice-detail"><div className="alert alert--danger">No se pudo abrir la factura.</div><Link className="soft-button" to="/facturas"><ArrowLeft size={16}/> Volver</Link></section>;
  const {factura,vinculaciones}=query.data;
  return <section className="invoice-detail"><header className="invoice-detail__header"><div><Link to="/facturas"><ArrowLeft size={15}/> Facturas</Link><p className="eyebrow">Conciliación guardada</p><h2>{factura.numero||`Factura #${factura.id}`}</h2><p>{factura.contactoNombre||"Sin cliente"} · {factura.fechaEmision?new Date(`${factura.fechaEmision}T00:00:00`).toLocaleDateString("es-ES"):"Fecha no disponible"}</p></div><button className="soft-button" onClick={()=>abrirPdf(factura.id)}><Download size={16}/> Abrir PDF</button></header>
    {factura.lineasPendientesRevision>0?<div className="invoice-detail__pending"><TriangleAlert size={19}/><div><strong>{factura.lineasPendientesRevision} {factura.lineasPendientesRevision===1?"línea pendiente":"líneas pendientes"}</strong><span>{factura.detalleLineasPendientes||"Hay líneas sin expediente asignado."}</span></div></div>:<div className="invoice-detail__complete"><ShieldCheck size={18}/><span>Todas las líneas registradas están asignadas.</span></div>}
    <div className="invoice-detail__section"><header><div><h3>Expedientes asignados</h3><p>{vinculaciones.length} vinculaciones guardadas</p></div></header>{vinculaciones.length?<div className="invoice-links-table"><div className="invoice-links-table__head"><span>Expediente</span><span>Datos detectados</span><span>Estado</span><span></span></div>{vinculaciones.map(v=><div className="invoice-links-table__row" key={v.id}><div><strong>{v.matricula||`EXP-${v.expedienteId}`}</strong><small>EXP-{v.expedienteId} · {v.cliente}</small></div><div><span>{v.matriculaDetectada||"Sin matrícula detectada"}</span><small>{v.compradorIdentificadorDetectado||"Comprador no detectado"}{v.bastidorDetectado?` · ${v.bastidorDetectado}`:""}</small></div><div><span className="invoice-link-state"><Check size={13}/>{v.estadoVinculacion.toLowerCase().replaceAll("_"," ")}</span><small>{v.confianza}% confianza</small>{v.motivoRevision?<small>{v.motivoRevision}</small>:null}</div><div>{user?.rol==="ADMIN"?<CorregirExpediente facturaId={facturaId} vinculacion={v} onDone={()=>{void query.refetch();void qc.invalidateQueries({queryKey:["facturas"]})}}/>:null}</div></div>)}</div>:<div className="empty-state">Todavía no hay expedientes vinculados.</div>}</div>
  </section>;
}