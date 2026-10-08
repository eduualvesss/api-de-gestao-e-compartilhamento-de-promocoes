package com.ecommerce.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cupom_promocional")
public class CupomPromocional extends Promocao {

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(name = "valor_fixo", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorFixo;

    @Column(nullable = false)
    private LocalDate validade;

    protected CupomPromocional() {
    }

    /** @param skuEspecifico opcional; nulo aplica a todos os produtos. */
    public CupomPromocional(String codigo, BigDecimal valorFixo, LocalDate validade, String skuEspecifico) {
        super(skuEspecifico);
        this.codigo = codigo;
        this.valorFixo = valorFixo;
        this.validade = validade;
    }

    public boolean expirado() {
        return validade.isBefore(LocalDate.now());
    }

    @Override
    public boolean aplicaA(Produto produto) {
        return !expirado() && super.aplicaA(produto);
    }

    @Override
    public BigDecimal aplicarDesconto(Produto produto, int quantidade) {
        if (!aplicaA(produto)) {
            return BigDecimal.ZERO;
        }
        return valorFixo.min(subtotal(produto, quantidade));
    }

    @Override
    public String getTipo() { return "CUPOM"; }

    public String getCodigo() { return codigo; }
    public BigDecimal getValorFixo() { return valorFixo; }
    public LocalDate getValidade() { return validade; }
    public String getSkuEspecifico() { return getSkuProduto(); }
}
