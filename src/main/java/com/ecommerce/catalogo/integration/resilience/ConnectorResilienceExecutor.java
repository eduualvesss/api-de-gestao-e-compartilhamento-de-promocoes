package com.ecommerce.catalogo.integration.resilience;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.exception.ConnectorConfigurationException;
import com.ecommerce.catalogo.integration.exception.ConnectorExecutionException;
import com.ecommerce.catalogo.integration.model.RetailerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Executor resiliente por varejista: aplica Rate Limit (ex.: 1 TPS na Amazon),
 * Circuit Breaker (CLOSED / OPEN / HALF_OPEN) e Retry com Exponential Backoff + Jitter.
 */
@Component
public class ConnectorResilienceExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConnectorResilienceExecutor.class);

    private final IntegrationProperties properties;
    private final Clock clock;
    private final LongConsumer sleeper;
    private final Map<RetailerId, StoreState> states = new ConcurrentHashMap<>();

    public ConnectorResilienceExecutor(IntegrationProperties properties, Clock clock) {
        this(properties, clock, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Thread interrompida durante backoff", e);
            }
        });
    }

    public ConnectorResilienceExecutor(IntegrationProperties properties, Clock clock, LongConsumer sleeper) {
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public <T> T execute(RetailerId retailer, double maxTps, Supplier<T> action) {
        StoreState state = states.computeIfAbsent(retailer, r -> new StoreState());
        checkCircuit(retailer, state);
        enforceRateLimit(state, maxTps);

        int maxAttempts = Math.max(1, properties.getResilience().getRetryMaxAttempts());
        long baseDelayMs = Math.max(10L, properties.getResilience().getRetryInitialDelayMs());

        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = action.get();
                recordSuccess(state);
                return result;
            } catch (ConnectorConfigurationException ex) {
                throw ex;
            } catch (Exception ex) {
                boolean retryable = isRetryable(ex);
                lastException = (ex instanceof ConnectorExecutionException cee)
                        ? cee
                        : new ConnectorExecutionException(retailer, extrairMensagem(ex), retryable, ex);

                if (!retryable || attempt == maxAttempts) {
                    recordFailure(retailer, state, lastException.getMessage());
                    throw lastException;
                }

                long delay = calcularBackoffComJitter(baseDelayMs, attempt);
                log.warn("[{}] Falha transitória na tentativa {}/{} ({}). Aguardando {}ms antes do retry.",
                        retailer, attempt, maxAttempts, extrairMensagem(ex), delay);
                sleeper.accept(delay);
            }
        }
        throw lastException != null
                ? lastException
                : new ConnectorExecutionException(retailer, "Falha desconhecida no conector", false);
    }

    public TelemetrySnapshot snapshot(RetailerId retailer) {
        StoreState s = states.computeIfAbsent(retailer, r -> new StoreState());
        String circuit = currentCircuitState(s);
        return new TelemetrySnapshot(
                circuit,
                s.successfulCalls.get(),
                s.failedCalls.get(),
                s.lastError,
                s.lastFailureAt
        );
    }

    public void resetState(RetailerId retailer) {
        states.remove(retailer);
    }

    private void checkCircuit(RetailerId retailer, StoreState state) {
        String circuit = currentCircuitState(state);
        if ("OPEN".equals(circuit)) {
            throw new ConnectorExecutionException(retailer,
                    "Circuit Breaker ABERTO para " + retailer + " devido a falhas consecutivas recentes", false);
        }
    }

    private String currentCircuitState(StoreState state) {
        Instant openedUntil = state.circuitOpenUntil;
        if (openedUntil == null) {
            return "CLOSED";
        }
        if (clock.instant().isBefore(openedUntil)) {
            return "OPEN";
        }
        return "HALF_OPEN";
    }

    private void enforceRateLimit(StoreState state, double maxTps) {
        if (maxTps <= 0) {
            return;
        }
        long minIntervalMs = Math.round(1000.0 / maxTps);
        long waitMs = 0L;
        synchronized (state.rateLimitLock) {
            long now = clock.millis();
            long earliestNext = state.lastPermitTimestampMs + minIntervalMs;
            if (now < earliestNext) {
                waitMs = earliestNext - now;
                state.lastPermitTimestampMs = earliestNext;
            } else {
                state.lastPermitTimestampMs = now;
            }
        }
        if (waitMs > 0) {
            sleeper.accept(waitMs);
        }
    }

    private void recordSuccess(StoreState state) {
        state.successfulCalls.incrementAndGet();
        state.consecutiveFailures.set(0);
        state.circuitOpenUntil = null;
    }

    private void recordFailure(RetailerId retailer, StoreState state, String errorMessage) {
        state.failedCalls.incrementAndGet();
        state.lastError = errorMessage;
        state.lastFailureAt = clock.instant();
        int failures = state.consecutiveFailures.incrementAndGet();
        int threshold = Math.max(1, properties.getResilience().getCircuitFailureThreshold());
        if (failures >= threshold) {
            long openSecs = Math.max(5L, properties.getResilience().getCircuitOpenDurationSeconds());
            state.circuitOpenUntil = clock.instant().plusSeconds(openSecs);
            log.error("[{}] Circuit Breaker ABERTO por {}s após {} falhas consecutivas.", retailer, openSecs, failures);
        }
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof ConnectorExecutionException cee) {
            return cee.isRetryable();
        }
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException rcre) {
            HttpStatusCode status = rcre.getStatusCode();
            int code = status.value();
            return code == 429 || code == 408 || code >= 500;
        }
        return false;
    }

    private long calcularBackoffComJitter(long baseDelayMs, int attempt) {
        long exponential = baseDelayMs * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(15L, baseDelayMs / 2));
        return Math.min(10_000L, exponential + jitter);
    }

    private String extrairMensagem(Exception ex) {
        if (ex instanceof RestClientResponseException rcre) {
            return "HTTP " + rcre.getStatusCode().value() + " (" + rcre.getStatusText() + ")";
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    public record TelemetrySnapshot(
            String circuitState,
            long successfulCalls,
            long failedCalls,
            String lastError,
            Instant lastFailureAt
    ) {
    }

    private static final class StoreState {
        private final Object rateLimitLock = new Object();
        private final AtomicLong successfulCalls = new AtomicLong();
        private final AtomicLong failedCalls = new AtomicLong();
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile long lastPermitTimestampMs = 0L;
        private volatile Instant circuitOpenUntil;
        private volatile String lastError;
        private volatile Instant lastFailureAt;
    }
}
