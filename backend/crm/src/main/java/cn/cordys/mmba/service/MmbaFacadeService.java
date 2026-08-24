package cn.cordys.mmba.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.common.exception.GenericException;
import cn.cordys.context.OrganizationContext;
import cn.cordys.context.TenantContext;
import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.crm.customer.service.CustomerCallStatusService;
import cn.cordys.crm.customer.service.CustomerWechatFriendStatusService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisCustomerContextRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeStatAnalysisMapper;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeStatEventRecordService;
import cn.cordys.crm.system.notice.sse.SseService;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.mapper.ExtUserMapper;
import cn.cordys.mmba.MmbaApiPaths;
import cn.cordys.mmba.MmbaBizTypes;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.MmbaIntegrationService;
import cn.cordys.mmba.MmbaInvokeException;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.domain.MmbaRequestRecord;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.common.dto.UserDeptDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class MmbaFacadeService {

    private static final int WX_FRIEND_LIST_MAX_LIMIT = 100;
    /** 设备列表查询方式一：单次请求 ums 数量上限（MMBA 文档最多 50） */
    private static final int DEVICE_LIST_QUERY_UMS_BATCH_SIZE = 50;

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
    private MmbaPhonePreferenceService mmbaPhonePreferenceService;
    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private SseService sseService;
    @Resource
    private CustomerCallStatusService customerCallStatusService;
    @Resource
    private CustomerWechatFriendStatusService customerWechatFriendStatusService;
    @Resource
    private EmployeeStatEventRecordService employeeStatEventRecordService;
    @Resource
    private EmployeeStatAnalysisMapper employeeStatAnalysisMapper;

    /**
     * 拨打电话。
     */
    // 2026-07-14：
    //@Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JsonNode dial(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = enrichDialRequest(request, userId, organizationId);
        JsonNode response = executeJson(
                MmbaBizTypes.CALL_DIAL,
                MmbaApiPaths.PHONE_DIAL,
                payload,
                userId,
                organizationId,
                mmbaIntegrationService::dial
        );
        customerCallStatusService.markDialInitiated(readCustomerId(payload));
        return response;
    }

    /**
     * 限制电话拨打。
     */
    public JsonNode callLimit(JsonNode request, String userId, String organizationId) {
        return executeJson(MmbaBizTypes.CALL_LIMIT, MmbaApiPaths.PHONE_CALL_LIMIT, request, userId, organizationId, mmbaIntegrationService::callLimit);
    }

    /**
     * 下发通话记录清除指令，统一复用 MMBA 请求流水，确保每个 reqId 均可追溯到执行人。
     */
    public JsonNode cleanCallLog(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeRequest(request);
        enrichOperatorBizExtInfo(payload, userId, organizationId);
        return executeJson(MmbaBizTypes.CALL_LOG_CLEAN, MmbaApiPaths.PHONE_CLEAN_CALL_LOG,
                payload, userId, organizationId, mmbaIntegrationService::cleanCallLog);
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
        return executeJson(MmbaBizTypes.WX_MSG_SEND, MmbaApiPaths.IM_SEND_WX_MSG,
                enrichWxMsgRequest(request, userId, organizationId), userId, organizationId, mmbaIntegrationService::sendWxMsg);
    }

    /**
     * 添加微信好友。
     */
    public JsonNode addWxFriend(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = enrichAddWxFriendRequest(request, userId, organizationId);
        JsonNode response = executeJson(
                MmbaBizTypes.WX_FRIEND_ADD,
                MmbaApiPaths.IM_ADD_FRIEND,
                payload,
                userId,
                organizationId,
                mmbaIntegrationService::addWxFriend
        );
        customerWechatFriendStatusService.markAddInitiated(readCustomerId(payload));
        employeeStatEventRecordService.recordWechatFriendPendingEvent(JSON.toJSONString(payload.path("bizExtInfo")), System.currentTimeMillis());
        return response;
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
     * 使用设备列表查询方式一：按本地 UM 分批（每批最多 50）请求并落库。
     */
    @Async(ExecutorBeanNames.MAIN_ASYNC)
    public void syncDevices(String userId) {
        String tenantId = StringUtils.trimToNull(TenantContext.getTenantId());
        if (tenantId == null) {
            log.warn("syncDevices skip: tenantId missing userId={}", userId);
            return;
        }
        List<MmbaDevice> mmbaDevices = mmbaDeviceService.syncDevices();
        if (CollectionUtils.isEmpty(mmbaDevices)) {
            sendMmbaDeviceSyncSse(userId, tenantId, true, Translator.get("mmba_device_sync_sse_no_local"));
            return;
        }
        List<String> ums = mmbaDevices.stream().map(BaseModel::getId).filter(StringUtils::isNotBlank).toList();
        if (ums.isEmpty()) {
            sendMmbaDeviceSyncSse(userId, tenantId, true, Translator.get("mmba_device_sync_sse_no_local"));
            return;
        }
        Set<String> localUmIds = Set.copyOf(ums);
        String organizationId = OrganizationContext.getOrganizationId();
        try {
            syncDeviceListByUms(userId, organizationId, ums, localUmIds);
            sendMmbaDeviceSyncSse(userId, tenantId, true, Translator.get("mmba_device_sync_sse_done"));
        } catch (Exception e) {
            log.error("MMBA syncDevices failed userId={} tenantId={}", userId, tenantId, e);
            String err = StringUtils.defaultIfBlank(e.getMessage(), "unknown");
            sendMmbaDeviceSyncSse(userId, tenantId, false, Translator.getWithArgs("mmba_device_sync_sse_failed", err));
        }
    }

    /**
     * 方式一：按 UM 列表分批调用设备列表接口（每批最多 {@link #DEVICE_LIST_QUERY_UMS_BATCH_SIZE} 个）。
     */
    private void syncDeviceListByUms(String userId, String organizationId, List<String> ums, Set<String> localUmIds) {
        ObjectMapper mapper = JSON.MAPPER;
        int batchCount = (ums.size() + DEVICE_LIST_QUERY_UMS_BATCH_SIZE - 1) / DEVICE_LIST_QUERY_UMS_BATCH_SIZE;
        for (int i = 0; i < ums.size(); i += DEVICE_LIST_QUERY_UMS_BATCH_SIZE) {
            int batchIndex = i / DEVICE_LIST_QUERY_UMS_BATCH_SIZE + 1;
            List<String> batch = ums.subList(i, Math.min(i + DEVICE_LIST_QUERY_UMS_BATCH_SIZE, ums.size()));
            ObjectNode request = mapper.createObjectNode();
            request.set("ums", mapper.valueToTree(batch));
            JsonNode response = executeJson(MmbaBizTypes.DEVICE_LIST_QUERY, MmbaApiPaths.DEVICE_LIST_QUERY, request, userId,
                    organizationId, mmbaIntegrationService::queryDeviceList);
            syncDeviceListSnapshot(response, userId, localUmIds);
            log.info("MMBA 设备列表按UM同步 batch={}/{} size={}", batchIndex, batchCount, batch.size());
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
        Integer defaultCardSlotNum = mmbaPhonePreferenceService.getDefaultCardSlotNum(userId);
        Integer cardSlotNum = defaultCardSlotNum == null
                ? readCardSlotNum(payload, "拨打电话")
                : defaultCardSlotNum;
        if (cardSlotNum == null) {
            throw new GenericException("请先选择拨号卡或前往个人中心设置默认拨号卡");
        }
        payload.put("cardSlotNum", cardSlotNum);
        ensureCardSlotAvailable(
                um, cardSlotNum, userId, organizationId, "拨打电话", defaultCardSlotNum != null);
        String customerId = payload.path("bizExtInfo").path("customerId").asText(null);
        if (StringUtils.isNotBlank(customerId)) {
            Customer customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer == null) {
                throw new GenericException("未查找到该用户信息,请刷新页面");
            }
            if (customer != null) {
                if (StringUtils.isNotBlank(customer.getMobile())) {
                    payload.put("toPhone", customer.getMobile());
                }
                enrichBizExtInfo(payload, customer, userId, organizationId);
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
            ensureCardSlotAvailable(um, cardSlotNum, userId, organizationId, "发送短信", false);
        }
        String customerId = payload.path("bizExtInfo").path("customerId").asText(null);
        if (StringUtils.isNotBlank(customerId)) {
            Customer customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer != null) {
                if (StringUtils.isNotBlank(customer.getMobile())) {
                    payload.put("toPhone", customer.getMobile());
                }
                enrichBizExtInfo(payload, customer, userId, organizationId);
            }
        }
        return payload;
    }

    private ObjectNode enrichWxMsgRequest(JsonNode request, String userId, String organizationId) {
        ObjectNode payload = normalizeRequest(request);
        String customerId = payload.path("bizExtInfo").path("customerId").asText(null);
        if (StringUtils.isNotBlank(customerId)) {
            Customer customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer != null) {
                enrichBizExtInfo(payload, customer, userId, organizationId);
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

    private String readCustomerId(JsonNode payload) {
        return StringUtils.trimToNull(payload.path("bizExtInfo").path("customerId").asText(null));
    }

    private void ensureCardSlotAvailable(String um, int cardSlotNum, String userId, String organizationId,
                                         String actionText, boolean defaultCardSlot) {
        MmbaDevice device = mmbaDeviceService.getDevice(um);
        if (!hasCardSlot(device, cardSlotNum)) {
            if ("拨打电话".equals(actionText) && defaultCardSlot) {
                throw new GenericException("默认拨号卡不可用，请前往个人中心重新设置");
            }
            throw new GenericException("当前登录人下未配置卡槽" + cardSlotNum + "，" + actionText);
        }
    }

    private boolean hasCardSlot(MmbaDevice device, int cardSlotNum) {
        return mmbaPhonePreferenceService.isCardSlotAvailable(device, cardSlotNum);
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
            enrichBizExtInfo(payload, customer, userId, organizationId);
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

    private void enrichBizExtInfo(ObjectNode payload, Customer customer, String userId, String organizationId) {
        if (payload == null || customer == null) {
            return;
        }
        ObjectNode bizExtInfo = payload.with("bizExtInfo");
        putIfNotBlank(bizExtInfo, "organization_id", organizationId);
        putIfNotBlank(bizExtInfo, "customer_id", customer.getId());
        putIfNotBlank(bizExtInfo, "customer_name", customer.getName());
        putIfNotBlank(bizExtInfo, "customer_mobile", customer.getMobile());
        putIfNotBlank(bizExtInfo, "customer_source", loadCustomerSource(customer.getId(), organizationId));

        String ownerUserId = StringUtils.trimToNull(customer.getOwner());
        putIfNotBlank(bizExtInfo, "owner_user_id", ownerUserId);
        putIfNotBlank(bizExtInfo, "operator_user_id", userId);

        Map<String, User> userMap = loadUserMap(userId, ownerUserId);
        User operatorUser = userMap.get(userId);
        User ownerUser = ownerUserId == null ? null : userMap.get(ownerUserId);
        if (ownerUser != null) {
            putIfNotBlank(bizExtInfo, "owner_user_name", ownerUser.getName());
        }
        if (operatorUser != null) {
            putIfNotBlank(bizExtInfo, "operator_user_name", operatorUser.getName());
        }

        Map<String, UserDeptDTO> userDeptMap = loadUserDeptMap(userId, ownerUserId, organizationId);
        UserDeptDTO ownerDept = ownerUserId == null ? null : userDeptMap.get(ownerUserId);
        UserDeptDTO operatorDept = userDeptMap.get(userId);
        if (ownerDept != null) {
            putIfNotBlank(bizExtInfo, "owner_dept_id", ownerDept.getDeptId());
            putIfNotBlank(bizExtInfo, "owner_dept_name", ownerDept.getDeptName());
        }
        if (operatorDept != null) {
            putIfNotBlank(bizExtInfo, "operator_dept_id", operatorDept.getDeptId());
            putIfNotBlank(bizExtInfo, "operator_dept_name", operatorDept.getDeptName());
        }
    }

    /**
     * 按拨打电话相同的字段约定补齐操作人上下文。
     * 清除通话记录没有客户对象，因此只写拨号 bizExtInfo 中与操作人相关的公共字段。
     */
    private void enrichOperatorBizExtInfo(ObjectNode payload, String userId, String organizationId) {
        ObjectNode bizExtInfo = payload.with("bizExtInfo");
        putIfNotBlank(bizExtInfo, "organization_id", organizationId);
        putIfNotBlank(bizExtInfo, "operator_user_id", userId);

        Map<String, User> userMap = loadUserMap(userId, null);
        User operatorUser = userMap.get(userId);
        if (operatorUser != null) {
            putIfNotBlank(bizExtInfo, "operator_user_name", operatorUser.getName());
        }

        Map<String, UserDeptDTO> userDeptMap = loadUserDeptMap(userId, null, organizationId);
        UserDeptDTO operatorDept = userDeptMap.get(userId);
        if (operatorDept != null) {
            putIfNotBlank(bizExtInfo, "operator_dept_id", operatorDept.getDeptId());
            putIfNotBlank(bizExtInfo, "operator_dept_name", operatorDept.getDeptName());
        }
    }

    private String loadCustomerSource(String customerId, String organizationId) {
        if (StringUtils.isBlank(customerId) || StringUtils.isBlank(organizationId)) {
            return null;
        }
        List<EmployeeFollowAnalysisCustomerContextRow> rows =
                employeeStatAnalysisMapper.listCustomerSourceRows(organizationId, List.of(customerId));
        if (CollectionUtils.isEmpty(rows)) {
            return null;
        }
        for (EmployeeFollowAnalysisCustomerContextRow row : rows) {
            if (row != null && StringUtils.equals(customerId, row.getCustomerId())) {
                return row.getCustomerSource();
            }
        }
        return null;
    }

    private Map<String, User> loadUserMap(String operatorUserId, String ownerUserId) {
        List<String> userIds = java.util.stream.Stream.of(operatorUserId, ownerUserId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }
        List<User> users = userBaseMapper.selectByIds(userIds.toArray(new String[0]));
        if (CollectionUtils.isEmpty(users)) {
            return Map.of();
        }
        Map<String, User> userMap = new LinkedHashMap<>();
        for (User user : users) {
            if (user == null || StringUtils.isBlank(user.getId())) {
                continue;
            }
            userMap.put(user.getId(), user);
        }
        return userMap;
    }

    private Map<String, UserDeptDTO> loadUserDeptMap(String operatorUserId, String ownerUserId, String organizationId) {
        List<String> userIds = java.util.stream.Stream.of(operatorUserId, ownerUserId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(userIds) || StringUtils.isBlank(organizationId)) {
            return Map.of();
        }
        List<UserDeptDTO> userDeptList = extUserMapper.getUserDeptByUserIds(userIds, organizationId);
        if (CollectionUtils.isEmpty(userDeptList)) {
            return Map.of();
        }
        Map<String, UserDeptDTO> userDeptMap = new LinkedHashMap<>();
        for (UserDeptDTO userDept : userDeptList) {
            if (userDept == null || StringUtils.isBlank(userDept.getUserId())) {
                continue;
            }
            userDeptMap.put(userDept.getUserId(), userDept);
        }
        return userDeptMap;
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

    private void syncDeviceListSnapshot(JsonNode response, String userId, Set<String> localUmIds) {
        JsonNode dataList = response.path("data");
        if (!dataList.isArray() || dataList.isEmpty()) {
            return;
        }
        int success = 0;
        int skipped = 0;
        int notInLocal = 0;
        for (JsonNode item : dataList) {
            String um = firstNotBlank(text(item, "loginName"), text(item, "um"));
            if (StringUtils.isBlank(um)) {
                log.warn("MMBA设备列表同步缺少 um，跳过同步 item={}", JSON.toJSONString(item));
                skipped++;
                continue;
            }
            else if (!localUmIds.contains(um)) {
                notInLocal++;
                continue;
            }
            MmbaDevice device = buildDeviceFromQueryItem(item);
            device.setId(um);
            mmbaDeviceService.saveOrUpdateDevice(device, userId);
            success++;
        }
        log.info("MMBA 设备列表同步完成 success={} skipped={} notInLocal={}", success, skipped, notInLocal);
    }

    private MmbaDevice buildDeviceFromQueryItem(JsonNode item) {
        MmbaDevice device = new MmbaDevice();
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


