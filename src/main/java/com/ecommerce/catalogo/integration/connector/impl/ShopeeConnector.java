package com.ecommerce.catalogo.integration.connector.impl;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.connector.AbstractRetailerConnector;
import com.ecommerce.catalogo.integration.exception.ConnectorExecutionException;
import com.ecommerce.catalogo.integration.model.*;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import com.ecommerce.catalogo.integration.service.OfferDeduplicator;
import com.ecommerce.catalogo.integration.service.PriceAndCurrencyNormalizer;
import com.ecommerce.catalogo.integration.util.CryptoSigningUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Conector Oficial Shopee Affiliate Open API (Brasil) — GraphQL com assinatura SHA-256.
 * Documentação: https://affiliate.shopee.com.br/ / https://open-api.affiliate.shopee.com.br/graphql
 */
@Component
public class ShopeeConnector extends AbstractRetailerConnector {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ShopeeConnector(
            IntegrationProperties properties,
            ConnectorResilienceExecutor resilienceExecutor,
            PriceAndCurrencyNormalizer normalizer,
            OfferDeduplicator deduplicator,
            Clock clock,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(
                RetailerId.SHOPEE,
                properties.getRetailers().getShopee().isEnabled(),
                properties.getRetailers().getShopee().isSandboxMode(),
                properties,
                resilienceExecutor,
                normalizer,
                deduplicator,
                clock
        );
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasRealCredentials() {
        IntegrationProperties.Shopee cfg = properties.getRetailers().getShopee();
        return notBlank(cfg.getAppId()) && notBlank(cfg.getAppSecret());
    }

    @Override
    protected List<UnifiedOffer> executeRealSearch(SearchCriteria criteria) {
        String safeKeyword = criteria.keyword().replace("\"", "");
        String gql = "query { productOfferV2(keyword: \"%s\", page: %d, limit: %d) { nodes { "
                .formatted(safeKeyword, criteria.page(), criteria.limit())
                + "itemId productName price priceMin priceMax priceDiscountRate "
                + "imageUrl productLink offerLink shopId shopName shopType ratingStar sales periodEndTime } } }";

        JsonNode root = postGraphQl(gql);
        JsonNode nodes = root.path("data").path("productOfferV2").path("nodes");
        if (!nodes.isArray()) {
            return List.of();
        }
        List<UnifiedOffer> offers = new ArrayList<>();
        for (JsonNode node : nodes) {
            offers.add(normalize(node, DataSourceMode.REAL));
        }
        return offers;
    }

    @Override
    protected Optional<UnifiedOffer> executeRealGetOffer(String externalId) {
        String cleanId = externalId.replace("SANDBOX-", "").replace("\"", "");
        String gql = "query { productOfferV2(keyword: \"%s\", page: 1, limit: 5) { nodes { "
                .formatted(cleanId)
                + "itemId productName price priceMin priceMax priceDiscountRate "
                + "imageUrl productLink offerLink shopId shopName shopType ratingStar sales periodEndTime } } }";

        JsonNode root = postGraphQl(gql);
        JsonNode nodes = root.path("data").path("productOfferV2").path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (cleanId.equals(node.path("itemId").asText())) {
                    return Optional.of(normalize(node, DataSourceMode.REAL));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public UnifiedOffer normalize(JsonNode rawItem, DataSourceMode mode) {
        String rawId = rawItem.path("itemId").asText("0");
        String externalId = mode == DataSourceMode.SANDBOX && !rawId.startsWith("SANDBOX-")
                ? "SANDBOX-" + rawId
                : rawId;

        String title = rawItem.path("productName").asText("Oferta Shopee");
        BigDecimal currentPrice = parseDecimalOrNull(rawItem.path("priceMin").asText(rawItem.path("price").asText("0")));
        if (currentPrice == null) {
            currentPrice = BigDecimal.ZERO;
        }
        BigDecimal discountRate = parseDecimalOrNull(rawItem.path("priceDiscountRate").asText("0"));
        BigDecimal originalPrice = currentPrice;
        if (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0
                && discountRate.compareTo(new BigDecimal("99")) <= 0) {
            BigDecimal factor = BigDecimal.ONE.subtract(discountRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            if (factor.signum() > 0) {
                originalPrice = currentPrice.divide(factor, 2, RoundingMode.HALF_UP);
            }
        }

        String ean = rawItem.hasNonNull("ean") ? rawItem.get("ean").asText() : null;
        String urlOriginal = sanitizeExternalUrl(rawItem.path("productLink")
                .asText("https://shopee.com.br/product/0/" + rawId));
        String offerLink = rawItem.path("offerLink").asText("");
        String urlAffiliate = notBlank(offerLink)
                ? sanitizeExternalUrl(offerLink)
                : buildAffiliateUrl(urlOriginal, defaultSubId(null));
        String imageUrl = sanitizeExternalUrl(rawItem.path("imageUrl").asText(null));

        UnifiedOffer.Pricing pricing = normalizer.buildPricing(
                currentPrice, originalPrice, "BRL", null, null, false);

        Instant now = clock.instant();
        long endEpoch = rawItem.path("periodEndTime").asLong(0L);
        Instant expiresAt = endEpoch > now.getEpochSecond()
                ? Instant.ofEpochSecond(endEpoch)
                : now.plusSeconds(6 * 3600);

        BigDecimal stars = parseDecimalOrNull(rawItem.path("ratingStar").asText(null));
        int sales = rawItem.path("sales").asInt(0);
        boolean isOfficial = rawItem.path("shopType").asText("").contains("1")
                || rawItem.path("shopName").asText("").toLowerCase(Locale.ROOT).contains("oficial");

        String skuCanonical = deduplicator.computeCanonicalSku(ean, title);
        String contentHash = normalizer.computeContentHash(RetailerId.SHOPEE, externalId, pricing.currentPrice(), null, true);

        return new UnifiedOffer(
                UUID.randomUUID().toString(),
                RetailerId.SHOPEE,
                mode == DataSourceMode.SANDBOX ? "SHOPEE_SANDBOX" : "SHOPEE_OPEN_API",
                mode,
                externalId,
                ean,
                skuCanonical,
                title,
                urlOriginal,
                urlAffiliate,
                imageUrl,
                pricing,
                null,
                new UnifiedOffer.Category(null, "GERAL"),
                new UnifiedOffer.Rating(stars, null, sales > 0 ? sales : null),
                new UnifiedOffer.Seller(
                        rawItem.path("shopId").asText(null),
                        rawItem.path("shopName").asText("Loja Shopee"),
                        isOfficial,
                        isOfficial ? "OFFICIAL_MALL" : "STANDARD"
                ),
                new UnifiedOffer.Shipping(true, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), "SHOPEE_XPRESS"),
                new UnifiedOffer.Stock(true, null),
                new UnifiedOffer.Timestamps(now, expiresAt),
                contentHash
        );
    }

    @Override
    public String buildAffiliateUrl(String originalUrl, String campaignSubId) {
        String cleanOriginal = sanitizeExternalUrl(originalUrl);
        String subId = notBlank(campaignSubId) ? campaignSubId : defaultSubId(null);
        if (!isSandboxMode() && hasRealCredentials() && isEnabled()) {
            try {
                String gql = "mutation { generateShortLink(input: { originUrl: \"%s\", subIds: [\"%s\"] }) { shortLink } }"
                        .formatted(cleanOriginal.replace("\"", ""), subId.replace("\"", ""));
                JsonNode root = resilienceExecutor.execute(RetailerId.SHOPEE, getMaxTps(), () -> postGraphQl(gql));
                String shortLink = root.path("data").path("generateShortLink").path("shortLink").asText("");
                if (notBlank(shortLink)) {
                    return sanitizeExternalUrl(shortLink);
                }
            } catch (Exception ignored) {
                // Fallback seguro para link parametrizado de afiliado
            }
        }
        String sep = cleanOriginal.contains("?") ? "&" : "?";
        return cleanOriginal + sep + "utm_source=shopee_affiliate&sub_id=" + URLEncoder.encode(subId, StandardCharsets.UTF_8);
    }

    private JsonNode postGraphQl(String query) {
        try {
            IntegrationProperties.Shopee cfg = properties.getRetailers().getShopee();
            String jsonBody = objectMapper.writeValueAsString(Map.of("query", query));
            long timestamp = clock.instant().getEpochSecond();
            String authHeader = CryptoSigningUtils.buildShopeeAuthorizationHeader(
                    cfg.getAppId(), timestamp, jsonBody, cfg.getAppSecret());

            String responseBody = restClient.post()
                    .uri(cfg.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authHeader)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            if (root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
                String msg = root.get("errors").get(0).path("message").asText("Erro GraphQL Shopee");
                throw new ConnectorExecutionException(RetailerId.SHOPEE, "Shopee GraphQL erro: " + msg, false);
            }
            return root;
        } catch (ConnectorExecutionException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.SHOPEE, "Erro ao processar resposta Shopee: " + e.getMessage(), false, e);
        }
    }

    @Override
    protected List<UnifiedOffer> executeSandboxSearch(SearchCriteria criteria) {
        try {
            String sampleJson = """
                    [
                      {
                        "itemId": "2481920391",
                        "ean": "7891234567890",
                        "productName": "[SANDBOX] Smartphone Samsung Galaxy S24 256GB 5G Preto",
                        "priceMin": "3499.00",
                        "priceDiscountRate": "25",
                        "imageUrl": "https://cf.shopee.com.br/file/sandbox-s24.jpg",
                        "productLink": "https://shopee.com.br/product/1001/2481920391",
                        "offerLink": "https://s.shopee.com.br/sandboxS24",
                        "shopId": "1001",
                        "shopName": "Samsung Loja Oficial",
                        "shopType": "1",
                        "ratingStar": "4.9",
                        "sales": 1240
                      },
                      {
                        "itemId": "9988112233",
                        "ean": "7899876543210",
                        "productName": "[SANDBOX] Fone Bluetooth JBL Tune 520BT Preto",
                        "priceMin": "199.90",
                        "priceDiscountRate": "30",
                        "imageUrl": "https://cf.shopee.com.br/file/sandbox-jbl520.jpg",
                        "productLink": "https://shopee.com.br/product/1002/9988112233",
                        "offerLink": "https://s.shopee.com.br/sandboxJBL",
                        "shopId": "1002",
                        "shopName": "JBL Oficial",
                        "shopType": "1",
                        "ratingStar": "4.8",
                        "sales": 3890
                      }
                    ]
                    """;
            JsonNode arr = objectMapper.readTree(sampleJson);
            String kw = criteria.keyword().toLowerCase(Locale.ROOT);
            List<UnifiedOffer> list = new ArrayList<>();
            for (JsonNode n : arr) {
                if (kw.isBlank() || n.path("productName").asText("").toLowerCase(Locale.ROOT).contains(kw)) {
                    list.add(normalize(n, DataSourceMode.SANDBOX));
                }
            }
            return list;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.SHOPEE, "Erro no sandbox Shopee", false, e);
        }
    }

    @Override
    protected Optional<UnifiedOffer> executeSandboxGetOffer(String externalId) {
        String clean = externalId.replace("SANDBOX-", "");
        return executeSandboxSearch(new SearchCriteria("", null, null, null, Set.of(RetailerId.SHOPEE), false, 1, 10))
                .stream()
                .filter(o -> o.externalId().endsWith(clean))
                .findFirst();
    }
}
