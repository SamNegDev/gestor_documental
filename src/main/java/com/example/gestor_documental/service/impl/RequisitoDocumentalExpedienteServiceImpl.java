package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RequisitoDocumentalExpedienteServiceImpl implements RequisitoDocumentalExpedienteService {

    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final ExpedienteService expedienteService;
    private final DocumentoService documentoService;
    private final InteresadoRepository interesadoRepository;

    @Override
    @Transactional
    public List<RequisitoDocumentalExpediente> sincronizarYListar(
            Expediente expediente,
            List<ExpedienteInteresado> interesados,
            List<Documento> documentos,
            Usuario usuario
    ) {
        generarRequisitosBase(expediente, interesados, usuario);
        reconciliarConDocumentos(expediente, documentos, usuario);
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId());
    }

    @Override
    @Transactional
    public RequisitoDocumentalExpediente crearManual(
            Long expedienteId,
            TipoDocumento tipoDocumento,
            String descripcion,
            Long interesadoId,
            RolInteresado rolInteresado,
            EstadoRequisitoDocumental estadoInicial,
            Usuario usuario
    ) {
        Expediente expediente = obtenerExpedienteConPermiso(expedienteId, usuario);
        EstadoRequisitoDocumental estado = estadoInicial == EstadoRequisitoDocumental.POSTERIOR
                ? EstadoRequisitoDocumental.POSTERIOR
                : EstadoRequisitoDocumental.REQUERIDO;
        Interesado interesado = interesadoId != null
                ? interesadoRepository.findById(interesadoId).orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"))
                : null;

        RequisitoDocumentalExpediente requisito = nuevoRequisito(
                expediente,
                tipoDocumento,
                descripcion,
                estado,
                interesado,
                interesado != null ? rolInteresado : null,
                usuario
        );
        requisito.setOrigen(OrigenRequisitoDocumental.MANUAL);
        return requisitoRepository.save(requisito);
    }

    @Override
    @Transactional
    public RequisitoDocumentalExpediente omitir(Long requisitoId, String motivo, Usuario usuario) {
        RequisitoDocumentalExpediente requisito = obtenerRequisitoConPermiso(requisitoId, usuario);
        requisito.setEstado(EstadoRequisitoDocumental.OMITIDO);
        requisito.setMotivoOmision(motivo);
        requisito.setDocumento(null);
        requisito.setFechaResolucion(LocalDateTime.now());
        requisito.setResueltoPor(usuario);
        return requisitoRepository.save(requisito);
    }

    @Override
    @Transactional
    public RequisitoDocumentalExpediente reabrir(Long requisitoId, Usuario usuario) {
        RequisitoDocumentalExpediente requisito = obtenerRequisitoConPermiso(requisitoId, usuario);
        requisito.setEstado(EstadoRequisitoDocumental.REQUERIDO);
        requisito.setDocumento(null);
        requisito.setMotivoOmision(null);
        requisito.setFechaResolucion(null);
        requisito.setResueltoPor(null);
        return requisitoRepository.save(requisito);
    }

    @Override
    @Transactional
    public RequisitoDocumentalExpediente vincularDocumento(Long requisitoId, Long documentoId, Usuario usuario) {
        RequisitoDocumentalExpediente requisito = obtenerRequisitoConPermiso(requisitoId, usuario);
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        if (documento.getExpediente() == null || !documento.getExpediente().getId().equals(requisito.getExpediente().getId())) {
            throw new OperacionInvalidaException("El documento no pertenece al expediente del requisito");
        }
        return marcarAportado(requisito, documento, usuario);
    }

    @Override
    @Transactional
    public RequisitoDocumentalExpediente subirDocumento(Long requisitoId, MultipartFile archivo, Usuario usuario) {
        RequisitoDocumentalExpediente requisito = obtenerRequisitoConPermiso(requisitoId, usuario);
        Documento documento = documentoService.guardarParaExpediente(
                requisito.getExpediente().getId(),
                archivo,
                requisito.getTipoDocumento(),
                usuario
        );
        if (documento == null) {
            throw new OperacionInvalidaException("No se pudo guardar el documento del requisito");
        }
        return marcarAportado(requisito, documento, usuario);
    }

    private Expediente obtenerExpedienteConPermiso(Long expedienteId, Usuario usuario) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para modificar este expediente");
        }
        return expediente;
    }

    private RequisitoDocumentalExpediente obtenerRequisitoConPermiso(Long requisitoId, Usuario usuario) {
        RequisitoDocumentalExpediente requisito = requisitoRepository.findById(requisitoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Requisito documental no encontrado"));
        obtenerExpedienteConPermiso(requisito.getExpediente().getId(), usuario);
        return requisito;
    }

    private RequisitoDocumentalExpediente marcarAportado(
            RequisitoDocumentalExpediente requisito,
            Documento documento,
            Usuario usuario
    ) {
        requisito.setDocumento(documento);
        requisito.setEstado(EstadoRequisitoDocumental.APORTADO);
        requisito.setMotivoOmision(null);
        requisito.setFechaResolucion(LocalDateTime.now());
        requisito.setResueltoPor(usuario);
        return requisitoRepository.save(requisito);
    }

    private void reconciliarConDocumentos(Expediente expediente, List<Documento> documentos, Usuario usuario) {
        List<RequisitoDocumentalExpediente> requisitos = requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId());

        for (RequisitoDocumentalExpediente requisito : requisitos) {
            if (requisito.getDocumento() != null || requisito.getEstado() == EstadoRequisitoDocumental.OMITIDO) {
                continue;
            }

            documentos.stream()
                    .filter(documento -> documento.getTipoDocumento() == requisito.getTipoDocumento())
                    .findFirst()
                    .ifPresent(documento -> {
                        requisito.setDocumento(documento);
                        requisito.setEstado(EstadoRequisitoDocumental.APORTADO);
                        requisito.setFechaResolucion(LocalDateTime.now());
                        requisito.setResueltoPor(usuario);
                        requisitoRepository.save(requisito);
                    });
        }
    }

    private void generarRequisitosBase(Expediente expediente, List<ExpedienteInteresado> interesados, Usuario usuario) {
        TipoTramiteEnum tramite = expediente.getTipoTramite() != null ? expediente.getTipoTramite().getNombre() : null;

        for (ExpedienteInteresado relacion : interesados) {
            Interesado interesado = relacion.getInteresado();
            RolInteresado rol = relacion.getRol();
            if (interesado == null || rol == null) {
                continue;
            }

            if ((tramite == TipoTramiteEnum.TRASPASO || tramite == TipoTramiteEnum.BATECOM)
                    && (rol == RolInteresado.COMPRADOR || rol == RolInteresado.VENDEDOR || rol == RolInteresado.COMPRAVENTA)) {
                generarIdentificacion(expediente, interesado, rol, usuario);
            }

            if (tramite != TipoTramiteEnum.TRASPASO && tramite != TipoTramiteEnum.BATECOM && rol == RolInteresado.TITULAR) {
                generarIdentificacion(expediente, interesado, rol, usuario);
            }
        }

        if (tramite == TipoTramiteEnum.TRASPASO || tramite == TipoTramiteEnum.BATECOM) {
            crearGlobalSiNoExiste(expediente, TipoDocumento.CONTRATO_COMPRAVENTA, "Contrato de compraventa", EstadoRequisitoDocumental.REQUERIDO, usuario);
        }

        crearGlobalSiNoExiste(expediente, TipoDocumento.MANDATO, "Mandato o autorizacion de gestion", EstadoRequisitoDocumental.REQUERIDO, usuario);
        eliminarModelo620Automatico(expediente);
    }

    private void generarIdentificacion(Expediente expediente, Interesado interesado, RolInteresado rol, Usuario usuario) {
        TipoPersona tipoPersona = inferirTipoPersona(interesado);
        String rolTexto = rol.name().toLowerCase();

        if (tipoPersona == TipoPersona.EMPRESA) {
            eliminarIdentificacionNoAplicable(expediente, interesado, rol, TipoDocumento.DNI, "DNI " + rolTexto);
            crearPorInteresadoSiNoExiste(expediente, TipoDocumento.CIF, interesado, rol, "CIF empresa " + rolTexto, usuario);
            crearPorInteresadoSiNoExiste(expediente, TipoDocumento.DNI, interesado, rol, "DNI administrador " + rolTexto, usuario);
            return;
        }

        eliminarIdentificacionNoAplicable(expediente, interesado, rol, TipoDocumento.CIF, null);
        crearPorInteresadoSiNoExiste(expediente, TipoDocumento.DNI, interesado, rol, "DNI " + rolTexto, usuario);
    }

    private TipoPersona inferirTipoPersona(Interesado interesado) {
        String identificador = normalizarIdentificador(interesado.getDni());
        if (identificador.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]")) {
            return TipoPersona.EMPRESA;
        }

        if (identificador.matches("[0-9]{8}[A-Z]") || identificador.matches("[XYZ][0-9]{7}[A-Z]")) {
            return TipoPersona.PARTICULAR;
        }

        return interesado.getTipoPersona() != null ? interesado.getTipoPersona() : TipoPersona.PARTICULAR;
    }

    private String normalizarIdentificador(String identificador) {
        return identificador == null
                ? ""
                : identificador.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private void eliminarIdentificacionNoAplicable(
            Expediente expediente,
            Interesado interesado,
            RolInteresado rol,
            TipoDocumento tipoDocumento,
            String descripcionProtegida
    ) {
        requisitoRepository.findByExpedienteIdAndInteresadoIdAndRolInteresado(
                        expediente.getId(),
                        interesado.getId(),
                        rol
                ).stream()
                .filter(requisito -> requisito.getTipoDocumento() == tipoDocumento)
                .filter(requisito -> requisito.getOrigen() == OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> requisito.getDocumento() == null)
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .filter(requisito -> descripcionProtegida == null || !descripcionProtegida.equals(requisito.getDescripcion()))
                .forEach(requisitoRepository::delete);
    }

    private void crearPorInteresadoSiNoExiste(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            Interesado interesado,
            RolInteresado rol,
            String descripcion,
            Usuario usuario
    ) {
        requisitoRepository.findByExpedienteIdAndTipoDocumentoAndInteresadoIdAndRolInteresado(
                expediente.getId(),
                tipoDocumento,
                interesado.getId(),
                rol
        ).ifPresentOrElse(
                requisito -> actualizarDescripcionRegla(requisito, descripcion),
                () -> requisitoRepository.save(nuevoRequisito(
                        expediente,
                        tipoDocumento,
                        descripcion,
                        EstadoRequisitoDocumental.REQUERIDO,
                        interesado,
                        rol,
                        usuario
                ))
        );
    }

    private void actualizarDescripcionRegla(RequisitoDocumentalExpediente requisito, String descripcion) {
        if (requisito.getOrigen() == OrigenRequisitoDocumental.REGLA && !descripcion.equals(requisito.getDescripcion())) {
            requisito.setDescripcion(descripcion);
            requisitoRepository.save(requisito);
        }
    }

    private void crearGlobalSiNoExiste(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            String descripcion,
            EstadoRequisitoDocumental estado,
            Usuario usuario
    ) {
        requisitoRepository.findByExpedienteIdAndTipoDocumentoAndInteresadoIsNullAndRolInteresadoIsNull(expediente.getId(), tipoDocumento)
                .orElseGet(() -> requisitoRepository.save(nuevoRequisito(
                        expediente,
                        tipoDocumento,
                        descripcion,
                        estado,
                        null,
                        null,
                        usuario
                )));
    }

    private void eliminarModelo620Automatico(Expediente expediente) {
        requisitoRepository.findByExpedienteIdAndTipoDocumentoAndInteresadoIsNullAndRolInteresadoIsNull(
                        expediente.getId(),
                        TipoDocumento.MODELO_620
                )
                .filter(requisito -> requisito.getOrigen() == OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> requisito.getDocumento() == null)
                .ifPresent(requisitoRepository::delete);
    }

    private RequisitoDocumentalExpediente nuevoRequisito(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            String descripcion,
            EstadoRequisitoDocumental estado,
            Interesado interesado,
            RolInteresado rol,
            Usuario usuario
    ) {
        RequisitoDocumentalExpediente requisito = new RequisitoDocumentalExpediente();
        requisito.setExpediente(expediente);
        requisito.setTipoDocumento(tipoDocumento);
        requisito.setDescripcion(descripcion);
        requisito.setEstado(estado);
        requisito.setOrigen(OrigenRequisitoDocumental.REGLA);
        requisito.setInteresado(interesado);
        requisito.setRolInteresado(rol);
        requisito.setCreadoPor(usuario);
        return requisito;
    }
}
