package com.ecommerce.catalogo.integration.connector.impl;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.connector.AbstractRetailerConnector;
import com.ecommerce.catalogo.integration.exception.ConnectorExecutionException;
import com.ecommerce.catalogo.integration.model.*;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import com.ecommerce.catalogo.integration.service.OfferDeduplicator;
import com.ecommerce.catalogo.integration.service.PriceAndCurrencyNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Conector Oficial via Rede de Afiliados Lomadee API v3 (https://docs.lomadee.com.br/)
 * Parametrizável por varejista nacional: Magazine Luiza, KaBuM!, Casas Bahia, Americanas e Netshoes.
 */
public class LomadeeRetailerConnector extends AbstractRetailerConnector {

    private final IntegrationProperties.LomadeeStore storeConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LomadeeRetailerConnector(
            RetailerId retailerId,
            IntegrationProperties.LomadeeStore storeConfig,
            IntegrationProperties properties,
            ConnectorResilienceExecutor resilienceExecutor,
            PriceAndCurrencyNormalizer normalizer,
            OfferDeduplicator deduplicator,
            Clock clock,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        super(
                retailerId,
                storeConfig.isEnabled(),
                storeConfig.isSandboxMode(),
                properties,
                resilienceExecutor,
                normalizer,
                deduplicator,
                clock
        );
        this.storeConfig = storeConfig;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasRealCredentials() {
        IntegrationProperties.Lomadee lomadee = properties.getLomadee();
        return notBlank(lomadee.getApiKey()) && notBlank(lomadee.getSourceId());
    }

    @Override
    protected List<UnifiedOffer> executeRealSearch(SearchCriteria criteria) {
        IntegrationProperties.Lomadee lomadee = properties.getLomadee();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(lomadee.getApiUrl() + "/affiliate/products")
                .queryParam("brandId", storeConfig.getBrandId())
                .queryParam("channelId", lomadee.getSourceId())
                .queryParam("page", criteria.page())
                .queryParam("limit", criteria.limit());
        if (notBlank(criteria.keyword())) {
            builder.queryParam("keyword", criteria.keyword());
        }

        JsonNode root = executeLomadeeGet(builder.build().encode().toUri());
        JsonNode data = root.has("data") ? root.get("data") : root.path("products");
        if (!data.isArray()) {
            return List.of();
        }
        List<UnifiedOffer> offers = new ArrayList<>();
        for (JsonNode node : data) {
            offers.add(normalize(node, DataSourceMode.REAL));
        }
        return offers;
    }

    @Override
    protected Optional<UnifiedOffer> executeRealGetOffer(String externalId) {
        IntegrationProperties.Lomadee lomadee = properties.getLomadee();
        String cleanId = externalId.replace("SANDBOX-", "");
        URI uri = UriComponentsBuilder.fromHttpUrl(lomadee.getApiUrl() + "/affiliate/products/" + cleanId)
                .queryParam("channelId", lomadee.getSourceId())
                .build()
                .encode()
                .toUri();

        JsonNode root = executeLomadeeGet(uri);
        JsonNode item = root.has("data") && root.get("data").isObject() ? root.get("data") : root;
        if (item.has("id") || item.has("sku")) {
            return Optional.of(normalize(item, DataSourceMode.REAL));
        }
        return Optional.empty();
    }

    @Override
    public UnifiedOffer normalize(JsonNode rawItem, DataSourceMode mode) {
        String rawId = rawItem.path("id").asText(rawItem.path("sku").asText("0"));
        String externalId = mode == DataSourceMode.SANDBOX && !rawId.startsWith("SANDBOX-")
                ? "SANDBOX-" + rawId
                : rawId;

        String title = rawItem.path("name").asText(rawItem.path("title").asText("Oferta " + retailerId.getDisplayName()));
        BigDecimal price = parseDecimalOrNull(rawItem.path("price").asText(rawItem.path("salePrice").asText("0")));
        BigDecimal origPrice = parseDecimalOrNull(rawItem.path("priceFrom").asText(rawItem.path("originalPrice").asText(null)));
        BigDecimal pixPrice = parseDecimalOrNull(rawItem.path("pixPrice").asText(null));

        UnifiedOffer.Pricing pricing = normalizer.buildPricing(
                price, origPrice, "BRL", rawItem.path("installments").asText(null), pixPrice, false);

        String ean = rawItem.hasNonNull("ean") ? rawItem.get("ean").asText() : null;
        String urlOriginal = sanitizeExternalUrl(rawItem.path("originalUrl")
                .asText(rawItem.path("url").asText("https://www.lomadee.com.br/oferta/" + rawId)));
        String link = rawItem.path("link").asText(rawItem.path("affiliateUrl").asText(""));
        String urlAffiliate = notBlank(link)
                ? sanitizeExternalUrl(link)
                : buildAffiliateUrl(urlOriginal, defaultSubId(null));
        String imageUrl = sanitizeExternalUrl(rawItem.path("thumbnail").asText(rawItem.path("imageUrl").asText(null)));

        boolean inStock = rawItem.path("available").asBoolean(true);
        boolean freeShipping = rawItem.path("freeShipping").asBoolean(false);

        UnifiedOffer.Coupon coupon = null;
        if (rawItem.hasNonNull("couponCode") && notBlank(rawItem.path("couponCode").asText(""))) {
            coupon = new UnifiedOffer.Coupon(
                    rawItem.path("couponCode").asText(),
                    rawItem.path("couponDescription").asText("Cupom " + retailerId.getDisplayName()),
                    parseDecimalOrNull(rawItem.path("couponDiscount").asText(null)),
                    "PROMO_CODE",
                    null
            );
        }

        Instant now = clock.instant();
        String skuCanonical = deduplicator.computeCanonicalSku(ean, title);
        String couponCode = coupon != null ? coupon.code() : null;
        String contentHash = normalizer.computeContentHash(retailerId, externalId, pricing.currentPrice(), couponCode, inStock);

        return new UnifiedOffer(
                UUID.randomUUID().toString(),
                retailerId,
                mode == DataSourceMode.SANDBOX ? retailerId.name() + "_SANDBOX" : "LOMADEE_API_V3",
                mode,
                externalId,
                ean,
                skuCanonical,
                title,
                urlOriginal,
                urlAffiliate,
                imageUrl,
                pricing,
                coupon,
                new UnifiedOffer.Category(rawItem.path("categoryId").asText(null),
                        rawItem.path("categoryName").asText("VAREJO_NACIONAL")),
                null,
                new UnifiedOffer.Seller(storeConfig.getBrandId(), retailerId.getDisplayName(), true, "VERIFIED_PARTNER"),
                new UnifiedOffer.Shipping(
                        freeShipping,
                        freeShipping ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : null,
                        "RETAILER_DELIVERY"
                ),
                new UnifiedOffer.Stock(inStock, null),
                new UnifiedOffer.Timestamps(now, now.plusSeconds(6 * 3600)),
                contentHash
        );
    }

    @Override
    public String buildAffiliateUrl(String originalUrl, String campaignSubId) {
        String clean = sanitizeExternalUrl(originalUrl);
        String sourceId = properties.getLomadee().getSourceId();
        String effectiveSource = notBlank(sourceId) ? sourceId : "lomadee_pendente";
        String subId = notBlank(campaignSubId) ? campaignSubId : defaultSubId(null);
        return "https://redir.lomadee.com/v2/deeplink?url="
                + URLEncoder.encode(clean, StandardCharsets.UTF_8)
                + "&sourceId=" + URLEncoder.encode(effectiveSource, StandardCharsets.UTF_8)
                + "&mdasc=" + URLEncoder.encode(subId, StandardCharsets.UTF_8);
    }

    private JsonNode executeLomadeeGet(URI uri) {
        try {
            String resp = restClient.get()
                    .uri(uri)
                    .header("x-api-key", properties.getLomadee().getApiKey())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(resp == null ? "{}" : resp);
        } catch (org.springframework.web.client.RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorExecutionException(retailerId,
                    "Erro ao processar resposta Lomadee (" + retailerId + "): " + e.getMessage(), false, e);
        }
    }

    @Override
    protected List<UnifiedOffer> executeSandboxSearch(SearchCriteria criteria) {
        try {
            String sampleJson = """
                    [
                      {
                        "id": "%s-778899",
                        "ean": "7891234567890",
                        "name": "[SANDBOX] Smartphone Samsung Galaxy S24 256GB 5G Preto - %s",
                        "price": "3349.00",
                        "priceFrom": "4499.00",
                        "pixPrice": "3199.00",
                        "originalUrl": "https://www.exemplo-varejista.com.br/p/%s-778899",
                        "link": "https://redir.lomadee.com/v2/sandbox/%s-778899",
                        "thumbnail": "https://img.lomadee.com/sandbox-s24.jpg",
                        "available": true,
                        "freeShipping": true
                      }
                    ]
                    """.formatted(
                    retailerId.name().toLowerCase(Locale.ROOT),
                    retailerId.getDisplayName(),
                    storeConfig.getBrandId(),
                    storeConfig.getBrandId()
            );
            JsonNode arr = objectMapper.readTree(sampleJson);
            String kw = criteria.keyword().toLowerCase(Locale.ROOT);
            List<UnifiedOffer> list = new ArrayList<>();
            for (JsonNode n : arr) {
                if (kw.isBlank() || n.path("name").asText("").toLowerCase(Locale.ROOT).contains(kw)) {
                    list.add(normalize(n, DataSourceMode.SANDBOX));
                }
            }
            return list;
        } catch (Exception e) {
            throw new ConnectorExecutionException(retailerId, "Erro no sandbox " + retailerId, false, e);
        }
    }

    @Override
    protected Optional<UnifiedOffer> executeSandboxGetOffer(String externalId) {
        String clean = externalId.replace("SANDBOX-", "");
        return executeSandboxSearch(new SearchCriteria("", null, null, null, Set.of(retailerId), false, 1, 10))
                .stream()
                .filter(o -> o.externalId().endsWith(clean))
                .findFirst();
    }
}
