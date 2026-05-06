package cn.cordys.mmba;

import cn.cordys.common.util.JSON;
import cn.cordys.crm.integration.common.utils.HttpRequestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MMBA 业务网关服务。
 * 统一组装标准请求体，并在业务失败时抛出带原始响应信息的异常。
 */
@Slf4j
@Service
public class MmbaGatewayService {

    private static final int OK = 200;
    private static final int PARTIAL = 201;
    private static final int REDIRECT = 302;

    @Resource
    private MmbaOutboundClient mmbaOutboundClient;
    @Resource
    private MmbaAccessTokenService mmbaAccessTokenService;

    public JsonNode invoke(MmbaCredential credential, String path, Object data) {
        return invoke(credential, path, data, true);
    }

    private JsonNode invoke(MmbaCredential credential, String path, Object data, boolean allowRetry) {
        String token = mmbaAccessTokenService.getToken(credential);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appKey", credential.appKey());
        body.put("accessToken", token);
        body.put("companyCode", credential.companyCode());
        body.put("data", data);
        log.info("MMBA 业务请求body:{}", JSON.toJSONString(body));
        JsonNode root = mmbaOutboundClient.postJson(trimBase(credential.apiBaseUrl()), path, credential.companyCode(), body);
        int code = root.path("code").asInt(-1);
        if (mmbaAccessTokenService.isAuthFail(root) && allowRetry) {
            log.warn("MMBA 业务返回 602，刷新 token 后重试 path={}", path);
            mmbaAccessTokenService.invalidate(credential);
            return invoke(credential, path, data, false);
        }
        if (code != OK && code != PARTIAL) {
            String message = root.path("message").asText();
            String responseBody = root.toString();
            String traceId = root.path("traceId").asText(null);
            log.warn("MMBA 业务失败 path={} code={} message={} body={}", path, code, message, responseBody);
            throw new MmbaInvokeException("MMBA 业务失败: " + message, code, traceId, responseBody, responseBody);
        }
        return root;
    }

    public byte[] invokeBinary(MmbaCredential credential, String path, Object data) {
        return invokeBinary(credential, path, data, true);
    }

    private byte[] invokeBinary(MmbaCredential credential, String path, Object data, boolean allowRetry) {
        String token = mmbaAccessTokenService.getToken(credential);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appKey", credential.appKey());
        body.put("accessToken", token);
        body.put("companyCode", credential.companyCode());
        body.put("data", data);

        HttpRequestUtil.BinaryResponse response = mmbaOutboundClient.postBinary(
                trimBase(credential.apiBaseUrl()), path, credential.companyCode(), body);

        if (response.statusCode() == REDIRECT) {
            String location = response.location();
            if (StringUtils.isBlank(location)) {
                throw new MmbaInvokeException("MMBA 下载失败: 302 响应缺少 Location", REDIRECT, null, null, null);
            }
            String redirectUrl = buildRedirectUrl(credential.apiBaseUrl(), location);
            response = mmbaOutboundClient.getBinary(redirectUrl);
        }

        return handleBinaryResponse(response, credential, path, data, allowRetry);
    }

    private byte[] handleBinaryResponse(HttpRequestUtil.BinaryResponse response, MmbaCredential credential,
                                        String path, Object data, boolean allowRetry) {
        byte[] raw = response.body();
        if (raw == null || raw.length == 0) {
            throw new MmbaInvokeException("MMBA 下载失败: 响应为空", response.statusCode(), null, null, null);
        }

        int start = firstNonWhitespace(raw);
        if (start >= 0 && start < raw.length && raw[start] == '{') {
            JsonNode root = tryParseJson(raw);
            if (root != null) {
                if (mmbaAccessTokenService.isAuthFail(root) && allowRetry) {
                    mmbaAccessTokenService.invalidate(credential);
                    return invokeBinary(credential, path, data, false);
                }
                int code = root.path("code").asInt(-1);
                if (code != OK && code != PARTIAL) {
                    String message = root.path("message").asText();
                    String responseBody = root.toString();
                    String traceId = root.path("traceId").asText(null);
                    throw new MmbaInvokeException("MMBA 下载失败: " + message, code, traceId, responseBody, responseBody);
                }
            }
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String responseBody = new String(raw, StandardCharsets.UTF_8);
            throw new MmbaInvokeException("MMBA 下载失败: HTTP " + response.statusCode(),
                    response.statusCode(), null, responseBody, responseBody);
        }
        return raw;
    }

    private JsonNode tryParseJson(byte[] raw) {
        try {
            return JSON.parseObject(new String(raw, StandardCharsets.UTF_8), JsonNode.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildRedirectUrl(String baseUrl, String location) {
        if (StringUtils.startsWithIgnoreCase(location, "http://") || StringUtils.startsWithIgnoreCase(location, "https://")) {
            return location;
        }
        String base = trimBase(baseUrl);
        if (location.startsWith("/")) {
            return base + location;
        }
        URI baseUri = URI.create(base + "/");
        return baseUri.resolve(location).toString();
    }

    private static String trimBase(String baseUrl) {
        return StringUtils.removeEnd(StringUtils.defaultString(baseUrl).trim(), "/");
    }

    private static int firstNonWhitespace(byte[] raw) {
        for (int i = 0; i < raw.length; i++) {
            if (!Character.isWhitespace((char) raw[i])) {
                return i;
            }
        }
        return -1;
    }
}
