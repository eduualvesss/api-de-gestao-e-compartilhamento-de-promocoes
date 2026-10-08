package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Payload único; campos específicos de cada tipo são validados no service.
 * No PUT o campo sku é ignorado/rejeitado se diferir do path.
 */
public record ProdutoRequest(
        @NotBlank String sku,
        @NotBlank String nome,
        @NotNull BigDecimal preco,
        Boolean ativo,
        @NotBlank @Pattern(regexp = "FISICO|DIGITAL", message = "deve ser FISICO ou DIGITAL") String tipoProduto,
        Double peso, Double altura, Double largura, Double profundidade,
        String linkDownload, String tipoLicenca) {
}
