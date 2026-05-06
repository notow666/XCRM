package cn.cordys.mmba;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * MMBA accessToken 获取与 Redis 缓存服务。
 * 当前按平台实际返回结构解析根节点 accessToken / expireTime。
 */
@Slf4j
@Service
public class MmbaAccessTokenService {

    private static final int AUTH_FAIL = 602;
    private static final String CACHE_KEY_PREFIX = "mmba:access-token:";
    private static final long CACHE_EARLY_EXPIRE_MS = 60_000;
    private static final long DEFAULT_TTL_MS = 24L * 3600_000;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MmbaOutboundClient mmbaOutboundClient;

    public String getToken(MmbaCredential credential) {
        String cacheKey = cacheKeyOf(credential);
        String cachedToken = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cachedToken)) {
            return cachedToken;
        }
        return refreshAndPut(credential);
    }

    public void invalidate(MmbaCredential credential) {
        stringRedisTemplate.delete(cacheKeyOf(credential));
    }

    public String refreshAndPut(MmbaCredential credential) {
        String base = StringUtils.removeEnd(credential.apiBaseUrl().trim(), "/");
        Object req = Map.of(
                "companyCode", credential.companyCode(),
                "appKey", credential.appKey(),
                "secret", credential.secret()
        );
        JsonNode root = mmbaOutboundClient.postJson(base, MmbaApiPaths.ACCESS_TOKEN, credential.companyCode(), req);
        String responseBody = root.toString();
        String traceId = root.path("traceId").asText(null);
        int code = root.path("code").asInt(-1);
        if (code != 200) {
            String message = root.path("message").asText();
            log.warn("MMBA 获取 accessToken 失败 code={} message={}", code, message);
            throw new MmbaInvokeException("MMBA 获取 accessToken 失败: " + message, code, traceId, responseBody, responseBody);
        }
        JsonNode tokenNode = root.get("accessToken");
        if (tokenNode == null || tokenNode.isNull() || StringUtils.isBlank(tokenNode.asText())) {
            throw new MmbaInvokeException("MMBA accessToken 响应缺少 accessToken", code, traceId, responseBody, responseBody);
        }
        String token = tokenNode.asText();
        long ttlMs = root.path("expireTime").asLong(0) * 1000;
        if (ttlMs <= 0) {
            ttlMs = DEFAULT_TTL_MS;
        }
        long cacheTtlMs = Math.max(ttlMs - CACHE_EARLY_EXPIRE_MS, 30_000);
        stringRedisTemplate.opsForValue().set(cacheKeyOf(credential), token, Duration.ofMillis(cacheTtlMs));
        long expireAt = System.currentTimeMillis() + cacheTtlMs;
        log.info("MMBA accessToken 已刷新并写入 Redis companyCode={} expireAtMs={}", credential.companyCode(), expireAt);
        return token;
    }

    public boolean isAuthFail(JsonNode responseRoot) {
        return responseRoot != null && responseRoot.path("code").asInt(0) == AUTH_FAIL;
    }

    private String cacheKeyOf(MmbaCredential credential) {
        String fingerprintRaw = credential.companyCode() + "|" + credential.appKey() + "|" + credential.secret() + "|" + credential.apiBaseUrl();
        String fingerprint = DigestUtils.md5DigestAsHex(fingerprintRaw.getBytes(StandardCharsets.UTF_8));
        return CACHE_KEY_PREFIX + fingerprint;
    }
}
