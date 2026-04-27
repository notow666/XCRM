package cn.cordys.mmba;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 封装 MMBA 标准业务请求体（appKey + accessToken + companyCode + data）。
 */
@Slf4j
@Service
public class MmbaGatewayService {

    private static final int OK = 200;
    private static final int PARTIAL = 201;

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
        JsonNode root = mmbaOutboundClient.postJson(trimBase(credential.apiBaseUrl()), path, credential.companyCode(), body);
        int code = root.path("code").asInt(-1);
        if (mmbaAccessTokenService.isAuthFail(root) && allowRetry) {
            log.warn("MMBA 业务返回 602，刷新 token 后重试 path={}", path);
            mmbaAccessTokenService.invalidate(credential);
            return invoke(credential, path, data, false);
        }
        if (code != OK && code != PARTIAL) {
            log.warn("MMBA 业务失败 path={} code={} message={} body={}", path, code, root.path("message").asText(), root);
            throw new GenericException("MMBA 业务失败: " + root.path("message").asText());
        }
        return root;
    }

    public byte[] invokeBinary(MmbaCredential credential, String path, Object data) {
        String token = mmbaAccessTokenService.getToken(credential);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appKey", credential.appKey());
        body.put("accessToken", token);
        body.put("companyCode", credential.companyCode());
        body.put("data", data);
        byte[] raw = mmbaOutboundClient.postBinary(trimBase(credential.apiBaseUrl()), path, credential.companyCode(), body);
        int start = firstNonWhitespace(raw);
        if (start >= 0 && start < raw.length && raw[start] == '{') {
            try {
                JsonNode n = JSON.parseObject(new String(raw, java.nio.charset.StandardCharsets.UTF_8), JsonNode.class);
                if (mmbaAccessTokenService.isAuthFail(n)) {
                    mmbaAccessTokenService.invalidate(credential);
                    return invokeBinaryAfterAuthRetry(credential, path, data);
                }
            } catch (Exception ignored) {
                // 二进制正文或非 JSON
            }
        }
        return raw;
    }

    private byte[] invokeBinaryAfterAuthRetry(MmbaCredential credential, String path, Object data) {
        String token = mmbaAccessTokenService.getToken(credential);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appKey", credential.appKey());
        body.put("accessToken", token);
        body.put("companyCode", credential.companyCode());
        body.put("data", data);
        return mmbaOutboundClient.postBinary(trimBase(credential.apiBaseUrl()), path, credential.companyCode(), body);
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
