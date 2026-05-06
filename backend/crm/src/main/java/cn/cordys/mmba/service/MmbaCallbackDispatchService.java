package cn.cordys.mmba.service;

import cn.cordys.common.util.JSON;
import cn.cordys.mmba.MmbaBehaviorTypes;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaCallbackRecord;
import cn.cordys.mmba.domain.MmbaCommandResult;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.domain.MmbaDeviceInfoAudit;
import cn.cordys.mmba.domain.MmbaDeviceMapping;
import cn.cordys.mmba.domain.MmbaDeviceStatusAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxAccountAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mmba.domain.MmbaWxFriendChangeAudit;
import cn.cordys.mmba.domain.MmbaWxFriendListAudit;
import cn.cordys.mmba.domain.MmbaWxLoginAudit;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

/**
 * MMBA 回调分发服务。
 * 负责把统一回调 DTO 拆成各业务表实体，并调用对应的持久化服务做幂等保存。
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaCallbackDispatchService {

    @Resource
    private MmbaAuditPersistenceService mmbaAuditPersistenceService;
    @Resource
    private MmbaCommandResultService mmbaCommandResultService;
    @Resource
    private MmbaDeviceService mmbaDeviceService;
    @Resource
    private MmbaWxMappingSyncService mmbaWxMappingSyncService;
    @Resource
    private MmbaFacadeService mmbaFacadeService;

    /**
     * 审计类回调分发。
     */
    public void dispatchAudit(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord) {
        for (ZzyData data : dto.getData()) {
            log.info("MMBA审计回调分发 callbackRecordId={} behaviorType={} reqId={} esId={} tenantId={}",
                    callbackRecord.getId(), dto.getBehaviorType(), data.getReqId(), data.getEsId(), data.getTenantId());
            switch (dto.getBehaviorType()) {
                case MmbaBehaviorTypes.CALL_RECORD_AUDIT -> {
                    MmbaCallRecordAudit callAudit = mmbaAuditPersistenceService.saveOrUpdateCallAudit(
                            buildCallAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER
                    );
                    mmbaFacadeService.downloadCallRecordAsset(callAudit, MmbaConstants.SYSTEM_USER);
                }
                case MmbaBehaviorTypes.SMS_RECORD_AUDIT -> mmbaAuditPersistenceService.saveOrUpdateSmsAudit(buildSmsAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                case MmbaBehaviorTypes.WX_CHAT_AUDIT -> mmbaAuditPersistenceService.saveOrUpdateWxChatAudit(buildWxChatAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                case MmbaBehaviorTypes.WX_FRIEND_CHANGE_AUDIT -> mmbaAuditPersistenceService.saveOrUpdateWxFriendChangeAudit(buildWxFriendChangeAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                case MmbaBehaviorTypes.WX_FRIEND_LIST_AUDIT -> mmbaAuditPersistenceService.saveOrUpdateWxFriendListAudit(buildWxFriendListAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                case MmbaBehaviorTypes.WX_ACCOUNT_AUDIT -> {
                    mmbaAuditPersistenceService.saveOrUpdateWxAccountAudit(buildWxAccountAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                    mmbaWxMappingSyncService.syncFromAccountAudit(data, MmbaConstants.SYSTEM_USER);
                }
                case MmbaBehaviorTypes.DEVICE_INFO_AUDIT -> {
                    mmbaAuditPersistenceService.saveOrUpdateDeviceInfoAudit(buildDeviceInfoAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                    syncDeviceSnapshot(data, dto.getBehaviorType());
                }
                case MmbaBehaviorTypes.DEVICE_STATUS_AUDIT -> {
                    mmbaAuditPersistenceService.saveOrUpdateDeviceStatusAudit(buildDeviceStatusAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                    syncDeviceSnapshot(data, dto.getBehaviorType());
                }
                case MmbaBehaviorTypes.WX_LOGIN_LOGOUT_AUDIT -> {
                    mmbaAuditPersistenceService.saveOrUpdateWxLoginAudit(buildWxLoginAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                    mmbaWxMappingSyncService.syncMappingStatusFromLoginAudit(data, MmbaConstants.SYSTEM_USER);
                }
                default -> {
                }
            }
        }
    }

    /**
     * 指令结果类回调分发。
     */
    public void dispatchCommand(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord) {
        for (ZzyData data : dto.getData()) {
            log.info("MMBA结果回调分发 callbackRecordId={} behaviorType={} reqId={} tenantId={}",
                    callbackRecord.getId(), dto.getBehaviorType(), data.getReqId(), data.getTenantId());
            mmbaCommandResultService.saveOrUpdate(buildCommandResult(dto.getBehaviorType(), data, callbackRecord), MmbaConstants.SYSTEM_USER);
        }
    }

    /**
     * 组装通话审计实体。
     * 这一类数据后续最常用于通话留痕、录音回放和与外呼请求闭环。
     */
    private MmbaCallRecordAudit buildCallAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaCallRecordAudit record = new MmbaCallRecordAudit();
        fillCommon(record, data, callbackRecord);
        record.setEsId(data.getEsId());
        record.setReqId(data.getReqId());
        record.setAnswerTime(data.getAnswerTime());
        record.setAnswerTimestamp(toLong(data.getAnswerTimestamp()));
        record.setBeginTime(data.getBeginTime());
        record.setBeginTimestamp(toLong(data.getBeginTimestamp()));
        record.setEndTime(data.getEndTime());
        record.setEndTimestamp(toLong(data.getEndTimestamp()));
        record.setCustomer(data.getCustomer());
        record.setCustomerTel(data.getCustomerTel());
        record.setCustomerId(resolveCustomerId(data.getBizExtInfo()));
        record.setDirection(toInteger(data.getDirection()));
        record.setDuration(toInteger(data.getDuration()));
        record.setRecord(data.getRecord());
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        record.setBizExtInfo(data.getBizExtInfo());
        record.setIccid(data.getIccid());
        record.setIccidPhone(data.getIccidPhone());
        record.setMobileVendor(toInteger(data.getMobileVendor()));
        record.setIsConnected(data.getIsConnected());
        record.setCallType(toInteger(data.getCallType()));
        record.setSoundChannel(toInteger(data.getSoundChannel()));
        record.setCallStatus(data.getCallStatus());
        record.setPhoneLocation(data.getPhoneLocation());
        record.setRingDuration(toInteger(data.getRingDuration()));
        record.setTimestamp(data.getTimestamp());
        record.setInsertTime(data.getInsertTime());
        return record;
    }

    /**
     * 组装短信审计实体。
     * type 字段可同时兼容短信和彩信，只是当前项目暂不处理彩信发送能力。
     */
    private MmbaSmsRecordAudit buildSmsAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaSmsRecordAudit record = new MmbaSmsRecordAudit();
        fillCommon(record, data, callbackRecord);
        record.setEsId(data.getEsId());
        record.setReqId(data.getReqId());
        record.setContent(data.getContent());
        record.setSubject(data.getSubject());
        record.setCustomer(data.getCustomer());
        record.setCustomerTel(data.getCustomerTel());
        record.setDirection(toInteger(data.getDirection()));
        record.setType(toInteger(data.getType()));
        record.setCreateTime(data.getCreateTime());
        record.setTimestamp(data.getTimestamp());
        record.setInsertTime(data.getInsertTime());
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        record.setIccid(data.getIccid());
        record.setIccidPhone(data.getIccidPhone());
        record.setNetSms(toBoolean(data.getNetSms()));
        record.setBizExtInfo(data.getBizExtInfo());
        return record;
    }

    /**
     * 组装微信聊天审计实体。
     * 微信聊天字段最多，因此这里尽量把高频查询字段单独结构化落库。
     */
    private MmbaWxChatAudit buildWxChatAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaWxChatAudit record = new MmbaWxChatAudit();
        fillCommon(record, data, callbackRecord);
        record.setEsId(data.getEsId());
        record.setReqId(data.getReqId());
        record.setAppName(data.getAppName());
        record.setChatType(toInteger(data.getChatType()));
        record.setContent(data.getContent());
        record.setDirection(toInteger(data.getDirection()));
        record.setStatus(toInteger(data.getStatus()));
        record.setTargetType(toInteger(data.getTargetType()));
        record.setChatSourcesType(toInteger(data.getChatSourcesType()));
        record.setTime(data.getTime());
        record.setTimestamp(data.getTimestamp());
        record.setInsertTime(data.getInsertTime());
        record.setStaffAccount(data.getStaffAccount());
        record.setStaffAccountId(data.getStaffAccountId());
        record.setStaffNickname(data.getStaffNickname());
        record.setStaffPic(data.getStaffPic());
        record.setCustomer(data.getCustomer());
        record.setCustomerAccount(data.getCustomerAccount());
        record.setCustomerAccountId(data.getCustomerAccountId());
        record.setCustomerNickname(data.getCustomerNickname());
        record.setCustomerPic(data.getCustomerPic());
        record.setFriendPhone(data.getFriendPhone());
        record.setContactDescription(data.getContactDescription());
        record.setGroupId(data.getGroupId());
        record.setGroupName(data.getGroupName());
        record.setMemberAccount(data.getMemberAccount());
        record.setMemberNickName(data.getMemberNickName());
        if (hasMethod(record, "setMemberPic", String.class)) {
            record.setMemberPic(data.getMemberPic());
        }
        record.setDuration(data.getDuration());
        record.setFriendSearch(data.getFriendSearch());
        record.setFileIdentifier(data.getFileIdentifier());
        record.setFileName(data.getFileName());
        record.setMsgId(data.getMsgId());
        record.setQuoteId(data.getQuoteId());
        return record;
    }

    private MmbaWxFriendChangeAudit buildWxFriendChangeAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaWxFriendChangeAudit record = new MmbaWxFriendChangeAudit();
        fillCommon(record, data, callbackRecord);
        record.setEsId(data.getEsId());
        record.setCreateTime(data.getCreateTime());
        record.setTimestamp(data.getTimestamp());
        record.setInsertTime(data.getInsertTime());
        record.setContacImAppNote(data.getContacImAppNote());
        record.setStaffIdInApp(data.getStaffIdInApp());
        record.setOperFlag(toInteger(data.getOperFlag()));
        record.setIsFriend(data.getIsFriend());
        record.setContactImIdInApp(data.getContactImIdInApp());
        record.setContactImAppAccount(data.getContactImAppAccount());
        record.setContactImAppNickName(data.getContactImAppNickName());
        record.setContactImAppNote(data.getContactImAppNote());
        record.setContactDescription(data.getContactDescription());
        record.setContactImAppHeaderPic(data.getContactImAppHeaderPic());
        record.setContactMobile(data.getContactMobile());
        record.setFriendPhone(data.getFriendPhone());
        record.setContactArea(data.getContactArea());
        record.setContactSex(data.getContactSex());
        record.setContactWeixinTags(data.getContactWeixinTags());
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        return record;
    }

    private MmbaWxFriendListAudit buildWxFriendListAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaWxFriendListAudit record = new MmbaWxFriendListAudit();
        fillCommon(record, data, callbackRecord);
        record.setEsId(data.getEsId());
        record.setCreateTime(data.getCreateTime());
        record.setTimestamp(data.getTimestamp());
        record.setInsertTime(data.getInsertTime());
        record.setContacImAppNote(data.getContacImAppNote());
        record.setStaffIdInApp(data.getStaffIdInApp());
        record.setIsFriend(data.getIsFriend());
        record.setContactImIdInApp(data.getContactImIdInApp());
        record.setContactImAppAccount(data.getContactImAppAccount());
        record.setContactImAppNickName(data.getContactImAppNickName());
        record.setContactImAppNote(data.getContactImAppNote());
        record.setContactDescription(data.getContactDescription());
        record.setContactImAppHeaderPic(data.getContactImAppHeaderPic());
        record.setContactMobile(data.getContactMobile());
        record.setContactArea(data.getContactArea());
        record.setContactSex(data.getContactSex());
        record.setContactWeixinTags(data.getContactWeixinTags());
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        return record;
    }

    /**
     * 组装微信账号审计实体。
     * 账号审计除了入流水，还会驱动设备映射表维护。
     */
    private MmbaWxAccountAudit buildWxAccountAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaWxAccountAudit record = new MmbaWxAccountAudit();
        fillCommon(record, data, callbackRecord);
        record.setEsId(data.getEsId());
        record.setAppId(data.getAppId());
        record.setAppName(data.getAppName());
        record.setCreateTime(data.getCreateTime());
        record.setTimestamp(data.getTimestamp());
        record.setInsertTime(data.getInsertTime());
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        record.setStaffIdInApp(data.getStaffIdInApp());
        record.setStaffImAppAccount(data.getStaffImAppAccount());
        record.setStaffImNickName(data.getStaffImNickName());
        record.setStaffImAppHeaderPic(data.getStaffImAppHeaderPic());
        record.setStaffMobile(data.getStaffMobile());
        record.setStaffArea(data.getStaffArea());
        record.setStaffSex(toInteger(data.getStaffSex()));
        record.setQq(data.getQq());
        record.setVerifiedStatus(toInteger(data.getVerifiedStatus()));
        record.setNote(data.getNote());
        record.setSign(data.getSign());
        return record;
    }

    /**
     * 组装设备信息审计实体。
     * 当前设备类型先沿用回调中的 appPkgName，后续如果文档明确存在独立 deviceType 字段，再直接切换映射。
     */
    private MmbaDeviceInfoAudit buildDeviceInfoAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaDeviceInfoAudit record = new MmbaDeviceInfoAudit();
        fillCommon(record, data, callbackRecord);
        record.setChangeTime(firstNotBlank(data.getChangeTime(), data.getCreateTime()));
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        record.setDeviceType(firstNotBlank(data.getDeviceType(), data.getAppPkgName()));
        record.setIccid(data.getIccid());
        record.setIccid2(null);
        record.setPhone(data.getIccidPhone());
        record.setPhone2(null);
        record.setTelecomOperators(data.getMobileVendor());
        record.setTelecomOperators2(null);
        record.setTimestamp(data.getTimestamp());
        return record;
    }

    private MmbaDeviceStatusAudit buildDeviceStatusAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaDeviceStatusAudit record = new MmbaDeviceStatusAudit();
        fillCommon(record, data, callbackRecord);
        record.setChangeTime(firstNotBlank(data.getChangeTime(), data.getCreateTime()));
        record.setDeviceStatus(toInteger(firstNotBlank(data.getDeviceStatus(), data.getStatus())));
        return record;
    }

    private MmbaWxLoginAudit buildWxLoginAudit(ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaWxLoginAudit record = new MmbaWxLoginAudit();
        fillCommon(record, data, callbackRecord);
        record.setAppPkgName(data.getAppPkgName());
        record.setCreateTime(resolveWxLoginCreateTimeValue(data));
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        record.setDeviceName(data.getDeviceName());
        record.setStaffIdInApp(data.getStaffIdInApp());
        record.setStaffImAppAccount(data.getStaffImAppAccount());
        record.setLoginStatus(toInteger(data.getLoginStatus()));
        return record;
    }

    /**
     * 组装统一指令结果实体。
     * 不同 behaviorType 对 targetType/targetValue 的取值规则不同，这里统一做归一化。
     */
    private MmbaCommandResult buildCommandResult(Integer behaviorType, ZzyData data, MmbaCallbackRecord callbackRecord) {
        MmbaCommandResult record = new MmbaCommandResult();
        record.setTenantId(data.getTenantId());
        record.setTenancyName(data.getTenancyName());
        record.setBehaviorType(behaviorType);
        record.setAppId(data.getAppId());
        record.setAppName(data.getAppName());
        record.setCreateTimeText(data.getCreateTime());
        record.setTimestampValue(data.getTimestamp());
        record.setInsertTimeValue(toLong(data.getInsertTime()));
        record.setReqId(data.getReqId());
        record.setUm(data.getUm());
        record.setStaffIdInApp(data.getStaffIdInApp());
        record.setStaffImAppAccount(data.getStaffImAppAccount());
        record.setStaffImNickName(data.getStaffImNickName());
        record.setStaffImAppHeaderPic(data.getStaffImAppHeaderPic());
        record.setStaffMobile(data.getStaffMobile());
        record.setStaffArea(data.getStaffArea());
        record.setStaffSex(data.getStaffSex());
        record.setStaffName(data.getStaffName());
        record.setOrgName(data.getOrgName());
        record.setOrgNames(data.getOrgNames());
        record.setDeptId(data.getDeptId());
        record.setDeptIdPath(data.getDeptIdPath());
        record.setDeptInfo(data.getDeptInfo());
        record.setCompany(data.getCompany());
        record.setRegion(data.getRegion());
        record.setDepartment(data.getDepartment());
        record.setDeviceId(toStringValue(data.getDeviceId()));
        record.setDeviceName(data.getDeviceName());
        record.setAppPkgName(data.getAppPkgName());
        record.setImei(data.getImei());
        record.setImei2(data.getImei2());
        record.setCustomer(data.getCustomer());
        record.setCustomerAccount(data.getCustomerAccount());
        record.setCustomerAccountId(data.getCustomerAccountId());
        record.setCustomerNickname(data.getCustomerNickname());
        record.setCustomerPic(data.getCustomerPic());
        record.setCustomerTel(data.getCustomerTel());
        record.setFriendPhone(data.getFriendPhone());
        record.setContactImIdInApp(data.getContactImIdInApp());
        record.setContactImAppAccount(data.getContactImAppAccount());
        record.setContactImAppNickName(data.getContactImAppNickName());
        record.setContactImAppNote(data.getContactImAppNote());
        record.setContactDescription(data.getContactDescription());
        record.setContactImAppHeaderPic(data.getContactImAppHeaderPic());
        record.setContactMobile(data.getContactMobile());
        record.setContactArea(data.getContactArea());
        record.setContactSex(data.getContactSex());
        record.setContactWeixinTags(data.getContactWeixinTags());
        record.setIsFriend(data.getIsFriend());
        record.setOperFlag(toInteger(data.getOperFlag()));
        record.setSource(data.getSource());
        record.setWxType(data.getWxType());
        record.setFriendSearch(data.getFriendSearch());
        record.setGroupId(data.getGroupId());
        record.setGroupName(data.getGroupName());
        record.setMemberAccount(data.getMemberAccount());
        record.setMemberNickName(data.getMemberNickName());
        record.setMemberPic(data.getMemberPic());
        record.setRecId(data.getCustomerAccountId());
        record.setMsgType(toInteger(data.getType()));
        record.setChatType(toInteger(data.getChatType()));
        record.setMsgStatus(toInteger(data.getStatus()));
        record.setProcessStatus(toInteger(data.getStatus()));
        record.setResultStatus(toInteger(data.getStatus()));
        record.setOperateTime(data.getCreateTime());
        record.setBizExtInfo(data.getBizExtInfo());
        record.setRawData(JSON.toJSONString(data));
        record.setCallbackRecordId(callbackRecord.getId());
        switch (behaviorType) {
            case MmbaBehaviorTypes.DIAL_FAIL_RECEIPT, MmbaBehaviorTypes.SMS_FAIL_RECEIPT -> {
                record.setTargetType("PHONE");
                record.setTargetValue(firstNotBlank(data.getCustomerTel(), data.getFriendPhone(), data.getUm()));
            }
            case MmbaBehaviorTypes.WX_MESSAGE_RECEIPT -> {
                record.setTargetType("WX_CHAT_TARGET");
                record.setTargetValue(firstNotBlank(data.getCustomerAccountId(), data.getCustomerAccount(), data.getFriendPhone()));
            }
            case MmbaBehaviorTypes.ADD_WECHAT_FRIEND_RECEIPT -> {
                record.setTargetType("WX_FRIEND");
                record.setTargetValue(firstNotBlank(data.getFriendPhone(), data.getContactImIdInApp(), data.getContactImAppAccount()));
            }
            case MmbaBehaviorTypes.WX_MOMENT_RECEIPT -> {
                record.setTargetType("WX_MOMENT");
                record.setTargetValue(firstNotBlank(data.getStaffIdInApp(), data.getUm()));
            }
            case MmbaBehaviorTypes.WX_REMARK_RECEIPT -> {
                record.setTargetType("WX_REMARK");
                record.setTargetValue(firstNotBlank(data.getContactImIdInApp(), data.getContactImAppAccount(), data.getFriendPhone()));
            }
            default -> {
                record.setTargetType("UNKNOWN");
                record.setTargetValue(firstNotBlank(data.getReqId(), data.getUm(), "UNKNOWN"));
            }
        }
        return record;
    }

    /**
     * 维护设备主表最新快照，避免前端查设备列表时扫审计流水。
     */
    private void syncDeviceSnapshot(ZzyData data, int behaviorType) {
        MmbaDevice device = new MmbaDevice();
        device.setDeviceId(toStringValue(data.getDeviceId()));
        device.setDeviceName(data.getDeviceName());
        device.setDeviceType(data.getAppPkgName());
        device.setDeviceStatus(toInteger(data.getStatus()));
        device.setImei(data.getImei());
        device.setImei2(data.getImei2());
        device.setIccid(data.getIccid());
        device.setPhone(data.getIccidPhone());
        device.setTelecomOperators(data.getMobileVendor());
        device.setUm(data.getUm());
        device.setStaffName(data.getStaffName());
        device.setOrgName(data.getOrgName());
        device.setOrgNames(data.getOrgNames());
        device.setLastBehaviorType(behaviorType);
        device.setLastAuditTime(data.getTimestamp());
        device.setRawData(JSON.toJSONString(data));
        log.info("MMBA设备快照同步 behaviorType={} tenantId={} deviceId={} imei={} um={}",
                behaviorType, data.getTenantId(), device.getDeviceId(), device.getImei(), device.getUm());
        mmbaDeviceService.saveOrUpdateDevice(device, MmbaConstants.SYSTEM_USER);
    }

    /**
     * 维护设备、员工、微信账号之间的映射关系，便于后续 CRM 业务关联。
     */
    private void syncDeviceMapping(ZzyData data) {
        if (StringUtils.isBlank(data.getUm()) && StringUtils.isBlank(data.getStaffIdInApp())) {
            log.warn("MMBA设备映射跳过，um 和 staffIdInApp 都为空 tenantId={} reqId={} esId={}",
                    data.getTenantId(), data.getReqId(), data.getEsId());
            return;
        }
        MmbaDeviceMapping mapping = new MmbaDeviceMapping();
        mapping.setUm(data.getUm());
        mapping.setStaffName(data.getStaffName());
        mapping.setDeviceId(toStringValue(data.getDeviceId()));
        mapping.setImei(data.getImei());
        mapping.setIccid(data.getIccid());
        mapping.setWxid(data.getStaffIdInApp());
        mapping.setWxAccount(data.getStaffImAppAccount());
        mapping.setWxPhone(data.getStaffMobile());
        mapping.setMappingStatus("ACTIVE");
        mapping.setLastSyncTime(data.getTimestamp());
        mapping.setRawData(JSON.toJSONString(data));
        log.info("MMBA设备映射同步 tenantId={} um={} deviceId={} imei={} wxid={}",
                data.getTenantId(), mapping.getUm(), mapping.getDeviceId(), mapping.getImei(), mapping.getWxid());
        mmbaDeviceService.saveOrUpdateMapping(mapping, MmbaConstants.SYSTEM_USER);
    }

    /**
     * 各业务表公共字段填充。
     * 这里统一处理组织、人员、设备、原始 JSON 和回调总表 ID。
     */
    private void fillCommon(Object target, ZzyData data, MmbaCallbackRecord callbackRecord) {
        try {
            if (hasMethod(target, "setBehaviorType", Integer.class)) {
                target.getClass().getMethod("setBehaviorType", Integer.class).invoke(target, data.getBehaviorType());
            }
            if (hasMethod(target, "setTenancyName", String.class)) {
                target.getClass().getMethod("setTenancyName", String.class).invoke(target, data.getTenancyName());
            }
            if (hasMethod(target, "setUm", String.class)) {
                target.getClass().getMethod("setUm", String.class).invoke(target, data.getUm());
            }
            if (hasMethod(target, "setStaffName", String.class)) {
                target.getClass().getMethod("setStaffName", String.class).invoke(target, data.getStaffName());
            }
            if (hasMethod(target, "setOrgName", String.class)) {
                target.getClass().getMethod("setOrgName", String.class).invoke(target, data.getOrgName());
            }
            if (hasMethod(target, "setOrgNames", String.class)) {
                target.getClass().getMethod("setOrgNames", String.class).invoke(target, data.getOrgNames());
            }
            if (hasMethod(target, "setDeviceId", String.class)) {
                target.getClass().getMethod("setDeviceId", String.class).invoke(target, toStringValue(data.getDeviceId()));
            }
            if (hasMethod(target, "setImei", String.class)) {
                target.getClass().getMethod("setImei", String.class).invoke(target, firstNotBlank(data.getImei(), data.getImei1()));
            }
            if (hasMethod(target, "setImei2", String.class)) {
                target.getClass().getMethod("setImei2", String.class).invoke(target, data.getImei2());
            }
            target.getClass().getMethod("setRawData", String.class).invoke(target, JSON.toJSONString(data));
            target.getClass().getMethod("setCallbackRecordId", String.class).invoke(target, callbackRecord.getId());
        } catch (Exception e) {
            throw new IllegalStateException("MMBA公共字段填充失败", e);
        }
    }

    private boolean hasMethod(Object target, String methodName, Class<?>... parameterTypes) {
        try {
            target.getClass().getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Long resolveWxLoginCreateTimeValue(ZzyData data) {
        Long createTimeValue = parseDateTimeText(data.getCreateTime());
        if (createTimeValue != null) {
            return createTimeValue;
        }
        if (data.getTimestamp() != null) {
            return data.getTimestamp();
        }
        Long insertTimeValue = toLong(data.getInsertTime());
        if (insertTimeValue != null) {
            return insertTimeValue;
        }
        throw new IllegalArgumentException("MMBA登录登出审计缺少时间字段，createTime、timestamp 和 insertTime 都为空");
    }

    private Integer toInteger(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean toBoolean(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
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

    private Long parseDateTimeText(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy/M/d H:m:s"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy-M-d H:m"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy/M/d H:m"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy-M-d"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy/M/d"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy-M"))
                    .appendOptional(DateTimeFormatter.ofPattern("yyyy/M"))
                    .toFormatter();
            TemporalAccessor parsed = formatter.parseBest(value, LocalDateTime::from, LocalDate::from, YearMonth::from);
            Instant instant = switch (parsed) {
                case LocalDateTime localDateTime -> localDateTime.atZone(ZoneId.systemDefault()).toInstant();
                case LocalDate localDate -> localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
                case YearMonth yearMonth -> yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                default -> throw new DateTimeParseException("无法解析日期时间: " + value, value, 0);
            };
            return instant.toEpochMilli();
        } catch (Exception e) {
            log.warn("MMBA登录登出审计 createTime 解析失败，改用其他时间字段 createTime={}", value, e);
            return null;
        }
    }

    private String resolveCustomerId(String bizExtInfo) {
        if (StringUtils.isBlank(bizExtInfo)) {
            return null;
        }
        try {
            JsonNode bizExt = JSON.parseObject(bizExtInfo, JsonNode.class);
            if (bizExt == null) {
                return null;
            }
            String customerId = bizExt.path("customerId").asText(null);
            return StringUtils.isBlank(customerId) ? null : customerId;
        } catch (Exception e) {
            log.warn("MMBA bizExtInfo 解析 customerId 失败 bizExtInfo={}", bizExtInfo, e);
            return null;
        }
    }
}
