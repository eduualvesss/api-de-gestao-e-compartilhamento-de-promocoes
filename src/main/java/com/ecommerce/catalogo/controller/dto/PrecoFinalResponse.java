package com.ecommerce.catalogo.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrecoFinalResponse(
        String sku, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal,
        BigDecimal desconto, BigDecimal total, BigDecimal precoUnitarioFinal,
        Long promocaoAplicadaId, String cupomAplicado) {
}
