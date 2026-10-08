package com.ecommerce.catalogo.service;

import com.ecommerce.catalogo.controller.dto.PrecoFinalResponse;
import com.ecommerce.catalogo.exception.ProdutoInativoException;
import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;
import com.ecommerce.catalogo.exception.RegraDeNegocioException;
import com.ecommerce.catalogo.model.CupomPromocional;
import com.ecommerce.catalogo.model.Produto;
import com.ecommerce.catalogo.model.Promocao;
import com.ecommerce.catalogo.repository.CupomRepository;
import com.ecommerce.catalogo.repository.PromocaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Optional;

/**
 * Calcula o preço final sob demanda. Regra: melhor promoção automática (percentual ou quantidade)
 * + cupom opcional, limitado ao subtotal. O preço de tabela nunca é alterado.
 */
@Service
public class PrecoFinalService {

    private final ProdutoService produtoService;
    private final PromocaoRepository promocaoRepository;
    private final CupomRepository cupomRepository;

    public PrecoFinalService(ProdutoService produtoService, PromocaoRepository promocaoRepository,
                             CupomRepository cupomRepository) {
        this.produtoService = produtoService;
        this.promocaoRepository = promocaoRepository;
        this.cupomRepository = cupomRepository;
    }

    @Transactional(readOnly = true)
    public PrecoFinalResponse calcular(String sku, int quantidade, String codigoCupom) {
        if (quantidade < 1) {
            throw new RegraDeNegocioException("A quantidade deve ser maior que zero");
        }
        Produto produto = produtoService.buscarPorSku(sku);
        if (!produto.isAtivo()) {
            throw new ProdutoInativoException(sku);
        }
        BigDecimal subtotal = produto.getPreco().multiply(BigDecimal.valueOf(quantidade));

        Optional<Promocao> melhor = promocaoRepository.findByAtivaTrueAndSkuProduto(sku).stream()
                .filter(p -> !"CUPOM".equals(p.getTipo()))
                .filter(p -> p.aplicarDesconto(produto, quantidade).signum() > 0)
                .max(Comparator.comparing(p -> p.aplicarDesconto(produto, quantidade)));

        BigDecimal desconto = melhor.map(p -> p.aplicarDesconto(produto, quantidade)).orElse(BigDecimal.ZERO);

        String cupomAplicado = null;
        if (codigoCupom != null && !codigoCupom.isBlank()) {
            CupomPromocional cupom = cupomRepository.findByCodigoIgnoreCase(codigoCupom.trim())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cupom não encontrado: " + codigoCupom));
            if (!cupom.aplicaA(produto)) {
                throw new RegraDeNegocioException("Cupom inválido, expirado ou não aplicável ao produto");
            }
            desconto = desconto.add(cupom.aplicarDesconto(produto, quantidade));
            cupomAplicado = cupom.getCodigo();
        }

        desconto = desconto.min(subtotal);
        BigDecimal total = subtotal.subtract(desconto);
        BigDecimal unitarioFinal = total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);

        return new PrecoFinalResponse(sku, quantidade, produto.getPreco(), subtotal, desconto, total,
                unitarioFinal, melhor.map(Promocao::getId).orElse(null), cupomAplicado);
    }
}
