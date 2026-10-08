package com.ecommerce.catalogo.controller.dto;

import com.ecommerce.catalogo.model.Estoque;

public record EstoqueResponse(String sku, int quantidadeDisponivel) {

    public static EstoqueResponse de(Estoque e) {
        return new EstoqueResponse(e.getSku(), e.getQuantidadeDisponivel());
    }
}
