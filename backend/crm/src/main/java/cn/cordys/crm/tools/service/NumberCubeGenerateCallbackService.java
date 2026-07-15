package cn.cordys.crm.tools.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.tools.constants.NumberCubeTaskStatus;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.NumberCubeJobManifest;
import cn.cordys.crm.tools.dto.request.NumberCubeGenerateCallbackRequest;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskProgressResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.platform.util.NumberCubePathUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class NumberCubeGenerateCallbackService {

    private final Map<String, String> callbackTokens = new ConcurrentHashMap<>();

    @Resource
    private BaseMapper<NumberCubeTask> numberCubeTaskMapper;
    @Resource
    private NumberCubeGenerateConcurrencyManager numberCubeGenerateConcurrencyManager;
    @Resource
    private NumberCubeTaskNotifyService numberCubeTaskNotifyService;

    public void registerCallbackToken(String jobId, String token) {
        if (StringUtils.isNotBlank(jobId) && StringUtils.isNotBlank(token)) {
            callbackTokens.put(jobId, token);
        }
    }

    public void handleCallback(NumberCubeGenerateCallbackRequest request, String remoteAddr) {
        validateLocalCallback(remoteAddr);
        if (request == null || StringUtils.isAnyBlank(request.getJobId(), request.getTenantId(), request.getToken())) {
            throw new GenericException(Translator.get("number_cube_callback_invalid"));
        }
        String expectedToken = callbackTokens.get(request.getJobId());
        if (expectedToken == null) {
            expectedToken = readTokenFromManifest(request.getJobId());
        }
        if (!StringUtils.equals(expectedToken, request.getToken())) {
            throw new GenericException(Translator.get("number_cube_callback_invalid"));
        }

        String previousTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(request.getTenantId());
            NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(request.getJobId());
            if (task == null) {
                throw new GenericException(Translator.get("number_cube_task_not_found"));
            }
            if (NumberCubeTaskStatus.SUCCESS.equals(task.getStatus()) || NumberCubeTaskStatus.FAILED.equals(task.getStatus())) {
                callbackTokens.remove(request.getJobId());
                return;
            }

            applyTerminalStatus(task, request.getStatus(), request.getErrorMessage());
            callbackTokens.remove(request.getJobId());
            numberCubeGenerateConcurrencyManager.releaseGenerateSlot();
            NumberCubeTaskProgressResponse progress = buildProgressFromStatusFile(request.getJobId(), task.getStatus());
            numberCubeTaskNotifyService.publishTaskUpdated(task, progress);
            log.info("number cube generate callback jobId={} status={} generated={} skipped={} failed={}",
                    request.getJobId(), request.getStatus(), request.getGenerated(), request.getSkipped(), request.getFailed());
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    public NumberCubeTaskProgressResponse getProgress(String taskId, String organizationId) {
        NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);
        if (task == null || !StringUtils.equals(organizationId, task.getOrganizationId())) {
            throw new GenericException(Translator.get("number_cube_task_not_found"));
        }

        NumberCubeTaskProgressResponse response = new NumberCubeTaskProgressResponse();
        response.setJobId(taskId);
        response.setStatus(task.getStatus());

        Path statusPath = NumberCubePathUtils.getJobStatusPath(taskId);
        if (!Files.exists(statusPath)) {
            return response;
        }

        Map<String, Object> statusMap;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JSON.parseObject(Files.readString(statusPath, StandardCharsets.UTF_8), Map.class);
            statusMap = parsed;
        } catch (IOException e) {
            log.warn("read number cube job status failed jobId={}", taskId, e);
            return response;
        }
        if (statusMap == null) {
            return response;
        }

        response.setTotal(asInt(statusMap.get("total")));
        response.setProcessed(asInt(statusMap.get("processed")));
        response.setGenerated(asInt(statusMap.get("generated")));
        response.setSkipped(asInt(statusMap.get("skipped")));
        response.setFailed(asInt(statusMap.get("failed")));

        String fileStatus = statusMap.get("status") == null ? null : String.valueOf(statusMap.get("status"));
        boolean dbOpen = NumberCubeTaskStatus.PENDING.equals(task.getStatus())
                || NumberCubeTaskStatus.RUNNING.equals(task.getStatus());
        boolean fileTerminal = NumberCubeTaskStatus.SUCCESS.equalsIgnoreCase(fileStatus)
                || NumberCubeTaskStatus.FAILED.equalsIgnoreCase(fileStatus);

        if (dbOpen && fileTerminal) {
            String errorMessage = statusMap.get("errorMessage") == null ? null : String.valueOf(statusMap.get("errorMessage"));
            applyTerminalStatus(task, fileStatus, errorMessage);
            numberCubeGenerateConcurrencyManager.releaseGenerateSlot();
            numberCubeTaskNotifyService.publishTaskUpdated(task,
                    buildProgressFromStatusMap(taskId, task.getStatus(), statusMap));
            log.info("number cube generate reconciled from status file jobId={} status={}", taskId, task.getStatus());
            response.setStatus(task.getStatus());
        } else if (fileStatus != null) {
            response.setStatus(fileStatus);
        }
        return response;
    }

    public List<NumberCubeTaskProgressResponse> getProgressBatch(List<String> taskIds, String organizationId) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return taskIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .map(taskId -> getProgress(taskId.trim(), organizationId))
                .toList();
    }

    private void applyTerminalStatus(NumberCubeTask task, String status, String errorMessage) {
        long now = System.currentTimeMillis();
        if (NumberCubeTaskStatus.SUCCESS.equalsIgnoreCase(status)) {
            task.setStatus(NumberCubeTaskStatus.SUCCESS);
            task.setErrorMessage(null);
        } else {
            task.setStatus(NumberCubeTaskStatus.FAILED);
            task.setErrorMessage(StringUtils.abbreviate(StringUtils.defaultString(errorMessage), 1000));
        }
        task.setUpdateTime(now);
        numberCubeTaskMapper.update(task);
    }

    private NumberCubeTaskProgressResponse buildProgressFromStatusFile(String taskId, String status) {
        NumberCubeTaskProgressResponse response = new NumberCubeTaskProgressResponse();
        response.setJobId(taskId);
        response.setStatus(status);
        Path statusPath = NumberCubePathUtils.getJobStatusPath(taskId);
        if (!Files.exists(statusPath)) {
            return response;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> statusMap = JSON.parseObject(Files.readString(statusPath, StandardCharsets.UTF_8), Map.class);
            return buildProgressFromStatusMap(taskId, status, statusMap);
        } catch (IOException e) {
            log.warn("read number cube job status failed jobId={}", taskId, e);
            return response;
        }
    }

    private NumberCubeTaskProgressResponse buildProgressFromStatusMap(String taskId, String status, Map<String, Object> statusMap) {
        NumberCubeTaskProgressResponse response = new NumberCubeTaskProgressResponse();
        response.setJobId(taskId);
        response.setStatus(status);
        if (statusMap == null) {
            return response;
        }
        response.setTotal(asInt(statusMap.get("total")));
        response.setProcessed(asInt(statusMap.get("processed")));
        response.setGenerated(asInt(statusMap.get("generated")));
        response.setSkipped(asInt(statusMap.get("skipped")));
        response.setFailed(asInt(statusMap.get("failed")));
        return response;
    }

    private String readTokenFromManifest(String jobId) {
        Path manifestPath = NumberCubePathUtils.getJobManifestPath(jobId);
        if (!Files.exists(manifestPath)) {
            return null;
        }
        try {
            NumberCubeJobManifest manifest = JSON.parseObject(
                    Files.readString(manifestPath, StandardCharsets.UTF_8), NumberCubeJobManifest.class);
            return manifest == null ? null : manifest.getCallbackToken();
        } catch (IOException e) {
            return null;
        }
    }

    private void validateLocalCallback(String remoteAddr) {
        // localhost validation handled at controller layer via properties if needed
    }

    private Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
