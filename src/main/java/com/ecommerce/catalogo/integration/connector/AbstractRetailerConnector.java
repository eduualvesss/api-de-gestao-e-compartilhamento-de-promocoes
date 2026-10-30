package com.ecommerce.catalogo.integration.connector;

import com.ecommerce.catalogo.exception.RegraDeNegocioException;
import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.exception.ConnectorConfigurationException;
import com.ecommerce.catalogo.integration.model.*;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import com.ecommerce.catalogo.integration.service.OfferDeduplicator;
import com.ecommerce.catalogo.integration.service.PriceAndCurrencyNormalizer;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base comum dos conectores: gerencia Feature Flags dinâmicas, validação estrita de modo
 * REAL vs. SANDBOX (proibindo dados falsos disfarçados de reais), resiliência e health check.
 */
public abstract class AbstractRetailerConnector implements RetailerConnector {

    protected final RetailerId retailerId;
    protected final IntegrationProperties properties;
    protected final ConnectorResilienceExecutor resilienceExecutor;
    protected final PriceAndCurrencyNormalizer normalizer;
    protected final OfferDeduplicator deduplicator;
    protected final Clock clock;

    private final AtomicBoolean enabled;
    private final AtomicBoolean sandboxMode;

    protected AbstractRetailerConnector(
            RetailerId retailerId,
            boolean initialEnabled,
            boolean initialSandboxMode,
            IntegrationProperties properties,
            ConnectorResilienceExecutor resilienceExecutor,
            PriceAndCurrencyNormalizer normalizer,
            OfferDeduplicator deduplicator,
            Clock clock
    ) {
        this.retailerId = retailerId;
        this.enabled = new AtomicBoolean(initialEnabled);
        this.sandboxMode = new AtomicBoolean(initialSandboxMode);
        this.properties = properties;
        this.resilienceExecutor = resilienceExecutor;
        this.normalizer = normalizer;
        this.deduplicator = deduplicator;
        this.clock = clock;
    }

    @Override
    public RetailerId getRetailerId() {
        return retailerId;
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void setEnabled(boolean value) {
        this.enabled.set(value);
    }

    @Override
    public boolean isSandboxMode() {
        return sandboxMode.get();
    }

    @Override
    public void setSandboxMode(boolean value) {
        this.sandboxMode.set(value);
    }

    protected double getMaxTps() {
        return 10.0;
    }

    @Override
    public final List<UnifiedOffer> search(SearchCriteria criteria) {
        DataSourceMode mode = resolveAndValidateExecutionMode();
        List<UnifiedOffer> results;
        if (mode == DataSourceMode.SANDBOX) {
            results = executeSandboxSearch(criteria);
        } else {
            results = resilienceExecutor.execute(retailerId, getMaxTps(), () -> executeRealSearch(criteria));
        }
        return applyPostCriteriaFilter(results, criteria);
    }

    @Override
    public final Optional<UnifiedOffer> getOffer(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new RegraDeNegocioException("externalId é obrigatório");
        }
        DataSourceMode mode = resolveAndValidateExecutionMode();
        if (mode == DataSourceMode.SANDBOX) {
            return executeSandboxGetOffer(externalId.trim());
        }
        return resilienceExecutor.execute(retailerId, getMaxTps(), () -> executeRealGetOffer(externalId.trim()));
    }

    @Override
    public ConnectorHealth checkHealth() {
        ConnectorResilienceExecutor.TelemetrySnapshot snap = resilienceExecutor.snapshot(retailerId);
        boolean isEn = isEnabled();
        boolean isSb = isSandboxMode();
        boolean creds = hasRealCredentials();

        String status;
        if (!isEn) {
            status = "DISABLED";
        } else if ("OPEN".equals(snap.circuitState())) {
            status = "DEGRADED_CIRCUIT_OPEN";
        } else if (isSb) {
            status = "UP_SANDBOX";
        } else if (!creds) {
            status = "MISSING_CREDENTIALS";
        } else {
            status = "UP_REAL";
        }

        return new ConnectorHealth(
                retailerId,
                retailerId.getDisplayName(),
                isEn,
                isSb,
                creds,
                status,
                snap.circuitState(),
                snap.successfulCalls(),
                snap.failedCalls(),
                snap.lastError(),
                snap.lastFailureAt(),
                clock.instant()
        );
    }

    protected DataSourceMode resolveAndValidateExecutionMode() {
        if (!isEnabled()) {
            throw new ConnectorConfigurationException(retailerId,
                    "Conector " + retailerId + " está desativado via Feature Flag");
        }
        if (isSandboxMode()) {
            return DataSourceMode.SANDBOX;
        }
        if (!hasRealCredentials()) {
            throw new ConnectorConfigurationException(retailerId,
                    "Credenciais oficiais não configuradas para " + retailerId
                            + " e sandboxMode=false. Configure as variáveis de ambiente ou ative sandboxMode explicitamente para testes.");
        }
        return DataSourceMode.REAL;
    }

    protected List<UnifiedOffer> applyPostCriteriaFilter(List<UnifiedOffer> offers, SearchCriteria criteria) {
        return offers.stream()
                .filter(o -> criteria.minDiscountPercentage() == null
                        || (o.pricing() != null && o.pricing().discountPercentage() != null
                        && o.pricing().discountPercentage().compareTo(criteria.minDiscountPercentage()) >= 0))
                .filter(o -> criteria.maxPriceBrl() == null
                        || (o.pricing() != null && o.pricing().currentPrice() != null
                        && o.pricing().currentPrice().compareTo(criteria.maxPriceBrl()) <= 0))
                .limit(criteria.limit())
                .toList();
    }

    protected String defaultSubId(String category) {
        String prefix = properties.getAffiliate().getCampaignPrefix();
        String cat = (category == null || category.isBlank()) ? "geral" : category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return prefix + "_" + retailerId.name().toLowerCase(Locale.ROOT) + "_" + cat;
    }

    /** Proteção contra SSRF / URLs inseguras: exige esquema https (ou http apenas em testes locais controlados). */
    protected String sanitizeExternalUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            throw new RegraDeNegocioException("URL externa com esquema inválido: " + rawUrl);
        }
        String host = uri.getHost();
        if (host == null || "169.254.169.254".equals(host)) {
            throw new RegraDeNegocioException("Host externo bloqueado por política SSRF: " + rawUrl);
        }
        return uri.toString();
    }

    protected static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    protected static BigDecimal parseDecimalOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replace("R$", "").replace("%", "").trim();
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected abstract List<UnifiedOffer> executeRealSearch(SearchCriteria criteria);

    protected abstract Optional<UnifiedOffer> executeRealGetOffer(String externalId);

    protected abstract List<UnifiedOffer> executeSandboxSearch(SearchCriteria criteria);

    protected abstract Optional<UnifiedOffer> executeSandboxGetOffer(String externalId);
}

