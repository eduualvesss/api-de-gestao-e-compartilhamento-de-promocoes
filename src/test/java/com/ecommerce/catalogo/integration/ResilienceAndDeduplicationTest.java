package com.ecommerce.catalogo.integration;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.connector.ConnectorRegistry;
import com.ecommerce.catalogo.integration.connector.RetailerConnector;
import com.ecommerce.catalogo.integration.connector.impl.AmazonConnector;
import com.ecommerce.catalogo.integration.connector.impl.LomadeeRetailerConnector;
import com.ecommerce.catalogo.integration.connector.impl.ShopeeConnector;
import com.ecommerce.catalogo.integration.exception.ConnectorExecutionException;
import com.ecommerce.catalogo.integration.filter.FilterServiceClient;
import com.ecommerce.catalogo.integration.model.*;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import com.ecommerce.catalogo.integration.service.OfferDeduplicator;
import com.ecommerce.catalogo.integration.service.OfferSearchOrchestratorService;
import com.ecommerce.catalogo.integration.service.PriceAndCurrencyNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceAndDeduplicationTest {

    private IntegrationProperties properties;
    private Clock fixedClock;
    private List<Long> recordedSleeps;
    private ConnectorResilienceExecutor resilience;
    private PriceAndCurrencyNormalizer normalizer;
    private OfferDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        properties.getResilience().setRetryMaxAttempts(3);
        properties.getResilience().setRetryInitialDelayMs(100);
        properties.getResilience().setCircuitFailureThreshold(2);
        properties.getResilience().setCircuitOpenDurationSeconds(60);

        fixedClock = Clock.fixed(Instant.parse("2026-10-29T23:30:00Z"), ZoneOffset.UTC);
        recordedSleeps = new ArrayList<>();
        resilience = new ConnectorResilienceExecutor(properties, fixedClock, recordedSleeps::add);
        normalizer = new PriceAndCurrencyNormalizer(properties);
        deduplicator = new OfferDeduplicator(properties, fixedClock);
    }

    @Test
    void deveExecutarRetryComBackoffExponencialEmErro429TooManyRequests() {
        AtomicInteger attempts = new AtomicInteger();
        String result = resilience.execute(RetailerId.SHOPEE, 0.0, () -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit", org.springframework.http.HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            }
            return "OK_APOS_RETRY";
        });

        assertThat(result).isEqualTo("OK_APOS_RETRY");
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(recordedSleeps).hasSize(2);
        assertThat(recordedSleeps.get(1)).isGreaterThan(recordedSleeps.get(0));
    }

    @Test
    void deveAbrirCircuitBreakerAposFalhasConsecutivasEBloquearChamadasImediatamente() {
        properties.getResilience().setRetryMaxAttempts(1);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> resilience.execute(RetailerId.AMAZON, 0.0, () -> {
                throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "Down", org.springframework.http.HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            })).isInstanceOf(ConnectorExecutionException.class);
        }

        assertThat(resilience.snapshot(RetailerId.AMAZON).circuitState()).isEqualTo("OPEN");

        assertThatThrownBy(() -> resilience.execute(RetailerId.AMAZON, 0.0, () -> "NUNCA_CHAMADO"))
                .isInstanceOf(ConnectorExecutionException.class)
                .hasMessageContaining("Circuit Breaker ABERTO");
    }

    @Test
    void deveNormalizarUsdParaBrlEDeduplicarMesmoEanEntreLojasPreservandoMenorCustoEfetivo() {
        UnifiedOffer.Pricing usdPricing = normalizer.buildPricing(
                new BigDecimal("100.00"), new BigDecimal("150.00"), "USD", null, null, false);
        assertThat(usdPricing.currency()).isEqualTo("BRL");
        assertThat(usdPricing.currentPrice()).isEqualByComparingTo("575.00");
        assertThat(usdPricing.originalPrice()).isEqualByComparingTo("862.50");
        assertThat(usdPricing.discountPercentage()).isEqualByComparingTo("33.33");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        properties.getRetailers().getShopee().setSandboxMode(true);
        properties.getRetailers().getAmazon().setEnabled(true);
        properties.getRetailers().getAmazon().setSandboxMode(true);
        properties.getRetailers().getMagalu().setEnabled(true);
        properties.getRetailers().getMagalu().setSandboxMode(true);
        // Mercado Livre habilitado SEM credenciais e SEM sandbox para provar falha parcial isolada
        properties.getRetailers().getMercadolivre().setEnabled(true);
        properties.getRetailers().getMercadolivre().setSandboxMode(false);

        ShopeeConnector shopee = new ShopeeConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, RestClient.builder(), mapper);
        AmazonConnector amazon = new AmazonConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, RestClient.builder(), mapper);
        RetailerConnector magalu = new LomadeeRetailerConnector(
                RetailerId.MAGALU, properties.getRetailers().getMagalu(),
                properties, resilience, normalizer, deduplicator, fixedClock, RestClient.builder().build(), mapper);
        var ml = new com.ecommerce.catalogo.integration.connector.impl.MercadoLivreConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, RestClient.builder(), mapper);

        ConnectorRegistry registry = new ConnectorRegistry(List.of(shopee, amazon, magalu, ml));
        FilterServiceClient filterClient = new FilterServiceClient(properties, RestClient.builder(), fixedClock, "test-app");
        var executor = Executors.newFixedThreadPool(4);
        try {
            OfferSearchOrchestratorService orchestrator = new OfferSearchOrchestratorService(
                    registry, deduplicator, filterClient, executor, fixedClock);

            AggregatedSearchResponse resp = orchestrator.searchAcrossRetailers(
                    new SearchCriteria("Galaxy S24", null, null, null, Set.of(), true, 1, 20), false);

            // Shopee (R$ 3499), Magalu (R$ 3349) e Amazon (R$ 3299) possuem o mesmo EAN 7891234567890:
            // Deduplicação cross-store deve colapsar os 3 em 1 única oferta vencedora (Amazon R$ 3299.00)
            // E o Mercado Livre sem credenciais não deve derrubar a busca!
            assertThat(resp.sandboxDataIncluded()).isTrue();
            assertThat(resp.deduplication().totalRawOffers()).isEqualTo(3);
            assertThat(resp.deduplication().crossStoreGroupsMatched()).isEqualTo(1);
            assertThat(resp.deduplication().crossStoreDuplicatesCollapsed()).isEqualTo(2);
            assertThat(resp.offers()).hasSize(1);
            assertThat(resp.offers().get(0).retailer()).isEqualTo(RetailerId.AMAZON);
            assertThat(resp.offers().get(0).pricing().currentPrice()).isEqualByComparingTo("3299.00");
            assertThat(resp.storeStatuses())
                    .anyMatch(s -> s.retailer() == RetailerId.MERCADO_LIVRE && "SKIPPED_MISSING_CREDENTIALS".equals(s.status()));
        } finally {
            executor.shutdownNow();
        }
    }
}
