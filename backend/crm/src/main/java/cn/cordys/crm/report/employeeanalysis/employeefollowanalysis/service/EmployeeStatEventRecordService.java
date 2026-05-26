package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import cn.cordys.common.util.JSON;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.follow.domain.FollowUpRecord;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain.EmployeeStatEvent;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeStatEventType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums.EmployeeStatMetricType;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper.EmployeeStatAnalysisMapper;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.mapper.ExtUserMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 员工分析事件沉淀服务。
 * 当前先承接“入库事件”沉淀，统一做快照组装和批量入表。
 */
@Service
@Slf4j
public class EmployeeStatEventRecordService {

    @Resource
    private BaseMapper<EmployeeStatEvent> employeeStatEventBaseMapper;
    @Resource
    private BaseMapper<Customer> customerBaseMapper;
    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private EmployeeStatAnalysisMapper employeeStatAnalysisMapper;

    /**
     * 手工创建客户入库事件。
     * 供 CustomerService.add 这类单条创建场景直接一行调用。
     */
    public void recordCustomerCreateEvent(Customer customer, String operatorUserId, String organizationId) {
        recordCustomerCreateEvents(customer == null ? Collections.emptyList() : List.of(customer), operatorUserId, organizationId);
    }

    /**
     * 创建类入库事件。
     * 供手工创建、私海导入等场景统一调用。
     */
    public void recordCustomerCreateEvents(List<Customer> customers, String operatorUserId, String organizationId) {
        InboundEventRecordRequest request = new InboundEventRecordRequest();
        request.setEventType(EmployeeStatEventType.CREATE);
        request.setOrganizationId(organizationId);
        request.setOperatorUserId(operatorUserId);
        request.setSourceTableName("customer");
        request.setCreateUser(operatorUserId);
        request.setCustomers(customers == null ? Collections.emptyList() : customers);
        recordInboundEvents(request);
    }

    /**
     * 公海单个归属入库事件。
     * 供 ownCustomer 这类公共挂点直接一行调用。
     */
    public void recordPoolOwnEvent(Customer customer, String operatorUserId,
                                   String organizationId, EmployeeStatEventType eventType) {
        if (customer == null) {
            return;
        }
        InboundEventRecordRequest request = new InboundEventRecordRequest();
        request.setEventType(eventType);
        request.setOrganizationId(organizationId);
        request.setOperatorUserId(operatorUserId);
        request.setEventTime(customer.getCollectionTime());
        request.setSourceTableName("customer");
        request.setCreateUser(operatorUserId);
        request.setCustomers(List.of(customer));
        recordInboundEvents(request);
    }

    /**
     * 公海批量领取入库事件。
     * 供按筛选条件批量领取等场景一行调用。
     */
    public void recordPoolPickEvents(List<Customer> customers, String operatorUserId, String organizationId, Long eventTime) {
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        try {
            List<EmployeeStatEvent> events = buildPoolPickEvents(customers, operatorUserId, organizationId, eventTime);
            saveEvents(events);
        } catch (Exception e) {
            log.error("员工分析公海领取入库事件沉淀失败, organizationId={}, operatorUserId={}, sourceTableName={}",
                    organizationId, operatorUserId, "customer", e);
        }
    }

    /**
     * 公海分配入库事件。
     * 供批量分配、按条件批量分配等场景一行调用。
     */
    public void recordPoolAssignEvents(Map<String, List<Customer>> ownerCustomersMap,
                                       String operatorUserId, String organizationId, Long eventTime) {
        if (ownerCustomersMap == null || ownerCustomersMap.isEmpty()) {
            return;
        }
        try {
            List<EmployeeStatEvent> events = buildPoolAssignEvents(ownerCustomersMap, operatorUserId, organizationId, eventTime);
            saveEvents(events);
        } catch (Exception e) {
            log.error("员工分析公海分配入库事件沉淀失败, organizationId={}, operatorUserId={}, sourceTableName={}",
                    organizationId, operatorUserId, "customer", e);
        }
    }

    /**
     * 客户批量转移事件
     */
    public void recordOwnerTransferEvents(List<Customer> customers, String operatorUserId, String organizationId){
        if (CollectionUtils.isEmpty(customers)) {
            return;
        }
        try {
            List<EmployeeStatEvent> events = buildOwnerTransferEvents(customers, operatorUserId, organizationId);
            saveEvents(events);
        } catch (Exception e) {
            log.error("员工分析客户批量转移事件沉淀失败, organizationId={}, operatorUserId={}, sourceTableName={}",
                organizationId, operatorUserId, "customer", e);
        }
    }

    /**
     * 客户联系事件。
     * 统一供 FollowUpRecordService.add 成功后直接一行调用。
     */
    public void recordCustomerFollowEvent(FollowUpRecord followUpRecord, String organizationId) {
        recordCustomerFollowEvent(followUpRecord, organizationId, EmployeeStatEventType.MANUAL_FOLLOW);
    }

    /**
     * 客户联系事件。
     * 供手工跟进、电话/短信/微信自动补跟进等场景按事件类型一行调用。
     */
    public void recordCustomerFollowEvent(FollowUpRecord followUpRecord, String organizationId, EmployeeStatEventType eventType) {
        if (followUpRecord == null || StringUtils.isBlank(organizationId)) {
            return;
        }
        try {
            EmployeeStatEvent event = buildCustomerFollowEvent(followUpRecord, organizationId, eventType);
            if (event == null) {
                return;
            }
            saveEvents(List.of(event));
        } catch (Exception e) {
            log.error("员工分析客户联系事件沉淀失败, organizationId={}, followRecordId={}, sourceTableName={}",
                    organizationId, followUpRecord.getId(), "follow_up_record", e);
        }
    }

    /**
     * 新增微信好友待确认事件。
     * 发起添加微信好友请求成功后先落一条待确认事件，等待成功回调再更新为已确认。
     */
    public void recordWechatFriendPendingEvent(String bizExtInfo, Long eventTime) {
        if (StringUtils.isBlank(bizExtInfo)) {
            return;
        }
        try {
            EmployeeStatEvent event = buildWechatFriendPendingEvent(bizExtInfo, eventTime);
            if (event == null) {
                return;
            }
            saveEvents(List.of(event));
        } catch (Exception e) {
            log.error("员工分析新增微信好友待确认事件沉淀失败, organizationId={}, sourceTableName={}",
                    extractOrganizationId(bizExtInfo), "mmba_wx_friend_change_audit", e);
        }
    }

    /**
     * 新增微信好友成功确认。
     * 根据回调里的 um + friendPhone 去匹配待确认事件，命中唯一一条后更新为已确认。
     */
    public void confirmWechatFriendSuccessEvent(String um, String friendPhone, String esId, Long eventTime) {
        if (StringUtils.isAnyBlank(um, friendPhone, esId) || eventTime == null) {
            return;
        }
        try {
            List<String> operatorUserIds = listUserIdsByUm(um);
            if (CollectionUtils.isEmpty(operatorUserIds)) {
                log.error("员工分析新增微信好友成功确认失败，未找到UM对应用户 um={}, friendPhone={}, esId={}", um, friendPhone, esId);
                return;
            }
            List<EmployeeStatEvent> pendingEvents = employeeStatAnalysisMapper.listPendingWechatFriendEvents(
                    friendPhone, operatorUserIds
            );
            if (CollectionUtils.isEmpty(pendingEvents)) {
                log.error("员工分析新增微信好友成功确认失败，未找到待确认事件 um={}, friendPhone={}, esId={}, eventTime={}",
                        um, friendPhone, esId, eventTime);
                return;
            }
            if (pendingEvents.size() > 1) {
                log.warn("员工分析新增微信好友成功确认命中多条待确认事件，按最近一条确认 um={}, friendPhone={}, esId={}, eventIds={}",
                        um, friendPhone, esId,
                        pendingEvents.stream().map(EmployeeStatEvent::getId).collect(Collectors.joining(",")));
            }
            EmployeeStatEvent pendingEvent = pendingEvents.get(0);
            int updated = employeeStatAnalysisMapper.confirmWechatFriendSuccessEvent(
                    pendingEvent.getId(),
                    esId,
                    eventTime,
                    toStatDate(eventTime)
            );
            if (updated <= 0) {
                log.error("员工分析新增微信好友成功确认失败，待确认事件更新未生效 um={}, friendPhone={}, esId={}, eventId={}",
                        um, friendPhone, esId, pendingEvent.getId());
            }
        } catch (Exception e) {
            log.error("员工分析新增微信好友成功确认失败, um={}, friendPhone={}, esId={}", um, friendPhone, esId, e);
        }
    }


    /**
     * 统一沉淀入库事件。
     * 调用方只需要传入当前批次的公共上下文和已成功处理的客户集合，
     * 这里会统一批量补齐用户快照并写入事件表。
     */
    public void recordInboundEvents(InboundEventRecordRequest request) {
        try {
            List<EmployeeStatEvent> events = buildInboundEvents(request);
            saveEvents(events);
        } catch (Exception e) {
            log.error("员工分析入库事件沉淀失败, eventType={}, organizationId={}, operatorUserId={}, sourceTableName={}",
                    request == null || request.getEventType() == null ? null : request.getEventType().getValue(),
                    request == null ? null : request.getOrganizationId(),
                    request == null ? null : request.getOperatorUserId(),
                    request == null ? null : request.getSourceTableName(),
                    e);
        }
    }

    /**
     * 组装入库事件。
     */
    public List<EmployeeStatEvent> buildInboundEvents(InboundEventRecordRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getCustomers())) {
            return Collections.emptyList();
        }
        validateInboundRequest(request);

        Map<String, UserSnapshot> userSnapshotMap = loadUserSnapshotMap(
                request.getOrganizationId(),
                request.getOperatorUserId(),
                request.getCustomers()
        );
        Map<String, String> customerSourceMap = loadCustomerSourceMap(
                request.getCustomers().stream()
                        .filter(Objects::nonNull)
                        .map(Customer::getId)
                        .filter(StringUtils::isNotBlank)
                        .toList(),
                request.getOrganizationId()
        );

        UserSnapshot operatorSnapshot = userSnapshotMap.get(request.getOperatorUserId());
        long eventTime = defaultEventTime(request.getEventTime());
        long createTime = defaultCreateTime(request.getCreateTime(), eventTime);
        String statDate = toStatDate(eventTime);

        List<EmployeeStatEvent> events = new ArrayList<>(request.getCustomers().size());
        for (Customer customer : request.getCustomers()) {
            if (customer == null || StringUtils.isBlank(customer.getId()) || StringUtils.isBlank(customer.getOwner())) {
                continue;
            }
            UserSnapshot ownerSnapshot = userSnapshotMap.get(customer.getOwner());
            EmployeeStatEvent event = new EmployeeStatEvent();
            event.setId(IDGenerator.nextStr());
            event.setOrganizationId(request.getOrganizationId());
            event.setEventTime(eventTime);
            event.setStatDate(statDate);
            event.setMetricType(EmployeeStatMetricType.INBOUND.getValue());
            event.setEventType(request.getEventType().getValue());
            event.setCustomerId(customer.getId());
            event.setCustomerName(customer.getName());
            event.setCustomerMobile(customer.getMobile());
            event.setCustomerSource(customerSourceMap.get(customer.getId()));
            event.setOwnerUserId(customer.getOwner());
            if (ownerSnapshot != null) {
                event.setOwnerUserName(ownerSnapshot.getUserName());
                event.setOwnerDeptId(ownerSnapshot.getDepartmentId());
                event.setOwnerDeptName(ownerSnapshot.getDepartmentName());
            }
            event.setOperatorUserId(request.getOperatorUserId());
            if (operatorSnapshot != null) {
                event.setOperatorUserName(operatorSnapshot.getUserName());
                event.setOperatorDeptId(operatorSnapshot.getDepartmentId());
                event.setOperatorDeptName(operatorSnapshot.getDepartmentName());
            }
            event.setBizTraceId(request.getBizTraceId());
            event.setSourceTableName(request.getSourceTableName());
            event.setCreateUser(defaultCreateUser(request.getCreateUser(), request.getOperatorUserId()));
            event.setCreateTime(createTime);
            events.add(event);
        }
        return events;
    }

    /**
     * 批量落表。
     * 当前只做最基本的空集合保护和 batchInsert。
     */
    public void saveEvents(List<EmployeeStatEvent> events) {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }
        for (EmployeeStatEvent event : events) {
            if (event != null && event.getValidFlag() == null) {
                event.setValidFlag(1);
            }
        }
        employeeStatEventBaseMapper.batchInsert(events);
    }

    /**
     * 公海批量分配场景构建事件。
     * 这里以 ownerCustomersMap 的 key 作为最终负责人，不依赖 Customer 对象上的 owner 字段。
     */
    private List<EmployeeStatEvent> buildPoolAssignEvents(Map<String, List<Customer>> ownerCustomersMap,
                                                          String operatorUserId, String organizationId, Long eventTime) {
        Map<String, UserSnapshot> userSnapshotMap = loadUserSnapshotMap(organizationId, operatorUserId, ownerCustomersMap.keySet());
        Map<String, String> customerSourceMap = loadCustomerSourceMap(
                ownerCustomersMap.values().stream()
                        .filter(CollectionUtils::isNotEmpty)
                        .flatMap(Collection::stream)
                        .filter(Objects::nonNull)
                        .map(Customer::getId)
                        .filter(StringUtils::isNotBlank)
                        .toList(),
                organizationId
        );
        UserSnapshot operatorSnapshot = userSnapshotMap.get(operatorUserId);
        long actualEventTime = defaultEventTime(eventTime);
        long createTime = defaultCreateTime(null, actualEventTime);
        String statDate = toStatDate(actualEventTime);

        List<EmployeeStatEvent> events = new ArrayList<>();
        for (Map.Entry<String, List<Customer>> entry : ownerCustomersMap.entrySet()) {
            String ownerUserId = entry.getKey();
            if (StringUtils.isBlank(ownerUserId) || CollectionUtils.isEmpty(entry.getValue())) {
                continue;
            }
            UserSnapshot ownerSnapshot = userSnapshotMap.get(ownerUserId);
            for (Customer customer : entry.getValue()) {
                if (customer == null || StringUtils.isBlank(customer.getId())) {
                    continue;
                }
                EmployeeStatEvent event = new EmployeeStatEvent();
                event.setId(IDGenerator.nextStr());
                event.setOrganizationId(organizationId);
                event.setEventTime(actualEventTime);
                event.setStatDate(statDate);
                event.setMetricType(EmployeeStatMetricType.INBOUND.getValue());
                event.setEventType(EmployeeStatEventType.ASSIGN.getValue());
                event.setCustomerId(customer.getId());
                event.setCustomerName(customer.getName());
                event.setCustomerMobile(customer.getMobile());
                event.setCustomerSource(customerSourceMap.get(customer.getId()));
                event.setOwnerUserId(ownerUserId);
                if (ownerSnapshot != null) {
                    event.setOwnerUserName(ownerSnapshot.getUserName());
                    event.setOwnerDeptId(ownerSnapshot.getDepartmentId());
                    event.setOwnerDeptName(ownerSnapshot.getDepartmentName());
                }
                event.setOperatorUserId(operatorUserId);
                if (operatorSnapshot != null) {
                    event.setOperatorUserName(operatorSnapshot.getUserName());
                    event.setOperatorDeptId(operatorSnapshot.getDepartmentId());
                    event.setOperatorDeptName(operatorSnapshot.getDepartmentName());
                }
                event.setSourceTableName("customer");
                event.setCreateUser(operatorUserId);
                event.setCreateTime(createTime);
                events.add(event);
            }
        }
        return events;
    }

    /**
     * 公海批量领取场景构建事件。
     * 这里最终负责人固定为当前领取人，不依赖 Customer 对象上的 owner 字段。
     */
    private List<EmployeeStatEvent> buildPoolPickEvents(List<Customer> customers, String operatorUserId,
                                                        String organizationId, Long eventTime) {
        Map<String, UserSnapshot> userSnapshotMap = loadUserSnapshotMap(organizationId, operatorUserId, Collections.singleton(operatorUserId));
        Map<String, String> customerSourceMap = loadCustomerSourceMap(
                customers.stream()
                        .filter(Objects::nonNull)
                        .map(Customer::getId)
                        .filter(StringUtils::isNotBlank)
                        .toList(),
                organizationId
        );
        UserSnapshot operatorSnapshot = userSnapshotMap.get(operatorUserId);
        long actualEventTime = defaultEventTime(eventTime);
        long createTime = defaultCreateTime(null, actualEventTime);
        String statDate = toStatDate(actualEventTime);

        List<EmployeeStatEvent> events = new ArrayList<>(customers.size());
        for (Customer customer : customers) {
            if (customer == null || StringUtils.isBlank(customer.getId())) {
                continue;
            }
            EmployeeStatEvent event = new EmployeeStatEvent();
            event.setId(IDGenerator.nextStr());
            event.setOrganizationId(organizationId);
            event.setEventTime(actualEventTime);
            event.setStatDate(statDate);
            event.setMetricType(EmployeeStatMetricType.INBOUND.getValue());
            event.setEventType(EmployeeStatEventType.PICK.getValue());
            event.setCustomerId(customer.getId());
            event.setCustomerName(customer.getName());
            event.setCustomerMobile(customer.getMobile());
            event.setCustomerSource(customerSourceMap.get(customer.getId()));
            event.setOwnerUserId(operatorUserId);
            if (operatorSnapshot != null) {
                event.setOwnerUserName(operatorSnapshot.getUserName());
                event.setOwnerDeptId(operatorSnapshot.getDepartmentId());
                event.setOwnerDeptName(operatorSnapshot.getDepartmentName());
                event.setOperatorUserName(operatorSnapshot.getUserName());
                event.setOperatorDeptId(operatorSnapshot.getDepartmentId());
                event.setOperatorDeptName(operatorSnapshot.getDepartmentName());
            }
            event.setOperatorUserId(operatorUserId);
            event.setSourceTableName("customer");
            event.setCreateUser(operatorUserId);
            event.setCreateTime(createTime);
            events.add(event);
        }
        return events;
    }

    /**
     * 批量转移场景构建事件
     */
    private List<EmployeeStatEvent> buildOwnerTransferEvents(List<Customer> customers, String operatorUserId,
                                                        String organizationId) {
        Set<String> ownerUserIds = customers.stream()
                .filter(Objects::nonNull)
                .map(Customer::getOwner)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> customerSourceMap = loadCustomerSourceMap(
                customers.stream()
                        .filter(Objects::nonNull)
                        .map(Customer::getId)
                        .filter(StringUtils::isNotBlank)
                        .toList(),
                organizationId
        );
        Map<String, UserSnapshot> userSnapshotMap = loadUserSnapshotMap(organizationId, operatorUserId, ownerUserIds);
        UserSnapshot operatorSnapshot = userSnapshotMap.get(operatorUserId);

        List<EmployeeStatEvent> events = new ArrayList<>(customers.size());
        for (Customer customer : customers) {
            if (customer == null || StringUtils.isBlank(customer.getId())) {
                continue;
            }

            UserSnapshot ownerSnapshot = userSnapshotMap.get(customer.getOwner());

            String statDate = toStatDate(customer.getUpdateTime());
            EmployeeStatEvent event = new EmployeeStatEvent();
            event.setId(IDGenerator.nextStr());
            event.setOrganizationId(organizationId);
            event.setEventTime(customer.getUpdateTime());
            event.setStatDate(statDate);
            event.setMetricType(EmployeeStatMetricType.INBOUND.getValue());
            event.setEventType(EmployeeStatEventType.TRANSFER.getValue());
            event.setCustomerId(customer.getId());
            event.setCustomerName(customer.getName());
            event.setCustomerMobile(customer.getMobile());
            event.setCustomerSource(customerSourceMap.get(customer.getId()));
            if (ownerSnapshot != null) {
                event.setOwnerUserId(ownerSnapshot.getUserId());
                event.setOwnerUserName(ownerSnapshot.getUserName());
                event.setOwnerDeptId(ownerSnapshot.getDepartmentId());
                event.setOwnerDeptName(ownerSnapshot.getDepartmentName());
            }
            if (operatorSnapshot != null) {
                event.setOperatorUserId(operatorUserId);
                event.setOperatorUserName(operatorSnapshot.getUserName());
                event.setOperatorDeptId(operatorSnapshot.getDepartmentId());
                event.setOperatorDeptName(operatorSnapshot.getDepartmentName());
            }
            event.setSourceTableName("customer");
            event.setCreateUser(operatorUserId);
            event.setCreateTime(customer.getUpdateTime());
            events.add(event);
        }
        return events;
    }

    /**
     * 客户联系场景构建事件。
     * 联系事件类型由调用方传入，默认手工跟进。
     */
    private EmployeeStatEvent buildCustomerFollowEvent(FollowUpRecord followUpRecord, String organizationId,
                                                       EmployeeStatEventType eventType) {
        Customer customer = customerBaseMapper.selectByPrimaryKey(followUpRecord.getCustomerId());
        if (customer == null || StringUtils.isBlank(customer.getId())) {
            return null;
        }

        Set<String> userIds = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(followUpRecord.getCreateUser())) {
            userIds.add(followUpRecord.getCreateUser());
        }
        if (StringUtils.isNotBlank(followUpRecord.getOwner())) {
            userIds.add(followUpRecord.getOwner());
        }
        Map<String, UserSnapshot> userSnapshotMap = loadUserSnapshotMap(organizationId, null, userIds);
        UserSnapshot operatorSnapshot = userSnapshotMap.get(followUpRecord.getCreateUser());
        UserSnapshot ownerSnapshot = userSnapshotMap.get(followUpRecord.getOwner());

        long eventTime = defaultEventTime(followUpRecord.getCreateTime());
        EmployeeStatEvent event = new EmployeeStatEvent();
        event.setId(IDGenerator.nextStr());
        event.setOrganizationId(organizationId);
        event.setEventTime(eventTime);
        event.setStatDate(toStatDate(eventTime));
        event.setMetricType(EmployeeStatMetricType.CONTACT.getValue());
        event.setEventType((eventType == null ? EmployeeStatEventType.MANUAL_FOLLOW : eventType).getValue());
        event.setCustomerId(customer.getId());
        event.setCustomerName(customer.getName());
        event.setCustomerMobile(customer.getMobile());
        event.setCustomerSource(loadSingleCustomerSource(customer.getId(), organizationId));
        event.setSourceTableName("follow_up_record");
        event.setCreateUser(followUpRecord.getCreateUser());
        event.setBizTraceId(followUpRecord.getId());
        event.setCreateTime(defaultCreateTime(followUpRecord.getCreateTime(), eventTime));

        if (ownerSnapshot != null) {
            event.setOwnerUserId(ownerSnapshot.getUserId());
            event.setOwnerUserName(ownerSnapshot.getUserName());
            event.setOwnerDeptId(ownerSnapshot.getDepartmentId());
            event.setOwnerDeptName(ownerSnapshot.getDepartmentName());
        }
        if (operatorSnapshot != null) {
            event.setOperatorUserId(operatorSnapshot.getUserId());
            event.setOperatorUserName(operatorSnapshot.getUserName());
            event.setOperatorDeptId(operatorSnapshot.getDepartmentId());
            event.setOperatorDeptName(operatorSnapshot.getDepartmentName());
        }
        return event;
    }

    /**
     * 新增微信好友待确认场景构建事件。
     * 当前快照统一从发起时补齐的 bizExtInfo 解析，不再回查 customer / user。
     */
    private EmployeeStatEvent buildWechatFriendPendingEvent(String bizExtInfo, Long eventTime) {
        JsonNode bizExt = parseBizExtInfo(bizExtInfo);
        if (bizExt == null) {
            return null;
        }

        String organizationId = readBizExtText(bizExt, "organization_id", "organizationId");
        String customerId = readBizExtText(bizExt, "customer_id", "customerId");
        String ownerUserId = readBizExtText(bizExt, "owner_user_id", "ownerUserId");
        String operatorUserId = readBizExtText(bizExt, "operator_user_id", "operatorUserId");
        if (StringUtils.isBlank(organizationId) || StringUtils.isBlank(customerId)
                || StringUtils.isBlank(ownerUserId) || StringUtils.isBlank(operatorUserId)) {
            return null;
        }

        long actualEventTime = defaultEventTime(eventTime);
        EmployeeStatEvent event = new EmployeeStatEvent();
        event.setId(IDGenerator.nextStr());
        event.setOrganizationId(organizationId);
        event.setEventTime(actualEventTime);
        event.setStatDate(toStatDate(actualEventTime));
        event.setMetricType(EmployeeStatMetricType.WECHAT_FRIEND.getValue());
        event.setEventType(EmployeeStatEventType.WECHAT_FRIEND_SUCCESS.getValue());
        event.setCustomerId(customerId);
        event.setCustomerName(readBizExtText(bizExt, "customer_name", "customerName"));
        event.setCustomerMobile(readBizExtText(bizExt, "customer_mobile", "customerMobile"));
        event.setCustomerSource(readBizExtText(bizExt, "customer_source", "customerSource"));
        event.setOwnerUserId(ownerUserId);
        event.setOwnerUserName(readBizExtText(bizExt, "owner_user_name", "ownerUserName"));
        event.setOwnerDeptId(readBizExtText(bizExt, "owner_dept_id", "ownerDeptId"));
        event.setOwnerDeptName(readBizExtText(bizExt, "owner_dept_name", "ownerDeptName"));
        event.setOperatorUserId(operatorUserId);
        event.setOperatorUserName(readBizExtText(bizExt, "operator_user_name", "operatorUserName"));
        event.setOperatorDeptId(readBizExtText(bizExt, "operator_dept_id", "operatorDeptId"));
        event.setOperatorDeptName(readBizExtText(bizExt, "operator_dept_name", "operatorDeptName"));
        event.setSourceTableName("mmba_wx_friend_change_audit");
        event.setValidFlag(0);
        event.setCreateUser(operatorUserId);
        event.setCreateTime(System.currentTimeMillis());
        return event;
    }

    private String extractOrganizationId(String bizExtInfo) {
        JsonNode bizExt = parseBizExtInfo(bizExtInfo);
        return readBizExtText(bizExt, "organization_id", "organizationId");
    }

    private String loadSingleCustomerSource(String customerId, String organizationId) {
        if (StringUtils.isBlank(customerId) || StringUtils.isBlank(organizationId)) {
            return null;
        }
        return loadCustomerSourceMap(List.of(customerId), organizationId).get(customerId);
    }

    private Map<String, String> loadCustomerSourceMap(Collection<String> customerIds, String organizationId) {
        if (CollectionUtils.isEmpty(customerIds) || StringUtils.isBlank(organizationId)) {
            return Collections.emptyMap();
        }
        List<String> distinctCustomerIds = customerIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(distinctCustomerIds)) {
            return Collections.emptyMap();
        }
        List<cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisCustomerContextRow> rows =
                employeeStatAnalysisMapper.listCustomerSourceRows(organizationId, distinctCustomerIds);
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyMap();
        }
        Map<String, String> customerSourceMap = new LinkedHashMap<>();
        for (cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisCustomerContextRow row : rows) {
            if (row == null || StringUtils.isBlank(row.getCustomerId())) {
                continue;
            }
            customerSourceMap.put(row.getCustomerId(), row.getCustomerSource());
        }
        return customerSourceMap;
    }

    /**
     * 批量加载本次事件所需的用户快照。
     */
    private Map<String, UserSnapshot> loadUserSnapshotMap(String organizationId, String operatorUserId, List<Customer> customers) {
        Set<String> userIds = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(operatorUserId)) {
            userIds.add(operatorUserId);
        }
        for (Customer customer : customers) {
            if (customer != null && StringUtils.isNotBlank(customer.getOwner())) {
                userIds.add(customer.getOwner());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> userIdList = new ArrayList<>(userIds);
        List<User> users = userBaseMapper.selectByIds(userIdList.toArray(new String[0]));
        List<UserDeptDTO> userDeptList = extUserMapper.getUserDeptByUserIds(userIdList, organizationId);
        if (CollectionUtils.isEmpty(users) && CollectionUtils.isEmpty(userDeptList)) {
            return Collections.emptyMap();
        }

        Map<String, UserSnapshot> snapshotMap = new LinkedHashMap<>(userIdList.size());
        if (CollectionUtils.isNotEmpty(users)) {
            for (User user : users) {
                if (user == null || StringUtils.isBlank(user.getId())) {
                    continue;
                }
                UserSnapshot snapshot = snapshotMap.computeIfAbsent(user.getId(), key -> new UserSnapshot());
                snapshot.setUserId(user.getId());
                snapshot.setUserName(user.getName());
            }
        }
        if (CollectionUtils.isNotEmpty(userDeptList)) {
            for (UserDeptDTO userDept : userDeptList) {
                if (userDept == null || StringUtils.isBlank(userDept.getUserId())) {
                    continue;
                }
                UserSnapshot snapshot = snapshotMap.computeIfAbsent(userDept.getUserId(), key -> new UserSnapshot());
                snapshot.setUserId(userDept.getUserId());
                snapshot.setDepartmentId(userDept.getDeptId());
                snapshot.setDepartmentName(userDept.getDeptName());
            }
        }
        snapshotMap.entrySet().removeIf(entry -> StringUtils.isBlank(entry.getValue().getUserId()));
        return snapshotMap;
    }

    /**
     * 按用户ID集合批量加载快照。
     * 供“负责人在 map key 中、Customer 对象仍是旧值”的特殊场景复用。
     */
    private Map<String, UserSnapshot> loadUserSnapshotMap(String organizationId, String operatorUserId, Set<String> ownerUserIds) {
        Set<String> userIds = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(operatorUserId)) {
            userIds.add(operatorUserId);
        }
        if (CollectionUtils.isNotEmpty(ownerUserIds)) {
            for (String ownerUserId : ownerUserIds) {
                if (StringUtils.isNotBlank(ownerUserId)) {
                    userIds.add(ownerUserId);
                }
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> userIdList = new ArrayList<>(userIds);
        List<User> users = userBaseMapper.selectByIds(userIdList.toArray(new String[0]));
        List<UserDeptDTO> userDeptList = extUserMapper.getUserDeptByUserIds(userIdList, organizationId);
        if (CollectionUtils.isEmpty(users) && CollectionUtils.isEmpty(userDeptList)) {
            return Collections.emptyMap();
        }

        Map<String, UserSnapshot> snapshotMap = new LinkedHashMap<>(userIdList.size());
        if (CollectionUtils.isNotEmpty(users)) {
            for (User user : users) {
                if (user == null || StringUtils.isBlank(user.getId())) {
                    continue;
                }
                UserSnapshot snapshot = snapshotMap.computeIfAbsent(user.getId(), key -> new UserSnapshot());
                snapshot.setUserId(user.getId());
                snapshot.setUserName(user.getName());
            }
        }
        if (CollectionUtils.isNotEmpty(userDeptList)) {
            for (UserDeptDTO userDept : userDeptList) {
                if (userDept == null || StringUtils.isBlank(userDept.getUserId())) {
                    continue;
                }
                UserSnapshot snapshot = snapshotMap.computeIfAbsent(userDept.getUserId(), key -> new UserSnapshot());
                snapshot.setUserId(userDept.getUserId());
                snapshot.setDepartmentId(userDept.getDeptId());
                snapshot.setDepartmentName(userDept.getDeptName());
            }
        }
        snapshotMap.entrySet().removeIf(entry -> StringUtils.isBlank(entry.getValue().getUserId()));
        return snapshotMap;
    }

    private List<String> listUserIdsByUm(String um) {
        String normalizedUm = StringUtils.trimToNull(um);
        if (normalizedUm == null) {
            return List.of();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUm, normalizedUm);
        return userBaseMapper.selectListByLambda(wrapper).stream()
                .map(User::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private void validateInboundRequest(InboundEventRecordRequest request) {
        if (request.getEventType() == null) {
            throw new IllegalArgumentException("eventType cannot be null");
        }
        if (StringUtils.isBlank(request.getOrganizationId())) {
            throw new IllegalArgumentException("organizationId cannot be blank");
        }
        if (StringUtils.isBlank(request.getOperatorUserId())) {
            throw new IllegalArgumentException("operatorUserId cannot be blank");
        }
        if (StringUtils.isBlank(request.getSourceTableName())) {
            throw new IllegalArgumentException("sourceTableName cannot be blank");
        }
    }

    private long defaultEventTime(Long eventTime) {
        return eventTime == null ? System.currentTimeMillis() : eventTime;
    }

    private long defaultCreateTime(Long createTime, long eventTime) {
        return createTime == null ? eventTime : createTime;
    }

    private String defaultCreateUser(String createUser, String operatorUserId) {
        return StringUtils.defaultIfBlank(createUser, operatorUserId);
    }

    private String toStatDate(long eventTime) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(eventTime), ZoneId.systemDefault()).toString();
    }

    private JsonNode parseBizExtInfo(String bizExtInfo) {
        if (StringUtils.isBlank(bizExtInfo)) {
            return null;
        }
        try {
            return JSON.parseObject(bizExtInfo, JsonNode.class);
        } catch (Exception e) {
            log.warn("员工分析 bizExtInfo 解析失败 bizExtInfo={}", bizExtInfo, e);
            return null;
        }
    }

    private String readBizExtText(JsonNode bizExt, String primaryField, String fallbackField) {
        if (bizExt == null) {
            return null;
        }
        String value = StringUtils.trimToNull(bizExt.path(primaryField).asText(null));
        if (value != null) {
            return value;
        }
        return StringUtils.trimToNull(bizExt.path(fallbackField).asText(null));
    }

    @Data
    public static class InboundEventRecordRequest {
        /**
         * 当前批次对应的事件类型，例如 CREATE / PICK / ASSIGN / TRANSFER。
         */
        private EmployeeStatEventType eventType;
        /**
         * 组织ID。
         */
        private String organizationId;
        /**
         * 当前操作人ID。
         */
        private String operatorUserId;
        /**
         * 本次事件时间。
         * 批量场景一般传同一批次实际落库时间，单条场景可传业务真实时间。
         */
        private Long eventTime;
        /**
         * 业务链路标识。
         */
        private String bizTraceId;
        /**
         * 来源表名。
         */
        private String sourceTableName;
        /**
         * 创建人。
         * 若不传，默认使用 operatorUserId。
         */
        private String createUser;
        /**
         * 创建时间。
         * 若不传，默认使用 eventTime。
         */
        private Long createTime;
        /**
         * 已成功处理、需要沉淀的客户集合。
         */
        private List<Customer> customers;
    }

    @Data
    private static class UserSnapshot {
        private String userId;
        private String userName;
        private String departmentId;
        private String departmentName;
    }
}
