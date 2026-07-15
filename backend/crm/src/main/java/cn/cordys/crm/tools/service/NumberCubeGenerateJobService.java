package cn.cordys.crm.tools.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.tools.config.NumberCubeProperties;
import cn.cordys.crm.tools.constants.NumberCubeTaskStatus;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.NumberCubeJobManifest;
import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.platform.util.NumberCubePathUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class NumberCubeGenerateJobService {

    @Resource
    private NumberCubeProperties numberCubeProperties;
    @Resource
    private BaseMapper<NumberCubeTask> numberCubeTaskMapper;
    @Resource
    private NumberCubeTaskSegmentService numberCubeTaskSegmentService;
    @Resource
    private NumberCubeGenerateCallbackService numberCubeGenerateCallbackService;
    @Resource
    private NumberCubeGenerateConcurrencyManager numberCubeGenerateConcurrencyManager;
    @Resource
    private NumberCubeTaskNotifyService numberCubeTaskNotifyService;
    @Resource
    @Qualifier(ExecutorBeanNames.NUMBER_CUBE_GENERATE)
    private Executor numberCubeGenerateExecutor;

    public void submit(NumberCubeTask task) {
        String tenantId = TenantContext.getTenantId();
        log.info("number cube generate job queued taskId={}, tenantId={}", task.getId(), tenantId);
        numberCubeGenerateExecutor.execute(() -> {
            if (StringUtils.isNotBlank(tenantId)) {
                TenantContext.setTenantId(tenantId);
            }
            try {
                runJob(task.getId(), tenantId);
            } finally {
                TenantContext.clear();
            }
        });
    }

    private void runJob(String taskId, String tenantId) {
        NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);
        if (task == null) {
            log.warn("number cube generate job skipped, task not found taskId={}, tenantId={}", taskId, tenantId);
            return;
        }
        if (!NumberCubeTaskStatus.PENDING.equals(task.getStatus())) {
            log.warn("number cube generate job skipped, unexpected status taskId={}, status={}", taskId, task.getStatus());
            return;
        }
        log.info("number cube generate job running taskId={}, tenantId={}", taskId, tenantId);

        List<String> segments;
        try {
            segments = numberCubeTaskSegmentService.resolveSegments(task);
        } catch (Exception e) {
            markFailed(task, Translator.get("number_cube_segment_invalid"));
            return;
        }
        if (segments == null || segments.isEmpty()) {
            markFailed(task, Translator.get("number_cube_segment_required"));
            return;
        }
        if (segments.size() > numberCubeProperties.getMaxSegmentsPerTask()) {
            markFailed(task, Translator.get("number_cube_segment_limit_exceeded"));
            return;
        }

        boolean slotAcquired = false;
        try {
            numberCubeGenerateConcurrencyManager.acquireGenerateSlot();
            slotAcquired = true;
            String callbackToken = IDGenerator.nextStr();
            numberCubeTaskSegmentService.writeSegmentsFile(task.getId(), segments);
            NumberCubeJobManifest manifest = buildManifest(task, tenantId, segments.size(), callbackToken);
            writeManifest(task.getId(), manifest);
            numberCubeGenerateCallbackService.registerCallbackToken(task.getId(), callbackToken);
            writeInitialStatus(task.getId(), segments.size());
            spawnPythonProcess(task.getId());
            updateRunning(task);
        } catch (GenericException e) {
            if (slotAcquired) {
                numberCubeGenerateConcurrencyManager.releaseGenerateSlot();
            }
            markFailed(task, e.getMessage());
        } catch (Exception e) {
            if (slotAcquired) {
                numberCubeGenerateConcurrencyManager.releaseGenerateSlot();
            }
            log.error("number cube generate job failed to start taskId={}", taskId, e);
            markFailed(task, StringUtils.abbreviate(e.getMessage(), 1000));
        }
    }

    private NumberCubeJobManifest buildManifest(NumberCubeTask task, String tenantId, int segmentCount, String callbackToken) {
        NumberCubeJobManifest manifest = new NumberCubeJobManifest();
        manifest.setJobId(task.getId());
        manifest.setTenantId(tenantId);
        manifest.setOrganizationId(task.getOrganizationId());
        manifest.setProvince(task.getProvince());
        manifest.setCity(task.getCity());
        manifest.setSegmentsFile(NumberCubePathUtils.getJobSegmentsPath(task.getId()).toString());
        manifest.setSegmentCount(segmentCount);
        manifest.setCubeRoot(NumberCubePathUtils.getMasterCubeRoot().toString());
        manifest.setJobsDir(NumberCubePathUtils.getJobsDir().toString());
        manifest.setCallbackUrl(buildCallbackUrl());
        manifest.setCallbackToken(callbackToken);
        manifest.setWorkers(numberCubeProperties.getPythonWorkers());
        manifest.setBatchSize(numberCubeProperties.getGenerateBatchSize());
        manifest.setMaxTasksPerChild(numberCubeProperties.getMaxTasksPerChild());
        return manifest;
    }

    private String buildCallbackUrl() {
        return "http://127.0.0.1:" + numberCubeProperties.getCallbackPort() + "/internal/number-cube/generate/callback";
    }

    private void writeManifest(String taskId, NumberCubeJobManifest manifest) throws IOException {
        Path manifestPath = NumberCubePathUtils.getJobManifestPath(taskId);
        Files.createDirectories(manifestPath.getParent());
        Path tmp = manifestPath.resolveSibling(manifestPath.getFileName() + ".tmp");
        Files.writeString(tmp, JSON.toJSONString(manifest), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, manifestPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeInitialStatus(String taskId, int total) throws IOException {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("jobId", taskId);
        status.put("status", NumberCubeTaskStatus.RUNNING);
        status.put("total", total);
        status.put("processed", 0);
        status.put("generated", 0);
        status.put("skipped", 0);
        status.put("failed", 0);
        Path statusPath = NumberCubePathUtils.getJobStatusPath(taskId);
        Files.createDirectories(statusPath.getParent());
        Path tmp = statusPath.resolveSibling(statusPath.getFileName() + ".tmp");
        Files.writeString(tmp, JSON.toJSONString(status), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, statusPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void spawnPythonProcess(String taskId) throws IOException {
        Path scriptPath = resolveScriptPath();
        Path manifestPath = NumberCubePathUtils.getJobManifestPath(taskId);
        Path logPath = NumberCubePathUtils.getJobLogPath(taskId);
        Files.createDirectories(logPath.getParent());

        ProcessBuilder builder = new ProcessBuilder(
                numberCubeProperties.getPythonPath(),
                scriptPath.toString(),
                "--manifest",
                manifestPath.toString()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(logPath.toFile());
        Process process = builder.start();
        log.info("started number cube python job taskId={} pid={} manifest={}", taskId, process.pid(), manifestPath);
    }

    private Path resolveScriptPath() throws IOException {
        Path configured = Paths.get(numberCubeProperties.getGenerateScriptPath());
        if (configured.isAbsolute()) {
            return configured;
        }
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path[] candidates = new Path[] {
                cwd.resolve(configured),
                cwd.resolve("..").resolve(configured).normalize(),
                cwd.resolve("../..").resolve(configured).normalize(),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates[0].normalize();
    }

    private void updateRunning(NumberCubeTask task) {
        long now = System.currentTimeMillis();
        task.setStatus(NumberCubeTaskStatus.RUNNING);
        task.setUpdateTime(now);
        numberCubeTaskMapper.update(task);
        numberCubeTaskNotifyService.publishTaskUpdated(task, null);
    }

    private void markFailed(NumberCubeTask task, String message) {
        long now = System.currentTimeMillis();
        task.setStatus(NumberCubeTaskStatus.FAILED);
        task.setErrorMessage(StringUtils.abbreviate(message, 1000));
        task.setUpdateTime(now);
        numberCubeTaskMapper.update(task);
    }
}
