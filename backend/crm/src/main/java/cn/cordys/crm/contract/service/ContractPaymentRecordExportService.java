package cn.cordys.crm.contract.service;

import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.ExportDTO;
import cn.cordys.common.dto.ExportFieldParam;
import cn.cordys.common.dto.ExportHeadDTO;
import cn.cordys.common.service.BaseExportService;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.TimeUtils;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.contract.domain.ContractPaymentRecordProduct;
import cn.cordys.crm.contract.domain.ContractPaymentRecordVersion;
import cn.cordys.crm.contract.dto.ContractPaymentRecordVersionSnapshot;
import cn.cordys.crm.contract.dto.request.ContractPaymentRecordPageRequest;
import cn.cordys.crm.contract.dto.response.ContractPaymentRecordResponse;
import cn.cordys.crm.contract.mapper.ExtContractPaymentRecordMapper;
import cn.cordys.crm.system.excel.domain.MergeResult;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.field.base.OptionProp;
import cn.cordys.crm.system.service.ModuleFieldExtService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.registry.ExportThreadRegistry;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author song-cc-rock
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class ContractPaymentRecordExportService extends BaseExportService {

	@Resource
	private ModuleFieldExtService moduleFieldExtService;
	@Resource
	private ContractPaymentRecordService contractPaymentRecordService;
	@Resource
	private ExtContractPaymentRecordMapper extContractPaymentRecordMapper;
	@Resource
	private BaseMapper<ContractPaymentRecordProduct> paymentRecordProductMapper;
	@Resource
	private BaseMapper<ContractPaymentRecordVersion> paymentRecordVersionMapper;

	@Override
	protected MergeResult getExportMergeData(String taskId, ExportDTO exportDTO) throws InterruptedException {
		List<ContractPaymentRecordResponse> list;
		if (CollectionUtils.isNotEmpty(exportDTO.getSelectIds())) {
			list = extContractPaymentRecordMapper.getListByIds(exportDTO.getSelectIds(), exportDTO.getUserId(),
					exportDTO.getOrgId(), exportDTO.getDeptDataPermission());
		} else {
			ContractPaymentRecordPageRequest request = (ContractPaymentRecordPageRequest) exportDTO.getPageRequest();
			PageHelper.startPage(request.getCurrent(), request.getPageSize());
			list = extContractPaymentRecordMapper.list(request, exportDTO.getUserId(), exportDTO.getOrgId(),
					exportDTO.getDeptDataPermission());
		}
		if (CollectionUtils.isEmpty(list)) {
			return MergeResult.builder().dataList(new ArrayList<>()).mergeRegions(new ArrayList<>()).build();
		}

		List<ContractPaymentRecordResponse> dataList = contractPaymentRecordService.buildListExtra(list, exportDTO.getOrgId());
		Map<String, List<Object>> productMap = getDisplayProductMap(dataList);
		List<List<Object>> rows = new ArrayList<>();
		List<int[]> mergeRegions = new ArrayList<>();
		int offset = 0;
		for (ContractPaymentRecordResponse response : dataList) {
			if (ExportThreadRegistry.isInterrupted(taskId)) {
				throw new InterruptedException("线程已被中断，主动退出");
			}
			List<List<Object>> recordRows = buildMergeData(response,
					productMap.getOrDefault(response.getId(), Collections.emptyList()), exportDTO);
			if (recordRows.size() > 1) {
				mergeRegions.add(new int[]{offset, offset + recordRows.size() - 1});
			}
			offset += recordRows.size();
			rows.addAll(recordRows);
		}
		return MergeResult.builder().dataList(rows).mergeRegions(mergeRegions).build();
	}

	private List<List<Object>> buildMergeData(ContractPaymentRecordResponse data, List<?> products,
											 ExportDTO exportDTO) {
		ExportFieldParam exportFieldParam = exportDTO.getExportFieldParam();
		List<BaseModuleFieldValue> moduleFields = ContractExportProductHelper.appendProducts(
				data.getModuleFields(), exportFieldParam, products);
		return buildDataWithSub(moduleFields, exportFieldParam, exportDTO.getExportMetas(), getSystemFieldMap(data));
	}

	private Map<String, List<Object>> getDisplayProductMap(List<ContractPaymentRecordResponse> list) {
		List<String> recordIds = list.stream().map(ContractPaymentRecordResponse::getId)
				.filter(StringUtils::isNotBlank).toList();
		List<ContractPaymentRecordProduct> products = paymentRecordProductMapper.selectListByLambda(
				new LambdaQueryWrapper<ContractPaymentRecordProduct>()
						.in(ContractPaymentRecordProduct::getPaymentRecordId, recordIds));
		products.sort(Comparator.comparing(ContractPaymentRecordProduct::getPaymentRecordId)
				.thenComparing(ContractPaymentRecordProduct::getSortNo));
		Map<String, List<Object>> result = products.stream().collect(Collectors.groupingBy(
				ContractPaymentRecordProduct::getPaymentRecordId, LinkedHashMap::new,
				Collectors.mapping(product -> (Object) product, Collectors.toList())));

		List<String> versionRecordIds = list.stream()
				.filter(item -> StringUtils.isNotBlank(item.getPendingVersionId())
						|| StringUtils.isBlank(item.getEffectiveVersionId()))
				.map(ContractPaymentRecordResponse::getId).filter(StringUtils::isNotBlank).distinct().toList();
		if (CollectionUtils.isEmpty(versionRecordIds)) {
			return result;
		}
		Map<String, List<ContractPaymentRecordVersion>> versionMap = paymentRecordVersionMapper.selectListByLambda(
				new LambdaQueryWrapper<ContractPaymentRecordVersion>()
						.in(ContractPaymentRecordVersion::getPaymentRecordId, versionRecordIds)).stream()
				.collect(Collectors.groupingBy(ContractPaymentRecordVersion::getPaymentRecordId));
		for (ContractPaymentRecordResponse item : list) {
			ContractPaymentRecordVersion version = getDisplayVersion(item,
					versionMap.getOrDefault(item.getId(), Collections.emptyList()));
			if (version == null || StringUtils.isBlank(version.getValueSnapshot())) {
				continue;
			}
			ContractPaymentRecordVersionSnapshot snapshot = JSON.parseObject(
					version.getValueSnapshot(), ContractPaymentRecordVersionSnapshot.class);
			if (snapshot != null && snapshot.getProducts() != null) {
				result.put(item.getId(), new ArrayList<>(snapshot.getProducts()));
			}
		}
		return result;
	}

	private ContractPaymentRecordVersion getDisplayVersion(ContractPaymentRecordResponse item,
													List<ContractPaymentRecordVersion> versions) {
		if (StringUtils.isNotBlank(item.getPendingVersionId())) {
			return versions.stream()
					.filter(version -> Strings.CS.equals(version.getId(), item.getPendingVersionId()))
					.findFirst().orElse(null);
		}
		if (StringUtils.isNotBlank(item.getEffectiveVersionId())) {
			return null;
		}
		return versions.stream().max(Comparator.comparing(ContractPaymentRecordVersion::getVersionNo,
				Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
	}

	@Override
	public List<List<Object>> getExportData(String taskId, ExportDTO exportDTO) throws InterruptedException {
		// 分页查询数据
		ContractPaymentRecordPageRequest request = (ContractPaymentRecordPageRequest) exportDTO.getPageRequest();
		PageHelper.startPage(request.getCurrent(), request.getPageSize());
		List<ContractPaymentRecordResponse> list = extContractPaymentRecordMapper.list(request, exportDTO.getUserId(), exportDTO.getOrgId(), exportDTO.getDeptDataPermission());
		return expandList(list, taskId, exportDTO);
	}

	/**
	 * 获取系统字段的值
	 * @param data 详情
	 * @return 字段值映射
	 */
	public LinkedHashMap<String, Object> getSystemFieldMap(ContractPaymentRecordResponse data) {
		LinkedHashMap<String, Object> systemFieldMap = new LinkedHashMap<>();
		systemFieldMap.put("name", data.getName());
		systemFieldMap.put("no", data.getNo());
		systemFieldMap.put("contractId", data.getContractName());
		systemFieldMap.put("paymentPlanId", data.getPaymentPlanName());
		systemFieldMap.put("owner", data.getOwnerName());
		systemFieldMap.put("departmentId", data.getDepartmentName());
			systemFieldMap.put("recordAmount", data.getRecordAmount());
			systemFieldMap.put("totalLoanAmount", data.getTotalLoanAmount());
			systemFieldMap.put("totalCostAmount", data.getTotalCostAmount());
			systemFieldMap.put("totalMiscFeeAmount", data.getTotalMiscFeeAmount());
			systemFieldMap.put("totalCommissionAmount", data.getTotalCommissionAmount());
			systemFieldMap.put("totalRevenueAmount", data.getTotalRevenueAmount());
			systemFieldMap.put("recordEndTime", getInternalDateStr(data.getRecordEndTime(), FormKey.CONTRACT_PAYMENT_RECORD.getKey(),
					data.getOrganizationId(), BusinessModuleField.CONTRACT_PAYMENT_RECORD_END_TIME.getKey()));
			if (StringUtils.isNotBlank(data.getApprovalStatus())) {
				systemFieldMap.put("approvalStatus", Translator.get(
						"contract.approval_status." + data.getApprovalStatus().toLowerCase(), Locale.SIMPLIFIED_CHINESE));
			}

		systemFieldMap.put("createUser", data.getCreateUserName());
		systemFieldMap.put("createTime", TimeUtils.getDateTimeStr(data.getCreateTime()));
		systemFieldMap.put("updateUser", data.getUpdateUserName());
		systemFieldMap.put("updateTime", TimeUtils.getDateTimeStr(data.getUpdateTime()));
		return systemFieldMap;
	}

	/**
	 * 获取勾选的回款记录导出数据
	 * @return 导出数据列表
	 */
	@Override
	public List<List<Object>> getSelectExportData(List<String> ids, String taskId, ExportDTO exportDTO) throws InterruptedException {
		List<ContractPaymentRecordResponse> allList = extContractPaymentRecordMapper.getListByIds(ids, exportDTO.getUserId(), exportDTO.getOrgId(), exportDTO.getDeptDataPermission());
		return expandList(allList, taskId, exportDTO);
	}

	/**
	 * 展开列表数据
	 * @param list 列表数据
	 * @param taskId 任务ID
	 * @param exportDTO 导出参数
	 * @return 导出数据
	 * @throws InterruptedException 异常信息
	 */
	private List<List<Object>> expandList(List<ContractPaymentRecordResponse> list, String taskId, ExportDTO exportDTO) throws InterruptedException {
		String orgId = exportDTO.getOrgId();
		List<ContractPaymentRecordResponse> dataList = contractPaymentRecordService.buildListExtra(list, orgId);
		Map<String, BaseField> fieldConfigMap = getFieldConfigMap(FormKey.CONTRACT_PAYMENT_RECORD.getKey(), orgId);
		//构建导出数据
		List<List<Object>> data = new ArrayList<>();
		for (ContractPaymentRecordResponse response : dataList) {
			if (ExportThreadRegistry.isInterrupted(taskId)) {
				throw new InterruptedException("线程已被中断，主动退出");
			}
			List<Object> value = buildData(exportDTO.getHeadList(), response, fieldConfigMap);
			data.add(value);
		}
		return data;
	}

	/**
	 * 构建导出数据
	 * @param headList 表头集合
	 * @param data 数据详情
	 * @param fieldConfigMap 字段配置
	 * @return 导出数据
	 */
	private List<Object> buildData(List<ExportHeadDTO> headList, ContractPaymentRecordResponse data, Map<String, BaseField> fieldConfigMap) {
		LinkedHashMap<String, Object> systemFieldMap = getSystemFieldMap(data);
		Map<String, Object> moduleFieldMap = getFieldIdValueMap(data.getModuleFields());
		return transModuleFieldValue(headList, systemFieldMap, moduleFieldMap, new ArrayList<>(), fieldConfigMap);
	}

	/**
	 * 处理内部选项值
	 * @param value 选项值
	 * @param formKey 表单Key
	 * @param orgId 组织ID
	 * @param internalKey 字段内部Key
	 * @return 名称
	 */
	private String processInternalOptions(String value, String formKey, String orgId, String internalKey) {
		List<OptionProp> options = moduleFieldExtService.getFieldOptions(formKey, orgId, internalKey);
		for (OptionProp option : options) {
			if (Strings.CS.equals(option.getValue(), value)) {
				return option.getLabel();
			}
		}
		return value;
	}

	/**
	 * 获取内部日期字符串
	 * @param timestamp 毫秒
	 * @param formKey 表单Key
	 * @param orgId 组织ID
	 * @param internalKey 内部Key
	 * @return 日期字符串
	 */
	private String getInternalDateStr(Long timestamp, String formKey, String orgId, String internalKey) {
		String dateType = moduleFieldExtService.getDateFieldType(formKey, orgId, internalKey);
		return switch (dateType) {
			case "date" -> TimeUtils.getDateStr(timestamp);
			case "month" -> TimeUtils.getMonthStr(timestamp);
			default -> TimeUtils.getDateTimeStr(timestamp);
		};
	}
}
