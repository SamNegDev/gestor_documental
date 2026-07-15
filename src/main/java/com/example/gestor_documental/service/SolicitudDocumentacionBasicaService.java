package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.ClienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import com.example.gestor_documental.validation.DniNieValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitudDocumentacionBasicaService {

    private static final double CONFIANZA_MINIMA_IDENTIDAD = 0.80;
    private static final Set<TipoDocumento> TIPOS_IDENTIDAD = EnumSet.of(TipoDocumento.DNI, TipoDocumento.CIF);
    private static final Set<TipoDocumento> TIPOS_ROLES = EnumSet.of(TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.FACTURA);

    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository identidadLecturaRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final DniNieValidator dniNieValidator;

    public Evaluacion evaluar(Solicitud solicitud, List<Documento> documentos) {
        Context context = context(solicitud, documentos);

        int pendientes = 0;
        TipoTramiteEnum tramite = tipoTramite(solicitud);
        List<InteresadoBasico> interesados = interesados(solicitud);
        Map<RolInteresado, InteresadoBasico> porRol = interesados.stream()
                .filter(interesado -> interesado.rol() != null)
                .collect(Collectors.toMap(InteresadoBasico::rol, interesado -> interesado, (first, second) -> first));

        for (RolInteresado rol : rolesEsperados(tramite)) {
            InteresadoBasico interesado = porRol.get(rol);
            String identificador = interesado != null ? normalizarIdentificador(interesado.dni()) : null;
            if (identificador == null || !identificadorValido(identificador)
                    || !documentoIdentidadAportado(
                    solicitud,
                    identificador,
                    context.documentos(),
                    context.lecturasIdentidad(),
                    context.documentosCliente(),
                    context.lecturasIdentidadCliente(),
                    context.relacionesCliente())) {
                pendientes++;
            }
        }

        boolean requiereContrato = tramite == TipoTramiteEnum.TRASPASO
                || tramite == TipoTramiteEnum.BATECOM
                || tramite == TipoTramiteEnum.NOTIFICACION_VENTA;
        if (tramite == TipoTramiteEnum.BATECOM) {
            long documentosRol = context.documentos().stream()
                    .filter(documento -> TIPOS_ROLES.contains(documento.getTipoDocumento()))
                    .count();
            pendientes += Math.max(0, 2 - (int) documentosRol);
        } else if (requiereContrato && context.documentos().stream().noneMatch(documento -> TIPOS_ROLES.contains(documento.getTipoDocumento()))) {
            pendientes++;
        }

        if (context.documentos().stream().noneMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MANDATO
                || documento.getTipoDocumento() == TipoDocumento.MANDATO_REPRESENTACION)) {
            pendientes++;
        }

        boolean documentacionVehiculo = context.documentos().stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.PERMISO_CIRCULACION
                || documento.getTipoDocumento() == TipoDocumento.FICHA_TECNICA
                || documento.getTipoDocumento() == TipoDocumento.INFORME_DGT);
        if (!documentacionVehiculo) {
            pendientes++;
        }

        return new Evaluacion(pendientes);
    }

    public DocumentoSoporte soporteIdentidad(Solicitud solicitud, List<Documento> documentos, String dni, boolean incluirSoporteCliente) {
        String identificador = normalizarIdentificador(dni);
        if (identificador == null) {
            return DocumentoSoporte.noAportado();
        }
        Context context = context(solicitud, documentos);
        if (documentoIdentidadAportadoEnSolicitud(identificador, context.documentos(), context.lecturasIdentidad())) {
            return new DocumentoSoporte(true, "SOLICITUD");
        }
        if (!incluirSoporteCliente) {
            return DocumentoSoporte.noAportado();
        }
        return documentoIdentidadAportadoEnFichaCliente(
                solicitud,
                identificador,
                context.documentosCliente(),
                context.lecturasIdentidadCliente(),
                context.relacionesCliente()
        ) ? new DocumentoSoporte(true, "FICHA_CLIENTE") : DocumentoSoporte.noAportado();
    }

    public RepresentanteSoporte soporteRepresentanteLegal(Solicitud solicitud, List<Documento> documentos, String dniEmpresa) {
        if (solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
            return RepresentanteSoporte.noAportado();
        }
        String nifCliente = normalizarIdentificador(solicitud.getCliente().getNif());
        String nifEmpresa = normalizarIdentificador(dniEmpresa);
        if (nifCliente == null || !nifCliente.equals(nifEmpresa)) {
            return RepresentanteSoporte.noAportado();
        }
        Context context = context(solicitud, documentos);
        List<ClienteInteresado> representantes = clienteInteresadoRepository
                .findByClienteIdAndRepresentanteLegalTrueOrderByInteresadoNombreAsc(solicitud.getCliente().getId());
        RepresentanteSoporte registradoSinDocumento = RepresentanteSoporte.noAportado();
        for (ClienteInteresado relacion : representantes) {
            if (relacion.getInteresado() == null || relacion.getInteresado().getId() == null) {
                continue;
            }
            String nombre = relacion.getInteresado().getNombre();
            String dni = relacion.getInteresado().getDni();
            if (registradoSinDocumento.nombre() == null) {
                registradoSinDocumento = new RepresentanteSoporte(false, nombre, dni);
            }
            String identificadorRepresentante = normalizarIdentificador(dni);
            boolean tieneDniEnSolicitud = documentoIdentidadAportadoEnSolicitud(
                    identificadorRepresentante,
                    context.documentos(),
                    context.lecturasIdentidad()
            );
            boolean tieneDniEnFicha = documentoRepository
                    .findByClienteIdAndInteresadoIdOrderByFechaSubidaDesc(solicitud.getCliente().getId(), relacion.getInteresado().getId()).stream()
                    .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.DNI);
            if (tieneDniEnSolicitud || tieneDniEnFicha) {
                return new RepresentanteSoporte(true, nombre, dni);
            }
        }
        return registradoSinDocumento;
    }

    public boolean documentoIdentidadAportado(
            Solicitud solicitud,
            String identificador,
            List<Documento> documentos,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidad,
            List<Documento> documentosCliente,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente,
            List<ClienteInteresado> relacionesCliente
    ) {
        if (documentoIdentidadAportadoEnSolicitud(identificador, documentos, lecturasIdentidad)) {
            return true;
        }
        return documentoIdentidadAportadoEnFichaCliente(
                solicitud,
                identificador,
                documentosCliente,
                lecturasIdentidadCliente,
                relacionesCliente
        );
    }

    public Set<TipoDocumento> tiposIdentidad() {
        return TIPOS_IDENTIDAD;
    }

    public List<RolInteresado> rolesEsperados(Solicitud solicitud) {
        return rolesEsperados(tipoTramite(solicitud));
    }

    private boolean documentoIdentidadAportadoEnSolicitud(
            String identificador,
            List<Documento> documentos,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidad
    ) {
        return documentos.stream()
                .filter(documento -> TIPOS_IDENTIDAD.contains(documento.getTipoDocumento()))
                .anyMatch(documento -> lecturaIdentidadCoincide(lecturasIdentidad.get(documento.getId()), identificador));
    }

    private boolean documentoIdentidadAportadoEnFichaCliente(
            Solicitud solicitud,
            String identificador,
            List<Documento> documentosCliente,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente,
            List<ClienteInteresado> relacionesCliente
    ) {
        if (identificador == null || solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
            return false;
        }
        TipoDocumento tipoEsperado = esPersonaJuridica(identificador) ? TipoDocumento.CIF : TipoDocumento.DNI;
        String nifCliente = normalizarIdentificador(solicitud.getCliente().getNif());
        if (identificador.equals(nifCliente)
                && documentoFichaClienteCoincide(documentosCliente, lecturasIdentidadCliente, tipoEsperado, null, identificador)) {
            return true;
        }
        return relacionesCliente.stream()
                .filter(relacion -> relacion.getInteresado() != null)
                .filter(relacion -> identificador.equals(normalizarIdentificador(relacion.getInteresado().getDni())))
                .anyMatch(relacion -> documentoFichaClienteCoincide(
                        documentosCliente,
                        lecturasIdentidadCliente,
                        tipoEsperado,
                        relacion.getInteresado().getId(),
                        identificador
                ));
    }

    private boolean documentoFichaClienteCoincide(
            List<Documento> documentosCliente,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente,
            TipoDocumento tipoEsperado,
            Long interesadoId,
            String identificador
    ) {
        return documentosCliente.stream()
                .filter(documento -> documento.getTipoDocumento() == tipoEsperado)
                .filter(documento -> interesadoId == null
                        || (documento.getInteresado() != null && interesadoId.equals(documento.getInteresado().getId())))
                .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.CIF
                        || documentoInteresadoCoincide(documento, identificador)
                        || lecturaIdentidadCoincide(lecturasIdentidadCliente.get(documento.getId()), identificador));
    }

    private boolean documentoInteresadoCoincide(Documento documento, String identificador) {
        return documento != null
                && documento.getInteresado() != null
                && identificador.equals(normalizarIdentificador(documento.getInteresado().getDni()));
    }

    public boolean esPersonaJuridica(String identificador) {
        String normalizado = normalizarIdentificador(identificador);
        return normalizado != null && normalizado.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private Context context(Solicitud solicitud, List<Documento> documentos) {
        List<Documento> documentosConId = documentos.stream()
                .filter(documento -> documento.getId() != null)
                .toList();
        Map<Long, DocumentoIdentidadLectura> lecturasIdentidad = lecturasIdentidad(documentosConId);
        Long clienteId = solicitud.getCliente() != null ? solicitud.getCliente().getId() : null;
        List<Documento> documentosCliente = clienteId == null
                ? List.of()
                : documentoRepository.findByClienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(clienteId, TIPOS_IDENTIDAD);
        Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente = lecturasIdentidad(documentosCliente);
        List<ClienteInteresado> relacionesCliente = clienteId == null
                ? List.of()
                : clienteInteresadoRepository.findByClienteIdAndHabitualTrueOrderByInteresadoNombreAsc(clienteId);
        return new Context(
                documentosConId,
                lecturasIdentidad,
                documentosCliente,
                lecturasIdentidadCliente,
                relacionesCliente
        );
    }

    private Map<Long, DocumentoIdentidadLectura> lecturasIdentidad(List<Documento> documentos) {
        List<Long> documentoIds = documentos.stream()
                .map(Documento::getId)
                .filter(id -> id != null)
                .toList();
        if (documentoIds.isEmpty()) {
            return Map.of();
        }
        return identidadLecturaRepository.findByDocumentoIdIn(documentoIds).stream()
                .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (first, second) -> first));
    }

    private boolean lecturaIdentidadCoincide(DocumentoIdentidadLectura lectura, String identificador) {
        if (lectura == null || identificador == null) {
            return false;
        }
        if (confianza(lectura.getConfianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD
                && identificador.equals(normalizarIdentificador(lectura.getIdentificador()))) {
            return true;
        }
        return DocumentoIdentidadLecturaJson.extraer(lectura).stream()
                .filter(item -> confianza(item.confianzaGlobal()) >= CONFIANZA_MINIMA_IDENTIDAD)
                .anyMatch(item -> identificador.equals(normalizarIdentificador(item.identificador())));
    }

    public List<RolInteresado> rolesEsperados(TipoTramiteEnum tramite) {
        if (tramite == TipoTramiteEnum.BATECOM) {
            return List.of(RolInteresado.VENDEDOR, RolInteresado.COMPRAVENTA, RolInteresado.COMPRADOR);
        }
        if (tramite == TipoTramiteEnum.ALTA
                || tramite == TipoTramiteEnum.BAJA
                || tramite == TipoTramiteEnum.DUPLICADO
                || tramite == TipoTramiteEnum.MATRICULACION) {
            return List.of(RolInteresado.TITULAR);
        }
        return List.of(RolInteresado.VENDEDOR, RolInteresado.COMPRADOR);
    }

    private List<InteresadoBasico> interesados(Solicitud solicitud) {
        return List.of(
                new InteresadoBasico(solicitud.getInteresado1Rol(), solicitud.getInteresado1Dni()),
                new InteresadoBasico(solicitud.getInteresado2Rol(), solicitud.getInteresado2Dni()),
                new InteresadoBasico(solicitud.getInteresado3Rol(), solicitud.getInteresado3Dni())
        );
    }

    private TipoTramiteEnum tipoTramite(Solicitud solicitud) {
        return solicitud.getTipoTramite() != null ? solicitud.getTipoTramite().getNombre() : null;
    }

    private boolean identificadorValido(String value) {
        String identificador = normalizarIdentificador(value);
        if (identificador == null) {
            return false;
        }
        if (identificador.matches("[0-9]{8}[A-Z]") || identificador.matches("[XYZ][0-9]{7}[A-Z]")) {
            return dniNieValidator.esValido(identificador);
        }
        return identificador.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private String normalizarIdentificador(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private double confianza(Double value) {
        return value != null ? value : 0.0;
    }

    private record InteresadoBasico(RolInteresado rol, String dni) {
    }

    private record Context(
            List<Documento> documentos,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidad,
            List<Documento> documentosCliente,
            Map<Long, DocumentoIdentidadLectura> lecturasIdentidadCliente,
            List<ClienteInteresado> relacionesCliente
    ) {
    }

    public record Evaluacion(int pendientes) {
    }

    public record DocumentoSoporte(boolean aportado, String origen) {
        private static DocumentoSoporte noAportado() {
            return new DocumentoSoporte(false, null);
        }
    }

    public record RepresentanteSoporte(boolean aportado, String nombre, String dni) {
        private static RepresentanteSoporte noAportado() {
            return new RepresentanteSoporte(false, null, null);
        }

        public static RepresentanteSoporte noAplica() {
            return new RepresentanteSoporte(false, null, null);
        }
    }
}
