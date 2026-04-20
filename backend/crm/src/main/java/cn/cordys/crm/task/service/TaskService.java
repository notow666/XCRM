package cn.cordys.crm.task.service;

import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.mapper.ExtCustomerStageConfigMapper;
import cn.cordys.crm.follow.constants.FollowUpPlanStatusType;
import cn.cordys.crm.follow.constants.FollowUpPlanType;
import cn.cordys.crm.follow.domain.FollowUpPlan;
import cn.cordys.crm.follow.dto.request.FollowUpPlanPageRequest;
import cn.cordys.crm.follow.dto.request.FollowUpPlanStatusRequest;
import cn.cordys.crm.follow.dto.response.FollowUpPlanListResponse;
import cn.cordys.crm.follow.mapper.ExtFollowUpPlanMapper;
import cn.cordys.crm.follow.service.FollowUpPlanService;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class TaskService {

    @Resource
    private ExtFollowUpPlanMapper extFollowUpPlanMapper;
    @Resource
    private FollowUpPlanService followUpPlanService;
    @Resource
    private BaseMapper<FollowUpPlan> followUpPlanMapper;
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private ExtCustomerStageConfigMapper extCustomerStageConfigMapper;

    public PagerWithOption<List<FollowUpPlanListResponse>> pageTaskFollowPlans(FollowUpPlanPageRequest request, String userId, String orgId) {
        request.setMyPlan(true);
        request.setTaskModuleQuery(true);
        request.setStatus("ALL");
        request.setCompletionStatus("UNCOMPLETED");

        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<String> resourceTypeList = Collections.singletonList(NotificationConstants.Module.CUSTOMER);
        List<FollowUpPlanListResponse> planList = extFollowUpPlanMapper.selectList(
                request, userId, orgId, null, FollowUpPlanType.CUSTOMER.name(), null, resourceTypeList);
        List<FollowUpPlanListResponse> enrichedList = followUpPlanService.buildListData(planList, orgId);
        enrichCustomerStage(enrichedList, orgId);
        Map<String, List<OptionDTO>> optionMap = followUpPlanService.buildOptionMap(orgId, planList, enrichedList);
        return PageUtils.setPageInfoWithOption(page, enrichedList, optionMap);
    }

    private void enrichCustomerStage(List<FollowUpPlanListResponse> list, String orgId) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<String> customerIds = list.stream()
                .map(FollowUpPlanListResponse::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (customerIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        qw.in(Customer::getId, customerIds);
        List<Customer> customers = customerMapper.selectListByLambda(qw);
        Map<String, Customer> customerMap = customers.stream().collect(Collectors.toMap(Customer::getId, c -> c));
        List<StageConfigResponse> stages = extCustomerStageConfigMapper.getStageConfigList(orgId);
        Map<String, String> stageNameMap = stages.stream()
                .collect(Collectors.toMap(StageConfigResponse::getId, StageConfigResponse::getName, (a, b) -> a));
        for (FollowUpPlanListResponse row : list) {
            Customer c = customerMap.get(row.getCustomerId());
            if (c != null && c.getStage() != null) {
                row.setCustomerStageId(c.getStage());
                row.setCustomerStageName(stageNameMap.getOrDefault(c.getStage(), ""));
            }
        }
    }

    public void completeFollowPlan(String planId, String userId, String orgId) {
        FollowUpPlan plan = followUpPlanMapper.selectByPrimaryKey(planId);
        if (plan == null) {
            throw new GenericException("plan_not_found");
        }
        if (!Strings.CS.equals(plan.getOrganizationId(), orgId)) {
            throw new GenericException(Translator.get("no.operation.permission"));
        }
        if (!FollowUpPlanType.CUSTOMER.name().equals(plan.getType())) {
            throw new GenericException(Translator.get("no.operation.permission"));
        }
        if (!Strings.CS.equals(plan.getOwner(), userId)) {
            throw new GenericException(Translator.get("task.complete.only_plan_owner"));
        }
        Customer customer = customerMapper.selectByPrimaryKey(plan.getCustomerId());
        if (customer == null) {
            throw new GenericException("customer.not.exist");
        }
        if (!Strings.CS.equals(customer.getOwner(), userId)) {
            throw new GenericException(Translator.get("task.complete.only_customer_owner"));
        }
        if (FollowUpPlanStatusType.COMPLETED.name().equals(plan.getStatus())) {
            return;
        }
        FollowUpPlanStatusRequest statusRequest = new FollowUpPlanStatusRequest();
        statusRequest.setId(planId);
        statusRequest.setStatus(FollowUpPlanStatusType.COMPLETED.name());
        followUpPlanService.updateStatus(statusRequest, userId);
    }
}
