package com.ecommerce.catalogo.integration.filter;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.model.UnifiedOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Cliente HTTP responsável pelo envio de lotes normalizados para a API de Filtros
 * (POST /api/v1/filter/offers:evaluate-batch), com headers de idempotência, correlação e degradação graciosa.
 */
@Component
public class FilterServiceClient {

    public static final String EVALUATE_BATCH_PATH = "/api/v1/filter/offers:evaluate-batch";

    private static final Logger log = LoggerFactory.getLogger(FilterServiceClient.class);

    private final IntegrationProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final String applicationName;

    public FilterServiceClient(
            IntegrationProperties properties,
            RestClient.Builder restClientBuilder,
            Clock clock,
            @Value("${spring.application.name:catalogo-promocoes}") String applicationName
    ) {
        this.properties = properties;
        this.clock = clock;
        this.applicationName = applicationName;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getFilterApi().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getFilterApi().getReadTimeoutMs());
        this.restClient = restClientBuilder.clone().requestFactory(factory).build();
    }

    public FilterDispatchOutcome dispatchBatch(List<UnifiedOffer> offers, String correlationId) {
        IntegrationProperties.FilterApi cfg = properties.getFilterApi();
        if (!cfg.isEnabled()) {
            return new FilterDispatchOutcome("SKIPPED_DISABLED", null, 0, null, null, null, null);
        }
        if (offers == null || offers.isEmpty()) {
            return new FilterDispatchOutcome("SKIPPED_EMPTY_OR_ALREADY_SEEN", null, 0, 0, 0, null, null);
        }

        String batchId = UUID.randomUUID().toString();
        String corrId = (correlationId == null || correlationId.isBlank()) ? batchId : correlationId;
        FilterBatchRequest requestPayload = new FilterBatchRequest(
                batchId,
                clock.instant(),
                applicationName,
                offers
        );

        try {
            RestClient.RequestBodySpec req = restClient.post()
                    .uri(cfg.getBaseUrl() + EVALUATE_BATCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", batchId)
                    .header("X-Correlation-Id", corrId);

            if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
                req.header("X-Internal-Api-Key", cfg.getApiKey());
            }

            FilterBatchResponse response = req.body(requestPayload)
                    .retrieve()
                    .body(FilterBatchResponse.class);

            int approved = response != null && response.summary() != null ? response.summary().approved() : 0;
            int rejected = response != null && response.summary() != null ? response.summary().rejected() : 0;

            return new FilterDispatchOutcome(
                    "SENT",
                    batchId,
                    offers.size(),
                    approved,
                    rejected,
                    null,
                    response
            );
        } catch (Exception ex) {
            log.warn("Falha ao enviar lote {} para API de Filtros ({}): {}",
                    batchId, cfg.getBaseUrl(), ex.getMessage());
            return new FilterDispatchOutcome(
                    "FAILED_DEGRADED",
                    batchId,
                    offers.size(),
                    null,
                    null,
                    ex.getMessage(),
                    null
            );
        }
    }
}
