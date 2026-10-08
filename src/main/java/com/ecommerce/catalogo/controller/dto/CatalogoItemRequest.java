package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CatalogoItemRequest(@NotBlank String sku, @Min(0) int ordem) {
}
