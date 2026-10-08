package com.ecommerce.catalogo.controller.dto;

import java.util.List;

public record CatalogoResponse(Long id, String nome, String descricao, boolean ativo, List<Item> produtos) {

    public record Item(String sku, int ordem) {
    }
}
