package com.ecommerce.catalogo.integration;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.connector.RetailerConnector;
import com.ecommerce.catalogo.integration.connector.impl.*;
import com.ecommerce.catalogo.integration.exception.ConnectorConfigurationException;
import com.ecommerce.catalogo.integration.model.*;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import com.ecommerce.catalogo.integration.service.OfferDeduplicator;
import com.ecommerce.catalogo.integration.service.PriceAndCurrencyNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RetailerConnectorsUnitTest {

    private IntegrationProperties properties;
    private Clock fixedClock;
    private ObjectMapper objectMapper;
    private ConnectorResilienceExecutor resilience;
    private PriceAndCurrencyNormalizer normalizer;
    private OfferDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        fixedClock = Clock.fixed(Instant.parse("2026-10-29T23:00:00Z"), ZoneOffset.UTC);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        resilience = new ConnectorResilienceExecutor(properties, fixedClock, ms -> {});
        normalizer = new PriceAndCurrencyNormalizer(properties);
        deduplicator = new OfferDeduplicator(properties, fixedClock);
    }

    @Test
    void deveBloquearBuscaSemCredenciaisQuandoSandboxDesativadoNuncaGerandoDadosFalsos() {
        properties.getRetailers().getShopee().setEnabled(true);
        properties.getRetailers().getShopee().setSandboxMode(false);
        properties.getRetailers().getShopee().setAppId("");
        properties.getRetailers().getShopee().setAppSecret("");

        ShopeeConnector connector = new ShopeeConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, RestClient.builder(), objectMapper);

        assertThat(connector.checkHealth().operationalStatus()).isEqualTo("MISSING_CREDENTIALS");
        SearchCriteria criteria = new SearchCriteria("iphone", null, null, null, Set.of(), true, 1, 10);
        assertThatThrownBy(() -> connector.search(criteria))
                .isInstanceOf(ConnectorConfigurationException.class)
                .hasMessageContaining("sandboxMode=false");
    }

    @Test
    void shopeeConnectorDeveAssinarRequisicaoSha256ENormalizarRespostaRealGraphQl() {
        properties.getRetailers().getShopee().setEnabled(true);
        properties.getRetailers().getShopee().setSandboxMode(false);
        properties.getRetailers().getShopee().setAppId("150099");
        properties.getRetailers().getShopee().setAppSecret("segredo-teste-shopee");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String recordedShopeeResponse = """
                {
                  "data": {
                    "productOfferV2": {
                      "nodes": [
                        {
                          "itemId": "5544332211",
                          "productName": "Console PlayStation 5 Slim Digital 1TB Branco",
                          "price": "3399.00",
                          "priceMin": "3399.00",
                          "priceMax": "3399.00",
                          "priceDiscountRate": "20",
                          "imageUrl": "https://cf.shopee.com.br/file/ps5slim.jpg",
                          "productLink": "https://shopee.com.br/product/888/5544332211",
                          "offerLink": "https://s.shopee.com.br/afiliadoPs5",
                          "shopId": "888",
                          "shopName": "PlayStation Loja Oficial",
                          "shopType": "1",
                          "ratingStar": "4.9",
                          "sales": 2100,
                          "periodEndTime": 1800000000
                        }
                      ]
                    }
                  }
                }
                """;

        server.expect(requestTo("https://open-api.affiliate.shopee.com.br/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", startsWith("SHA256 Credential=150099, Timestamp=1793314800, Signature=")))
                .andExpect(content().string(containsString("productOfferV2")))
                .andRespond(withSuccess(recordedShopeeResponse, MediaType.APPLICATION_JSON));

        ShopeeConnector connector = new ShopeeConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, builder, objectMapper);

        List<UnifiedOffer> offers = connector.search(
                new SearchCriteria("ps5", null, null, null, Set.of(RetailerId.SHOPEE), true, 1, 10));

        server.verify();
        assertThat(offers).hasSize(1);
        UnifiedOffer offer = offers.get(0);
        assertThat(offer.retailer()).isEqualTo(RetailerId.SHOPEE);
        assertThat(offer.dataSourceMode()).isEqualTo(DataSourceMode.REAL);
        assertThat(offer.externalId()).isEqualTo("5544332211");
        assertThat(offer.urlAffiliate()).isEqualTo("https://s.shopee.com.br/afiliadoPs5");
        assertThat(offer.pricing().currentPrice()).isEqualByComparingTo("3399.00");
        assertThat(offer.pricing().originalPrice()).isEqualByComparingTo("4248.75");
        assertThat(offer.pricing().discountPercentage()).isEqualByComparingTo("20.00");
        assertThat(offer.seller().isOfficialStore()).isTrue();
    }

    @Test
    void aliExpressConnectorDeveAssinarHmacSha256ECalcularImpostoRemessaConformeECupom() {
        properties.getRetailers().getAliexpress().setEnabled(true);
        properties.getRetailers().getAliexpress().setSandboxMode(false);
        properties.getRetailers().getAliexpress().setAppKey("33001122");
        properties.getRetailers().getAliexpress().setAppSecret("segredo-ali-hmac");
        properties.getRetailers().getAliexpress().setTrackingId("canal_br");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String recordedAliResponse = """
                {
                  "aliexpress_affiliate_product_query_response": {
                    "resp_result": {
                      "result": {
                        "products": {
                          "product": [
                            {
                              "product_id": "1005009001001",
                              "product_title": "Mini Projetor Portatil Full HD WiFi 6",
                              "target_sale_price": "220.00",
                              "target_original_price": "380.00",
                              "target_sale_price_currency": "BRL",
                              "product_main_image_url": "https://ae01.alicdn.com/kf/projetor.jpg",
                              "product_detail_url": "https://pt.aliexpress.com/item/1005009001001.html",
                              "promotion_link": "https://s.click.aliexpress.com/e/_corteReal",
                              "evaluate_rate": "95.0%",
                              "lastest_volume": 920,
                              "shop_id": "7711",
                              "promo_code_info": {
                                "promo_code": "PROJ25",
                                "code_value": "25.00"
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        server.expect(requestTo("https://api-sg.aliexpress.com/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("method=aliexpress.affiliate.product.query")))
                .andExpect(content().string(containsString("sign=")))
                .andRespond(withSuccess(recordedAliResponse, MediaType.APPLICATION_JSON));

        AliExpressConnector connector = new AliExpressConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, builder, objectMapper);

        List<UnifiedOffer> offers = connector.search(
                new SearchCriteria("projetor", null, null, null, Set.of(RetailerId.ALIEXPRESS), true, 1, 10));

        server.verify();
        assertThat(offers).hasSize(1);
        UnifiedOffer offer = offers.get(0);
        assertThat(offer.dataSourceMode()).isEqualTo(DataSourceMode.REAL);
        assertThat(offer.pricing().isInternational()).isTrue();
        assertThat(offer.pricing().currentPrice()).isEqualByComparingTo("220.00");
        assertThat(offer.pricing().estimatedImportTax()).isGreaterThan(new BigDecimal("90.00"));
        assertThat(offer.coupon()).isNotNull();
        assertThat(offer.coupon().code()).isEqualTo("PROJ25");
        assertThat(offer.urlAffiliate()).isEqualTo("https://s.click.aliexpress.com/e/_corteReal");
    }

    @Test
    void mercadoLivreConnectorDeveRenovarTokenOAuth2Em401EExtrairGtinEImagemHd() {
        IntegrationProperties.MercadoLivre cfg = properties.getRetailers().getMercadolivre();
        cfg.setEnabled(true);
        cfg.setSandboxMode(false);
        cfg.setAccessToken("token-expirado");
        cfg.setClientId("ml-client");
        cfg.setClientSecret("ml-secret");
        cfg.setRefreshToken("ml-refresh-valido");
        cfg.setAffiliateTool("998877");
        cfg.setAffiliateWord("canal_telegram");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 1ª chamada com token expirado retorna 401
        server.expect(requestTo(startsWith("https://api.mercadolibre.com/sites/MLB/search")))
                .andExpect(header("Authorization", "Bearer token-expirado"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Renovação automática OAuth 2.0 via refresh_token
        server.expect(requestTo("https://api.mercadolibre.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\"token-renovado-ok\"}", MediaType.APPLICATION_JSON));

        // 2ª chamada com token renovado retorna 200
        String recordedMlbResponse = """
                {
                  "results": [
                    {
                      "id": "MLB40506070",
                      "title": "Smart TV LG OLED evo C4 55 4K",
                      "price": 5499.00,
                      "original_price": 7999.00,
                      "currency_id": "BRL",
                      "available_quantity": 9,
                      "sold_quantity": 150,
                      "permalink": "https://produto.mercadolivre.com.br/MLB-40506070-smart-tv-lg",
                      "thumbnail": "http://http2.mlstatic.com/D_12345-I.jpg",
                      "official_store_id": 190,
                      "shipping": { "free_shipping": true, "logistic_type": "fulfillment" },
                      "seller": { "id": 500, "nickname": "LG_OFICIAL" },
                      "attributes": [ { "id": "GTIN", "value_name": "7893299912345" } ]
                    }
                  ]
                }
                """;

        server.expect(requestTo(startsWith("https://api.mercadolibre.com/sites/MLB/search")))
                .andExpect(header("Authorization", "Bearer token-renovado-ok"))
                .andRespond(withSuccess(recordedMlbResponse, MediaType.APPLICATION_JSON));

        MercadoLivreConnector connector = new MercadoLivreConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, builder, objectMapper);

        List<UnifiedOffer> offers = connector.search(
                new SearchCriteria("oled", null, null, null, Set.of(RetailerId.MERCADO_LIVRE), true, 1, 10));

        server.verify();
        assertThat(offers).hasSize(1);
        UnifiedOffer offer = offers.get(0);
        assertThat(offer.eanGtin()).isEqualTo("7893299912345");
        assertThat(offer.skuCanonical()).isEqualTo("EAN-7893299912345");
        assertThat(offer.imageUrl()).isEqualTo("https://http2.mlstatic.com/D_12345-O.jpg");
        assertThat(offer.urlAffiliate()).contains("matt_tool=998877").contains("matt_word=canal_telegram");
        assertThat(offer.shipping().fulfillmentType()).isEqualTo("FULL");
    }

    @Test
    void amazonConnectorDeveAutenticarOAuth2CreatorsApiENormalizarOfertaPrime() {
        IntegrationProperties.Amazon cfg = properties.getRetailers().getAmazon();
        cfg.setEnabled(true);
        cfg.setSandboxMode(false);
        cfg.setCredentialId("amz-cred-id");
        cfg.setCredentialSecret("amz-cred-secret");
        cfg.setPartnerTag("minhaloja-20");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.amazon.com/auth/o2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"amz-bearer-123\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        String recordedAmazonResponse = """
                {
                  "SearchResult": {
                    "Items": [
                      {
                        "ASIN": "B09V3KXJPB",
                        "DetailPageURL": "https://www.amazon.com.br/dp/B09V3KXJPB?tag=minhaloja-20",
                        "ItemInfo": {
                          "Title": { "DisplayValue": "Apple AirPods Pro 2a Geracao USB-C" },
                          "ExternalIds": { "EANs": { "DisplayValues": [ "0195949052484" ] } }
                        },
                        "Images": { "Primary": { "Large": { "URL": "https://m.media-amazon.com/images/I/airpods.jpg" } } },
                        "Offers": {
                          "Listings": [
                            {
                              "Price": { "Amount": 1699.00, "Currency": "BRL" },
                              "SavingBasis": { "Amount": 2599.00 },
                              "Availability": { "Message": "Em estoque" },
                              "DeliveryInfo": { "IsFreeShippingEligible": true }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
                """;

        server.expect(requestTo("https://affiliate-program.amazon.com/creatorsapi/v1/searchItems"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer amz-bearer-123"))
                .andExpect(header("X-Amz-Marketplace", "www.amazon.com.br"))
                .andRespond(withSuccess(recordedAmazonResponse, MediaType.APPLICATION_JSON));

        AmazonConnector connector = new AmazonConnector(
                properties, resilience, normalizer, deduplicator, fixedClock, builder, objectMapper);

        List<UnifiedOffer> offers = connector.search(
                new SearchCriteria("airpods", null, null, null, Set.of(RetailerId.AMAZON), true, 1, 5));

        server.verify();
        assertThat(offers).hasSize(1);
        UnifiedOffer offer = offers.get(0);
        assertThat(offer.externalId()).isEqualTo("B09V3KXJPB");
        assertThat(offer.eanGtin()).isEqualTo("0195949052484");
        assertThat(offer.urlAffiliate()).isEqualTo("https://www.amazon.com.br/dp/B09V3KXJPB?tag=minhaloja-20");
        assertThat(offer.pricing().currentPrice()).isEqualByComparingTo("1699.00");
        assertThat(offer.pricing().originalPrice()).isEqualByComparingTo("2599.00");
    }

    @Test
    void lomadeeConnectorDeveConsultarMagaluComHeaderApiKeyENormalizarPrecoPixEDeeplink() {
        properties.getLomadee().setApiKey("lomadee-key-xyz");
        properties.getLomadee().setSourceId("3819201");
        IntegrationProperties.LomadeeStore magaluCfg = properties.getRetailers().getMagalu();
        magaluCfg.setEnabled(true);
        magaluCfg.setSandboxMode(false);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String recordedLomadeeResponse = """
                {
                  "data": [
                    {
                      "id": "MGLU-23551100",
                      "ean": "7891122334455",
                      "name": "Cafeteira Expresso Nespresso Vertuo Pop Preta",
                      "price": "449.90",
                      "priceFrom": "699.90",
                      "pixPrice": "404.91",
                      "originalUrl": "https://www.magazineluiza.com.br/p/23551100",
                      "link": "https://redir.lomadee.com/v2/deeplink?id=23551100",
                      "thumbnail": "https://a-static.mlcdn.com.br/nespresso.jpg",
                      "available": true,
                      "freeShipping": true,
                      "couponCode": "CAFE10"
                    }
                  ]
                }
                """;

        server.expect(requestTo(startsWith("https://api.lomadee.com.br/affiliate/products")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "lomadee-key-xyz"))
                .andRespond(withSuccess(recordedLomadeeResponse, MediaType.APPLICATION_JSON));

        RetailerConnector magaluConnector = new LomadeeRetailerConnector(
                RetailerId.MAGALU, magaluCfg, properties, resilience, normalizer, deduplicator,
                fixedClock, builder.build(), objectMapper);

        List<UnifiedOffer> offers = magaluConnector.search(
                new SearchCriteria("nespresso", null, null, null, Set.of(RetailerId.MAGALU), true, 1, 10));

        server.verify();
        assertThat(offers).hasSize(1);
        UnifiedOffer offer = offers.get(0);
        assertThat(offer.retailer()).isEqualTo(RetailerId.MAGALU);
        assertThat(offer.pricing().pixOrCashPrice()).isEqualByComparingTo("404.91");
        assertThat(offer.coupon().code()).isEqualTo("CAFE10");
        assertThat(offer.urlAffiliate()).isEqualTo("https://redir.lomadee.com/v2/deeplink?id=23551100");
    }
}

