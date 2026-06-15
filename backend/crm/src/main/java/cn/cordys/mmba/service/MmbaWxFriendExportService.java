package cn.cordys.mmba.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.context.TenantContext;
import cn.cordys.mmba.MmbaIntegrationService;
import cn.cordys.mmba.dto.request.MmbaWxFriendExportRequest;
import cn.cordys.mmba.dto.response.MmbaWxFriendExportResponse;
import cn.cordys.mmba.excel.MmbaWxFriendExportRow;
import cn.idev.excel.EasyExcel;
import cn.idev.excel.ExcelWriter;
import cn.idev.excel.write.metadata.WriteSheet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员手工拉取 MMBA 微信好友列表并落盘为 Excel。
 */
@Slf4j
@Service
public class MmbaWxFriendExportService {

    private static final int WX_FRIEND_LIST_LIMIT = 100;
    private static final int DEFAULT_MAX_WX_ACCOUNTS = 1000;
    private static final int MAX_WX_ACCOUNTS = 3000;
    private static final int DEFAULT_MAX_PAGES_PER_WX = 80;
    private static final int MAX_PAGES_PER_WX = 200;
    private static final int DEFAULT_INTERVAL_MILLIS = 500;
    private static final int MIN_INTERVAL_MILLIS = 300;
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter ROW_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MmbaIntegrationService mmbaIntegrationService;

    public MmbaWxFriendExportResponse prepareExport(MmbaWxFriendExportRequest request) {
        String outputDir = StringUtils.trimToNull(request.getOutputDir());
        if (outputDir == null) {
            throw new GenericException("导出目录不能为空");
        }
        int maxWxAccounts = normalize(request.getMaxWxAccounts(), DEFAULT_MAX_WX_ACCOUNTS, 1, MAX_WX_ACCOUNTS);
        int maxPagesPerWx = normalize(request.getMaxPagesPerWx(), DEFAULT_MAX_PAGES_PER_WX, 1, MAX_PAGES_PER_WX);
        int intervalMillis = normalize(request.getIntervalMillis(), DEFAULT_INTERVAL_MILLIS, MIN_INTERVAL_MILLIS, Integer.MAX_VALUE);

        String taskId = IDGenerator.nextStr();
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new GenericException("创建导出目录失败: " + e.getMessage(), e);
        }

        String filePrefix = "mmba_wx_friend_" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + "_" + taskId;
        MmbaWxFriendExportResponse response = new MmbaWxFriendExportResponse();
        response.setTaskId(taskId);
        response.setStatus("RUNNING");
        response.setOutputFile(dir.resolve(filePrefix + ".xlsx").toString());
        response.setStatusFile(dir.resolve(filePrefix + ".status.json").toString());
        response.setSourceCsvFile(Path.of(request.getSourceCsvFile()).toAbsolutePath().normalize().toString());
        response.setFailureCsvFile(dir.resolve(filePrefix + "_failed.csv").toString());
        response.setMaxWxAccounts(maxWxAccounts);
        response.setMaxPagesPerWx(maxPagesPerWx);
        response.setIntervalMillis(intervalMillis);
        writeStatus(response.getStatusFile(), response, new ExportProgress(), null);
        return response;
    }

    @Async(ExecutorBeanNames.MAIN_ASYNC)
    public void exportAsync(MmbaWxFriendExportResponse task, String tenantId, String userId, String organizationId) {
        ExportProgress progress = new ExportProgress();
        progress.startedAt = System.currentTimeMillis();
        TenantContext.setTenantId(tenantId);
        writeStatus(task.getStatusFile(), task, progress, null);
        try (ExcelWriter writer = EasyExcel.write(new File(task.getOutputFile())).build()) {
            WriteSheet friendSheet = EasyExcel.writerSheet(0, "微信好友列表")
                    .head(MmbaWxFriendExportRow.class)
                    .build();
            writer.write(Collections.emptyList(), friendSheet);

            List<WxAccountSource> accounts = readAccountsFromCsv(task.getSourceCsvFile(), task.getMaxWxAccounts());
            writeFailureCsvHeader(task.getFailureCsvFile(), accounts);
            progress.totalAccounts = CollectionUtils.size(accounts);
            writeStatus(task.getStatusFile(), task, progress, null);
            if (CollectionUtils.isEmpty(accounts)) {
                progress.finishedAt = System.currentTimeMillis();
                writeStatus(task.getStatusFile(), task, progress, null);
                log.info("MMBA 微信好友导出结束，无可导出账号 taskId={} tenantId={} organizationId={} userId={}",
                        task.getTaskId(), tenantId, organizationId, userId);
                return;
            }

            for (WxAccountSource account : accounts) {
                progress.currentUm = account.getUm();
                progress.currentWxid = account.getWxid();
                exportAccount(writer, friendSheet, account, task, progress);
                progress.processedAccounts++;
                writeStatus(task.getStatusFile(), task, progress, null);
            }
            progress.finishedAt = System.currentTimeMillis();
            writeStatus(task.getStatusFile(), task, progress, null);
            log.info("MMBA 微信好友导出完成 taskId={} tenantId={} organizationId={} userId={} accounts={} rows={} failures={} file={}",
                    task.getTaskId(), tenantId, organizationId, userId, progress.processedAccounts,
                    progress.friendRows, progress.failedAccounts, task.getOutputFile());
        } catch (Exception e) {
            progress.finishedAt = System.currentTimeMillis();
            writeStatus(task.getStatusFile(), task, progress, e);
            log.error("MMBA 微信好友导出失败 taskId={} tenantId={} organizationId={} userId={} file={}",
                    task.getTaskId(), tenantId, organizationId, userId, task.getOutputFile(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private void exportAccount(ExcelWriter writer, WriteSheet friendSheet, WxAccountSource account,
                               MmbaWxFriendExportResponse task, ExportProgress progress)
            throws InterruptedException {
        String cursor = null;
        boolean success = false;
        for (int pageNo = 1; pageNo <= task.getMaxPagesPerWx(); pageNo++) {
            try {
                JsonNode response = queryFriendPage(account, cursor);
                List<MmbaWxFriendExportRow> rows = buildFriendRows(account, response);
                if (CollectionUtils.isNotEmpty(rows)) {
                    writer.write(rows, friendSheet);
                    progress.friendRows += rows.size();
                }
                success = true;
                progress.requestCount++;
                cursor = readCursor(response);
                pause(task.getIntervalMillis());
                if (StringUtils.isBlank(cursor)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                appendFailureCsvRow(task.getFailureCsvFile(), account);
                progress.failedAccounts++;
                progress.requestCount++;
                log.warn("MMBA 微信好友导出账号查询失败 taskId={} um={} wxid={} cursor={} message={}",
                        task.getTaskId(), account.getUm(), account.getWxid(), cursor, e.getMessage(), e);
                pause(task.getIntervalMillis());
                return;
            }
        }
        if (success) {
            progress.successAccounts++;
        }
    }

    private JsonNode queryFriendPage(WxAccountSource account, String cursor) {
        ObjectNode request = JSON.MAPPER.createObjectNode();
        request.put("limit", WX_FRIEND_LIST_LIMIT);
        request.put("reqId", IDGenerator.nextStr());
        if (StringUtils.isBlank(cursor)) {
            request.put("um", account.getUm());
            request.put("wxid", account.getWxid());
        } else {
            request.put("cursor", cursor);
        }
        return mmbaIntegrationService.queryWxFriendList(request);
    }

    private List<MmbaWxFriendExportRow> buildFriendRows(WxAccountSource account, JsonNode response) {
        JsonNode data = response.path("data");
        JsonNode friends = data.path("friends");
        if (!friends.isArray() || friends.isEmpty()) {
            return Collections.emptyList();
        }
        String exportTime = LocalDateTime.now().format(ROW_TIME_FORMATTER);
        List<MmbaWxFriendExportRow> rows = new ArrayList<>(friends.size());
        for (JsonNode friend : friends) {
            MmbaWxFriendExportRow row = new MmbaWxFriendExportRow();
            row.setUm(account.getUm());
            row.setStaffName(account.getStaffName());
            row.setDepartment(account.getDepartment());
            row.setDeviceName(account.getDeviceName());
            row.setWxid(account.getWxid());
            row.setWxAccount(account.getWxAccount());
            row.setWxPhone(account.getWxPhone());
            row.setWxNickName(account.getWxNickName());
            row.setWxLoginStatus(account.getLoginStatus());
            row.setSourceUpdateTime(account.getUpdateTime());
            row.setDeviceId(text(data, "deviceId"));
            row.setLoginStatus(text(data, "loginStatus"));
            row.setTotal(data.path("total").isMissingNode() ? null : data.path("total").asInt());
            row.setContactImAppAccount(text(friend, "contactImAppAccount"));
            row.setContactImIdInApp(text(friend, "contactImIdInApp"));
            row.setContactImAppNickName(text(friend, "contactImAppNickName"));
            row.setContactImAppNote(text(friend, "contactImAppNote"));
            row.setContactMobile(text(friend, "contactMobile"));
            row.setFriendPhone(text(friend, "friendPhone"));
            row.setFriendSearch(text(friend, "friendSearch"));
            row.setContactArea(text(friend, "contactArea"));
            row.setContactSex(text(friend, "contactSex"));
            row.setContactImAppHeaderPic(text(friend, "contactImAppHeaderPic"));
            row.setContactDescription(text(friend, "contactDescription"));
            row.setExportTime(exportTime);
            rows.add(row);
        }
        return rows;
    }

    private List<WxAccountSource> readAccountsFromCsv(String sourceCsvFile, int maxWxAccounts) throws IOException {
        Path csvPath = Path.of(sourceCsvFile).toAbsolutePath().normalize();
        if (!Files.exists(csvPath) || !Files.isRegularFile(csvPath)) {
            throw new GenericException("来源CSV文件不存在: " + csvPath);
        }
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        String headerLine = removeBom(lines.get(0));
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> headerIndex = buildHeaderIndex(headers);
        requireColumn(headerIndex, "用户名");
        requireColumn(headerIndex, "imId");

        List<WxAccountSource> accounts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < lines.size() && accounts.size() < maxWxAccounts; i++) {
            if (StringUtils.isBlank(lines.get(i))) {
                continue;
            }
            List<String> values = parseCsvLine(lines.get(i));
            String um = value(values, headerIndex, "用户名");
            String wxid = value(values, headerIndex, "imId");
            if (StringUtils.isBlank(um) || StringUtils.isBlank(wxid)) {
                continue;
            }
            String key = um + "\n" + wxid;
            if (!seen.add(key)) {
                continue;
            }
            WxAccountSource account = new WxAccountSource();
            account.setCsvHeaderLine(headerLine);
            account.setRawCsvLine(lines.get(i));
            account.setStaffName(value(values, headerIndex, "姓名"));
            account.setUm(um);
            account.setDepartment(value(values, headerIndex, "部门"));
            account.setDeviceName(value(values, headerIndex, "设备名"));
            account.setWxid(wxid);
            account.setWxAccount(value(values, headerIndex, "im账号"));
            account.setWxNickName(value(values, headerIndex, "昵称"));
            account.setWxPhone(value(values, headerIndex, "手机号"));
            account.setLoginStatus(value(values, headerIndex, "微信登录状态"));
            account.setUpdateTime(value(values, headerIndex, "更新时间"));
            accounts.add(account);
        }
        return accounts;
    }

    private void writeFailureCsvHeader(String failureCsvFile, List<WxAccountSource> accounts) throws IOException {
        String headerLine = CollectionUtils.isEmpty(accounts) ? null : accounts.get(0).getCsvHeaderLine();
        if (StringUtils.isBlank(headerLine)) {
            headerLine = "姓名,用户名,部门,设备名,imId,im账号,昵称,性别,实名认证状态,手机号,微信登录状态,更新时间";
        }
        Files.writeString(Path.of(failureCsvFile), headerLine + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private void appendFailureCsvRow(String failureCsvFile, WxAccountSource account) {
        try {
            Files.writeString(Path.of(failureCsvFile), account.getRawCsvLine() + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("写入 MMBA 微信好友导出失败CSV失败 file={} um={} wxid={} message={}",
                    failureCsvFile, account.getUm(), account.getWxid(), e.getMessage(), e);
        }
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(StringUtils.trimToEmpty(headers.get(i)), i);
        }
        return index;
    }

    private void requireColumn(Map<String, Integer> headerIndex, String columnName) {
        if (!headerIndex.containsKey(columnName)) {
            throw new GenericException("来源CSV文件缺少列: " + columnName);
        }
    }

    private String value(List<String> values, Map<String, Integer> headerIndex, String columnName) {
        Integer index = headerIndex.get(columnName);
        if (index == null || index >= values.size()) {
            return null;
        }
        return StringUtils.trimToNull(values.get(index));
    }

    private String removeBom(String line) {
        if (StringUtils.isNotEmpty(line) && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String readCursor(JsonNode response) {
        String cursor = text(response, "cursor");
        if (StringUtils.isNotBlank(cursor)) {
            return cursor;
        }
        return text(response.path("data"), "cursor");
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.isBlank(text) ? null : text;
    }

    private void pause(int intervalMillis) throws InterruptedException {
        if (intervalMillis > 0) {
            Thread.sleep(intervalMillis);
        }
    }

    private int normalize(Integer value, int defaultValue, int min, int max) {
        int result = value == null ? defaultValue : value;
        if (result < min) {
            return min;
        }
        return Math.min(result, max);
    }

    private void writeStatus(String statusFile, MmbaWxFriendExportResponse task, ExportProgress progress, Exception error) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("taskId", task.getTaskId());
        String statusText = error != null ? "FAILED" : progress.finishedAt == null ? "RUNNING" : "SUCCESS";
        status.put("status", statusText);
        status.put("outputFile", task.getOutputFile());
        status.put("sourceCsvFile", task.getSourceCsvFile());
        status.put("failureCsvFile", task.getFailureCsvFile());
        status.put("maxWxAccounts", task.getMaxWxAccounts());
        status.put("maxPagesPerWx", task.getMaxPagesPerWx());
        status.put("intervalMillis", task.getIntervalMillis());
        status.put("totalAccounts", progress.totalAccounts);
        status.put("processedAccounts", progress.processedAccounts);
        status.put("successAccounts", progress.successAccounts);
        status.put("failedAccounts", progress.failedAccounts);
        status.put("friendRows", progress.friendRows);
        status.put("requestCount", progress.requestCount);
        status.put("currentUm", progress.currentUm);
        status.put("currentWxid", progress.currentWxid);
        status.put("startedAt", progress.startedAt);
        status.put("finishedAt", progress.finishedAt);
        if (error != null) {
            status.put("error", error.getMessage());
        }
        try {
            Files.writeString(Path.of(statusFile), JSON.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(status));
        } catch (IOException e) {
            log.warn("写入 MMBA 微信好友导出状态文件失败 statusFile={} message={}", statusFile, e.getMessage(), e);
        }
    }

    private static class ExportProgress {
        private int totalAccounts;
        private int processedAccounts;
        private int successAccounts;
        private int failedAccounts;
        private int friendRows;
        private int requestCount;
        private String currentUm;
        private String currentWxid;
        private Long startedAt;
        private Long finishedAt;
    }

    private static class WxAccountSource {
        private String csvHeaderLine;
        private String rawCsvLine;
        private String staffName;
        private String um;
        private String department;
        private String deviceName;
        private String wxid;
        private String wxAccount;
        private String wxPhone;
        private String wxNickName;
        private String loginStatus;
        private String updateTime;

        public String getCsvHeaderLine() {
            return csvHeaderLine;
        }

        public void setCsvHeaderLine(String csvHeaderLine) {
            this.csvHeaderLine = csvHeaderLine;
        }

        public String getRawCsvLine() {
            return rawCsvLine;
        }

        public void setRawCsvLine(String rawCsvLine) {
            this.rawCsvLine = rawCsvLine;
        }

        public String getStaffName() {
            return staffName;
        }

        public void setStaffName(String staffName) {
            this.staffName = staffName;
        }

        public String getUm() {
            return um;
        }

        public void setUm(String um) {
            this.um = um;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public String getWxid() {
            return wxid;
        }

        public void setWxid(String wxid) {
            this.wxid = wxid;
        }

        public String getWxAccount() {
            return wxAccount;
        }

        public void setWxAccount(String wxAccount) {
            this.wxAccount = wxAccount;
        }

        public String getWxPhone() {
            return wxPhone;
        }

        public void setWxPhone(String wxPhone) {
            this.wxPhone = wxPhone;
        }

        public String getWxNickName() {
            return wxNickName;
        }

        public void setWxNickName(String wxNickName) {
            this.wxNickName = wxNickName;
        }

        public String getLoginStatus() {
            return loginStatus;
        }

        public void setLoginStatus(String loginStatus) {
            this.loginStatus = loginStatus;
        }

        public String getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(String updateTime) {
            this.updateTime = updateTime;
        }
    }
}
