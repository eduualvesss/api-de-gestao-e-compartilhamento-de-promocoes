package com.ecommerce.catalogo.integration.model;

import com.ecommerce.catalogo.integration.filter.FilterDispatchOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Resposta completa da busca agregada multi-varejista, expondo claramente falhas parciais,
 * estatísticas de deduplicação, flag explícita de sandbox e resultado do envio à API de Filtros.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggregatedSearchResponse(
        String correlationId,
        Instant executedAt,
        boolean sandboxDataIncluded,
        int totalReturned,
        DeduplicationSummary deduplication,
        List<StoreExecutionStatus> storeStatuses,
        FilterDispatchOutcome filterDispatch,
        List<UnifiedOffer> offers
) {
    public record DeduplicationSummary(
            int totalRawOffers,
            int intraStoreDuplicatesRemoved,
            int crossStoreGroupsMatched,
            int crossStoreDuplicatesCollapsed
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StoreExecutionStatus(
            RetailerId retailer,
            String status,
            DataSourceMode mode,
            int offersReturned,
            long latencyMs,
            String message
    ) {
    }
}
