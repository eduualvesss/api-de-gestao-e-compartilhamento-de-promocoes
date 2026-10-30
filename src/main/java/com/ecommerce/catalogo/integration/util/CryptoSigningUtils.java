package com.ecommerce.catalogo.integration.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utilitários criptográficos para assinatura oficial de requisições (Shopee SHA-256,
 * AliExpress TOP/IOP HMAC-SHA256) e geração de contentHash SHA-256 determinístico.
 */
public final class CryptoSigningUtils {

    private CryptoSigningUtils() {
    }

    public static String sha256HexLower(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular SHA-256", e);
        }
    }

    /**
     * Assinatura oficial Shopee Affiliate Open API:
     * Signature = SHA256_HEX(AppId + Timestamp + PayloadJson + Secret)
     */
    public static String buildShopeeAuthorizationHeader(String appId, long timestampEpochSec, String jsonBody, String secret) {
        String factor = appId + timestampEpochSec + jsonBody + secret;
        String signature = sha256HexLower(factor);
        return "SHA256 Credential=" + appId + ", Timestamp=" + timestampEpochSec + ", Signature=" + signature;
    }

    /**
     * Assinatura oficial AliExpress Open Platform (HMAC-SHA256 uppercase hex sobre parâmetros ordenados).
     */
    public static String signAliExpressParameters(Map<String, String> params, String appSecret) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                sb.append(entry.getKey()).append(entry.getValue());
            }
        }
        return hmacSha256HexUpper(sb.toString(), appSecret);
    }

    public static String hmacSha256HexUpper(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular HMAC-SHA256", e);
        }
    }
}
