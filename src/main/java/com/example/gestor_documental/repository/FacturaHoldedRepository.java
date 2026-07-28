package com.example.gestor_documental.repository;
import com.example.gestor_documental.model.FacturaHolded;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
public interface FacturaHoldedRepository extends JpaRepository<FacturaHolded, Long>, JpaSpecificationExecutor<FacturaHolded> {
 Optional<FacturaHolded> findByHoldedInvoiceId(String holdedInvoiceId);
 Optional<FacturaHolded> findFirstByNumeroIgnoreCase(String numero);
 List<FacturaHolded> findByHoldedInvoiceIdIn(Collection<String> ids);
}
