package com.ecommerce.catalogo.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Schema Unificado de Oferta (UnifiedOffer) compartilhado entre a API de Busca
 * e a API de Filtros, mantendo todos os metadados de preço, afiliado, loja e origem.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedOffer(
        String internalId,
        RetailerId retailer,
        String connectorSource,
        DataSourceMode dataSourceMode,
        String externalId,
        String eanGtin,
        String skuCanonical,
        String title,
        String urlOriginal,
        String urlAffiliate,
        String imageUrl,
        Pricing pricing,
        Coupon coupon,
        Category category,
        Rating rating,
        Seller seller,
        Shipping shipping,
        Stock stock,
        Timestamps timestamps,
        String contentHash
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pricing(
            String currency,
            BigDecimal currentPrice,
            BigDecimal originalPrice,
            BigDecimal discountAbsolute,
            BigDecimal discountPercentage,
            String installmentText,
            BigDecimal pixOrCashPrice,
            boolean isInternational,
            BigDecimal estimatedImportTax
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Coupon(
            String code,
            String discountDescription,
            BigDecimal discountValue,
            String discountType,
            Instant validUntil
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Category(
            String externalCategoryId,
            String canonicalCategory
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rating(
            BigDecimal averageStars,
            Integer reviewsCount,
            Integer soldCount
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Seller(
            String externalSellerId,
            String name,
            boolean isOfficialStore,
            String reputationLevel
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Shipping(
            boolean freeShipping,
            BigDecimal minShippingCost,
            String fulfillmentType
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stock(
            boolean inStock,
            Integer availableQuantity
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Timestamps(
            Instant collectedAt,
            Instant expiresAt
    ) {
    }

    /**
     * Custo efetivo total estimado em BRL (preço atual + imposto internacional estimado + frete mínimo - cupom fixo).
     */
    public BigDecimal effectiveCostBrl() {
        BigDecimal total = pricing != null && pricing.currentPrice() != null
                ? pricing.currentPrice()
                : BigDecimal.ZERO;
        if (pricing != null && pricing.estimatedImportTax() != null) {
            total = total.add(pricing.estimatedImportTax());
        }
        if (shipping != null && !shipping.freeShipping() && shipping.minShippingCost() != null) {
            total = total.add(shipping.minShippingCost());
        }
        if (coupon != null && "FIXED".equalsIgnoreCase(coupon.discountType()) && coupon.discountValue() != null) {
            total = total.subtract(coupon.discountValue()).max(BigDecimal.ZERO);
        }
        return total;
    }
}

