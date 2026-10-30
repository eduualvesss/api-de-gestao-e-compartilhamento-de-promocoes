package com.ecommerce.catalogo.integration.connector.impl;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.connector.RetailerConnector;
import com.ecommerce.catalogo.integration.model.RetailerId;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import com.ecommerce.catalogo.integration.service.OfferDeduplicator;
import com.ecommerce.catalogo.integration.service.PriceAndCurrencyNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Registra dinamicamente os conectores individuais das grandes varejistas nacionais
 * operadas via Lomadee API v3 (Magazine Luiza, KaBuM!, Casas Bahia, Americanas e Netshoes).
 */
@Configuration
public class LomadeeConnectorsConfig {

    @Bean
    public RetailerConnector magaluConnector(
            IntegrationProperties props, ConnectorResilienceExecutor resilience,
            PriceAndCurrencyNormalizer normalizer, OfferDeduplicator deduplicator,
            Clock clock, RestClient.Builder builder, ObjectMapper mapper) {
        return new LomadeeRetailerConnector(
                RetailerId.MAGALU, props.getRetailers().getMagalu(),
                props, resilience, normalizer, deduplicator, clock, builder.build(), mapper);
    }

    @Bean
    public RetailerConnector kabumConnector(
            IntegrationProperties props, ConnectorResilienceExecutor resilience,
            PriceAndCurrencyNormalizer normalizer, OfferDeduplicator deduplicator,
            Clock clock, RestClient.Builder builder, ObjectMapper mapper) {
        return new LomadeeRetailerConnector(
                RetailerId.KABUM, props.getRetailers().getKabum(),
                props, resilience, normalizer, deduplicator, clock, builder.build(), mapper);
    }

    @Bean
    public RetailerConnector casasBahiaConnector(
            IntegrationProperties props, ConnectorResilienceExecutor resilience,
            PriceAndCurrencyNormalizer normalizer, OfferDeduplicator deduplicator,
            Clock clock, RestClient.Builder builder, ObjectMapper mapper) {
        return new LomadeeRetailerConnector(
                RetailerId.CASAS_BAHIA, props.getRetailers().getCasasbahia(),
                props, resilience, normalizer, deduplicator, clock, builder.build(), mapper);
    }

    @Bean
    public RetailerConnector americanasConnector(
            IntegrationProperties props, ConnectorResilienceExecutor resilience,
            PriceAndCurrencyNormalizer normalizer, OfferDeduplicator deduplicator,
            Clock clock, RestClient.Builder builder, ObjectMapper mapper) {
        return new LomadeeRetailerConnector(
                RetailerId.AMERICANAS, props.getRetailers().getAmericanas(),
                props, resilience, normalizer, deduplicator, clock, builder.build(), mapper);
    }

    @Bean
    public RetailerConnector netshoesConnector(
            IntegrationProperties props, ConnectorResilienceExecutor resilience,
            PriceAndCurrencyNormalizer normalizer, OfferDeduplicator deduplicator,
            Clock clock, RestClient.Builder builder, ObjectMapper mapper) {
        return new LomadeeRetailerConnector(
                RetailerId.NETSHOES, props.getRetailers().getNetshoes(),
                props, resilience, normalizer, deduplicator, clock, builder.build(), mapper);
    }
}
