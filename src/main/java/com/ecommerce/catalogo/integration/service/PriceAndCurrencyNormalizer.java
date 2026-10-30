package com.ecommerce.catalogo.integration.service;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.model.RetailerId;
import com.ecommerce.catalogo.integration.model.UnifiedOffer;
import com.ecommerce.catalogo.integration.util.CryptoSigningUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Normaliza preços, moedas (USD -> BRL), percentuais de desconto reais,
 * estimativa de tributos internacionais (Remessa Conforme) e contentHash.
 */
@Component
public class PriceAndCurrencyNormalizer {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal LIMITE_USD_REMESSA = new BigDecimal("50.00");
    // Alíquota efetiva aproximada Remessa Conforme até US$ 50 (20% II + 17% ICMS por dentro ~ 44.58%)
    private static final BigDecimal ALIQUOTA_ATE_50_USD = new BigDecimal("0.4458");
    // Alíquota efetiva aproximada acima de US$ 50 (~92.77% bruto com dedução de US$ 20)
    private static final BigDecimal ALIQUOTA_ACIMA_50_USD = new BigDecimal("0.9277");
    private static final BigDecimal DEDUCAO_ACIMA_50_USD = new BigDecimal("20.00");

    private final IntegrationProperties properties;

    public PriceAndCurrencyNormalizer(IntegrationProperties properties) {
        this.properties = properties;
    }

    public BigDecimal toBrl(BigDecimal amount, String sourceCurrency) {
        if (amount == null || amount.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String curr = sourceCurrency == null ? "BRL" : sourceCurrency.trim().toUpperCase(Locale.ROOT);
        if ("BRL".equals(curr) || "R$".equals(curr)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        if ("USD".equals(curr) || "US$".equals(curr)) {
            BigDecimal rate = properties.getCurrency().getUsdToBrlRate();
            return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public UnifiedOffer.Pricing buildPricing(
            BigDecimal rawCurrentPrice,
            BigDecimal rawOriginalPrice,
            String sourceCurrency,
            String installmentText,
            BigDecimal rawPixPrice,
            boolean isInternational
    ) {
        BigDecimal currentBrl = toBrl(rawCurrentPrice, sourceCurrency);
        BigDecimal originalBrl = rawOriginalPrice != null ? toBrl(rawOriginalPrice, sourceCurrency) : currentBrl;
        if (originalBrl.compareTo(currentBrl) < 0) {
            originalBrl = currentBrl;
        }

        BigDecimal discountAbs = originalBrl.subtract(currentBrl).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (originalBrl.signum() > 0 && discountAbs.signum() > 0) {
            discountPct = discountAbs.multiply(CEM).divide(originalBrl, 2, RoundingMode.HALF_UP);
        }

        BigDecimal pixBrl = rawPixPrice != null ? toBrl(rawPixPrice, sourceCurrency) : null;
        BigDecimal estimatedTax = isInternational ? estimateImportTaxBrl(currentBrl) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new UnifiedOffer.Pricing(
                "BRL",
                currentBrl,
                originalBrl,
                discountAbs,
                discountPct,
                installmentText,
                pixBrl,
                isInternational,
                estimatedTax
        );
    }

    public BigDecimal estimateImportTaxBrl(BigDecimal priceBrl) {
        if (priceBrl == null || priceBrl.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rate = properties.getCurrency().getUsdToBrlRate();
        if (rate == null || rate.signum() <= 0) {
            rate = new BigDecimal("5.75");
        }
        BigDecimal approxUsd = priceBrl.divide(rate, 2, RoundingMode.HALF_UP);
        if (approxUsd.compareTo(LIMITE_USD_REMESSA) <= 0) {
            return priceBrl.multiply(ALIQUOTA_ATE_50_USD).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal deductionBrl = DEDUCAO_ACIMA_50_USD.multiply(rate);
        return priceBrl.multiply(ALIQUOTA_ACIMA_50_USD)
                .subtract(deductionBrl)
                .max(priceBrl.multiply(ALIQUOTA_ATE_50_USD))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public String computeContentHash(RetailerId retailer, String externalId, BigDecimal currentPriceBrl,
                                     String couponCode, boolean inStock) {
        String normalizedPrice = currentPriceBrl == null ? "0.00" : currentPriceBrl.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String normalizedCoupon = couponCode == null ? "" : couponCode.trim().toUpperCase(Locale.ROOT);
        String raw = retailer.name() + ":" + externalId + ":" + normalizedPrice + ":" + normalizedCoupon + ":" + inStock;
        return "sha256:" + CryptoSigningUtils.sha256HexLower(raw);
    }
}

