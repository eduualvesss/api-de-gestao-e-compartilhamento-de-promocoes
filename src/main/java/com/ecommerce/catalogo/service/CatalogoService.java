package com.ecommerce.catalogo.service;

import com.ecommerce.catalogo.controller.dto.CatalogoRequest;
import com.ecommerce.catalogo.controller.dto.CatalogoResponse;
import com.ecommerce.catalogo.exception.ConflitoException;
import com.ecommerce.catalogo.exception.ProdutoInativoException;
import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;
import com.ecommerce.catalogo.model.Catalogo;
import com.ecommerce.catalogo.model.CatalogoProduto;
import com.ecommerce.catalogo.model.CatalogoProdutoId;
import com.ecommerce.catalogo.model.Produto;
import com.ecommerce.catalogo.repository.CatalogoProdutoRepository;
import com.ecommerce.catalogo.repository.CatalogoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogoService {

    private final CatalogoRepository catalogoRepository;
    private final CatalogoProdutoRepository itemRepository;
    private final ProdutoService produtoService;

    public CatalogoService(CatalogoRepository catalogoRepository, CatalogoProdutoRepository itemRepository,
                           ProdutoService produtoService) {
        this.catalogoRepository = catalogoRepository;
        this.itemRepository = itemRepository;
        this.produtoService = produtoService;
    }

    @Transactional
    public CatalogoResponse criar(CatalogoRequest req) {
        Catalogo c = catalogoRepository.save(
                new Catalogo(req.nome(), req.descricao(), req.ativo() == null || req.ativo()));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar() {
        return catalogoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CatalogoResponse atualizar(Long id, CatalogoRequest req) {
        Catalogo c = buscar(id);
        c.setNome(req.nome());
        c.setDescricao(req.descricao());
        if (req.ativo() != null) {
            c.setAtivo(req.ativo());
        }
        return toResponse(c);
    }

    @Transactional
    public CatalogoResponse adicionarProduto(Long id, String sku, int ordem) {
        Catalogo c = buscar(id);
        Produto p = produtoService.buscarPorSku(sku);
        if (!p.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        if (itemRepository.existsById(new CatalogoProdutoId(id, sku))) {
            throw new ConflitoException("Produto " + sku + " já está no catálogo " + id);
        }
        itemRepository.save(new CatalogoProduto(c, p, ordem));
        return toResponse(c);
    }

    @Transactional
    public CatalogoResponse removerProduto(Long id, String sku) {
        Catalogo c = buscar(id);
        itemRepository.delete(buscarItem(id, sku));
        return toResponse(c);
    }

    @Transactional
    public CatalogoResponse alterarOrdem(Long id, String sku, int ordem) {
        Catalogo c = buscar(id);
        buscarItem(id, sku).setOrdem(ordem);
        return toResponse(c);
    }

    private Catalogo buscar(Long id) {
        return catalogoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Catálogo não encontrado: " + id));
    }

    private CatalogoProduto buscarItem(Long id, String sku) {
        return itemRepository.findById(new CatalogoProdutoId(id, sku))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto " + sku + " não está no catálogo " + id));
    }

    private CatalogoResponse toResponse(Catalogo c) {
        List<CatalogoResponse.Item> itens = itemRepository.findByIdIdCatalogoOrderByOrdemAsc(c.getId()).stream()
                .map(i -> new CatalogoResponse.Item(i.getId().getSku(), i.getOrdem())).toList();
        return new CatalogoResponse(c.getId(), c.getNome(), c.getDescricao(), c.isAtivo(), itens);
    }
}
