package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
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
    private final DocumentoRepository documentoRepository;
    private final HitoExpedienteRepository hitoRepository;
    private final OperacionExpedienteRepository operacionRepository;

    @Override
    @Transactional
    public List<RequisitoDocumentalExpediente> sincronizarYListar(
            Expediente expediente,
            List<ExpedienteInteresado> interesados,
            List<Documento> documentos,
            Usuario usuario
    ) {
        generarRequisitosBase(expediente, interesados, usuario);
        generarRequisitosPosteriores(expediente, usuario);
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
                descripcion != null && !descripcion.isBlank()
                        ? descripcion
                        : "APORTAR " + tipoDocumento.name().replace('_', ' '),
                estado,
                interesado,
                interesado != null ? rolInteresado : null,
                usuario
        );
        requisito.setOrigen(OrigenRequisitoDocumental.MANUAL);
        RequisitoDocumentalExpediente guardado = requisitoRepository.save(requisito);
        if (estado == EstadoRequisitoDocumental.REQUERIDO) {
            expedienteService.marcarPendienteDocumentacion(expedienteId, usuario);
        }
        return guardado;
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
        RequisitoDocumentalExpediente guardado = requisitoRepository.save(requisito);
        expedienteService.reanudarTrasDocumentacion(requisito.getExpediente().getId(), usuario);
        return guardado;
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
        RequisitoDocumentalExpediente guardado = requisitoRepository.save(requisito);
        expedienteService.marcarPendienteDocumentacion(requisito.getExpediente().getId(), usuario);
        return guardado;
    }

    @Override
    @Transactional
    public RequisitoDocumentalExpediente vincularDocumento(Long requisitoId, Long documentoId, Usuario usuario) {
        RequisitoDocumentalExpediente requisito = obtenerRequisitoConPermiso(requisitoId, usuario);
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        if (documento.getExpediente() == null || !documento.getExpediente().getId().equals(requisito.getExpediente().getId())) {
            throw new OperacionInvalidaException("El documento no pertenece al expediente del requisito");
        }
        validarDocumentoOperacion(requisito, documento);
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
                requisito.getOperacion() != null ? requisito.getOperacion().getId() : null,
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
        RequisitoDocumentalExpediente guardado = requisitoRepository.save(requisito);
        expedienteService.reanudarTrasDocumentacion(requisito.getExpediente().getId(), usuario);
        return guardado;
    }

    private void validarDocumentoOperacion(RequisitoDocumentalExpediente requisito, Documento documento) {
        if (requisito.getOperacion() == null) {
            return;
        }
        if (requisito.getOperacion().getTipo() == TipoOperacionExpediente.TRASPASO_DIRECTO && documento.getOperacion() == null) {
            return;
        }
        if (documento.getOperacion() == null
                || documento.getOperacion().getId() == null
                || !documento.getOperacion().getId().equals(requisito.getOperacion().getId())) {
            throw new OperacionInvalidaException("El documento no pertenece a la operacion del requisito");
        }
    }

    private void reconciliarConDocumentos(Expediente expediente, List<Documento> documentos, Usuario usuario) {
        List<RequisitoDocumentalExpediente> requisitos = requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId());

        for (RequisitoDocumentalExpediente requisito : requisitos) {
            if (requisito.getDocumento() != null || requisito.getEstado() == EstadoRequisitoDocumental.OMITIDO) {
                continue;
            }

            documentos.stream()
                    .filter(documento -> documentoCubreRequisito(documento, requisito))
                    .findFirst()
                    .or(() -> documentoHabitualCubreRequisito(expediente, requisito))
                    .or(() -> documentoClienteCubreRequisito(expediente, requisito))
                    .ifPresent(documento -> {
                        requisito.setDocumento(documento);
                        requisito.setEstado(EstadoRequisitoDocumental.APORTADO);
                        requisito.setFechaResolucion(LocalDateTime.now());
                        requisito.setResueltoPor(usuario);
                        requisitoRepository.save(requisito);
                    });
        }
        expedienteService.reanudarTrasDocumentacion(expediente.getId(), usuario);
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
            crearGlobalSiNoExiste(expediente, TipoDocumento.CONTRATO_COMPRAVENTA, "Contrato de compraventa o factura de venta", EstadoRequisitoDocumental.REQUERIDO, usuario);
        }

        crearGlobalSiNoExiste(expediente, TipoDocumento.MANDATO, "Mandato o autorizacion de gestion", EstadoRequisitoDocumental.REQUERIDO, usuario);
        if (!requiereModelo620(expediente)) {
            eliminarModelos620DeReglaSinResolver(expediente);
        }
    }

    private void generarRequisitosPosteriores(Expediente expediente, Usuario usuario) {
        TipoTramiteEnum tramite = expediente.getTipoTramite() != null ? expediente.getTipoTramite().getNombre() : null;
        if (tramite == TipoTramiteEnum.BATECOM) {
            generarRequisitosOperacion(
                    expediente,
                    TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE,
                    CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION,
                    CodigoHitoExpediente.BATE_FINALIZADO,
                    "Modelo 620 de Entrega a compraventa (BATE)",
                    "Comprobante DGT de Entrega a compraventa (BATE)",
                    usuario
            );
            generarRequisitosOperacion(
                    expediente,
                    TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM,
                    CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION,
                    CodigoHitoExpediente.COM_FINALIZADO,
                    "Modelo 620 de Finalizacion entrega a compraventa (COM)",
                    "Comprobante DGT de Finalizacion entrega a compraventa (COM)",
                    usuario
            );
            return;
        }

        generarRequisitosOperacion(
                expediente,
                TipoOperacionExpediente.TRASPASO_DIRECTO,
                CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION,
                null,
                "Modelo 620",
                "Comprobante DGT o huella del tramite",
                usuario
        );
    }

    private void generarRequisitosOperacion(
            Expediente expediente,
            TipoOperacionExpediente tipoOperacion,
            CodigoHitoExpediente hitoTramite,
            CodigoHitoExpediente hitoFinalizacion,
            String descripcionModelo,
            String descripcionComprobante,
            Usuario usuario
    ) {
        OperacionExpediente operacion = operacionRepository.findByExpedienteIdAndTipo(expediente.getId(), tipoOperacion)
                .orElse(null);
        if (operacion == null) {
            return;
        }

        boolean tramiteAvanzado = hitoRepository.existsByExpedienteIdAndCodigo(expediente.getId(), hitoTramite);
        if (tramiteAvanzado && requiereModelo620(expediente)) {
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.MODELO_620, operacion, descripcionModelo, EstadoRequisitoDocumental.POSTERIOR, usuario);
        }

        boolean finalizado = expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO
                || (hitoFinalizacion != null && hitoRepository.existsByExpedienteIdAndCodigo(expediente.getId(), hitoFinalizacion));
        if (tramiteAvanzado || finalizado) {
            EstadoRequisitoDocumental estado = finalizado ? EstadoRequisitoDocumental.REQUERIDO : EstadoRequisitoDocumental.POSTERIOR;
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.COMPROBANTE_DGT, operacion, descripcionComprobante, estado, usuario);
        }
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
        requisitoRepository.findFirstByExpedienteIdAndTipoDocumentoAndInteresadoIdAndRolInteresadoOrderByIdAsc(
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

    private void actualizarRegla(RequisitoDocumentalExpediente requisito, String descripcion, EstadoRequisitoDocumental estado) {
        if (requisito.getOrigen() != OrigenRequisitoDocumental.REGLA) {
            return;
        }
        boolean modificado = false;
        if (!descripcion.equals(requisito.getDescripcion())) {
            requisito.setDescripcion(descripcion);
            modificado = true;
        }
        if (requisito.getEstado() == EstadoRequisitoDocumental.POSTERIOR && estado == EstadoRequisitoDocumental.REQUERIDO) {
            requisito.setEstado(EstadoRequisitoDocumental.REQUERIDO);
            modificado = true;
        }
        if (requisito.getTipoDocumento() == TipoDocumento.MODELO_620
                && requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO
                && estado == EstadoRequisitoDocumental.POSTERIOR
                && requisito.getDocumento() == null) {
            requisito.setEstado(EstadoRequisitoDocumental.POSTERIOR);
            modificado = true;
        }
        if (modificado) {
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
        requisitoRepository.findFirstByExpedienteIdAndTipoDocumentoAndInteresadoIsNullAndRolInteresadoIsNullOrderByIdAsc(expediente.getId(), tipoDocumento)
                .ifPresentOrElse(
                        requisito -> actualizarDescripcionRegla(requisito, descripcion),
                        () -> requisitoRepository.save(nuevoRequisito(
                                expediente,
                                tipoDocumento,
                                descripcion,
                                estado,
                                null,
                                null,
                                usuario
                        ))
                );
    }

    private void crearPorOperacionSiNoExiste(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            OperacionExpediente operacion,
            String descripcion,
            EstadoRequisitoDocumental estado,
            Usuario usuario
    ) {
        requisitoRepository.findFirstByExpedienteIdAndTipoDocumentoAndOperacionIdOrderByIdAsc(
                        expediente.getId(),
                        tipoDocumento,
                        operacion.getId()
                )
                .ifPresentOrElse(
                        requisito -> actualizarRegla(requisito, descripcion, estado),
                        () -> {
                            RequisitoDocumentalExpediente requisito = nuevoRequisito(
                                    expediente,
                                    tipoDocumento,
                                    descripcion,
                                    estado,
                                    null,
                                    null,
                                    usuario
                            );
                            requisito.setOperacion(operacion);
                            requisitoRepository.save(requisito);
                        }
                );
    }

    private boolean documentoCubreRequisito(Documento documento, RequisitoDocumentalExpediente requisito) {
        if (requisito.getOperacion() != null) {
            if (documento.getOperacion() == null || documento.getOperacion().getId() == null) {
                if (requisito.getOperacion().getTipo() != TipoOperacionExpediente.TRASPASO_DIRECTO) {
                    return false;
                }
            } else if (!documento.getOperacion().getId().equals(requisito.getOperacion().getId())) {
                return false;
            }
        }
        if (documento.getTipoDocumento() == requisito.getTipoDocumento()) {
            return true;
        }
        if (requisito.getTipoDocumento() == TipoDocumento.MANDATO) {
            return documento.getTipoDocumento() == TipoDocumento.MANDATO_REPRESENTACION;
        }
        return requisito.getTipoDocumento() == TipoDocumento.CONTRATO_COMPRAVENTA
                && documento.getTipoDocumento() == TipoDocumento.FACTURA;
    }

    private java.util.Optional<Documento> documentoClienteCubreRequisito(
            Expediente expediente,
            RequisitoDocumentalExpediente requisito
    ) {
        if (expediente.getCliente() == null || requisito.getEstado() == EstadoRequisitoDocumental.POSTERIOR) {
            return java.util.Optional.empty();
        }
        if (!esDocumentoReutilizableCliente(requisito.getTipoDocumento())) {
            return java.util.Optional.empty();
        }
        if (requisito.getInteresado() != null && !interesadoEsCliente(expediente, requisito.getInteresado())) {
            return java.util.Optional.empty();
        }

        return documentoRepository.findByClienteIdOrderByFechaSubidaDesc(expediente.getCliente().getId()).stream()
                .filter(documento -> documento.getInteresado() == null)
                .filter(documento -> documentoCubreRequisito(documento, requisito))
                .findFirst();
    }

    private java.util.Optional<Documento> documentoHabitualCubreRequisito(
            Expediente expediente,
            RequisitoDocumentalExpediente requisito
    ) {
        if (expediente.getCliente() == null || requisito.getInteresado() == null) {
            return java.util.Optional.empty();
        }
        if (!esDocumentoReutilizableCliente(requisito.getTipoDocumento())) {
            return java.util.Optional.empty();
        }
        return documentoRepository.findByClienteIdAndInteresadoIdOrderByFechaSubidaDesc(
                        expediente.getCliente().getId(),
                        requisito.getInteresado().getId()
                ).stream()
                .filter(documento -> documentoCubreRequisito(documento, requisito))
                .findFirst();
    }

    private boolean esDocumentoReutilizableCliente(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.DNI
                || tipoDocumento == TipoDocumento.CIF
                || tipoDocumento == TipoDocumento.MANDATO
                || tipoDocumento == TipoDocumento.MANDATO_REPRESENTACION;
    }

    private boolean interesadoEsCliente(Expediente expediente, Interesado interesado) {
        String nifCliente = normalizarIdentificador(expediente.getCliente().getNif());
        String dniInteresado = normalizarIdentificador(interesado.getDni());
        return !nifCliente.isBlank() && nifCliente.equals(dniInteresado);
    }

    private boolean requiereModelo620(Expediente expediente) {
        TipoTramiteEnum tramite = expediente.getTipoTramite() != null ? expediente.getTipoTramite().getNombre() : null;
        return tramite != TipoTramiteEnum.NOTIFICACION_VENTA;
    }

    private void eliminarModelos620DeReglaSinResolver(Expediente expediente) {
        requisitoRepository.findByExpedienteIdAndTipoDocumento(expediente.getId(), TipoDocumento.MODELO_620).stream()
                .filter(requisito -> requisito.getOrigen() == OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> requisito.getDocumento() == null)
                .filter(requisito -> requisito.getEstado() != EstadoRequisitoDocumental.OMITIDO)
                .forEach(requisitoRepository::delete);
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
