package com.ecommerce.catalogo.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ecommerce.catalogo.model.Produto;
import com.ecommerce.catalogo.model.ProdutoDigital;
import com.ecommerce.catalogo.model.ProdutoFisico;

import java.math.BigDecimal;

/** Campos nulos são omitidos: físico nunca expõe link; digital nunca expõe dimensões. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProdutoResponse(
        String sku, String nome, BigDecimal preco, boolean ativo, String tipoProduto,
        Double peso, Double altura, Double largura, Double profundidade,
        String linkDownload, String tipoLicenca) {

    public static ProdutoResponse de(Produto p) {
        if (p instanceof ProdutoFisico f) {
            return new ProdutoResponse(f.getSku(), f.getNome(), f.getPreco(), f.isAtivo(), f.getTipoProduto(),
                    f.getPeso(), f.getAltura(), f.getLargura(), f.getProfundidade(), null, null);
        }
        ProdutoDigital d = (ProdutoDigital) p;
        return new ProdutoResponse(d.getSku(), d.getNome(), d.getPreco(), d.isAtivo(), d.getTipoProduto(),
                null, null, null, null, d.getLinkDownload(), d.getTipoLicenca());
    }
}
