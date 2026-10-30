package com.ecommerce.catalogo.integration.connector;

import com.ecommerce.catalogo.integration.model.*;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * Interface comum (Padrão Adapter/Connector) para integração com varejistas
 * e redes oficiais de afiliados.
 */
public interface RetailerConnector {

    RetailerId getRetailerId();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isSandboxMode();

    void setSandboxMode(boolean sandboxMode);

    boolean hasRealCredentials();

    /** Busca ofertas promocionais com base nos critérios informados. */
    List<UnifiedOffer> search(SearchCriteria criteria);

    /** Obtém e revalida os detalhes atualizados de uma oferta pelo ID externo na loja. */
    Optional<UnifiedOffer> getOffer(String externalId);

    /** Converte um nó de resposta nativa do varejista para o Schema Unificado de Oferta. */
    UnifiedOffer normalize(JsonNode rawItem, DataSourceMode mode);

    /** Constrói ou enriquece o link rastreável de afiliado da loja. */
    String buildAffiliateUrl(String originalUrl, String campaignSubId);

    /** Retorna o diagnóstico de saúde operacional e métricas do conector. */
    ConnectorHealth checkHealth();
}
