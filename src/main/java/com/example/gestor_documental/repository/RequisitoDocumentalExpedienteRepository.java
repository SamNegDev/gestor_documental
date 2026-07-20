package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequisitoDocumentalExpedienteRepository extends JpaRepository<RequisitoDocumentalExpediente, Long> {
    List<RequisitoDocumentalExpediente> findByExpedienteIdOrderByIdAsc(Long expedienteId);

    List<RequisitoDocumentalExpediente> findByExpedienteIdInOrderByIdAsc(List<Long> expedienteIds);

    List<RequisitoDocumentalExpediente> findByDocumentoId(Long documentoId);

    Optional<RequisitoDocumentalExpediente> findFirstByExpedienteIdAndTipoDocumentoAndInteresadoIdAndRolInteresadoOrderByIdAsc(
            Long expedienteId,
            TipoDocumento tipoDocumento,
            Long interesadoId,
            RolInteresado rolInteresado
    );

    List<RequisitoDocumentalExpediente> findByExpedienteIdAndInteresadoIdAndRolInteresado(
            Long expedienteId,
            Long interesadoId,
            RolInteresado rolInteresado
    );

    Optional<RequisitoDocumentalExpediente> findFirstByExpedienteIdAndTipoDocumentoAndInteresadoIsNullAndRolInteresadoIsNullOrderByIdAsc(
            Long expedienteId,
            TipoDocumento tipoDocumento
    );

    Optional<RequisitoDocumentalExpediente> findFirstByExpedienteIdAndTipoDocumentoAndInteresadoIsNullAndRolInteresadoOrderByIdAsc(
            Long expedienteId,
            TipoDocumento tipoDocumento,
            RolInteresado rolInteresado
    );

    Optional<RequisitoDocumentalExpediente> findFirstByExpedienteIdAndTipoDocumentoAndOperacionIdOrderByIdAsc(
            Long expedienteId,
            TipoDocumento tipoDocumento,
            Long operacionId
    );

    List<RequisitoDocumentalExpediente> findByExpedienteIdAndTipoDocumentoAndOperacionId(
            Long expedienteId,
            TipoDocumento tipoDocumento,
            Long operacionId
    );

    List<RequisitoDocumentalExpediente> findByExpedienteIdAndTipoDocumento(
            Long expedienteId,
            TipoDocumento tipoDocumento
    );
}
