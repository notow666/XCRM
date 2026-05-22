package cn.cordys.crm.customer.service;

import cn.cordys.common.constants.BusinessModuleField;
import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.AsyncUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.CustomerPool;
import cn.cordys.crm.customer.dto.MobileConflictDTO;
import cn.cordys.crm.customer.dto.response.PoolCustomerImportCheckResponse;
import cn.cordys.crm.customer.dto.response.PoolImportErrorSummary;
import cn.cordys.crm.customer.mapper.ExtCustomerMapper;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.constants.SheetKey;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.excel.handler.CustomHeadColWidthStyleStrategy;
import cn.cordys.crm.system.excel.handler.CustomTemplateWriteHandler;
import cn.cordys.crm.system.excel.listener.CustomFieldCheckEventListener;
import cn.cordys.crm.system.notice.CommonNoticeSendService;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.excel.domain.ExcelErrData;
import cn.cordys.excel.utils.EasyExcelExporter;
import cn.cordys.context.TenantContext;
import cn.cordys.common.constants.SsePrincipalKind;
import cn.cordys.crm.system.notice.sse.SseService;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.file.engine.DefaultRepositoryDir;
import cn.cordys.mybatis.BaseMapper;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.context.AnalysisContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
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
    private BaseMapper<User> userBaseMapper;
    @Resource
    private CommonNoticeSendService commonNoticeSendService;
    @Resource
    private BaseMapper<CustomerPool> customerPoolBaseMapper;
    @Resource
    private PoolCustomerImportExecutor poolCustomerImportExecutor;
    @Resource(name = ExecutorBeanNames.BATCH)
    private Executor executor;
    @Resource
    private SseService sseService;

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
    private static final String ERROR_TYPE_OTHER_POOL_CONFLICT = "OTHER_POOL_CONFLICT";
    private static final String ERROR_TYPE_POOL_SOURCE_CONFLICT = "POOL_SOURCE_CONFLICT";
    
    /**
     * 冲突类型常量（从数据库返回）
     */
    private static final String CONFLICT_TYPE_OTHER_POOL = "OTHER_POOL";
    private static final String CONFLICT_TYPE_POOL_SOURCE_PRIVATE = "POOL_SOURCE_PRIVATE";

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
                .filter(field -> fieldName != null && fieldName.equals(field.getName()))
                .map(BaseField::getInternalKey)
                .filter(Objects::nonNull)
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
        log.info("========== 公海导入预检查开始 ========== poolId: {}", poolId);
        if (file == null) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }
        Map<String, Object> view = new HashMap<>();
        // 步骤1-校验公海池
        CustomerPool pool = validatePool(poolId);
        view.put("操作员", userId);
        view.put("公海池", poolId + "-" + pool.getName());

        // 步骤2-获取表单字段
        List<BaseField> fields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), orgId);
        List<BaseField> filteredFields = filterOwnerField(fields);
        removeUniqueRules(filteredFields);

        // 步骤3-读取Excel
        PoolCustomerCheckEventListener checkListener = new PoolCustomerCheckEventListener(
                filteredFields, "customer", "customer_field", orgId);
        readExcel(file, checkListener);

        view.put("读取成功行数", checkListener.getSuccess());
        view.put("读取错误行数", checkListener.getErrList().size());

        int totalRows = checkListener.getSuccess() + checkListener.getErrList().size();
        Map<Integer, String> rowMobileMap = checkListener.getRowMobileMap();
        String mobileFieldName = checkListener.getMobileFieldName();

        view.put("Excel总行数", totalRows);
        view.put("手机号数量", rowMobileMap.size());

        // 步骤4-错误检查
        ErrorCheckResult result = doErrorCheck(rowMobileMap, checkListener.getErrList(), orgId, poolId, mobileFieldName);

        PoolCustomerImportCheckResponse response = buildResponse(result, totalRows);
        if (!result.isPassed()) {
            // 步骤5-写入错误Excel
            String errorFileId = IDGenerator.nextStr();
            String errorFileName = Translator.get("pool.import.error.file.name");
            try {
                writeErrorExcelStreaming(file, result, errorFileId, orgId);
                response.setErrorFileId(errorFileId);
                response.setErrorFileName(errorFileName);

                view.put("错误行数", result.getRowErrorCount());
            } catch (Exception e) {
                log.error("write error excel failed: {}", e.getMessage(), e);
            }
            response.setErrorSummary(buildErrorSummary(result));
        }

        log.info("========== 公海导入预检查完成 ========== 总耗时: {} s, 预检结果: {}, 预检详情: [{}]",
                checkListener.getSeconds(), result.isPassed() ? "全部通过" : "有异常", JSON.toFormatJSONString(view));
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
     * 3. 其他公海池/公海来源客户冲突检查 - 第三优先
     * 
     * 如果第N层检查有错误，则不继续执行第N+1层检查
     */
    private ErrorCheckResult doErrorCheck(Map<Integer, String> rowMobileMap, List<ExcelErrData> fieldErrors,
                                           String orgId, String poolId, String mobileFieldName) {
        ErrorCheckResult result = new ErrorCheckResult();
        Map<String, Object> view = new HashMap<>();
        // 第一层：基础字段校验（必填 + 手机号格式）
        checkFieldValidation(rowMobileMap, fieldErrors, result, mobileFieldName);
        view.put("Layer1-字段校验", result.getRowErrorCount());
        
        // 如果第一层有错误，直接返回，不继续后续校验
        if (!result.isPassed()) {
            log.info("Layer 1 validation failed, skip layer 2/3. Error count: {}", result.getRowErrorCount());
            return result;
        }

        // 第二层：Excel内手机号重复检查
        checkExcelDuplicate(rowMobileMap, result);
        view.put("Layer2-Excel重复检查", result.getRowErrorCount());

        // 如果第二层有错误，直接返回，不继续后续校验
        if (!result.isPassed()) {
            log.info("Layer 2 validation failed, skip layer 3. Error count: {}", result.getRowErrorCount());
            return result;
        }

        // 第三层：数据库冲突检查（其他公海池 + 公海来源客户）
        checkDatabaseConflicts(rowMobileMap, orgId, poolId, result, view);

        view.put("Layer3-数据库冲突错误数", result.getRowErrorCount());

        log.info("基础校验详情: [{}]", JSON.toFormatJSONString(view));

        return result;
    }

    /**
     * 检查数据库冲突（其他公海池 + 公海来源客户）
     * 
     * 优化策略：
     * - 一次数据库查询获取所有冲突信息（性能优化）
     * - 分层处理错误，公海体系冲突优先（逻辑优化）
     */
    private void checkDatabaseConflicts(Map<Integer, String> rowMobileMap, String orgId, String poolId,
                                        ErrorCheckResult result, Map<String, Object> view) {
        List<String> validMobiles = rowMobileMap.entrySet().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && !result.isInvalidMobileRow(e.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .collect(Collectors.toList());

        view.put("有效手机号数量", validMobiles.size());

        if (CollectionUtils.isEmpty(validMobiles)) {
            return;
        }

        // 一次查询获取所有冲突信息
        Map<String, String> mobileConflictTypeMap = queryConflictsBatch(orgId, poolId, validMobiles);
        log.debug("冲突手机号数量: {}", mobileConflictTypeMap.size());

        processPoolRelatedConflicts(rowMobileMap, mobileConflictTypeMap, result);
        view.put("Layer3-其他公海池冲突数", result.getOtherPoolConflictCount());
        view.put("Layer3-公海来源客户冲突数", result.getPoolSourceConflictCount());
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
                log.debug("[耗时] DB查询第{}批: {} ms, 本批手机号数: {}, 冲突数: {}",
                        batchCount, batchTime, batchMobiles.size(), conflicts.size());
            }
        }

        log.debug("[耗时] DB查询总计: {} ms, 总批数: {}, 总手机号数: {}",
                totalDbQueryTime, batchCount, validMobiles.size());

        return mobileConflictTypeMap;
    }

    /**
     * 第四层：处理其他公海池冲突
     */
    private void processPoolRelatedConflicts(Map<Integer, String> rowMobileMap, Map<String, String> mobileConflictTypeMap,
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
            } else if (CONFLICT_TYPE_POOL_SOURCE_PRIVATE.equals(conflictType)) {
                result.addRowError(entry.getKey(), ERROR_TYPE_POOL_SOURCE_CONFLICT, null);
                result.incrementPoolSourceConflictCount();
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
                .otherPoolConflictCount(result.getOtherPoolConflictCount())
                .poolSourceConflictCount(result.getPoolSourceConflictCount())
                .build();
    }

    /**
     * 使用EasyExcel流式写入错误Excel
     * 优化：重新生成Excel而非修改原文件，避免内存溢出
     */
    private void writeErrorExcelStreaming(MultipartFile file, ErrorCheckResult result,
                                          String fileId, String orgId) throws IOException {
        String exportDirPath = DefaultRepositoryDir.getDefaultDir() + File.separator
                + DefaultRepositoryDir.getExportDir(TenantContext.requireTenantId()) + File.separator + fileId;
        File dir = new File(exportDirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("cannot create export dir: " + dir.getAbsolutePath());
        }
        String fileName = Translator.get("pool.import.error.file.name") + ".xlsx";
        File outputFile = new File(dir, fileName);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream);
             OutputStream os = new FileOutputStream(outputFile)) {
            markErrorRows(workbook.getSheetAt(0), result, workbook);
            workbook.write(os);
        }
    }

    private void markErrorRows(Sheet sheet, ErrorCheckResult result, Workbook workbook) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }
        int columnCount = headerRow.getLastCellNum();
        ErrorRowStyleHandler styleHandler = new ErrorRowStyleHandler(result.getRowErrorMsgMap());
        for (Map.Entry<Integer, String> entry : result.getRowErrorTypeMap().entrySet()) {
            Row row = sheet.getRow(entry.getKey());
            if (row == null) {
                continue;
            }
            styleHandler.markRow(row, columnCount, entry.getValue(), workbook);
        }
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
        private int otherPoolConflictCount = 0;
        @Getter
        private int poolSourceConflictCount = 0;

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

        public void incrementOtherPoolConflictCount() {
            otherPoolConflictCount++;
        }

        public void incrementPoolSourceConflictCount() {
            poolSourceConflictCount++;
        }
    }

    /**
     * 错误行样式处理器
     */
    private static class ErrorRowStyleHandler {
        private final Map<Integer, String> rowErrorMsgMap;
        private final Map<String, CellStyle> styleCache = new HashMap<>();

        public ErrorRowStyleHandler(Map<Integer, String> rowErrorMsgMap) {
            this.rowErrorMsgMap = rowErrorMsgMap;
        }

        public void markRow(Row row, int columnCount, String errorType, Workbook workbook) {
            for (int i = 0; i < columnCount; i++) {
                Cell cell = row.getCell(i);
                if (cell == null) {
                    cell = row.createCell(i);
                }
                cell.setCellStyle(getOrCreateErrorStyle(workbook, cell.getCellStyle(), errorType));
            }

            String errorMsg = rowErrorMsgMap.get(row.getRowNum());
            if (StringUtils.isNotBlank(errorMsg)) {
                addComment(row, errorMsg, workbook);
            }
        }

        private CellStyle getOrCreateErrorStyle(Workbook workbook, CellStyle baseStyle, String errorType) {
            short baseStyleIndex = baseStyle == null ? -1 : baseStyle.getIndex();
            String cacheKey = baseStyleIndex + "_" + errorType;
            return styleCache.computeIfAbsent(cacheKey, key -> createErrorStyle(workbook, baseStyle, errorType));
        }

        private CellStyle createErrorStyle(Workbook workbook, CellStyle baseStyle, String errorType) {
            CellStyle style = workbook.createCellStyle();
            if (baseStyle != null) {
                style.cloneStyleFrom(baseStyle);
            }
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
            } else if (ERROR_TYPE_OTHER_POOL_CONFLICT.equals(errorType) || ERROR_TYPE_POOL_SOURCE_CONFLICT.equals(errorType)) {
                return IndexedColors.LIGHT_BLUE.getIndex();
            } else {
                return IndexedColors.RED.getIndex();
            }
        }

        private void addComment(Row row, String errorMsg, Workbook workbook) {
            Cell firstCell = row.getCell(0);
            if (firstCell == null) {
                firstCell = row.createCell(0);
            }
            Drawing<?> drawing = row.getSheet().createDrawingPatriarch();
            CreationHelper factory = workbook.getCreationHelper();
            Comment comment = drawing.createCellComment(factory.createClientAnchor());
            comment.setString(factory.createRichTextString(errorMsg));
            comment.setAuthor("XCRM");
            firstCell.setCellComment(comment);
        }
    }

    /**
     * 下载预检查错误文件
     */
    public void downloadErrorFile(String fileId, String orgId, HttpServletResponse response) {
        String exportDirPath = DefaultRepositoryDir.getDefaultDir() + File.separator
                + DefaultRepositoryDir.getExportDir(TenantContext.requireTenantId()) + File.separator + fileId;
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
     * 公海导入执行（异步）；完成后通过站内消息通知操作人，不再创建导出任务。
     */
    public String realImport(MultipartFile file, String poolId, String userId, String orgId) {
        CustomerPool pool = validatePool(poolId);

        AsyncUtils.runAsync(() -> {
            Locale prevLocale = LocaleContextHolder.getLocale();
            try {
                User operator = userBaseMapper.selectByPrimaryKey(userId);
                Locale locale = resolveUserLocale(operator);
                LocaleContextHolder.setLocale(locale);

                String success = poolCustomerImportExecutor.executeImport(file, poolId, userId, orgId);
                sendImportNotice(userId, orgId, pool,true, success);
            } catch (Exception e) {
                log.error("pool customer import error", e);
                User operator = userBaseMapper.selectByPrimaryKey(userId);
                Locale locale = resolveUserLocale(operator);
                LocaleContextHolder.setLocale(locale);
                String errMsg = e.getMessage() != null ? StringUtils.abbreviate(e.getMessage(), 500) : "";
                sendImportNotice(userId, orgId, pool, false, errMsg);
            } finally {
                LocaleContextHolder.setLocale(prevLocale);
            }
        }, executor);

        return Translator.get("pool.import.accepted");
    }

    private static Locale resolveUserLocale(User operator) {
        if (operator == null || StringUtils.isBlank(operator.getLanguage())) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        String language = operator.getLanguage();
        if (Strings.CI.contains(language, "US")) {
            return Locale.US;
        }
        if (Strings.CI.contains(language, "TW")) {
            return Locale.TAIWAN;
        }
        return Locale.SIMPLIFIED_CHINESE;
    }

    private void sendImportNotice(String userId, String orgId, CustomerPool pool, boolean success, String msg) {
        if (DataSpecialistConstants.isSpecialistUserId(userId)) {
            sendDataSpecialistPoolImportSse(userId, pool, success, msg);
            return;
        }
        String resultSuffix = success
                ? Translator.get("pool.import.notify.result.success") + msg
                : Translator.get("pool.import.notify.result.failure") + (StringUtils.isNotBlank(msg) ? msg : "");
        Map<String, Object> resource = new HashMap<>();
        resource.put("poolId", pool.getId());
        resource.put("poolName", pool.getName());
        resource.put("resultSuffix", resultSuffix);
        resource.put("name", pool.getName());
        commonNoticeSendService.sendNotice(NotificationConstants.Module.CUSTOMER,
                NotificationConstants.Event.CUSTOMER_IMPORT, resource, userId, orgId,
                List.of(userId), false);
    }

    /**
     * 数据专员不属于租户用户，不走站内通知；按数据专员主体推送 SSE（与当前操作租户无关）。
     */
    private void sendDataSpecialistPoolImportSse(String userId, CustomerPool pool, boolean success, String msg) {
        String text = success
                ? Translator.get("pool.import.notify.result.success") + msg
                : Translator.get("pool.import.notify.result.failure") + (StringUtils.isNotBlank(msg) ? msg : "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", DataSpecialistConstants.SSE_POOL_IMPORT_RESULT_TYPE);
        payload.put("success", success);
        payload.put("poolId", pool.getId());
        payload.put("poolName", pool.getName());
        payload.put("message", text);
        String masterId = DataSpecialistConstants.masterSpecialistIdFromBusinessUserId(userId);
        sseService.sendToPrincipal(SsePrincipalKind.DATA_SPECIALIST, null, masterId, payload);
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
