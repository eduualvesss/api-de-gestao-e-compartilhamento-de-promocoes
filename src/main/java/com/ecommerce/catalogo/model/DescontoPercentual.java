package com.ecommerce.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "desconto_percentual")
public class DescontoPercentual extends Promocao {

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentual;

    protected DescontoPercentual() {
    }

    public DescontoPercentual(String sku, BigDecimal percentual) {
        super(sku);
        this.percentual = percentual;
    }

    @Override
    public BigDecimal aplicarDesconto(Produto produto, int quantidade) {
        if (!aplicaA(produto)) {
            return BigDecimal.ZERO;
        }
        return subtotal(produto, quantidade).multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String getTipo() { return "PERCENTUAL"; }

    public BigDecimal getPercentual() { return percentual; }
}
