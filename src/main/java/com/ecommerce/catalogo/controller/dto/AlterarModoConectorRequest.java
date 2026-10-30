package com.ecommerce.catalogo.controller.dto;

import jakarta.validation.constraints.NotNull;

public record AlterarModoConectorRequest(@NotNull Boolean sandboxMode) {
}
