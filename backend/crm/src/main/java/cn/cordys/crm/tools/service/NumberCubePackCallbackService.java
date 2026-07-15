package cn.cordys.crm.tools.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.constants.ExportConstants;
import cn.cordys.crm.system.domain.ExportTask;
import cn.cordys.crm.system.service.ExportTaskService;
import cn.cordys.crm.tools.constants.NumberCubeMaskMode;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.request.NumberCubePackCallbackRequest;
import cn.cordys.file.engine.DefaultRepositoryDir;
import cn.cordys.file.engine.FileSourceModule;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.platform.util.NumberCubePathUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NumberCubePackCallbackService {

    private final Map<String, String> callbackTokens = new ConcurrentHashMap<>();
    private final Map<String, String> packUserIds = new ConcurrentHashMap<>();
    private final Map<String, String> packTaskIds = new ConcurrentHashMap<>();

    @Resource
    private BaseMapper<ExportTask> exportTaskMapper;
    @Resource
    private BaseMapper<NumberCubeTask> numberCubeTaskMapper;
    @Resource
    private ExportTaskService exportTaskService;
    @Lazy
    @Resource
    private NumberCubeDownloadService numberCubeDownloadService;

    public void registerCallbackToken(String exportJobId, String token, String userId) {
        registerCallbackToken(exportJobId, token, userId, null);
    }

    public void registerCallbackToken(String exportJobId, String token, String userId, String taskId) {
        if (StringUtils.isNotBlank(exportJobId) && StringUtils.isNotBlank(token)) {
            callbackTokens.put(exportJobId, token);
        }
        if (StringUtils.isNotBlank(exportJobId) && StringUtils.isNotBlank(userId)) {
            packUserIds.put(exportJobId, userId);
        }
        if (StringUtils.isNotBlank(exportJobId) && StringUtils.isNotBlank(taskId)) {
            packTaskIds.put(exportJobId, taskId);
        }
    }

    public void handleCallback(NumberCubePackCallbackRequest request, String remoteAddr) {
        if (request == null || StringUtils.isAnyBlank(request.getExportJobId(), request.getTenantId(), request.getToken())) {
            throw new GenericException(Translator.get("number_cube_callback_invalid"));
        }

        String previousTenant = TenantContext.getTenantId();
        boolean releaseSlot = false;
        String taskId = packTaskIds.get(request.getExportJobId());
        String maskMode = null;
        try {
            TenantContext.setTenantId(request.getTenantId());

            if (StringUtils.isBlank(taskId)) {
                taskId = findTaskIdForExport(request.getFileId(), request.getTenantId());
            }
            Map<String, Object> manifest = readManifest(taskId, request.getFileId(), request.getTenantId());

            String expectedToken = callbackTokens.get(request.getExportJobId());
            if (expectedToken == null && manifest != null && manifest.get("callbackToken") != null) {
                expectedToken = String.valueOf(manifest.get("callbackToken"));
            }
            if (!StringUtils.equals(expectedToken, request.getToken())) {
                throw new GenericException(Translator.get("number_cube_callback_invalid"));
            }

            if (manifest != null) {
                Object tid = manifest.get("taskId");
                Object mm = manifest.get("maskMode");
                if (tid != null) {
                    taskId = String.valueOf(tid);
                }
                if (mm != null) {
                    maskMode = NumberCubeMaskMode.normalize(String.valueOf(mm));
                }
            }

            ExportTask exportTask = exportTaskMapper.selectByPrimaryKey(request.getExportJobId());
            if (exportTask == null) {
                throw new GenericException(Translator.get("number_cube_download_failed"));
            }
            if (ExportConstants.ExportStatus.SUCCESS.toString().equals(exportTask.getStatus())
                    || ExportConstants.ExportStatus.ERROR.toString().equals(exportTask.getStatus())) {
                releaseSlot = callbackTokens.remove(request.getExportJobId()) != null;
                packUserIds.remove(request.getExportJobId());
                packTaskIds.remove(request.getExportJobId());
                return;
            }

            String userId = packUserIds.getOrDefault(request.getExportJobId(), exportTask.getUpdateUser());
            boolean success = "SUCCESS".equalsIgnoreCase(request.getStatus());
            exportTaskService.update(
                    request.getExportJobId(),
                    success ? ExportConstants.ExportStatus.SUCCESS.toString() : ExportConstants.ExportStatus.ERROR.toString(),
                    userId);

            if (success) {
                writePackFileIdToTask(taskId, maskMode, request.getFileId());
            }

            releaseSlot = callbackTokens.remove(request.getExportJobId()) != null;
            packUserIds.remove(request.getExportJobId());
            packTaskIds.remove(request.getExportJobId());
            log.info("number cube pack callback exportJobId={} status={} processed={}/{}",
                    request.getExportJobId(), request.getStatus(), request.getProcessed(), request.getTotal());
        } finally {
            if (StringUtils.isNotBlank(taskId) && StringUtils.isNotBlank(maskMode)) {
                numberCubeDownloadService.clearInflightPack(taskId, maskMode);
            }
            if (releaseSlot) {
                numberCubeDownloadService.releaseDownloadSlot();
            }
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void writePackFileIdToTask(String taskId, String maskMode, String fileId) {
        if (StringUtils.isAnyBlank(taskId, maskMode, fileId)) {
            return;
        }
        NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);
        if (task == null) {
            return;
        }
        if (NumberCubeMaskMode.MASKED.equals(maskMode)) {
            task.setMaskedPackFileId(fileId);
        } else {
            task.setPlainPackFileId(fileId);
        }
        task.setUpdateTime(System.currentTimeMillis());
        numberCubeTaskMapper.update(task);
        log.info("number cube pack file cached taskId={} maskMode={} fileId={}", taskId, maskMode, fileId);
    }

    private String findTaskIdForExport(String exportId, String tenantId) {
        if (StringUtils.isAnyBlank(exportId, tenantId)) {
            return null;
        }
        Path cubeRoot = Path.of(
                DefaultRepositoryDir.getDefaultDir(),
                DefaultRepositoryDir.getExportDir(tenantId, FileSourceModule.CUBE));
        if (!Files.isDirectory(cubeRoot)) {
            return null;
        }
        try (var taskDirs = Files.list(cubeRoot)) {
            String safeExportId = NumberCubePathUtils.safeFileName(exportId);
            return taskDirs
                    .filter(Files::isDirectory)
                    .filter(taskDir -> Files.exists(taskDir.resolve(safeExportId).resolve("pack.manifest.json")))
                    .map(taskDir -> taskDir.getFileName().toString())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readManifest(String taskId, String exportId, String tenantId) {
        if (StringUtils.isAnyBlank(taskId, exportId, tenantId)) {
            return null;
        }
        Path manifestPath = Path.of(
                DefaultRepositoryDir.getDefaultDir(),
                DefaultRepositoryDir.getExportDir(tenantId, FileSourceModule.CUBE),
                NumberCubePathUtils.safeFileName(taskId),
                NumberCubePathUtils.safeFileName(exportId),
                "pack.manifest.json");
        if (!Files.exists(manifestPath)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readString(manifestPath, StandardCharsets.UTF_8), Map.class);
        } catch (IOException e) {
            return null;
        }
    }
}
