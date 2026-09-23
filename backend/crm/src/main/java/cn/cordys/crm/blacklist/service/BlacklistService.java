package cn.cordys.crm.blacklist.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.PhoneMaskUtil;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.system.service.GlobalPhoneMaskConfigService;
import cn.cordys.file.engine.DefaultRepositoryDir;
import cn.cordys.file.engine.FileSourceModule;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.blacklist.domain.Blacklist;
import cn.cordys.crm.blacklist.mapper.BlacklistMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

@Service
public class BlacklistService {
    private static final int MAX_ROWS = 10000;
    @Resource private BlacklistMapper mapper;
    @Resource private BlacklistCheckService check;
    @Resource private BlacklistAuditService audit;
    @Resource private GlobalPhoneMaskConfigService globalPhoneMaskConfigService;

    public Map<String, Object> page(String keyword, int current, int size) {
        TenantContext.requireTenantId();
        if (current < 1 || current > 1000000 || size < 1 || size > 200) {
            throw new GenericException("分页参数不正确");
        }
        String value = StringUtils.trimToEmpty(keyword);
        List<Blacklist> rows = mapper.page(value, (current - 1) * size, size);
        if (globalPhoneMaskConfigService.isEnabled(OrganizationContext.getOrganizationId())) {
            rows.forEach(row -> row.setMobile(PhoneMaskUtil.maskGlobalPhone(row.getMobile())));
        }
        return Map.of("list", rows, "total", mapper.count(value));
    }

    public Map<String, Object> add(String userId, String mobile, String name) {
        String phone = StringUtils.trimToEmpty(mobile);
        String customerName = StringUtils.trimToEmpty(name);
        validate(phone, customerName);
        return execute(userId, "ADD", "新建", () -> {
            Blacklist row = newRow(phone, customerName, userId);
            mapper.upsert(row);
            Blacklist saved = mapper.find(List.of(phone)).getFirst();
            boolean exists = !row.getId().equals(saved.getId());
            Map<String, Object> result = counts(1, exists ? 0 : 1, exists ? 1 : 0, 0);
            result.put("mobile", phone);
            result.put("resourceId", saved.getId());
            return new Outcome(result, null);
        });
    }

    public Map<String, Object> delete(String userId, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_ROWS || ids.stream().anyMatch(StringUtils::isBlank)) {
            throw new GenericException("请选择要删除的数据，单次最多删除10000条");
        }
        return deleteSelected(userId, () -> ids.stream().distinct().sorted().toList());
    }

    public Map<String, Object> deleteByCondition(String userId, String keyword) {
        return deleteSelected(userId, () -> mapper.findIdsByKeyword(StringUtils.trimToEmpty(keyword)));
    }

    private Map<String, Object> deleteSelected(String userId, Supplier<List<String>> selection) {
        return execute(userId, "DELETE", "删除", () -> {
            List<String> selected = selection.get();
            List<Blacklist> original = new ArrayList<>();
            int count = 0;
            for (int i = 0; i < selected.size(); i += 500) {
                List<String> batch = selected.subList(i, Math.min(i + 500, selected.size()));
                original.addAll(mapper.findIds(batch));
                count += mapper.delete(batch);
            }
            Map<String, Object> result = counts(count, 0, 0, 0);
            result.put("deletedRecords", original);
            return new Outcome(result, null);
        });
    }

    public Map<String, Object> importFile(String userId, MultipartFile file) {
        TenantContext.requireTenantId();
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new GenericException("请选择不超过10MB的Excel文件");
        }
        // 文件解析在数据库事务外完成。有效重复行保持最后一条，不对重复作错误提示。
        Map<Integer, String> errors = new LinkedHashMap<>();
        Map<String, String> valid = new LinkedHashMap<>();
        int total = 0;
        int validRows = 0;
        String errorFileName;
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new GenericException("文件缺少数据工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            errorFileName = workbook instanceof org.apache.poi.hssf.usermodel.HSSFWorkbook
                    ? "黑名单导入错误.xls" : "黑名单导入错误.xlsx";
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            if (header == null || !"手机号码".equals(cell(header, 0, formatter))
                    || !"客户姓名".equals(cell(header, 1, formatter))) {
                throw new GenericException("模板列不正确，请下载黑名单导入模板");
            }
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw new GenericException("单次最多导入10000行数据");
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String phone = cell(row, 0, formatter);
                String name = cell(row, 1, formatter);
                if (phone.isEmpty() && name.isEmpty()) continue;
                total++;
                try {
                    if ((row.getCell(0) != null && row.getCell(0).getCellType() == CellType.FORMULA)
                            || (row.getCell(1) != null && row.getCell(1).getCellType() == CellType.FORMULA)) {
                        throw new GenericException("不支持公式，请粘贴为文本值");
                    }
                    validate(phone, name);
                    valid.put(phone, name);
                    validRows++;
                } catch (GenericException e) {
                    errors.put(i, e.getMessage());
                }
            }
        } catch (GenericException e) {
            throw e;
        } catch (Exception e) {
            throw new GenericException("无法读取Excel文件，请使用提供的模板");
        }
        final int totalRows = total;
        final int duplicateRows = validRows - valid.size();
        byte[] errorFile = errors.isEmpty() ? null : errorExcel(file, errors);
        return execute(userId, "IMPORT", "导入", () -> {
            List<Blacklist> rows = valid.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> newRow(entry.getKey(), entry.getValue(), userId)).toList();
            int inserted = 0;
            for (int i = 0; i < rows.size(); i += 500) {
                List<Blacklist> batch = rows.subList(i, Math.min(i + 500, rows.size()));
                mapper.upsertRows(batch);
                Set<String> newIds = new HashSet<>(batch.stream().map(Blacklist::getId).toList());
                inserted += (int) mapper.find(batch.stream().map(Blacklist::getMobile).toList()).stream()
                        .filter(row -> newIds.contains(row.getId())).count();
            }
            Map<String, Object> result = counts(valid.size(), inserted, valid.size() - inserted, errors.size());
            result.put("fileName", StringUtils.defaultIfBlank(file.getOriginalFilename(), "黑名单导入.xlsx"));
            result.put("totalRows", totalRows);
            result.put("duplicateRows", duplicateRows);
            if (errorFile != null) {
                String fileId = IDGenerator.nextStr();
                try {
                    Path path = errorFilePath(userId, fileId);
                    Files.createDirectories(path.getParent());
                    Files.write(path, errorFile);
                } catch (java.io.IOException e) {
                    throw new GenericException("错误文件保存失败，请重试");
                }
                result.put("errorFileId", fileId);
                result.put("errorFileName", errorFileName);
            }
            return new Outcome(result, null);
        });
    }

    public byte[] exportFile(String userId, String keyword, List<String> ids) {
        Map<String, Object> exported = execute(userId, "EXPORT", "导出", () -> {
            List<Blacklist> rows = new ArrayList<>();
            if (ids != null && !ids.isEmpty()) {
                if (ids.size() > MAX_ROWS || ids.stream().anyMatch(StringUtils::isBlank)) {
                    throw new GenericException("单次最多导出10000条");
                }
                List<String> selected = ids.stream().distinct().sorted().toList();
                for (int i = 0; i < selected.size(); i += 500) {
                    rows.addAll(mapper.findIds(selected.subList(i, Math.min(i + 500, selected.size()))));
                }
            } else {
                String value = StringUtils.trimToEmpty(keyword);
                if (mapper.count(value) > MAX_ROWS) {
                    throw new GenericException("单次最多导出10000条，请缩小筛选范围");
                }
                rows.addAll(mapper.page(value, 0, MAX_ROWS));
            }
            List<List<String>> data = rows.stream()
                    .map(row -> List.of(row.getMobile(), StringUtils.defaultString(row.getCustomerName()))).toList();
            return new Outcome(counts(rows.size(), 0, 0, 0), excel(List.of("手机号码", "客户姓名"), data));
        });
        return (byte[]) exported.get("file");
    }

    /** 与客户、公海导入一致：保留原工作簿，错误整行标红，首列批注原因。 */
    private byte[] errorExcel(MultipartFile file, Map<Integer, String> errors) {
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            int columnCount = sheet.getRow(0).getLastCellNum();
            Map<Short, CellStyle> styles = new HashMap<>();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper helper = workbook.getCreationHelper();
            for (Map.Entry<Integer, String> error : errors.entrySet()) {
                Row row = sheet.getRow(error.getKey());
                if (row == null) continue;
                for (int column = 0; column < columnCount; column++) {
                    Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    CellStyle base = cell.getCellStyle();
                    CellStyle marked = styles.computeIfAbsent(base.getIndex(), key -> {
                        CellStyle style = workbook.createCellStyle();
                        style.cloneStyleFrom(base);
                        style.setFillForegroundColor(IndexedColors.RED.getIndex());
                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        return style;
                    });
                    cell.setCellStyle(marked);
                }
                Cell firstCell = row.getCell(0);
                firstCell.removeCellComment();
                Comment comment = drawing.createCellComment(helper.createClientAnchor());
                comment.setString(helper.createRichTextString(error.getValue()));
                comment.setAuthor("XCRM");
                firstCell.setCellComment(comment);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new GenericException("导入错误文件生成失败，请重试");
        }
    }
    private Path errorFilePath(String userId, String fileId) {
        if (fileId == null || !fileId.matches("[A-Za-z0-9_-]{1,50}")) {
            throw new GenericException("文件标识无效");
        }
        String owner = Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes(StandardCharsets.UTF_8));
        return Path.of(DefaultRepositoryDir.getDefaultDir(),
                DefaultRepositoryDir.getTempFileDir(TenantContext.requireTenantId(), FileSourceModule.CUSTOMER),
                "blacklist", owner, fileId, "import-error");
    }

    public byte[] importErrorFile(String userId, String fileId) {
        try {
            return Files.readAllBytes(errorFilePath(userId, fileId));
        } catch (java.io.IOException e) {
            throw new GenericException("错误文件不存在或已清理，请重新导入");
        }
    }
    public byte[] template() {
        return excel(List.of("手机号码", "客户姓名"), List.of());
    }

    private Map<String, Object> execute(String userId, String type, String verb, Supplier<Outcome> work) {
        return check.write(() -> {
            Outcome outcome = work.get();
            Map<String, Object> result = outcome.result();
            audit.record(IDGenerator.nextStr(), userId, type, verb, result);
            Map<String, Object> response = new LinkedHashMap<>(result);
            response.remove("deletedRecords");
            response.remove("resourceId");
            response.remove("fileName");
            // 导出文件仅用于当前响应，不写入日志或数据库。
            if (outcome.file() != null) response.put("file", outcome.file());
            return response;
        });
    }
    private Blacklist newRow(String mobile, String name, String userId) {
        Blacklist row = new Blacklist();
        row.setId(IDGenerator.nextStr());
        row.setMobile(mobile);
        row.setCustomerName(StringUtils.trimToNull(name));
        row.setCreateTime(System.currentTimeMillis());
        row.setUpdateTime(row.getCreateTime());
        row.setCreateUser(userId);
        row.setUpdateUser(userId);
        return row;
    }

    private void validate(String mobile, String name) {
        if (!mobile.matches("^1[0-9]\\d{9}$")) {
            throw new GenericException("手机号码必填，且必须为11位有效手机号");
        }
        if (name.length() > 255) throw new GenericException("客户姓名不能超过255个字符");
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        return StringUtils.trimToEmpty(formatter.formatCellValue(row.getCell(index)));
    }

    private Map<String, Object> counts(int success, int inserted, int overwritten, int failed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", success);
        result.put("insertCount", inserted);
        result.put("overwriteCount", overwritten);
        result.put("failCount", failed);
        return result;
    }

    private byte[] excel(List<String> headers, List<List<String>> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("数据");
            Row header = sheet.createRow(0);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int col = 0; col < headers.size(); col++) {
                header.createCell(col).setCellValue(headers.get(col));
                sheet.setColumnWidth(col, (col == 3 ? 50 : 24) * 256);
                sheet.setDefaultColumnStyle(col, text);
            }
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                for (int col = 0; col < data.get(i).size(); col++) {
                    row.createCell(col, CellType.STRING).setCellValue(data.get(i).get(col));
                }
            }
            sheet.createFreezePane(0, 1);
            Sheet help = workbook.createSheet("说明");
            help.createRow(0).createCell(0).setCellValue("手机号码必填（11位），客户姓名非必填。重复号码静默覆盖，空姓名会清空原姓名。");
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new GenericException("Excel文件生成失败");
        }
    }

    private record Outcome(Map<String, Object> result, byte[] file) { }
}
