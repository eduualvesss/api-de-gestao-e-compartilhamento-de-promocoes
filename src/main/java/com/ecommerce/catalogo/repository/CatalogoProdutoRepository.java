package com.ecommerce.catalogo.repository;

import com.ecommerce.catalogo.model.CatalogoProduto;
import com.ecommerce.catalogo.model.CatalogoProdutoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogoProdutoRepository extends JpaRepository<CatalogoProduto, CatalogoProdutoId> {

    List<CatalogoProduto> findByIdIdCatalogoOrderByOrdemAsc(Long idCatalogo);

    /** Usado na regra de remoção segura: produto em catálogo ativo não pode ser excluído. */
    boolean existsByIdSkuAndCatalogoAtivo(String sku, boolean ativo);

    void deleteByIdSku(String sku);
}
