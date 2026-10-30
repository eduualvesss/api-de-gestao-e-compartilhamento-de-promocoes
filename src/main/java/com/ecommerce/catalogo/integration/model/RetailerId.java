package com.ecommerce.catalogo.integration.model;

import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Identificadores canônicos dos varejistas integrados à API de Busca.
 */
public enum RetailerId {
    SHOPEE("Shopee Brasil"),
    ALIEXPRESS("AliExpress"),
    MERCADO_LIVRE("Mercado Livre"),
    AMAZON("Amazon Brasil"),
    MAGALU("Magazine Luiza"),
    KABUM("KaBuM!"),
    CASAS_BAHIA("Casas Bahia"),
    AMERICANAS("Americanas"),
    NETSHOES("Netshoes");

    private final String displayName;

    RetailerId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RetailerId fromPathOrParam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RecursoNaoEncontradoException("Identificador de loja não informado");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "MERCADOLIVRE", "MLB" -> MERCADO_LIVRE;
            case "CASASBAHIA" -> CASAS_BAHIA;
            case "MAGAZINELUIZA" -> MAGALU;
            default -> Arrays.stream(values())
                    .filter(r -> r.name().equals(normalized))
                    .findFirst()
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Loja não suportada: " + raw));
        };
    }
}

