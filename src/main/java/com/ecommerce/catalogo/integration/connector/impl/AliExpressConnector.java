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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Conector Oficial AliExpress Open Platform (AE-Affiliate) com assinatura HMAC-SHA256
 * e cálculo automático de estimativa de tributos internacionais (Remessa Conforme).
 * Documentação: https://openservice.aliexpress.com/ / https://portals.aliexpress.com/
 */
@Component
public class AliExpressConnector extends AbstractRetailerConnector {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AliExpressConnector(
            IntegrationProperties properties,
            ConnectorResilienceExecutor resilienceExecutor,
            PriceAndCurrencyNormalizer normalizer,
            OfferDeduplicator deduplicator,
            Clock clock,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(
                RetailerId.ALIEXPRESS,
                properties.getRetailers().getAliexpress().isEnabled(),
                properties.getRetailers().getAliexpress().isSandboxMode(),
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
        IntegrationProperties.AliExpress cfg = properties.getRetailers().getAliexpress();
        return notBlank(cfg.getAppKey()) && notBlank(cfg.getAppSecret());
    }

    @Override
    protected List<UnifiedOffer> executeRealSearch(SearchCriteria criteria) {
        IntegrationProperties.AliExpress cfg = properties.getRetailers().getAliexpress();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "aliexpress.affiliate.product.query");
        params.put("app_key", cfg.getAppKey());
        params.put("sign_method", "sha256");
        params.put("timestamp", String.valueOf(clock.millis()));
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("target_currency", "BRL");
        params.put("target_language", "PT");
        params.put("ship_to_country", "BR");
        params.put("tracking_id", cfg.getTrackingId());
        params.put("page_no", String.valueOf(criteria.page()));
        params.put("page_size", String.valueOf(criteria.limit()));
        if (notBlank(criteria.keyword())) {
            params.put("keywords", criteria.keyword());
        }

        JsonNode root = postSignedTopRequest(params);
        JsonNode products = root.path("aliexpress_affiliate_product_query_response")
                .path("resp_result").path("result").path("products").path("product");
        if (!products.isArray()) {
            return List.of();
        }
        List<UnifiedOffer> offers = new ArrayList<>();
        for (JsonNode prod : products) {
            offers.add(normalize(prod, DataSourceMode.REAL));
        }
        return offers;
    }

    @Override
    protected Optional<UnifiedOffer> executeRealGetOffer(String externalId) {
        IntegrationProperties.AliExpress cfg = properties.getRetailers().getAliexpress();
        String cleanId = externalId.replace("SANDBOX-", "");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "aliexpress.affiliate.productdetail.get");
        params.put("app_key", cfg.getAppKey());
        params.put("sign_method", "sha256");
        params.put("timestamp", String.valueOf(clock.millis()));
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("target_currency", "BRL");
        params.put("target_language", "PT");
        params.put("country", "BR");
        params.put("tracking_id", cfg.getTrackingId());
        params.put("product_ids", cleanId);

        JsonNode root = postSignedTopRequest(params);
        JsonNode products = root.path("aliexpress_affiliate_productdetail_get_response")
                .path("resp_result").path("result").path("products").path("product");
        if (products.isArray() && !products.isEmpty()) {
            return Optional.of(normalize(products.get(0), DataSourceMode.REAL));
        }
        return Optional.empty();
    }

    @Override
    public UnifiedOffer normalize(JsonNode rawItem, DataSourceMode mode) {
        String rawId = rawItem.path("product_id").asText("0");
        String externalId = mode == DataSourceMode.SANDBOX && !rawId.startsWith("SANDBOX-")
                ? "SANDBOX-" + rawId
                : rawId;

        String title = rawItem.path("product_title").asText("Oferta AliExpress");
        String currency = rawItem.path("target_sale_price_currency")
                .asText(rawItem.path("sale_price_currency").asText("BRL"));
        BigDecimal salePrice = parseDecimalOrNull(
                rawItem.path("target_sale_price").asText(rawItem.path("sale_price").asText("0")));
        BigDecimal origPrice = parseDecimalOrNull(
                rawItem.path("target_original_price").asText(rawItem.path("original_price").asText("")));

        UnifiedOffer.Pricing pricing = normalizer.buildPricing(
                salePrice, origPrice, currency, null, null, true);

        UnifiedOffer.Coupon coupon = null;
        JsonNode promoCodeNode = rawItem.path("promo_code_info");
        if (promoCodeNode.isObject() && notBlank(promoCodeNode.path("promo_code").asText(""))) {
            String code = promoCodeNode.path("promo_code").asText();
            BigDecimal codeVal = parseDecimalOrNull(promoCodeNode.path("code_value").asText(null));
            coupon = new UnifiedOffer.Coupon(
                    code,
                    promoCodeNode.path("code_campaigntype").asText("Cupom AliExpress"),
                    codeVal != null ? normalizer.toBrl(codeVal, currency) : null,
                    codeVal != null ? "FIXED" : "PROMO_CODE",
                    null
            );
        }

        String urlOriginal = sanitizeExternalUrl(rawItem.path("product_detail_url")
                .asText("https://pt.aliexpress.com/item/" + rawId + ".html"));
        String promoLink = rawItem.path("promotion_link").asText("");
        String urlAffiliate = notBlank(promoLink)
                ? sanitizeExternalUrl(promoLink)
                : buildAffiliateUrl(urlOriginal, defaultSubId(null));
        String imageUrl = sanitizeExternalUrl(rawItem.path("product_main_image_url").asText(null));

        BigDecimal evalRate = parseDecimalOrNull(rawItem.path("evaluate_rate").asText(null));
        BigDecimal stars = null;
        if (evalRate != null) {
            stars = evalRate.compareTo(new BigDecimal("5")) > 0
                    ? evalRate.divide(new BigDecimal("20"), 2, java.math.RoundingMode.HALF_UP)
                    : evalRate;
        }
        int volume = rawItem.path("lastest_volume").asInt(0);

        Instant now = clock.instant();
        String ean = rawItem.hasNonNull("ean") ? rawItem.get("ean").asText() : null;
        String skuCanonical = deduplicator.computeCanonicalSku(ean, title);
        String couponCode = coupon != null ? coupon.code() : null;
        String contentHash = normalizer.computeContentHash(RetailerId.ALIEXPRESS, externalId, pricing.currentPrice(), couponCode, true);

        return new UnifiedOffer(
                UUID.randomUUID().toString(),
                RetailerId.ALIEXPRESS,
                mode == DataSourceMode.SANDBOX ? "ALIEXPRESS_SANDBOX" : "ALIEXPRESS_OPEN_PLATFORM",
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
                new UnifiedOffer.Category(rawItem.path("first_level_category_id").asText(null),
                        rawItem.path("first_level_category_name").asText("IMPORTADOS")),
                new UnifiedOffer.Rating(stars, null, volume > 0 ? volume : null),
                new UnifiedOffer.Seller(rawItem.path("shop_id").asText(null), "AliExpress Seller", false, "CHOICE"),
                new UnifiedOffer.Shipping(true, BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP), "ALIEXPRESS_CHOICE"),
                new UnifiedOffer.Stock(true, null),
                new UnifiedOffer.Timestamps(now, now.plusSeconds(6 * 3600)),
                contentHash
        );
    }

    @Override
    public String buildAffiliateUrl(String originalUrl, String campaignSubId) {
        String cleanUrl = sanitizeExternalUrl(originalUrl);
        String tracking = properties.getRetailers().getAliexpress().getTrackingId();
        String subId = notBlank(campaignSubId) ? campaignSubId : defaultSubId(null);
        String sep = cleanUrl.contains("?") ? "&" : "?";
        return cleanUrl + sep + "aff_fcid=" + URLEncoder.encode(tracking, StandardCharsets.UTF_8)
                + "&sk=" + URLEncoder.encode(subId, StandardCharsets.UTF_8);
    }

    private JsonNode postSignedTopRequest(Map<String, String> params) {
        try {
            IntegrationProperties.AliExpress cfg = properties.getRetailers().getAliexpress();
            String signature = CryptoSigningUtils.signAliExpressParameters(params, cfg.getAppSecret());

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            params.forEach(form::add);
            form.add("sign", signature);

            String responseBody = restClient.post()
                    .uri(cfg.getApiUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            if (root.has("error_response")) {
                String msg = root.path("error_response").path("msg").asText("Erro na API AliExpress");
                throw new ConnectorExecutionException(RetailerId.ALIEXPRESS, "AliExpress API erro: " + msg, false);
            }
            return root;
        } catch (ConnectorExecutionException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.ALIEXPRESS,
                    "Erro ao processar resposta AliExpress: " + e.getMessage(), false, e);
        }
    }

    @Override
    protected List<UnifiedOffer> executeSandboxSearch(SearchCriteria criteria) {
        try {
            String sampleJson = """
                    [
                      {
                        "product_id": "1005006218991",
                        "ean": "7899876543210",
                        "product_title": "[SANDBOX] Fone Bluetooth JBL Tune 520BT Global",
                        "target_sale_price": "145.00",
                        "target_original_price": "260.00",
                        "target_sale_price_currency": "BRL",
                        "product_main_image_url": "https://ae01.alicdn.com/kf/sandbox-jbl.jpg",
                        "product_detail_url": "https://pt.aliexpress.com/item/1005006218991.html",
                        "promotion_link": "https://s.click.aliexpress.com/e/_sandboxJbl",
                        "evaluate_rate": "96.0%",
                        "lastest_volume": 850,
                        "shop_id": "5501",
                        "first_level_category_id": "44",
                        "first_level_category_name": "AUDIO",
                        "promo_code_info": {
                          "promo_code": "BRJBL15",
                          "code_value": "15.00"
                        }
                      },
                      {
                        "product_id": "1005007711223",
                        "product_title": "[SANDBOX] SSD NVMe M.2 Kingston NV2 1TB PCIe 4.0",
                        "target_sale_price": "240.00",
                        "target_original_price": "399.00",
                        "target_sale_price_currency": "BRL",
                        "product_main_image_url": "https://ae01.alicdn.com/kf/sandbox-ssd.jpg",
                        "product_detail_url": "https://pt.aliexpress.com/item/1005007711223.html",
                        "promotion_link": "https://s.click.aliexpress.com/e/_sandboxSsd",
                        "evaluate_rate": "98.0%",
                        "lastest_volume": 4200,
                        "shop_id": "5502",
                        "first_level_category_id": "7",
                        "first_level_category_name": "HARDWARE"
                      }
                    ]
                    """;
            JsonNode arr = objectMapper.readTree(sampleJson);
            String kw = criteria.keyword().toLowerCase(Locale.ROOT);
            List<UnifiedOffer> list = new ArrayList<>();
            for (JsonNode n : arr) {
                if (kw.isBlank() || n.path("product_title").asText("").toLowerCase(Locale.ROOT).contains(kw)) {
                    list.add(normalize(n, DataSourceMode.SANDBOX));
                }
            }
            return list;
        } catch (Exception e) {
            throw new ConnectorExecutionException(RetailerId.ALIEXPRESS, "Erro no sandbox AliExpress", false, e);
        }
    }

    @Override
    protected Optional<UnifiedOffer> executeSandboxGetOffer(String externalId) {
        String clean = externalId.replace("SANDBOX-", "");
        return executeSandboxSearch(new SearchCriteria("", null, null, null, Set.of(RetailerId.ALIEXPRESS), false, 1, 10))
                .stream()
                .filter(o -> o.externalId().endsWith(clean))
                .findFirst();
    }
}
