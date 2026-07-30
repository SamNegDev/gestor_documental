package com.example.gestor_documental.service.impl;
import com.example.gestor_documental.dto.factura.JustificanteProvisionalResponse;
import com.example.gestor_documental.enums.*;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class JustificanteProvisionalService {
 private final SolicitudRepository solicitudRepository; private final JustificanteProvisionalRepository repository;
 @Value("${app.upload.dir:uploads}") private String uploadDir;
 @Transactional(readOnly=true) public JustificanteProvisionalResponse obtener(Long solicitudId,Usuario usuario){Solicitud s=requireSolicitud(solicitudId,usuario);return repository.findBySolicitudId(solicitudId).map(this::map).orElse(new JustificanteProvisionalResponse(null,s.getId(),EstadoJustificanteProvisional.NO_SOLICITADO,null,null,null));}
 @Transactional public JustificanteProvisionalResponse solicitar(Long solicitudId,Usuario usuario){Solicitud s=requireSolicitud(solicitudId,usuario);validarTraspaso(s);JustificanteProvisional j=repository.findBySolicitudId(solicitudId).orElseGet(()->{JustificanteProvisional n=new JustificanteProvisional();n.setSolicitud(s);return n;});if(j.getEstado()!=EstadoJustificanteProvisional.NO_SOLICITADO&&j.getEstado()!=EstadoJustificanteProvisional.CANCELADO)throw new ResponseStatusException(HttpStatus.CONFLICT,"El justificante ya esta solicitado");j.setEstado(EstadoJustificanteProvisional.SOLICITADO);j.setSolicitadoEn(LocalDateTime.now());j.setActualizadoEn(LocalDateTime.now());return map(repository.save(j));}
 @Transactional public JustificanteProvisionalResponse cambiarEstado(Long solicitudId,EstadoJustificanteProvisional estado,Usuario admin){requireAdmin(admin);JustificanteProvisional j=repository.findBySolicitudId(solicitudId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Justificante no solicitado"));if(estado!=EstadoJustificanteProvisional.EN_PREPARACION&&estado!=EstadoJustificanteProvisional.CANCELADO)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado administrativo no valido");j.setEstado(estado);j.setActualizadoEn(LocalDateTime.now());return map(j);}
 @Transactional public JustificanteProvisionalResponse adjuntar(Long solicitudId,MultipartFile archivo,Usuario admin)throws IOException{requireAdmin(admin);Solicitud s=requireSolicitud(solicitudId,admin);validarTraspaso(s);if(archivo==null||archivo.isEmpty()||archivo.getSize()>10L*1024*1024||!"application/pdf".equalsIgnoreCase(archivo.getContentType()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Adjunta un PDF de hasta 10 MB");JustificanteProvisional j=repository.findBySolicitudId(solicitudId).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"El cliente debe solicitar primero el justificante"));Path base=Paths.get(uploadDir,"justificantes-provisionales").toAbsolutePath().normalize();Files.createDirectories(base);String nombre=UUID.randomUUID()+".pdf";Path destino=base.resolve(nombre);Files.copy(archivo.getInputStream(),destino,StandardCopyOption.REPLACE_EXISTING);j.setNombreArchivo(nombre);j.setNombreOriginal(seguro(archivo.getOriginalFilename()));j.setEstado(EstadoJustificanteProvisional.DISPONIBLE);j.setActualizadoEn(LocalDateTime.now());return map(j);}
 @Transactional(readOnly=true) public Download descargar(Long solicitudId,Usuario usuario)throws IOException{requireSolicitud(solicitudId,usuario);JustificanteProvisional j=repository.findBySolicitudId(solicitudId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Justificante no encontrado"));if(j.getEstado()!=EstadoJustificanteProvisional.DISPONIBLE||j.getNombreArchivo()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"El justificante provisional aun no esta disponible");Path base=Paths.get(uploadDir,"justificantes-provisionales").toAbsolutePath().normalize(),path=base.resolve(j.getNombreArchivo()).normalize();if(!path.startsWith(base)||!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Archivo no encontrado");return new Download(Files.readAllBytes(path),j.getNombreOriginal());}
 private Solicitud requireSolicitud(Long id,Usuario u){Solicitud s=solicitudRepository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Solicitud no encontrada"));if(u.getRolUsuario()!=RolUsuario.ADMIN){Set<Long> ids=new HashSet<>();if(u.getCliente()!=null)ids.add(u.getCliente().getId());u.getClientesAutorizados().forEach(c->ids.add(c.getId()));if(s.getCliente()==null||!ids.contains(s.getCliente().getId()))throw new AccesoDenegadoException("No tienes acceso a esta solicitud");}return s;}
 private void validarTraspaso(Solicitud s){if(s.getTipoTramite()==null||(s.getTipoTramite().getNombre()!=TipoTramiteEnum.TRASPASO&&s.getTipoTramite().getNombre()!=TipoTramiteEnum.BATECOM))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El justificante provisional solo esta disponible para solicitudes de transferencia o BATECOM");}
 private void requireAdmin(Usuario u){if(u.getRolUsuario()!=RolUsuario.ADMIN)throw new AccesoDenegadoException("Solo administracion puede preparar el justificante");}
 private JustificanteProvisionalResponse map(JustificanteProvisional j){return new JustificanteProvisionalResponse(j.getId(),j.getSolicitud().getId(),j.getEstado(),j.getNombreOriginal(),j.getSolicitadoEn(),j.getActualizadoEn());}
 private String seguro(String n){return Optional.ofNullable(n).orElse("justificante-provisional.pdf").replaceAll("[\\\\/:*?\"<>|]","_");}
 public record Download(byte[] bytes,String filename){}
}
