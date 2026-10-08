package com.ecommerce.catalogo.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "promocao")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Promocao implements RegraDesconto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean ativa = true;

    /** SKU alvo; nulo apenas para cupons que valem para todos os produtos. */
    @Column(name = "sku_produto")
    private String skuProduto;

    protected Promocao() {
    }

    protected Promocao(String skuProduto) {
        this.skuProduto = skuProduto;
    }

    public abstract String getTipo();

    public boolean aplicaA(Produto produto) {
        return ativa && (skuProduto == null || skuProduto.equals(produto.getSku()));
    }

    public void desativar() {
        this.ativa = false;
    }

    protected BigDecimal subtotal(Produto produto, int quantidade) {
        return produto.getPreco().multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() { return id; }
    public boolean isAtiva() { return ativa; }
    public String getSkuProduto() { return skuProduto; }
}
