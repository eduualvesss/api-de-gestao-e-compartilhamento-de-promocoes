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
import org.springframework.web.client.HttpClientErrorException;
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
 * Conector Oficial Mercado Livre Brasil (MLB) — OAuth 2.0 Bearer + Auto-Refresh Token,
 * busca /sites/MLB/search, /items e enriquecimento de link de campanha de afiliado.
 * Documentação: https://developers.mercadolivre.com.br/
 */
@Component
public class MercadoLivreConnector extends AbstractRetailerConnector {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private volatile String activeAccessToken;

    public MercadoLivreConnector(
            IntegrationProperties properties,
            ConnectorResilienceExecutor resilienceExecutor,
            PriceAndCurrencyNormalizer normalizer,
            OfferDeduplicator deduplicator,
            Clock clock,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(
                RetailerId.MERCADO_LIVRE,
                properties.getRetailers().getMercadolivre().isEnabled(),
                properties.getRetailers().getMercadolivre().isSandboxMode(),
                properties,
                resilienceExecutor,
                normalizer,
                deduplicator,
                clock
        );
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.activeAccessToken = properties.getRetailers().getMercadolivre().getAccessToken();
    }

    @Override
    public boolean hasRealCredentials() {
        IntegrationProperties.MercadoLivre cfg = properties.getRetailers().getMercadolivre();
        boolean hasDirectToken = notBlank(cfg.getAccessToken()) || notBlank(activeAccessToken);
        boolean canRefresh = notBlank(cfg.getClientId()) && notBlank(cfg.getClientSecret()) && notBlank(cfg.getRefreshToken());
        return hasDirectToken || canRefresh;
    }

    @Override
    protected List<UnifiedOffer> executeRealSearch(SearchCriteria criteria) {
        IntegrationProperties.MercadoLivre cfg = properties.getRetailers().getMercadolivre();
        int offset = Math.min(950, (criteria.page() - 1) * criteria.limit());
        URI searchUri = UriComponentsBuilder.fromHttpUrl(cfg.getApiUrl() + "/sites/MLB/search")
                .queryParam("q", criteria.keyword().isBlank() ? "ofertas" : criteria.keyword())
                .queryParam("limit", criteria.limit())
                .queryParam("offset", offset)
                .build()
                .encode()
                .toUri();

        JsonNode root = getAuthenticatedJson(searchUri);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return List.of();
        }
        List<UnifiedOffer> offers = new ArrayList<>();
        for (JsonNode item : results) {
            offers.add(normalize(item, DataSourceMode.REAL));
        }
        return offers;
    }

    @Override
    protected Optional<UnifiedOffer> executeRealGetOffer(String externalId) {
        IntegrationProperties.MercadoLivre cfg = properties.getRetailers().getMercadolivre();
        String cleanId = externalId.replace("SANDBOX-", "");
        URI itemUri = UriComponentsBuilder.fromHttpUrl(cfg.getApiUrl() + "/items")
                .queryParam("ids", cleanId)
                .build()
                .encode()
                .toUri();

        JsonNode arr = getAuthenticatedJson(itemUri);
        if (arr.isArray() && !arr.isEmpty()) {
            JsonNode wrapper = arr.get(0);
            if (wrapper.path("code").asInt(200) == 200 && wrapper.has("body")) {
                return Optional.of(normalize(wrapper.get("body"), DataSourceMode.REAL));
            }
        }
        return Optional.empty();
    }

    @Override
    public UnifiedOffer normalize(JsonNode rawItem, DataSourceMode mode) {
        String rawId = rawItem.path("id").asText("MLB0");
        String externalId = mode == DataSourceMode.SANDBOX && !rawId.startsWith("SANDBOX-")
                ? "SANDBOX-" + rawId
                : rawId;

        String title = rawItem.path("title").asText("Oferta Mercado Livre");
        String currency = rawItem.path("currency_id").asText("BRL");
        BigDecimal price = parseDecimalOrNull(rawItem.path("price").asText("0"));
        BigDecimal originalPrice = rawItem.hasNonNull("original_price")
                ? parseDecimalOrNull(rawItem.path("original_price").asText(null))
                : null;

        UnifiedOffer.Pricing pricing = normalizer.buildPricing(
                price, originalPrice, currency, null, null, false);

        String ean = extractGtinFromAttributes(rawItem.path("attributes"));
        String permalink = sanitizeExternalUrl(rawItem.path("permalink")
                .asText("https://produto.mercadolivre.com.br/" + rawId));
        String urlAffiliate = buildAffiliateUrl(permalink, defaultSubId(rawItem.path("category_id").asText(null)));

        String thumb = rawItem.path("thumbnail").asText(null);
        if (thumb != null) {
            thumb = thumb.replace("http://", "https://").replace("-I.jpg", "-O.jpg");
        }
        String imageUrl = sanitizeExternalUrl(thumb);

        boolean freeShipping = rawItem.path("shipping").path("free_shipping").asBoolean(false);
        String logisticType = rawItem.path("shipping").path("logistic_type").asText("STANDARD");
        boolean isOfficial = rawItem.hasNonNull("official_store_id");
        int availableQty = rawItem.path("available_quantity").asInt(1);
        boolean inStock = availableQty > 0 && !"paused".equalsIgnoreCase(rawItem.path("status").asText("active"));

        Instant now = clock.instant();
        String skuCanonical = deduplicator.computeCanonicalSku(ean, title);
        String contentHash = normalizer.computeContentHash(RetailerId.MERCADO_LIVRE, externalId, pricing.currentPrice(), null, inStock);

        return new UnifiedOffer(
                UUID.randomUUID().toString(),
                RetailerId.MERCADO_LIVRE,
                mode == DataSourceMode.SANDBOX ? "MERCADO_LIVRE_SANDBOX" : "MERCADO_LIVRE_API",
                mode,
                externalId,
                ean,
                skuCanonical,
                title,
                permalink,
                urlAffiliate,
                imageUrl,
                pricing,
                null,
                new UnifiedOffer.Category(rawItem.path("category_id").asText(null), "MLB_CATALOGO"),
                new UnifiedOffer.Rating(null, null, rawItem.has("sold_quantity") ? rawItem.path("sold_quantity").asInt() : null),
                new UnifiedOffer.Seller(
                        rawItem.path("seller").path("id").asText(rawItem.path("seller_id").asText(null)),
                        rawItem.path("seller").path("nickname").asText("Vendedor Mercado Livre"),
                        isOfficial,
                        isOfficial ? "OFFICIAL_STORE" : "MERCADOLIDER"
                ),
                new UnifiedOffer.Shipping(
                        freeShipping,
                        freeShipping ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : null,
                        "fulfillment".equalsIgnoreCase(logisticType) ? "FULL" : "STANDARD"
                ),
                new UnifiedOffer.Stock(inStock, availableQty),
                new UnifiedOffer.Timestamps(now, now.plusSeconds(4 * 3600)),
                contentHash
        );
    }

    @Override
    public String buildAffiliateUrl(String originalUrl, String campaignSubId) {
        String clean = sanitizeExternalUrl(originalUrl);
        IntegrationProperties.MercadoLivre cfg = properties.getRetailers().getMercadolivre();
        String tool = notBlank(cfg.getAffiliateTool()) ? cfg.getAffiliateTool() : "afiliado_pendente";
        String word = notBlank(cfg.getAffiliateWord()) ? cfg.getAffiliateWord() : (notBlank(campaignSubId) ? campaignSubId : defaultSubId(null));
        String sep = clean.contains("?") ? "&" : "?";
        return clean + sep + "matt_tool=" + URLEncoder.encode(tool, StandardCharsets.UTF_8)
                + "&matt_word=" + URLEncoder.encode(word, StandardCharsets.UTF_8);
    }

    private JsonNode getAuthenticatedJson(URI uri) {
        String token = ensureAccessToken(false);
        try {
            return executeGetWithToken(uri, token);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            String refreshed = ensureAccessToken(true);
            return executeGetWithToken(uri, refreshed);
        }
    }

    private JsonNode executeGetWithToken(URI uri, String token) {
        try {
            String body = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (org.springframework.web.client.RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.MERCADO_LIVRE,
                    "Erro ao interpretar JSON do Mercado Livre: " + e.getMessage(), false, e);
        }
    }

    private synchronized String ensureAccessToken(boolean forceRefresh) {
        IntegrationProperties.MercadoLivre cfg = properties.getRetailers().getMercadolivre();
        if (!forceRefresh && notBlank(activeAccessToken)) {
            return activeAccessToken;
        }
        if (!forceRefresh && notBlank(cfg.getAccessToken())) {
            activeAccessToken = cfg.getAccessToken();
            return activeAccessToken;
        }
        if (notBlank(cfg.getClientId()) && notBlank(cfg.getClientSecret()) && notBlank(cfg.getRefreshToken())) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("client_id", cfg.getClientId());
            form.add("client_secret", cfg.getClientSecret());
            form.add("refresh_token", cfg.getRefreshToken());
            try {
                String resp = restClient.post()
                        .uri(cfg.getApiUrl() + "/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class);
                JsonNode node = objectMapper.readTree(resp == null ? "{}" : resp);
                String newToken = node.path("access_token").asText("");
                if (notBlank(newToken)) {
                    activeAccessToken = newToken;
                    return activeAccessToken;
                }
            } catch (Exception e) {
                throw new ConnectorExecutionException(RetailerId.MERCADO_LIVRE,
                        "Falha ao renovar OAuth token do Mercado Livre: " + e.getMessage(), false, e);
            }
        }
        throw new ConnectorExecutionException(RetailerId.MERCADO_LIVRE,
                "Access token OAuth 2.0 indisponível para Mercado Livre", false);
    }

    private String extractGtinFromAttributes(JsonNode attributes) {
        if (!attributes.isArray()) {
            return null;
        }
        for (JsonNode attr : attributes) {
            String id = attr.path("id").asText("");
            if ("GTIN".equalsIgnoreCase(id) || "EAN".equalsIgnoreCase(id)) {
                String val = attr.path("value_name").asText("");
                if (notBlank(val)) {
                    return val;
                }
            }
        }
        return null;
    }

    @Override
    protected List<UnifiedOffer> executeSandboxSearch(SearchCriteria criteria) {
        try {
            String sampleJson = """
                    [
                      {
                        "id": "MLB3918273645",
                        "title": "[SANDBOX] Smartphone Samsung Galaxy S24 256GB 5G Preto",
                        "price": 3399.00,
                        "original_price": 4499.00,
                        "currency_id": "BRL",
                        "available_quantity": 18,
                        "sold_quantity": 410,
                        "permalink": "https://produto.mercadolivre.com.br/MLB-3918273645-galaxy-s24",
                        "thumbnail": "https://http2.mlstatic.com/D_NQ_NP_391827-MLB-I.jpg",
                        "category_id": "MLB1055",
                        "official_store_id": 270,
                        "shipping": { "free_shipping": true, "logistic_type": "fulfillment" },
                        "seller": { "id": 99001, "nickname": "SAMSUNG_OFICIAL_MLB" },
                        "attributes": [ { "id": "GTIN", "value_name": "7891234567890" } ]
                      }
                    ]
                    """;
            JsonNode arr = objectMapper.readTree(sampleJson);
            String kw = criteria.keyword().toLowerCase(Locale.ROOT);
            List<UnifiedOffer> list = new ArrayList<>();
            for (JsonNode n : arr) {
                if (kw.isBlank() || n.path("title").asText("").toLowerCase(Locale.ROOT).contains(kw)) {
                    list.add(normalize(n, DataSourceMode.SANDBOX));
                }
            }
            return list;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.MERCADO_LIVRE, "Erro no sandbox Mercado Livre", false, e);
        }
    }

    @Override
    protected Optional<UnifiedOffer> executeSandboxGetOffer(String externalId) {
        String clean = externalId.replace("SANDBOX-", "");
        return executeSandboxSearch(new SearchCriteria("", null, null, null, Set.of(RetailerId.MERCADO_LIVRE), false, 1, 10))
                .stream()
                .filter(o -> o.externalId().endsWith(clean))
                .findFirst();
    }
}
