package com.ecommerce.catalogo.integration.connector;

import com.ecommerce.catalogo.exception.RecursoNaoEncontradoException;
import com.ecommerce.catalogo.integration.model.ConnectorHealth;
import com.ecommerce.catalogo.integration.model.RetailerId;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registro dinâmico de todos os conectores de varejistas registrados no contexto Spring.
 */
@Component
public class ConnectorRegistry {

    private final Map<RetailerId, RetailerConnector> connectorsByRetailer;

    public ConnectorRegistry(List<RetailerConnector> connectors) {
        Map<RetailerId, RetailerConnector> map = new EnumMap<>(RetailerId.class);
        for (RetailerConnector connector : connectors) {
            map.put(connector.getRetailerId(), connector);
        }
        this.connectorsByRetailer = Collections.unmodifiableMap(map);
    }

    public List<RetailerConnector> listAll() {
        return List.copyOf(connectorsByRetailer.values());
    }

    public List<RetailerConnector> listEnabled(Set<RetailerId> filterRetailers) {
        return connectorsByRetailer.values().stream()
                .filter(RetailerConnector::isEnabled)
                .filter(c -> filterRetailers == null || filterRetailers.isEmpty() || filterRetailers.contains(c.getRetailerId()))
                .toList();
    }

    public RetailerConnector getRequired(RetailerId retailerId) {
        RetailerConnector connector = connectorsByRetailer.get(retailerId);
        if (connector == null) {
            throw new RecursoNaoEncontradoException("Conector não registrado para a loja: " + retailerId);
        }
        return connector;
    }

    public List<ConnectorHealth> checkAllHealth() {
        return connectorsByRetailer.values().stream()
                .map(RetailerConnector::checkHealth)
                .toList();
    }
}
