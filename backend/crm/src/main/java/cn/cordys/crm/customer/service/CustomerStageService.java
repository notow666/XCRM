package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogContextInfo;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerStageConfig;
import cn.cordys.crm.customer.dto.request.CustomerStageAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageRollBackRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerNextStageResponse;
import cn.cordys.crm.customer.dto.response.CustomerStageConfigResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerStageConfigMapper;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import cn.cordys.common.service.BaseService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomerStageService {

    public static final Long DEFAULT_POS = 1L;

    /**
     * 阶段状态枚举
     */
    public static final String STATUS_NEW = "NEW";          // 待xxx
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";  // xxx中
    public static final String STATUS_COMPLETED = "COMPLETED";      // 已xxx
    public static final String STATUS_FAILED = "FAILED";            // 无效

    /**
     * 无效客户阶段类型
     */
    public static final String STAGE_TYPE_END = "END";

    @Resource
    private BaseMapper<CustomerStageConfig> customerStageConfigMapper;

    @Resource
    private ExtCustomerStageConfigMapper extCustomerStageConfigMapper;

    @Resource
    private ExtCustomerMapper extCustomerMapper;

    @Resource
    private BaseMapper<Customer> customerMapper;

    @Resource
    private BaseService baseService;

    public CustomerStageConfigResponse getStageConfigList(String orgId) {
        CustomerStageConfigResponse response = new CustomerStageConfigResponse();
        List<StageConfigResponse> stageConfigList = extCustomerStageConfigMapper.getStageConfigList(orgId);
        buildList(stageConfigList, response, orgId);
        return response;
    }

    private void buildList(List<StageConfigResponse> stageConfigList, CustomerStageConfigResponse response, String orgId) {
        List<CustomerStageConfigResponse.StageConfigItem> configItems = new java.util.ArrayList<>();
        if (CollectionUtils.isNotEmpty(stageConfigList)) {
            for (StageConfigResponse sc : stageConfigList) {
                CustomerStageConfigResponse.StageConfigItem item = new CustomerStageConfigResponse.StageConfigItem();
                item.setId(sc.getId());
                item.setName(sc.getName());
                item.setType(sc.getType());
                item.setRate(sc.getRate());
                item.setAfootRollBack(sc.getAfootRollBack());
                item.setEndRollBack(sc.getEndRollBack());
                item.setPos(sc.getPos());
                item.setStageHasData(extCustomerStageConfigMapper.countByStage(sc.getId(), response.getAfootRollBack() != null ? response.getAfootRollBack().toString() : orgId) > 0);
                item.setIsFixed(sc.getIsFixed() != null && sc.getIsFixed());
                configItems.add(item);
            }
            var first = stageConfigList.getFirst();
            response.setEndRollBack(first.getEndRollBack());
            response.setAfootRollBack(first.getAfootRollBack());
        }
        response.setStageConfigList(configItems);
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.ADD)
    public String addStageConfig(CustomerStageAddRequest request, String userId, String orgId) {
        checkConfigCount(orgId);
        Long pos = DEFAULT_POS;
        Boolean afootRollBack = true;
        Boolean endRollBack = false;

        CustomerStageConfig target = customerStageConfigMapper.selectByPrimaryKey(request.getTargetId());
        if (target != null) {
            pos = target.getPos();
            if (request.getDropPosition() == -1) {
                extCustomerStageConfigMapper.moveUpStageConfig(pos, orgId, DEFAULT_POS);
            } else {
                extCustomerStageConfigMapper.moveDownStageConfig(pos, orgId, DEFAULT_POS);
                pos = pos + 1;
            }
            afootRollBack = target.getAfootRollBack();
            endRollBack = target.getEndRollBack();
        }

        CustomerStageConfig stageConfig = new CustomerStageConfig();
        stageConfig.setId(IDGenerator.nextStr());
        stageConfig.setName(request.getName());
        stageConfig.setType(request.getType() != null ? request.getType() : "AFOOT");
        stageConfig.setRate(request.getRate());
        stageConfig.setAfootRollBack(afootRollBack);
        stageConfig.setEndRollBack(endRollBack);
        stageConfig.setPos(pos);
        stageConfig.setOrganizationId(orgId);
        stageConfig.setIsFixed(false);
        stageConfig.setCreateUser(userId);
        stageConfig.setUpdateUser(userId);
        stageConfig.setCreateTime(System.currentTimeMillis());
        stageConfig.setUpdateTime(System.currentTimeMillis());
        customerStageConfigMapper.insert(stageConfig);

        OperationLogContext.setContext(LogContextInfo.builder()
                .modifiedValue(stageConfig)
                .resourceId(stageConfig.getId())
                .resourceName(Translator.get("customer_stage_setting").concat(":").concat(request.getName()))
                .build());

        return stageConfig.getId();
    }

    private void checkConfigCount(String orgId) {
        if (extCustomerStageConfigMapper.countStageConfig(orgId) >= 10) {
            throw new GenericException(Translator.get("customer_stage_config_list"));
        }
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.DELETE, resourceId = "{#id}")
    public void delete(String id, String orgId) {
        CustomerStageConfig stageConfig = deletePreCheck(id, orgId);
        customerStageConfigMapper.deleteByPrimaryKey(id);
        OperationLogContext.setResourceName(Translator.get("customer_stage_setting").concat(":").concat(stageConfig.getName()));
    }

    private CustomerStageConfig deletePreCheck(String id, String orgId) {
        CustomerStageConfig stageConfig = customerStageConfigMapper.selectByPrimaryKey(id);
        if (stageConfig == null) {
            throw new GenericException(Translator.get("customer_stage_delete"));
        }

        // 固定节点不可删除
        if (Boolean.TRUE.equals(stageConfig.getIsFixed())) {
            throw new GenericException(Translator.get("customer_stage_fixed_cannot_delete"));
        }

        if (extCustomerStageConfigMapper.countByType("AFOOT", orgId) <= 1) {
            throw new GenericException(Translator.get("customer_stage_at_least_one"));
        }

        int customerCount = extCustomerMapper.countByStage(id, orgId);
        if (customerCount > 0) {
            throw new GenericException(Translator.get("customer_stage_has_customer").replace("{count}", String.valueOf(customerCount)));
        }

        return stageConfig;
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.UPDATE)
    public void updateRollBack(CustomerStageRollBackRequest request, String orgId) {
        LambdaQueryWrapper<CustomerStageConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerStageConfig::getOrganizationId, orgId);
        List<CustomerStageConfig> stageConfigList = customerStageConfigMapper.selectListByLambda(wrapper);
        extCustomerStageConfigMapper.updateRollBack(request, orgId);

        if (CollectionUtils.isNotEmpty(stageConfigList)) {
            Map<String, String> originalVal = new HashMap<>(1);
            originalVal.put("afootRollBack", Translator.get("log.enable.".concat(stageConfigList.getFirst().getAfootRollBack().toString())));
            originalVal.put("endRollBack", Translator.get("log.enable.".concat(stageConfigList.getFirst().getEndRollBack().toString())));
            Map<String, String> modifiedVal = new HashMap<>(1);
            modifiedVal.put("afootRollBack", Translator.get("log.enable.".concat(request.getAfootRollBack().toString())));
            modifiedVal.put("endRollBack", Translator.get("log.enable.".concat(request.getEndRollBack().toString())));
            OperationLogContext.setContext(LogContextInfo.builder()
                    .originalValue(originalVal)
                    .resourceName(Translator.get("customer_stage_setting"))
                    .modifiedValue(modifiedVal)
                    .resourceId(orgId)
                    .build());
        }
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.UPDATE)
    public void update(CustomerStageUpdateRequest request, String userId) {
        CustomerStageConfig oldStageConfig = customerStageConfigMapper.selectByPrimaryKey(request.getId());
        if (oldStageConfig == null) {
            throw new GenericException(Translator.get("customer_stage_not_exist"));
        }

        // 固定节点只能修改名称
        if (Boolean.TRUE.equals(oldStageConfig.getIsFixed())) {
            request.setRate(oldStageConfig.getRate());
        }

        extCustomerStageConfigMapper.updateStageConfig(request, userId);

        Map<String, String> originalVal = new HashMap<>(1);
        originalVal.put("stage", oldStageConfig.getName());
        originalVal.put("rate", oldStageConfig.getRate());
        Map<String, String> modifiedVal = new HashMap<>(1);
        modifiedVal.put("stage", request.getName());
        modifiedVal.put("rate", request.getRate());
        OperationLogContext.setContext(
                LogContextInfo.builder()
                        .resourceId(request.getId())
                        .resourceName(Translator.get("customer_stage_setting"))
                        .originalValue(originalVal)
                        .modifiedValue(modifiedVal)
                        .build()
        );
    }

    @OperationLog(module = LogModule.SYSTEM_MODULE, type = LogType.UPDATE)
    public void sort(List<String> ids, String orgId) {
        List<StageConfigResponse> oldStageConfigList = extCustomerStageConfigMapper.getStageConfigList(orgId);
        List<String> oldNames = oldStageConfigList.stream().map(StageConfigResponse::getName).toList();

        for (int i = 0; i < ids.size(); i++) {
            extCustomerStageConfigMapper.updatePos(ids.get(i), (long) (i + 1));
        }

        List<StageConfigResponse> newStageConfigList = extCustomerStageConfigMapper.getStageConfigList(orgId);
        List<String> newNames = newStageConfigList.stream().map(StageConfigResponse::getName).toList();

        Map<String, List<String>> originalVal = new HashMap<>(1);
        originalVal.put("stageSort", oldNames);
        Map<String, List<String>> modifiedVal = new HashMap<>(1);
        modifiedVal.put("stageSort", newNames);
        OperationLogContext.setContext(
                LogContextInfo.builder()
                        .resourceId(orgId)
                        .resourceName(Translator.get("customer_stage_setting"))
                        .originalValue(originalVal)
                        .modifiedValue(modifiedVal)
                        .build()
        );
    }

    public List<StageConfigResponse> getStageConfigListForSelect(String orgId) {
        return extCustomerStageConfigMapper.getStageConfigList(orgId);
    }

    /**
     * 获取默认阶段ID（pos最小的阶段）
     *
     * @param orgId 组织ID
     * @return 默认阶段ID
     */
    public String getDefaultStageId(String orgId) {
        List<StageConfigResponse> stageList = getStageConfigListForSelect(orgId);
        if (CollectionUtils.isNotEmpty(stageList)) {
            return stageList.getFirst().getId();
        }
        return null;
    }

    /**
     * 获取无效客户阶段ID
     * 优先匹配is_fixed=true且name包含"无效"的阶段，其次匹配type=END且name包含"无效"的阶段
     *
     * @param orgId 组织ID
     * @return 无效客户阶段ID
     */
    public String getFailStageId(String orgId) {
        List<StageConfigResponse> stageList = getStageConfigListForSelect(orgId);
        if (CollectionUtils.isNotEmpty(stageList)) {
            return stageList.stream()
                    .filter(s -> STAGE_TYPE_END.equals(s.getType()) && s.getName() != null && s.getName().contains("无效"))
                    .map(StageConfigResponse::getId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 获取回款阶段ID
     * 通过name精确匹配"stage_payment"
     *
     * @param orgId 组织ID
     * @return 回款阶段ID
     */
    public String getPaymentStageId(String orgId) {
        List<StageConfigResponse> stageList = getStageConfigListForSelect(orgId);
        if (CollectionUtils.isNotEmpty(stageList)) {
            return stageList.stream()
                    .filter(s -> STAGE_TYPE_END.equals(s.getType()) && s.getName() != null && s.getName().contains("无效客户"))
                    .map(StageConfigResponse::getId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 根据ID获取阶段配置
     *
     * @param id 阶段配置ID
     * @param orgId 组织ID
     * @return 阶段配置
     */
    public CustomerStageConfig getStageConfigById(String id, String orgId) {
        CustomerStageConfig stageConfig = customerStageConfigMapper.selectByPrimaryKey(id);
        if (stageConfig != null && orgId.equals(stageConfig.getOrganizationId())) {
            return stageConfig;
        }
        return null;
    }

    /**
     * 获取客户下一阶段信息（用于跟进计划表单预填）
     *
     * @param customerId 客户ID
     * @param orgId 组织ID
     * @return 下一阶段信息
     */
    public CustomerNextStageResponse getCustomerNextStage(String customerId, String orgId) {
        CustomerNextStageResponse response = new CustomerNextStageResponse();
        if (StringUtils.isBlank(customerId)) {
            return response;
        }

        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (customer == null) {
            return response;
        }

        response.setCustomerId(customer.getId());
        response.setCustomerName(customer.getName());
        response.setOwner(customer.getOwner());
        response.setCurrentStageId(customer.getStage());

        // 获取阶段列表
        List<StageConfigResponse> stageList = getStageConfigListForSelect(orgId);
        if (CollectionUtils.isEmpty(stageList)) {
            return response;
        }

        // 设置当前阶段名称
        for (StageConfigResponse stage : stageList) {
            if (stage.getId().equals(customer.getStage())) {
                response.setCurrentStageName(stage.getName());
                break;
            }
        }

        // 计算下一阶段
        for (int i = 0; i < stageList.size(); i++) {
            if (stageList.get(i).getId().equals(customer.getStage())) {
                if (i < stageList.size() - 1) {
                    response.setNextStageId(stageList.get(i + 1).getId());
                    response.setNextStageName(stageList.get(i + 1).getName());
                }
                break;
            }
        }

        // 设置负责人名称
        if (StringUtils.isNotBlank(customer.getOwner())) {
            Map<String, String> userNameMap = baseService.getUserNameMap(List.of(customer.getOwner()));
            response.setOwnerName(userNameMap.get(customer.getOwner()));
        }

        return response;
    }
}