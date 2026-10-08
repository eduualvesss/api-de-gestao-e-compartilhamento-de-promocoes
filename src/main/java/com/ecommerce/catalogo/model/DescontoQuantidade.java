package com.ecommerce.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** "Leve qtdMinima, pague (qtdMinima - qtdGratis)". */
@Entity
@Table(name = "desconto_quantidade")
public class DescontoQuantidade extends Promocao {

    @Column(name = "qtd_minima", nullable = false)
    private int qtdMinima;

    @Column(name = "qtd_gratis", nullable = false)
    private int qtdGratis;

    protected DescontoQuantidade() {
    }

    public DescontoQuantidade(String sku, int qtdMinima, int qtdGratis) {
        super(sku);
        this.qtdMinima = qtdMinima;
        this.qtdGratis = qtdGratis;
    }

    @Override
    public BigDecimal aplicarDesconto(Produto produto, int quantidade) {
        if (!aplicaA(produto) || quantidade < qtdMinima) {
            return BigDecimal.ZERO;
        }
        int grupos = quantidade / qtdMinima;
        return produto.getPreco().multiply(BigDecimal.valueOf((long) grupos * qtdGratis));
    }

    @Override
    public String getTipo() { return "QUANTIDADE"; }

    public int getQtdMinima() { return qtdMinima; }
    public int getQtdGratis() { return qtdGratis; }
}
