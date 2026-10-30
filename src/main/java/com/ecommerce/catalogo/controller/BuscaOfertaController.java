package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.exception.RegraDeNegocioException;
import com.ecommerce.catalogo.integration.exception.ConnectorConfigurationException;
import com.ecommerce.catalogo.integration.model.*;
import com.ecommerce.catalogo.integration.service.OfferSearchOrchestratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Endpoints de busca agregada de promoções multi-varejista, revalidação unitária
 * e despacho em lote para a API de Filtros.
 */
@RestController
@RequestMapping("/ofertas")
public class BuscaOfertaController {

    private final OfferSearchOrchestratorService orchestrator;

    public BuscaOfertaController(OfferSearchOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/busca")
    public AggregatedSearchResponse buscar(
            @RequestParam(defaultValue = "") String termo,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) BigDecimal descontoMin,
            @RequestParam(required = false) BigDecimal precoMax,
            @RequestParam(required = false) String lojas,
            @RequestParam(defaultValue = "true") boolean deduplicarEntreLojas,
            @RequestParam(defaultValue = "false") boolean enviarParaFiltro,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int limite
    ) {
        SearchCriteria criteria = buildCriteria(
                termo, categoria, descontoMin, precoMax, lojas, deduplicarEntreLojas, pagina, limite);
        return orchestrator.searchAcrossRetailers(criteria, enviarParaFiltro);
    }

    @PostMapping("/coletar-e-filtrar")
    public AggregatedSearchResponse coletarEFiltrar(
            @RequestParam(defaultValue = "") String termo,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) BigDecimal descontoMin,
            @RequestParam(required = false) BigDecimal precoMax,
            @RequestParam(required = false) String lojas,
            @RequestParam(defaultValue = "true") boolean deduplicarEntreLojas,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int limite
    ) {
        SearchCriteria criteria = buildCriteria(
                termo, categoria, descontoMin, precoMax, lojas, deduplicarEntreLojas, pagina, limite);
        return orchestrator.searchAcrossRetailers(criteria, true);
    }

    @GetMapping("/{loja}/{idExterno}")
    public ResponseEntity<UnifiedOffer> obterOfertaNaLoja(
            @PathVariable String loja,
            @PathVariable String idExterno
    ) {
        RetailerId retailerId = RetailerId.fromPathOrParam(loja);
        try {
            return ResponseEntity.ok(orchestrator.getSingleOffer(retailerId, idExterno));
        } catch (ConnectorConfigurationException ex) {
            throw new RegraDeNegocioException(ex.getMessage());
        }
    }

    private SearchCriteria buildCriteria(
            String termo, String categoria, BigDecimal descontoMin, BigDecimal precoMax,
            String lojas, boolean deduplicarEntreLojas, int pagina, int limite
    ) {
        Set<RetailerId> selectedStores = new LinkedHashSet<>();
        if (lojas != null && !lojas.isBlank()) {
            for (String token : lojas.split(",")) {
                if (!token.isBlank()) {
                    selectedStores.add(RetailerId.fromPathOrParam(token));
                }
            }
        }
        return new SearchCriteria(
                termo, categoria, descontoMin, precoMax, selectedStores, deduplicarEntreLojas, pagina, limite);
    }
}

