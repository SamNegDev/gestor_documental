package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.factura.*;
import com.example.gestor_documental.enums.EstadoFacturaHolded;
import com.example.gestor_documental.enums.ModalidadFacturacion;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.impl.HoldedFacturaService;
import com.example.gestor_documental.service.impl.FacturaDocumentoAnalisisService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
public class FacturaHoldedApiController {
 private final HoldedFacturaService service; private final FacturaDocumentoAnalisisService analisisService; private final CurrentUserService currentUserService;
 @GetMapping public PagedResponse<FacturaHoldedResponse> listar(@RequestParam(required=false) String busqueda,@RequestParam(required=false) EstadoFacturaHolded estado,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="25") int tamanio,Authentication auth){return service.listar(currentUserService.requireUser(auth),busqueda,estado,desde,hasta,pagina,tamanio);}
 @PostMapping("/sincronizar") public SincronizacionHoldedResponse sincronizar(Authentication auth){currentUserService.requireAdmin(auth);return service.sincronizarTodo();}
 @PostMapping(value="/analizar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public List<AnalisisFacturaArchivoResponse> analizar(@RequestParam("archivos") List<MultipartFile> archivos,Authentication auth)throws IOException{currentUserService.requireAdmin(auth);return analisisService.analizar(archivos);}
 @PostMapping(value="/confirmar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public AnalisisFacturaArchivoResponse confirmar(@RequestParam(required=false) Long facturaId,@RequestParam ModalidadFacturacion modalidad,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate periodoDesde,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate periodoHasta,@RequestParam List<Long> expedienteIds,@RequestParam(required=false) List<Long> expedienteIdsManuales,@RequestParam(required=false) List<Integer> lineasAsignadasManualmente,@RequestParam MultipartFile archivo,Authentication auth)throws IOException{currentUserService.requireAdmin(auth);return analisisService.confirmar(facturaId,modalidad,periodoDesde,periodoHasta,expedienteIds,expedienteIdsManuales,lineasAsignadasManualmente,archivo);}
 @GetMapping("/{id}") public FacturaDetalleResponse detalle(@PathVariable Long id,Authentication auth){return service.detalle(id,currentUserService.requireUser(auth));}
 @PutMapping("/{facturaId}/vinculaciones/{vinculacionId}") public FacturaDetalleResponse corregirVinculacion(@PathVariable Long facturaId,@PathVariable Long vinculacionId,@RequestBody CorregirVinculacionFacturaRequest request,Authentication auth){Usuario admin=currentUserService.requireAdmin(auth);if(request==null||request.expedienteId()==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Falta expediente");return service.corregirVinculacion(facturaId,vinculacionId,request.expedienteId(),admin);}
 @PutMapping("/{facturaId}/lineas-pendientes/{indice}") public FacturaDetalleResponse asignarLineaPendiente(@PathVariable Long facturaId,@PathVariable int indice,@RequestBody AsignarLineaFacturaRequest request,Authentication auth){Usuario admin=currentUserService.requireAdmin(auth);if(request==null||request.expedienteId()==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Falta expediente");return service.asignarLineaPendiente(facturaId,indice,request.expedienteId(),admin);}
 @GetMapping("/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable Long id,Authentication auth){byte[] body=service.descargarPdf(id,currentUserService.requireUser(auth));return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"factura-"+id+".pdf\"").body(body);}
 @PostMapping("/zip") public void zip(@RequestBody List<Long> ids,Authentication auth,HttpServletResponse response)throws IOException{response.setContentType("application/zip");response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"facturas.zip\"");service.descargarZip(ids,currentUserService.requireUser(auth),response.getOutputStream());}
 @PostMapping(value="/{id}/comprobantes",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ComprobantePagoResponse aportar(@PathVariable Long id,@RequestParam MultipartFile archivo,Authentication auth)throws IOException{return service.aportarComprobante(id,archivo,currentUserService.requireUser(auth));}
 @PutMapping("/comprobantes/{id}") public ComprobantePagoResponse revisar(@PathVariable Long id,@RequestBody RevisarComprobanteRequest request,Authentication auth){return service.revisarComprobante(id,request,currentUserService.requireAdmin(auth));}
 @GetMapping("/comprobantes/{id}/archivo") public ResponseEntity<byte[]> comprobante(@PathVariable Long id,Authentication auth)throws IOException{var d=service.comprobante(id,currentUserService.requireUser(auth));MediaType type;try{type=MediaType.parseMediaType(d.contentType());}catch(Exception e){type=MediaType.APPLICATION_OCTET_STREAM;}return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+d.filename().replace("\"","")+"\"").body(d.bytes());}
}
