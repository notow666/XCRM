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
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.context.AnalysisContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
     * 下载公海导入模板（去除负责人字段）
     */
    public void downloadImportTpl(HttpServletResponse response, String currentOrg) {
        List<List<String>> headList = moduleFormService.getCustomImportHeadsNoRef(FormKey.CUSTOMER.getKey(), currentOrg);
        List<BaseField> allFields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), currentOrg);
        List<BaseField> filteredFields = filterOwnerField(allFields);
        
        // 从原表头中过滤掉负责人字段
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
     * 公海导入预检查
     */
    public PoolCustomerImportCheckResponse preCheck(MultipartFile file, String poolId, String orgId, String userId) {
        if (file == null) {
            throw new GenericException(Translator.get("file_cannot_be_null"));
        }
        CustomerPool pool = validatePool(poolId);

        List<BaseField> fields = moduleFormService.getAllCustomImportFields(FormKey.CUSTOMER.getKey(), orgId);
        List<BaseField> filteredFields = filterOwnerField(fields);

        // 公海导入中，唯一性校验仅针对手机号，由 preCheck 方法自行分类处理（Excel重复、客户池冲突、其他公海池冲突）
        // 其他字段（如客户姓名）不做唯一性校验，移除所有字段的 unique 规则
        for (BaseField field : filteredFields) {
            field.getRules().removeIf(rule -> "unique".equals(rule.getKey()));
        }

        PoolCustomerCheckEventListener checkListener = new PoolCustomerCheckEventListener(
                filteredFields, "customer", "customer_field", orgId);
        try {
            EasyExcel.read(file.getInputStream(), checkListener).headRowNumber(1).ignoreEmptyRow(true).sheet().doRead();
        } catch (Exception e) {
            log.error("pool customer import pre-check error: {}", e.getMessage());
            throw new GenericException(e.getMessage());
        }

        int totalRows = checkListener.getSuccess() + checkListener.getErrList().size();
        Map<Integer, String> rowMobileMap = checkListener.getRowMobileMap();
        List<String> mobileList = rowMobileMap.values().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        // 检查Excel内手机号重复
        Map<String, Set<Integer>> mobileRowCount = new HashMap<>();
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            if (StringUtils.isNotBlank(entry.getValue())) {
                mobileRowCount.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
            }
        }

        // 检查客户池冲突（in_shared_pool=false）
        Set<String> privateConflictMobiles = new HashSet<>();
        if (CollectionUtils.isNotEmpty(mobileList)) {
            List<String> privateMobiles = customerMapper.getPrivatePoolMobiles(orgId, mobileList);
            privateConflictMobiles.addAll(privateMobiles);
        }

        // 检查其他公海池冲突
        Set<String> otherPoolConflictMobiles = new HashSet<>();
        if (CollectionUtils.isNotEmpty(mobileList)) {
            List<String> otherPoolMobiles = customerMapper.getOtherPoolMobiles(orgId, poolId, mobileList);
            otherPoolConflictMobiles.addAll(otherPoolMobiles);
        }

        // 手机号格式校验：不合法的手机号不参与重复/冲突检查
        Set<Integer> invalidMobileRows = new HashSet<>();
        String mobileFieldName = checkListener.getMobileFieldName();
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            String mobile = entry.getValue();
            if (StringUtils.isNotBlank(mobile) && !PoolCustomerCheckEventListener.MOBILE_PATTERN.matcher(mobile).matches()) {
                invalidMobileRows.add(entry.getKey());
            }
        }

        // 构建行级错误映射，优先级: FIELD_VALIDATION(手机格式) > EXCEL_DUPLICATE > PRIVATE_CONFLICT > OTHER_POOL_CONFLICT
        // 手机号唯一性问题由专门的重复/冲突检查处理，不应归为字段校验错误
        Map<Integer, String> rowErrorTypeMap = new LinkedHashMap<>();
        Map<Integer, String> rowErrorMsgMap = new LinkedHashMap<>();
        String cellNotUniqueKey = Translator.get("cell.not.unique");

        // 0. 手机号格式校验错误（红色）
        int mobileFormatErrorCount = 0;
        for (Integer rowNum : invalidMobileRows) {
            rowErrorTypeMap.put(rowNum, "FIELD_VALIDATION");
            rowErrorMsgMap.put(rowNum, mobileFieldName + Translator.get("phone.wrong.format"));
            mobileFormatErrorCount++;
        }

        // 1. Excel内手机号重复（黄色）- 手机号格式错误的行不参与重复检查
        int excelDuplicateCount = 0;
        for (Map.Entry<String, Set<Integer>> entry : mobileRowCount.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (Integer rowNum : entry.getValue()) {
                    if (!invalidMobileRows.contains(rowNum) && !rowErrorTypeMap.containsKey(rowNum)) {
                        rowErrorTypeMap.put(rowNum, "EXCEL_DUPLICATE");
                    }
                }
                excelDuplicateCount += entry.getValue().size();
            }
        }

        // 2. 客户池冲突（橙色）- 手机号格式错误的行不参与冲突检查
        int privateConflictCount = 0;
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            if (!invalidMobileRows.contains(entry.getKey())
                    && StringUtils.isNotBlank(entry.getValue()) && privateConflictMobiles.contains(entry.getValue())
                    && !rowErrorTypeMap.containsKey(entry.getKey())) {
                rowErrorTypeMap.put(entry.getKey(), "PRIVATE_CONFLICT");
                privateConflictCount++;
            }
        }

        // 3. 其他公海池冲突（蓝色）- 手机号格式错误的行不参与冲突检查
        int otherPoolConflictCount = 0;
        for (Map.Entry<Integer, String> entry : rowMobileMap.entrySet()) {
            if (!invalidMobileRows.contains(entry.getKey())
                    && StringUtils.isNotBlank(entry.getValue()) && otherPoolConflictMobiles.contains(entry.getValue())
                    && !rowErrorTypeMap.containsKey(entry.getKey())) {
                rowErrorTypeMap.put(entry.getKey(), "OTHER_POOL_CONFLICT");
                otherPoolConflictCount++;
            }
        }

        // 4. 字段校验错误（红色）- 仅对未被重复/冲突分类的行生效
        // 手机号唯一性错误由上面的重复/冲突检查覆盖，不计入字段校验
        int fieldValidationCount = mobileFormatErrorCount;
        for (ExcelErrData errData : checkListener.getErrList()) {
            if (!rowErrorTypeMap.containsKey(errData.getRowNum())) {
                rowErrorTypeMap.put(errData.getRowNum(), "FIELD_VALIDATION");
                fieldValidationCount++;
            }
        }

        boolean passed = rowErrorTypeMap.isEmpty();
        int errorCount = rowErrorTypeMap.size();
        int successCount = totalRows - errorCount;

        PoolCustomerImportCheckResponse response = PoolCustomerImportCheckResponse.builder()
                .passed(passed)
                .totalCount(totalRows)
                .successCount(successCount)
                .errorCount(errorCount)
                .build();

        if (!passed) {
            String errorFileId = IDGenerator.nextStr();
            String errorFileName = Translator.get("pool.import.error.file.name");
            boolean errorFileWritten = false;
            try {
                writeErrorExcel(file, rowErrorTypeMap, rowErrorMsgMap, checkListener.getErrList(), errorFileId, orgId);
                errorFileWritten = true;
            } catch (Exception e) {
                log.error("write error excel failed: {}", e.getMessage(), e);
            }
            if (errorFileWritten) {
                response.setErrorFileId(errorFileId);
                response.setErrorFileName(errorFileName);
            }
            response.setErrorSummary(PoolImportErrorSummary.builder()
                    .fieldValidationCount(fieldValidationCount)
                    .excelDuplicateCount(excelDuplicateCount)
                    .privateConflictCount(privateConflictCount)
                    .otherPoolConflictCount(otherPoolConflictCount)
                    .build());
        }

        return response;
    }

    /**
     * 写入错误Excel文件（修改背景色和批注）
     */
    private void writeErrorExcel(MultipartFile file, Map<Integer, String> rowErrorTypeMap,
                                  Map<Integer, String> rowErrorMsgMap,
                                  List<ExcelErrData> fieldErrors, String fileId, String orgId) throws IOException {
        String exportDirPath = DefaultRepositoryDir.getDefaultDir() + File.separator
                + DefaultRepositoryDir.getExportDir(orgId) + File.separator + fileId;
        File dir = new File(exportDirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("cannot create export dir: " + dir.getAbsolutePath());
        }
        String fileName = Translator.get("pool.import.error.file.name") + ".xlsx";
        File outputFile = new File(dir, fileName);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getSheetAt(0);

            Map<Integer, String> fieldErrorMap = new HashMap<>();
            for (ExcelErrData errData : fieldErrors) {
                fieldErrorMap.put(errData.getRowNum(), errData.getErrMsg());
            }

            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper creationHelper = workbook.getCreationHelper();

            for (Map.Entry<Integer, String> entry : rowErrorTypeMap.entrySet()) {
                int rowIndex = entry.getKey();
                if (rowIndex < 0 || rowIndex > sheet.getLastRowNum()) {
                    continue;
                }
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String errorType = entry.getValue();
                byte[] rgb = getColorRGB(errorType);
                String customMsg = rowErrorMsgMap.get(rowIndex);
                String fieldMsg = fieldErrorMap.get(rowIndex);
                String errorMsg = StringUtils.isNotBlank(customMsg) ? customMsg : getErrorMsg(errorType, fieldMsg);

                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null) {
                        cell = row.createCell(i);
                    }
                    CellStyle originalStyle = cell.getCellStyle();
                    CellStyle newStyle = workbook.createCellStyle();
                    newStyle.cloneStyleFrom(originalStyle);
                    if (workbook instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook) {
                        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) newStyle).setFillForegroundColor(
                                new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
                    }
                    newStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    cell.setCellStyle(newStyle);
                }

                if (StringUtils.isNotBlank(errorMsg)) {
                    Cell firstCell = row.getCell(0);
                    if (firstCell != null) {
                        try {
                            if (firstCell.getCellComment() != null) {
                                firstCell.removeCellComment();
                            }
                            int colIdx = firstCell.getColumnIndex();
                            Comment comment = drawing.createCellComment(
                                    new org.apache.poi.xssf.usermodel.XSSFClientAnchor(
                                            0, 0, 0, 0, colIdx, rowIndex, colIdx + 4, rowIndex + 2));
                            RichTextString richText = creationHelper.createRichTextString(errorMsg);
                            comment.setString(richText);
                            firstCell.setCellComment(comment);
                        } catch (IllegalArgumentException e) {
                            log.warn("skip comment for row {}: {}", rowIndex, e.getMessage());
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }
    }

    private byte[] getColorRGB(String errorType) {
        return switch (errorType) {
            case "FIELD_VALIDATION" -> new byte[]{(byte) 255, 0, 0};           // 红色
            case "EXCEL_DUPLICATE" -> new byte[]{(byte) 255, (byte) 255, 0};   // 黄色
            case "PRIVATE_CONFLICT" -> new byte[]{(byte) 255, (byte) 165, 0};  // 橙色
            case "OTHER_POOL_CONFLICT" -> new byte[]{(byte) 100, (byte) 149, (byte) 237}; // 蓝色
            default -> new byte[]{(byte) 255, 0, 0};
        };
    }

    private String getErrorMsg(String errorType, String fieldErrorMsg) {
        return switch (errorType) {
            case "FIELD_VALIDATION" -> StringUtils.isNotBlank(fieldErrorMsg) ? fieldErrorMsg : Translator.get("pool.import.field.validation");
            case "EXCEL_DUPLICATE" -> Translator.get("pool.import.excel.duplicate");
            case "PRIVATE_CONFLICT" -> Translator.get("pool.import.private.conflict");
            case "OTHER_POOL_CONFLICT" -> Translator.get("pool.import.other.pool.conflict");
            default -> "";
        };
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

            // 跳过所有字段的唯一性校验，公海导入中手机号重复由覆盖更新处理，其他字段不做唯一性校验
            for (BaseField field : filteredFields) {
                field.getRules().removeIf(rule -> "unique".equals(rule.getKey()));
            }

            CustomImportAfterDoConsumer<Customer, BaseResourceSubField> afterDo = (customers, customerFields, customerFieldBlobs) -> {
                // 设置公海客户特有属性
                customers.forEach(customer -> {
                    customer.setInSharedPool(true);
                    customer.setPoolId(poolId);
                    customer.setOwner(null);
                    customer.setCollectionTime(null);
                    customer.setStage(null);
                    customer.setStageStatus(null);
                });

                // 收集手机号用于重复检测
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

                // 更新自定义字段的resourceId映射（现有客户的ID）
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

                // 批量插入新客户及其自定义字段
                if (CollectionUtils.isNotEmpty(newCustomers)) {
                    customerBaseMapper.batchInsert(newCustomers);

                    // batchInsert不会插入null字段，需要补充设置公海特有属性（pool_id, in_shared_pool, owner, collection_time, stage, stage_status）
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

                // 创建联系人（仅新增客户）
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

                // 更新现有客户及其自定义字段
                if (CollectionUtils.isNotEmpty(updateCustomers)) {
                    List<String> updateCustomerIds = updateCustomers.stream().map(Customer::getId).collect(Collectors.toList());
                    for (Customer customer : updateCustomers) {
                        customerMapper.moveToPoolIncludeStage(customer);
                    }

                    // 删除旧的客户字段后重新插入
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