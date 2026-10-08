package com.ecommerce.catalogo.repository;

import com.ecommerce.catalogo.model.Catalogo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogoRepository extends JpaRepository<Catalogo, Long> {
}
