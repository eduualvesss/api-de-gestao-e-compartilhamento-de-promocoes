package com.ecommerce.catalogo.integration.service;

import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;
import com.ecommerce.catalogo.integration.connector.ConnectorRegistry;
import com.ecommerce.catalogo.integration.connector.RetailerConnector;
import com.ecommerce.catalogo.integration.exception.ConnectorConfigurationException;
import com.ecommerce.catalogo.integration.filter.FilterDispatchOutcome;
import com.ecommerce.catalogo.integration.filter.FilterServiceClient;
import com.ecommerce.catalogo.integration.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orquestrador paralelo da API de Busca: consulta conectores habilitados simultaneamente,
 * isola falhas parciais (uma loja falhando nunca derruba a busca), deduplica e despacha à API de Filtros.
 */
@Service
public class OfferSearchOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OfferSearchOrchestratorService.class);

    private final ConnectorRegistry connectorRegistry;
    private final OfferDeduplicator deduplicator;
    private final FilterServiceClient filterServiceClient;
    private final ExecutorService executorService;
    private final Clock clock;

    public OfferSearchOrchestratorService(
            ConnectorRegistry connectorRegistry,
            OfferDeduplicator deduplicator,
            FilterServiceClient filterServiceClient,
            ExecutorService connectorSearchExecutor,
            Clock clock
    ) {
        this.connectorRegistry = connectorRegistry;
        this.deduplicator = deduplicator;
        this.filterServiceClient = filterServiceClient;
        this.executorService = connectorSearchExecutor;
        this.clock = clock;
    }

    public AggregatedSearchResponse searchAcrossRetailers(SearchCriteria criteria, boolean dispatchToFilter) {
        String correlationId = UUID.randomUUID().toString();
        List<RetailerConnector> targets = connectorRegistry.listEnabled(criteria.retailers());

        List<CompletableFuture<ConnectorTaskResult>> futures = targets.stream()
                .map(connector -> CompletableFuture.supplyAsync(() -> executeSingleConnector(connector, criteria), executorService)
                        .orTimeout(25, TimeUnit.SECONDS)
                        .exceptionally(ex -> new ConnectorTaskResult(
                                new AggregatedSearchResponse.StoreExecutionStatus(
                                        connector.getRetailerId(),
                                        "FAILED",
                                        connector.isSandboxMode() ? DataSourceMode.SANDBOX : DataSourceMode.REAL,
                                        0,
                                        0L,
                                        ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()
                                ),
                                List.of()
                        )))
                .toList();

        List<AggregatedSearchResponse.StoreExecutionStatus> storeStatuses = new ArrayList<>();
        List<UnifiedOffer> rawOffers = new ArrayList<>();

        for (CompletableFuture<ConnectorTaskResult> future : futures) {
            ConnectorTaskResult res = future.join();
            storeStatuses.add(res.status());
            rawOffers.addAll(res.offers());
        }

        OfferDeduplicator.DeduplicationResult dedup = deduplicator.deduplicate(rawOffers, criteria.deduplicateCrossStore());
        List<UnifiedOffer> finalOffers = dedup.offers();
        boolean sandboxIncluded = finalOffers.stream().anyMatch(o -> o.dataSourceMode() == DataSourceMode.SANDBOX);

        FilterDispatchOutcome dispatchOutcome = null;
        if (dispatchToFilter) {
            List<UnifiedOffer> unseenOffers = deduplicator.filterUnseenForDispatch(finalOffers);
            dispatchOutcome = filterServiceClient.dispatchBatch(unseenOffers, correlationId);
        }

        return new AggregatedSearchResponse(
                correlationId,
                clock.instant(),
                sandboxIncluded,
                finalOffers.size(),
                new AggregatedSearchResponse.DeduplicationSummary(
                        dedup.totalRawOffers(),
                        dedup.intraStoreDuplicatesRemoved(),
                        dedup.crossStoreGroupsMatched(),
                        dedup.crossStoreDuplicatesCollapsed()
                ),
                storeStatuses,
                dispatchOutcome,
                finalOffers
        );
    }

    public UnifiedOffer getSingleOffer(RetailerId retailerId, String externalId) {
        RetailerConnector connector = connectorRegistry.getRequired(retailerId);
        return connector.getOffer(externalId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Oferta " + externalId + " não encontrada na loja " + retailerId));
    }

    private ConnectorTaskResult executeSingleConnector(RetailerConnector connector, SearchCriteria criteria) {
        long startNs = System.nanoTime();
        DataSourceMode mode = connector.isSandboxMode() ? DataSourceMode.SANDBOX : DataSourceMode.REAL;
        try {
            List<UnifiedOffer> offers = connector.search(criteria);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            return new ConnectorTaskResult(
                    new AggregatedSearchResponse.StoreExecutionStatus(
                            connector.getRetailerId(),
                            "SUCCESS",
                            mode,
                            offers.size(),
                            latencyMs,
                            null
                    ),
                    offers
            );
        } catch (ConnectorConfigurationException cfgEx) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.info("[{}] Conector pulado por configuração: {}", connector.getRetailerId(), cfgEx.getMessage());
            return new ConnectorTaskResult(
                    new AggregatedSearchResponse.StoreExecutionStatus(
                            connector.getRetailerId(),
                            "SKIPPED_MISSING_CREDENTIALS",
                            mode,
                            0,
                            latencyMs,
                            cfgEx.getMessage()
                    ),
                    List.of()
            );
        } catch (Exception ex) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.warn("[{}] Falha parcial isolada ao buscar ofertas ({}ms): {}",
                    connector.getRetailerId(), latencyMs, ex.getMessage());
            return new ConnectorTaskResult(
                    new AggregatedSearchResponse.StoreExecutionStatus(
                            connector.getRetailerId(),
                            "FAILED",
                            mode,
                            0,
                            latencyMs,
                            ex.getMessage()
                    ),
                    List.of()
            );
        }
    }

    private record ConnectorTaskResult(
            AggregatedSearchResponse.StoreExecutionStatus status,
            List<UnifiedOffer> offers
    ) {
    }
}

