package cn.cordys.crm.contract.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.CommonResultCode;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.*;
import cn.cordys.common.dto.condition.BaseCondition;
import cn.cordys.common.dto.condition.FilterCondition;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.permission.PermissionCache;
import cn.cordys.common.permission.PermissionUtils;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.uid.SerialNumGenerator;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.contract.constants.ContractApprovalStatus;
import cn.cordys.crm.contract.constants.ContractStage;
import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.contract.domain.ContractProduct;
import cn.cordys.crm.contract.domain.ContractPaymentRecord;
import cn.cordys.crm.contract.domain.ContractSnapshot;
import cn.cordys.crm.contract.domain.ContractVersion;
import cn.cordys.crm.contract.dto.ContractVersionSnapshot;
import cn.cordys.crm.contract.dto.request.*;
import cn.cordys.crm.contract.dto.response.ContractGetResponse;
import cn.cordys.crm.contract.dto.response.ContractListResponse;
import cn.cordys.crm.contract.dto.response.ContractStatisticResponse;
import cn.cordys.crm.contract.dto.response.CustomerContractStatisticResponse;
import cn.cordys.crm.contract.mapper.ExtContractInvoiceMapper;
import cn.cordys.crm.contract.mapper.ExtContractMapper;
import cn.cordys.crm.contract.mapper.ExtContractSnapshotMapper;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.opportunity.constants.ApprovalState;
import cn.cordys.crm.system.constants.DictModule;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.domain.MessageTaskConfig;
import cn.cordys.crm.system.dto.MessageTaskConfigDTO;
import cn.cordys.crm.system.dto.field.SerialNumberField;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.response.BatchAffectSkipResponse;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.notice.CommonNoticeSendService;
import cn.cordys.crm.system.service.DictService;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContractService {

    @Resource
    private ContractFieldService contractFieldService;
    @Resource
    private BaseMapper<Contract> contractMapper;
    @Resource
    private BaseService baseService;
    @Resource
    private ModuleFormService moduleFormService;
    @Resource
    private BaseMapper<ContractSnapshot> snapshotBaseMapper;
    @Resource
    private ExtContractMapper extContractMapper;
    @Resource
    private ModuleFormCacheService moduleFormCacheService;
    @Resource
    private PermissionCache permissionCache;
    @Resource
    private BaseMapper<Customer> customerBaseMapper;
    @Resource
    private LogService logService;
    @Resource
    private SerialNumGenerator serialNumGenerator;
    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private CommonNoticeSendService commonNoticeSendService;
    @Resource
    private BaseMapper<MessageTaskConfig> messageTaskConfigMapper;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private BaseMapper<ContractPaymentRecord> contractPaymentRecordMapper;
    @Resource
    private ExtContractInvoiceMapper extContractInvoiceMapper;
    @Resource
    private DictService dictService;
    @Resource
    private ContractTransactionService contractTransactionService;
    @Resource
    private BaseMapper<ContractVersion> contractVersionMapper;
    @Resource
    private BaseMapper<ContractProduct> contractProductMapper;

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999");

    /**
     * 新建合同
     *
     * @param request
     * @param operatorId
     * @param orgId
     * @return
     */
    @OperationLog(module = LogModule.CONTRACT_INDEX, type = LogType.ADD, resourceName = "{#request.name}")
    public Contract add(ContractAddRequest request, String operatorId, String orgId) {
        ModuleFormConfigDTO currentForm = getFormConfig(orgId);
        String contractNumber = createContractNumber(currentForm, orgId, request.getNumber());
        return contractTransactionService.add(request, currentForm, contractNumber, operatorId, orgId);
    }

    private String createContractNumber(ModuleFormConfigDTO moduleFormConfigDTO, String orgId, String prefix) {
        BaseField numberField = moduleFormConfigDTO.getFields().stream()
                .filter(field -> field.isSerialNumber() && StringUtils.isNotEmpty(field.getBusinessKey())).findFirst().orElse(null);

        if (numberField instanceof SerialNumberField serialField) {
            return serialNumGenerator.generateByRules(serialField.getSerialNumberRules(prefix), orgId, FormKey.CONTRACT.getKey());
        }
        return null;
    }


    /**
     * 保存合同快照
     *
     * @param contract
     * @param moduleFormConfigDTO
     * @param response
     */
    private void saveSnapshot(Contract contract, ModuleFormConfigDTO moduleFormConfigDTO, ContractGetResponse response) {
        //移除response中moduleFields 集合里 的 BaseModuleFieldValue 的 fieldId="products"的数据，避免快照数据过大
        if (CollectionUtils.isNotEmpty(response.getModuleFields())) {
            response.setModuleFields(response.getModuleFields().stream()
                    .filter(field -> (field.getFieldValue() != null && StringUtils.isNotBlank(field.getFieldValue().toString()) && !"[]".equals(field.getFieldValue().toString()))).toList());
        }
        ContractSnapshot snapshot = new ContractSnapshot();
        snapshot.setId(IDGenerator.nextStr());
        snapshot.setContractId(contract.getId());
        snapshot.setContractProp(JSON.toJSONString(moduleFormConfigDTO));
        snapshot.setContractValue(JSON.toJSONString(response));
        snapshotBaseMapper.insert(snapshot);

    }

    public ContractGetResponse getWithDataPermissionCheck(String id, String userId, String orgId) {
        ContractGetResponse getResponse = get(id);
        if (getResponse == null) {
            throw new GenericException(Translator.get("resource.not.exist"));
        }
        dataScopeService.checkDataPermission(userId, orgId, getResponse.getOwner(), PermissionConstants.CONTRACT_READ);
        return getResponse;
    }

    public ContractGetResponse getSnapshotWithDataPermissionCheck(String id, String userId, String orgId) {
        ContractGetResponse getResponse = getSnapshot(id);
        if (getResponse == null) {
            throw new GenericException(Translator.get("resource.not.exist"));
        }
        dataScopeService.checkDataPermission(userId, orgId, getResponse.getOwner(), PermissionConstants.CONTRACT_READ);
        return getResponse;
    }

    private ContractGetResponse get(Contract contract, List<BaseModuleFieldValue> contractFields, ModuleFormConfigDTO contractFormConfig) {
        ContractGetResponse contractGetResponse = BeanUtils.copyBean(new ContractGetResponse(), contract);
        contractGetResponse = baseService.setCreateUpdateOwnerUserName(contractGetResponse);
        contractGetResponse.setCustomerName(contract.getCustomerNameSnapshot());
        contractGetResponse.setOwnerName(contract.getOwnerNameSnapshot());
        contractGetResponse.setDisplayStage(StringUtils.isNotBlank(contract.getPendingVersionId())
                ? ContractStage.PENDING_SIGNING.name() : contract.getStage());

        String id = contract.getId();
        // 获取模块字段
        moduleFormService.processBusinessFieldValues(contractGetResponse, contractFields, contractFormConfig);
        List<BaseField> flattenFormFields = moduleFormService.getFlattenFormFields(
                FormKey.CONTRACT.getKey(), contract.getOrganizationId());
        contractFields = contractFieldService.setBusinessRefFieldValue(List.of(contractGetResponse),
                flattenFormFields, new HashMap<>(Map.of(id, contractFields))).get(id);
        if (contractFields == null) {
            contractFields = new ArrayList<>();
        }
        applyCustomerSnapshotModuleFields(contract, contractFields, flattenFormFields);

        Map<String, List<OptionDTO>> optionMap = moduleFormService.getOptionMap(contractFormConfig, contractFields);

        // 补充负责人选项
        List<OptionDTO> ownerFieldOption = moduleFormService.getBusinessFieldOption(contractGetResponse,
                ContractGetResponse::getOwner, ContractGetResponse::getOwnerName);
        optionMap.put(BusinessModuleField.CONTRACT_OWNER.getBusinessKey(), ownerFieldOption);

        optionMap.put(BusinessModuleField.CONTRACT_CUSTOMER_NAME.getBusinessKey(),
                Collections.singletonList(new OptionDTO(contract.getCustomerId(), contract.getCustomerNameSnapshot())));
        optionMap.put(BusinessModuleField.CONTRACT_SIGNER.getBusinessKey(),
                Collections.singletonList(new OptionDTO(contract.getSignerId(), contract.getSignerNameSnapshot())));

        contractGetResponse.setOptionMap(optionMap);
        contractGetResponse.setModuleFields(contractFields);

        contractGetResponse.setDepartmentId(contract.getSignerDeptIdSnapshot());
        contractGetResponse.setDepartmentName(contract.getSignerDeptNameSnapshot());

        // 附件信息
        contractGetResponse.setAttachmentMap(moduleFormService.getAttachmentMap(contractFormConfig, contractFields));
        contractGetResponse.setAlreadyPayAmount(sumContractRecordAmount(id));
        contractGetResponse.setProducts(getContractProductMaps(id));
        List<ContractVersion> versions = getVersionHistory(id);
        contractGetResponse.setVersionHistory(versions);
        contractGetResponse.setVersionUserNameMap(getVersionUserNameMap(versions));

        return contractGetResponse;
    }

    /**
     * 获取合同详情
     *
     * @param id
     * @return
     */
    public ContractGetResponse get(String id) {
        Contract contract = contractMapper.selectByPrimaryKey(id);
        if (contract == null) {
            return null;
        }
        ContractVersion displayVersion = getPendingDisplayVersion(contract);
        if (displayVersion != null) {
            return buildVersionDisplayResponse(contract, displayVersion);
        }
        // 获取模块字段
        ModuleFormConfigDTO contractFormConfig = getFormConfig(contract.getOrganizationId());
        List<BaseModuleFieldValue> contractFields = contractFieldService.getModuleFieldValuesByResourceId(id);
        return get(contract, contractFields, contractFormConfig);
    }

    /**
     * 编辑合同
     *
     * @param request
     * @param userId
     * @param orgId
     * @return
     */
    @OperationLog(module = LogModule.CONTRACT_INDEX, type = LogType.UPDATE, resourceId = "{#request.id}")
    public Contract update(ContractUpdateRequest request, String userId, String orgId) {
        return contractTransactionService.update(request, getFormConfig(orgId), userId, orgId);
    }

    private void setAmount(String amount, Contract contract) {
        if (StringUtils.isNotBlank(amount)) {
            contract.setAmount(new BigDecimal(amount));
            if (contract.getAmount().compareTo(MAX_AMOUNT) > 0) {
                throw new GenericException(Translator.get("contract.amount.exceed.max"));
            }
        } else {
            contract.setAmount(BigDecimal.ZERO);
        }
    }


    /**
     * 更新自定义字段
     *
     * @param moduleFields
     * @param contract
     * @param orgId
     * @param userId
     */
    private void updateFields(List<BaseModuleFieldValue> moduleFields, Contract contract, String orgId, String userId) {
        if (moduleFields == null) {
            return;
        }
        contractFieldService.deleteByResourceId(contract.getId());
        contractFieldService.saveModuleField(contract, orgId, userId, moduleFields, true);
    }


    /**
     * 删除合同
     *
     * @param id 合同ID
     */
    @OperationLog(module = LogModule.CONTRACT_INDEX, type = LogType.DELETE, resourceId = "{#id}")
    public void delete(String id, String userId, String orgId) {
        Contract contract = contractTransactionService.delete(id, userId, orgId);
        OperationLogContext.setResourceName(contract.getName());
    }


    /**
     * ⚠️反射调用; 勿修改入参, 返回, 方法名!
     *
     * @param id 合同ID
     * @return 合同详情
     */
    public ContractGetResponse getSnapshot(String id) {
        Contract contract = contractMapper.selectByPrimaryKey(id);
        if (contract == null) {
            return null;
        }
        ContractVersion version = getApprovalDisplayVersion(contract);
        if (version == null) {
            return get(id);
        }
        return buildVersionDisplayResponse(contract, version);
    }

    private ContractGetResponse buildVersionDisplayResponse(Contract contract, ContractVersion version) {
        ContractVersionSnapshot snapshot = JSON.parseObject(version.getValueSnapshot(), ContractVersionSnapshot.class);
        Contract displayContract = BeanUtils.copyBean(new Contract(), contract);
        applySnapshotForDisplay(displayContract, snapshot);
        ModuleFormConfigDTO formConfig = StringUtils.isBlank(version.getFormSnapshot())
                ? getFormConfig(contract.getOrganizationId())
                : JSON.parseObject(version.getFormSnapshot(), ModuleFormConfigDTO.class);
        ContractGetResponse response = get(displayContract,
                snapshot.getModuleFields() == null ? new ArrayList<>() : snapshot.getModuleFields(), formConfig);
        response.setApprovalVersion(version);
        response.setProducts(toMapList(snapshot.getProducts()));
        Customer customer = customerBaseMapper.selectByPrimaryKey(contract.getCustomerId());
        if (customer != null) {
            response.setInCustomerPool(customer.getInSharedPool());
            response.setPoolId(customer.getPoolId());
        }
        response.setAlreadyPayAmount(sumContractRecordAmount(contract.getId()));
        return response;
    }


    /**
     * 合同列表
     *
     * @param request
     * @param userId
     * @param orgId
     * @param deptDataPermission
     * @return
     */
    public PagerWithOption<List<ContractListResponse>> list(ContractPageRequest request, String userId, String orgId, DeptDataPermissionDTO deptDataPermission, Boolean source) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<ContractListResponse> list = extContractMapper.list(request, orgId, userId, deptDataPermission, source);
        List<ContractListResponse> results = buildList(list, orgId);
        ModuleFormConfigDTO customerFormConfig = getFormConfig(orgId);
        Map<String, List<OptionDTO>> optionMap = buildOptionMap(list, results, customerFormConfig);

        return PageUtils.setPageInfoWithOption(page, results, optionMap);
    }

    private Map<String, List<OptionDTO>> buildOptionMap(List<ContractListResponse> list, List<ContractListResponse> buildList,
                                                        ModuleFormConfigDTO formConfig) {
        // 获取所有模块字段的值
        List<BaseModuleFieldValue> moduleFieldValues = moduleFormService.getBaseModuleFieldValues(list, ContractListResponse::getModuleFields);
        // 获取选项值对应的 option
        Map<String, List<OptionDTO>> optionMap = moduleFormService.getOptionMap(formConfig, moduleFieldValues);
        // 补充负责人选项
        List<OptionDTO> ownerFieldOption = moduleFormService.getBusinessFieldOption(buildList,
                ContractListResponse::getOwner, ContractListResponse::getOwnerName);
        optionMap.put(BusinessModuleField.CONTRACT_OWNER.getBusinessKey(), ownerFieldOption);
        optionMap.put(BusinessModuleField.CONTRACT_SIGNER.getBusinessKey(),
                moduleFormService.getBusinessFieldOption(buildList,
                        ContractListResponse::getSignerId, ContractListResponse::getSignerNameSnapshot));
        optionMap.put(BusinessModuleField.CONTRACT_CUSTOMER_NAME.getBusinessKey(),
                moduleFormService.getBusinessFieldOption(buildList,
                        ContractListResponse::getCustomerId, ContractListResponse::getCustomerNameSnapshot));
        return optionMap;
    }

    private ModuleFormConfigDTO getFormConfig(String orgId) {
        return moduleFormCacheService.getBusinessFormConfig(FormKey.CONTRACT.getKey(), orgId);
    }

    public List<ContractListResponse> buildList(List<ContractListResponse> list, String orgId) {
        if (CollectionUtils.isEmpty(list)) {
            return list;
        }

        List<String> contractIds = list.stream().map(ContractListResponse::getId)
                .collect(Collectors.toList());
        Map<String, List<BaseModuleFieldValue>> contractFiledMap = new HashMap<>(
                contractFieldService.getResourceFieldMap(contractIds, true));
        mergeDisplayVersionSnapshots(list, contractFiledMap);
        List<BaseField> flattenFormFields = moduleFormService.getFlattenFormFields(FormKey.CONTRACT.getKey(), orgId);
        Map<String, List<BaseModuleFieldValue>> resolvefieldValueMap = contractFieldService.setBusinessRefFieldValue(
                list, flattenFormFields, contractFiledMap);


        list.forEach(item -> {
            item.setOwnerName(item.getOwnerNameSnapshot());
            item.setCustomerName(item.getCustomerNameSnapshot());
            item.setDisplayStage(StringUtils.isNotBlank(item.getPendingVersionId())
                    ? ContractStage.PENDING_SIGNING.name() : item.getStage());
            item.setDepartmentId(item.getSignerDeptIdSnapshot());
            item.setDepartmentName(item.getSignerDeptNameSnapshot());
            // 获取自定义字段
            List<BaseModuleFieldValue> contractFields = resolvefieldValueMap.computeIfAbsent(
                    item.getId(), key -> new ArrayList<>());
            applyCustomerSnapshotModuleFields(item, contractFields, flattenFormFields);
            item.setModuleFields(contractFields);
        });
        return baseService.setCreateAndUpdateUserName(list);
    }

    /**
     * 合同中的客户基础信息使用创建合同时保存的快照。
     * 数据源引用字段默认会实时查询客户表；客户被删除或客户信息发生变化后，
     * 合同列表和详情仍应展示合同自身保存的手机号快照。
     */
    private void applyCustomerSnapshotModuleFields(Contract contract,
                                                    List<BaseModuleFieldValue> moduleFields,
                                                    List<BaseField> formFields) {
        formFields.stream()
                .filter(field -> StringUtils.isNotBlank(field.getResourceFieldId()))
                .filter(field -> Strings.CS.equals(field.getInternalKey(),
                        BusinessModuleField.CUSTOMER_MOBILE.getKey()))
                .findFirst()
                .ifPresent(field -> {
                    moduleFields.removeIf(value -> Strings.CS.equals(value.getFieldId(), field.getId()));
                    if (StringUtils.isNotBlank(contract.getCustomerMobileSnapshot())) {
                        moduleFields.add(new BaseModuleFieldValue(field.getId(),
                                contract.getCustomerMobileSnapshot()));
                    }
                });
    }

    /**
     * 待审批期间展示待审批版本；首次审批不通过且尚无生效版本时展示最新提交版本。
     * 已有生效版本的修改审批不通过后，待审批版本被清除，列表自然回退到生效投影。
     */
    private void mergeDisplayVersionSnapshots(List<ContractListResponse> list,
                                              Map<String, List<BaseModuleFieldValue>> contractFieldMap) {
        List<String> displayContractIds = list.stream()
                .filter(item -> StringUtils.isNotBlank(item.getPendingVersionId())
                        || StringUtils.isBlank(item.getEffectiveVersionId()))
                .map(ContractListResponse::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(displayContractIds)) {
            return;
        }
        Map<String, List<ContractVersion>> versionMap = contractVersionMapper.selectListByLambda(
                        new LambdaQueryWrapper<ContractVersion>()
                                .in(ContractVersion::getContractId, displayContractIds)).stream()
                .collect(Collectors.groupingBy(ContractVersion::getContractId));
        list.forEach(item -> {
            if (StringUtils.isBlank(item.getPendingVersionId())
                    && StringUtils.isNotBlank(item.getEffectiveVersionId())) {
                return;
            }
            List<ContractVersion> versions = versionMap.getOrDefault(item.getId(), Collections.emptyList());
            ContractVersion version;
            if (StringUtils.isNotBlank(item.getPendingVersionId())) {
                version = versions.stream()
                        .filter(current -> Strings.CS.equals(current.getId(), item.getPendingVersionId()))
                        .findFirst()
                        .orElse(null);
            } else {
                version = versions.stream()
                        .max(Comparator.comparing(ContractVersion::getVersionNo,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(null);
            }
            if (version == null || StringUtils.isBlank(version.getValueSnapshot())) {
                return;
            }
            ContractVersionSnapshot snapshot = JSON.parseObject(
                    version.getValueSnapshot(), ContractVersionSnapshot.class);
            if (snapshot == null) {
                return;
            }
            applySnapshotForDisplay(item, snapshot);
            if (snapshot.getModuleFields() != null) {
                contractFieldMap.put(item.getId(), snapshot.getModuleFields());
            }
        });
    }


    /**
     * 获取表单快照
     *
     * @param id
     * @param orgId
     * @return
     */
    public ModuleFormConfigDTO getFormSnapshot(String id, String orgId) {
        Contract contract = contractMapper.selectByPrimaryKey(id);
        if (contract == null) {
            throw new GenericException(Translator.get("contract.not.exist"));
        }
        ContractVersion version = getApprovalDisplayVersion(contract);
        if (version != null && StringUtils.isNotBlank(version.getFormSnapshot())) {
            return JSON.parseObject(version.getFormSnapshot(), ModuleFormConfigDTO.class);
        }
        return moduleFormCacheService.getBusinessFormConfig(FormKey.CONTRACT.getKey(), orgId);
    }


    public ResourceTabEnableDTO getTabEnableConfig(String userId, String orgId) {
        List<RolePermissionDTO> rolePermissions = permissionCache.getRolePermissions(userId, orgId);
        return PermissionUtils.getTabEnableConfig(userId, PermissionConstants.CONTRACT_READ, rolePermissions);
    }


    /**
     * 更新合同状态
     *
     * @param request
     * @param userId
     */
    public void updateStage(ContractStageRequest request, String userId, String orgId) {
        Contract oldContract = contractMapper.selectByPrimaryKey(request.getId());
        if (oldContract == null) {
            throw new GenericException(Translator.get("contract.not.exist"));
        }
        Map<String, String> oldMap = new HashMap<>();
        oldMap.put("contractStage", Translator.get("contract.stage." + oldContract.getStage().toLowerCase()));
        Contract contract = contractTransactionService.changeStage(request, userId, orgId);

        LogDTO logDTO = new LogDTO(orgId, request.getId(), userId, LogType.UPDATE, LogModule.CONTRACT_INDEX, contract.getName());
        Map<String, String> newMap = new HashMap<>();
        newMap.put("contractStage", Translator.get("contract.stage." + request.getStage().toLowerCase()));
        logDTO.setOriginalValue(oldMap);
        logDTO.setModifiedValue(newMap);
        logService.add(logDTO);

        if (Strings.CI.equals(request.getStage(), ContractStage.VOID.name())
                || Strings.CI.equals(request.getStage(), ContractStage.COMPLETED_PERFORMANCE.name())) {
            String event = Strings.CI.equals(request.getStage(), ContractStage.VOID.name()) ?
                    NotificationConstants.Event.CONTRACT_VOID : NotificationConstants.Event.CONTRACT_ARCHIVED;
            sendNotice(contract, userId, orgId, event, contract.getCustomerNameSnapshot());
        }

    }

    /**
     * 发送通知
     *
     * @param contract 合同实体
     * @param userId   用户ID
     * @param orgId    组织ID
     * @param event    事件类型
     */
    private void sendNotice(Contract contract, String userId, String orgId, String event, String customerName) {
        //查询通知配置的接收范围
        List<String> receiveUserIds = new ArrayList<>();
        List<MessageTaskConfig> messageTaskConfigList = messageTaskConfigMapper.selectListByLambda(new LambdaQueryWrapper<MessageTaskConfig>()
                .eq(MessageTaskConfig::getOrganizationId, orgId)
                .eq(MessageTaskConfig::getTaskType, NotificationConstants.Module.CONTRACT)
                .eq(MessageTaskConfig::getEvent, event));
        if (CollectionUtils.isNotEmpty(messageTaskConfigList)) {
            MessageTaskConfig messageTaskConfig = messageTaskConfigList.getFirst();
            MessageTaskConfigDTO messageTaskConfigDTO = JSON.parseObject(messageTaskConfig.getValue(), MessageTaskConfigDTO.class);
            receiveUserIds = commonNoticeSendService.getNoticeReceiveUserIds(messageTaskConfigDTO, contract.getCreateUser(), contract.getOwner(), orgId);
        } else {
            //默认通知创建人
            receiveUserIds.add(contract.getOwner());
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("customerName", customerName);
        paramMap.put("name", contract.getName());
        commonNoticeSendService.sendNotice(NotificationConstants.Module.CONTRACT, event,
                paramMap, userId, orgId, receiveUserIds, true);
    }

    private void updateStatusSnapshot(String id, String stage, String approvalStatus) {
        LambdaQueryWrapper<ContractSnapshot> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(ContractSnapshot::getContractId, id);
        List<ContractSnapshot> contractSnapshots = snapshotBaseMapper.selectListByLambda(delWrapper);
        ContractSnapshot first = contractSnapshots.getFirst();
        if (first != null) {
            ContractGetResponse response = JSON.parseObject(first.getContractValue(), ContractGetResponse.class);
            if (StringUtils.isNotBlank(stage)) {
                response.setStage(stage);
            }
            if (StringUtils.isNotBlank(approvalStatus)) {
                response.setApprovalStatus(approvalStatus);
            }
            first.setContractValue(JSON.toJSONString(response));
            snapshotBaseMapper.update(first);
        }
    }

    public CustomerContractStatisticResponse calculateContractStatisticByCustomerId(String customerId, String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        return extContractMapper.calculateContractStatisticByCustomerId(customerId, userId, orgId, deptDataPermission);
    }


    /**
     * 审核通过/不通过
     *
     * @param request
     * @param userId
     */
    public void approvalContract(ContractApprovalRequest request, String userId, String orgId) {
        Contract contract = contractMapper.selectByPrimaryKey(request.getId());
        if (contract == null) {
            throw new GenericException(Translator.get("contract.not.exist"));
        }
        checkApprovalScope(contract, userId, orgId);
        String state = contract.getApprovalStatus();
        Contract result = contractTransactionService.approve(request, userId, orgId);

        // 添加日志上下文
        LogDTO logDTO = getApprovalLogDTO(orgId, request.getId(), userId, result.getName(), state, request.getApprovalStatus());
        logService.add(logDTO);
    }

    public String revoke(String id, String userId, String orgId) {
        // 新合同版本流程明确不支持撤销审批，旧实现保留在版本历史中，不再改变主表或待审批版本。
        throw new GenericException("新合同流程不支持撤销审批");
    }

    private void checkApprovalConfig(String orgId) {
        if (!dictService.isDictConfigEnable(DictModule.CONTRACT_APPROVAL.name(), orgId)) {
            // 未开启审批
            throw new GenericException(CommonResultCode.APPROVAL_NOT_ENABLED_ERROR);
        }
    }


    /**
     * 批量审核
     *
     * @param request
     * @param userId
     * @param orgId
     */
    public BatchAffectSkipResponse batchApprovalContract(ContractApprovalBatchRequest request, String userId, String orgId) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        for (String id : request.getIds()) {
            Contract contract = contractMapper.selectByPrimaryKey(id);
            if (contract == null || !Strings.CS.equals(contract.getApprovalStatus(), ContractApprovalStatus.APPROVING.name())) {
                skip++;
                continue;
            }
            try {
                checkApprovalScope(contract, userId, orgId);
                ContractApprovalRequest singleRequest = new ContractApprovalRequest();
                singleRequest.setId(id);
                singleRequest.setApprovalStatus(request.getApprovalStatus());
                Contract result = contractTransactionService.approve(singleRequest, userId, orgId);
                logService.add(getApprovalLogDTO(orgId, id, userId, result.getName(),
                        ContractApprovalStatus.APPROVING.name(), request.getApprovalStatus()));
                success++;
            } catch (Exception e) {
                fail++;
            }
        }
        return BatchAffectSkipResponse.builder().success(success).fail(fail).skip(skip).build();
    }

    private LogDTO getApprovalLogDTO(String orgId, String id, String userId, String response, String state, String newState) {
        LogDTO logDTO = new LogDTO(orgId, id, userId, LogType.APPROVAL, LogModule.CONTRACT_INDEX, response);
        Map<String, String> oldMap = new HashMap<>();
        oldMap.put("approvalStatus", Translator.get("contract.approval_status." + state.toLowerCase()));
        logDTO.setOriginalValue(oldMap);
        Map<String, String> newMap = new HashMap<>();
        newMap.put("approvalStatus", Translator.get("contract.approval_status." + newState.toLowerCase()));
        logDTO.setModifiedValue(newMap);
        return logDTO;
    }

    private void checkApprovalScope(Contract contract, String userId, String orgId) {
        if (Strings.CS.equals(userId, InternalUser.ADMIN.getValue())) {
            return;
        }
        ContractVersion version = StringUtils.isBlank(contract.getPendingVersionId())
                ? null : contractVersionMapper.selectByPrimaryKey(contract.getPendingVersionId());
        if (version == null) {
            throw new GenericException("合同不存在待审批版本");
        }
        ContractVersionSnapshot snapshot = JSON.parseObject(version.getValueSnapshot(), ContractVersionSnapshot.class);
        DeptDataPermissionDTO permission = dataScopeService.getDeptDataPermission(userId, orgId,
                PermissionConstants.CONTRACT_APPROVAL);
        if (Boolean.TRUE.equals(permission.getAll())) {
            return;
        }
        if (Boolean.TRUE.equals(permission.getSelf()) && snapshot != null
                && Strings.CS.equals(snapshot.getSignerId(), userId)) {
            return;
        }
        if (permission.getDeptIds().contains(version.getApprovalDeptId())) {
            return;
        }
        throw new GenericException("无权审批该签约人部门的合同");
    }

    private ContractVersion getApprovalDisplayVersion(Contract contract) {
        if (StringUtils.isNotBlank(contract.getPendingVersionId())) {
            ContractVersion pending = contractVersionMapper.selectByPrimaryKey(contract.getPendingVersionId());
            if (pending != null) {
                return pending;
            }
        }
        if (StringUtils.isNotBlank(contract.getEffectiveVersionId())) {
            ContractVersion effective = contractVersionMapper.selectByPrimaryKey(contract.getEffectiveVersionId());
            if (effective != null) {
                return effective;
            }
        }
        List<ContractVersion> versions = getVersionHistory(contract.getId());
        return CollectionUtils.isEmpty(versions) ? null : versions.getFirst();
    }

    private ContractVersion getPendingDisplayVersion(Contract contract) {
        if (StringUtils.isNotBlank(contract.getPendingVersionId())) {
            return contractVersionMapper.selectByPrimaryKey(contract.getPendingVersionId());
        }
        if (StringUtils.isNotBlank(contract.getEffectiveVersionId())) {
            return null;
        }
        List<ContractVersion> versions = getVersionHistory(contract.getId());
        return CollectionUtils.isEmpty(versions) ? null : versions.getFirst();
    }

    private List<ContractVersion> getVersionHistory(String contractId) {
        List<ContractVersion> versions = contractVersionMapper.selectListByLambda(
                new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getContractId, contractId));
        versions.sort(Comparator.comparing(ContractVersion::getVersionNo,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return versions;
    }

    private Map<String, String> getVersionUserNameMap(List<ContractVersion> versions) {
        Set<String> userIds = new HashSet<>();
        for (ContractVersion version : versions) {
            if (StringUtils.isNotBlank(version.getSubmitUser())) {
                userIds.add(version.getSubmitUser());
            }
            if (StringUtils.isNotBlank(version.getApprovalUser())) {
                userIds.add(version.getApprovalUser());
            }
        }
        return baseService.getUserNameMap(userIds);
    }

    private List<Map<String, Object>> getContractProductMaps(String contractId) {
        List<ContractProduct> products = contractProductMapper.selectListByLambda(
                new LambdaQueryWrapper<ContractProduct>().eq(ContractProduct::getContractId, contractId));
        products.sort(Comparator.comparing(ContractProduct::getSortNo));
        return toMapList(products);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        return (List<Map<String, Object>>) (List<?>) JSON.parseArray(JSON.toJSONString(value), Map.class);
    }

    private void applySnapshotForDisplay(Contract contract, ContractVersionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        contract.setName(snapshot.getName());
        contract.setNumber(snapshot.getNumber());
        contract.setCustomerId(snapshot.getCustomerId());
        contract.setCustomerNameSnapshot(snapshot.getCustomerNameSnapshot());
        contract.setCustomerMobileSnapshot(snapshot.getCustomerMobileSnapshot());
        contract.setCustomerSourceSnapshot(snapshot.getCustomerSourceSnapshot());
        contract.setOwner(snapshot.getOwner());
        contract.setOwnerNameSnapshot(snapshot.getOwnerNameSnapshot());
        contract.setSignerId(snapshot.getSignerId());
        contract.setSignerNameSnapshot(snapshot.getSignerNameSnapshot());
        contract.setSignerDeptIdSnapshot(snapshot.getSignerDeptIdSnapshot());
        contract.setSignerDeptNameSnapshot(snapshot.getSignerDeptNameSnapshot());
        contract.setAmount(snapshot.getAmount());
        contract.setExpectedRepaymentAmount(snapshot.getExpectedRepaymentAmount());
        contract.setStartTime(snapshot.getStartTime());
        contract.setEndTime(snapshot.getEndTime());
    }

    public String getContractName(String id) {
        Contract contract = contractMapper.selectByPrimaryKey(id);
        return Optional.ofNullable(contract).map(Contract::getName).orElse(null);
    }

    public Contract selectByPrimaryKey(String id) {
        return contractMapper.selectByPrimaryKey(id);
    }

    /**
     * 通过名称获取合同集合
     *
     * @param names 名称集合
     * @return 合同集合
     */
    public List<Contract> getContractListByNames(List<String> names) {
        LambdaQueryWrapper<Contract> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(Contract::getName, names);
        return contractMapper.selectListByLambda(lambdaQueryWrapper);
    }

    /**
     * 设置默认的数据源搜索条件
     *
     * @return 搜索条件
     */
    public List<FilterCondition> getDefaultSourceFilters(String sourceFormKey) {
        // 只展示状态为通过且非作废/归档阶段的合同
        List<FilterCondition> conditions = new ArrayList<>();

        if (dictService.isDictConfigEnable(DictModule.CONTRACT_APPROVAL.name(), OrganizationContext.getOrganizationId())) {
            FilterCondition statusCondition = new FilterCondition();
            statusCondition.setMultipleValue(false);
            statusCondition.setName("approvalStatus");
            statusCondition.setOperator(FilterCondition.CombineConditionOperator.IN.name());
            statusCondition.setValue(List.of(ContractApprovalStatus.APPROVED.name()));
            conditions.add(statusCondition);
        }

        FilterCondition stageCondition = new FilterCondition();
        stageCondition.setMultipleValue(false);
        stageCondition.setName("stage");
        stageCondition.setOperator(FilterCondition.CombineConditionOperator.IN.name());
        if (Strings.CS.equals(sourceFormKey, FormKey.CONTRACT_PAYMENT_RECORD.getKey())) {
            stageCondition.setValue(List.of(ContractStage.SIGNED.name(), ContractStage.IN_PROGRESS.name()));
        } else {
            stageCondition.setValue(List.of(ContractStage.PENDING_SIGNING.name(), ContractStage.SIGNED.name(),
                    ContractStage.IN_PROGRESS.name(), ContractStage.COMPLETED_PERFORMANCE.name(), ContractStage.CHANGE.name()));
        }
        conditions.add(stageCondition);

        return conditions;
    }

    /**
     * 计算合同已回款金额
     *
     * @param contractId 合同ID
     * @return 已回款金额
     */
    private BigDecimal sumContractRecordAmount(String contractId) {
        LambdaQueryWrapper<ContractPaymentRecord> paymentRecordWrapper = new LambdaQueryWrapper<>();
        paymentRecordWrapper.eq(ContractPaymentRecord::getContractId, contractId)
                .eq(ContractPaymentRecord::getApprovalStatus, ContractApprovalStatus.APPROVED.name());
        List<ContractPaymentRecord> contractPaymentRecords = contractPaymentRecordMapper.selectListByLambda(paymentRecordWrapper);
        if (CollectionUtils.isNotEmpty(contractPaymentRecords)) {
            return contractPaymentRecords.stream()
                    .map(ContractPaymentRecord::getRecordAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 校验合同是否存在关联数据
     *
     * @param contractId 合同ID
     */
    private void checkContractRelated(String contractId) {
        LambdaQueryWrapper<ContractPaymentRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(ContractPaymentRecord::getContractId, contractId);
        List<ContractPaymentRecord> contractPaymentRecords = contractPaymentRecordMapper.selectListByLambda(recordWrapper);
        if (CollectionUtils.isNotEmpty(contractPaymentRecords)) {
            throw new GenericException(Translator.get("contract.has.payment.record"));
        }
        if (extContractInvoiceMapper.hasContractInvoice(contractId)) {
            throw new GenericException(Translator.get("contract.has.invoice.cannot.delete"));
        }
    }


    /**
     * 统计
     *
     * @param request
     * @param userId
     * @param orgId
     * @param deptDataPermission
     * @return
     */
    public ContractStatisticResponse searchStatistic(BaseCondition request, String userId, String orgId, DeptDataPermissionDTO deptDataPermission) {
        ContractStatisticResponse response = extContractMapper.searchStatistic(request, orgId, userId, deptDataPermission);
        return Optional.ofNullable(response).orElse(new ContractStatisticResponse());
    }
}
