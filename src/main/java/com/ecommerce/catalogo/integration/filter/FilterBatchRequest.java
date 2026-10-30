package com.ecommerce.catalogo.integration.filter;

import com.ecommerce.catalogo.integration.model.UnifiedOffer;

import java.time.Instant;
import java.util.List;

/**
 * Payload de envio em lote da API de Busca (Serviço 1) para a API de Filtros (Serviço 2)
 * em POST /api/v1/filter/offers:evaluate-batch.
 */
public record FilterBatchRequest(
        String batchId,
        Instant collectedAt,
        String sourceInstance,
        List<UnifiedOffer> offers
) {
}

