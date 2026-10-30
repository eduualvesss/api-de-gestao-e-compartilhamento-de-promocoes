package com.ecommerce.catalogo.integration.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Critérios de busca de ofertas promocionais nos conectores de varejistas.
 */
public record SearchCriteria(
        String keyword,
        String category,
        BigDecimal minDiscountPercentage,
        BigDecimal maxPriceBrl,
        Set<RetailerId> retailers,
        boolean deduplicateCrossStore,
        int page,
        int limit
) {
    public SearchCriteria {
        keyword = keyword == null ? "" : keyword.trim();
        category = (category == null || category.isBlank()) ? null : category.trim();
        page = Math.max(1, page);
        limit = Math.min(50, Math.max(1, limit));
    }
}

