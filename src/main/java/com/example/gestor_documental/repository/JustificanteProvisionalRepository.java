package com.example.gestor_documental.repository;
import com.example.gestor_documental.model.JustificanteProvisional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import com.example.gestor_documental.enums.EstadoJustificanteProvisional;
public interface JustificanteProvisionalRepository extends JpaRepository<JustificanteProvisional, Long> {
 Optional<JustificanteProvisional> findBySolicitudId(Long solicitudId);
 @EntityGraph(attributePaths = {"solicitud", "solicitud.cliente"})
 List<JustificanteProvisional> findByEstadoInOrderBySolicitadoEnAsc(List<EstadoJustificanteProvisional> estados);
}
