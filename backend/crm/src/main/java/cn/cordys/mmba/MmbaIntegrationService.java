package cn.cordys.mmba;

import cn.cordys.context.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MMBA 原始能力服务。
 * 这一层只负责把请求转给网关，不做请求留痕和业务闭环。
 */
@Slf4j
@Service
public class MmbaIntegrationService {

    @Value("${mmba.api-base-url:}")
    private String apiBaseUrl;
    @Value("${mmba.company-code:}")
    private String companyCode;
    @Value("${mmba.app-key:}")
    private String appKey;
    @Value("${mmba.secret:}")
    private String secret;

    @Resource
    private MmbaGatewayService mmbaGatewayService;

    public JsonNode dial(JsonNode request) {
        return invoke(MmbaApiPaths.PHONE_DIAL, request);
    }

    public JsonNode callLimit(JsonNode request) {
        return invoke(MmbaApiPaths.PHONE_CALL_LIMIT, request);
    }

    public JsonNode sendSms(JsonNode request) {
        return invoke(MmbaApiPaths.PHONE_SEND_MSG, wrapSmsData(request));
    }

    public JsonNode sendWxMsg(JsonNode request) {
        return invoke(MmbaApiPaths.IM_SEND_WX_MSG, request);
    }

    public JsonNode addWxFriend(JsonNode request) {
        return invoke(MmbaApiPaths.IM_ADD_FRIEND, request);
    }

    public JsonNode sendWxMoment(JsonNode request) {
        return invoke(MmbaApiPaths.IM_SEND_WX_MOMENT, request);
    }

    public JsonNode modifyWxFriendRemark(JsonNode request) {
        return invoke(MmbaApiPaths.IM_MODIFY_WX_FRIEND_REMARK, request);
    }

    public JsonNode queryLoginWxAccount(JsonNode request) {
        return invoke(MmbaApiPaths.IM_QUERY_LOGIN_WX_ACCOUNT, request);
    }

    public JsonNode queryWxFriendList(JsonNode request) {
        return invoke(MmbaApiPaths.IM_QUERY_WX_FRIEND_LIST, request);
    }

    public JsonNode queryDuplicateWxFriend(JsonNode request) {
        return invoke(MmbaApiPaths.IM_QUERY_DUPLICATE_WX_FRIEND, request);
    }

    public JsonNode queryChatMessage(JsonNode request) {
        return invoke(MmbaApiPaths.IM_QUERY_CHAT_MESSAGE, request);
    }

    public JsonNode pushDeviceMessage(JsonNode request) {
        return invoke(MmbaApiPaths.DEVICE_MESSAGE_PUSH, request);
    }

    public JsonNode queryDeviceList(JsonNode request) {
        return invoke(MmbaApiPaths.DEVICE_LIST_QUERY, request);
    }

    public byte[] fetchFile(JsonNode request) {
        return binary(MmbaApiPaths.FILE_FETCH_FILE, request);
    }

    public byte[] fetchAsset(JsonNode request) {
        return binary(MmbaApiPaths.FILE_FETCH_ASSET, request);
    }

    private JsonNode invoke(String path, Object request) {
        MmbaCredential credential = loadCredential();
        log.info("MMBA 业务调用 path={} companyCode={} tenantId={}", path, credential.companyCode(), TenantContext.getTenantId());
        return mmbaGatewayService.invoke(credential, path, request);
    }

    private byte[] binary(String path, Object request) {
        MmbaCredential credential = loadCredential();
        log.info("MMBA 文件下载 path={} companyCode={} tenantId={}", path, credential.companyCode(), TenantContext.getTenantId());
        return mmbaGatewayService.invokeBinary(credential, path, request);
    }

    private MmbaCredential loadCredential() {
        return new MmbaCredential(apiBaseUrl, companyCode, appKey, secret);
    }

    /**
     * 短信接口按 MMBA 文档要求，data 必须是数组。
     * 当前 CRM 内部接口仍按单条对象入参接收，这里统一包装成单元素数组后再出站。
     */
    private List<JsonNode> wrapSmsData(JsonNode request) {
        return List.of(request);
    }
}
