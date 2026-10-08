package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.Min;

public record QuantidadeRequest(@Min(value = 1, message = "deve ser maior que zero") int quantidade) {
}
