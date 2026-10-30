package com.ecommerce.catalogo.integration.filter;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilterDispatchOutcome(
        String status,
        String batchId,
        int sentOffersCount,
        Integer approvedCount,
        Integer rejectedCount,
        String errorMessage,
        FilterBatchResponse filterResponse
) {
}

