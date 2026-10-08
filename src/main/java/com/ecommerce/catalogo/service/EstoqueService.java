package com.ecommerce.catalogo.service;

import com.ecommerce.catalogo.exception.ProdutoInativoException;
import com.ecommerce.catalogo.model.Estoque;
import com.ecommerce.catalogo.model.Produto;
import com.ecommerce.catalogo.repository.EstoqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {

    private final EstoqueRepository estoqueRepository;
    private final ProdutoService produtoService;

    public EstoqueService(EstoqueRepository estoqueRepository, ProdutoService produtoService) {
        this.estoqueRepository = estoqueRepository;
        this.produtoService = produtoService;
    }

    @Transactional
    public Estoque consultar(String sku) {
        Produto produto = produtoService.buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        return obterOuCriar(produto);
    }

    @Transactional
    public Estoque reservar(String sku, int quantidade) {
        Produto produto = produtoService.buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        obterOuCriar(produto);
        Estoque estoque = estoqueRepository.buscarComLock(sku).orElseThrow();
        estoque.reservar(quantidade);
        return estoque;
    }

    @Transactional
    public Estoque repor(String sku, int quantidade) {
        Produto produto = produtoService.buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        obterOuCriar(produto);
        Estoque estoque = estoqueRepository.buscarComLock(sku).orElseThrow();
        estoque.repor(quantidade);
        return estoque;
    }

    /** Produtos sem registro de estoque começam com 0 unidades. */
    private Estoque obterOuCriar(Produto produto) {
        return estoqueRepository.findById(produto.getSku())
                .orElseGet(() -> estoqueRepository.saveAndFlush(new Estoque(produto, 0)));
    }
}
