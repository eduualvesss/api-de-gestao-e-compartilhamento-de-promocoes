package com.ecommerce.catalogo.integration;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.filter.FilterDispatchOutcome;
import com.ecommerce.catalogo.integration.filter.FilterServiceClient;
import com.ecommerce.catalogo.integration.model.DataSourceMode;
import com.ecommerce.catalogo.integration.model.RetailerId;
import com.ecommerce.catalogo.integration.model.UnifiedOffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Teste de contrato HTTP entre a API de Busca (Serviço 1) e a API de Filtros (Serviço 2).
 */
class FilterServiceContractTest {

    private IntegrationProperties properties;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        properties.getFilterApi().setEnabled(true);
        properties.getFilterApi().setBaseUrl("http://localhost:8081");
        properties.getFilterApi().setApiKey("internal-shared-secret-test");
        fixedClock = Clock.fixed(Instant.parse("2026-10-29T23:50:00Z"), ZoneOffset.UTC);
    }

    @Test
    void deveCumprirContratoDeEnvioEmLoteEReceberAvaliacaoDaApiDeFiltros() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String filterApiContractResponse = """
                {
                  "batchId": "b8c4e1a0-92f1-4c11-8d2e-112233445566",
                  "processedAt": "2026-10-29T23:50:02Z",
                  "summary": {
                    "received": 1,
                    "approved": 1,
                    "rejected": 0
                  },
                  "results": [
                    {
                      "internalId": "0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e",
                      "externalId": "2481920391",
                      "retailer": "SHOPEE",
                      "status": "APPROVED",
                      "qualityScore": 92.5,
                      "realDiscountVerified": true,
                      "historicalAvgPrice30d": 5120.00,
                      "historicalLowestPrice90d": 4199.00,
                      "rejectionReasons": []
                    }
                  ]
                }
                """;

        server.expect(requestTo("http://localhost:8081/api/v1/filter/offers:evaluate-batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Internal-Api-Key", "internal-shared-secret-test"))
                .andExpect(header("X-Idempotency-Key", notNullValue()))
                .andExpect(header("X-Correlation-Id", "corr-12345"))
                .andExpect(jsonPath("$.batchId").isNotEmpty())
                .andExpect(jsonPath("$.sourceInstance").value("catalogo-promocoes"))
                .andExpect(jsonPath("$.offers[0].internalId").value("0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e"))
                .andExpect(jsonPath("$.offers[0].retailer").value("SHOPEE"))
                .andExpect(jsonPath("$.offers[0].dataSourceMode").value("REAL"))
                .andExpect(jsonPath("$.offers[0].externalId").value("2481920391"))
                .andExpect(jsonPath("$.offers[0].urlAffiliate").value("https://s.shopee.com.br/4f9aBcDeFg"))
                .andExpect(jsonPath("$.offers[0].pricing.currency").value("BRL"))
                .andExpect(jsonPath("$.offers[0].pricing.currentPrice").value(4299.00))
                .andExpect(jsonPath("$.offers[0].pricing.originalPrice").value(5499.00))
                .andRespond(withSuccess(filterApiContractResponse, MediaType.APPLICATION_JSON));

        FilterServiceClient client = new FilterServiceClient(properties, builder, fixedClock, "catalogo-promocoes");

        FilterDispatchOutcome outcome = client.dispatchBatch(List.of(sampleOffer()), "corr-12345");

        server.verify();
        assertThat(outcome.status()).isEqualTo("SENT");
        assertThat(outcome.sentOffersCount()).isEqualTo(1);
        assertThat(outcome.approvedCount()).isEqualTo(1);
        assertThat(outcome.rejectedCount()).isEqualTo(0);
        assertThat(outcome.filterResponse().results()).hasSize(1);
        assertThat(outcome.filterResponse().results().get(0).qualityScore()).isEqualByComparingTo("92.5");
    }

    @Test
    void deveDegradarGraciosamenteQuandoApiDeFiltrosEstiverIndisponivel503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("http://localhost:8081/api/v1/filter/offers:evaluate-batch"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        FilterServiceClient client = new FilterServiceClient(properties, builder, fixedClock, "catalogo-promocoes");
        FilterDispatchOutcome outcome = client.dispatchBatch(List.of(sampleOffer()), "corr-falha");

        server.verify();
        assertThat(outcome.status()).isEqualTo("FAILED_DEGRADED");
        assertThat(outcome.errorMessage()).isNotBlank();
    }

    private UnifiedOffer sampleOffer() {
        Instant now = fixedClock.instant();
        return new UnifiedOffer(
                "0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e",
                RetailerId.SHOPEE,
                "SHOPEE_OPEN_API",
                DataSourceMode.REAL,
                "2481920391",
                "7891234567890",
                "EAN-7891234567890",
                "Apple iPhone 15 128GB Preto 5G",
                "https://shopee.com.br/product/123456/2481920391",
                "https://s.shopee.com.br/4f9aBcDeFg",
                "https://cf.shopee.com.br/file/iphone15.jpg",
                new UnifiedOffer.Pricing(
                        "BRL",
                        new BigDecimal("4299.00"),
                        new BigDecimal("5499.00"),
                        new BigDecimal("1200.00"),
                        new BigDecimal("21.82"),
                        "10x de R$ 429,90",
                        new BigDecimal("4099.00"),
                        false,
                        new BigDecimal("0.00")
                ),
                null,
                new UnifiedOffer.Category("100013", "SMARTPHONES"),
                new UnifiedOffer.Rating(new BigDecimal("4.9"), 1420, 3850),
                new UnifiedOffer.Seller("123456", "Apple Loja Oficial", true, "OFFICIAL_MALL"),
                new UnifiedOffer.Shipping(true, new BigDecimal("0.00"), "FULL"),
                new UnifiedOffer.Stock(true, 42),
                new UnifiedOffer.Timestamps(now, now.plusSeconds(21600)),
                "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        );
    }
}
