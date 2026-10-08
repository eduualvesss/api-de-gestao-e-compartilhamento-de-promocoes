package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CupomRequest(
        @NotBlank String codigo,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal valorFixo,
        @NotNull LocalDate validade,
        String skuEspecifico) {
}
