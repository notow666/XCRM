package cn.cordys.mmba;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.integration.common.utils.HttpRequestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MMBA 明文 JSON 出站调用（带 Api-Info 头）。
 */
@Slf4j
@Component
public class MmbaOutboundClient {

    public JsonNode postJson(String baseUrl, String path, String companyCode, Object body) {
        String url = joinBaseAndPath(baseUrl, path);
        try {
            log.info("MMBA POST {} Api-Info={}", url, companyCode);
            String responseBody = HttpRequestUtil.postString(url, companyCode, JSON.toJSONString(body));
            return JSON.parseObject(responseBody, JsonNode.class);
        } catch (Exception e) {
            log.error("MMBA 调用异常 url={}", url, e);
            throw new GenericException("MMBA 调用失败: " + e.getMessage());
        }
    }

    public byte[] postBinary(String baseUrl, String path, String companyCode, Object body) {
        String url = joinBaseAndPath(baseUrl, path);
        try {
            log.info("MMBA POST(binary) {} Api-Info={}", url, companyCode);
            return HttpRequestUtil.postBinary(url, companyCode, JSON.toJSONString(body));
        } catch (Exception e) {
            log.error("MMBA 下载异常 url={}", url, e);
            throw new GenericException("MMBA 下载失败: " + e.getMessage());
        }
    }

    private static String joinBaseAndPath(String baseUrl, String path) {
        String b = StringUtils.removeEnd(StringUtils.defaultString(baseUrl).trim(), "/");
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }
}
