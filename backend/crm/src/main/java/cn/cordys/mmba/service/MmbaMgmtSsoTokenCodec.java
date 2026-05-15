package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 管理平台 SSO 自定义 token：生成与解析（HMAC-SHA256 防篡改）。
 */
@Slf4j
@Component
public class MmbaMgmtSsoTokenCodec {

    private static final String HMAC_ALG = "HmacSHA256";

    @Value("${mmba.secret:}")
    private String secret;

    @Getter
    private byte[] hmacKey;

    @PostConstruct
    void initKey() {
        String s = StringUtils.trimToEmpty(secret);
        if (StringUtils.isBlank(s)) {
            log.warn("mmba.secret 未配置，管理平台 SSO token 将无法生成/校验");
            hmacKey = new byte[0];
            return;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            hmacKey = sha256.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化 mmba SSO token 密钥失败", e);
        }
    }

    /**
     * 生成带时效的 token 字符串（两段式：Base64URL(payload).Base64URL(hmac)）。
     *
     * @param um          当前租户用户 MMBA um，作为 ticket.loginName 对应值
     * @param companyCode 指掌易租户，与 orgCode 一致
     * @param ttlMs       有效期毫秒数
     */
    public String generateToken(String um, String companyCode, long ttlMs) {
        requireKey();
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("um", um);
        payload.put("companyCode", companyCode);
        payload.put("traceId", IDGenerator.nextStr());
        payload.put("exp", now + ttlMs);
        String json = JSON.toJSONString(payload);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String part0 = base64UrlEncode(body);
        String part1 = base64UrlEncode(sign(body));
        return part0 + "." + part1;
    }

    /**
     * 解析并校验 token：签名、未过期。
     *
     * @return um 与 companyCode，非法或过期则为 empty
     */
    public Optional<MmbaMgmtSsoTokenPayload> parseAndVerify(String token) {
        if (StringUtils.isBlank(token) || hmacKey == null || hmacKey.length == 0) {
            return Optional.empty();
        }
        String[] parts = StringUtils.splitPreserveAllTokens(token.trim(), '.');
        if (parts.length != 2) {
            return Optional.empty();
        }
        byte[] body;
        byte[] sigExpected;
        try {
            body = base64UrlDecode(parts[0]);
            sigExpected = base64UrlDecode(parts[1]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        byte[] sigActual = sign(body);
        if (sigActual.length != sigExpected.length || !slowEquals(sigActual, sigExpected)) {
            return Optional.empty();
        }
        Map<String, Object> map;
        try {
            map = JSON.parseObject(new String(body, StandardCharsets.UTF_8), new TypeReference<>() {});
        } catch (Exception e) {
            return Optional.empty();
        }
        if (map == null) {
            return Optional.empty();
        }
        String um = StringUtils.trimToNull(String.valueOf(map.getOrDefault("um", "")));
        String cc = StringUtils.trimToNull(String.valueOf(map.getOrDefault("companyCode", "")));
        String traceId = StringUtils.trimToNull(String.valueOf(map.getOrDefault("traceId", "")));
        Object expObj = map.get("exp");
        if (um == null || cc == null || traceId == null || expObj == null) {
            return Optional.empty();
        }
        long exp;
        try {
            exp = ((Number) expObj).longValue();
        } catch (Exception e) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > exp) {
            return Optional.empty();
        }
        return Optional.of(new MmbaMgmtSsoTokenPayload(um, cc));
    }

    private void requireKey() {
        if (hmacKey == null || hmacKey.length == 0) {
            throw new IllegalStateException("mmba.secret 未配置，无法生成管理平台 SSO token");
        }
    }

    private byte[] sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALG));
            return mac.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        int diff = a.length ^ b.length;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static String base64UrlEncode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] base64UrlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    public record MmbaMgmtSsoTokenPayload(String um, String companyCode) {}
}
