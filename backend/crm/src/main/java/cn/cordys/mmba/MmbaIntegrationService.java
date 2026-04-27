package cn.cordys.mmba;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 对外暴露的 MMBA 业务能力（仅对接需求范围内的接口）。
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

    public JsonNode sendSms(JsonNode request) {
        return invoke(MmbaApiPaths.PHONE_SEND_MSG, request);
    }

    public JsonNode sendWxMsg(JsonNode request) {
        return invoke(MmbaApiPaths.IM_SEND_WX_MSG, request);
    }

    public JsonNode addWxFriend(JsonNode request) {
        return invoke(MmbaApiPaths.IM_ADD_FRIEND, request);
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

    public byte[] fetchFile(JsonNode request) {
        return binary(MmbaApiPaths.FILE_FETCH_FILE, request);
    }

    public byte[] fetchAsset(JsonNode request) {
        return binary(MmbaApiPaths.FILE_FETCH_ASSET, request);
    }

    private JsonNode invoke(String path, JsonNode request) {
        MmbaCredential credential = loadCredential();
        log.info("MMBA 业务调用 path={} companyCode={}", path, credential.companyCode());
        return mmbaGatewayService.invoke(credential, path, request);
    }

    private byte[] binary(String path, JsonNode request) {
        MmbaCredential credential = loadCredential();
        log.info("MMBA 文件下载 path={} companyCode={}", path, credential.companyCode());
        return mmbaGatewayService.invokeBinary(credential, path, request);
    }

    private MmbaCredential loadCredential() {
        return new MmbaCredential(apiBaseUrl, companyCode, appKey, secret);
    }
}
