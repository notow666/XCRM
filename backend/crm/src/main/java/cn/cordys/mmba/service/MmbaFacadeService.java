package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.domain.User;
import cn.cordys.mmba.MmbaApiPaths;
import cn.cordys.mmba.MmbaBizTypes;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.MmbaIntegrationService;
import cn.cordys.mmba.MmbaInvokeException;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.domain.MmbaRequestRecord;
import cn.cordys.mybatis.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MMBA 对内门面服务。
 * 统一负责：
 * 1. 生成 reqId
 * 2. 记录请求流水
 * 3. 调用底层 MMBA 接口
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaFacadeService {

    private static final int WX_FRIEND_LIST_MAX_LIMIT = 100;

    @Value("${mmba.company-code:}")
    private String companyCode;
    @Resource
    private MmbaIntegrationService mmbaIntegrationService;
    @Resource
    private MmbaRequestRecordService mmbaRequestRecordService;
    @Resource
    private MmbaWxMappingSyncService mmbaWxMappingSyncService;
    @Resource
    private MmbaDeviceService mmbaDeviceService;
    @Resource
    private BaseMapper<User> userBaseMapper;

    /**
     * 拨打电话。
     */
    public JsonNode dial(JsonNode request, String userId, String organizationId) {
        return executeJson(
                MmbaBizTypes.CALL_DIAL,
                MmbaApiPaths.PHONE_DIAL,
                enrichDialRequest(request, userId),
                userId,
                organizationId,
                mmbaIntegrationService::dial
        );
    }

    /**
     * 限制电话拨打。
     */
    public JsonNode callLimit(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.CALL_LIMIT, MmbaApiPaths.PHONE_CALL_LIMIT, request, userId, organizationId, mmbaIntegrationService::callLimit);
    }

    /**
     * 发送短信。
     */
    public JsonNode sendSms(JsonNode request, String userId, String organizationId) {
        return executeJson(
                MmbaBizTypes.SMS_SEND,
                MmbaApiPaths.PHONE_SEND_MSG,
                enrichSmsRequest(request, userId),
                userId,
                organizationId,
                mmbaIntegrationService::sendSms
        );
    }

    /**
     * 发送微信消息。
     */
    public JsonNode sendWxMsg(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.WX_MSG_SEND, MmbaApiPaths.IM_SEND_WX_MSG, request, userId, organizationId, mmbaIntegrationService::sendWxMsg);
    }

    /**
     * 添加微信好友。
     */
    public JsonNode addWxFriend(JsonNode request, String userId, String organizationId) {
        return executeJson(
                MmbaBizTypes.WX_FRIEND_ADD,
                MmbaApiPaths.IM_ADD_FRIEND,
                enrichAddWxFriendRequest(request, userId),
                userId,
                organizationId,
                mmbaIntegrationService::addWxFriend
        );
    }

    /**
     * 发送朋友圈。
     */
    public JsonNode sendWxMoment(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.WX_SEND_MOMENT, MmbaApiPaths.IM_SEND_WX_MOMENT, request, userId, organizationId, mmbaIntegrationService::sendWxMoment);
    }

    /**
     * 修改微信好友备注或描述。
     */
    public JsonNode modifyWxFriendRemark(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.WX_FRIEND_REMARK_MODIFY, MmbaApiPaths.IM_MODIFY_WX_FRIEND_REMARK, request, userId, organizationId, mmbaIntegrationService::modifyWxFriendRemark);
    }

    /**
     * 查询已登录微信账号。
     */
    public JsonNode queryLoginWxAccount(JsonNode request, String userId, String organizationId) {
        JsonNode response = executeJson(MmbaBizTypes.WX_LOGIN_ACCOUNT_QUERY, MmbaApiPaths.IM_QUERY_LOGIN_WX_ACCOUNT, request, userId, organizationId, mmbaIntegrationService::queryLoginWxAccount);
        mmbaWxMappingSyncService.syncFromLoginQueryResponse(request, response, userId);
        return response;
    }

    /**
     * 查询微信好友列表。
     */
    public JsonNode queryWxFriendList(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeRequest(request);
        enforceWxFriendListLimit(payload);
        return executeJson(MmbaBizTypes.WX_FRIEND_LIST_QUERY, MmbaApiPaths.IM_QUERY_WX_FRIEND_LIST, payload, userId, organizationId, mmbaIntegrationService::queryWxFriendList);
    }

    /**
     * 查询重复微信好友。
     */
    public JsonNode queryDuplicateWxFriend(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.WX_DUPLICATE_FRIEND_QUERY, MmbaApiPaths.IM_QUERY_DUPLICATE_WX_FRIEND, request, userId, organizationId, mmbaIntegrationService::queryDuplicateWxFriend);
    }

    /**
     * 查询微信聊天记录。
     */
    public JsonNode queryChatMessage(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.WX_CHAT_QUERY, MmbaApiPaths.IM_QUERY_CHAT_MESSAGE, request, userId, organizationId, mmbaIntegrationService::queryChatMessage);
    }

    /**
     * 设备消息推送。
     */
    public JsonNode pushDeviceMessage(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.DEVICE_PUSH, MmbaApiPaths.DEVICE_MESSAGE_PUSH, request, userId, organizationId, mmbaIntegrationService::pushDeviceMessage);
    }

    public JsonNode queryDeviceList(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeDeviceListRequest(request);
        JsonNode response = executeJson(MmbaBizTypes.DEVICE_LIST_QUERY, MmbaApiPaths.DEVICE_LIST_QUERY, payload, userId, organizationId, mmbaIntegrationService::queryDeviceList);
        syncDeviceListSnapshot(response, userId);
        return response;
    }

    /**
     * 下载 MMBA 文件。
     */
    public byte[] fetchFile(JsonNode request, String userId, String organizationId) {
        return executeBinary(MmbaBizTypes.FILE_FETCH, MmbaApiPaths.FILE_FETCH_FILE, request, userId, organizationId, mmbaIntegrationService::fetchFile);
    }

    /**
     * 实时下载 MMBA 媒体文件流，用于页面预览，避免提前落本地文件。
     */
    public byte[] fetchAssetBinary(JsonNode request, String userId, String organizationId) {
        return executeBinary(MmbaBizTypes.ASSET_FETCH, MmbaApiPaths.FILE_FETCH_ASSET, request, userId, organizationId, mmbaIntegrationService::fetchAsset);
    }

    /**
     * 所有 JSON 接口统一走这里。
     */
    private JsonNode executeJson(String bizType, String mmbaUrl, JsonNode request, String userId, String organizationId,
                                 Function<JsonNode, JsonNode> action) {
        ObjectNode payload = normalizeRequest(request);
        String reqId = ensureReqId(payload);
        String tenantId = TenantContext.getTenantId();
        MmbaRequestRecord record = initRequestRecord(bizType, mmbaUrl, reqId, organizationId, userId, payload);
        log.info("MMBA 请求开始 bizType={} reqId={} tenantId={} organizationId={} url={}", bizType, reqId, tenantId, organizationId, mmbaUrl);
        try {
            JsonNode response = action.apply(payload);
            fillSuccess(record, response, userId);
            log.info("MMBA 请求成功 bizType={} reqId={} tenantId={} organizationId={} code={} message={} traceId={}",
                    bizType, reqId, tenantId, organizationId, record.getResponseCode(), record.getResponseMessage(), record.getTraceId());
            return response;
        } catch (Exception e) {
            fillFailed(record, e, userId);
            log.error("MMBA 请求失败 bizType={} reqId={} tenantId={} organizationId={} url={} message={}",
                    bizType, reqId, tenantId, organizationId, mmbaUrl, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 普通二进制下载接口统一走这里。
     */
    private byte[] executeBinary(String bizType, String mmbaUrl, JsonNode request, String userId, String organizationId,
                                 Function<JsonNode, byte[]> action) {
        ObjectNode payload = normalizeRequest(request);
        String reqId = ensureReqId(payload);
        String tenantId = TenantContext.getTenantId();
        MmbaRequestRecord record = initRequestRecord(bizType, mmbaUrl, reqId, organizationId, userId, payload);
        log.info("MMBA 二进制请求开始 bizType={} reqId={} tenantId={} organizationId={} url={}", bizType, reqId, tenantId, organizationId, mmbaUrl);
        try {
            byte[] response = action.apply(payload);
            record.setResponseCode(200);
            record.setResponseMessage("BINARY");
            record.setRequestStatus(MmbaConstants.REQUEST_STATUS_SUCCESS);
            record.setRawResult(response == null ? null : ("binary:" + response.length));
            mmbaRequestRecordService.update(record, userId);
            log.info("MMBA 二进制请求成功 bizType={} reqId={} tenantId={} organizationId={} size={}",
                    bizType, reqId, tenantId, organizationId, response == null ? null : response.length);
            return response;
        } catch (Exception e) {
            fillFailed(record, e, userId);
            log.error("MMBA 二进制请求失败 bizType={} reqId={} tenantId={} organizationId={} url={} message={}",
                    bizType, reqId, tenantId, organizationId, mmbaUrl, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 初始化请求流水。
     */
    private MmbaRequestRecord initRequestRecord(String bizType, String mmbaUrl, String reqId, String organizationId, String userId, ObjectNode payload) {
        MmbaRequestRecord record = new MmbaRequestRecord();
        record.setBizType(bizType);
        record.setReqId(reqId);
        record.setMmbaUrl(mmbaUrl);
        record.setRequestHeaders(buildRequestHeaders(organizationId));
        record.setRequestBody(JSON.toJSONString(payload));
        record.setRequestStatus(MmbaConstants.REQUEST_STATUS_INIT);
        return mmbaRequestRecordService.init(record, userId);
    }

    /**
     * 记录本次 MMBA 调用的请求头快照，便于后续联调排查。
     */
    private String buildRequestHeaders(String organizationId) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Api-Info", companyCode);
        headers.put("X-Tenant-ID", TenantContext.getTenantId());
        headers.put("Organization-Id", organizationId);
        return JSON.toJSONString(headers);
    }

    /**
     * 回填同步成功结果。
     */
    private void fillSuccess(MmbaRequestRecord record, JsonNode response, String userId) {
        record.setTraceId(response.path("traceId").asText(null));
        record.setResponseBody(JSON.toJSONString(response));
        record.setResponseCode(response.path("code").asInt());
        record.setResponseMessage(response.path("message").asText(null));
        record.setRequestStatus(MmbaConstants.REQUEST_STATUS_SUCCESS);
        record.setRawResult(JSON.toJSONString(response));
        mmbaRequestRecordService.update(record, userId);
    }

    /**
     * 回填同步失败结果。
     * 优先记录 MMBA 原始响应，其次再退回到本地异常信息。
     */
    private void fillFailed(MmbaRequestRecord record, Exception e, String userId) {
        MmbaInvokeException invokeException = unwrapMmbaInvokeException(e);
        if (invokeException != null) {
            record.setTraceId(invokeException.getTraceId());
            record.setResponseBody(invokeException.getResponseBody());
            record.setResponseCode(invokeException.getResponseCode() == null ? -1 : invokeException.getResponseCode());
            record.setResponseMessage(invokeException.getMessage());
            record.setRawResult(invokeException.getRawResult());
        } else {
            record.setResponseCode(-1);
            record.setResponseMessage(e.getMessage());
            record.setRawResult(e.getClass().getName());
        }
        record.setRequestStatus(MmbaConstants.REQUEST_STATUS_FAILED);
        mmbaRequestRecordService.update(record, userId);
    }

    /**
     * 从异常链中提取 MMBA 调用异常，便于完整记录下游原始响应。
     */
    private MmbaInvokeException unwrapMmbaInvokeException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MmbaInvokeException invokeException) {
                return invokeException;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 统一转成可修改的 ObjectNode，方便回填 reqId。
     */
    private ObjectNode normalizeRequest(JsonNode request) {
        if (request instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return JSON.parseObject(JSON.toJSONString(request), ObjectNode.class);
    }

    private ObjectNode enrichDialRequest(JsonNode request, String userId) {
        ObjectNode payload = normalizeRequest(request);
        if (StringUtils.isNotBlank(payload.path("um").asText(null))) {
            return payload;
        }
        User user = userBaseMapper.selectByPrimaryKey(userId);
        String um = user == null ? null : StringUtils.trimToNull(user.getUm());
        if (StringUtils.isBlank(um)) {
            throw new IllegalArgumentException("\u5f53\u524d\u767b\u5f55\u4eba\u672a\u914d\u7f6eUM\uff0c\u65e0\u6cd5\u62e8\u6253\u7535\u8bdd");
        }
        payload.put("um", um);
        return payload;
    }

    private ObjectNode enrichSmsRequest(JsonNode request, String userId) {
        ObjectNode payload = normalizeRequest(request);
        if (StringUtils.isNotBlank(payload.path("um").asText(null))) {
            return payload;
        }
        User user = userBaseMapper.selectByPrimaryKey(userId);
        String um = user == null ? null : StringUtils.trimToNull(user.getUm());
        if (StringUtils.isBlank(um)) {
            throw new IllegalArgumentException("当前登录人未配置UM，无法发送短信");
        }
        payload.put("um", um);
        return payload;
    }

    private ObjectNode enrichAddWxFriendRequest(JsonNode request, String userId) {
        ObjectNode payload = normalizeRequest(request);
        if (StringUtils.isBlank(payload.path("friendSearch").asText(null))) {
            String friendPhone = StringUtils.trimToNull(payload.path("friendPhone").asText(null));
            if (StringUtils.isNotBlank(friendPhone)) {
                payload.put("friendSearch", friendPhone);
            }
        }
        if (StringUtils.isNotBlank(payload.path("um").asText(null))) {
            return payload;
        }
        User user = userBaseMapper.selectByPrimaryKey(userId);
        String um = user == null ? null : StringUtils.trimToNull(user.getUm());
        if (StringUtils.isBlank(um)) {
            throw new IllegalArgumentException("当前登录人未配置UM，无法添加微信好友");
        }
        payload.put("um", um);
        return payload;
    }

    /**
     * reqId 固定由接入层统一生成，并覆盖到发给 MMBA 的请求体中。
     */
    private String ensureReqId(ObjectNode payload) {
        String reqId = IDGenerator.nextStr();
        payload.put("reqId", reqId);
        return reqId;
    }

    /**
     * 微信好友列表单次最多查询 100 条，避免调用方过量查询压垮 MMBA 文档中提示的 ES 查询链路。
     */
    private void enforceWxFriendListLimit(ObjectNode payload) {
        JsonNode limitNode = payload.get("limit");
        if (limitNode == null || limitNode.isNull()) {
            payload.put("limit", WX_FRIEND_LIST_MAX_LIMIT);
            log.info("MMBA 微信好友列表未传 limit，使用默认值 {}", WX_FRIEND_LIST_MAX_LIMIT);
            return;
        }
        if (!limitNode.canConvertToInt()) {
            throw new IllegalArgumentException("MMBA 微信好友列表参数 limit 必须为整数");
        }
        int limit = limitNode.asInt();
        if (limit <= 0) {
            throw new IllegalArgumentException("MMBA 微信好友列表参数 limit 必须大于 0");
        }
        if (limit > WX_FRIEND_LIST_MAX_LIMIT) {
            throw new IllegalArgumentException("MMBA 微信好友列表参数 limit 不能超过 100");
        }
    }

    /**
     * 设备列表查询兼容两种 CRM 内部入参：
     * 1. 直接传业务字段：{"ums":[...]} 或 {"limit":500,"cursor":"..."}
     * 2. 历史包一层 data：{"data":{"ums":[...]}} 或 {"data":{"limit":500,"cursor":"..."}}
     * 统一摊平成 MMBA 业务 data 体，避免再次被网关包装后出现 data.data。
     */
    private ObjectNode normalizeDeviceListRequest(JsonNode request) {
        ObjectNode payload = normalizeRequest(request);
        JsonNode dataNode = payload.get("data");
        if (!(dataNode instanceof ObjectNode dataObject) || !shouldUnwrapDeviceListData(payload, dataObject)) {
            return payload;
        }
        ObjectNode normalized = dataObject.deepCopy();
        JsonNode reqId = payload.get("reqId");
        if (reqId != null && !reqId.isNull()) {
            normalized.set("reqId", reqId.deepCopy());
        }
        return normalized;
    }

    private boolean shouldUnwrapDeviceListData(ObjectNode payload, ObjectNode dataObject) {
        if (payload.size() == 1) {
            return true;
        }
        if (payload.size() != 2 || !payload.has("reqId")) {
            return false;
        }
        return dataObject.has("ums") || dataObject.has("limit") || dataObject.has("cursor");
    }

    private void syncDeviceListSnapshot(JsonNode response, String userId) {
        JsonNode dataList = response.path("data");
        if (!dataList.isArray() || dataList.isEmpty()) {
            return;
        }
        int success = 0;
        int skipped = 0;
        for (JsonNode item : dataList) {
            MmbaDevice device = buildDeviceFromQueryItem(item);
            if (device == null) {
                skipped++;
                continue;
            }
            mmbaDeviceService.saveOrUpdateDevice(device, userId);
            success++;
        }
        log.info("MMBA 设备列表同步完成 success={} skipped={}", success, skipped);
    }

    private MmbaDevice buildDeviceFromQueryItem(JsonNode item) {
        String deviceId = text(item, "deviceId");
        if (StringUtils.isBlank(deviceId)) {
            log.warn("MMBA 设备列表项缺少 deviceId，跳过同步 item={}", JSON.toJSONString(item));
            return null;
        }
        MmbaDevice device = new MmbaDevice();
        device.setDeviceId(deviceId);
        device.setDeviceName(text(item, "deviceName"));
        device.setDeviceType(text(item, "deviceType"));
        device.setDeviceStatus(intValue(item, "deviceStatus"));
        device.setImei(firstNotBlank(text(item, "imei1"), text(item, "imei")));
        device.setImei2(text(item, "imei2"));
        device.setIccid(firstNotBlank(text(item, "iccid1"), text(item, "iccid")));
        device.setIccid2(text(item, "iccid2"));
        device.setPhone(firstNotBlank(text(item, "phone1"), text(item, "phone")));
        device.setPhone2(text(item, "phone2"));
        device.setUm(firstNotBlank(text(item, "loginName"), text(item, "um")));
        device.setStaffName(firstNotBlank(text(item, "name"), text(item, "staffName")));
        device.setOrgName(text(item, "orgName"));
        device.setOrgNames(text(item, "orgNames"));
        device.setLastOnline(longValue(item, "lastOnline"));
        device.setLastOnlineTime(text(item, "lastOnlineTime"));
        device.setLoginStatus(intValue(item, "loginStatus"));
        device.setLastAuditTime(device.getLastOnline());
        device.setRawData(JSON.toJSONString(item));
        return device;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.isBlank(text) ? null : text;
    }

    private Integer intValue(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longValue(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private void putIfNotBlank(ObjectNode payload, String fieldName, String value) {
        if (StringUtils.isNotBlank(value)) {
            payload.put(fieldName, value);
        }
    }
}


