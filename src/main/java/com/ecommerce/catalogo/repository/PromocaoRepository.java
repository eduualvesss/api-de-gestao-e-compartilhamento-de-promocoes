package com.ecommerce.catalogo.repository;

import com.ecommerce.catalogo.model.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromocaoRepository extends JpaRepository<Promocao, Long> {

    List<Promocao> findByAtivaTrueAndSkuProduto(String skuProduto);
}
