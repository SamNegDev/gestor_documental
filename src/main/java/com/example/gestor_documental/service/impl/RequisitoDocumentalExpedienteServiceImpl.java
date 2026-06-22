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
import com.example.gestor_documental.model.ClienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.GestionPersonaRepresentanteCatalogo;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.GestionPersonaRepresentanteCatalogoRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RequisitoDocumentalExpedienteServiceImpl implements RequisitoDocumentalExpedienteService {

    private static final double CONFIANZA_MINIMA_IDENTIDAD = 0.80;

    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final ExpedienteService expedienteService;
    private final DocumentoService documentoService;
    private final InteresadoRepository interesadoRepository;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final GestionPersonaRepresentanteCatalogoRepository representanteCatalogoRepository;
    private final HitoExpedienteRepository hitoRepository;
    private final OperacionExpedienteRepository operacionRepository;
    private final HistorialCambioService historialCambioService;

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
        depurarRequisitosAutomaticos(expediente, interesados, usuario);
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
        validarDocumentoInteresado(requisito, documento);
        if (requisito.getInteresado() != null && documento.getInteresado() == null) {
            documento.setInteresado(requisito.getInteresado());
            documentoRepository.save(documento);
        }
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

    private void validarDocumentoInteresado(RequisitoDocumentalExpediente requisito, Documento documento) {
        if (!esDocumentoIdentidad(requisito.getTipoDocumento()) || requisito.getInteresado() == null) {
            return;
        }
        if (documento.getInteresado() != null && !Objects.equals(documento.getInteresado().getId(), requisito.getInteresado().getId())) {
            throw new OperacionInvalidaException("El documento pertenece a otro interesado");
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
                        vincularDocumentoIdentidadSiProcede(documento, requisito);
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
        List<ExpedienteInteresado> relaciones = interesados != null ? interesados : List.of();

        if (tramite == TipoTramiteEnum.BATECOM) {
            generarRequisitosBaseBatecom(expediente, relaciones, usuario);
            if (!requiereModelo620(expediente)) {
                eliminarModelos620DeReglaSinResolver(expediente);
            }
            return;
        }

        for (RolInteresado rol : rolesEsperadosIdentificacion(tramite)) {
            generarIdentificacionRol(expediente, interesadoPorRol(relaciones, rol), rol, null, usuario);
        }

        if (tramite == TipoTramiteEnum.TRASPASO || tramite == TipoTramiteEnum.NOTIFICACION_VENTA) {
            crearGlobalSiNoExiste(expediente, TipoDocumento.CONTRATO_COMPRAVENTA, "Contrato de compraventa o factura de venta", EstadoRequisitoDocumental.REQUERIDO, usuario);
        }

        crearGlobalSiNoExiste(expediente, TipoDocumento.PERMISO_CIRCULACION, "Permiso de circulacion o Informe DGT", EstadoRequisitoDocumental.REQUERIDO, usuario);
        crearGlobalSiNoExiste(expediente, TipoDocumento.FICHA_TECNICA, "Ficha tecnica o Informe DGT", EstadoRequisitoDocumental.REQUERIDO, usuario);
        crearGlobalSiNoExiste(expediente, TipoDocumento.MANDATO, "Mandato o autorizacion de gestion", EstadoRequisitoDocumental.REQUERIDO, usuario);
        if (!requiereModelo620(expediente)) {
            eliminarModelos620DeReglaSinResolver(expediente);
        }
    }

    private void generarRequisitosBaseBatecom(Expediente expediente, List<ExpedienteInteresado> interesados, Usuario usuario) {
        OperacionExpediente bate = operacionRepository.findByExpedienteIdAndTipo(expediente.getId(), TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE)
                .orElse(null);
        OperacionExpediente com = operacionRepository.findByExpedienteIdAndTipo(expediente.getId(), TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM)
                .orElse(null);

        if (bate != null) {
            generarIdentificacionRol(expediente, interesadoPorRol(interesados, RolInteresado.VENDEDOR), RolInteresado.VENDEDOR, bate, usuario);
            generarIdentificacionRol(expediente, interesadoPorRol(interesados, RolInteresado.COMPRAVENTA), RolInteresado.COMPRAVENTA, bate, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.CONTRATO_COMPRAVENTA, bate, "Contrato o factura de Entrega a compraventa (BATE)", EstadoRequisitoDocumental.REQUERIDO, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.PERMISO_CIRCULACION, bate, "Permiso de circulacion o Informe DGT (BATE)", EstadoRequisitoDocumental.REQUERIDO, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.FICHA_TECNICA, bate, "Ficha tecnica o Informe DGT (BATE)", EstadoRequisitoDocumental.REQUERIDO, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.MANDATO, bate, "Mandato o autorizacion de gestion (BATE)", EstadoRequisitoDocumental.REQUERIDO, usuario);
        }
        if (com != null) {
            generarIdentificacionRol(expediente, interesadoPorRol(interesados, RolInteresado.COMPRAVENTA), RolInteresado.COMPRAVENTA, com, usuario);
            generarIdentificacionRol(expediente, interesadoPorRol(interesados, RolInteresado.COMPRADOR), RolInteresado.COMPRADOR, com, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.CONTRATO_COMPRAVENTA, com, "Contrato o factura de Finalizacion entrega a compraventa (COM)", EstadoRequisitoDocumental.REQUERIDO, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.PERMISO_CIRCULACION, com, "Permiso de circulacion o Informe DGT (COM)", EstadoRequisitoDocumental.REQUERIDO, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.FICHA_TECNICA, com, "Ficha tecnica o Informe DGT (COM)", EstadoRequisitoDocumental.REQUERIDO, usuario);
            crearPorOperacionSiNoExiste(expediente, TipoDocumento.MANDATO, com, "Mandato o autorizacion de gestion (COM)", EstadoRequisitoDocumental.REQUERIDO, usuario);
        }
    }

    private List<RolInteresado> rolesEsperadosIdentificacion(TipoTramiteEnum tramite) {
        if (tramite == TipoTramiteEnum.TRASPASO || tramite == TipoTramiteEnum.NOTIFICACION_VENTA) {
            return List.of(RolInteresado.VENDEDOR, RolInteresado.COMPRADOR);
        }
        return List.of(RolInteresado.TITULAR);
    }

    private Interesado interesadoPorRol(List<ExpedienteInteresado> relaciones, RolInteresado rol) {
        return relaciones.stream()
                .filter(relacion -> relacion.getRol() == rol)
                .map(ExpedienteInteresado::getInteresado)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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

    private void generarIdentificacionRol(Expediente expediente, Interesado interesado, RolInteresado rol, OperacionExpediente operacion, Usuario usuario) {
        if (interesado == null) {
            crearPorRolSiNoExiste(expediente, TipoDocumento.DNI, rol, operacion, "DNI/CIF " + rol.name().toLowerCase(Locale.ROOT), usuario);
            return;
        }
        generarIdentificacion(expediente, interesado, rol, operacion, usuario);
    }

    private void generarIdentificacion(Expediente expediente, Interesado interesado, RolInteresado rol, OperacionExpediente operacion, Usuario usuario) {
        TipoPersona tipoPersona = inferirTipoPersona(interesado);
        String rolTexto = rol.name().toLowerCase();

        if (tipoPersona == TipoPersona.EMPRESA) {
            eliminarIdentificacionNoAplicable(expediente, interesado, rol, TipoDocumento.DNI, "DNI " + rolTexto, operacion);
            eliminarPlaceholderIdentificacion(expediente, rol, operacion);
            crearPorInteresadoSiNoExiste(expediente, TipoDocumento.CIF, interesado, rol, operacion, "CIF empresa " + rolTexto, usuario);
            crearRepresentanteEmpresaSiNoExiste(expediente, interesado, rol, operacion, "DNI administrador " + rolTexto, usuario);
            return;
        }

        eliminarIdentificacionNoAplicable(expediente, interesado, rol, TipoDocumento.CIF, null, operacion);
        crearPorInteresadoSiNoExiste(expediente, TipoDocumento.DNI, interesado, rol, operacion, "DNI " + rolTexto, usuario);
        eliminarIdentificacionObsoleta(expediente, interesado, rol, TipoDocumento.DNI, operacion);
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
            String descripcionProtegida,
            OperacionExpediente operacion
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
                .filter(requisito -> mismaOperacion(requisito.getOperacion(), operacion))
                .filter(requisito -> descripcionProtegida == null || !descripcionProtegida.equals(requisito.getDescripcion()))
                .forEach(requisitoRepository::delete);
    }

    private void crearPorInteresadoSiNoExiste(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            Interesado interesado,
            RolInteresado rol,
            OperacionExpediente operacion,
            String descripcion,
            Usuario usuario
    ) {
        buscarPorInteresado(expediente, tipoDocumento, interesado, rol, operacion).ifPresentOrElse(
                requisito -> actualizarDescripcionRegla(requisito, descripcion),
                () -> {
                    Optional<RequisitoDocumentalExpediente> placeholder = buscarPlaceholder(expediente, tipoDocumento, rol, operacion);
                    if (placeholder.isPresent()) {
                        RequisitoDocumentalExpediente requisito = placeholder.get();
                        requisito.setInteresado(interesado);
                        requisito.setDescripcion(descripcion);
                        requisitoRepository.save(requisito);
                        return;
                    }
                    RequisitoDocumentalExpediente requisito = nuevoRequisito(
                            expediente,
                            tipoDocumento,
                            descripcion,
                            EstadoRequisitoDocumental.REQUERIDO,
                            interesado,
                            rol,
                            usuario
                    );
                    requisito.setOperacion(operacion);
                    requisitoRepository.save(requisito);
                }
        );
    }

    private void crearPorRolSiNoExiste(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            RolInteresado rol,
            OperacionExpediente operacion,
            String descripcion,
            Usuario usuario
    ) {
        buscarPlaceholder(expediente, tipoDocumento, rol, operacion).ifPresentOrElse(
                requisito -> actualizarDescripcionRegla(requisito, descripcion),
                () -> {
                    RequisitoDocumentalExpediente requisito = nuevoRequisito(
                            expediente,
                            tipoDocumento,
                            descripcion,
                            EstadoRequisitoDocumental.REQUERIDO,
                            null,
                            rol,
                            usuario
                    );
                    requisito.setOperacion(operacion);
                    requisitoRepository.save(requisito);
                }
        );
    }

    private void crearRepresentanteEmpresaSiNoExiste(
            Expediente expediente,
            Interesado empresa,
            RolInteresado rolRepresentado,
            OperacionExpediente operacion,
            String descripcion,
            Usuario usuario
    ) {
        buscarRepresentanteEmpresa(expediente, empresa, rolRepresentado, operacion).ifPresentOrElse(
                requisito -> actualizarDescripcionRegla(requisito, descripcion),
                () -> {
                    RequisitoDocumentalExpediente requisito = nuevoRequisito(
                            expediente,
                            TipoDocumento.DNI,
                            descripcion,
                            EstadoRequisitoDocumental.REQUERIDO,
                            null,
                            null,
                            usuario
                    );
                    requisito.setInteresadoRepresentado(empresa);
                    requisito.setRolRepresentado(rolRepresentado);
                    requisito.setOperacion(operacion);
                    requisitoRepository.save(requisito);
                }
        );
    }

    private void actualizarDescripcionRegla(RequisitoDocumentalExpediente requisito, String descripcion) {
        if (requisito.getOrigen() == OrigenRequisitoDocumental.REGLA && !descripcion.equals(requisito.getDescripcion())) {
            requisito.setDescripcion(descripcion);
            requisitoRepository.save(requisito);
        }
    }

    private Optional<RequisitoDocumentalExpediente> buscarPorInteresado(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            Interesado interesado,
            RolInteresado rol,
            OperacionExpediente operacion
    ) {
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getTipoDocumento() == tipoDocumento)
                .filter(requisito -> requisito.getInteresado() != null)
                .filter(requisito -> Objects.equals(requisito.getInteresado().getId(), interesado.getId()))
                .filter(requisito -> requisito.getRolInteresado() == rol)
                .filter(requisito -> mismaOperacion(requisito.getOperacion(), operacion))
                .findFirst();
    }

    private Optional<RequisitoDocumentalExpediente> buscarPlaceholder(
            Expediente expediente,
            TipoDocumento tipoDocumento,
            RolInteresado rol,
            OperacionExpediente operacion
    ) {
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getTipoDocumento() == tipoDocumento)
                .filter(requisito -> requisito.getInteresado() == null)
                .filter(requisito -> requisito.getInteresadoRepresentado() == null)
                .filter(requisito -> requisito.getRolInteresado() == rol)
                .filter(requisito -> mismaOperacion(requisito.getOperacion(), operacion))
                .findFirst();
    }

    private Optional<RequisitoDocumentalExpediente> buscarRepresentanteEmpresa(
            Expediente expediente,
            Interesado empresa,
            RolInteresado rolRepresentado,
            OperacionExpediente operacion
    ) {
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getTipoDocumento() == TipoDocumento.DNI)
                .filter(requisito -> requisito.getInteresado() == null)
                .filter(requisito -> requisito.getInteresadoRepresentado() != null)
                .filter(requisito -> Objects.equals(requisito.getInteresadoRepresentado().getId(), empresa.getId()))
                .filter(requisito -> requisito.getRolRepresentado() == rolRepresentado)
                .filter(requisito -> mismaOperacion(requisito.getOperacion(), operacion))
                .findFirst();
    }

    private void eliminarPlaceholderIdentificacion(Expediente expediente, RolInteresado rol, OperacionExpediente operacion) {
        buscarPlaceholder(expediente, TipoDocumento.DNI, rol, operacion)
                .filter(requisito -> requisito.getOrigen() == OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> requisito.getDocumento() == null)
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .ifPresent(requisitoRepository::delete);
    }

    private void eliminarIdentificacionObsoleta(
            Expediente expediente,
            Interesado interesadoActual,
            RolInteresado rol,
            TipoDocumento tipoDocumento,
            OperacionExpediente operacion
    ) {
        if (interesadoActual == null || interesadoActual.getId() == null) {
            return;
        }
        requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .filter(requisito -> requisito.getOrigen() == OrigenRequisitoDocumental.REGLA)
                .filter(requisito -> requisito.getTipoDocumento() == tipoDocumento)
                .filter(requisito -> requisito.getRolInteresado() == rol)
                .filter(requisito -> mismaOperacion(requisito.getOperacion(), operacion))
                .filter(requisito -> requisito.getDocumento() == null)
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .filter(requisito -> requisito.getInteresado() == null
                        || requisito.getInteresado().getId() == null
                        || !Objects.equals(requisito.getInteresado().getId(), interesadoActual.getId()))
                .forEach(requisitoRepository::delete);
    }

    private void depurarRequisitosAutomaticos(
            Expediente expediente,
            List<ExpedienteInteresado> relaciones,
            Usuario usuario
    ) {
        List<RequisitoDocumentalExpediente> requisitos = requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId());
        Map<RolInteresado, Interesado> interesadoActualPorRol = new LinkedHashMap<>();
        for (ExpedienteInteresado relacion : relaciones != null ? relaciones : List.<ExpedienteInteresado>of()) {
            if (relacion.getRol() != null && relacion.getInteresado() != null) {
                interesadoActualPorRol.putIfAbsent(relacion.getRol(), relacion.getInteresado());
            }
        }

        List<RequisitoDocumentalExpediente> eliminables = new ArrayList<>();
        for (RequisitoDocumentalExpediente requisito : requisitos) {
            if (!esRequisitoAutomaticoPendienteSinDocumento(requisito)) {
                continue;
            }
            if (!esDocumentoIdentidad(requisito.getTipoDocumento()) || requisito.getRolInteresado() == null) {
                continue;
            }
            Interesado interesadoActual = interesadoActualPorRol.get(requisito.getRolInteresado());
            if (interesadoActual == null || interesadoActual.getId() == null) {
                continue;
            }
            if (requisito.getInteresado() == null
                    || requisito.getInteresado().getId() == null
                    || !Objects.equals(requisito.getInteresado().getId(), interesadoActual.getId())) {
                eliminables.add(requisito);
            }
        }

        eliminables.addAll(requisitosAutomaticosDuplicados(requisitos, eliminables));
        List<RequisitoDocumentalExpediente> unicos = eliminables.stream().distinct().toList();
        if (unicos.isEmpty()) {
            return;
        }
        requisitoRepository.deleteAll(unicos);
        historialCambioService.registrarCambioExpediente(
                expediente,
                usuario,
                "SANEAMIENTO DOCUMENTAL",
                "Se eliminaron " + unicos.size() + " requisitos automaticos obsoletos o duplicados."
        );
    }

    private List<RequisitoDocumentalExpediente> requisitosAutomaticosDuplicados(
            List<RequisitoDocumentalExpediente> requisitos,
            List<RequisitoDocumentalExpediente> yaEliminables
    ) {
        List<RequisitoDocumentalExpediente> duplicados = new ArrayList<>();
        Map<String, RequisitoDocumentalExpediente> requeridoPorClave = new LinkedHashMap<>();
        Map<String, Boolean> resueltoPorClave = new LinkedHashMap<>();
        for (RequisitoDocumentalExpediente requisito : requisitos) {
            if (requisito.getOrigen() != OrigenRequisitoDocumental.REGLA) {
                continue;
            }
            String clave = claveRequisito(requisito);
            if (requisito.getDocumento() != null || requisito.getEstado() == EstadoRequisitoDocumental.APORTADO) {
                resueltoPorClave.put(clave, true);
                continue;
            }
            if (!esRequisitoAutomaticoPendienteSinDocumento(requisito) || yaEliminables.contains(requisito)) {
                continue;
            }
            if (Boolean.TRUE.equals(resueltoPorClave.get(clave))) {
                duplicados.add(requisito);
                continue;
            }
            RequisitoDocumentalExpediente previo = requeridoPorClave.putIfAbsent(clave, requisito);
            if (previo != null) {
                duplicados.add(requisito);
            }
        }
        return duplicados;
    }

    private boolean esRequisitoAutomaticoPendienteSinDocumento(RequisitoDocumentalExpediente requisito) {
        return requisito.getOrigen() == OrigenRequisitoDocumental.REGLA
                && requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO
                && requisito.getDocumento() == null;
    }

    private String claveRequisito(RequisitoDocumentalExpediente requisito) {
        return String.join("|",
                requisito.getTipoDocumento() != null ? requisito.getTipoDocumento().name() : "",
                requisito.getInteresado() != null && requisito.getInteresado().getId() != null ? requisito.getInteresado().getId().toString() : "",
                requisito.getRolInteresado() != null ? requisito.getRolInteresado().name() : "",
                requisito.getInteresadoRepresentado() != null && requisito.getInteresadoRepresentado().getId() != null ? requisito.getInteresadoRepresentado().getId().toString() : "",
                requisito.getRolRepresentado() != null ? requisito.getRolRepresentado().name() : "",
                requisito.getOperacion() != null && requisito.getOperacion().getId() != null ? requisito.getOperacion().getId().toString() : ""
        );
    }

    private boolean mismaOperacion(OperacionExpediente actual, OperacionExpediente esperada) {
        Long actualId = actual != null ? actual.getId() : null;
        Long esperadaId = esperada != null ? esperada.getId() : null;
        return Objects.equals(actualId, esperadaId);
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
        if (documento.getTipoDocumento() == TipoDocumento.EXPEDIENTE_COMPLETO && esRequisitoBaseCubiertoPorExpedienteCompleto(requisito)) {
            return true;
        }
        if (requisito.getOperacion() != null) {
            if (documento.getOperacion() == null || documento.getOperacion().getId() == null) {
                if (requisito.getOperacion().getTipo() != TipoOperacionExpediente.TRASPASO_DIRECTO
                        && !esDocumentoVehiculoBase(requisito.getTipoDocumento())) {
                    return false;
                }
            } else if (!documento.getOperacion().getId().equals(requisito.getOperacion().getId())) {
                return false;
            }
        }
        if (esDocumentoIdentidad(requisito.getTipoDocumento())) {
            return documentoIdentidadCubreRequisito(documento, requisito, false);
        }
        return tipoDocumentoCubreRequisito(documento.getTipoDocumento(), requisito.getTipoDocumento());
    }

    private boolean esRequisitoBaseCubiertoPorExpedienteCompleto(RequisitoDocumentalExpediente requisito) {
        return requisito.getEstado() != EstadoRequisitoDocumental.POSTERIOR
                && requisito.getTipoDocumento() != TipoDocumento.MODELO_620
                && requisito.getTipoDocumento() != TipoDocumento.COMPROBANTE_DGT
                && requisito.getTipoDocumento() != TipoDocumento.HUELLA_TRAMITE
                && requisito.getTipoDocumento() != TipoDocumento.DNI
                && requisito.getTipoDocumento() != TipoDocumento.CIF;
    }

    private boolean documentoIdentidadCubreRequisito(
            Documento documento,
            RequisitoDocumentalExpediente requisito,
            boolean permitirDocumentoClientePropio
    ) {
        if (!tipoDocumentoCubreRequisito(documento.getTipoDocumento(), requisito.getTipoDocumento())) {
            return false;
        }
        if (esRequisitoRepresentanteEmpresa(requisito)) {
            return documentoRepresentanteEmpresaCubreRequisito(documento, requisito);
        }
        if (requisito.getInteresado() == null) {
            return false;
        }
        if (documento.getInteresado() != null) {
            return Objects.equals(documento.getInteresado().getId(), requisito.getInteresado().getId());
        }
        if (lecturaIdentidadCubreRequisito(documento, requisito)) {
            return true;
        }
        return permitirDocumentoClientePropio && interesadoEsCliente(requisito.getExpediente(), requisito.getInteresado());
    }

    private boolean lecturaIdentidadCubreRequisito(Documento documento, RequisitoDocumentalExpediente requisito) {
        if (documento.getId() == null || documento.getExpediente() == null || requisito.getExpediente() == null) {
            return false;
        }
        if (!Objects.equals(documento.getExpediente().getId(), requisito.getExpediente().getId())) {
            return false;
        }
        DocumentoIdentidadLectura lectura = identidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
        if (lectura == null || lectura.getConfianzaGlobal() == null || lectura.getConfianzaGlobal() < CONFIANZA_MINIMA_IDENTIDAD) {
            return false;
        }
        if (!normalizarIdentificador(requisito.getInteresado().getDni()).equals(normalizarIdentificador(lectura.getIdentificador()))) {
            return false;
        }
        TipoDocumento tipoDetectado = lectura.getTipoDocumentoDetectado();
        return tipoDetectado == null || tipoDocumentoCubreRequisito(tipoDetectado, requisito.getTipoDocumento());
    }

    private void vincularDocumentoIdentidadSiProcede(Documento documento, RequisitoDocumentalExpediente requisito) {
        if (!esDocumentoIdentidad(requisito.getTipoDocumento()) || documento.getId() == null) {
            return;
        }
        if (esRequisitoRepresentanteEmpresa(requisito)) {
            vincularDocumentoRepresentanteEmpresa(documento, requisito);
            return;
        }
        if (documento.getInteresado() != null) {
            return;
        }
        if (requisito.getInteresado() == null) {
            return;
        }
        DocumentoIdentidadLectura lectura = identidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
        if (lectura == null || !lecturaIdentidadCubreRequisito(documento, requisito)) {
            return;
        }
        Interesado interesado = requisito.getInteresado();
        documento.setInteresado(interesado);
        if (lectura.getTipoDocumentoDetectado() != null && lectura.getTipoDocumentoDetectado() != documento.getTipoDocumento()) {
            documento.setTipoDocumento(lectura.getTipoDocumentoDetectado());
        }
        documentoRepository.save(documento);

        String direccion = normalizarDireccionCompleta(lectura.getDireccionTexto());
        if (direccion != null && !direccion.equals(interesado.getDireccion())) {
            interesado.setDireccion(direccion);
            interesadoRepository.save(interesado);
        }
        lectura.setInteresadoVinculado(interesado);
        lectura.setVinculadoAutomaticamente(true);
        lectura.setRequiereRevision(false);
        lectura.setMensaje("Identidad leida y vinculada con interesado existente.");
        identidadLecturaRepository.save(lectura);
    }

    private String normalizarDireccionCompleta(String direccion) {
        return direccion == null ? null : com.example.gestor_documental.util.TextNormalizer.upperOrNull(direccion.replaceAll("\\s+", " "));
    }

    private boolean esRequisitoRepresentanteEmpresa(RequisitoDocumentalExpediente requisito) {
        return requisito.getTipoDocumento() == TipoDocumento.DNI
                && requisito.getInteresado() == null
                && requisito.getInteresadoRepresentado() != null;
    }

    private boolean documentoRepresentanteEmpresaCubreRequisito(
            Documento documento,
            RequisitoDocumentalExpediente requisito
    ) {
        if (documento.getId() == null || requisito.getExpediente() == null || requisito.getInteresadoRepresentado() == null) {
            return false;
        }
        if (documento.getExpediente() != null
                && !Objects.equals(documento.getExpediente().getId(), requisito.getExpediente().getId())) {
            return false;
        }
        if (documento.getCliente() != null
                && requisito.getExpediente().getCliente() != null
                && !Objects.equals(documento.getCliente().getId(), requisito.getExpediente().getCliente().getId())) {
            return false;
        }
        if (documento.getInteresado() != null) {
            return catalogoRepresentante(requisito.getInteresadoRepresentado(), documento.getInteresado().getDni()).isPresent()
                    || representanteLegalHabitual(requisito, documento.getInteresado());
        }
        DocumentoIdentidadLectura lectura = identidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
        if (lectura == null || lectura.getConfianzaGlobal() == null || lectura.getConfianzaGlobal() < CONFIANZA_MINIMA_IDENTIDAD) {
            return false;
        }
        TipoDocumento tipoDetectado = lectura.getTipoDocumentoDetectado();
        if (tipoDetectado != null && !tipoDocumentoCubreRequisito(tipoDetectado, requisito.getTipoDocumento())) {
            return false;
        }
        return catalogoRepresentante(requisito.getInteresadoRepresentado(), lectura.getIdentificador()).isPresent()
                || documentoClientePuedeSerRepresentante(requisito, documento, lectura);
    }

    private void vincularDocumentoRepresentanteEmpresa(Documento documento, RequisitoDocumentalExpediente requisito) {
        DocumentoIdentidadLectura lectura = identidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
        if (lectura == null || !documentoRepresentanteEmpresaCubreRequisito(documento, requisito)) {
            return;
        }
        GestionPersonaRepresentanteCatalogo catalogo = catalogoRepresentante(
                requisito.getInteresadoRepresentado(),
                lectura.getIdentificador()
        ).orElse(null);
        if (catalogo == null && !documentoClientePuedeSerRepresentante(requisito, documento, lectura)) {
            return;
        }
        Interesado representante = obtenerOCrearRepresentante(lectura, catalogo);
        documento.setInteresado(representante);
        if (lectura.getTipoDocumentoDetectado() != null && lectura.getTipoDocumentoDetectado() != documento.getTipoDocumento()) {
            documento.setTipoDocumento(lectura.getTipoDocumentoDetectado());
        }
        documentoRepository.save(documento);
        asociarRepresentanteACliente(requisito.getExpediente(), representante);

        lectura.setInteresadoVinculado(representante);
        lectura.setVinculadoAutomaticamente(true);
        lectura.setRequiereRevision(false);
        lectura.setMensaje("Identidad leida y vinculada como administrador de " + requisito.getInteresadoRepresentado().getNombre() + ".");
        identidadLecturaRepository.save(lectura);
    }

    private Optional<GestionPersonaRepresentanteCatalogo> catalogoRepresentante(Interesado empresa, String representanteDni) {
        String nifEmpresa = normalizarIdentificador(empresa != null ? empresa.getDni() : null);
        String nifRepresentante = normalizarIdentificador(representanteDni);
        if (nifEmpresa.isBlank() || nifRepresentante.isBlank()) {
            return Optional.empty();
        }
        return representanteCatalogoRepository.findByEmpresaNifNormalizadoOrderByIdAsc(nifEmpresa).stream()
                .filter(catalogo -> nifRepresentante.equals(normalizarIdentificador(catalogo.getRepresentanteNifNormalizado()))
                        || nifRepresentante.equals(normalizarIdentificador(catalogo.getRepresentanteNif())))
                .findFirst();
    }

    private boolean representanteLegalHabitual(RequisitoDocumentalExpediente requisito, Interesado representante) {
        if (requisito.getExpediente() == null
                || requisito.getExpediente().getCliente() == null
                || requisito.getInteresadoRepresentado() == null
                || representante == null
                || representante.getId() == null) {
            return false;
        }
        String nifCliente = normalizarIdentificador(requisito.getExpediente().getCliente().getNif());
        String nifEmpresa = normalizarIdentificador(requisito.getInteresadoRepresentado().getDni());
        return !nifCliente.isBlank()
                && nifCliente.equals(nifEmpresa)
                && clienteInteresadoRepository.existsByClienteIdAndInteresadoIdAndRepresentanteLegalTrue(
                requisito.getExpediente().getCliente().getId(),
                representante.getId()
        );
    }

    private boolean documentoClientePuedeSerRepresentante(
            RequisitoDocumentalExpediente requisito,
            Documento documento,
            DocumentoIdentidadLectura lectura
    ) {
        if (requisito.getExpediente() == null
                || requisito.getExpediente().getCliente() == null
                || requisito.getInteresadoRepresentado() == null
                || documento.getCliente() == null
                || lectura == null) {
            return false;
        }
        String clienteNif = normalizarIdentificador(requisito.getExpediente().getCliente().getNif());
        String documentoClienteNif = normalizarIdentificador(documento.getCliente().getNif());
        String empresaNif = normalizarIdentificador(requisito.getInteresadoRepresentado().getDni());
        String representanteNif = normalizarIdentificador(lectura.getIdentificador());
        TipoDocumento tipoDetectado = lectura.getTipoDocumentoDetectado();
        return !clienteNif.isBlank()
                && clienteNif.equals(documentoClienteNif)
                && clienteNif.equals(empresaNif)
                && !clienteNif.equals(representanteNif)
                && representanteNif.matches("([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])")
                && (tipoDetectado == null || tipoDocumentoCubreRequisito(tipoDetectado, TipoDocumento.DNI));
    }

    private Interesado obtenerOCrearRepresentante(
            DocumentoIdentidadLectura lectura,
            GestionPersonaRepresentanteCatalogo catalogo
    ) {
        String dni = normalizarIdentificador(lectura.getIdentificador());
        Interesado interesado = interesadoRepository.findByDni(dni).orElse(null);
        String nombre = nombreRepresentante(lectura, catalogo);
        String direccion = primerNoVacio(
                normalizarDireccionCompleta(lectura.getDireccionTexto()),
                direccionRepresentanteCatalogo(catalogo)
        );
        boolean creado = false;
        boolean actualizado = false;
        if (interesado == null) {
            interesado = new Interesado();
            interesado.setDni(dni);
            interesado.setNombre(nombre != null ? nombre : dni);
            interesado.setTipoPersona(TipoPersona.PARTICULAR);
            interesado.setDireccion(direccion);
            creado = true;
        } else {
            actualizado |= setIfBlank(interesado.getNombre(), nombre, interesado::setNombre);
            if (direccion != null && !direccion.equals(interesado.getDireccion())) {
                interesado.setDireccion(direccion);
                actualizado = true;
            }
            if (interesado.getTipoPersona() == null) {
                interesado.setTipoPersona(TipoPersona.PARTICULAR);
                actualizado = true;
            }
        }
        return creado || actualizado ? interesadoRepository.save(interesado) : interesado;
    }

    private String nombreRepresentante(
            DocumentoIdentidadLectura lectura,
            GestionPersonaRepresentanteCatalogo catalogo
    ) {
        String lecturaNombre = unirNombre(lectura.getNombre(), lectura.getApellido1(), lectura.getApellido2());
        if (lecturaNombre != null) {
            return lecturaNombre;
        }
        if (catalogo == null) {
            return null;
        }
        return unirNombre(
                catalogo.getRepresentanteNombre(),
                catalogo.getRepresentanteApellido1RazonSocial(),
                catalogo.getRepresentanteApellido2()
        );
    }

    private String unirNombre(String nombre, String apellido1, String apellido2) {
        String text = String.join(" ", List.of(
                nombre != null ? nombre : "",
                apellido1 != null ? apellido1 : "",
                apellido2 != null ? apellido2 : ""
        )).replaceAll("\\s+", " ").trim();
        return com.example.gestor_documental.util.TextNormalizer.upperOrNull(text);
    }

    private String direccionRepresentanteCatalogo(GestionPersonaRepresentanteCatalogo catalogo) {
        if (catalogo == null) {
            return null;
        }
        return normalizarDireccionCompleta(String.join(" ", List.of(
                catalogo.getRepresentanteDirSiglas() != null ? catalogo.getRepresentanteDirSiglas() : "",
                catalogo.getRepresentanteDirCalle() != null ? catalogo.getRepresentanteDirCalle() : "",
                catalogo.getRepresentanteDirNumero() != null ? catalogo.getRepresentanteDirNumero() : "",
                catalogo.getRepresentanteDirPiso() != null ? catalogo.getRepresentanteDirPiso() : "",
                catalogo.getRepresentanteDirPuerta() != null ? catalogo.getRepresentanteDirPuerta() : "",
                catalogo.getRepresentanteDirCp() != null ? catalogo.getRepresentanteDirCp() : "",
                catalogo.getRepresentanteDirMunicipio() != null ? catalogo.getRepresentanteDirMunicipio() : "",
                catalogo.getRepresentanteDirProvincia() != null ? catalogo.getRepresentanteDirProvincia() : ""
        )));
    }

    private void asociarRepresentanteACliente(Expediente expediente, Interesado representante) {
        if (expediente.getCliente() == null || representante.getId() == null) {
            return;
        }
        ClienteInteresado relacion = clienteInteresadoRepository
                .findByClienteIdAndInteresadoId(expediente.getCliente().getId(), representante.getId())
                .orElseGet(() -> {
                    ClienteInteresado nueva = new ClienteInteresado();
                    nueva.setCliente(expediente.getCliente());
                    nueva.setInteresado(representante);
                    return nueva;
                });
        relacion.setRepresentanteLegal(true);
        clienteInteresadoRepository.save(relacion);
    }

    private String primerNoVacio(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    private boolean setIfBlank(String actual, String nuevo, java.util.function.Consumer<String> setter) {
        if ((actual == null || actual.isBlank()) && nuevo != null && !nuevo.isBlank()) {
            setter.accept(nuevo);
            return true;
        }
        return false;
    }

    private boolean tipoDocumentoCubreRequisito(TipoDocumento documento, TipoDocumento requisito) {
        if (documento == null || requisito == null) {
            return false;
        }
        if (documento == requisito) {
            return true;
        }
        if (requisito == TipoDocumento.MANDATO) {
            return documento == TipoDocumento.MANDATO_REPRESENTACION;
        }
        if (requisito == TipoDocumento.CONTRATO_COMPRAVENTA) {
            return documento == TipoDocumento.FACTURA;
        }
        return (requisito == TipoDocumento.PERMISO_CIRCULACION || requisito == TipoDocumento.FICHA_TECNICA)
                && documento == TipoDocumento.INFORME_DGT;
    }

    private boolean esDocumentoVehiculoBase(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.PERMISO_CIRCULACION
                || tipoDocumento == TipoDocumento.FICHA_TECNICA;
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
                .filter(documento -> documento.getInteresado() == null || esRequisitoRepresentanteEmpresa(requisito))
                .filter(documento -> esDocumentoIdentidad(requisito.getTipoDocumento())
                        ? documentoIdentidadCubreRequisito(documento, requisito, true)
                        : documentoCubreRequisito(documento, requisito))
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

    private boolean esDocumentoIdentidad(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.DNI || tipoDocumento == TipoDocumento.CIF;
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
