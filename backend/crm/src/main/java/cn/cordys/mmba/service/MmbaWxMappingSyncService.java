package cn.cordys.mmba.service;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.mmba.domain.MmbaDeviceMapping;
import cn.cordys.mmba.dto.ZzyData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 只负责维护微信账号相关的当前态映射表，不处理设备主表。
 */
@Slf4j
@Service
public class MmbaWxMappingSyncService {
    @Resource
    private MmbaDeviceService mmbaDeviceService;

    /**
     * 账号审计回调同步设备与微信账号映射。
     */
    public void syncFromAccountAudit(ZzyData data, String userId) {
        if (data == null) {
            return;
        }
        MmbaDeviceMapping mapping = buildMapping(
                data.getUm(),
                data.getStaffName(),
                toStringValue(data.getDeviceId()),
                data.getImei(),
                data.getImei2(),
                data.getIccid(),
                data.getStaffIdInApp(),
                data.getStaffImAppAccount(),
                data.getStaffMobile(),
                data.getStaffImNickName(),
                data.getStaffImAppHeaderPic(),
                data.getQq(),
                data.getTimestamp()
        );
        if (StringUtils.isBlank(mapping.getUm()) && StringUtils.isBlank(mapping.getWxid())) {
            log.warn(CrmLoggers.MMBA_CALLBACK_MARKER, "MMBA微信映射同步跳过，um 和 wxid 都为空 tenantId={} reqId={} esId={}", data.getTenantId(), data.getReqId(), data.getEsId());
            return;
        }
        log.info(CrmLoggers.MMBA_CALLBACK_MARKER, "MMBA微信映射同步 tenantId={} um={} deviceId={} imei={} wxid={}",
                data.getTenantId(), mapping.getUm(), mapping.getDeviceId(), mapping.getImei(), mapping.getWxid());
        mmbaDeviceService.saveOrUpdateMapping(mapping, userId);
    }

    /**
     * 已登录微信账号查询成功后，同步当前态映射。
     */
    public void syncFromLoginQueryResponse(JsonNode request, JsonNode response, String userId) {
        if (response == null || response.path("code").asInt(-1) != 200) {
            return;
        }
        String requestUm = request == null ? null : textValue(request, "um");
        String requestStaffName = request == null ? null : textValue(request, "staffName");
        List<JsonNode> records = extractRecords(response.path("data"));
        if (records.isEmpty()) {
            log.info("MMBA已登录微信账号查询未返回可同步的映射数据");
            return;
        }
        for (JsonNode record : records) {
            if (record == null || !record.isObject()) {
                continue;
            }
            MmbaDeviceMapping mapping = buildMapping(
                    requestUm,
                    requestStaffName,
                    textValue(record, "deviceId"),
                    textValue(record, "imei"),
                    textValue(record, "imei2"),
                    textValue(record, "iccid"),
                    firstNotBlank(textValue(record, "wxid"), textValue(record, "staffIdInApp"), textValue(record, "staffAccountId"), textValue(record, "staffAccoutId")),
                    firstNotBlank(textValue(record, "wxNo"), textValue(record, "staffImAppAccount"), textValue(record, "staffAccount"), textValue(record, "staffAccout")),
                    firstNotBlank(textValue(record, "wxPhone"), textValue(record, "staffMobile")),
                    firstNotBlank(textValue(record, "nickName"), textValue(record, "staffImNickName")),
                    firstNotBlank(textValue(record, "headerPic"), textValue(record, "staffImAppHeaderPic")),
                    textValue(record, "qq"),
                    longValue(record, "timestamp")
            );
            saveMapping(mapping, userId, textValue(record, "tenantId"), textValue(record, "reqId"), textValue(record, "esId"));
        }
    }

    /**
     * 登录登出审计仅更新现有映射状态，不新增映射。
     */
    public void syncMappingStatusFromLoginAudit(ZzyData data, String userId) {
        if (data == null) {
            return;
        }
        String um = StringUtils.trimToNull(data.getUm());
        String wxid = StringUtils.trimToNull(data.getStaffIdInApp());
        String mappingStatus = resolveMappingStatus(data.getLoginStatus());
        if (StringUtils.isBlank(um) || StringUtils.isBlank(wxid) || StringUtils.isBlank(mappingStatus)) {
            log.warn(CrmLoggers.MMBA_CALLBACK_MARKER, "MMBA微信映射状态同步跳过 tenantId={} um={} wxid={} loginStatus={}",
                    data.getTenantId(), um, wxid, data.getLoginStatus());
            return;
        }
        boolean updated = mmbaDeviceService.updateMappingStatus(um, wxid, mappingStatus, userId);
        if (!updated) {
            log.info(CrmLoggers.MMBA_CALLBACK_MARKER, "MMBA微信映射状态同步未命中现有映射 tenantId={} um={} wxid={} mappingStatus={}",
                    data.getTenantId(), um, wxid, mappingStatus);
            return;
        }
        log.info(CrmLoggers.MMBA_CALLBACK_MARKER, "MMBA微信映射状态同步 tenantId={} um={} wxid={} mappingStatus={}",
                data.getTenantId(), um, wxid, mappingStatus);
    }

    private void saveMapping(MmbaDeviceMapping mapping, String userId, String tenantId, String reqId, String esId) {
        if (mapping == null) {
            return;
        }
        if (StringUtils.isBlank(mapping.getUm()) && StringUtils.isBlank(mapping.getWxid())) {
            log.warn("MMBA微信映射同步跳过，um 和 wxid 都为空 tenantId={} reqId={} esId={}", tenantId, reqId, esId);
            return;
        }
        log.info("MMBA微信映射同步 tenantId={} um={} deviceId={} imei={} wxid={}",
                tenantId, mapping.getUm(), mapping.getDeviceId(), mapping.getImei(), mapping.getWxid());
        mmbaDeviceService.saveOrUpdateMapping(mapping, userId);
    }

    private MmbaDeviceMapping buildMapping(String um, String staffName, String deviceId,
                                           String imei, String imei2, String iccid, String wxid, String wxAccount,
                                           String wxPhone, String wxNickName, String wxHeaderPic, String qq,
                                           Long lastSyncTime) {
        MmbaDeviceMapping mapping = new MmbaDeviceMapping();
        mapping.setUm(um);
        mapping.setStaffName(staffName);
        mapping.setDeviceId(deviceId);
        mapping.setImei(imei);
        mapping.setImei2(imei2);
        mapping.setIccid(iccid);
        mapping.setWxid(wxid);
        mapping.setWxAccount(wxAccount);
        mapping.setWxPhone(wxPhone);
        mapping.setWxNickName(wxNickName);
        mapping.setWxHeaderPic(wxHeaderPic);
        mapping.setQq(qq);
        mapping.setMappingStatus("ACTIVE");
        mapping.setLastSyncTime(lastSyncTime == null ? System.currentTimeMillis() : lastSyncTime);
        return mapping;
    }

    private List<JsonNode> extractRecords(JsonNode dataNode) {
        List<JsonNode> records = new ArrayList<>();
        if (dataNode == null || dataNode.isNull()) {
            return records;
        }
        if (dataNode.isArray()) {
            dataNode.forEach(records::add);
            return records;
        }
        if (!dataNode.isObject()) {
            return records;
        }
        JsonNode listNode = firstContainer(dataNode, "wechatAccount", "list", "rows", "records", "result", "items");
        if (listNode != null && listNode.isArray()) {
            listNode.forEach(records::add);
            return records;
        }
        records.add(dataNode);
        return records;
    }

    private JsonNode firstContainer(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode child = node.get(fieldName);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText(null);
        return StringUtils.isBlank(value) ? null : value;
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isNumber()) {
            return valueNode.longValue();
        }
        String value = valueNode.asText(null);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveMappingStatus(String loginStatus) {
        if ("1".equals(loginStatus)) {
            return "ACTIVE";
        }
        if ("2".equals(loginStatus)) {
            return "INACTIVE";
        }
        return null;
    }
}
