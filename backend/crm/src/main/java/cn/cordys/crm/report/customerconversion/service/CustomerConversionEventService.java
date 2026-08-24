package cn.cordys.crm.report.customerconversion.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.contract.domain.ContractPaymentRecord;
import cn.cordys.crm.report.customerconversion.constants.CustomerConversionEventType;
import cn.cordys.crm.report.customerconversion.domain.CustomerConversionEvent;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 客户转化历史事件。业务删除不会调用本服务删除事件。
 */
@Service
public class CustomerConversionEventService {

    @Resource
    private BaseMapper<CustomerConversionEvent> eventMapper;

    public void recordContractSigned(Contract contract, String operator) {
        upsert(CustomerConversionEventType.CONTRACT_SIGNED, contract.getId(), contract, null,
                contract.getStartTime(), operator);
    }

    public void recordPaymentApproved(Contract contract, ContractPaymentRecord paymentRecord, String operator) {
        upsert(CustomerConversionEventType.PAYMENT_APPROVED, paymentRecord.getId(), contract, paymentRecord,
                paymentRecord.getFirstRepaymentTime(), operator);
    }

    private void upsert(CustomerConversionEventType type, String businessId, Contract contract,
                        ContractPaymentRecord paymentRecord, Long eventTime, String operator) {
        if (eventTime == null) {
            return;
        }
        CustomerConversionEvent existing = eventMapper.selectListByLambda(
                        new LambdaQueryWrapper<CustomerConversionEvent>()
                                .eq(CustomerConversionEvent::getOrganizationId, contract.getOrganizationId())
                                .eq(CustomerConversionEvent::getEventType, type.name())
                                .eq(CustomerConversionEvent::getBusinessId, businessId))
                .stream().findFirst().orElse(null);
        long now = System.currentTimeMillis();
        if (existing != null) {
            // 事件表是"每个业务每类事件一条的最新生效投影镜像"：
            // 编辑重提审批通过时整体覆盖为主表当前投影（签约人、部门、客户快照、业务时间），
            // 业务删除时事件保留（删除后报表仍可统计）。
            existing.setCustomerId(contract.getCustomerId());
            existing.setCustomerName(contract.getCustomerNameSnapshot());
            existing.setCustomerMobile(contract.getCustomerMobileSnapshot());
            existing.setCustomerSource(contract.getCustomerSourceSnapshot());
            existing.setSignerId(contract.getSignerId());
            existing.setSignerName(contract.getSignerNameSnapshot());
            existing.setSignerDeptId(contract.getSignerDeptIdSnapshot());
            existing.setSignerDeptName(contract.getSignerDeptNameSnapshot());
            existing.setEventTime(eventTime);
            existing.setStatDate(toDate(eventTime));
            existing.setUpdateTime(now);
            existing.setUpdateUser(operator);
            eventMapper.updateById(existing);
            return;
        }
        CustomerConversionEvent event = new CustomerConversionEvent();
        event.setId(IDGenerator.nextStr());
        event.setEventType(type.name());
        event.setBusinessId(businessId);
        event.setContractId(contract.getId());
        event.setPaymentRecordId(paymentRecord == null ? null : paymentRecord.getId());
        event.setCustomerId(contract.getCustomerId());
        event.setCustomerName(contract.getCustomerNameSnapshot());
        event.setCustomerMobile(contract.getCustomerMobileSnapshot());
        event.setCustomerSource(contract.getCustomerSourceSnapshot());
        event.setSignerId(contract.getSignerId());
        event.setSignerName(contract.getSignerNameSnapshot());
        event.setSignerDeptId(contract.getSignerDeptIdSnapshot());
        event.setSignerDeptName(contract.getSignerDeptNameSnapshot());
        event.setEventTime(eventTime);
        event.setStatDate(toDate(eventTime));
        event.setOrganizationId(contract.getOrganizationId());
        event.setCreateTime(now);
        event.setUpdateTime(now);
        event.setCreateUser(operator);
        event.setUpdateUser(operator);
        eventMapper.insert(event);
    }

    private LocalDate toDate(long eventTime) {
        return Instant.ofEpochMilli(eventTime).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
