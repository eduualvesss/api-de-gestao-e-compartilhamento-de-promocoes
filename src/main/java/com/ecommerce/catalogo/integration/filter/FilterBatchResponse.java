package com.ecommerce.catalogo.integration.filter;

import com.ecommerce.catalogo.integration.model.RetailerId;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contrato de resposta da API de Filtros (Serviço 2) em POST /api/v1/filter/offers:evaluate-batch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilterBatchResponse(
        String batchId,
        Instant processedAt,
        Summary summary,
        List<OfferEvaluationResult> results
) {
    public record Summary(
            int received,
            int approved,
            int rejected
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OfferEvaluationResult(
            String internalId,
            String externalId,
            RetailerId retailer,
            String status,
            BigDecimal qualityScore,
            boolean realDiscountVerified,
            BigDecimal historicalAvgPrice30d,
            BigDecimal historicalLowestPrice90d,
            List<String> rejectionReasons
    ) {
    }
}
