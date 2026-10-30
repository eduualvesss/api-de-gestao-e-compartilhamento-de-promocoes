package com.ecommerce.catalogo.integration.local;

import com.ecommerce.catalogo.integration.filter.FilterBatchRequest;
import com.ecommerce.catalogo.integration.filter.FilterBatchResponse;
import com.ecommerce.catalogo.integration.model.DataSourceMode;
import com.ecommerce.catalogo.integration.model.UnifiedOffer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Simulador local dos componentes externos do pipeline (Banco TCP H2 compatível com PostgreSQL,
 * API de Filtros na porta :8081 e API de Compartilhamento na porta :8082) para execução ponta a ponta
 * enquanto os microsserviços definitivos da equipe ainda estão em desenvolvimento.
 */
@Component
@Profile("local")
public class LocalPipelineSimulatorsServer {

    private static final Logger log = LoggerFactory.getLogger(LocalPipelineSimulatorsServer.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean enabled;
    private final int filterPort;
    private final int sharePort;
    private final int h2TcpPort;
    private final String expectedInternalApiKey;

    private Object h2TcpServer;
    private HttpServer filterHttpServer;
    private HttpServer shareHttpServer;

    private final Set<String> seenBatchIds = ConcurrentHashMap.newKeySet();
    private final List<UnifiedOffer> approvedOffersDb = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> publishedMessagesLog = new CopyOnWriteArrayList<>();
    private int totalRejectedCount = 0;

    public LocalPipelineSimulatorsServer(
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${integration.local-simulators.enabled:true}") boolean enabled,
            @Value("${integration.local-simulators.filter-port:8081}") int filterPort,
            @Value("${integration.local-simulators.share-port:8082}") int sharePort,
            @Value("${integration.local-simulators.h2-tcp-port:9092}") int h2TcpPort,
            @Value("${integration.filter-api.api-key:}") String expectedInternalApiKey
    ) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.enabled = enabled;
        this.filterPort = filterPort;
        this.sharePort = sharePort;
        this.h2TcpPort = h2TcpPort;
        this.expectedInternalApiKey = expectedInternalApiKey;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("Simuladores locais do pipeline (:8081 / :8082) desabilitados por configuracao.");
            return;
        }
        startH2TcpServer();
        startShareApiSimulator();
        startFilterApiSimulator();
    }

    private void startH2TcpServer() {
        try {
            Class<?> serverClass = Class.forName("org.h2.tools.Server");
            Method createTcpServer = serverClass.getMethod("createTcpServer", String[].class);
            String[] args = new String[]{"-tcp", "-tcpAllowOthers", "-tcpPort", String.valueOf(h2TcpPort), "-ifNotExists"};
            Object server = createTcpServer.invoke(null, (Object) args);
            Method startMethod = serverClass.getMethod("start");
            h2TcpServer = startMethod.invoke(server);
            log.info("[DB-LOCAL] Servidor TCP H2 (compativel com PostgreSQL) iniciado na porta :{}", h2TcpPort);
        } catch (Exception ex) {
            log.info("[DB-LOCAL] Porta TCP H2 :{} ja em uso ou indisponivel; mantendo banco em memoria.", h2TcpPort);
        }
    }

    private void startFilterApiSimulator() {
        try {
            filterHttpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", filterPort), 0);
            filterHttpServer.setExecutor(Executors.newCachedThreadPool());

            filterHttpServer.createContext("/actuator/health", exchange -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", "UP");
                body.put("service", "api-filtros (simulador-contrato-local)");
                body.put("port", filterPort);
                body.put("database", "H2-PostgreSQL-Mode");
                body.put("approvedOffersPersisted", approvedOffersDb.size());
                body.put("rejectedOffersTotal", totalRejectedCount);
                writeJson(exchange, 200, body);
            });

            filterHttpServer.createContext("/api/v1/filter/offers", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    writeJson(exchange, 200, Map.of(
                            "totalApproved", approvedOffersDb.size(),
                            "offers", approvedOffersDb
                    ));
                } else {
                    writeJson(exchange, 405, Map.of("error", "Method Not Allowed"));
                }
            });

            filterHttpServer.createContext("/api/v1/filter/offers:evaluate-batch", this::handleFilterEvaluateBatch);

            filterHttpServer.start();
            log.info("[API-FILTROS-LOCAL] Simulador de contrato da API de Filtros ativo em http://localhost:{}", filterPort);
        } catch (BindException be) {
            log.info("[API-FILTROS-LOCAL] Porta :{} ja esta em uso por outro processo; simulador local nao sobrescreveu a porta.", filterPort);
        } catch (IOException ex) {
            log.warn("[API-FILTROS-LOCAL] Nao foi possivel iniciar simulador na porta :{}: {}", filterPort, ex.getMessage());
        }
    }

    private void startShareApiSimulator() {
        try {
            shareHttpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", sharePort), 0);
            shareHttpServer.setExecutor(Executors.newCachedThreadPool());

            shareHttpServer.createContext("/actuator/health", exchange -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", "UP");
                body.put("service", "api-compartilhamento (simulador-contrato-local)");
                body.put("port", sharePort);
                body.put("mode", "DRY_RUN_SANDBOX");
                body.put("channelsConfigured", List.of("TELEGRAM", "WHATSAPP", "DISCORD"));
                body.put("publicationsTotal", publishedMessagesLog.size());
                writeJson(exchange, 200, body);
            });

            shareHttpServer.createContext("/api/v1/share/publications", exchange -> {
                writeJson(exchange, 200, Map.of(
                        "totalPublications", publishedMessagesLog.size(),
                        "publications", publishedMessagesLog
                ));
            });

            shareHttpServer.createContext("/api/v1/share/publish", this::handleSharePublish);

            shareHttpServer.start();
            log.info("[API-COMPARTILHAMENTO-LOCAL] Simulador de contrato da API de Compartilhamento ativo em http://localhost:{}", sharePort);
        } catch (BindException be) {
            log.info("[API-COMPARTILHAMENTO-LOCAL] Porta :{} ja esta em uso por outro processo; simulador local nao sobrescreveu a porta.", sharePort);
        } catch (IOException ex) {
            log.warn("[API-COMPARTILHAMENTO-LOCAL] Nao foi possivel iniciar simulador na porta :{}: {}", sharePort, ex.getMessage());
        }
    }

    private void handleFilterEvaluateBatch(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        if (expectedInternalApiKey != null && !expectedInternalApiKey.isBlank()) {
            String headerKey = exchange.getRequestHeaders().getFirst("X-Internal-Api-Key");
            if (!expectedInternalApiKey.equals(headerKey)) {
                writeJson(exchange, 401, Map.of("error", "Invalid or missing X-Internal-Api-Key"));
                return;
            }
        }

        FilterBatchRequest batchRequest;
        try (InputStream is = exchange.getRequestBody()) {
            batchRequest = objectMapper.readValue(is, FilterBatchRequest.class);
        }

        String idempotencyKey = Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"))
                .orElse(batchRequest.batchId());
        boolean duplicateBatch = !seenBatchIds.add(idempotencyKey);

        List<FilterBatchResponse.OfferEvaluationResult> results = new ArrayList<>();
        List<UnifiedOffer> newlyApproved = new ArrayList<>();
        int approved = 0;
        int rejected = 0;

        List<UnifiedOffer> offers = batchRequest.offers() != null ? batchRequest.offers() : List.of();
        for (UnifiedOffer offer : offers) {
            BigDecimal currentPrice = offer.pricing() != null && offer.pricing().currentPrice() != null
                    ? offer.pricing().currentPrice() : BigDecimal.ZERO;
            BigDecimal originalPrice = offer.pricing() != null && offer.pricing().originalPrice() != null
                    ? offer.pricing().originalPrice() : currentPrice;

            if (duplicateBatch) {
                rejected++;
                results.add(new FilterBatchResponse.OfferEvaluationResult(
                        offer.internalId(),
                        offer.externalId(),
                        offer.retailer(),
                        "REJECTED",
                        BigDecimal.ZERO,
                        false,
                        originalPrice,
                        currentPrice,
                        List.of("BATCH_ALREADY_PROCESSED")
                ));
                continue;
            }

            Evaluation eval = evaluateOfferQuality(offer);
            if ("APPROVED".equals(eval.status())) {
                approved++;
                approvedOffersDb.add(offer);
                newlyApproved.add(offer);
            } else {
                rejected++;
                totalRejectedCount++;
            }
            results.add(new FilterBatchResponse.OfferEvaluationResult(
                    offer.internalId(),
                    offer.externalId(),
                    offer.retailer(),
                    eval.status(),
                    BigDecimal.valueOf(eval.score()),
                    "APPROVED".equals(eval.status()),
                    originalPrice,
                    currentPrice,
                    "APPROVED".equals(eval.status()) ? List.of() : List.of(eval.reasonCode())
            ));
        }

        if (!newlyApproved.isEmpty()) {
            forwardApprovedOffersToShareApi(newlyApproved, batchRequest.batchId());
        }

        FilterBatchResponse response = new FilterBatchResponse(
                batchRequest.batchId(),
                Instant.now(),
                new FilterBatchResponse.Summary(offers.size(), approved, rejected),
                results
        );
        writeJson(exchange, 200, response);
    }

    private Evaluation evaluateOfferQuality(UnifiedOffer offer) {
        if (offer.stock() == null || !Boolean.TRUE.equals(offer.stock().inStock())) {
            return new Evaluation("REJECTED", 0, "OUT_OF_STOCK");
        }
        if (offer.pricing() == null || offer.pricing().currentPrice() == null
                || offer.pricing().currentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return new Evaluation("REJECTED", 0, "INVALID_PRICE");
        }
        BigDecimal discountPct = offer.pricing().discountPercentage() != null
                ? offer.pricing().discountPercentage()
                : BigDecimal.ZERO;
        if (discountPct.compareTo(new BigDecimal("5.00")) < 0) {
            return new Evaluation("REJECTED", 20, "DISCOUNT_BELOW_MINIMUM_5PCT");
        }
        if (discountPct.compareTo(new BigDecimal("90.00")) > 0) {
            return new Evaluation("REJECTED", 10, "SUSPECTED_FAKE_DISCOUNT_ABOVE_90PCT");
        }

        int score = 50 + Math.min(30, discountPct.intValue());
        if (offer.seller() != null && Boolean.TRUE.equals(offer.seller().isOfficialStore())) {
            score += 10;
        }
        if (offer.shipping() != null && Boolean.TRUE.equals(offer.shipping().freeShipping())) {
            score += 10;
        }
        return new Evaluation("APPROVED", Math.min(100, score), "PASSED_QUALITY_GATE");
    }

    private void forwardApprovedOffersToShareApi(List<UnifiedOffer> approvedOffers, String batchId) {
        try {
            restClient.post()
                    .uri("http://localhost:" + sharePort + "/api/v1/share/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", batchId)
                    .body(Map.of("batchId", batchId, "offers", approvedOffers))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("[API-FILTROS-LOCAL] Falha ao encaminhar {} ofertas aprovadas para API de Compartilhamento (:{}) - {}",
                    approvedOffers.size(), sharePort, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSharePublish(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        Map<String, Object> payload;
        try (InputStream is = exchange.getRequestBody()) {
            payload = objectMapper.readValue(is, Map.class);
        }

        String batchId = String.valueOf(payload.getOrDefault("batchId", UUID.randomUUID().toString()));
        List<Map<String, Object>> rawOffers = (List<Map<String, Object>>) payload.getOrDefault("offers", List.of());

        List<Map<String, Object>> receipts = new ArrayList<>();
        for (Map<String, Object> raw : rawOffers) {
            UnifiedOffer offer = objectMapper.convertValue(raw, UnifiedOffer.class);
            String prefix = offer.dataSourceMode() == DataSourceMode.SANDBOX ? "[SANDBOX] " : "";
            String taxInfo = (offer.pricing() != null && Boolean.TRUE.equals(offer.pricing().isInternational())
                    && offer.pricing().estimatedImportTax() != null)
                    ? " (+ R$ " + offer.pricing().estimatedImportTax() + " imp. est. Remessa Conforme)"
                    : "";
            String couponInfo = (offer.coupon() != null && offer.coupon().code() != null)
                    ? " | Cupom: " + offer.coupon().code()
                    : "";

            String messageText = String.format(
                    "%s🔥 [%s] %s por R$ %s%s (-%s%%)%s -> %s",
                    prefix,
                    offer.retailer(),
                    offer.title(),
                    offer.pricing() != null ? offer.pricing().currentPrice() : "0.00",
                    taxInfo,
                    offer.pricing() != null ? offer.pricing().discountPercentage() : "0",
                    couponInfo,
                    offer.urlAffiliate()
            );

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("publicationId", UUID.randomUUID().toString());
            entry.put("batchId", batchId);
            entry.put("internalOfferId", offer.internalId());
            entry.put("retailer", offer.retailer().name());
            entry.put("dataSourceMode", offer.dataSourceMode().name());
            entry.put("channels", List.of("TELEGRAM", "WHATSAPP", "DISCORD"));
            entry.put("status", "PUBLISHED_DRY_RUN");
            entry.put("formattedMessage", messageText);
            entry.put("publishedAt", Instant.now().toString());

            publishedMessagesLog.add(entry);
            receipts.add(entry);
            log.info("[API-COMPARTILHAMENTO-LOCAL] Oferta publicada (Dry-Run): {}", messageText);
        }

        writeJson(exchange, 200, Map.of(
                "batchId", batchId,
                "status", "PUBLISHED_DRY_RUN",
                "publishedOffersCount", receipts.size(),
                "receipts", receipts
        ));
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @PreDestroy
    public void stop() {
        if (filterHttpServer != null) {
            filterHttpServer.stop(0);
        }
        if (shareHttpServer != null) {
            shareHttpServer.stop(0);
        }
        if (h2TcpServer != null) {
            try {
                Method stopMethod = h2TcpServer.getClass().getMethod("stop");
                stopMethod.invoke(h2TcpServer);
            } catch (Exception ignored) {
            }
        }
    }

    private record Evaluation(String status, int score, String reasonCode) {
    }
}
