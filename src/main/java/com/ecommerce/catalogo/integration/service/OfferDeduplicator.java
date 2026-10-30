package com.ecommerce.catalogo.integration.service;

import com.ecommerce.catalogo.integration.config.IntegrationProperties;
import com.ecommerce.catalogo.integration.model.UnifiedOffer;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Deduplicador em dois níveis:
 * 1) Intra-loja: descarta duplicatas exatas (retailer + externalId / contentHash) e mantém cache TTL de coleta.
 * 2) Entre lojas (Cross-Store Product Matching): agrupa ofertas do mesmo produto via EAN/GTIN (Prioridade 1)
 *    ou assinatura normalizada de título/capacidade (Prioridade 2), preservando a oferta de menor custo efetivo BRL.
 */
@Component
public class OfferDeduplicator {

    private static final Set<String> STOPWORDS = Set.of(
            "DE", "DA", "DO", "DAS", "DOS", "COM", "PARA", "POR", "EM", "NO", "NA",
            "ORIGINAL", "OFICIAL", "NOVO", "LACRADO", "PROMOCAO", "OFERTA", "ENVIO",
            "IMEDIATO", "FULL", "BRASIL", "NACIONAL", "GARANTIA", "NOTA", "FISCAL", "COR", "TELA"
    );
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Z0-9 ]");

    private final IntegrationProperties properties;
    private final Clock clock;
    private final Map<String, Instant> seenContentHashes = new ConcurrentHashMap<>();

    public OfferDeduplicator(IntegrationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String computeCanonicalSku(String eanGtin, String title) {
        if (eanGtin != null) {
            String digits = eanGtin.replaceAll("\\D", "");
            if (digits.length() >= 8 && digits.length() <= 14) {
                return "EAN-" + digits;
            }
        }
        if (title == null || title.isBlank()) {
            return "UNMATCHED-ITEM";
        }
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        String cleaned = NON_ALPHANUM.matcher(ascii).replaceAll(" ").trim();
        String[] parts = cleaned.split("\\s+");
        List<String> significant = new ArrayList<>();
        for (String token : parts) {
            if (token.length() <= 1 || STOPWORDS.contains(token)) {
                continue;
            }
            significant.add(token);
            if (significant.size() == 5) {
                break;
            }
        }
        return significant.isEmpty() ? "ITEM-GENERICO" : String.join("-", significant);
    }

    public DeduplicationResult deduplicate(List<UnifiedOffer> rawOffers, boolean deduplicateCrossStore) {
        evictExpiredHashes();
        int totalRaw = rawOffers.size();
        int intraRemoved = 0;

        // Nível 1: Deduplicação intra-loja no lote (retailer + externalId)
        Map<String, UnifiedOffer> uniqueIntraStore = new LinkedHashMap<>();
        for (UnifiedOffer offer : rawOffers) {
            UnifiedOffer enriched = ensureCanonicalSku(offer);
            String storeKey = enriched.retailer().name() + ":" + enriched.externalId();
            UnifiedOffer existing = uniqueIntraStore.get(storeKey);
            if (existing == null) {
                uniqueIntraStore.put(storeKey, enriched);
            } else {
                intraRemoved++;
                if (enriched.effectiveCostBrl().compareTo(existing.effectiveCostBrl()) < 0) {
                    uniqueIntraStore.put(storeKey, enriched);
                }
            }
        }

        // Nível 2: Agrupamento / Deduplicação entre lojas por skuCanonical
        Map<String, List<UnifiedOffer>> byCanonical = new LinkedHashMap<>();
        for (UnifiedOffer offer : uniqueIntraStore.values()) {
            byCanonical.computeIfAbsent(offer.skuCanonical(), k -> new ArrayList<>()).add(offer);
        }

        int crossStoreGroupsMatched = (int) byCanonical.values().stream().filter(list -> list.size() > 1).count();
        int crossStoreCollapsed = 0;
        List<UnifiedOffer> finalOffers = new ArrayList<>();

        if (deduplicateCrossStore) {
            for (List<UnifiedOffer> group : byCanonical.values()) {
                group.sort(Comparator.comparing(UnifiedOffer::effectiveCostBrl));
                finalOffers.add(group.get(0));
                crossStoreCollapsed += (group.size() - 1);
            }
        } else {
            finalOffers.addAll(uniqueIntraStore.values());
        }

        finalOffers.sort(Comparator.comparing(UnifiedOffer::effectiveCostBrl));

        return new DeduplicationResult(
                finalOffers,
                totalRaw,
                intraRemoved,
                crossStoreGroupsMatched,
                crossStoreCollapsed
        );
    }

    /**
     * Filtra apenas ofertas inéditas ou que mudaram de preço/cupom/estoque dentro do TTL configurado,
     * evitando reenviar ofertas idênticas repetidamente à API de Filtros.
     */
    public List<UnifiedOffer> filterUnseenForDispatch(List<UnifiedOffer> offers) {
        evictExpiredHashes();
        long ttlSeconds = Math.max(60L, properties.getDeduplication().getTtlMinutes() * 60L);
        Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        List<UnifiedOffer> unseen = new ArrayList<>();
        for (UnifiedOffer offer : offers) {
            Instant previousExpiry = seenContentHashes.putIfAbsent(offer.contentHash(), expiresAt);
            if (previousExpiry == null || clock.instant().isAfter(previousExpiry)) {
                seenContentHashes.put(offer.contentHash(), expiresAt);
                unseen.add(offer);
            }
        }
        return unseen;
    }

    public void clearCache() {
        seenContentHashes.clear();
    }

    private UnifiedOffer ensureCanonicalSku(UnifiedOffer offer) {
        if (offer.skuCanonical() != null && !offer.skuCanonical().isBlank()) {
            return offer;
        }
        String canonical = computeCanonicalSku(offer.eanGtin(), offer.title());
        return new UnifiedOffer(
                offer.internalId(), offer.retailer(), offer.connectorSource(), offer.dataSourceMode(),
                offer.externalId(), offer.eanGtin(), canonical, offer.title(), offer.urlOriginal(),
                offer.urlAffiliate(), offer.imageUrl(), offer.pricing(), offer.coupon(), offer.category(),
                offer.rating(), offer.seller(), offer.shipping(), offer.stock(), offer.timestamps(),
                offer.contentHash()
        );
    }

    private void evictExpiredHashes() {
        Instant now = clock.instant();
        seenContentHashes.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }

    public record DeduplicationResult(
            List<UnifiedOffer> offers,
            int totalRawOffers,
            int intraStoreDuplicatesRemoved,
            int crossStoreGroupsMatched,
            int crossStoreDuplicatesCollapsed
    ) {
    }
}
