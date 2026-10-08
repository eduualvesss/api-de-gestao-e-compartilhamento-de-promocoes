package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CatalogoRequest(@NotBlank String nome, String descricao, Boolean ativo) {
}
