package cn.cordys.crm.contract.service;

import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.contract.constants.ContractPaymentRecordApprovalStatus;
import cn.cordys.crm.contract.constants.ContractStage;
import cn.cordys.crm.contract.constants.ContractVersionSubmitType;
import cn.cordys.crm.contract.domain.*;
import cn.cordys.crm.contract.dto.ContractPaymentRecordVersionSnapshot;
import cn.cordys.crm.contract.dto.request.*;
import cn.cordys.crm.contract.mapper.ExtContractMapper;
import cn.cordys.crm.contract.mapper.ExtContractPaymentRecordMapper;
import cn.cordys.crm.report.customerconversion.service.CustomerConversionEventService;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
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
 * 回款记录单资源短事务。
 */
@Service
public class ContractPaymentRecordTransactionService {

    @Resource
    private BaseMapper<Contract> contractMapper;
    @Resource
    private BaseMapper<ContractPaymentRecord> paymentRecordMapper;
    @Resource
    private BaseMapper<ContractPaymentRecordVersion> paymentVersionMapper;
    @Resource
    private BaseMapper<ContractPaymentRecordProduct> paymentProductMapper;
    @Resource
    private ExtContractMapper extContractMapper;
    @Resource
    private ExtContractPaymentRecordMapper extPaymentRecordMapper;
    @Resource
    private ContractPaymentRecordFieldService paymentFieldService;
    @Resource
    private RevenueFormulaEvaluator formulaEvaluator;
    @Resource
    private CustomerConversionEventService customerConversionEventService;

    @Transactional(rollbackFor = Exception.class)
    public ContractPaymentRecord add(ContractPaymentRecordAddRequest request, ModuleFormConfigDTO formConfig,
                                     String recordNo, String userId, String orgId) {
        Contract contract = requireLockedContract(request.getContractId(), orgId);
        checkCanCreate(contract, userId);
        checkUnique(request.getName(), recordNo, orgId, null);
        List<ContractPaymentRecordProductRequest> products = normalizeProducts(request.getProducts());
        long now = System.currentTimeMillis();

        ContractPaymentRecord record = new ContractPaymentRecord();
        record.setId(IDGenerator.nextStr());
        record.setName(request.getName());
        record.setNo(recordNo);
        record.setOwner(contract.getSignerId());
        record.setContractId(contract.getId());
        record.setPaymentPlanId(null);
        record.setApprovalStatus(ContractPaymentRecordApprovalStatus.APPROVING.name());
        record.setLockVersion(0);
        record.setOrganizationId(orgId);
        applyTotals(record, products);
        fillCreateAudit(record, userId, now);

        ContractPaymentRecordVersion version = buildVersion(record, contract, request.getModuleFields(), products,
                formConfig, ContractVersionSubmitType.CREATE.name(), null, 1, userId, now);
        record.setPendingVersionId(version.getId());
        paymentRecordMapper.insert(record);
        paymentVersionMapper.insert(version);
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractPaymentRecord update(ContractPaymentRecordUpdateRequest request, ModuleFormConfigDTO formConfig,
                                        String userId, String orgId) {
        ContractPaymentRecord reference = paymentRecordMapper.selectByPrimaryKey(request.getId());
        if (reference == null) {
            throw new GenericException("回款记录不存在");
        }
        Contract contract = requireLockedContract(reference.getContractId(), orgId);
        ContractPaymentRecord record = requireLockedRecord(request.getId(), orgId);
        if (!Objects.equals(record.getLockVersion(), request.getLockVersion())) {
            throw new GenericException("回款记录已被其他用户修改，请刷新后重试");
        }
        if (StringUtils.isNotBlank(record.getPendingVersionId())) {
            throw new GenericException("回款记录存在待审批版本，不能重复提交");
        }
        if (!Strings.CS.equals(record.getName(), request.getName())
                || !Strings.CS.equals(record.getContractId(), request.getContractId())) {
            throw new GenericException("回款记录名称、编码和合同创建后不允许修改");
        }
        if (!Strings.CS.equals(record.getContractId(), contract.getId())) {
            throw new GenericException("回款记录所属合同不一致");
        }

        List<ContractPaymentRecordProductRequest> products = normalizeProducts(request.getProducts());
        ContractPaymentRecord proposed = JSON.parseObject(JSON.toJSONString(record), ContractPaymentRecord.class);
        proposed.setOwner(contract.getSignerId());
        applyTotals(proposed, products);
        int versionNo = nextVersionNo(record.getId());
        long now = System.currentTimeMillis();
        ContractPaymentRecordVersion version = buildVersion(proposed, contract, request.getModuleFields(), products,
                formConfig, ContractVersionSubmitType.UPDATE.name(), record.getEffectiveVersionId(),
                versionNo, userId, now);
        version.setChangeSnapshot(buildChangeSnapshot(record.getEffectiveVersionId(), version.getValueSnapshot()));
        paymentVersionMapper.insert(version);

        if (StringUtils.isBlank(record.getEffectiveVersionId())) {
            applyProjection(record, proposed);
        }
        record.setPendingVersionId(version.getId());
        record.setApprovalStatus(ContractPaymentRecordApprovalStatus.APPROVING.name());
        record.setLockVersion(record.getLockVersion() + 1);
        fillUpdateAudit(record, userId, now);
        paymentRecordMapper.update(record);
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractPaymentRecord approve(ContractPaymentRecordApprovalRequest request, String userId, String orgId) {
        ContractPaymentRecord reference = paymentRecordMapper.selectByPrimaryKey(request.getId());
        if (reference == null) {
            throw new GenericException("回款记录不存在");
        }
        Contract contract = requireLockedContract(reference.getContractId(), orgId);
        ContractPaymentRecord record = requireLockedRecord(request.getId(), orgId);
        if (StringUtils.isBlank(record.getPendingVersionId())) {
            throw new GenericException("回款记录不存在待审批版本");
        }
        ContractPaymentRecordVersion version = paymentVersionMapper.selectByPrimaryKey(record.getPendingVersionId());
        if (version == null || !Strings.CS.equals(version.getApprovalStatus(),
                ContractPaymentRecordApprovalStatus.APPROVING.name())) {
            throw new GenericException("回款记录版本已审批，请勿重复操作");
        }
        if (!Strings.CS.equalsAny(request.getApprovalStatus(),
                ContractPaymentRecordApprovalStatus.APPROVED.name(),
                ContractPaymentRecordApprovalStatus.UNAPPROVED.name())) {
            throw new GenericException("审批状态不合法");
        }

        long now = System.currentTimeMillis();
        version.setApprovalStatus(request.getApprovalStatus());
        version.setApprovalUser(userId);
        version.setApprovalTime(now);
        version.setApprovalOpinion(request.getOpinion());
        fillUpdateAudit(version, userId, now);
        paymentVersionMapper.update(version);

        if (Strings.CS.equals(request.getApprovalStatus(), ContractPaymentRecordApprovalStatus.APPROVED.name())) {
            ContractPaymentRecordVersionSnapshot snapshot = parseSnapshot(version);
            projectApprovedVersion(record, version, snapshot, userId, now);
            record.setEffectiveVersionId(version.getId());
            record.setApprovalStatus(ContractPaymentRecordApprovalStatus.APPROVED.name());
            if (!isTerminal(contract.getStage()) && Strings.CS.equals(contract.getStage(), ContractStage.SIGNED.name())) {
                contract.setStage(ContractStage.IN_PROGRESS.name());
                fillUpdateAudit(contract, userId, now);
                contract.setLockVersion(contract.getLockVersion() + 1);
                contractMapper.update(contract);
            }
        } else {
            record.setApprovalStatus(StringUtils.isBlank(record.getEffectiveVersionId())
                    ? ContractPaymentRecordApprovalStatus.UNAPPROVED.name()
                    : ContractPaymentRecordApprovalStatus.APPROVED.name());
        }
        record.setPendingVersionId(null);
        record.setLockVersion(record.getLockVersion() + 1);
        fillUpdateAudit(record, userId, now);
        paymentRecordMapper.updateById(record);
        extPaymentRecordMapper.clearPendingVersionId(record.getId());
        if (Strings.CS.equals(request.getApprovalStatus(),
                ContractPaymentRecordApprovalStatus.APPROVED.name())) {
            customerConversionEventService.recordPaymentApproved(contract, record, userId);
        }
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractPaymentRecord delete(String id, String userId, String orgId) {
        ContractPaymentRecord reference = paymentRecordMapper.selectByPrimaryKey(id);
        if (reference == null) {
            throw new GenericException("回款记录不存在");
        }
        Contract contract = requireLockedContract(reference.getContractId(), orgId);
        ContractPaymentRecord record = requireLockedRecord(id, orgId);

        paymentFieldService.deleteByResourceId(id);
        paymentProductMapper.deleteByLambda(new LambdaQueryWrapper<ContractPaymentRecordProduct>()
                .eq(ContractPaymentRecordProduct::getPaymentRecordId, id));
        paymentVersionMapper.deleteByLambda(new LambdaQueryWrapper<ContractPaymentRecordVersion>()
                .eq(ContractPaymentRecordVersion::getPaymentRecordId, id));
        paymentRecordMapper.deleteByPrimaryKey(id);

        boolean hasEffectiveRecords = paymentRecordMapper.selectListByLambda(
                        new LambdaQueryWrapper<ContractPaymentRecord>()
                                .eq(ContractPaymentRecord::getContractId, contract.getId()))
                .stream().anyMatch(item -> StringUtils.isNotBlank(item.getEffectiveVersionId()));
        if (!hasEffectiveRecords && Strings.CS.equals(contract.getStage(), ContractStage.IN_PROGRESS.name())) {
            contract.setStage(ContractStage.SIGNED.name());
            contract.setLockVersion(contract.getLockVersion() + 1);
            fillUpdateAudit(contract, userId, System.currentTimeMillis());
            contractMapper.update(contract);
        }
        return record;
    }

    private Contract requireLockedContract(String contractId, String orgId) {
        Contract contract = extContractMapper.selectForUpdate(contractId, orgId);
        if (contract == null) {
            throw new GenericException("合同不存在");
        }
        return contract;
    }

    private ContractPaymentRecord requireLockedRecord(String id, String orgId) {
        ContractPaymentRecord record = extPaymentRecordMapper.selectForUpdate(id, orgId);
        if (record == null) {
            throw new GenericException("回款记录不存在");
        }
        return record;
    }

    private void checkCanCreate(Contract contract, String userId) {
        if (!Strings.CS.equals(contract.getSignerId(), userId)) {
            throw new GenericException("仅合同当前生效签约人可创建回款记录");
        }
        if (StringUtils.isBlank(contract.getEffectiveVersionId())) {
            throw new GenericException("合同尚未生效，不能创建回款记录");
        }
        if (StringUtils.isNotBlank(contract.getPendingVersionId())) {
            throw new GenericException("合同存在待审批修改，不能创建回款记录");
        }
        if (!Strings.CS.equalsAny(contract.getStage(), ContractStage.SIGNED.name(), ContractStage.IN_PROGRESS.name())) {
            throw new GenericException("只有已签署或履行中的合同可以创建回款记录");
        }
    }

    private boolean isTerminal(String stage) {
        return Strings.CS.equalsAny(stage, ContractStage.COMPLETED_PERFORMANCE.name(), ContractStage.VOID.name());
    }

    private List<ContractPaymentRecordProductRequest> normalizeProducts(
            List<ContractPaymentRecordProductRequest> requestProducts) {
        if (CollectionUtils.isEmpty(requestProducts) || requestProducts.size() > 10) {
            throw new GenericException("回款产品必须为 1 至 10 行");
        }
        List<ContractPaymentRecordProductRequest> products = new ArrayList<>(requestProducts.size());
        for (ContractPaymentRecordProductRequest source : requestProducts) {
            if (source == null || source.getLoanTime() == null || source.getRepaymentTime() == null
                    || hasNullAmount(source) || hasNegativeAmount(source)) {
                throw new GenericException("回款产品日期和金额必须填写，金额不能为负数");
            }
            ContractPaymentRecordProductRequest product = JSON.parseObject(
                    JSON.toJSONString(source), ContractPaymentRecordProductRequest.class);
            product.setLoanAmount(scale(source.getLoanAmount()));
            product.setRepaymentAmount(scale(source.getRepaymentAmount()));
            product.setCostAmount(scale(source.getCostAmount()));
            product.setMiscFeeAmount(scale(source.getMiscFeeAmount()));
            product.setCommissionAmount(scale(source.getCommissionAmount()));
            Map<String, BigDecimal> variables = new HashMap<>();
            variables.put("放款金额", product.getLoanAmount());
            variables.put("回款金额", product.getRepaymentAmount());
            variables.put("成本金额", product.getCostAmount());
            variables.put("杂费金额", product.getMiscFeeAmount());
            variables.put("返佣金额", product.getCommissionAmount());
            RevenueFormulaEvaluator.EvaluationResult result = formulaEvaluator.evaluate(
                    source.getRevenueFormula(), variables);
            product.setRevenueFormulaNormalized(result.normalizedFormula());
            product.setRevenueAmount(result.value());
            products.add(product);
        }
        return products;
    }

    private boolean hasNullAmount(ContractPaymentRecordProductRequest product) {
        return product.getLoanAmount() == null || product.getRepaymentAmount() == null
                || product.getCostAmount() == null || product.getMiscFeeAmount() == null
                || product.getCommissionAmount() == null;
    }

    private boolean hasNegativeAmount(ContractPaymentRecordProductRequest product) {
        return product.getLoanAmount().signum() < 0 || product.getRepaymentAmount().signum() < 0
                || product.getCostAmount().signum() < 0 || product.getMiscFeeAmount().signum() < 0
                || product.getCommissionAmount().signum() < 0;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void applyTotals(ContractPaymentRecord record, List<ContractPaymentRecordProductRequest> products) {
        record.setTotalLoanAmount(sum(products, ContractPaymentRecordProductRequest::getLoanAmount));
        record.setRecordAmount(sum(products, ContractPaymentRecordProductRequest::getRepaymentAmount));
        record.setTotalCostAmount(sum(products, ContractPaymentRecordProductRequest::getCostAmount));
        record.setTotalMiscFeeAmount(sum(products, ContractPaymentRecordProductRequest::getMiscFeeAmount));
        record.setTotalCommissionAmount(sum(products, ContractPaymentRecordProductRequest::getCommissionAmount));
        record.setTotalRevenueAmount(sum(products, ContractPaymentRecordProductRequest::getRevenueAmount));
        record.setFirstLoanTime(products.stream().map(ContractPaymentRecordProductRequest::getLoanTime)
                .min(Long::compareTo).orElse(null));
        record.setLastLoanTime(products.stream().map(ContractPaymentRecordProductRequest::getLoanTime)
                .max(Long::compareTo).orElse(null));
        record.setFirstRepaymentTime(products.stream().map(ContractPaymentRecordProductRequest::getRepaymentTime)
                .min(Long::compareTo).orElse(null));
        record.setLastRepaymentTime(products.stream().map(ContractPaymentRecordProductRequest::getRepaymentTime)
                .max(Long::compareTo).orElse(null));
        record.setRecordEndTime(record.getFirstRepaymentTime());
    }

    private BigDecimal sum(List<ContractPaymentRecordProductRequest> products,
                           java.util.function.Function<ContractPaymentRecordProductRequest, BigDecimal> getter) {
        return products.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void checkUnique(String name, String no, String orgId, String excludeId) {
        LambdaQueryWrapper<ContractPaymentRecord> nameQuery = new LambdaQueryWrapper<ContractPaymentRecord>()
                .eq(ContractPaymentRecord::getOrganizationId, orgId)
                .eq(ContractPaymentRecord::getName, name);
        if (StringUtils.isNotBlank(excludeId)) {
            nameQuery.nq(ContractPaymentRecord::getId, excludeId);
        }
        if (CollectionUtils.isNotEmpty(paymentRecordMapper.selectListByLambda(nameQuery))) {
            throw new GenericException("回款记录名称已存在");
        }

        if (StringUtils.isBlank(no)) {
            return;
        }
        LambdaQueryWrapper<ContractPaymentRecord> noQuery = new LambdaQueryWrapper<ContractPaymentRecord>()
                .eq(ContractPaymentRecord::getOrganizationId, orgId)
                .eq(ContractPaymentRecord::getNo, no);
        if (StringUtils.isNotBlank(excludeId)) {
            noQuery.nq(ContractPaymentRecord::getId, excludeId);
        }
        if (CollectionUtils.isNotEmpty(paymentRecordMapper.selectListByLambda(noQuery))) {
            throw new GenericException("回款记录编码已存在");
        }
    }

    private ContractPaymentRecordVersion buildVersion(ContractPaymentRecord record, Contract contract,
                                                       List<BaseModuleFieldValue> moduleFields,
                                                       List<ContractPaymentRecordProductRequest> products,
                                                       ModuleFormConfigDTO formConfig, String submitType,
                                                       String baseEffectiveVersionId, int versionNo,
                                                       String userId, long now) {
        ContractPaymentRecordVersion version = new ContractPaymentRecordVersion();
        version.setId(IDGenerator.nextStr());
        version.setPaymentRecordId(record.getId());
        version.setContractId(contract.getId());
        version.setVersionNo(versionNo);
        version.setSubmitType(submitType);
        version.setApprovalStatus(ContractPaymentRecordApprovalStatus.APPROVING.name());
        version.setBaseEffectiveVersionId(baseEffectiveVersionId);
        version.setValueSnapshot(JSON.toJSONString(buildSnapshot(record, moduleFields, products)));
        version.setFormSnapshot(JSON.toJSONString(formConfig));
        version.setSignerIdSnapshot(contract.getSignerId());
        version.setSignerNameSnapshot(contract.getSignerNameSnapshot());
        version.setApprovalDeptId(contract.getSignerDeptIdSnapshot());
        version.setSubmitUser(userId);
        version.setSubmitTime(now);
        version.setOrganizationId(record.getOrganizationId());
        fillCreateAudit(version, userId, now);
        return version;
    }

    private ContractPaymentRecordVersionSnapshot buildSnapshot(ContractPaymentRecord record,
                                                                List<BaseModuleFieldValue> moduleFields,
                                                                List<ContractPaymentRecordProductRequest> products) {
        ContractPaymentRecordVersionSnapshot snapshot = new ContractPaymentRecordVersionSnapshot();
        snapshot.setName(record.getName());
        snapshot.setNo(record.getNo());
        snapshot.setContractId(record.getContractId());
        snapshot.setOwner(record.getOwner());
        snapshot.setRecordAmount(record.getRecordAmount());
        snapshot.setTotalLoanAmount(record.getTotalLoanAmount());
        snapshot.setTotalCostAmount(record.getTotalCostAmount());
        snapshot.setTotalMiscFeeAmount(record.getTotalMiscFeeAmount());
        snapshot.setTotalCommissionAmount(record.getTotalCommissionAmount());
        snapshot.setTotalRevenueAmount(record.getTotalRevenueAmount());
        snapshot.setFirstLoanTime(record.getFirstLoanTime());
        snapshot.setLastLoanTime(record.getLastLoanTime());
        snapshot.setFirstRepaymentTime(record.getFirstRepaymentTime());
        snapshot.setLastRepaymentTime(record.getLastRepaymentTime());
        snapshot.setProducts(products);
        snapshot.setModuleFields(moduleFields == null ? new ArrayList<>() : moduleFields);
        return snapshot;
    }

    private int nextVersionNo(String recordId) {
        return paymentVersionMapper.selectListByLambda(new LambdaQueryWrapper<ContractPaymentRecordVersion>()
                        .eq(ContractPaymentRecordVersion::getPaymentRecordId, recordId))
                .stream().map(ContractPaymentRecordVersion::getVersionNo).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private String buildChangeSnapshot(String baseVersionId, String newSnapshot) {
        if (StringUtils.isBlank(baseVersionId)) {
            return null;
        }
        ContractPaymentRecordVersion baseVersion = paymentVersionMapper.selectByPrimaryKey(baseVersionId);
        if (baseVersion == null) {
            return null;
        }
        Map<String, Object> before = JSON.parseMap(baseVersion.getValueSnapshot());
        Map<String, Object> after = JSON.parseMap(newSnapshot);
        Map<String, Object> changes = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            if (Objects.equals(before.get(key), after.get(key))) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", before.get(key));
            change.put("after", after.get(key));
            changes.put(key, change);
        }
        return JSON.toJSONString(changes);
    }

    private ContractPaymentRecordVersionSnapshot parseSnapshot(ContractPaymentRecordVersion version) {
        ContractPaymentRecordVersionSnapshot snapshot = JSON.parseObject(
                version.getValueSnapshot(), ContractPaymentRecordVersionSnapshot.class);
        if (snapshot == null || snapshot.getSchemaVersion() == null || snapshot.getSchemaVersion() < 2) {
            throw new GenericException("历史回款版本不支持重新投影，请重新编辑后提交");
        }
        return snapshot;
    }

    private void projectApprovedVersion(ContractPaymentRecord record, ContractPaymentRecordVersion version,
                                        ContractPaymentRecordVersionSnapshot snapshot, String userId, long now) {
        ContractPaymentRecord proposed = new ContractPaymentRecord();
        proposed.setName(snapshot.getName());
        proposed.setNo(snapshot.getNo());
        proposed.setContractId(snapshot.getContractId());
        proposed.setOwner(snapshot.getOwner());
        proposed.setRecordAmount(snapshot.getRecordAmount());
        proposed.setTotalLoanAmount(snapshot.getTotalLoanAmount());
        proposed.setTotalCostAmount(snapshot.getTotalCostAmount());
        proposed.setTotalMiscFeeAmount(snapshot.getTotalMiscFeeAmount());
        proposed.setTotalCommissionAmount(snapshot.getTotalCommissionAmount());
        proposed.setTotalRevenueAmount(snapshot.getTotalRevenueAmount());
        proposed.setFirstLoanTime(snapshot.getFirstLoanTime());
        proposed.setLastLoanTime(snapshot.getLastLoanTime());
        proposed.setFirstRepaymentTime(snapshot.getFirstRepaymentTime());
        proposed.setLastRepaymentTime(snapshot.getLastRepaymentTime());
        proposed.setRecordEndTime(snapshot.getFirstRepaymentTime());
        applyProjection(record, proposed);

        paymentProductMapper.deleteByLambda(new LambdaQueryWrapper<ContractPaymentRecordProduct>()
                .eq(ContractPaymentRecordProduct::getPaymentRecordId, record.getId()));
        Contract contract = contractMapper.selectByPrimaryKey(record.getContractId());
        List<ContractPaymentRecordProduct> products = new ArrayList<>(snapshot.getProducts().size());
        for (int index = 0; index < snapshot.getProducts().size(); index++) {
            ContractPaymentRecordProductRequest source = snapshot.getProducts().get(index);
            ContractPaymentRecordProduct product = new ContractPaymentRecordProduct();
            product.setId(IDGenerator.nextStr());
            product.setPaymentRecordId(record.getId());
            product.setContractId(record.getContractId());
            if (contract != null) {
                product.setSignerId(contract.getSignerId());
                product.setSignerNameSnapshot(contract.getSignerNameSnapshot());
                product.setSignerDeptIdSnapshot(contract.getSignerDeptIdSnapshot());
                product.setSignerDeptNameSnapshot(contract.getSignerDeptNameSnapshot());
                product.setCustomerSourceSnapshot(contract.getCustomerSourceSnapshot());
            }
            product.setSourceVersionId(version.getId());
            product.setSortNo(index + 1);
            product.setLoanTime(source.getLoanTime());
            product.setLoanAmount(source.getLoanAmount());
            product.setRepaymentTime(source.getRepaymentTime());
            product.setRepaymentAmount(source.getRepaymentAmount());
            product.setCostAmount(source.getCostAmount());
            product.setMiscFeeAmount(source.getMiscFeeAmount());
            product.setCommissionAmount(source.getCommissionAmount());
            product.setRevenueFormula(source.getRevenueFormula());
            product.setRevenueFormulaNormalized(source.getRevenueFormulaNormalized());
            product.setRevenueAmount(source.getRevenueAmount());
            product.setOrganizationId(record.getOrganizationId());
            fillCreateAudit(product, userId, now);
            products.add(product);
        }
        paymentProductMapper.batchInsert(products);

        paymentFieldService.deleteByResourceId(record.getId());
        paymentFieldService.saveModuleField(record, record.getOrganizationId(), userId,
                snapshot.getModuleFields(), true);
    }

    private void applyProjection(ContractPaymentRecord target, ContractPaymentRecord source) {
        target.setName(source.getName());
        target.setNo(source.getNo());
        target.setContractId(source.getContractId());
        target.setOwner(source.getOwner());
        target.setRecordAmount(source.getRecordAmount());
        target.setTotalLoanAmount(source.getTotalLoanAmount());
        target.setTotalCostAmount(source.getTotalCostAmount());
        target.setTotalMiscFeeAmount(source.getTotalMiscFeeAmount());
        target.setTotalCommissionAmount(source.getTotalCommissionAmount());
        target.setTotalRevenueAmount(source.getTotalRevenueAmount());
        target.setRecordEndTime(source.getRecordEndTime());
        target.setFirstLoanTime(source.getFirstLoanTime());
        target.setLastLoanTime(source.getLastLoanTime());
        target.setFirstRepaymentTime(source.getFirstRepaymentTime());
        target.setLastRepaymentTime(source.getLastRepaymentTime());
    }

    private void fillCreateAudit(BaseModel model, String userId, long now) {
        model.setCreateTime(now);
        model.setCreateUser(StringUtils.defaultIfBlank(userId, InternalUser.ADMIN.getValue()));
        model.setUpdateTime(now);
        model.setUpdateUser(StringUtils.defaultIfBlank(userId, InternalUser.ADMIN.getValue()));
    }

    private void fillUpdateAudit(BaseModel model, String userId, long now) {
        model.setUpdateTime(now);
        model.setUpdateUser(userId);
    }
}
