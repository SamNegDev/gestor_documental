package com.example.gestor_documental.repository;
import com.example.gestor_documental.enums.EstadoComprobantePago;
import com.example.gestor_documental.model.ComprobantePago;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, Long> {
 List<ComprobantePago> findByFacturaIdOrderByCreadoEnDesc(Long facturaId);
 List<ComprobantePago> findByEstadoOrderByCreadoEnAsc(EstadoComprobantePago estado);
 boolean existsByFacturaId(Long facturaId);
}
