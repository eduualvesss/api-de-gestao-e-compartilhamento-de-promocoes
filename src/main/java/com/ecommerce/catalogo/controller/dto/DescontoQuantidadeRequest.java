package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DescontoQuantidadeRequest(
        @NotBlank String sku,
        @Min(2) int qtdMinima,
        @Min(1) int qtdGratis) {
}
