package cn.cordys.crm.contract.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.UserDeptDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.contract.constants.ContractApprovalStatus;
import cn.cordys.crm.contract.constants.ContractPaymentRecordApprovalStatus;
import cn.cordys.crm.contract.constants.ContractStage;
import cn.cordys.crm.contract.constants.ContractVersionSubmitType;
import cn.cordys.crm.contract.domain.*;
import cn.cordys.crm.contract.dto.ContractVersionSnapshot;
import cn.cordys.crm.contract.dto.request.ContractAddRequest;
import cn.cordys.crm.contract.dto.request.ContractApprovalRequest;
import cn.cordys.crm.contract.dto.request.ContractProductRequest;
import cn.cordys.crm.contract.dto.request.ContractStageRequest;
import cn.cordys.crm.contract.dto.request.ContractUpdateRequest;
import cn.cordys.crm.contract.mapper.ExtContractMapper;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerStageConfig;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.follow.constants.FollowUpPlanStatusType;
import cn.cordys.crm.follow.domain.FollowUpPlan;
import cn.cordys.crm.report.customerconversion.service.CustomerConversionEventService;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 合同单资源短事务。
 * 批量入口必须逐合同调用本服务，禁止在外层开启事务。
 */
@Service
public class ContractTransactionService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999999.99");
    private static final String CUSTOMER_SIGN_STAGE_ID = "stage_sign";
    private static final String CUSTOMER_INVALID_STAGE_ID = "stage_fail";

    @Resource
    private BaseMapper<Contract> contractMapper;
    @Resource
    private BaseMapper<ContractVersion> contractVersionMapper;
    @Resource
    private BaseMapper<ContractProduct> contractProductMapper;
    @Resource
    private BaseMapper<ContractSnapshot> contractSnapshotMapper;
    @Resource
    private BaseMapper<ContractPaymentRecord> paymentRecordMapper;
    @Resource
    private BaseMapper<ContractPaymentRecordVersion> paymentRecordVersionMapper;
    @Resource
    private BaseMapper<ContractPaymentRecordProduct> paymentRecordProductMapper;
    @Resource
    private ExtContractMapper extContractMapper;
    @Resource
    private ExtCustomerMapper extCustomerMapper;
    @Resource
    private BaseMapper<CustomerStageConfig> customerStageMapper;
    @Resource
    private BaseMapper<FollowUpPlan> followUpPlanMapper;
    @Resource
    private BaseService baseService;
    @Resource
    private ContractFieldService contractFieldService;
    @Resource
    private ContractPaymentRecordFieldService paymentRecordFieldService;
    @Resource
    private CustomerConversionEventService customerConversionEventService;
    @Resource
    private LogService logService;
    @Resource
    private ContractDataPermissionService contractDataPermissionService;
    @Resource
    private ModuleFormService moduleFormService;

    @Transactional(rollbackFor = Exception.class)
    public Contract add(ContractAddRequest request, ModuleFormConfigDTO formConfig, String contractNumber,
                        String operatorId, String orgId) {
        Customer customer = extCustomerMapper.selectForContractCreate(request.getCustomerId(), orgId);
        checkCustomerCanCreateContract(customer, operatorId, orgId);
        checkDateRange(request.getStartTime(), request.getEndTime());
        checkContractUnique(request.getName(), contractNumber, orgId, null);

        List<ContractProductRequest> products = normalizeProducts(request.getProducts());
        UserDeptDTO signerDept = requireSignerDept(request.getSignerId(), orgId);
        long now = System.currentTimeMillis();

        Contract contract = new Contract();
        contract.setId(IDGenerator.nextStr());
        contract.setName(request.getName());
        contract.setNumber(contractNumber);
        contract.setCustomerId(customer.getId());
        contract.setCustomerNameSnapshot(customer.getName());
        contract.setCustomerMobileSnapshot(customer.getMobile());
        contract.setCustomerSourceSnapshot(customer.getCreateSource());
        contract.setOwner(customer.getOwner());
        contract.setOwnerNameSnapshot(getUserName(customer.getOwner()));
        contract.setSignerId(request.getSignerId());
        contract.setSignerNameSnapshot(getUserName(request.getSignerId()));
        contract.setSignerDeptIdSnapshot(signerDept.getDeptId());
        contract.setSignerDeptNameSnapshot(signerDept.getDeptName());
        contract.setAmount(sumLoanAmount(products));
        contract.setExpectedRepaymentAmount(sumExpectedRepaymentAmount(products));
        contract.setApprovalStatus(ContractApprovalStatus.APPROVING.name());
        contract.setStage(ContractStage.PENDING_SIGNING.name());
        contract.setStartTime(request.getStartTime());
        contract.setEndTime(request.getEndTime());
        contract.setLockVersion(0);
        contract.setOrganizationId(orgId);
        fillCreateAudit(contract, operatorId, now);

        ContractVersion version = buildVersion(contract, request.getModuleFields(), products, formConfig,
                ContractVersionSubmitType.CREATE.name(), null, 1, operatorId, now);
        contract.setPendingVersionId(version.getId());

        contractMapper.insert(contract);
        contractVersionMapper.insert(version);
        return contract;
    }

    @Transactional(rollbackFor = Exception.class)
    public Contract update(ContractUpdateRequest request, ModuleFormConfigDTO formConfig, String userId, String orgId) {
        Contract contract = requireLockedContract(request.getId(), orgId);
        contractDataPermissionService.checkDataPermission(userId, orgId, contract, PermissionConstants.CONTRACT_UPDATE);
        if (!Objects.equals(contract.getLockVersion(), request.getLockVersion())) {
            throw new GenericException("合同已被其他用户修改，请刷新后重试");
        }
        checkContractEditable(contract);
        if (StringUtils.isNotBlank(contract.getPendingVersionId())) {
            throw new GenericException("合同存在待审批版本，不能重复提交");
        }
        checkImmutableFields(request, contract);
        restoreCustomerMobileSnapshotField(request.getModuleFields(), contract, orgId);
        checkDateRange(request.getStartTime(), request.getEndTime());
        List<ContractProductRequest> products = normalizeProducts(request.getProducts());
        UserDeptDTO signerDept = requireSignerDept(request.getSignerId(), orgId);
        if (!Strings.CS.equals(contract.getSignerId(), request.getSignerId()) && hasAnyPaymentRecord(contract.getId())) {
            throw new GenericException("合同存在回款记录，不能修改签约人");
        }

        long now = System.currentTimeMillis();
        Contract proposed = copyContractForVersion(contract);
        proposed.setSignerId(request.getSignerId());
        proposed.setSignerNameSnapshot(getUserName(request.getSignerId()));
        proposed.setSignerDeptIdSnapshot(signerDept.getDeptId());
        proposed.setSignerDeptNameSnapshot(signerDept.getDeptName());
        proposed.setAmount(sumLoanAmount(products));
        proposed.setExpectedRepaymentAmount(sumExpectedRepaymentAmount(products));
        proposed.setStartTime(request.getStartTime());
        proposed.setEndTime(request.getEndTime());

        int versionNo = nextVersionNo(contract.getId());
        ContractVersion version = buildVersion(proposed, request.getModuleFields(), products, formConfig,
                ContractVersionSubmitType.UPDATE.name(), contract.getEffectiveVersionId(), versionNo, userId, now);
        version.setChangeSnapshot(buildChangeSnapshot(contract.getEffectiveVersionId(), version.getValueSnapshot()));
        contractVersionMapper.insert(version);

        if (StringUtils.isBlank(contract.getEffectiveVersionId())) {
            applyCoreProjection(contract, proposed);
        }
        contract.setPendingVersionId(version.getId());
        contract.setApprovalStatus(ContractApprovalStatus.APPROVING.name());
        contract.setLockVersion(contract.getLockVersion() + 1);
        fillUpdateAudit(contract, userId, now);
        contractMapper.update(contract);
        return contract;
    }

    @Transactional(rollbackFor = Exception.class)
    public Contract approve(ContractApprovalRequest request, String userId, String orgId) {
        Contract contract = requireLockedContract(request.getId(), orgId);
        if (StringUtils.isBlank(contract.getPendingVersionId())) {
            throw new GenericException("合同不存在待审批版本");
        }
        ContractVersion version = contractVersionMapper.selectByPrimaryKey(contract.getPendingVersionId());
        if (version == null || !Strings.CS.equals(version.getApprovalStatus(), ContractApprovalStatus.APPROVING.name())) {
            throw new GenericException("合同版本已审批，请勿重复操作");
        }
        if (!Strings.CS.equalsAny(request.getApprovalStatus(), ContractApprovalStatus.APPROVED.name(),
                ContractApprovalStatus.UNAPPROVED.name())) {
            throw new GenericException("审批状态不合法");
        }

        long now = System.currentTimeMillis();
        version.setApprovalStatus(request.getApprovalStatus());
        version.setApprovalUser(userId);
        version.setApprovalTime(now);
        version.setApprovalOpinion(request.getOpinion());
        fillUpdateAudit(version, userId, now);
        contractVersionMapper.update(version);

        if (Strings.CS.equals(request.getApprovalStatus(), ContractApprovalStatus.APPROVED.name())) {
            ContractVersionSnapshot snapshot = parseVersionSnapshot(version);
            boolean firstApproval = StringUtils.isBlank(contract.getEffectiveVersionId());
            projectApprovedVersion(contract, version, snapshot, userId, now);
            contract.setEffectiveVersionId(version.getId());
            contract.setApprovalStatus(ContractApprovalStatus.APPROVED.name());
            if (firstApproval) {
                contract.setStage(ContractStage.SIGNED.name());
            }
        } else {
            contract.setApprovalStatus(StringUtils.isBlank(contract.getEffectiveVersionId())
                    ? ContractApprovalStatus.UNAPPROVED.name()
                    : ContractApprovalStatus.APPROVED.name());
        }
        contract.setPendingVersionId(null);
        contract.setLockVersion(contract.getLockVersion() + 1);
        fillUpdateAudit(contract, userId, now);
        contractMapper.updateById(contract);
        extContractMapper.clearPendingVersionId(contract.getId());
        if (Strings.CS.equals(request.getApprovalStatus(), ContractApprovalStatus.APPROVED.name())) {
            customerConversionEventService.recordContractSigned(contract, userId);
            syncPaymentProductSigner(contract);
        }
        return contract;
    }

    /**
     * 合同生效后同步回款产品上的签约人快照（合同成交分析报表实时口径，避免 join）。
     */
    private void syncPaymentProductSigner(Contract contract) {
        List<ContractPaymentRecordProduct> products = paymentRecordProductMapper.selectListByLambda(
                new LambdaQueryWrapper<ContractPaymentRecordProduct>()
                        .eq(ContractPaymentRecordProduct::getContractId, contract.getId()));
        if (CollectionUtils.isEmpty(products)) {
            return;
        }
        for (ContractPaymentRecordProduct product : products) {
            product.setSignerId(contract.getSignerId());
            product.setSignerNameSnapshot(contract.getSignerNameSnapshot());
            product.setSignerDeptIdSnapshot(contract.getSignerDeptIdSnapshot());
            product.setSignerDeptNameSnapshot(contract.getSignerDeptNameSnapshot());
            product.setCustomerSourceSnapshot(contract.getCustomerSourceSnapshot());
            paymentRecordProductMapper.update(product);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Contract changeStage(ContractStageRequest request, String userId, String orgId) {
        Contract contract = requireLockedContract(request.getId(), orgId);
        contractDataPermissionService.checkDataPermission(userId, orgId, contract, PermissionConstants.CONTRACT_STAGE);
        if (StringUtils.isBlank(contract.getEffectiveVersionId())) {
            throw new GenericException("合同尚未生效，不能变更阶段");
        }
        if (isTerminal(contract.getStage())) {
            throw new GenericException("履行完毕和作废为不可逆终态");
        }
        if (!Strings.CS.equalsAny(request.getStage(), ContractStage.COMPLETED_PERFORMANCE.name(),
                ContractStage.VOID.name())) {
            throw new GenericException("合同只能手动变更为履行完毕或作废");
        }
        contract.setStage(request.getStage());
        contract.setVoidReason(Strings.CS.equals(request.getStage(), ContractStage.VOID.name())
                ? request.getVoidReason() : null);
        contract.setLockVersion(contract.getLockVersion() + 1);
        fillUpdateAudit(contract, userId, System.currentTimeMillis());
        contractMapper.update(contract);
        return contract;
    }

    @Transactional(rollbackFor = Exception.class)
    public Contract delete(String id, String userId, String orgId) {
        return deleteResources(id, userId, orgId, null, false, true);
    }

    /**
     * 客户 CASCADE 删除时的单合同短事务，同时记录合同及其回款删除日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public Contract deleteForCustomerCascade(String id, String userId, String orgId, String reason) {
        return deleteResources(id, userId, orgId, reason, true, false);
    }

    private Contract deleteResources(String id, String userId, String orgId, String reason, boolean writeCascadeLogs,
                                     boolean checkContractPermission) {
        Contract contract = requireLockedContract(id, orgId);
        if (checkContractPermission) {
            contractDataPermissionService.checkDataPermission(userId, orgId, contract, PermissionConstants.CONTRACT_DELETE);
        }
        List<ContractPaymentRecord> records = paymentRecordMapper.selectListByLambda(
                new LambdaQueryWrapper<ContractPaymentRecord>().eq(ContractPaymentRecord::getContractId, id));
        List<String> paymentRecordIds = records.stream().map(ContractPaymentRecord::getId).toList();
        if (CollectionUtils.isNotEmpty(paymentRecordIds)) {
            paymentRecordFieldService.deleteByResourceIds(paymentRecordIds);
            paymentRecordProductMapper.deleteByLambda(new LambdaQueryWrapper<ContractPaymentRecordProduct>()
                    .in(ContractPaymentRecordProduct::getPaymentRecordId, paymentRecordIds));
            paymentRecordVersionMapper.deleteByLambda(new LambdaQueryWrapper<ContractPaymentRecordVersion>()
                    .in(ContractPaymentRecordVersion::getPaymentRecordId, paymentRecordIds));
            paymentRecordMapper.deleteByLambda(new LambdaQueryWrapper<ContractPaymentRecord>()
                    .in(ContractPaymentRecord::getId, paymentRecordIds));
        }

        contractFieldService.deleteByResourceId(id);
        contractProductMapper.deleteByLambda(new LambdaQueryWrapper<ContractProduct>().eq(ContractProduct::getContractId, id));
        contractVersionMapper.deleteByLambda(new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getContractId, id));
        contractSnapshotMapper.deleteByLambda(new LambdaQueryWrapper<ContractSnapshot>().eq(ContractSnapshot::getContractId, id));
        contractMapper.deleteByPrimaryKey(id);
        if (writeCascadeLogs) {
            List<LogDTO> logs = new ArrayList<>();
            for (ContractPaymentRecord record : records) {
                LogDTO paymentLog = new LogDTO(orgId, record.getId(), userId, LogType.DELETE,
                        LogModule.CONTRACT_PAYMENT_RECORD, record.getName());
                paymentLog.setDetail(reason);
                logs.add(paymentLog);
            }
            LogDTO contractLog = new LogDTO(orgId, contract.getId(), userId, LogType.DELETE,
                    LogModule.CONTRACT_INDEX, contract.getName());
            contractLog.setDetail(reason);
            logs.add(contractLog);
            logService.batchAddSync(logs);
        }
        return contract;
    }

    private Contract requireLockedContract(String id, String orgId) {
        Contract contract = extContractMapper.selectForUpdate(id, orgId);
        if (contract == null) {
            throw new GenericException("合同不存在");
        }
        return contract;
    }

    private void checkCustomerCanCreateContract(Customer customer, String operatorId, String orgId) {
        if (customer == null) {
            throw new GenericException("客户不存在");
        }
        if (Boolean.TRUE.equals(customer.getInSharedPool())) {
            throw new GenericException("公海客户不能创建合同");
        }
        CustomerStageConfig currentStage = customerStageMapper.selectByPrimaryKey(customer.getStage());
        CustomerStageConfig signStage = customerStageMapper.selectByPrimaryKey(CUSTOMER_SIGN_STAGE_ID);
        if (currentStage == null || signStage == null || !Strings.CS.equals(signStage.getOrganizationId(), orgId)
                || Strings.CS.equals(currentStage.getId(), CUSTOMER_INVALID_STAGE_ID)
                || currentStage.getPos() < signStage.getPos()) {
            throw new GenericException("客户尚未进入可签约阶段");
        }
        if (Strings.CS.equals(customer.getOwner(), operatorId)) {
            return;
        }
        Set<String> signStageIds = Set.of(signStage.getId());
        List<FollowUpPlan> plans = followUpPlanMapper.selectListByLambda(new LambdaQueryWrapper<FollowUpPlan>()
                .eq(FollowUpPlan::getCustomerId, customer.getId())
                .eq(FollowUpPlan::getProcessor, operatorId));
        boolean plannedProcessor = plans.stream().anyMatch(plan ->
                signStageIds.contains(plan.getNextStage())
                        && !Strings.CS.equalsAny(plan.getStatus(), FollowUpPlanStatusType.COMPLETED.name(),
                        FollowUpPlanStatusType.CANCELLED.name()));
        if (!plannedProcessor) {
            throw new GenericException("仅客户负责人或签约阶段预计处理人可创建合同");
        }
    }

    private void checkContractEditable(Contract contract) {
        if (isTerminal(contract.getStage())) {
            throw new GenericException("履行完毕和作废合同不允许编辑");
        }
    }

    private boolean isTerminal(String stage) {
        return Strings.CS.equalsAny(stage, ContractStage.COMPLETED_PERFORMANCE.name(), ContractStage.VOID.name());
    }

    private void checkImmutableFields(ContractUpdateRequest request, Contract contract) {
        if (!Strings.CS.equals(request.getName(), contract.getName())
                || !Strings.CS.equals(request.getCustomerId(), contract.getCustomerId())
                || (StringUtils.isNotBlank(request.getOwner()) && !Strings.CS.equals(request.getOwner(), contract.getOwner()))
                || (StringUtils.isNotBlank(request.getNumber()) && !Strings.CS.equals(request.getNumber(), contract.getNumber()))) {
            throw new GenericException("合同名称、编号、客户和负责人创建后不允许修改");
        }
    }

    /**
     * 详情展示时手机号可能已按全局配置脱敏，更新版本前必须恢复为合同保存的原始快照，
     * 避免将脱敏文本写入版本数据，同时阻止前端绕过基础字段保护修改客户手机号。
     */
    private void restoreCustomerMobileSnapshotField(List<BaseModuleFieldValue> moduleFields,
                                                    Contract contract, String orgId) {
        if (CollectionUtils.isEmpty(moduleFields)) {
            return;
        }
        moduleFormService.getFlattenFormFields(FormKey.CONTRACT.getKey(), orgId).stream()
                .filter(field -> StringUtils.isNotBlank(field.getResourceFieldId()))
                .filter(field -> Strings.CS.equals(field.getInternalKey(),
                        BusinessModuleField.CUSTOMER_MOBILE.getKey()))
                .findFirst()
                .ifPresent(field -> {
                    moduleFields.removeIf(value -> Strings.CS.equals(value.getFieldId(), field.getId()));
                    if (StringUtils.isNotBlank(contract.getCustomerMobileSnapshot())) {
                        moduleFields.add(new BaseModuleFieldValue(field.getId(), contract.getCustomerMobileSnapshot()));
                    }
                });
    }

    private void checkDateRange(Long startTime, Long endTime) {
        if (startTime == null || endTime == null || endTime < startTime) {
            throw new GenericException("合同结束时间不能早于开始时间");
        }
    }

    private void checkContractUnique(String name, String number, String orgId, String excludeId) {
        LambdaQueryWrapper<Contract> nameQuery = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrganizationId, orgId)
                .eq(Contract::getName, name);
        if (StringUtils.isNotBlank(excludeId)) {
            nameQuery.nq(Contract::getId, excludeId);
        }
        if (CollectionUtils.isNotEmpty(contractMapper.selectListByLambda(nameQuery))) {
            throw new GenericException("合同名称已存在");
        }

        if (StringUtils.isBlank(number)) {
            return;
        }
        LambdaQueryWrapper<Contract> numberQuery = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getOrganizationId, orgId)
                .eq(Contract::getNumber, number);
        if (StringUtils.isNotBlank(excludeId)) {
            numberQuery.nq(Contract::getId, excludeId);
        }
        if (CollectionUtils.isNotEmpty(contractMapper.selectListByLambda(numberQuery))) {
            throw new GenericException("合同编号已存在");
        }
    }

    private List<ContractProductRequest> normalizeProducts(List<ContractProductRequest> requestProducts) {
        if (CollectionUtils.isEmpty(requestProducts) || requestProducts.size() > 10) {
            throw new GenericException("合同产品必须为 1 至 10 行");
        }
        List<ContractProductRequest> products = new ArrayList<>(requestProducts.size());
        for (ContractProductRequest source : requestProducts) {
            if (source == null || source.getLoanAmount() == null || source.getExpectedRepaymentAmount() == null
                    || source.getLoanAmount().signum() < 0 || source.getExpectedRepaymentAmount().signum() < 0
                    || (source.getPointRate() != null && source.getPointRate().signum() < 0)) {
                throw new GenericException("合同产品金额和点位不能为负数");
            }
            ContractProductRequest product = new ContractProductRequest();
            product.setLoanAmount(source.getLoanAmount().setScale(2, RoundingMode.HALF_UP));
            product.setExpectedRepaymentAmount(source.getExpectedRepaymentAmount().setScale(2, RoundingMode.HALF_UP));
            product.setPointRate(source.getPointRate() == null ? null : source.getPointRate().setScale(2, RoundingMode.HALF_UP));
            products.add(product);
        }
        if (sumLoanAmount(products).compareTo(MAX_AMOUNT) > 0
                || sumExpectedRepaymentAmount(products).compareTo(MAX_AMOUNT) > 0) {
            throw new GenericException("合同金额超出允许范围");
        }
        return products;
    }

    private BigDecimal sumLoanAmount(List<ContractProductRequest> products) {
        return products.stream().map(ContractProductRequest::getLoanAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumExpectedRepaymentAmount(List<ContractProductRequest> products) {
        return products.stream().map(ContractProductRequest::getExpectedRepaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private UserDeptDTO requireSignerDept(String signerId, String orgId) {
        UserDeptDTO signerDept = baseService.getUserDeptMapByUserId(signerId, orgId);
        if (signerDept == null || StringUtils.isBlank(signerDept.getDeptId())) {
            throw new GenericException("签约人不存在或未加入当前组织");
        }
        return signerDept;
    }

    private String getUserName(String userId) {
        return baseService.getUserNameMap(List.of(userId)).get(userId);
    }

    private ContractVersion buildVersion(Contract contract, List<BaseModuleFieldValue> moduleFields,
                                         List<ContractProductRequest> products, ModuleFormConfigDTO formConfig,
                                         String submitType, String baseVersionId, int versionNo,
                                         String userId, long now) {
        ContractVersionSnapshot snapshot = buildSnapshot(contract, moduleFields, products);
        ContractVersion version = new ContractVersion();
        version.setId(IDGenerator.nextStr());
        version.setContractId(contract.getId());
        version.setVersionNo(versionNo);
        version.setSubmitType(submitType);
        version.setApprovalStatus(ContractApprovalStatus.APPROVING.name());
        version.setBaseEffectiveVersionId(baseVersionId);
        version.setValueSnapshot(JSON.toJSONString(snapshot));
        version.setFormSnapshot(JSON.toJSONString(formConfig));
        version.setApprovalDeptId(contract.getSignerDeptIdSnapshot());
        version.setSubmitUser(userId);
        version.setSubmitTime(now);
        version.setOrganizationId(contract.getOrganizationId());
        fillCreateAudit(version, userId, now);
        return version;
    }

    private ContractVersionSnapshot buildSnapshot(Contract contract, List<BaseModuleFieldValue> moduleFields,
                                                  List<ContractProductRequest> products) {
        ContractVersionSnapshot snapshot = new ContractVersionSnapshot();
        snapshot.setName(contract.getName());
        snapshot.setNumber(contract.getNumber());
        snapshot.setCustomerId(contract.getCustomerId());
        snapshot.setCustomerNameSnapshot(contract.getCustomerNameSnapshot());
        snapshot.setCustomerMobileSnapshot(contract.getCustomerMobileSnapshot());
        snapshot.setCustomerSourceSnapshot(contract.getCustomerSourceSnapshot());
        snapshot.setOwner(contract.getOwner());
        snapshot.setOwnerNameSnapshot(contract.getOwnerNameSnapshot());
        snapshot.setSignerId(contract.getSignerId());
        snapshot.setSignerNameSnapshot(contract.getSignerNameSnapshot());
        snapshot.setSignerDeptIdSnapshot(contract.getSignerDeptIdSnapshot());
        snapshot.setSignerDeptNameSnapshot(contract.getSignerDeptNameSnapshot());
        snapshot.setAmount(contract.getAmount());
        snapshot.setExpectedRepaymentAmount(contract.getExpectedRepaymentAmount());
        snapshot.setStartTime(contract.getStartTime());
        snapshot.setEndTime(contract.getEndTime());
        snapshot.setProducts(products);
        snapshot.setModuleFields(moduleFields == null ? new ArrayList<>() : moduleFields);
        return snapshot;
    }

    private Contract copyContractForVersion(Contract source) {
        Contract copy = JSON.parseObject(JSON.toJSONString(source), Contract.class);
        copy.setPendingVersionId(null);
        return copy;
    }

    private int nextVersionNo(String contractId) {
        List<ContractVersion> versions = contractVersionMapper.selectListByLambda(
                new LambdaQueryWrapper<ContractVersion>().eq(ContractVersion::getContractId, contractId));
        return versions.stream().map(ContractVersion::getVersionNo).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private String buildChangeSnapshot(String baseVersionId, String newSnapshot) {
        if (StringUtils.isBlank(baseVersionId)) {
            return null;
        }
        ContractVersion baseVersion = contractVersionMapper.selectByPrimaryKey(baseVersionId);
        if (baseVersion == null) {
            return null;
        }
        Map<String, Object> before = JSON.parseMap(baseVersion.getValueSnapshot());
        Map<String, Object> after = JSON.parseMap(newSnapshot);
        Map<String, Object> changes = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", oldValue);
            change.put("after", newValue);
            changes.put(key, change);
        }
        return JSON.toJSONString(changes);
    }

    private ContractVersionSnapshot parseVersionSnapshot(ContractVersion version) {
        ContractVersionSnapshot snapshot = JSON.parseObject(version.getValueSnapshot(), ContractVersionSnapshot.class);
        if (snapshot == null || snapshot.getSchemaVersion() == null || snapshot.getSchemaVersion() < 2) {
            throw new GenericException("历史合同版本不支持重新投影，请重新编辑后提交");
        }
        return snapshot;
    }

    private void projectApprovedVersion(Contract contract, ContractVersion version, ContractVersionSnapshot snapshot,
                                        String userId, long now) {
        Contract projected = new Contract();
        projected.setName(snapshot.getName());
        projected.setNumber(snapshot.getNumber());
        projected.setCustomerId(snapshot.getCustomerId());
        projected.setCustomerNameSnapshot(snapshot.getCustomerNameSnapshot());
        projected.setCustomerMobileSnapshot(snapshot.getCustomerMobileSnapshot());
        projected.setCustomerSourceSnapshot(snapshot.getCustomerSourceSnapshot());
        projected.setOwner(snapshot.getOwner());
        projected.setOwnerNameSnapshot(snapshot.getOwnerNameSnapshot());
        projected.setSignerId(snapshot.getSignerId());
        projected.setSignerNameSnapshot(snapshot.getSignerNameSnapshot());
        projected.setSignerDeptIdSnapshot(snapshot.getSignerDeptIdSnapshot());
        projected.setSignerDeptNameSnapshot(snapshot.getSignerDeptNameSnapshot());
        projected.setAmount(snapshot.getAmount());
        projected.setExpectedRepaymentAmount(snapshot.getExpectedRepaymentAmount());
        projected.setStartTime(snapshot.getStartTime());
        projected.setEndTime(snapshot.getEndTime());
        applyCoreProjection(contract, projected);

        contractProductMapper.deleteByLambda(new LambdaQueryWrapper<ContractProduct>()
                .eq(ContractProduct::getContractId, contract.getId()));
        List<ContractProduct> products = new ArrayList<>(snapshot.getProducts().size());
        for (int index = 0; index < snapshot.getProducts().size(); index++) {
            ContractProductRequest source = snapshot.getProducts().get(index);
            ContractProduct product = new ContractProduct();
            product.setId(IDGenerator.nextStr());
            product.setContractId(contract.getId());
            product.setSourceVersionId(version.getId());
            product.setSortNo(index + 1);
            product.setLoanAmount(source.getLoanAmount());
            product.setPointRate(source.getPointRate());
            product.setExpectedRepaymentAmount(source.getExpectedRepaymentAmount());
            product.setOrganizationId(contract.getOrganizationId());
            fillCreateAudit(product, userId, now);
            products.add(product);
        }
        contractProductMapper.batchInsert(products);

        contractFieldService.deleteByResourceId(contract.getId());
        contractFieldService.saveModuleField(contract, contract.getOrganizationId(), userId,
                snapshot.getModuleFields(), true);
    }

    private void applyCoreProjection(Contract target, Contract source) {
        target.setName(source.getName());
        target.setNumber(source.getNumber());
        target.setCustomerId(source.getCustomerId());
        target.setCustomerNameSnapshot(source.getCustomerNameSnapshot());
        target.setCustomerMobileSnapshot(source.getCustomerMobileSnapshot());
        target.setCustomerSourceSnapshot(source.getCustomerSourceSnapshot());
        target.setOwner(source.getOwner());
        target.setOwnerNameSnapshot(source.getOwnerNameSnapshot());
        target.setSignerId(source.getSignerId());
        target.setSignerNameSnapshot(source.getSignerNameSnapshot());
        target.setSignerDeptIdSnapshot(source.getSignerDeptIdSnapshot());
        target.setSignerDeptNameSnapshot(source.getSignerDeptNameSnapshot());
        target.setAmount(source.getAmount());
        target.setExpectedRepaymentAmount(source.getExpectedRepaymentAmount());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
    }

    private boolean hasAnyPaymentRecord(String contractId) {
        return CollectionUtils.isNotEmpty(paymentRecordMapper.selectListByLambda(
                new LambdaQueryWrapper<ContractPaymentRecord>().eq(ContractPaymentRecord::getContractId, contractId)));
    }

    private void fillCreateAudit(cn.cordys.common.domain.BaseModel model, String userId, long now) {
        model.setCreateTime(now);
        model.setCreateUser(StringUtils.defaultIfBlank(userId, InternalUser.ADMIN.getValue()));
        model.setUpdateTime(now);
        model.setUpdateUser(StringUtils.defaultIfBlank(userId, InternalUser.ADMIN.getValue()));
    }

    private void fillUpdateAudit(cn.cordys.common.domain.BaseModel model, String userId, long now) {
        model.setUpdateTime(now);
        model.setUpdateUser(userId);
    }
}
