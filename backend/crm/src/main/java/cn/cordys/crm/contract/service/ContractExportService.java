package cn.cordys.crm.contract.service;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.ExportDTO;
import cn.cordys.common.dto.ExportFieldParam;
import cn.cordys.common.dto.FieldExportMeta;
import cn.cordys.common.service.BaseExportService;
import cn.cordys.common.util.AsyncUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.TimeUtils;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.contract.domain.ContractProduct;
import cn.cordys.crm.contract.domain.ContractVersion;
import cn.cordys.crm.contract.dto.ContractVersionSnapshot;
import cn.cordys.crm.contract.dto.request.ContractPageRequest;
import cn.cordys.crm.contract.dto.response.ContractListResponse;
import cn.cordys.crm.contract.mapper.ExtContractMapper;
import cn.cordys.crm.system.excel.domain.MergeResult;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.registry.ExportThreadRegistry;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class ContractExportService extends BaseExportService {

    @Resource
    private ContractService contractService;
    @Resource
    private ExtContractMapper extContractMapper;
    @Resource
    private ModuleFormService moduleFormService;
	@Resource
	private BaseMapper<ContractProduct> contractProductMapper;
	@Resource
	private BaseMapper<ContractVersion> contractVersionMapper;

    @Resource(name = "parallelTaskExecutor")
    private Executor executor;

    @Override
    protected MergeResult getExportMergeData(String taskId, ExportDTO exportParam) {
        var exportList = collectExportList(exportParam);
        if (CollectionUtils.isEmpty(exportList)) {
            return MergeResult.builder().dataList(new ArrayList<>()).mergeRegions(new ArrayList<>()).build();
        }
        var dataList = contractService.buildList(exportList, exportParam.getOrgId());
        moduleFormService.getBaseModuleFieldValues(dataList, ContractListResponse::getModuleFields);
        var exportFieldParam = exportParam.getExportFieldParam();
		Map<String, List<Object>> productMap = getDisplayProductMap(dataList);
        return parallelBuildMergeResult(taskId, exportParam, dataList, exportFieldParam, productMap);
    }

    private List<ContractListResponse> collectExportList(ExportDTO exportParam) {
        var orgId = exportParam.getOrgId();
        var userId = exportParam.getUserId();
        var deptDataPermission = exportParam.getDeptDataPermission();
        if (CollectionUtils.isNotEmpty(exportParam.getSelectIds())) {
            return extContractMapper.getListByIds(exportParam.getSelectIds(), userId, orgId, deptDataPermission);
        }
        var request = (ContractPageRequest) exportParam.getPageRequest();
		PageHelper.startPage(request.getCurrent(), request.getPageSize());
        return extContractMapper.list(request, orgId, userId, deptDataPermission, false);
    }

	/**
	 * 并行构建导出数据及合并区域
	 * @param taskId 导出任务ID
	 * @param exportParam 导出参数
	 * @param dataList 数据列表
	 * @param exportFieldParam 导出字段参数
	 * @return 合并结果
	 */
	private MergeResult parallelBuildMergeResult(String taskId, ExportDTO exportParam, List<ContractListResponse> dataList,
											 ExportFieldParam exportFieldParam, Map<String, List<Object>> productMap) {

		int size = dataList.size();
		List<List<Object>> mergeRowData = new ArrayList<>(size);
		List<int[]> mergeRegions = new ArrayList<>();

		// 任务列表 - 每个任务处理一行数据，构建该行的导出数据 Pair<位置索引, 行数据>
		List<Future<Pair<Integer, List<List<Object>>>>> futures = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			final int idx = i;
			ContractListResponse detail = dataList.get(i);
			futures.add(
                    AsyncUtils.supplyAsync(() -> {
                        if (ExportThreadRegistry.isInterrupted(taskId)) {
                            throw new RuntimeException("导出中断", new InterruptedException("导出中断"));
                        }
	                        List<List<Object>> buildData = buildData(detail, exportFieldParam,
											productMap.getOrDefault(detail.getId(), Collections.emptyList()), exportParam.getExportMetas());
                        return Pair.of(idx, buildData);
                    }, executor));
		}

		// 收集结果 (阻塞)
		List<Pair<Integer, List<List<Object>>>> results = new ArrayList<>(size);
		for (Future<Pair<Integer, List<List<Object>>>> f : futures) {
			try {
				Pair<Integer, List<List<Object>>> pairData = f.get();
				results.add(pairData);
			} catch (Exception e) {
				log.error("Parse row data error: {}", e.getMessage());
			}
		}

		// 按原始索引排序 (很重要, 否则合并区域错乱)
		results.sort(Comparator.comparingInt(Pair::getLeft));

		// 构建合并区域
		int offset = 0;
		for (Pair<Integer, List<List<Object>>> r : results) {
			List<List<Object>> buildData = r.getRight();
			if (buildData.size() > 1) {
				mergeRegions.add(new int[]{offset, offset + buildData.size() - 1});
			}
			offset += buildData.size();
			mergeRowData.addAll(buildData);
		}

		// 返回合并的结构
		return MergeResult.builder().mergeRegions(mergeRegions).dataList(mergeRowData).build();
	}

    private List<List<Object>> buildData(ContractListResponse detail, ExportFieldParam exportFieldParam,
										 List<?> products, List<FieldExportMeta> exportMetas) {
		List<BaseModuleFieldValue> moduleFields = ContractExportProductHelper.appendProducts(
				detail.getModuleFields(), exportFieldParam, products);
		return buildDataWithSub(moduleFields, exportFieldParam, exportMetas, getSystemFieldMap(detail));
    }

	private Map<String, List<Object>> getDisplayProductMap(List<ContractListResponse> list) {
		List<String> contractIds = list.stream().map(ContractListResponse::getId)
				.filter(StringUtils::isNotBlank).toList();
		List<ContractProduct> products = contractProductMapper.selectListByLambda(
				new LambdaQueryWrapper<ContractProduct>().in(ContractProduct::getContractId, contractIds));
		products.sort(Comparator.comparing(ContractProduct::getContractId)
				.thenComparing(ContractProduct::getSortNo));
		Map<String, List<Object>> result = products.stream().collect(Collectors.groupingBy(
				ContractProduct::getContractId, LinkedHashMap::new,
				Collectors.mapping(product -> (Object) product, Collectors.toList())));

		List<String> versionContractIds = list.stream()
				.filter(item -> StringUtils.isNotBlank(item.getPendingVersionId())
						|| StringUtils.isBlank(item.getEffectiveVersionId()))
				.map(ContractListResponse::getId).filter(StringUtils::isNotBlank).distinct().toList();
		if (CollectionUtils.isEmpty(versionContractIds)) {
			return result;
		}
		Map<String, List<ContractVersion>> versionMap = contractVersionMapper.selectListByLambda(
				new LambdaQueryWrapper<ContractVersion>().in(ContractVersion::getContractId, versionContractIds)).stream()
				.collect(Collectors.groupingBy(ContractVersion::getContractId));
		for (ContractListResponse item : list) {
			ContractVersion version = getDisplayVersion(item,
					versionMap.getOrDefault(item.getId(), Collections.emptyList()));
			if (version == null || StringUtils.isBlank(version.getValueSnapshot())) {
				continue;
			}
			ContractVersionSnapshot snapshot = JSON.parseObject(version.getValueSnapshot(), ContractVersionSnapshot.class);
			if (snapshot != null && snapshot.getProducts() != null) {
				result.put(item.getId(), new ArrayList<>(snapshot.getProducts()));
			}
		}
		return result;
	}

	private ContractVersion getDisplayVersion(ContractListResponse item, List<ContractVersion> versions) {
		if (StringUtils.isNotBlank(item.getPendingVersionId())) {
			return versions.stream()
					.filter(version -> Strings.CS.equals(version.getId(), item.getPendingVersionId()))
					.findFirst().orElse(null);
		}
		if (StringUtils.isNotBlank(item.getEffectiveVersionId())) {
			return null;
		}
		return versions.stream().max(Comparator.comparing(ContractVersion::getVersionNo,
				Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
	}

    public LinkedHashMap<String, Object> getSystemFieldMap(ContractListResponse data) {
        LinkedHashMap<String, Object> systemFieldMap = new LinkedHashMap<>();
        systemFieldMap.put("name", data.getName());
        systemFieldMap.put("owner", data.getOwnerName());
        systemFieldMap.put("departmentId", data.getDepartmentName());
        systemFieldMap.put("customerId", data.getCustomerName());
        systemFieldMap.put("amount", data.getAmount());
		systemFieldMap.put("expectedRepaymentAmount", data.getExpectedRepaymentAmount());
        systemFieldMap.put("alreadyPayAmount", data.getAlreadyPayAmount());
        systemFieldMap.put("number", data.getNumber());
        if (StringUtils.isNotBlank(data.getApprovalStatus())) {
            systemFieldMap.put("approvalStatus", Translator.get("contract.approval_status." + data.getApprovalStatus().toLowerCase(), Locale.SIMPLIFIED_CHINESE));
        }
		String exportStage = StringUtils.defaultIfBlank(data.getDisplayStage(), data.getStage());
        if (StringUtils.isNotBlank(exportStage)) {
			systemFieldMap.put("stage", Translator.get("contract.stage." + exportStage.toLowerCase(), Locale.SIMPLIFIED_CHINESE));
        }
		systemFieldMap.put("signerId", data.getSignerNameSnapshot());
        systemFieldMap.put("createUser", data.getCreateUserName());
        systemFieldMap.put("createTime", TimeUtils.getDateTimeStr(data.getCreateTime()));
        systemFieldMap.put("updateUser", data.getUpdateUserName());
        systemFieldMap.put("updateTime", TimeUtils.getDateTimeStr(data.getUpdateTime()));
        systemFieldMap.put("voidReason", data.getVoidReason());
        systemFieldMap.put("startTime", TimeUtils.getDateTimeStr(data.getStartTime()));
        systemFieldMap.put("endTime", TimeUtils.getDateTimeStr(data.getEndTime()));
        return systemFieldMap;
    }
}
