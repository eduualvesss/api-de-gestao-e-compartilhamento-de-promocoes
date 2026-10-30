package com.ecommerce.catalogo.integration.health;

import com.ecommerce.catalogo.integration.connector.ConnectorRegistry;
import com.ecommerce.catalogo.integration.model.ConnectorHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expõe o estado consolidado dos conectores de varejistas dentro do endpoint oficial /actuator/health.
 */
@Component("retailerConnectors")
public class RetailerConnectorsHealthIndicator implements HealthIndicator {

    private final ConnectorRegistry registry;

    public RetailerConnectorsHealthIndicator(ConnectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        List<ConnectorHealth> statuses = registry.checkAllHealth();
        long enabledCount = statuses.stream().filter(ConnectorHealth::enabled).count();
        long readyReal = statuses.stream().filter(s -> "UP_REAL".equals(s.operationalStatus())).count();
        long readySandbox = statuses.stream().filter(s -> "UP_SANDBOX".equals(s.operationalStatus())).count();
        long missingCreds = statuses.stream().filter(s -> "MISSING_CREDENTIALS".equals(s.operationalStatus())).count();
        long circuitOpen = statuses.stream().filter(s -> "DEGRADED_CIRCUIT_OPEN".equals(s.operationalStatus())).count();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("totalRegistered", statuses.size());
        details.put("enabled", enabledCount);
        details.put("readyReal", readyReal);
        details.put("readySandbox", readySandbox);
        details.put("missingCredentials", missingCreds);
        details.put("circuitOpen", circuitOpen);

        return Health.up().withDetails(details).build();
    }
}

