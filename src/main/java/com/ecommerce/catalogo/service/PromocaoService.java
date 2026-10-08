package com.ecommerce.catalogo.service;

import com.ecommerce.catalogo.exception.ConflitoException;
import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;
import com.ecommerce.catalogo.exception.RegraDeNegocioException;
import com.ecommerce.catalogo.model.CupomPromocional;
import com.ecommerce.catalogo.model.DescontoPercentual;
import com.ecommerce.catalogo.model.DescontoQuantidade;
import com.ecommerce.catalogo.model.Produto;
import com.ecommerce.catalogo.model.Promocao;
import com.ecommerce.catalogo.exception.ProdutoInativoException;
import com.ecommerce.catalogo.repository.CupomRepository;
import com.ecommerce.catalogo.repository.PromocaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PromocaoService {

    private final PromocaoRepository promocaoRepository;
    private final CupomRepository cupomRepository;
    private final ProdutoService produtoService;

    public PromocaoService(PromocaoRepository promocaoRepository, CupomRepository cupomRepository,
                           ProdutoService produtoService) {
        this.promocaoRepository = promocaoRepository;
        this.cupomRepository = cupomRepository;
        this.produtoService = produtoService;
    }

    @Transactional
    public Promocao criarPercentual(String sku, BigDecimal percentual) {
        validarProdutoAtivo(sku);
        return promocaoRepository.save(new DescontoPercentual(sku, percentual));
    }

    @Transactional
    public Promocao criarQuantidade(String sku, int qtdMinima, int qtdGratis) {
        validarProdutoAtivo(sku);
        if (qtdGratis >= qtdMinima) {
            throw new RegraDeNegocioException("qtdGratis deve ser menor que qtdMinima");
        }
        return promocaoRepository.save(new DescontoQuantidade(sku, qtdMinima, qtdGratis));
    }

    @Transactional
    public Promocao criarCupom(String codigo, BigDecimal valorFixo, LocalDate validade, String skuEspecifico) {
        String cod = codigo.trim().toUpperCase();
        if (cod.isEmpty()) {
            throw new RegraDeNegocioException("Código do cupom é obrigatório");
        }
        if (validade.isBefore(LocalDate.now())) {
            throw new RegraDeNegocioException("A validade do cupom não pode estar no passado");
        }
        if (cupomRepository.existsByCodigoIgnoreCase(cod)) {
            throw new ConflitoException("Já existe um cupom com o código " + cod);
        }
        String sku = (skuEspecifico == null || skuEspecifico.isBlank()) ? null : skuEspecifico.trim();
        if (sku != null) {
            validarProdutoAtivo(sku);
        }
        return promocaoRepository.save(new CupomPromocional(cod, valorFixo, validade, sku));
    }

    @Transactional(readOnly = true)
    public List<Promocao> listar(String produto, String cupom) {
        return promocaoRepository.findAll().stream()
                .filter(p -> produto == null || produto.isBlank() || produto.equals(p.getSkuProduto()))
                .filter(p -> cupom == null || cupom.isBlank()
                        || (p instanceof CupomPromocional c && c.getCodigo().equalsIgnoreCase(cupom.trim())))
                .toList();
    }

    @Transactional
    public Promocao desativar(Long id) {
        Promocao p = promocaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Promoção não encontrada: " + id));
        p.desativar();
        return p;
    }

    private void validarProdutoAtivo(String sku) {
        Produto produto = produtoService.buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
    }
}
