package com.ecommerce.catalogo.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Propriedades tipadas para integração com varejistas, redes de afiliados,
 * resiliência, deduplicação e API de Filtros.
 */
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private FilterApi filterApi = new FilterApi();
    private Resilience resilience = new Resilience();
    private Deduplication deduplication = new Deduplication();
    private Currency currency = new Currency();
    private Affiliate affiliate = new Affiliate();
    private Lomadee lomadee = new Lomadee();
    private Awin awin = new Awin();
    private Retailers retailers = new Retailers();

    public FilterApi getFilterApi() { return filterApi; }
    public void setFilterApi(FilterApi filterApi) { this.filterApi = filterApi; }
    public Resilience getResilience() { return resilience; }
    public void setResilience(Resilience resilience) { this.resilience = resilience; }
    public Deduplication getDeduplication() { return deduplication; }
    public void setDeduplication(Deduplication deduplication) { this.deduplication = deduplication; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public Affiliate getAffiliate() { return affiliate; }
    public void setAffiliate(Affiliate affiliate) { this.affiliate = affiliate; }
    public Lomadee getLomadee() { return lomadee; }
    public void setLomadee(Lomadee lomadee) { this.lomadee = lomadee; }
    public Awin getAwin() { return awin; }
    public void setAwin(Awin awin) { this.awin = awin; }
    public Retailers getRetailers() { return retailers; }
    public void setRetailers(Retailers retailers) { this.retailers = retailers; }

    public static class FilterApi {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8081";
        private String apiKey = "";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 8000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    public static class Resilience {
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 8000;
        private int retryMaxAttempts = 3;
        private long retryInitialDelayMs = 300;
        private int circuitFailureThreshold = 5;
        private long circuitOpenDurationSeconds = 60;

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public int getRetryMaxAttempts() { return retryMaxAttempts; }
        public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }
        public long getRetryInitialDelayMs() { return retryInitialDelayMs; }
        public void setRetryInitialDelayMs(long retryInitialDelayMs) { this.retryInitialDelayMs = retryInitialDelayMs; }
        public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
        public void setCircuitFailureThreshold(int circuitFailureThreshold) { this.circuitFailureThreshold = circuitFailureThreshold; }
        public long getCircuitOpenDurationSeconds() { return circuitOpenDurationSeconds; }
        public void setCircuitOpenDurationSeconds(long circuitOpenDurationSeconds) { this.circuitOpenDurationSeconds = circuitOpenDurationSeconds; }
    }

    public static class Deduplication {
        private long ttlMinutes = 360;

        public long getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    public static class Currency {
        private BigDecimal usdToBrlRate = new BigDecimal("5.75");

        public BigDecimal getUsdToBrlRate() { return usdToBrlRate; }
        public void setUsdToBrlRate(BigDecimal usdToBrlRate) { this.usdToBrlRate = usdToBrlRate; }
    }

    public static class Affiliate {
        private String campaignPrefix = "agy";

        public String getCampaignPrefix() { return campaignPrefix; }
        public void setCampaignPrefix(String campaignPrefix) { this.campaignPrefix = campaignPrefix; }
    }

    public static class Lomadee {
        private String apiUrl = "https://api.lomadee.com.br";
        private String apiKey = "";
        private String sourceId = "";

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    }

    public static class Awin {
        private String apiUrl = "https://api.awin.com";
        private String oauthToken = "";
        private String publisherId = "";

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getOauthToken() { return oauthToken; }
        public void setOauthToken(String oauthToken) { this.oauthToken = oauthToken; }
        public String getPublisherId() { return publisherId; }
        public void setPublisherId(String publisherId) { this.publisherId = publisherId; }
    }

    public static class Retailers {
        private Shopee shopee = new Shopee();
        private AliExpress aliexpress = new AliExpress();
        private MercadoLivre mercadolivre = new MercadoLivre();
        private Amazon amazon = new Amazon();
        private LomadeeStore magalu = new LomadeeStore(false, false, "magazineluiza");
        private LomadeeStore kabum = new LomadeeStore(false, false, "kabum");
        private LomadeeStore casasbahia = new LomadeeStore(false, false, "casasbahia");
        private LomadeeStore americanas = new LomadeeStore(false, false, "americanas");
        private LomadeeStore netshoes = new LomadeeStore(false, false, "netshoes");

        public Shopee getShopee() { return shopee; }
        public void setShopee(Shopee shopee) { this.shopee = shopee; }
        public AliExpress getAliexpress() { return aliexpress; }
        public void setAliexpress(AliExpress aliexpress) { this.aliexpress = aliexpress; }
        public MercadoLivre getMercadolivre() { return mercadolivre; }
        public void setMercadolivre(MercadoLivre mercadolivre) { this.mercadolivre = mercadolivre; }
        public Amazon getAmazon() { return amazon; }
        public void setAmazon(Amazon amazon) { this.amazon = amazon; }
        public LomadeeStore getMagalu() { return magalu; }
        public void setMagalu(LomadeeStore magalu) { this.magalu = magalu; }
        public LomadeeStore getKabum() { return kabum; }
        public void setKabum(LomadeeStore kabum) { this.kabum = kabum; }
        public LomadeeStore getCasasbahia() { return casasbahia; }
        public void setCasasbahia(LomadeeStore casasbahia) { this.casasbahia = casasbahia; }
        public LomadeeStore getAmericanas() { return americanas; }
        public void setAmericanas(LomadeeStore americanas) { this.americanas = americanas; }
        public LomadeeStore getNetshoes() { return netshoes; }
        public void setNetshoes(LomadeeStore netshoes) { this.netshoes = netshoes; }
    }

    public static class Shopee {
        private boolean enabled = true;
        private boolean sandboxMode = false;
        private String apiUrl = "https://open-api.affiliate.shopee.com.br/graphql";
        private String appId = "";
        private String appSecret = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isSandboxMode() { return sandboxMode; }
        public void setSandboxMode(boolean sandboxMode) { this.sandboxMode = sandboxMode; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    }

    public static class AliExpress {
        private boolean enabled = true;
        private boolean sandboxMode = false;
        private String apiUrl = "https://api-sg.aliexpress.com/sync";
        private String appKey = "";
        private String appSecret = "";
        private String trackingId = "default";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isSandboxMode() { return sandboxMode; }
        public void setSandboxMode(boolean sandboxMode) { this.sandboxMode = sandboxMode; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public String getTrackingId() { return trackingId; }
        public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    }

    public static class MercadoLivre {
        private boolean enabled = false;
        private boolean sandboxMode = false;
        private String apiUrl = "https://api.mercadolibre.com";
        private String clientId = "";
        private String clientSecret = "";
        private String accessToken = "";
        private String refreshToken = "";
        private String affiliateTool = "";
        private String affiliateWord = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isSandboxMode() { return sandboxMode; }
        public void setSandboxMode(boolean sandboxMode) { this.sandboxMode = sandboxMode; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public String getAffiliateTool() { return affiliateTool; }
        public void setAffiliateTool(String affiliateTool) { this.affiliateTool = affiliateTool; }
        public String getAffiliateWord() { return affiliateWord; }
        public void setAffiliateWord(String affiliateWord) { this.affiliateWord = affiliateWord; }
    }

    public static class Amazon {
        private boolean enabled = false;
        private boolean sandboxMode = false;
        private String apiUrl = "https://affiliate-program.amazon.com/creatorsapi/v1";
        private String tokenUrl = "https://api.amazon.com/auth/o2/token";
        private String credentialId = "";
        private String credentialSecret = "";
        private String partnerTag = "";
        private String marketplace = "www.amazon.com.br";
        private double maxTps = 1.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isSandboxMode() { return sandboxMode; }
        public void setSandboxMode(boolean sandboxMode) { this.sandboxMode = sandboxMode; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getCredentialId() { return credentialId; }
        public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
        public String getCredentialSecret() { return credentialSecret; }
        public void setCredentialSecret(String credentialSecret) { this.credentialSecret = credentialSecret; }
        public String getPartnerTag() { return partnerTag; }
        public void setPartnerTag(String partnerTag) { this.partnerTag = partnerTag; }
        public String getMarketplace() { return marketplace; }
        public void setMarketplace(String marketplace) { this.marketplace = marketplace; }
        public double getMaxTps() { return maxTps; }
        public void setMaxTps(double maxTps) { this.maxTps = maxTps; }
    }

    public static class LomadeeStore {
        private boolean enabled;
        private boolean sandboxMode;
        private String brandId;

        public LomadeeStore() {
        }

        public LomadeeStore(boolean enabled, boolean sandboxMode, String brandId) {
            this.enabled = enabled;
            this.sandboxMode = sandboxMode;
            this.brandId = brandId;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isSandboxMode() { return sandboxMode; }
        public void setSandboxMode(boolean sandboxMode) { this.sandboxMode = sandboxMode; }
        public String getBrandId() { return brandId; }
        public void setBrandId(String brandId) { this.brandId = brandId; }
    }
}
