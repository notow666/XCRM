package cn.cordys.mmba.service;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerFollowWayConfig;
import cn.cordys.crm.customer.service.CustomerFollowWayService;
import cn.cordys.crm.follow.dto.request.FollowUpRecordAddRequest;
import cn.cordys.crm.follow.service.FollowUpRecordService;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeStatEventType;
import cn.cordys.crm.system.domain.User;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MMBA 自动新增客户跟进记录。
 * 仅在回调首次进入成功态时触发，避免把现有回调幂等和业务副作用幂等混在一起。
 */
@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaAutoCustomerFollowService {

    private static final int CALL_CONNECTED = 1;
    private static final int SMS_DIRECTION_SEND_SUCCESS = 2;
    private static final int WX_CHAT_SOURCE_NORMAL = 3;
    private static final int WX_CHAT_STATUS_SUCCESS = 0;
    private static final int WX_CHAT_DIRECTION_SEND = 0;
    private static final String FOLLOW_RESULT_IN_PROGRESS = "IN_PROGRESS";
    private static final String FOLLOW_TYPE_CUSTOMER = "CUSTOMER";
    private static final String FOLLOW_CONTENT_PHONE = "电话沟通";
    private static final String FOLLOW_WAY_PHONE = "电话";
    private static final String FOLLOW_WAY_SMS = "短信";
    private static final String FOLLOW_WAY_WECHAT = "微信";

    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private FollowUpRecordService followUpRecordService;
    @Resource
    private CustomerFollowWayService customerFollowWayService;

    public void handleConnectedCall(MmbaCallRecordAudit previous, MmbaCallRecordAudit current) {
        if (current == null || !isCallConnected(current)) {
            return;
        }
        if (previous != null && isCallConnected(previous)) {
            return;
        }
        createCustomerFollow(current.getUm(), current.getCustomerTel(), FOLLOW_WAY_PHONE, FOLLOW_CONTENT_PHONE,
                current.getAnswerTimestamp(), current.getReqId(), "电话");
    }

    public void handleSmsDelivered(MmbaSmsRecordAudit previous, MmbaSmsRecordAudit current, boolean inserted) {
        if (current == null || !isSmsDelivered(current)) {
            return;
        }
        if (previous == null && !inserted) {
            log.info("MMBA自动新增客户跟进跳过，短信审计并发更新非首次插入 scene=短信 reqId={} esId={}",
                    current.getReqId(), current.getEsId());
            return;
        }
        if (previous != null && isSmsDelivered(previous)) {
            return;
        }
        createCustomerFollow(current.getUm(), current.getCustomerTel(), FOLLOW_WAY_SMS, current.getContent(),
                current.getTimestamp(), current.getReqId(), "短信");
    }

    public void handleWxChatSuccess(MmbaWxChatAudit previous, MmbaWxChatAudit current) {
        if (current == null || !isWxChatSuccess(current)) {
            return;
        }
        if (previous != null && isWxChatSuccess(previous)) {
            return;
        }
        createCustomerFollow(current.getUm(), current.getFriendPhone(), FOLLOW_WAY_WECHAT,
                StringUtils.trimToNull(current.getContent()), current.getTimestamp(), current.getEsId(), "微信");
    }

    private boolean isCallConnected(MmbaCallRecordAudit record) {
        return record != null && Integer.valueOf(CALL_CONNECTED).equals(record.getIsConnected());
    }

    private boolean isSmsDelivered(MmbaSmsRecordAudit record) {
        return record != null && Integer.valueOf(SMS_DIRECTION_SEND_SUCCESS).equals(record.getDirection());
    }

    private boolean isWxChatSuccess(MmbaWxChatAudit record) {
        return record != null
                && Integer.valueOf(WX_CHAT_SOURCE_NORMAL).equals(record.getChatSourcesType())
                && Integer.valueOf(WX_CHAT_DIRECTION_SEND).equals(record.getDirection())
                && Integer.valueOf(WX_CHAT_STATUS_SUCCESS).equals(record.getStatus());
    }

    private void createCustomerFollow(String um, String mobile, String followWayName, String content, Long followTime, String reqId, String scene) {
        Customer customer = findOwnedCustomer(um, mobile);
        if (customer == null) {
            log.warn("MMBA自动新增客户跟进跳过，未找到客户 scene={} reqId={} um={} mobile={}", scene, reqId, um, mobile);
            return;
        }
        if (followTime == null) {
            log.warn("MMBA自动新增客户跟进跳过，时间为空 scene={} reqId={} customerId={}", scene, reqId, customer.getId());
            return;
        }
        if (StringUtils.isBlank(content)) {
            log.warn("MMBA自动新增客户跟进跳过，内容为空 scene={} reqId={} customerId={}", scene, reqId, customer.getId());
            return;
        }
        CustomerFollowWayConfig followWay = customerFollowWayService.findByName(followWayName);
        if (followWay == null || StringUtils.isBlank(followWay.getId())) {
            log.warn("MMBA自动新增客户跟进跳过，未找到跟进方式 scene={} reqId={} followWayName={}", scene, reqId, followWayName);
            return;
        }
        String operatorUserId = resolveOperatorUserId(um);
        if (StringUtils.isBlank(operatorUserId)) {
            log.warn("MMBA自动新增客户跟进跳过，未找到创建人 scene={} reqId={} um={} customerId={}",
                    scene, reqId, um, customer.getId());
            return;
        }
        FollowUpRecordAddRequest request = new FollowUpRecordAddRequest();
        request.setType(FOLLOW_TYPE_CUSTOMER);
        request.setCustomerId(customer.getId());
        request.setOwner(customer.getOwner());
        request.setFollowMethod(followWay.getId());
        request.setFollowResult(FOLLOW_RESULT_IN_PROGRESS);
        request.setContent(content);
        request.setFollowTime(followTime);
        EmployeeStatEventType eventType=null;
        if(scene.equals("电话")){
            eventType = EmployeeStatEventType.CALL_AUTO_FOLLOW;
        }else if(scene.equals("短信")){
            eventType = EmployeeStatEventType.SMS_AUTO_FOLLOW;
        }
        else if(scene.equals("微信")){
            eventType = EmployeeStatEventType.WECHAT_AUTO_FOLLOW;
        }
        followUpRecordService.add(request, operatorUserId, customer.getOrganizationId(),eventType);
        log.info("MMBA自动新增客户跟进成功 scene={} reqId={} customerId={} followWayId={} operatorUserId={}",
                scene, reqId, customer.getId(), followWay.getId(), operatorUserId);
    }

    private Customer findOwnedCustomer(String um, String mobile) {
        String normalizedUm = StringUtils.trimToNull(um);
        String normalizedMobile = StringUtils.trimToNull(mobile);
        if (normalizedUm == null || normalizedMobile == null) {
            return null;
        }
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUm, normalizedUm);
        List<String> ownerIds = userBaseMapper.selectListByLambda(userWrapper).stream()
                .map(User::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper.in(Customer::getOwner, ownerIds)
                .eq(Customer::getMobile, normalizedMobile)
                .eq(Customer::getInSharedPool, false);
        return customerMapper.selectListByLambda(customerWrapper).stream().findFirst().orElse(null);
    }

    private String resolveOperatorUserId(String um) {
        String normalizedUm = StringUtils.trimToNull(um);
        if (normalizedUm == null) {
            return null;
        }
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUm, normalizedUm);
        return userBaseMapper.selectListByLambda(userWrapper).stream()
                .map(User::getId)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
