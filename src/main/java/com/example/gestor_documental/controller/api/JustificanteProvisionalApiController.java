package com.example.gestor_documental.controller.api;
import com.example.gestor_documental.dto.factura.JustificanteProvisionalResponse;
import com.example.gestor_documental.enums.EstadoJustificanteProvisional;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.impl.JustificanteProvisionalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController @RequestMapping("/api/solicitudes/{solicitudId}/justificante-provisional") @RequiredArgsConstructor
public class JustificanteProvisionalApiController {
 private final JustificanteProvisionalService service; private final CurrentUserService currentUserService;
 @GetMapping public JustificanteProvisionalResponse obtener(@PathVariable Long solicitudId,Authentication auth){return service.obtener(solicitudId,currentUserService.requireUser(auth));}
 @PostMapping("/solicitar") public JustificanteProvisionalResponse solicitar(@PathVariable Long solicitudId,Authentication auth){return service.solicitar(solicitudId,currentUserService.requireUser(auth));}
 @PutMapping("/estado/{estado}") public JustificanteProvisionalResponse estado(@PathVariable Long solicitudId,@PathVariable EstadoJustificanteProvisional estado,Authentication auth){return service.cambiarEstado(solicitudId,estado,currentUserService.requireAdmin(auth));}
 @PostMapping(value="/archivo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public JustificanteProvisionalResponse archivo(@PathVariable Long solicitudId,@RequestParam MultipartFile archivo,Authentication auth)throws IOException{return service.adjuntar(solicitudId,archivo,currentUserService.requireAdmin(auth));}
 @GetMapping("/archivo") public ResponseEntity<byte[]> descargar(@PathVariable Long solicitudId,Authentication auth)throws IOException{var d=service.descargar(solicitudId,currentUserService.requireUser(auth));return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+d.filename().replace("\"","")+"\"").body(d.bytes());}
}
