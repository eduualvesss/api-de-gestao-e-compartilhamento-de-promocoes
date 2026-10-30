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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Conector Oficial Amazon Brasil — Amazon Creators API (OAuth 2.0 Bearer Token,
 * substituta oficial da PA-API 5.0) com Rate Limiter estrito de 1 TPS padrão.
 * Documentação: https://affiliate-program.amazon.com/creatorsapi/docs/
 */
@Component
public class AmazonConnector extends AbstractRetailerConnector {

    private static final List<String> RESOURCES = List.of(
            "ItemInfo.Title",
            "ItemInfo.ExternalIds",
            "Images.Primary.Large",
            "Offers.Listings.Price",
            "Offers.Listings.SavingBasis",
            "Offers.Listings.Availability.Message",
            "Offers.Listings.DeliveryInfo.IsFreeShippingEligible"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public AmazonConnector(
            IntegrationProperties properties,
            ConnectorResilienceExecutor resilienceExecutor,
            PriceAndCurrencyNormalizer normalizer,
            OfferDeduplicator deduplicator,
            Clock clock,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(
                RetailerId.AMAZON,
                properties.getRetailers().getAmazon().isEnabled(),
                properties.getRetailers().getAmazon().isSandboxMode(),
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
    protected double getMaxTps() {
        return Math.max(0.5, properties.getRetailers().getAmazon().getMaxTps());
    }

    @Override
    public boolean hasRealCredentials() {
        IntegrationProperties.Amazon cfg = properties.getRetailers().getAmazon();
        return notBlank(cfg.getCredentialId())
                && notBlank(cfg.getCredentialSecret())
                && notBlank(cfg.getPartnerTag());
    }

    @Override
    protected List<UnifiedOffer> executeRealSearch(SearchCriteria criteria) {
        IntegrationProperties.Amazon cfg = properties.getRetailers().getAmazon();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Keywords", criteria.keyword().isBlank() ? "ofertas" : criteria.keyword());
        payload.put("PartnerTag", cfg.getPartnerTag());
        payload.put("PartnerType", "Associates");
        payload.put("Marketplace", cfg.getMarketplace());
        payload.put("ItemPage", Math.min(10, criteria.page()));
        payload.put("ItemCount", Math.min(10, criteria.limit()));
        payload.put("Resources", RESOURCES);

        JsonNode root = postCreatorsApi("/searchItems", payload);
        JsonNode items = root.path("SearchResult").path("Items");
        if (!items.isArray()) {
            return List.of();
        }
        List<UnifiedOffer> offers = new ArrayList<>();
        for (JsonNode item : items) {
            offers.add(normalize(item, DataSourceMode.REAL));
        }
        return offers;
    }

    @Override
    protected Optional<UnifiedOffer> executeRealGetOffer(String externalId) {
        IntegrationProperties.Amazon cfg = properties.getRetailers().getAmazon();
        String cleanAsin = externalId.replace("SANDBOX-", "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ItemIds", List.of(cleanAsin));
        payload.put("PartnerTag", cfg.getPartnerTag());
        payload.put("PartnerType", "Associates");
        payload.put("Marketplace", cfg.getMarketplace());
        payload.put("Resources", RESOURCES);

        JsonNode root = postCreatorsApi("/getItems", payload);
        JsonNode items = root.path("ItemsResult").path("Items");
        if (items.isArray() && !items.isEmpty()) {
            return Optional.of(normalize(items.get(0), DataSourceMode.REAL));
        }
        return Optional.empty();
    }

    @Override
    public UnifiedOffer normalize(JsonNode rawItem, DataSourceMode mode) {
        String asin = rawItem.path("ASIN").asText("B000000000");
        String externalId = mode == DataSourceMode.SANDBOX && !asin.startsWith("SANDBOX-")
                ? "SANDBOX-" + asin
                : asin;

        String title = rawItem.path("ItemInfo").path("Title").path("DisplayValue").asText("Oferta Amazon");
        String ean = null;
        JsonNode eans = rawItem.path("ItemInfo").path("ExternalIds").path("EANs").path("DisplayValues");
        if (eans.isArray() && !eans.isEmpty()) {
            ean = eans.get(0).asText(null);
        }

        JsonNode listing = rawItem.path("Offers").path("Listings").isArray()
                && !rawItem.path("Offers").path("Listings").isEmpty()
                ? rawItem.path("Offers").path("Listings").get(0)
                : objectMapper.createObjectNode();

        String currency = listing.path("Price").path("Currency").asText("BRL");
        BigDecimal currentPrice = parseDecimalOrNull(listing.path("Price").path("Amount").asText("0"));
        BigDecimal savingBasis = listing.has("SavingBasis")
                ? parseDecimalOrNull(listing.path("SavingBasis").path("Amount").asText(null))
                : null;

        UnifiedOffer.Pricing pricing = normalizer.buildPricing(
                currentPrice, savingBasis, currency, null, null, false);

        String detailUrl = sanitizeExternalUrl(rawItem.path("DetailPageURL")
                .asText("https://www.amazon.com.br/dp/" + asin));
        String urlAffiliate = detailUrl.contains("tag=")
                ? detailUrl
                : buildAffiliateUrl(detailUrl, defaultSubId(null));
        String imageUrl = sanitizeExternalUrl(
                rawItem.path("Images").path("Primary").path("Large").path("URL").asText(null));

        boolean freeShipping = listing.path("DeliveryInfo").path("IsFreeShippingEligible").asBoolean(false);
        String availabilityMsg = listing.path("Availability").path("Message").asText("Em estoque");
        boolean inStock = !availabilityMsg.toLowerCase(Locale.ROOT).contains("indispon");

        Instant now = clock.instant();
        String skuCanonical = deduplicator.computeCanonicalSku(ean, title);
        String contentHash = normalizer.computeContentHash(RetailerId.AMAZON, externalId, pricing.currentPrice(), null, inStock);

        return new UnifiedOffer(
                UUID.randomUUID().toString(),
                RetailerId.AMAZON,
                mode == DataSourceMode.SANDBOX ? "AMAZON_SANDBOX" : "AMAZON_CREATORS_API",
                mode,
                externalId,
                ean,
                skuCanonical,
                title,
                "https://www.amazon.com.br/dp/" + asin,
                urlAffiliate,
                imageUrl,
                pricing,
                null,
                new UnifiedOffer.Category(null, "AMAZON_BR"),
                null,
                new UnifiedOffer.Seller("AMAZON_BR", "Amazon.com.br", true, "PRIME"),
                new UnifiedOffer.Shipping(
                        freeShipping,
                        freeShipping ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : null,
                        "FBA_PRIME"
                ),
                new UnifiedOffer.Stock(inStock, null),
                new UnifiedOffer.Timestamps(now, now.plusSeconds(4 * 3600)),
                contentHash
        );
    }

    @Override
    public String buildAffiliateUrl(String originalUrl, String campaignSubId) {
        String clean = sanitizeExternalUrl(originalUrl);
        String tag = properties.getRetailers().getAmazon().getPartnerTag();
        String effectiveTag = notBlank(tag) ? tag : "associado-pendente-20";
        String sep = clean.contains("?") ? "&" : "?";
        return clean + sep + "tag=" + URLEncoder.encode(effectiveTag, StandardCharsets.UTF_8);
    }

    private JsonNode postCreatorsApi(String path, Map<String, Object> payload) {
        try {
            IntegrationProperties.Amazon cfg = properties.getRetailers().getAmazon();
            String token = obtainOAuthBearerToken();
            String jsonBody = objectMapper.writeValueAsString(payload);

            String response = restClient.post()
                    .uri(cfg.getApiUrl() + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Amz-Marketplace", cfg.getMarketplace())
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            if (root.has("Errors") && root.get("Errors").isArray() && !root.get("Errors").isEmpty()) {
                String msg = root.get("Errors").get(0).path("Message").asText("Erro Creators API Amazon");
                throw new ConnectorExecutionException(RetailerId.AMAZON, "Amazon API erro: " + msg, false);
            }
            return root;
        } catch (ConnectorExecutionException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.AMAZON,
                    "Erro ao processar resposta Amazon Creators API: " + e.getMessage(), false, e);
        }
    }

    private synchronized String obtainOAuthBearerToken() {
        Instant now = clock.instant();
        if (notBlank(cachedAccessToken) && now.isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedAccessToken;
        }
        IntegrationProperties.Amazon cfg = properties.getRetailers().getAmazon();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", cfg.getCredentialId());
        form.add("client_secret", cfg.getCredentialSecret());
        form.add("scope", "creatorsapi/default");

        try {
            String resp = restClient.post()
                    .uri(cfg.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(resp == null ? "{}" : resp);
            String token = node.path("access_token").asText("");
            long expiresIn = node.path("expires_in").asLong(3600L);
            if (!notBlank(token)) {
                throw new ConnectorExecutionException(RetailerId.AMAZON, "Token OAuth2 vazio retornado pela Amazon", false);
            }
            cachedAccessToken = token;
            tokenExpiresAt = now.plusSeconds(expiresIn);
            return cachedAccessToken;
        } catch (org.springframework.web.client.RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.AMAZON,
                    "Falha na autenticação OAuth2 Amazon Creators API: " + e.getMessage(), false, e);
        }
    }

    @Override
    protected List<UnifiedOffer> executeSandboxSearch(SearchCriteria criteria) {
        try {
            String sampleJson = """
                    [
                      {
                        "ASIN": "B0CS5XYZ99",
                        "DetailPageURL": "https://www.amazon.com.br/dp/B0CS5XYZ99?tag=sandbox-20",
                        "ItemInfo": {
                          "Title": { "DisplayValue": "[SANDBOX] Smartphone Samsung Galaxy S24 256GB 5G Preto" },
                          "ExternalIds": { "EANs": { "DisplayValues": [ "7891234567890" ] } }
                        },
                        "Images": { "Primary": { "Large": { "URL": "https://m.media-amazon.com/images/I/sandbox-s24.jpg" } } },
                        "Offers": {
                          "Listings": [
                            {
                              "Price": { "Amount": 3299.00, "Currency": "BRL" },
                              "SavingBasis": { "Amount": 4499.00 },
                              "Availability": { "Message": "Em estoque" },
                              "DeliveryInfo": { "IsFreeShippingEligible": true }
                            }
                          ]
                        }
                      }
                    ]
                    """;
            JsonNode arr = objectMapper.readTree(sampleJson);
            String kw = criteria.keyword().toLowerCase(Locale.ROOT);
            List<UnifiedOffer> list = new ArrayList<>();
            for (JsonNode n : arr) {
                String title = n.path("ItemInfo").path("Title").path("DisplayValue").asText("").toLowerCase(Locale.ROOT);
                if (kw.isBlank() || title.contains(kw)) {
                    list.add(normalize(n, DataSourceMode.SANDBOX));
                }
            }
            return list;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.AMAZON, "Erro no sandbox Amazon", false, e);
        }
    }

    @Override
    protected Optional<UnifiedOffer> executeSandboxGetOffer(String externalId) {
        String clean = externalId.replace("SANDBOX-", "");
        return executeSandboxSearch(new SearchCriteria("", null, null, null, Set.of(RetailerId.AMAZON), false, 1, 10))
                .stream()
                .filter(o -> o.externalId().endsWith(clean))
                .findFirst();
    }
}
