package com.ecommerce.catalogo.repository;

import com.ecommerce.catalogo.model.Estoque;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EstoqueRepository extends JpaRepository<Estoque, String> {

    /** Lock pessimista evita reservas concorrentes acima do disponível. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estoque e where e.sku = :sku")
    Optional<Estoque> buscarComLock(@Param("sku") String sku);
}
