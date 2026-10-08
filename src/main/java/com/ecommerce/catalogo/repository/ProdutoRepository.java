package com.ecommerce.catalogo.repository;

import com.ecommerce.catalogo.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, String> {

    List<Produto> findByNomeContainingIgnoreCaseOrSkuContainingIgnoreCase(String nome, String sku);
}
