package cn.cordys.crm.customer.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogContextInfo;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.CustomerStageConfig;
import cn.cordys.crm.customer.dto.request.CustomerStageAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageRollBackRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageUpdateRequest;
import cn.cordys.crm.customer.dto.response.CustomerStageConfigResponse;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.customer.mapper.ExtCustomerStageConfigMapper;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.Strings;
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

    @Resource
    private BaseMapper<CustomerStageConfig> customerStageConfigMapper;

    @Resource
    private ExtCustomerStageConfigMapper extCustomerStageConfigMapper;

    @Resource
    private ExtCustomerMapper extCustomerMapper;

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
        stageConfig.setType(request.getType());
        stageConfig.setRate(request.getRate());
        stageConfig.setAfootRollBack(afootRollBack);
        stageConfig.setEndRollBack(endRollBack);
        stageConfig.setPos(pos);
        stageConfig.setOrganizationId(orgId);
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
}