package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.PreferenciaCanalCliente;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.service.CorreoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class ResumenFinalizadosDiarioService {
 private static final DateTimeFormatter F=DateTimeFormatter.ofPattern("dd/MM/yyyy"), H=DateTimeFormatter.ofPattern("HH:mm");
 private static final CorreoService.ImagenInline LOGO=new CorreoService.ImagenInline("gestoria-cn-logo","static/assets/logos/casado-negrin-logo.png","image/png","logo.png");
 private final HistorialCambioRepository historialCambioRepository; private final CorreoService correoService;
 @Value("${app.daily-finalized.bcc-recipients:}") private String bcc;
 @Value("${app.daily-finalized.zone:Atlantic/Canary}") private String zone;
 Resultado enviarDelDia(){ZoneId z=ZoneId.of(zone);LocalDate d=LocalDate.now(z);return enviar(d,historialCambioRepository.findFinalizacionesExpedienteEntre(d.atStartOfDay(),d.plusDays(1).atStartOfDay()));}
 public Resultado enviarClienteDelDia(Long clienteId){ZoneId z=ZoneId.of(zone);LocalDate d=LocalDate.now(z);return enviar(d,historialCambioRepository.findFinalizacionesExpedienteClienteEntre(clienteId,d.atStartOfDay(),d.plusDays(1).atStartOfDay()));}
 private Resultado enviar(LocalDate d,List<HistorialCambio> cambios){Map<Long,Grupo> grupos=new LinkedHashMap<>();
  for(var c:cambios){var e=c.getExpediente();var cl=e==null?null:e.getCliente();if(e==null||e.getId()==null||cl==null||cl.getId()==null)continue;grupos.computeIfAbsent(cl.getId(),x->new Grupo(cl,new LinkedHashMap<>())).items.put(e.getId(),new Item(e,c.getFechaCambio()));}
  int enviados=0,total=0;List<String> avisos=new ArrayList<>();for(var g:grupos.values()){if(!permite(g.cliente))continue;var items=List.copyOf(g.items.values());if(items.isEmpty())continue;var r=correoService.enviarHtml(g.cliente.getEmail(),asunto(d,items.size()),html(g.cliente,d,items),texto(g.cliente,d,items),copias(g.cliente.getEmail()),LOGO);if(r.exito()){enviados++;total+=items.size();}else avisos.add(g.cliente.getEmail()+": "+r.error());}return new Resultado(enviados,total,List.copyOf(avisos));} private boolean permite(Cliente c){if(c==null||!StringUtils.hasText(c.getEmail()))return false;var p=c.getPreferenciaCanal()==null?PreferenciaCanalCliente.AMBOS:c.getPreferenciaCanal();return p==PreferenciaCanalCliente.EMAIL||p==PreferenciaCanalCliente.AMBOS;}
 private String asunto(LocalDate d,int n){return (n==1?"Tramite finalizado hoy":"Tramites finalizados hoy ("+n+")")+" - "+d.format(F);}
 private String texto(Cliente c,LocalDate d,List<Item> xs){StringBuilder s=new StringBuilder("Hola ").append(c.getNombre()).append(",\n\nTramites finalizados hoy, ").append(d.format(F)).append(":\n");xs.forEach(x->s.append("- ").append(ref(x.e)).append(" | ").append(tipo(x.e)).append(" | ").append(hora(x.fecha)).append("\n"));return s.append("\nGestoria Casado & Negrin").toString();}
 private String html(Cliente c,LocalDate d,List<Item> xs){StringBuilder filas=new StringBuilder();xs.forEach(x->filas.append("<tr><td style='padding:12px;border-bottom:1px solid #e5e7eb;font-weight:700'>").append(esc(ref(x.e))).append("</td><td style='padding:12px;border-bottom:1px solid #e5e7eb'>").append(esc(tipo(x.e))).append("</td><td style='padding:12px;border-bottom:1px solid #e5e7eb;color:#166534'>").append(hora(x.fecha)).append("</td></tr>"));return "<html><body style='margin:0;background:#f3f5f8;font-family:Arial;color:#172033'><div style='max-width:680px;margin:28px auto;background:white;border-radius:16px;overflow:hidden'><div style='padding:26px 30px;background:#172033;color:white'><small>RESUMEN DIARIO</small><h1 style='margin:7px 0 0'>Trámites finalizados</h1></div><div style='padding:28px 30px'><p>Hola "+esc(c.getNombre())+",</p><p>Estos son los expedientes finalizados hoy, "+d.format(F)+".</p><p style='color:#166534;font-weight:700'>"+xs.size()+" expediente(s) finalizado(s)</p><table width='100%' cellspacing='0' style='border:1px solid #e5e7eb'><tr style='background:#f8fafc'><th align='left' style='padding:10px'>Expediente</th><th align='left'>Trámite</th><th align='left'>Hora</th></tr>"+filas+"</table></div><div style='text-align:center;padding:20px;border-top:1px solid #eee'><img src='cid:gestoria-cn-logo' style='max-width:180px'><p style='font-size:12px;color:#94a3b8'>Aviso automático independiente de incidencias</p></div></div></body></html>";}
 private String ref(Expediente e){return StringUtils.hasText(e.getMatricula())?e.getMatricula():"EXP-"+e.getId();} private String tipo(Expediente e){if(e.getTipoTramite()==null)return "Tramite";return StringUtils.hasText(e.getTipoTramite().getDescripcion())?e.getTipoTramite().getDescripcion():e.getTipoTramite().getNombre().name();} private String hora(LocalDateTime f){return f==null?"--:--":f.format(H);} private List<String> copias(String to){if(!StringUtils.hasText(bcc))return List.of();return Arrays.stream(bcc.split("[,;]")).map(String::trim).filter(StringUtils::hasText).filter(x->!x.equalsIgnoreCase(to)).distinct().toList();} private String esc(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
 record Item(Expediente e,LocalDateTime fecha){} record Grupo(Cliente cliente,LinkedHashMap<Long,Item> items){} public record Resultado(int correos,int expedientes,List<String> avisos){}
}