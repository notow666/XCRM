package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.domain.BaseResourceSubField;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerContact;
import cn.cordys.crm.customer.domain.CustomerField;
import cn.cordys.crm.customer.domain.CustomerFieldBlob;
import cn.cordys.crm.customer.domain.CustomerPool;
import cn.cordys.crm.customer.dto.MobileConflictDTO;
import cn.cordys.crm.customer.dto.response.PoolCustomerImportCheckResponse;
import cn.cordys.crm.customer.dto.response.PoolImportErrorSummary;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.constants.ExportConstants;
import cn.cordys.crm.system.constants.SheetKey;
import cn.cordys.crm.system.domain.ExportTask;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.field.base.SubField;
import cn.cordys.crm.system.excel.CustomImportAfterDoConsumer;
import cn.cordys.crm.system.excel.handler.CustomHeadColWidthStyleStrategy;
import cn.cordys.crm.system.excel.handler.CustomTemplateWriteHandler;
import cn.cordys.crm.system.excel.listener.CustomFieldCheckEventListener;
import cn.cordys.crm.system.excel.listener.CustomFieldImportEventListener;
import cn.cordys.crm.system.service.ExportTaskService;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.excel.domain.ExcelErrData;
import cn.cordys.excel.utils.EasyExcelExporter;
import cn.cordys.file.engine.DefaultRepositoryDir;
import cn.cordys.mybatis.BaseMapper;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.util.BooleanUtils;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class PoolCustomerImportService {

    @Resource
    private ExtCustomerMapper customerMapper;
    @Resource
    private CustomerPoolService customerPoolService;
    @Resource
    private ModuleFormService moduleFormService;
    @Resource
    private CustomerStageService customerStageService;
    @Resource
    private ExportTaskService exportTaskService;
    @Resource
    private BaseMapper<CustomerPool> customerPoolBaseMapper;
    @Resource
    private BaseMapper<Customer> customerBaseMapper;
    @Resource
    private BaseMapper<CustomerField> customerFieldMapper;
    @Resource
    private BaseMapper<CustomerFieldBlob> customerFieldBlobMapper;
    @Resource
    private CustomerFieldService customerFieldService;
    @Resource
    private BaseMapper<CustomerContact> customerContactMapper;

    private static final String OWNER_FIELD_KEY = "customerOwner";
    
    /**
     * 分批查询数据库的手机号数量上限
     * MySQL IN 条件超过1000个值后效率急剧下降，设置为1000
     */
    private static final int BATCH_QUERY_SIZE = 1000;
    
    /**
     * 错误类型常量
     */
    private static final String ERROR_TYPE_FIELD_VALIDATION = "FIELD_VALIDATION";
    private static final String ERROR_TYPE_EXCEL_DUPLICATE = "EXCEL_DUPLICATE";
    private static final String ERROR_TYPE_PRIVATE_CONFLICT = "PRIVATE_CONFLICT";
    private static final String ERROR_TYPE_OTHER_POOL_CONFLICT = "OTHER_POOL_CONFLICT";
    
    /**
     * 冲突类型常量（从数据库返回）
     */
    private static final String CONFLICT_TYPE_PRIVATE = "PRIVATE";
    private static final String CONFLICT_TYPE_OTHER_POOL = "OTHER_POOL";

    /**
     * 下载公海导入模板（去除负责人字段）
     */
    public void downloadImportTpl(HttpServletResponse response, String currentOrg) {
        List<List<String>> headList = moduleFormService.getCustomImportHeadsNoRef(FormKey.CUSTOMER.getKey(), currentOrg);
        List<BaseField> allFields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), currentOrg);
        List<BaseField> filteredFields = filterOwnerField(allFields);
        
        List<List<String>> filteredHeadList = headList.stream()
                .filter(head -> !OWNER_FIELD_KEY.equals(getFieldInternalKeyByName(head.get(0), allFields)))
                .collect(Collectors.toList());
        
        new EasyExcelExporter()
                .exportMultiSheetTplWithSharedHandler(response, filteredHeadList,
                        Translator.get("pool.import.tpl.name"), Translator.get(SheetKey.DATA), Translator.get(SheetKey.COMMENT),
                        new CustomTemplateWriteHandler(filteredFields),
                        new CustomHeadColWidthStyleStrategy());
    }
    
    private String getFieldInternalKeyByName(String fieldName, List<BaseField> fields) {
        return fields.stream()
                .filter(field -> fieldName.equals(field.getName()))
                .map(BaseField::getInternalKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * 公海导入预检查（重构版）
     * 
     * 优化点：
     * 1. 合并数据库查询：一次查询替代原来两次查询（客户池+其他公海池）
     * 2. 分批处理：每批最多查询1000个手机号，避免SQL IN条件过长
     * 3. 流式写入错误Excel：使用EasyExcel重新生成，避免内存溢出
     */
    public PoolCustomerImportCheckResponse preCheck(MultipartFile file, String poolId, String orgId, String userId) {
        long totalStartTime = System.currentTimeMillis();
        log.info("========== 公海导入预检查开始 ========== poolId: {}", poolId);

        if (file == null) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }

        long stepStartTime = System.currentTimeMillis();
        CustomerPool pool = validatePool(poolId);
        log.info("[耗时] 步骤1-校验公海池: {} ms", System.currentTimeMillis() - stepStartTime);

        stepStartTime = System.currentTimeMillis();
        List<BaseField> fields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), orgId);
        List<BaseField> filteredFields = filterOwnerField(fields);
        removeUniqueRules(filteredFields);
        log.info("[耗时] 步骤2-获取表单字段: {} ms", System.currentTimeMillis() - stepStartTime);

        PoolCustomerCheckEventListener checkListener = new PoolCustomerCheckEventListener(
                filteredFields, "customer", "customer_field", orgId);
        
        stepStartTime = System.currentTimeMillis();
        readExcel(file, checkListener);
        log.info("[耗时] 步骤3-读取Excel: {} ms, 成功行数: {}, 错误行数: {}", 
                System.currentTimeMillis() - stepStartTime, checkListener.getSuccess(), checkListener.getErrList().size());

        int totalRows = checkListener.getSuccess() + checkListener.getErrList().size();
        Map<Integer, String> rowMobileMap = checkListener.getRowMobileMap();
        String mobileFieldName = checkListener.getMobileFieldName();
        log.info("[统计] Excel总行数: {}, 手机号数量: {}", totalRows, rowMobileMap.size());

        stepStartTime = System.currentTimeMillis();
        ErrorCheckResult result = doErrorCheck(rowMobileMap, checkListener.getErrList(), orgId, poolId, mobileFieldName);
        log.info("[耗时] 步骤4-错误检查: {} ms", System.currentTimeMillis() - stepStartTime);

        PoolCustomerImportCheckResponse response = buildResponse(result, totalRows);

        if (!result.isPassed()) {
            stepStartTime = System.currentTimeMillis();
            String errorFileId = IDGenerator.nextStr();
            String errorFileName = Translator.get("pool.import.error.file.name");
            try {
                writeErrorExcelStreaming(file, result, filteredFields, errorFileId, orgId);
                response.setErrorFileId(errorFileId);
                response.setErrorFileName(errorFileName);
                log.info("[耗时] 步骤5-写入错误Excel: {} ms, 错误行数: {}", 
                        System.currentTimeMillis() - stepStartTime, result.getRowErrorCount());
            } catch (Exception e) {
                log.error("write error excel failed: {}", e.getMessage(), e);
            }
            response.setErrorSummary(buildErrorSummary(result));
        }

        log.info("========== 公海导入预检查完成 ========== 总耗时: {} ms, 通过: {}, 错误数: {}", 
                System.currentTimeMillis() - totalStartTime, result.isPassed(), result.getRowErrorCount());

        return response;
    }

    /**
     * 移除唯一性校验规则
     */
    private void removeUniqueRules(List<BaseField> fields) {
        for (BaseField field : fields) {
            field.getRules().removeIf(rule -> "unique".equals(rule.getKey()));
        }
    }

    /**
     * 读取Excel文件
     */
    private void readExcel(MultipartFile file, PoolCustomerCheckEventListener listener) {
        try {
            EasyExcel.read(file.getInputStream(), listener).headRowNumber(1).ignoreEmptyRow(true).sheet().doRead();
        } catch (Exception e) {
            log.error("pool customer import pre-check error: {}", e.getMessage());
            throw new GenericException(e.getMessage());
        }
    }

    /**
     * 执行分层错误检查（优先级校验）
     * 
     * 校验优先级：
     * 1. 基础字段校验（必填、手机号格式） - 最优先
     * 2. Excel内重复检查 - 第二优先
     * 3. 客户池冲突检查 - 第三优先
     * 4. 其他公海池冲突检查 - 最后
     * 
     * 如果第N层检查有错误，则不继续执行第N+1层检查
     */
    private ErrorCheckResult doErrorCheck(Map<Integer, String> rowMobileMap, List<ExcelErrData> fieldErrors,
                                           String orgId, String poolId, String mobileFieldName) {
        long layerStartTime;
        ErrorCheckResult result = new ErrorCheckResult();

        // 第一层：基础字段校验（必填 + 手机号格式）
        layerStartTime = System.currentTimeMillis();
        checkFieldValidation(rowMobileMap, fieldErrors, result, mobileFieldName);
        log.info("[耗时] Layer1-字段校验: {} ms, 错误数: {}", 
                System.currentTimeMillis() - layerStartTime, result.getRowErrorCount());
        
        // 如果第一层有错误，直接返回，不继续后续校验
        if (!result.isPassed()) {
            log.info("Layer 1 validation failed, skip layer 2/3/4. Error count: {}", result.getRowErrorCount());
            return result;
        }

        // 第二层：Excel内手机号重复检查
        layerStartTime = System.currentTimeMillis();
        checkExcelDuplicate(rowMobileMap, result);
        log.info("[耗时] Layer2-Excel重复检查: {} ms, 错误数: {}", 
                System.currentTimeMillis() - layerStartTime, result.getRowErrorCount());
        
        // 如果第二层有错误，直接返回，不继续后续校验
        if (!result.isPassed()) {
            log.info("Layer 2 validation failed, skip layer 3/4. Error count: {}", result.getRowErrorCount());
            return result;
        }

        // 第三层和第四层：数据库冲突检查
        layerStartTime = System.currentTimeMillis();
        checkDatabaseConflictsLayered(rowMobileMap, orgId, poolId, result);
        log.info("[耗时] Layer3&4-数据库冲突检查: {} ms, 错误数: {}", 
                System.currentTimeMillis() - layerStartTime, result.getRowErrorCount());
        
        return result;
    }

    /**
     * 分层检查数据库冲突（第三层：客户池 + 第四层：其他公海池）
     * 
     * 优化策略：
     * - 一次数据库查询获取所有冲突信息（性能优化）
     * - 分层处理错误，客户池冲突优先（逻辑优化）
     */
    private void checkDatabaseConflictsLayered(Map<Integer, String> rowMobileMap, String orgId, String poolId,
                                                 ErrorCheckResult result) {
        long stepStartTime = System.currentTimeMillis();
        
        List<String> validMobiles = rowMobileMap.entrySet().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && !result.isInvalidMobileRow(e.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .collect(Collectors.toList());

        log.info("[耗时] 筛选有效手机号: {} ms, 有效手机号数量: {}", 
                System.currentTimeMillis() - stepStartTime, validMobiles.size());

        if (CollectionUtils.isEmpty(validMobiles)) {
            return;
        }

        // 一次查询获取所有冲突信息
        stepStartTime = System.currentTimeMillis();
        Map<String, String> mobileConflictTypeMap = queryConflictsBatch(orgId, poolId, validMobiles);
        log.info("[耗时] 批量查询数据库冲突: {} ms, 冲突手机号数量: {}", 
                System.currentTimeMillis() - stepStartTime, mobileConflictTypeMap.size());

        // 第三层：先处理客户池冲突
        stepStartTime = System.currentTimeMillis();
        processPrivatePoolConflicts(rowMobileMap, mobileConflictTypeMap, result);
        log.info("[耗时] Layer3-处理客户池冲突: {} ms, 客户池冲突数: {}", 
                System.currentTimeMillis() - stepStartTime, result.getPrivateConflictCount());
        
        // 如果第三层有错误，直接返回，不处理第四层
        if (!result.isPassed()) {
            log.info("Layer 3 validation failed (private pool conflict), skip layer 4. Error count: {}", result.getRowErrorCount());
            return;
        }

        // 第四层：处理其他公海池冲突
        stepStartTime = System.currentTimeMillis();
        processOtherPoolConflicts(rowMobileMap, mobileConflictTypeMap, result);
        log.info("[耗时] Layer4-处理其他公海池冲突: {} ms, 其他公海池冲突数: {}", 
                System.currentTimeMillis() - stepStartTime, result.getOtherPoolConflictCount());
    }

    /**
     * 分批查询数据库获取冲突信息
     */
    private Map<String, String> queryConflictsBatch(String orgId, String poolId, List<String> validMobiles) {
        Map<String, String> mobileConflictTypeMap = new HashMap<>();
        int batchCount = 0;
        long batchStartTime;
        long totalDbQueryTime = 0;

        for (int i = 0; i < validMobiles.size(); i += BATCH_QUERY_SIZE) {
            int end = Math.min(i + BATCH_QUERY_SIZE, validMobiles.size());
            List<String> batchMobiles = validMobiles.subList(i, end);
            batchCount++;
            
            batchStartTime = System.currentTimeMillis();
            List<MobileConflictDTO> conflicts = customerMapper.getMobileConflicts(orgId, poolId, batchMobiles);
            long batchTime = System.currentTimeMillis() - batchStartTime;
            totalDbQueryTime += batchTime;
            
            for (MobileConflictDTO dto : conflicts) {
                mobileConflictTypeMap.put(dto.getMobile(), dto.getConflictType());
            }
            
            if (batchCount % 10 == 0 || i + BATCH_QUERY_SIZE >= validMobiles.size()) {
                log.info("[耗时] DB查询第{}批: {} ms, 本批手机号数: {}, 冲突数: {}", 
                        batchCount, batchTime, batchMobiles.size(), conflicts.size());
            }
        }
        
        log.info("[耗时] DB查询总计: {} ms, 总批数: {}, 总手机号数: {}", 
                totalDbQueryTime, batchCount, validMobiles.size());

        return mobileConflictTypeMap;
    }

    /**
     * 第三层：处理客户池冲突
     */
    private void processPrivatePoolConflicts(Map<Integer, String> rowMobileMap, Map<String, String> mobileConflictTypeMap,
                                              ErrorCheckResult result) {
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            if (result.hasRowError(entry.getKey())) {
                continue;
            }
            String mobile = entry.getValue();
            if (StringUtils.isBlank(mobile)) {
                continue;
            }
            String conflictType = mobileConflictTypeMap.get(mobile);
            if (CONFLICT_TYPE_PRIVATE.equals(conflictType)) {
                result.addRowError(entry.getKey(), ERROR_TYPE_PRIVATE_CONFLICT, null);
                result.incrementPrivateConflictCount();
            }
        }
    }

    /**
     * 第四层：处理其他公海池冲突
     */
    private void processOtherPoolConflicts(Map<Integer, String> rowMobileMap, Map<String, String> mobileConflictTypeMap,
                                            ErrorCheckResult result) {
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            if (result.hasRowError(entry.getKey())) {
                continue;
            }
            String mobile = entry.getValue();
            if (StringUtils.isBlank(mobile)) {
                continue;
            }
            String conflictType = mobileConflictTypeMap.get(mobile);
            if (CONFLICT_TYPE_OTHER_POOL.equals(conflictType)) {
                result.addRowError(entry.getKey(), ERROR_TYPE_OTHER_POOL_CONFLICT, null);
                result.incrementOtherPoolConflictCount();
            }
        }
    }

    /**
     * 第一层校验：基础字段校验（必填 + 手机号格式）
     */
    private void checkFieldValidation(Map<Integer, String> rowMobileMap, List<ExcelErrData> fieldErrors,
                                       ErrorCheckResult result, String mobileFieldName) {
        // 1. 手机号格式校验
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            String mobile = entry.getValue();
            if (StringUtils.isNotBlank(mobile) && !PoolCustomerCheckEventListener.MOBILE_PATTERN.matcher(mobile).matches()) {
                result.addInvalidMobileRow(entry.getKey());
                result.addRowError(entry.getKey(), ERROR_TYPE_FIELD_VALIDATION, 
                        mobileFieldName + Translator.get("phone.wrong.format"));
                result.incrementFieldValidationCount();
            }
        }

        // 2. 收集字段校验错误（必填等其他校验）
        for (ExcelErrData errData : fieldErrors) {
            if (!result.hasRowError(errData.getRowNum())) {
                result.addRowError(errData.getRowNum(), ERROR_TYPE_FIELD_VALIDATION, errData.getErrMsg());
                result.incrementFieldValidationCount();
            }
        }
    }

    /**
     * 检查Excel内手机号重复
     */
    private void checkExcelDuplicate(Map<Integer, String> rowMobileMap, ErrorCheckResult result) {
        Map<String, Set<Integer>> mobileRowCount = new HashMap<>();
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            if (StringUtils.isNotBlank(entry.getValue()) && !result.isInvalidMobileRow(entry.getKey())) {
                mobileRowCount.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
            }
        }

        for (Map.Entry<String, Set<Integer>> entry : mobileRowCount.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (Integer rowNum : entry.getValue()) {
                    if (!result.hasRowError(rowNum)) {
                        result.addRowError(rowNum, ERROR_TYPE_EXCEL_DUPLICATE, null);
                        result.incrementExcelDuplicateCount();
                    }
                }
            }
        }
    }

    /**
      * 构建响应对象
      */
    private PoolCustomerImportCheckResponse buildResponse(ErrorCheckResult result, int totalRows) {
        int errorCount = result.getRowErrorCount();
        int successCount = totalRows - errorCount;

        return PoolCustomerImportCheckResponse.builder()
                .passed(result.isPassed())
                .totalCount(totalRows)
                .successCount(successCount)
                .errorCount(errorCount)
                .build();
    }

    /**
     * 构建错误摘要
     */
    private PoolImportErrorSummary buildErrorSummary(ErrorCheckResult result) {
        return PoolImportErrorSummary.builder()
                .fieldValidationCount(result.getFieldValidationCount())
                .excelDuplicateCount(result.getExcelDuplicateCount())
                .privateConflictCount(result.getPrivateConflictCount())
                .otherPoolConflictCount(result.getOtherPoolConflictCount())
                .build();
    }

/**
     * 使用EasyExcel流式写入错误Excel
     * 优化：重新生成Excel而非修改原文件，避免内存溢出
     */
    private void writeErrorExcelStreaming(MultipartFile file, ErrorCheckResult result, 
                                           List<BaseField> fields, String fileId, String orgId) throws IOException {
        String exportDirPath = DefaultRepositoryDir.getDefaultDir() + File.separator
                + DefaultRepositoryDir.getExportDir(orgId) + File.separator + fileId;
        File dir = new File(exportDirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("cannot create export dir: " + dir.getAbsolutePath());
        }
        String fileName = Translator.get("pool.import.error.file.name") + ".xlsx";
        File outputFile = new File(dir, fileName);

        List<List<String>> heads = buildImportHeads(fields);
        
        List<ErrorRowData> errorRowList = buildErrorRowDataList(file, result, heads.size());

        try (OutputStream os = new FileOutputStream(outputFile)) {
            EasyExcel.write(os)
                    .head(heads)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .registerWriteHandler(new ErrorRowStyleHandler(result.getRowErrorTypeMap(), result.getRowErrorMsgMap()))
                    .sheet(Translator.get(SheetKey.DATA))
                    .doWrite(errorRowList);
        }
    }

    /**
     * 从原Excel读取数据并标记错误行
     */
    private List<ErrorRowData> buildErrorRowDataList(MultipartFile file, ErrorCheckResult result, int columnCount) {
        List<ErrorRowData> dataList = new ArrayList<>();
        
        try {
            EasyExcel.read(file.getInputStream(), new ErrorDataListener(dataList, columnCount))
                    .headRowNumber(1)
                    .ignoreEmptyRow(true)
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            log.error("read excel for error marking failed: {}", e.getMessage());
        }
        
        return dataList;
    }

    /**
     * 错误检查结果内部类
     */
    private static class ErrorCheckResult {
        @Getter
        private final Map<Integer, String> rowErrorTypeMap = new LinkedHashMap<>();
        @Getter
        private final Map<Integer, String> rowErrorMsgMap = new LinkedHashMap<>();
        @Getter
        private final Set<Integer> invalidMobileRows = new HashSet<>();
        @Getter
        private int fieldValidationCount = 0;
        @Getter
        private int excelDuplicateCount = 0;
        @Getter
        private int privateConflictCount = 0;
        @Getter
        private int otherPoolConflictCount = 0;

        public boolean isPassed() {
            return rowErrorTypeMap.isEmpty();
        }

        public int getRowErrorCount() {
            return rowErrorTypeMap.size();
        }

        public void addInvalidMobileRow(Integer rowNum) {
            invalidMobileRows.add(rowNum);
        }

        public boolean isInvalidMobileRow(Integer rowNum) {
            return invalidMobileRows.contains(rowNum);
        }

        public void addRowError(Integer rowNum, String errorType, String errorMsg) {
            rowErrorTypeMap.put(rowNum, errorType);
            if (StringUtils.isNotBlank(errorMsg)) {
                rowErrorMsgMap.put(rowNum, errorMsg);
            }
        }

        public boolean hasRowError(Integer rowNum) {
            return rowErrorTypeMap.containsKey(rowNum);
        }

        public void incrementFieldValidationCount() {
            fieldValidationCount++;
        }

        public void incrementExcelDuplicateCount() {
            excelDuplicateCount++;
        }

        public void incrementPrivateConflictCount() {
            privateConflictCount++;
        }

        public void incrementOtherPoolConflictCount() {
            otherPoolConflictCount++;
        }
}

    /**
     * 错误行数据（用于 EasyExcel 写入）
     */
    private static class ErrorRowData extends ArrayList<String> {
    }

    /**
     * 错误数据读取监听器
     */
    private static class ErrorDataListener implements cn.idev.excel.read.listener.ReadListener<Map<Integer, String>> {
        private final List<ErrorRowData> dataList;
        private final int columnCount;

        public ErrorDataListener(List<ErrorRowData> dataList, int columnCount) {
            this.dataList = dataList;
            this.columnCount = columnCount;
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            ErrorRowData rowData = new ErrorRowData();
            for (int i = 0; i < columnCount; i++) {
                rowData.add(data.getOrDefault(i, ""));
            }
            dataList.add(rowData);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
        }
    }

    /**
     * 错误行样式处理器（基于EasyExcel RowWriteHandler）
     */
    private static class ErrorRowStyleHandler implements cn.idev.excel.write.handler.RowWriteHandler {
        private final Map<Integer, String> rowErrorTypeMap;
        private final Map<Integer, String> rowErrorMsgMap;
        private final Map<String, CellStyle> styleCache = new HashMap<>();

        public ErrorRowStyleHandler(Map<Integer, String> rowErrorTypeMap, Map<Integer, String> rowErrorMsgMap) {
            this.rowErrorTypeMap = rowErrorTypeMap;
            this.rowErrorMsgMap = rowErrorMsgMap;
        }

        @Override
        public void afterRowDispose(cn.idev.excel.write.handler.context.RowWriteHandlerContext context) {
            if (cn.idev.excel.util.BooleanUtils.isTrue(context.getHead())) {
                return;
            }
            
            Row row = context.getRow();
            if (row == null) {
                return;
            }
            
            Integer relativeRowIndex = context.getRelativeRowIndex();
            if (relativeRowIndex == null) {
                return;
            }
            
            Integer rowNum = relativeRowIndex + 1;
            String errorType = rowErrorTypeMap.get(rowNum);
            if (errorType == null) {
                return;
            }

            Workbook workbook = context.getWriteSheetHolder().getSheet().getWorkbook();
            CellStyle errorStyle = getOrCreateErrorStyle(workbook, errorType);

            for (int i = 0; i < row.getLastCellNum(); i++) {
                Cell cell = row.getCell(i);
                if (cell == null) {
                    cell = row.createCell(i);
                }
                cell.setCellStyle(errorStyle);
            }

            String errorMsg = rowErrorMsgMap.get(rowNum);
            if (StringUtils.isNotBlank(errorMsg)) {
                addComment(row, errorMsg, workbook);
            }
        }

        private CellStyle getOrCreateErrorStyle(Workbook workbook, String errorType) {
            return styleCache.computeIfAbsent(errorType, type -> createErrorStyle(workbook, type));
        }

        private CellStyle createErrorStyle(Workbook workbook, String errorType) {
            CellStyle style = workbook.createCellStyle();
            short colorIndex = getColorIndex(errorType);
            style.setFillForegroundColor(colorIndex);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private short getColorIndex(String errorType) {
            if (ERROR_TYPE_FIELD_VALIDATION.equals(errorType)) {
                return IndexedColors.RED.getIndex();
            } else if (ERROR_TYPE_EXCEL_DUPLICATE.equals(errorType)) {
                return IndexedColors.LIGHT_YELLOW.getIndex();
            } else if (ERROR_TYPE_PRIVATE_CONFLICT.equals(errorType)) {
                return IndexedColors.ORANGE.getIndex();
            } else if (ERROR_TYPE_OTHER_POOL_CONFLICT.equals(errorType)) {
                return IndexedColors.LIGHT_BLUE.getIndex();
            } else {
                return IndexedColors.RED.getIndex();
            }
        }

        private void addComment(Row row, String errorMsg, Workbook workbook) {
            Cell firstCell = row.getCell(0);
            if (firstCell == null) {
                return;
            }
            Drawing<?> drawing = row.getSheet().createDrawingPatriarch();
            CreationHelper factory = workbook.getCreationHelper();
            Comment comment = drawing.createCellComment(factory.createClientAnchor());
            comment.setString(factory.createRichTextString(errorMsg));
            firstCell.setCellComment(comment);
        }
    }

    /**
     * 下载预检查错误文件
     */
    public void downloadErrorFile(String fileId, String orgId, HttpServletResponse response) {
        String exportDirPath = DefaultRepositoryDir.getDefaultDir() + File.separator
                + DefaultRepositoryDir.getExportDir(orgId) + File.separator + fileId;
        File dir = new File(exportDirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".xlsx"));
        if (files == null || files.length == 0) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }
        File errorFile = files[0];
        try (FileInputStream fis = new FileInputStream(errorFile)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(errorFile.getName(), "UTF-8") + "\"");
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("download error file failed: {}", e.getMessage());
            throw new GenericException(e.getMessage());
        }
    }

    /**
     * 公海导入执行（异步）
     */
    public String realImport(MultipartFile file, String poolId, String userId, String orgId) {
        CustomerPool pool = validatePool(poolId);
        exportTaskService.checkUserTaskLimit(userId, ExportConstants.ExportStatus.PREPARED.toString());

        String fileId = IDGenerator.nextStr();
        String fileName = Translator.get("pool.import.tpl.name");
        ExportTask exportTask = exportTaskService.saveTask(orgId, fileId, userId,
                ExportConstants.ExportType.CUSTOMER_POOL_IMPORT.toString(), fileName);

        Locale locale = LocaleContextHolder.getLocale();
        Thread.startVirtualThread(() -> {
            try {
                LocaleContextHolder.setLocale(locale);
                doImport(file, poolId, userId, orgId);
                exportTaskService.update(exportTask.getId(), ExportConstants.ExportStatus.SUCCESS.toString(), userId);
            } catch (Exception e) {
                log.error("pool customer import error: {}", e.getMessage());
                exportTaskService.update(exportTask.getId(), ExportConstants.ExportStatus.ERROR.toString(), userId);
            }
        });

        return exportTask.getId();
    }

    private void doImport(MultipartFile file, String poolId, String userId, String orgId) {
        try {
            List<BaseField> fields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), orgId);
            List<BaseField> filteredFields = filterOwnerField(fields);
            removeUniqueRules(filteredFields);

            CustomImportAfterDoConsumer<Customer, BaseResourceSubField> afterDo = (customers, customerFields, customerFieldBlobs) -> {
                customers.forEach(customer -> {
                    customer.setInSharedPool(true);
                    customer.setPoolId(poolId);
                    customer.setOwner(null);
                    customer.setCollectionTime(null);
                    customer.setStage(null);
                    customer.setStageStatus(null);
                });

                List<String> mobileList = customers.stream()
                        .map(Customer::getMobile)
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .collect(Collectors.toList());

                List<Customer> newCustomers = new ArrayList<>();
                List<Customer> updateCustomers = new ArrayList<>();
                Map<String, String> idMapping = new HashMap<>();

                if (CollectionUtils.isNotEmpty(mobileList)) {
                    List<Customer> existingCustomers = customerMapper.getPoolCustomersByMobiles(orgId, poolId, mobileList);
                    Map<String, Customer> existingMap = existingCustomers.stream()
                            .collect(Collectors.toMap(Customer::getMobile, c -> c, (a, b) -> a));

                    for (Customer customer : customers) {
                        String mobile = customer.getMobile();
                        if (StringUtils.isNotBlank(mobile) && existingMap.containsKey(mobile)) {
                            Customer existing = existingMap.get(mobile);
                            idMapping.put(customer.getId(), existing.getId());
                            customer.setId(existing.getId());
                            customer.setUpdateTime(System.currentTimeMillis());
                            customer.setUpdateUser(userId);
                            updateCustomers.add(customer);
                        } else {
                            newCustomers.add(customer);
                        }
                    }
                } else {
                    newCustomers.addAll(customers);
                }

                if (!idMapping.isEmpty()) {
                    customerFields.forEach(f -> {
                        if (idMapping.containsKey(f.getResourceId())) {
                            f.setResourceId(idMapping.get(f.getResourceId()));
                        }
                    });
                    customerFieldBlobs.forEach(f -> {
                        if (idMapping.containsKey(f.getResourceId())) {
                            f.setResourceId(idMapping.get(f.getResourceId()));
                        }
                    });
                }

                if (CollectionUtils.isNotEmpty(newCustomers)) {
                    customerBaseMapper.batchInsert(newCustomers);

                    for (Customer customer : newCustomers) {
                        customer.setUpdateUser(userId);
                        customer.setUpdateTime(System.currentTimeMillis());
                        customerMapper.moveToPoolIncludeStage(customer);
                    }

                    List<String> newCustomerIds = newCustomers.stream().map(Customer::getId).collect(Collectors.toList());

                    List<BaseResourceSubField> newFields = customerFields.stream()
                            .filter(f -> newCustomerIds.contains(f.getResourceId()))
                            .collect(Collectors.toList());
                    List<BaseResourceSubField> newBlobFields = customerFieldBlobs.stream()
                            .filter(f -> newCustomerIds.contains(f.getResourceId()))
                            .collect(Collectors.toList());

                    if (CollectionUtils.isNotEmpty(newFields)) {
                        customerFieldMapper.batchInsert(newFields.stream()
                                .map(field -> BeanUtils.copyBean(new CustomerField(), field)).collect(Collectors.toList()));
                    }
                    if (CollectionUtils.isNotEmpty(newBlobFields)) {
                        customerFieldBlobMapper.batchInsert(newBlobFields.stream()
                                .map(field -> BeanUtils.copyBean(new CustomerFieldBlob(), field)).collect(Collectors.toList()));
                    }
                }

                List<CustomerContact> contacts = new ArrayList<>();
                for (Customer customer : newCustomers) {
                    if (StringUtils.isNotBlank(customer.getMobile())) {
                        CustomerContact contact = new CustomerContact();
                        contact.setId(IDGenerator.nextStr());
                        contact.setCustomerId(customer.getId());
                        contact.setName(customer.getName());
                        contact.setPhone(customer.getMobile());
                        contact.setOrganizationId(orgId);
                        contact.setCreateTime(System.currentTimeMillis());
                        contact.setUpdateTime(System.currentTimeMillis());
                        contact.setCreateUser(userId);
                        contact.setUpdateUser(userId);
                        contact.setEnable(true);
                        contacts.add(contact);
                    }
                }
                if (CollectionUtils.isNotEmpty(contacts)) {
                    customerContactMapper.batchInsert(contacts);
                }

                if (CollectionUtils.isNotEmpty(updateCustomers)) {
                    List<String> updateCustomerIds = updateCustomers.stream().map(Customer::getId).collect(Collectors.toList());
                    for (Customer customer : updateCustomers) {
                        customerMapper.moveToPoolIncludeStage(customer);
                    }

                    customerFieldService.deleteByResourceIds(updateCustomerIds);

                    List<BaseResourceSubField> updateFields = customerFields.stream()
                            .filter(f -> updateCustomerIds.contains(f.getResourceId()))
                            .collect(Collectors.toList());
                    List<BaseResourceSubField> updateBlobFields = customerFieldBlobs.stream()
                            .filter(f -> updateCustomerIds.contains(f.getResourceId()))
                            .collect(Collectors.toList());

                    if (CollectionUtils.isNotEmpty(updateFields)) {
                        customerFieldMapper.batchInsert(updateFields.stream()
                                .map(field -> BeanUtils.copyBean(new CustomerField(), field)).collect(Collectors.toList()));
                    }
                    if (CollectionUtils.isNotEmpty(updateBlobFields)) {
                        customerFieldBlobMapper.batchInsert(updateBlobFields.stream()
                                .map(field -> BeanUtils.copyBean(new CustomerFieldBlob(), field)).collect(Collectors.toList()));
                    }
                }
            };

            CustomFieldImportEventListener<Customer> eventListener = new CustomFieldImportEventListener<>(
                    filteredFields, Customer.class, orgId, userId, "customer_field", afterDo, 2000, null, null);
            FastExcelFactory.read(file.getInputStream(), eventListener).headRowNumber(1).ignoreEmptyRow(true).sheet().doRead();

        } catch (Exception e) {
            Throwable cause = e.getCause();
            log.error("pool customer import error: {}", cause != null ? cause.getMessage() : e.getMessage());
            throw new GenericException(cause != null ? cause : e);
        }
    }

    private CustomerPool validatePool(String poolId) {
        CustomerPool pool = customerPoolBaseMapper.selectByPrimaryKey(poolId);
        if (pool == null) {
            throw new GenericException(Translator.get("pool_import_pool_not_exist"));
        }
        if (!pool.getEnable()) {
            throw new GenericException(Translator.get("pool_import_pool_disabled"));
        }
        return pool;
    }

    private List<BaseField> filterOwnerField(List<BaseField> fields) {
        return fields.stream()
                .filter(field -> !OWNER_FIELD_KEY.equals(field.getInternalKey()))
                .collect(Collectors.toList());
    }

    private List<List<String>> buildImportHeads(List<BaseField> fields) {
        List<List<String>> heads = new ArrayList<>();
        for (BaseField field : fields) {
            if (StringUtils.isEmpty(field.getResourceFieldId())) {
                if (field instanceof SubField subField && CollectionUtils.isNotEmpty(subField.getSubFields())) {
                    for (BaseField sub : subField.getSubFields()) {
                        List<String> head = new ArrayList<>();
                        head.add(field.getName());
                        head.add(sub.getName());
                        heads.add(head);
                    }
                } else {
                    heads.add(new ArrayList<>(Collections.singletonList(field.getName())));
                }
            }
        }
        return heads;
    }

    /**
     * 公海导入校验监听器（扩展自CustomFieldCheckEventListener，收集手机号并校验格式）
     */
    private static class PoolCustomerCheckEventListener extends CustomFieldCheckEventListener {

        public static final String MOBILE_REGEX = "^1[0-9]\\d{9}$";
        public static final Pattern MOBILE_PATTERN = Pattern.compile(MOBILE_REGEX);

        @Getter
        private final Map<Integer, String> rowMobileMap = new LinkedHashMap<>();

        @Getter
        private String mobileFieldName;

        private final Map<String, BusinessModuleField> businessFieldMap;

        public PoolCustomerCheckEventListener(List<BaseField> fields, String sourceTable, String fieldTable, String currentOrg) {
            super(fields, sourceTable, fieldTable, currentOrg);
            this.businessFieldMap = Arrays.stream(BusinessModuleField.values())
                    .collect(Collectors.toMap(BusinessModuleField::getKey, Function.identity()));
            for (BaseField field : fields) {
                if (field.getInternalKey() != null && BusinessModuleField.CUSTOMER_MOBILE.getKey().equals(field.getInternalKey())) {
                    mobileFieldName = field.getName();
                }
            }
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            super.invoke(data, context);
            Integer rowIndex = context.readRowHolder().getRowIndex();
            String mobileValue = null;
            if (mobileFieldName != null && this.headMap != null) {
                for (Map.Entry<Integer, String> entry : this.headMap.entrySet()) {
                    if (mobileFieldName.equals(entry.getValue())) {
                        mobileValue = data.get(entry.getKey());
                        break;
                    }
                }
            }
            if (mobileValue != null) {
                rowMobileMap.put(rowIndex, mobileValue);
            }
        }
    }
}