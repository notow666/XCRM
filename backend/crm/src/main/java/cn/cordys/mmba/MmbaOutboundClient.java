package cn.cordys.mmba;

import cn.cordys.common.util.JSON;
import cn.cordys.crm.integration.common.utils.HttpRequestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * MMBA 明文出站调用客户端。
 * 统一负责 HTTP 请求发送，并在失败时保留原始返回内容。
 */
@Slf4j
@Component
public class MmbaOutboundClient {

    public JsonNode postJson(String baseUrl, String path, String companyCode, Object body) {
        String url = joinBaseAndPath(baseUrl, path);
        try {
            log.info("MMBA POST {} Api-Info={}", url, companyCode);
            String responseBody = HttpRequestUtil.postString(url, companyCode, JSON.toJSONString(body));
            log.info("MMBA POST RETURN responseBody:{}", responseBody);
            try {
                return JSON.parseObject(responseBody, JsonNode.class);
            } catch (Exception parseException) {
                throw new MmbaInvokeException("MMBA 响应解析失败", -1, null, responseBody,
                        parseException.getClass().getName(), parseException);
            }
        } catch (Exception e) {
            if (e instanceof MmbaInvokeException invokeException) {
                log.error("MMBA 调用异常 url={}", url, invokeException);
                throw invokeException;
            }
            log.error("MMBA 调用异常 url={}", url, e);
            throw new MmbaInvokeException("MMBA 调用失败: " + e.getMessage(), -1, null, null,
                    e.getClass().getName(), e);
        }
    }

    public HttpRequestUtil.BinaryResponse postBinary(String baseUrl, String path, String companyCode, Object body) {
        String url = joinBaseAndPath(baseUrl, path);
        try {
            log.info("MMBA POST(binary) {} Api-Info={}", url, companyCode);
            return HttpRequestUtil.postBinary(url, companyCode, JSON.toJSONString(body));
        } catch (Exception e) {
            log.error("MMBA 下载异常 url={}", url, e);
            throw new MmbaInvokeException("MMBA 下载失败: " + e.getMessage(), -1, null, null,
                    e.getClass().getName(), e);
        }
    }

    public HttpRequestUtil.BinaryResponse getBinary(String url) {
        try {
            log.info("MMBA GET(binary) {}", url);
            return HttpRequestUtil.getBinary(url);
        } catch (Exception e) {
            log.error("MMBA GET 下载异常 url={}", url, e);
            throw new MmbaInvokeException("MMBA 下载失败: " + e.getMessage(), -1, null, null,
                    e.getClass().getName(), e);
        }
    }

    private static String joinBaseAndPath(String baseUrl, String path) {
        String b = StringUtils.removeEnd(StringUtils.defaultString(baseUrl).trim(), "/");
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }
}
