package cn.cordys.mmba.service;

import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.common.exception.GenericException;
import cn.cordys.context.OrganizationContext;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.notice.sse.SsePrincipalKind;
import cn.cordys.crm.system.notice.sse.SseService;
import cn.cordys.crm.customer.domain.Customer;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.List;
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
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private SseService sseService;

    /**
     * 拨打电话。
     */
    public JsonNode dial(JsonNode request, String userId, String organizationId) {
        return executeJson(
                MmbaBizTypes.CALL_DIAL,
                MmbaApiPaths.PHONE_DIAL,
                enrichDialRequest(request, userId, organizationId),
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
                enrichSmsRequest(request, userId, organizationId),
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
                enrichAddWxFriendRequest(request, userId, organizationId),
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

    /**
     * 查询设备并同步（异步执行，结束后通过 SSE 推送 {@link MmbaConstants#SSE_EVENT_DEVICE_SYNC}）。
     */
    @Async("threadPoolTaskExecutor")
    public void syncDevices(String userId) {
        String tenantId = StringUtils.trimToNull(TenantContext.getTenantId());
        if (tenantId == null) {
            log.warn("syncDevices skip: tenantId missing userId={}", userId);
            return;
        }
        try {
            List<MmbaDevice> mmbaDevices = mmbaDeviceService.syncDevices();
            if (CollectionUtils.isEmpty(mmbaDevices)) {
                sendMmbaDeviceSyncSse(userId, tenantId, true, Translator.get("mmba_device_sync_sse_no_local"));
                return;
            }
            List<String> ums = mmbaDevices.stream().map(BaseModel::getId).toList();
            ObjectMapper mapper = JSON.MAPPER;
            ObjectNode objectNode = mapper.createObjectNode();
            objectNode.set("ums", mapper.valueToTree(ums));
            JsonNode response = executeJson(MmbaBizTypes.DEVICE_LIST_QUERY, MmbaApiPaths.DEVICE_LIST_QUERY, objectNode, userId,
                    OrganizationContext.getOrganizationId(), mmbaIntegrationService::queryDeviceList);
            syncDeviceListSnapshot(response, userId);
            sendMmbaDeviceSyncSse(userId, tenantId, true, Translator.get("mmba_device_sync_sse_done"));
        } catch (Exception e) {
            log.error("MMBA syncDevices failed userId={} tenantId={}", userId, tenantId, e);
            String err = StringUtils.defaultIfBlank(e.getMessage(), "unknown");
            sendMmbaDeviceSyncSse(userId, tenantId, false, Translator.getWithArgs("mmba_device_sync_sse_failed", err));
        }
    }

    private void sendMmbaDeviceSyncSse(String userId, String tenantId, boolean success, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", MmbaConstants.SSE_EVENT_DEVICE_SYNC);
        payload.put("success", success);
        payload.put("message", message);
        sseService.sendToPrincipal(SsePrincipalKind.TENANT, tenantId, userId, payload);
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
        headers.put("X-Tenant-ID", TenantContext.requireTenantId());
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

    private ObjectNode enrichDialRequest(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeRequest(request);
        String um = payload.path("um").asText(null);
        if (StringUtils.isBlank(um)) {
            um = requireCurrentUserUm(userId, "无法拨打电话");
            payload.put("um", um);
        }
        Integer cardSlotNum = readCardSlotNum(payload, "拨打电话");
        if (cardSlotNum != null) {
            ensureCardSlotAvailable(um, cardSlotNum, userId, organizationId, "拨打电话");
        }
        String customerId = payload.path("bizExtInfo").path("customerId").asText(null);
        if (StringUtils.isNotBlank(customerId)) {
            Customer customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer != null && StringUtils.isNotBlank(customer.getMobile())) {
                payload.put("toPhone", customer.getMobile());
            }
        }
        return payload;
    }

    private ObjectNode enrichSmsRequest(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeRequest(request);
        String um = payload.path("um").asText(null);
        if (StringUtils.isBlank(um)) {
            um = requireCurrentUserUm(userId, "无法发送短信");
            payload.put("um", um);
        }
        Integer cardSlotNum = readCardSlotNum(payload, "发送短信");
        if (cardSlotNum != null) {
            ensureCardSlotAvailable(um, cardSlotNum, userId, organizationId, "发送短信");
        }
        String customerId = payload.path("bizExtInfo").path("customerId").asText(null);
        if (StringUtils.isNotBlank(customerId)) {
            Customer customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer != null && StringUtils.isNotBlank(customer.getMobile())) {
                payload.put("toPhone", customer.getMobile());
            }
        }
        return payload;
    }

    private String requireCurrentUserUm(String userId, String actionText) {
        User user = userBaseMapper.selectByPrimaryKey(userId);
        String um = user == null ? null : StringUtils.trimToNull(user.getUm());
        if (StringUtils.isBlank(um)) {
            throw new GenericException("当前登录人未配置UM，" + actionText);
        }
        return um;
    }

    private Integer readCardSlotNum(ObjectNode payload, String actionText) {
        JsonNode cardSlotNode = payload.get("cardSlotNum");
        if (cardSlotNode == null || cardSlotNode.isNull()) {
            return null;
        }
        if (!cardSlotNode.canConvertToInt()) {
            throw new GenericException(actionText + "时卡槽参数无效");
        }
        int cardSlotNum = cardSlotNode.asInt();
        if (cardSlotNum != 1 && cardSlotNum != 2) {
            throw new GenericException(actionText + "时卡槽参数无效");
        }
        return cardSlotNum;
    }

    private void ensureCardSlotAvailable(String um, int cardSlotNum, String userId, String organizationId, String actionText) {
        MmbaDevice device = mmbaDeviceService.getDevice(um);
        if (!hasCardSlot(device, cardSlotNum)) {
            throw new GenericException("当前登录人下未配置卡槽" + cardSlotNum + "，" + actionText);
        }
    }

    private boolean hasCardSlot(MmbaDevice device, int cardSlotNum) {
        if (device == null) {
            return false;
        }
        if (cardSlotNum == 1) {
            return StringUtils.isNotBlank(device.getPhone()) || StringUtils.isNotBlank(device.getIccid());
        }
        return StringUtils.isNotBlank(device.getPhone2()) || StringUtils.isNotBlank(device.getIccid2());
    }

    private ObjectNode enrichAddWxFriendRequest(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeRequest(request);
        String customerId = payload.path("bizExtInfo").path("customerId").asText(null);
        if (StringUtils.isNotBlank(customerId)) {
            Customer customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer != null && StringUtils.isNotBlank(customer.getMobile())) {
                payload.put("friendPhone", customer.getMobile());
                payload.put("friendSearch", customer.getMobile());
            }
        }
        if (StringUtils.isBlank(payload.path("friendSearch").asText(null))) {
            String friendPhone = StringUtils.trimToNull(payload.path("friendPhone").asText(null));
            if (StringUtils.isNotBlank(friendPhone)) {
                payload.put("friendSearch", friendPhone);
            }
        }
        if (StringUtils.isNotBlank(payload.path("um").asText(null))) {
            return payload;
        }
        String um = requireCurrentUserUm(userId, "无法添加微信好友");
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
        JsonNode dataNode = request.get("data");
        if (!(dataNode instanceof ObjectNode dataObject) || !shouldUnwrapDeviceListData(request, dataObject)) {
            return normalizeRequest(request);
        }
        ObjectNode normalized = dataObject.deepCopy();
        JsonNode reqId = request.get("reqId");
        if (reqId != null && !reqId.isNull()) {
            normalized.set("reqId", reqId.deepCopy());
        }
        return normalized;
    }

    private boolean shouldUnwrapDeviceListData(JsonNode payload, ObjectNode dataObject) {
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
        String um = firstNotBlank(text(item, "loginName"), text(item, "um"));
        if (StringUtils.isBlank(um)) {
            log.warn("MMBA设备列表同步缺少 um，跳过同步 item={}", JSON.toJSONString(item));
            return null;
        }
        MmbaDevice device = new MmbaDevice();
        device.setId(um);
        device.setDeviceId(text(item, "deviceId"));
        device.setDeviceName(text(item, "deviceName"));
        device.setDeviceType(text(item, "deviceType"));
        device.setDeviceStatus(intValue(item, "deviceStatus"));
        device.setImei(text(item, "imei1"));
        device.setImei2(text(item, "imei2"));
        device.setIccid(text(item, "iccid1"));
        device.setIccid2(text(item, "iccid2"));
        device.setPhone(text(item, "phone1"));
        device.setPhone2(text(item, "phone2"));
        device.setStaffName(text(item, "name"));
        device.setOrgName(text(item, "orgName"));
        device.setOrgNames(text(item, "orgNames"));
        device.setLastOnline(longValue(item, "lastOnline"));
        device.setLastOnlineTime(text(item, "lastOnlineTime"));
        device.setLoginStatus(intValue(item, "loginStatus"));
        device.setLastAuditTime(System.currentTimeMillis());
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


