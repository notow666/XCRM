package cn.cordys.mmba.service;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.PhoneMaskUtil;
import cn.cordys.crm.customer.service.CustomerCallStatusService;
import cn.cordys.crm.customer.service.CustomerWechatFriendStatusService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service.EmployeeStatEventRecordService;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.dto.MessageDetailDTO;
import cn.cordys.crm.system.notice.common.NoticeModel;
import cn.cordys.crm.system.notice.common.Receiver;
import cn.cordys.crm.system.notice.sender.insite.InSiteNoticeSender;
import cn.cordys.crm.system.service.GlobalPhoneMaskConfigService;
import cn.cordys.mmba.MmbaBehaviorTypes;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.domain.*;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.mmba.dto.ZzyData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MMBA 回调分发服务。
 * 负责把统一回调 DTO 拆成各业务表实体，并调用对应的持久化服务做幂等保存。
 */
@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
@Service
public class MmbaCallbackDispatchService {

    @Resource
    private MmbaCallbackItemDispatchService mmbaCallbackItemDispatchService;
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
    @Resource
    private CustomerCallStatusService customerCallStatusService;
    @Resource
    private CustomerWechatFriendStatusService customerWechatFriendStatusService;
    @Resource
    private MmbaAutoCustomerFollowService mmbaAutoCustomerFollowService;

    @Resource
    private EmployeeStatEventRecordService employeeStatEventRecordService;

    @Resource
    private GlobalPhoneMaskConfigService globalPhoneMaskConfigService;

    /**
     * 审计类回调分发。
     */
    public void dispatchAudit(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord) {
        List<ZzyData> orderedDataList = dto.getData() == null ? List.of() : dto.getData().stream()
                .sorted(Comparator.comparing(ZzyData::getEsId, Comparator.nullsLast(String::compareTo)))
                .toList();
        for (ZzyData data : orderedDataList) {
            log.info("MMBA审计回调分发 callbackRecordId={} behaviorType={} reqId={} esId={} tenantId={}",
                    callbackRecord.getId(), dto.getBehaviorType(), data.getReqId(), data.getEsId(), data.getTenantId());
            mmbaCallbackItemDispatchService.dispatchAuditItem(dto, callbackRecord, data);
        }
    }

    public void processAuditItem(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord, ZzyData data) {
        switch (dto.getBehaviorType()) {
            case MmbaBehaviorTypes.CALL_RECORD_AUDIT -> {
                MmbaAuditPersistenceService.SaveOrUpdateResult<MmbaCallRecordAudit> callAuditResult =
                        mmbaAuditPersistenceService.saveOrUpdateCallAuditWithResult(
                        buildCallAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER
                );
                MmbaCallRecordAudit previousCallAudit = callAuditResult.getPrevious();
                MmbaCallRecordAudit callAudit = callAuditResult.getCurrent();
                upgradeCustomerCallStatus(callAudit.getCustomerId(), callAudit.getCustomerTel(), callAudit.getUm(),
                        resolveAuditCustomerCallStatus(callAudit), callbackRecord.getId());
                mmbaAutoCustomerFollowService.handleConnectedCall(previousCallAudit, callAudit);
            }
            case MmbaBehaviorTypes.SMS_RECORD_AUDIT -> {
                MmbaAuditPersistenceService.SaveOrUpdateResult<MmbaSmsRecordAudit> smsAuditResult =
                        mmbaAuditPersistenceService.saveOrUpdateSmsAuditWithResult(
                                buildSmsAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER
                        );
                MmbaSmsRecordAudit previousSmsAudit = smsAuditResult.getPrevious();
                MmbaSmsRecordAudit smsAudit = smsAuditResult.getCurrent();
                mmbaAutoCustomerFollowService.handleSmsDelivered(previousSmsAudit, smsAudit);
            }
            case MmbaBehaviorTypes.WX_CHAT_AUDIT -> {
                MmbaAuditPersistenceService.SaveOrUpdateResult<MmbaWxChatAudit> wxChatAuditResult =
                        mmbaAuditPersistenceService.saveOrUpdateWxChatAuditWithResult(
                                buildWxChatAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER
                        );
                MmbaWxChatAudit previousWxChatAudit = wxChatAuditResult.getPrevious();
                MmbaWxChatAudit wxChatAudit = wxChatAuditResult.getCurrent();
                mmbaAutoCustomerFollowService.handleWxChatSuccess(previousWxChatAudit, wxChatAudit);
            }
            case MmbaBehaviorTypes.WX_FRIEND_CHANGE_AUDIT -> {
                mmbaAuditPersistenceService.saveOrUpdateWxFriendChangeAudit(buildWxFriendChangeAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                customerWechatFriendStatusService.handleFriendChangeAudit(
                        data.getUm(), data.getFriendPhone(), data.getIsFriend(), toInteger(data.getOperFlag()), MmbaConstants.SYSTEM_USER
                );
                boolean friendAdded = "1".equals(StringUtils.trimToEmpty(data.getIsFriend()))
                    || Integer.valueOf(1).equals(toInteger(data.getOperFlag()));
                if (friendAdded) {
                    employeeStatEventRecordService.confirmWechatFriendSuccessEvent(
                        data.getUm(),
                        data.getFriendPhone(),
                        data.getEsId(),
                        data.getTimestamp()
                    );
                }
            }
            case MmbaBehaviorTypes.WX_FRIEND_LIST_AUDIT -> mmbaAuditPersistenceService.saveOrUpdateWxFriendListAudit(buildWxFriendListAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
            case MmbaBehaviorTypes.WX_ACCOUNT_AUDIT -> {
                mmbaAuditPersistenceService.saveOrUpdateWxAccountAudit(buildWxAccountAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                mmbaWxMappingSyncService.syncFromAccountAudit(data, MmbaConstants.SYSTEM_USER);
            }
            case MmbaBehaviorTypes.DEVICE_INFO_AUDIT -> {
                mmbaAuditPersistenceService.saveOrUpdateDeviceInfoAudit(buildDeviceInfoAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                syncDeviceSnapshot(data, dto.getBehaviorType());
            }
            case MmbaBehaviorTypes.WX_LOGIN_LOGOUT_AUDIT -> {
                mmbaAuditPersistenceService.saveOrUpdateWxLoginAudit(buildWxLoginAudit(data, callbackRecord), MmbaConstants.SYSTEM_USER);
                mmbaWxMappingSyncService.syncMappingStatusFromLoginAudit(data, MmbaConstants.SYSTEM_USER);
                customerWechatFriendStatusService.recalculateByUm(data.getUm(), MmbaConstants.SYSTEM_USER);
            }
            default -> {
            }
        }
    }

    /**
     * 指令结果类回调分发。
     */
    public void dispatchCommand(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord) {
        if (dto.getData() == null) {
            return;
        }
        for (ZzyData data : dto.getData()) {
            log.info("MMBA结果回调分发 callbackRecordId={} behaviorType={} reqId={} tenantId={}",
                    callbackRecord.getId(), dto.getBehaviorType(), data.getReqId(), data.getTenantId());
            mmbaCallbackItemDispatchService.dispatchCommandItem(dto, callbackRecord, data);
        }
    }

    public void processCommandItem(MmbaAuditRequest dto, MmbaCallbackRecord callbackRecord, ZzyData data) {
        MmbaCommandResultService.SaveOrUpdateResult saveResult = mmbaCommandResultService.saveOrUpdate(
                buildCommandResult(dto.getBehaviorType(), data, callbackRecord), MmbaConstants.SYSTEM_USER
        );
        if (dto.getBehaviorType() == MmbaBehaviorTypes.DIAL_FAIL_RECEIPT) {
            upgradeCustomerCallStatus(resolveCustomerId(data.getBizExtInfo()), data.getCustomerTel(), data.getUm(),
                    CustomerCallStatusService.DIALED_NOT_CONNECTED, callbackRecord.getId());
        }
        if (dto.getBehaviorType() == MmbaBehaviorTypes.ADD_WECHAT_FRIEND_RECEIPT) {
            customerWechatFriendStatusService.handleAddFriendReceipt(
                    data.getUm(),
                    data.getFriendPhone(),
                    toInteger(firstNotBlank(data.getProcessStatus(), data.getStatus())),
                    MmbaConstants.SYSTEM_USER
            );
            if (saveResult.isCreated()) {
                sendAddWechatFriendReceiptNotice(data);
            }
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
        record.setRetry(toInteger(data.getRetry()));
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
        record.setCardSlotNum(data.getCardSlotNum());
        record.setDirection(toInteger(data.getDirection()));
        record.setType(toInteger(data.getType()));
        record.setSentStatus(toInteger(firstNotBlank(data.getSentStatus(), data.getStatus())));
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
        record.setFriendSearch(data.getFriendSearch());
        record.setContactArea(data.getContactArea());
        record.setContactSex(data.getContactSex());
        record.setContactWeixinTags(data.getContactWeixinTags());
        record.setSource(data.getSource());
        record.setWxType(data.getWxType());
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
        record.setIccid2(data.getIccid2());
        record.setPhone(data.getPhone());
        record.setPhone2(data.getPhone2());
        record.setTelecomOperators(data.getTelecomOperators());
        record.setTelecomOperators2(data.getTelecomOperators2());
        record.setImei(data.getImei());
        record.setImei2(data.getImei2());
        record.setTimestamp(data.getTimestamp());
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
        String operateTime = firstNotBlank(data.getOperateTime(), data.getCreateTime());
        record.setTenantId(data.getTenantId());
        record.setTenancyName(data.getTenancyName());
        record.setBehaviorType(behaviorType);
        record.setAppId(data.getAppId());
        record.setAppName(data.getAppName());
        record.setCreateTimeText(operateTime);
        record.setTimestampValue(data.getTimestamp());
        record.setInsertTimeValue(toLong(data.getInsertTime()));
        record.setReqId(data.getReqId());
        record.setUm(data.getUm());
        record.setUmPhone(data.getUmPhone());
        record.setUmWxid(data.getUmWxid());
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
        record.setDeviceId(data.getDeviceId());
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
        record.setContactImAppNote(firstNotBlank(data.getContactImAppNote(), data.getNote()));
        record.setContactDescription(firstNotBlank(data.getContactDescription(), data.getDescription()));
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
        record.setRecId(firstNotBlank(data.getRecId(), data.getCustomerAccountId()));
        record.setMsgType(toInteger(firstNotBlank(data.getMsgType(), data.getType())));
        record.setChatType(toInteger(data.getChatType()));
        record.setMsgStatus(toInteger(firstNotBlank(data.getMsgStatus(), data.getStatus())));
        record.setProcessStatus(toInteger(firstNotBlank(data.getProcessStatus(), data.getStatus())));
        record.setResultStatus(toInteger(firstNotBlank(data.getResultStatus(), data.getProcessStatus(), data.getStatus())));
        record.setOperateTime(operateTime);
        record.setProcessMsg(data.getProcessMsg());
        record.setBizExtInfo(data.getBizExtInfo());
        record.setCallbackRecordId(callbackRecord.getId());
        switch (behaviorType) {
            case MmbaBehaviorTypes.DIAL_FAIL_RECEIPT, MmbaBehaviorTypes.SMS_FAIL_RECEIPT -> {
                record.setTargetType("PHONE");
                record.setTargetValue(firstNotBlank(data.getCustomerTel(), data.getFriendPhone(), data.getUm()));
            }
            case MmbaBehaviorTypes.WX_MESSAGE_RECEIPT -> {
                record.setTargetType("WX_CHAT_TARGET");
                record.setTargetValue(firstNotBlank(data.getRecId(), data.getCustomerAccountId(), data.getCustomerAccount(), data.getFriendPhone()));
            }
            case MmbaBehaviorTypes.ADD_WECHAT_FRIEND_RECEIPT -> {
                record.setTargetType("WX_FRIEND");
                record.setTargetValue(firstNotBlank(data.getFriendSearch(), data.getFriendPhone(), data.getContactImIdInApp(), data.getContactImAppAccount()));
            }
            case MmbaBehaviorTypes.WX_MOMENT_RECEIPT -> {
                record.setTargetType("WX_MOMENT");
                record.setTargetValue(firstNotBlank(data.getUmWxid(), data.getStaffIdInApp(), data.getUm()));
            }
            case MmbaBehaviorTypes.WX_REMARK_RECEIPT -> {
                record.setTargetType("WX_REMARK");
                record.setTargetValue(firstNotBlank(data.getContactImIdInApp(), data.getContactImAppAccount(), data.getFriendPhone(), data.getUmWxid(), data.getUm()));
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
        if (StringUtils.isBlank(data.getUm())) {
            log.warn("MMBA设备快照同步跳过，um为空 behaviorType={} tenantId={} deviceId={} reqId={} esId={}",
                    behaviorType, data.getTenantId(), data.getDeviceId(), data.getReqId(), data.getEsId());
            return;
        }
        MmbaDevice device = new MmbaDevice();
        device.setId(data.getUm());
        device.setDeviceId(data.getDeviceId());
        if (StringUtils.isNotBlank(data.getDeviceName())) {
            device.setDeviceName(data.getDeviceName());
        }
        if (StringUtils.isNotBlank(data.getDeviceType())) {
            device.setDeviceType(data.getDeviceType());
        }
        String deviceStatus = firstNotBlank(data.getDeviceStatus(), data.getStatus());
        if (StringUtils.isNotBlank(deviceStatus)) {
            device.setDeviceStatus(toInteger(deviceStatus));
        }
        if (StringUtils.isNotBlank(data.getImei())) {
            device.setImei(data.getImei());
        }
        if (StringUtils.isNotBlank(data.getImei2())) {
            device.setImei2(data.getImei2());
        }
        if (StringUtils.isNotBlank(data.getIccid())) {
            device.setIccid(data.getIccid());
        }
        if (StringUtils.isNotBlank(data.getIccid2())) {
            device.setIccid2(data.getIccid2());
        }
        if (StringUtils.isNotBlank(data.getPhone())) {
            device.setPhone(data.getPhone());
        }
        if (StringUtils.isNotBlank(data.getPhone2())) {
            device.setPhone2(data.getPhone2());
        }
        if (StringUtils.isNotBlank(data.getTelecomOperators())) {
            device.setTelecomOperators(data.getTelecomOperators());
        }
        if (StringUtils.isNotBlank(data.getTelecomOperators2())) {
            device.setTelecomOperators2(data.getTelecomOperators2());
        }
        if (StringUtils.isNotBlank(data.getStaffName())) {
            device.setStaffName(data.getStaffName());
        }
        if (StringUtils.isNotBlank(data.getOrgName())) {
            device.setOrgName(data.getOrgName());
        }
        if (StringUtils.isNotBlank(data.getOrgNames())) {
            device.setOrgNames(data.getOrgNames());
        }
        device.setLastBehaviorType(behaviorType);
        device.setLastAuditTime(data.getTimestamp());
        log.debug("MMBA设备快照同步 behaviorType={} tenantId={} deviceId={} imei={} um={}",
                behaviorType, data.getTenantId(), device.getDeviceId(), device.getImei(), data.getUm());
        mmbaDeviceService.saveOrUpdateDevice(device, MmbaConstants.SYSTEM_USER);
    }


    /**
     * 各业务表公共字段填充。
     * 这里统一处理组织、人员、设备和回调总表 ID。
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
                target.getClass().getMethod("setDeviceId", String.class).invoke(target, data.getDeviceId());
            }
            if (hasMethod(target, "setImei", String.class)) {
                target.getClass().getMethod("setImei", String.class).invoke(target, data.getImei());
            }
            if (hasMethod(target, "setImei2", String.class)) {
                target.getClass().getMethod("setImei2", String.class).invoke(target, data.getImei2());
            }
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
        Long timestampValue = toLong(value);
        if (timestampValue != null) {
            return timestampValue;
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

    private Integer resolveAuditCustomerCallStatus(MmbaCallRecordAudit record) {
        return record != null && Integer.valueOf(1).equals(record.getIsConnected())
                ? CustomerCallStatusService.DIALED_CONNECTED
                : CustomerCallStatusService.DIALED_NOT_CONNECTED;
    }

    private void upgradeCustomerCallStatus(String customerId, String customerTel, String um, Integer targetStatus, String callbackRecordId) {
        customerCallStatusService.upgradeByCustomer(customerId, customerTel, um, targetStatus, MmbaConstants.SYSTEM_USER);
        log.debug("MMBA客户拨打状态维护 callbackRecordId={} customerId={} customerTel={} um={} targetStatus={}",
                callbackRecordId, customerId, customerTel, um, targetStatus);
    }

    @Resource
    private MmbaRequestRecordService mmbaRequestRecordService;

    @Resource
    private InSiteNoticeSender inSiteNoticeSender;
    private void sendAddWechatFriendReceiptNotice(ZzyData data) {
        try {
            String reqId = StringUtils.trimToNull(data.getReqId());
            if (reqId == null) {
                return;
            }

            MmbaRequestRecord requestRecord = mmbaRequestRecordService.findByReqId(reqId);
            if (requestRecord == null || StringUtils.isBlank(requestRecord.getRequestBody())) {
                log.warn("MMBA添加微信好友回执通知跳过，未找到请求记录 reqId={}", reqId);
                return;
            }

            JsonNode requestBody = JSON.parseObject(requestRecord.getRequestBody(), JsonNode.class);
            JsonNode bizExtInfo = requestBody == null ? null : requestBody.path("bizExtInfo");
            if (bizExtInfo == null || bizExtInfo.isMissingNode()) {
                log.warn("MMBA添加微信好友回执通知跳过，bizExtInfo为空 reqId={}", reqId);
                return;
            }

            String operatorUserId = readBizExtText(bizExtInfo, "operator_user_id", "operatorUserId");
            if (StringUtils.isBlank(operatorUserId)) {
                log.warn("MMBA添加微信好友回执通知跳过，operator_user_id为空 reqId={}", reqId);
                return;
            }

            String customerId = readBizExtText(bizExtInfo, "customer_id", "customerId");
            String customerName = readBizExtText(bizExtInfo, "customer_name", "customerName");
            String customerMobile = readBizExtText(bizExtInfo, "customer_mobile", "customerMobile");
            String organizationId = readBizExtText(bizExtInfo, "organization_id", "organizationId");

            Integer processStatus = toInteger(firstNotBlank(data.getProcessStatus(), data.getStatus()));
            String processStatusText = resolveAddWechatFriendProcessStatusText(processStatus);
            String context = buildAddWechatFriendReceiptNoticeContext(customerName, customerMobile, processStatusText,organizationId);
            String subjectText = "添加微信好友回执";

            inSiteNoticeSender.sendAnnouncement(
                buildAddWechatFriendReceiptMessageDetail(reqId, organizationId),
                buildAddWechatFriendReceiptNoticeModel(operatorUserId, organizationId, customerId, customerName),
                context,
                subjectText
            );
        } catch (Exception e) {
            log.error("MMBA添加微信好友回执通知发送失败 reqId={}", data == null ? null : data.getReqId(), e);
        }
    }


    private MessageDetailDTO buildAddWechatFriendReceiptMessageDetail(String reqId, String organizationId) {
        MessageDetailDTO dto = new MessageDetailDTO();
        dto.setId(reqId);
        dto.setEvent(NotificationConstants.Event.MMBA_ADD_WECHAT_FRIEND_RECEIPT);
        dto.setTaskType(NotificationConstants.Module.CUSTOMER);
        dto.setOrganizationId(organizationId);
        dto.setSysEnable(true);
        return dto;
    }

    private NoticeModel buildAddWechatFriendReceiptNoticeModel(String operatorUserId, String organizationId,
                                                               String customerId, String customerName) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("organizationId", organizationId);
        if (StringUtils.isNotBlank(customerId)) {
            paramMap.put("resourceId", customerId);
        }
        if (StringUtils.isNotBlank(customerName)) {
            paramMap.put("name", customerName);
        }

        return NoticeModel.builder()
            .operator(MmbaConstants.SYSTEM_USER)
            .event(NotificationConstants.Event.MMBA_ADD_WECHAT_FRIEND_RECEIPT)
            .paramMap(paramMap)
            .receivers(List.of(new Receiver(operatorUserId, NotificationConstants.Type.SYSTEM_NOTICE.name())))
            .excludeSelf(false)
            .build();
    }

    private String buildAddWechatFriendReceiptNoticeContext(String customerName, String customerMobile, String statusText, String organizationId){
        StringBuilder sb = new StringBuilder();
        sb.append("收到微信好友添加回执");
        if (StringUtils.isNotBlank(customerName)) {
            sb.append("，客户：").append(customerName);
        }
        if (StringUtils.isNotBlank(customerMobile)) {
            String noticeCustomerMobile = globalPhoneMaskConfigService.isEnabled(organizationId)
                ? PhoneMaskUtil.maskGlobalPhone(customerMobile)
                : customerMobile;
            sb.append("，手机号：").append(noticeCustomerMobile);
        }
        sb.append("，回执结果：").append(statusText);
        return sb.toString();
    }
    private String readBizExtText(JsonNode bizExtInfo, String primaryField, String fallbackField) {
        if (bizExtInfo == null || bizExtInfo.isMissingNode()) {
            return null;
        }
        String value = StringUtils.trimToNull(bizExtInfo.path(primaryField).asText(null));
        if (value != null) {
            return value;
        }
        return StringUtils.trimToNull(bizExtInfo.path(fallbackField).asText(null));
    }
    private String resolveAddWechatFriendProcessStatusText(Integer processStatus) {
        return switch (processStatus) {
            case 2 -> "未搜索到微信联系人";
            case 3 -> "已经是好友";
            case 4 -> "添加好友请求发送成功";
            case 5 -> "添加好友请求发送失败";
            case 6 -> "添加失败：被对方加入黑名单";
            case 7 -> "其它原因";
            case 8 -> "操作过于频繁";
            case 9 -> "微信登出";
            case 10 -> "添加失败：设备处于灭屏状态或亮屏未解锁状态";
            case 11 -> "微信未安装";
            case 12 -> "辅助功能处于关闭状态";
            case 13 -> "启动微信失败";
            case 14 -> "搜索好友失败";
            case 15 -> "搜索好友异常";
            case 16 -> "设置备注失败";
            case 17 -> "绑定sim卡与设备插入sim卡不一致";
            case 18 -> "设备登录微信Id与指定微信Id不符";
            case 19 -> "定时添加好友，超出时间范围";
            case 20 -> "未适配设备安装微信版本";
            case 21 -> "有权查看应用使用情况权限未开启";
            case 22 -> "该设备未找到指定的微信ID，请检查微信是否登陆成功";
            case 23 -> "该设备未找到指定的微信ID，请检查微信分身是否安装并登陆成功";
            case 24 -> "双开策略下，api参数异常umWxid字段为空";
            default -> "收到添加微信好友回执，状态码：" + processStatus;
        };
    }
}
