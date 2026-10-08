package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DescontoPercentualRequest(
        @NotBlank String sku,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("100.00") BigDecimal percentual) {
}
