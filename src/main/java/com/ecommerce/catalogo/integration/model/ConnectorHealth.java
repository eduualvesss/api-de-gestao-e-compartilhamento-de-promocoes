package com.ecommerce.catalogo.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Estado de saúde e prontidão operacional de um conector de varejista.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectorHealth(
        RetailerId retailer,
        String displayName,
        boolean enabled,
        boolean sandboxMode,
        boolean credentialsConfigured,
        String operationalStatus,
        String circuitState,
        long successfulCalls,
        long failedCalls,
        String lastError,
        Instant lastFailureAt,
        Instant checkedAt
) {
}

