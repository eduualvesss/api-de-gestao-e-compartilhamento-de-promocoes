package com.ecommerce.catalogo.repository;

import com.ecommerce.catalogo.model.CupomPromocional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CupomRepository extends JpaRepository<CupomPromocional, Long> {

    Optional<CupomPromocional> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);
}
