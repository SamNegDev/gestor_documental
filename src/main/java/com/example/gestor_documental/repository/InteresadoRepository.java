package com.example.gestor_documental.repository;


import com.example.gestor_documental.model.Interesado;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InteresadoRepository extends JpaRepository<Interesado, Long> {

    Optional<Interesado> findByDni(String dni);

    List<Interesado> findByDniContainingIgnoreCaseOrNombreContainingIgnoreCaseOrderByNombreAsc(
            String dni,
            String nombre,
            Pageable pageable
    );

    @Query("""
            select distinct i
            from ExpedienteInteresado ei
            join ei.interesado i
            join ei.expediente e
            where e.cliente.id = :clienteId
              and (upper(i.dni) like upper(concat('%', :query, '%'))
                   or upper(i.nombre) like upper(concat('%', :query, '%')))
            order by i.nombre asc
            """)
    List<Interesado> buscarPorClienteYTexto(
            @Param("clienteId") Long clienteId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select distinct i from Interesado i
            left join ExpedienteInteresado ei on ei.interesado = i
            left join ei.expediente e
            where (:clienteId is null or e.cliente.id = :clienteId)
              and (upper(coalesce(i.dni, '')) like :texto or upper(coalesce(i.nombre, '')) like :texto)
            order by i.nombre asc
            """)
    List<Interesado> buscarGlobal(@Param("clienteId") Long clienteId,
                                  @Param("texto") String texto,
                                  Pageable pageable);

}
