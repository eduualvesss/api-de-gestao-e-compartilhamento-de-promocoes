package com.ecommerce.catalogo.service;

import com.ecommerce.catalogo.controller.dto.ProdutoRequest;
import com.ecommerce.catalogo.exception.ConflitoException;
import com.ecommerce.catalogo.exception.ProdutoInativoException;
import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;
import com.ecommerce.catalogo.exception.RegraDeNegocioException;
import com.ecommerce.catalogo.model.Produto;
import com.ecommerce.catalogo.model.ProdutoDigital;
import com.ecommerce.catalogo.model.ProdutoFisico;
import com.ecommerce.catalogo.repository.CatalogoProdutoRepository;
import com.ecommerce.catalogo.repository.EstoqueRepository;
import com.ecommerce.catalogo.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProdutoService {

    private final ProdutoRepository repository;
    private final CatalogoProdutoRepository catalogoProdutoRepository;
    private final EstoqueRepository estoqueRepository;

    public ProdutoService(ProdutoRepository repository, CatalogoProdutoRepository catalogoProdutoRepository,
                          EstoqueRepository estoqueRepository) {
        this.repository = repository;
        this.catalogoProdutoRepository = catalogoProdutoRepository;
        this.estoqueRepository = estoqueRepository;
    }

    @Transactional
    public Produto cadastrar(ProdutoRequest req) {
        String sku = req.sku().trim();
        if (sku.isEmpty()) {
            throw new RegraDeNegocioException("SKU é obrigatório");
        }
        validarPreco(req.preco());
        if (repository.existsById(sku)) {
            throw new ConflitoException("Já existe produto com o SKU " + sku);
        }
        boolean ativo = req.ativo() == null || req.ativo();
        Produto produto = switch (req.tipoProduto()) {
            case "FISICO" -> {
                exigirDimensoes(req);
                if (req.linkDownload() != null || req.tipoLicenca() != null) {
                    throw new RegraDeNegocioException("Produto físico não possui link de download/licença");
                }
                yield new ProdutoFisico(sku, req.nome(), req.preco(), ativo,
                        req.peso(), req.altura(), req.largura(), req.profundidade());
            }
            case "DIGITAL" -> {
                semDimensoes(req);
                if (blank(req.linkDownload()) || blank(req.tipoLicenca())) {
                    throw new RegraDeNegocioException("linkDownload e tipoLicenca são obrigatórios para produto digital");
                }
                yield new ProdutoDigital(sku, req.nome(), req.preco(), ativo,
                        req.linkDownload(), req.tipoLicenca());
            }
            default -> throw new RegraDeNegocioException("tipoProduto inválido");
        };
        return repository.save(produto);
    }

    @Transactional(readOnly = true)
    public List<Produto> buscar(String busca) {
        if (busca == null || busca.isBlank()) {
            return repository.findAll();
        }
        return repository.findByNomeContainingIgnoreCaseOrSkuContainingIgnoreCase(busca, busca);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorSku(String sku) {
        return repository.findById(sku)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + sku));
    }

    @Transactional
    public Produto atualizar(String sku, ProdutoRequest req) {
        Produto produto = buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        if (!sku.equals(req.sku())) {
            throw new RegraDeNegocioException("O SKU não pode ser alterado");
        }
        if (!produto.getTipoProduto().equals(req.tipoProduto())) {
            throw new RegraDeNegocioException("O tipo do produto não pode ser alterado");
        }
        validarPreco(req.preco());
        produto.setNome(req.nome());
        produto.setPreco(req.preco());
        if (req.ativo() != null) {
            produto.setAtivo(req.ativo());
        }
        if (produto instanceof ProdutoFisico f) {
            exigirDimensoes(req);
            f.setPeso(req.peso());
            f.setAltura(req.altura());
            f.setLargura(req.largura());
            f.setProfundidade(req.profundidade());
        } else if (produto instanceof ProdutoDigital d) {
            if (blank(req.linkDownload()) || blank(req.tipoLicenca())) {
                throw new RegraDeNegocioException("linkDownload e tipoLicenca são obrigatórios para produto digital");
            }
            d.setLinkDownload(req.linkDownload());
            d.setTipoLicenca(req.tipoLicenca());
        }
        return repository.save(produto);
    }

    @Transactional
    public void remover(String sku) {
        Produto produto = buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        if (catalogoProdutoRepository.existsByIdSkuAndCatalogoAtivo(sku, true)) {
            throw new ConflitoException("Produto " + sku + " está associado a um catálogo ativo");
        }
        catalogoProdutoRepository.deleteByIdSku(sku);
        estoqueRepository.deleteById(sku);
        repository.delete(produto);
    }

    private void validarPreco(BigDecimal preco) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("O preço deve ser maior que zero");
        }
    }

    private void exigirDimensoes(ProdutoRequest r) {
        if (r.peso() == null || r.altura() == null || r.largura() == null || r.profundidade() == null
                || r.peso() <= 0 || r.altura() <= 0 || r.largura() <= 0 || r.profundidade() <= 0) {
            throw new RegraDeNegocioException("peso, altura, largura e profundidade (> 0) são obrigatórios para produto físico");
        }
    }

    private void semDimensoes(ProdutoRequest r) {
        if (r.peso() != null || r.altura() != null || r.largura() != null || r.profundidade() != null) {
            throw new RegraDeNegocioException("Produto digital não possui peso/dimensões nem frete");
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
