package cn.cordys.crm.tools.service;

import cn.cordys.common.constants.ExecutorBeanNames;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.tools.config.NumberCubeProperties;
import cn.cordys.crm.tools.constants.NumberCubeMaskMode;
import cn.cordys.crm.tools.constants.NumberCubeTaskStatus;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.response.NumberCubeDownloadProgressResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeDownloadStartResponse;
import cn.cordys.crm.system.constants.ExportConstants;
import cn.cordys.crm.system.domain.ExportTask;
import cn.cordys.crm.system.service.ExportTaskService;
import cn.cordys.file.engine.DefaultRepositoryDir;
import cn.cordys.file.engine.FileSourceModule;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.platform.util.NumberCubePathUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class NumberCubeDownloadService {

    private static final String PACK_STATUS_FILE = "pack.status.json";
    private static final String PACK_MANIFEST_FILE = "pack.manifest.json";
    private static final String PACK_SEGMENTS_FILE = "pack.segments.txt";

    private final ConcurrentHashMap<String, String> inflightPackJobs = new ConcurrentHashMap<>();

    private Semaphore downloadSemaphore;

    @Resource
    private NumberCubeProperties numberCubeProperties;
    @Resource
    private BaseMapper<NumberCubeTask> numberCubeTaskMapper;
    @Resource
    private BaseMapper<ExportTask> exportTaskMapper;
    @Resource
    private NumberCubeTaskSegmentService numberCubeTaskSegmentService;
    @Resource
    private ExportTaskService exportTaskService;
    @Lazy
    @Resource
    private NumberCubePackCallbackService numberCubePackCallbackService;
    @Resource
    @Qualifier(ExecutorBeanNames.NUMBER_CUBE_DOWNLOAD)
    private Executor numberCubeDownloadExecutor;

    @PostConstruct
    public void init() {
        this.downloadSemaphore = new Semaphore(Math.max(1, numberCubeProperties.getMaxConcurrentDownloads()), true);
    }

    public void releaseDownloadSlot() {
        if (downloadSemaphore != null) {
            downloadSemaphore.release();
        }
    }

    public void clearInflightPack(String taskId, String maskMode) {
        if (StringUtils.isAnyBlank(taskId, maskMode)) {
            return;
        }
        inflightPackJobs.remove(inflightKey(taskId, maskMode));
    }

    public boolean isPackZipReady(String taskId, String exportId, String fileName) {
        if (StringUtils.isAnyBlank(taskId, exportId, fileName)) {
            return false;
        }
        File zipFile = resolveZipFile(taskId, exportId, fileName);
        return zipFile.exists() && zipFile.length() > 0;
    }

    public NumberCubeDownloadStartResponse start(String taskId, String organizationId, String maskMode, String userId) {
        NumberCubeTask task = requireReadyTask(taskId, organizationId);
        String normalizedMaskMode = NumberCubeMaskMode.normalize(maskMode);
        String fileName = buildPackFileName(task, normalizedMaskMode);

        String cachedExportId = resolvePackFileId(task, normalizedMaskMode);
        if (StringUtils.isNotBlank(cachedExportId) && isPackZipReady(taskId, cachedExportId, fileName)) {
            return cachedResponse(cachedExportId);
        }

        NumberCubeDownloadStartResponse scanned = reconcileFromDisk(task, normalizedMaskMode, fileName, organizationId, userId);
        if (scanned != null) {
            return scanned;
        }

        String inflightKey = inflightKey(taskId, normalizedMaskMode);
        String existingJobId = inflightPackJobs.get(inflightKey);
        if (StringUtils.isNotBlank(existingJobId) && isExportInProgress(existingJobId, organizationId)) {
            NumberCubeDownloadStartResponse response = new NumberCubeDownloadStartResponse();
            response.setExportJobId(existingJobId);
            response.setStatus(ExportConstants.ExportStatus.PREPARED.toString());
            response.setCached(false);
            return response;
        }

        List<String> segments = numberCubeTaskSegmentService.resolveSegments(task);
        if (segments == null || segments.isEmpty()) {
            throw new GenericException(Translator.get("number_cube_segment_required"));
        }

        exportTaskService.checkUserTaskLimit(userId, ExportConstants.ExportStatus.PREPARED.toString());

        String exportId = IDGenerator.nextStr();
        ExportTask exportTask = exportTaskService.saveTask(
                organizationId,
                exportId,
                userId,
                ExportConstants.ExportType.NUMBER_CUBE.toString(),
                fileName);

        inflightPackJobs.put(inflightKey, exportTask.getId());

        writePackStatus(taskId, exportId, Map.of(
                "exportJobId", exportTask.getId(),
                "taskId", taskId,
                "maskMode", normalizedMaskMode,
                "status", ExportConstants.ExportStatus.PREPARED.toString(),
                "total", segments.size(),
                "processed", 0
        ));

        String tenantId = TenantContext.getTenantId();
        numberCubeDownloadExecutor.execute(() -> {
            if (StringUtils.isNotBlank(tenantId)) {
                TenantContext.setTenantId(tenantId);
            }
            try {
                runPack(exportTask.getId(), exportId, taskId, organizationId, userId, normalizedMaskMode, fileName, segments.size());
            } finally {
                TenantContext.clear();
            }
        });

        NumberCubeDownloadStartResponse response = new NumberCubeDownloadStartResponse();
        response.setExportJobId(exportTask.getId());
        response.setStatus(ExportConstants.ExportStatus.PREPARED.toString());
        response.setCached(false);
        return response;
    }

    public NumberCubeDownloadProgressResponse progress(String taskId, String exportJobId, String organizationId, String userId) {
        requireReadyTask(taskId, organizationId);
        ExportTask exportTask = requireExportTask(exportJobId, organizationId);
        NumberCubeDownloadProgressResponse response = new NumberCubeDownloadProgressResponse();
        response.setExportJobId(exportTask.getId());
        response.setStatus(exportTask.getStatus());
        response.setFileName(exportTask.getFileName());

        Map<String, Object> packStatus = readPackStatus(taskId, exportTask.getFileId());
        if (packStatus != null) {
            response.setTotal(asInt(packStatus.get("total")));
            response.setProcessed(asInt(packStatus.get("processed")));
            if (packStatus.get("errorMessage") != null) {
                response.setErrorMessage(String.valueOf(packStatus.get("errorMessage")));
            }
            String packState = packStatus.get("status") == null ? null : String.valueOf(packStatus.get("status"));
            if (ExportConstants.ExportStatus.PREPARED.toString().equals(exportTask.getStatus()) && packState != null) {
                if ("RUNNING".equalsIgnoreCase(packState) || "ERROR".equalsIgnoreCase(packState)) {
                    response.setStatus(packState);
                } else if ("SUCCESS".equalsIgnoreCase(packState)) {
                    // Disk finished but callback missed: reconcile so frontend can download.
                    reconcileExportSuccess(taskId, exportTask, packStatus, userId);
                    response.setStatus(ExportConstants.ExportStatus.SUCCESS.toString());
                }
            }
        }
        return response;
    }

    public void streamFile(String taskId, String exportJobId, String organizationId, String userId, HttpServletResponse response) {
        NumberCubeTask task = requireReadyTask(taskId, organizationId);
        ExportTask exportTask = requireExportTask(exportJobId, organizationId);
        Map<String, Object> packStatus = readPackStatus(taskId, exportTask.getFileId());
        if (!ExportConstants.ExportStatus.SUCCESS.toString().equals(exportTask.getStatus())) {
            if (packStatus != null && "SUCCESS".equalsIgnoreCase(String.valueOf(packStatus.get("status")))) {
                reconcileExportSuccess(taskId, exportTask, packStatus, userId);
            } else {
                throw new GenericException(Translator.get("number_cube_download_not_ready"));
            }
        }
        streamZip(taskId, exportTask.getFileId(), exportTask.getFileName(), response);
    }

    public void streamCached(String taskId, String organizationId, String maskMode, HttpServletResponse response) {
        NumberCubeTask task = requireReadyTask(taskId, organizationId);
        String normalizedMaskMode = NumberCubeMaskMode.normalize(maskMode);
        String exportId = resolvePackFileId(task, normalizedMaskMode);
        String fileName = buildPackFileName(task, normalizedMaskMode);
        if (StringUtils.isBlank(exportId) || !isPackZipReady(taskId, exportId, fileName)) {
            throw new GenericException(Translator.get("number_cube_download_not_ready"));
        }
        streamZip(taskId, exportId, fileName, response);
    }

    public void deletePackDirs(NumberCubeTask task) {
        if (task == null || StringUtils.isBlank(task.getId())) {
            return;
        }
        deleteDirQuietly(resolveTaskPackRoot(task.getId()));
    }

    private NumberCubeDownloadStartResponse reconcileFromDisk(NumberCubeTask task, String maskMode, String fileName,
                                                              String organizationId, String userId) {
        Path taskRoot = resolveTaskPackRoot(task.getId());
        if (!Files.isDirectory(taskRoot)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(taskRoot)) {
            for (Path exportDir : stream) {
                if (!Files.isDirectory(exportDir)) {
                    continue;
                }
                String exportId = exportDir.getFileName().toString();
                Map<String, Object> packStatus = readPackStatus(task.getId(), exportId);
                if (packStatus == null) {
                    continue;
                }
                String statusMask = packStatus.get("maskMode") == null
                        ? null : NumberCubeMaskMode.normalize(String.valueOf(packStatus.get("maskMode")));
                if (!StringUtils.equals(maskMode, statusMask)) {
                    continue;
                }
                String packState = packStatus.get("status") == null ? null : String.valueOf(packStatus.get("status"));
                String exportJobId = packStatus.get("exportJobId") == null ? null : String.valueOf(packStatus.get("exportJobId"));

                if ("SUCCESS".equalsIgnoreCase(packState) && isPackZipReady(task.getId(), exportId, fileName)) {
                    writePackFileIdToTask(task, maskMode, exportId);
                    if (StringUtils.isNotBlank(exportJobId)) {
                        ExportTask exportTask = exportTaskMapper.selectByPrimaryKey(exportJobId);
                        if (exportTask != null
                                && StringUtils.equals(organizationId, exportTask.getOrganizationId())
                                && !ExportConstants.ExportStatus.SUCCESS.toString().equals(exportTask.getStatus())) {
                            exportTaskService.update(exportJobId, ExportConstants.ExportStatus.SUCCESS.toString(),
                                    StringUtils.defaultIfBlank(userId, exportTask.getUpdateUser()));
                        }
                    }
                    inflightPackJobs.remove(inflightKey(task.getId(), maskMode));
                    return cachedResponse(exportId);
                }

                if ("RUNNING".equalsIgnoreCase(packState) || ExportConstants.ExportStatus.PREPARED.toString().equalsIgnoreCase(packState)) {
                    if (StringUtils.isBlank(exportJobId)) {
                        continue;
                    }
                    ExportTask exportTask = exportTaskMapper.selectByPrimaryKey(exportJobId);
                    if (exportTask == null || !StringUtils.equals(organizationId, exportTask.getOrganizationId())) {
                        continue;
                    }
                    if (ExportConstants.ExportStatus.ERROR.toString().equals(exportTask.getStatus())
                            || ExportConstants.ExportStatus.SUCCESS.toString().equals(exportTask.getStatus())) {
                        continue;
                    }
                    inflightPackJobs.put(inflightKey(task.getId(), maskMode), exportJobId);
                    NumberCubeDownloadStartResponse response = new NumberCubeDownloadStartResponse();
                    response.setExportJobId(exportJobId);
                    response.setStatus(ExportConstants.ExportStatus.PREPARED.toString());
                    response.setCached(false);
                    response.setFileId(exportId);
                    return response;
                }
            }
        } catch (IOException e) {
            log.warn("scan number cube pack dirs failed taskId={}", task.getId(), e);
        }
        return null;
    }

    private void reconcileExportSuccess(String taskId, ExportTask exportTask, Map<String, Object> packStatus, String userId) {
        String maskMode = packStatus.get("maskMode") == null
                ? null : NumberCubeMaskMode.normalize(String.valueOf(packStatus.get("maskMode")));
        NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);
        if (task != null && StringUtils.isNotBlank(maskMode)) {
            writePackFileIdToTask(task, maskMode, exportTask.getFileId());
        }
        if (!ExportConstants.ExportStatus.SUCCESS.toString().equals(exportTask.getStatus())) {
            exportTaskService.update(exportTask.getId(), ExportConstants.ExportStatus.SUCCESS.toString(),
                    StringUtils.defaultIfBlank(userId, exportTask.getUpdateUser()));
            exportTask.setStatus(ExportConstants.ExportStatus.SUCCESS.toString());
        }
        if (StringUtils.isNotBlank(maskMode)) {
            clearInflightPack(taskId, maskMode);
        }
    }

    private void writePackFileIdToTask(NumberCubeTask task, String maskMode, String exportId) {
        if (NumberCubeMaskMode.MASKED.equals(maskMode)) {
            if (StringUtils.equals(task.getMaskedPackFileId(), exportId)) {
                return;
            }
            task.setMaskedPackFileId(exportId);
        } else {
            if (StringUtils.equals(task.getPlainPackFileId(), exportId)) {
                return;
            }
            task.setPlainPackFileId(exportId);
        }
        task.setUpdateTime(System.currentTimeMillis());
        numberCubeTaskMapper.update(task);
    }

    private NumberCubeDownloadStartResponse cachedResponse(String exportId) {
        NumberCubeDownloadStartResponse response = new NumberCubeDownloadStartResponse();
        response.setStatus(ExportConstants.ExportStatus.SUCCESS.toString());
        response.setCached(true);
        response.setFileId(exportId);
        return response;
    }

    private void streamZip(String taskId, String exportId, String fileName, HttpServletResponse response) {
        File zipFile = resolveZipFile(taskId, exportId, fileName);
        if (!zipFile.exists() || zipFile.length() <= 0) {
            throw new GenericException(Translator.get("number_cube_download_failed"));
        }
        String zipName = fileName + ".zip";
        response.setContentType("application/zip");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(zipName, StandardCharsets.UTF_8.name()) + "\"");
            Files.copy(zipFile.toPath(), response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("number cube stream zip failed taskId={} exportId={}", taskId, exportId, e);
            throw new GenericException(Translator.get("number_cube_download_failed"));
        }
    }

    private void runPack(String exportJobId, String exportId, String taskId, String organizationId, String userId,
                         String maskMode, String fileName, int expectedTotal) {
        boolean slotAcquired = false;
        try {
            if (!downloadSemaphore.tryAcquire()) {
                markExportError(exportJobId, taskId, exportId, userId, Translator.get("number_cube_download_busy"), expectedTotal, 0);
                clearInflightPack(taskId, maskMode);
                return;
            }
            slotAcquired = true;

            NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);
            if (task == null || !StringUtils.equals(organizationId, task.getOrganizationId())
                    || !NumberCubeTaskStatus.SUCCESS.equals(task.getStatus())) {
                markExportError(exportJobId, taskId, exportId, userId, Translator.get("number_cube_download_not_ready"), expectedTotal, 0);
                clearInflightPack(taskId, maskMode);
                return;
            }

            List<String> segments = numberCubeTaskSegmentService.resolveSegments(task);
            if (segments == null || segments.isEmpty()) {
                markExportError(exportJobId, taskId, exportId, userId, Translator.get("number_cube_segment_required"), expectedTotal, 0);
                clearInflightPack(taskId, maskMode);
                return;
            }

            String tenantId = TenantContext.requireTenantId();
            String callbackToken = IDGenerator.nextStr();
            File zipFile = resolveZipFile(taskId, exportId, fileName);
            Files.createDirectories(zipFile.getParentFile().toPath());

            Path packDir = resolvePackDir(taskId, exportId);
            Path segmentsPath = packDir.resolve(PACK_SEGMENTS_FILE);
            writeSegmentsFile(segmentsPath, segments);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("exportJobId", exportJobId);
            manifest.put("fileId", exportId);
            manifest.put("taskId", taskId);
            manifest.put("tenantId", tenantId);
            manifest.put("organizationId", organizationId);
            manifest.put("userId", userId);
            manifest.put("province", task.getProvince());
            manifest.put("city", task.getCity());
            manifest.put("cityBase", NumberCubePathUtils.safeFileName(task.getCity()));
            manifest.put("cubeRoot", NumberCubePathUtils.getMasterCubeRoot().toString());
            manifest.put("segmentsFile", segmentsPath.toString());
            manifest.put("maskMode", maskMode);
            manifest.put("rowsPerExcel", numberCubeProperties.getRowsPerExcel());
            manifest.put("outputZip", zipFile.getAbsolutePath());
            manifest.put("statusFile", packDir.resolve(PACK_STATUS_FILE).toString());
            manifest.put("callbackUrl", buildPackCallbackUrl());
            manifest.put("callbackToken", callbackToken);

            writePackManifest(taskId, exportId, manifest);
            numberCubePackCallbackService.registerCallbackToken(exportJobId, callbackToken, userId, taskId);

            writePackStatus(taskId, exportId, Map.of(
                    "exportJobId", exportJobId,
                    "taskId", taskId,
                    "maskMode", maskMode,
                    "status", "RUNNING",
                    "total", segments.size(),
                    "processed", 0
            ));

            spawnPythonPack(taskId, exportId);
            slotAcquired = false;
            log.info("number cube pack python started exportJobId={} taskId={} segments={}", exportJobId, taskId, segments.size());
        } catch (Exception e) {
            log.error("number cube pack failed to start exportJobId={} taskId={}", exportJobId, taskId, e);
            markExportError(exportJobId, taskId, exportId, userId,
                    StringUtils.abbreviate(StringUtils.defaultIfBlank(e.getMessage(), Translator.get("number_cube_download_failed")), 500),
                    expectedTotal, 0);
            clearInflightPack(taskId, maskMode);
        } finally {
            if (slotAcquired) {
                downloadSemaphore.release();
            }
        }
    }

    private void spawnPythonPack(String taskId, String exportId) throws IOException {
        Path scriptPath = resolvePackScriptPath();
        Path packDir = resolvePackDir(taskId, exportId);
        Path manifestPath = packDir.resolve(PACK_MANIFEST_FILE);
        Path logPath = packDir.resolve("pack.log");
        Files.createDirectories(logPath.getParent());

        ProcessBuilder builder = new ProcessBuilder(
                numberCubeProperties.getPythonPath(),
                scriptPath.toString(),
                "--manifest",
                manifestPath.toString()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(logPath.toFile());
        builder.environment().put("PYTHONUNBUFFERED", "1");
        Process process = builder.start();
        log.info("started number cube pack python taskId={} exportId={} pid={}", taskId, exportId, process.pid());
    }

    private Path resolvePackScriptPath() {
        Path configured = Paths.get(numberCubeProperties.getPackScriptPath());
        if (configured.isAbsolute()) {
            return configured;
        }
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path[] candidates = new Path[]{
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

    private String buildPackCallbackUrl() {
        return "http://127.0.0.1:" + numberCubeProperties.getCallbackPort() + "/internal/number-cube/pack/callback";
    }

    private void writeSegmentsFile(Path path, List<String> segments) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, String.join("\n", segments) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void writePackManifest(String taskId, String exportId, Map<String, Object> manifest) throws IOException {
        Path manifestPath = resolvePackDir(taskId, exportId).resolve(PACK_MANIFEST_FILE);
        Files.createDirectories(manifestPath.getParent());
        Path tmp = manifestPath.resolveSibling(manifestPath.getFileName() + ".tmp");
        Files.writeString(tmp, JSON.toJSONString(manifest), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, manifestPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void markExportError(String exportJobId, String taskId, String exportId, String userId,
                                 String errorMessage, int total, int processed) {
        try {
            exportTaskService.update(exportJobId, ExportConstants.ExportStatus.ERROR.toString(), userId);
        } catch (Exception e) {
            log.warn("failed to mark export error exportJobId={}", exportJobId, e);
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("exportJobId", exportJobId);
        status.put("taskId", taskId);
        status.put("status", ExportConstants.ExportStatus.ERROR.toString());
        status.put("total", total);
        status.put("processed", processed);
        status.put("errorMessage", errorMessage);
        writePackStatus(taskId, exportId, status);
    }

    private boolean isExportInProgress(String exportJobId, String organizationId) {
        ExportTask exportTask = exportTaskMapper.selectByPrimaryKey(exportJobId);
        if (exportTask == null || !StringUtils.equals(organizationId, exportTask.getOrganizationId())) {
            return false;
        }
        return ExportConstants.ExportStatus.PREPARED.toString().equals(exportTask.getStatus());
    }

    private String resolvePackFileId(NumberCubeTask task, String maskMode) {
        if (NumberCubeMaskMode.MASKED.equals(maskMode)) {
            return task.getMaskedPackFileId();
        }
        return task.getPlainPackFileId();
    }

    public static String buildPackFileName(NumberCubeTask task, String maskMode) {
        return NumberCubePathUtils.safeFileName(task.getProvince() + task.getCity()) + "_" + maskMode;
    }

    private static String inflightKey(String taskId, String maskMode) {
        return taskId + ":" + maskMode;
    }

    private NumberCubeTask requireReadyTask(String taskId, String organizationId) {
        NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);
        if (task == null || !StringUtils.equals(organizationId, task.getOrganizationId())) {
            throw new GenericException(Translator.get("number_cube_task_not_found"));
        }
        if (!NumberCubeTaskStatus.SUCCESS.equals(task.getStatus())) {
            throw new GenericException(Translator.get("number_cube_download_not_ready"));
        }
        return task;
    }

    private ExportTask requireExportTask(String exportJobId, String organizationId) {
        ExportTask exportTask = exportTaskMapper.selectByPrimaryKey(exportJobId);
        if (exportTask == null
                || !StringUtils.equals(organizationId, exportTask.getOrganizationId())
                || !ExportConstants.ExportType.NUMBER_CUBE.toString().equals(exportTask.getResourceType())) {
            throw new GenericException(Translator.get("number_cube_download_failed"));
        }
        return exportTask;
    }

    public Path resolveTaskPackRoot(String taskId) {
        return Path.of(
                DefaultRepositoryDir.getDefaultDir(),
                DefaultRepositoryDir.getExportDir(TenantContext.requireTenantId(), FileSourceModule.CUBE),
                NumberCubePathUtils.safeFileName(taskId));
    }

    public Path resolvePackDir(String taskId, String exportId) {
        return resolveTaskPackRoot(taskId).resolve(NumberCubePathUtils.safeFileName(exportId));
    }

    private File resolveZipFile(String taskId, String exportId, String fileName) {
        return resolvePackDir(taskId, exportId).resolve(fileName + ".zip").toFile();
    }

    private void deleteDirQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("failed to delete number cube pack dir {}", dir, e);
        }
    }

    private void writePackStatus(String taskId, String exportId, Map<String, Object> payload) {
        try {
            Path statusPath = resolvePackDir(taskId, exportId).resolve(PACK_STATUS_FILE);
            Files.createDirectories(statusPath.getParent());
            Path tmp = statusPath.resolveSibling(statusPath.getFileName() + ".tmp");
            Files.writeString(tmp, JSON.toJSONString(payload), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, statusPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("write number cube pack status failed taskId={} exportId={}", taskId, exportId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPackStatus(String taskId, String exportId) {
        Path statusPath = resolvePackDir(taskId, exportId).resolve(PACK_STATUS_FILE);
        if (!Files.exists(statusPath)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readString(statusPath, StandardCharsets.UTF_8), Map.class);
        } catch (IOException e) {
            log.warn("read number cube pack status failed taskId={} exportId={}", taskId, exportId, e);
            return null;
        }
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
