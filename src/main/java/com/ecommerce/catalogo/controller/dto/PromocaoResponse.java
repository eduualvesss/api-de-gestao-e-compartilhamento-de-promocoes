package com.ecommerce.catalogo.controller.dto;

import com.ecommerce.catalogo.model.CupomPromocional;
import com.ecommerce.catalogo.model.DescontoPercentual;
import com.ecommerce.catalogo.model.DescontoQuantidade;
import com.ecommerce.catalogo.model.Promocao;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromocaoResponse(
        Long id, String tipo, boolean ativa, String sku,
        BigDecimal percentual,
        Integer qtdMinima, Integer qtdGratis,
        String codigo, BigDecimal valorFixo, LocalDate validade) {

    public static PromocaoResponse de(Promocao p) {
        if (p instanceof DescontoPercentual d) {
            return new PromocaoResponse(d.getId(), d.getTipo(), d.isAtiva(), d.getSkuProduto(),
                    d.getPercentual(), null, null, null, null, null);
        }
        if (p instanceof DescontoQuantidade q) {
            return new PromocaoResponse(q.getId(), q.getTipo(), q.isAtiva(), q.getSkuProduto(),
                    null, q.getQtdMinima(), q.getQtdGratis(), null, null, null);
        }
        CupomPromocional c = (CupomPromocional) p;
        return new PromocaoResponse(c.getId(), c.getTipo(), c.isAtiva(), c.getSkuEspecifico(),
                null, null, null, c.getCodigo(), c.getValorFixo(), c.getValidade());
    }
}
