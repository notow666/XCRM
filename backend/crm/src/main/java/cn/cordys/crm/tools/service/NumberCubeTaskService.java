package cn.cordys.crm.tools.service;

import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.tools.config.NumberCubeProperties;
import cn.cordys.crm.tools.constants.NumberCubeMaskMode;
import cn.cordys.crm.tools.constants.NumberCubeSelectionMode;
import cn.cordys.crm.tools.constants.NumberCubeTaskStatus;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskCreateRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskPageRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskProgressBatchRequest;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskSegmentDetailRequest;
import cn.cordys.crm.tools.dto.response.NumberCubeDownloadProgressResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeDownloadStartResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskDetailResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskProgressResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskResponse;
import cn.cordys.crm.tools.dto.response.NumberCubeTaskSegmentDetailResponse;
import cn.cordys.crm.tools.mapper.ExtNumberCubeTaskMapper;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.platform.dto.response.PhoneSegmentRegionNodeResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentGroupResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentResponse;
import cn.cordys.platform.service.PlatformPhoneSegmentService;
import cn.cordys.platform.util.NumberCubePathUtils;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NumberCubeTaskService {

    private static final Map<String, List<OptionDTO>> EMPTY_OPTIONS = Collections.emptyMap();

    @Resource
    private BaseMapper<NumberCubeTask> numberCubeTaskMapper;

    @Resource
    private ExtNumberCubeTaskMapper extNumberCubeTaskMapper;

    @Resource
    private PlatformPhoneSegmentService platformPhoneSegmentService;

    @Resource
    private NumberCubeGenerateJobService numberCubeGenerateJobService;

    @Resource
    private NumberCubeDownloadService numberCubeDownloadService;

    @Resource
    private NumberCubeGenerateCallbackService numberCubeGenerateCallbackService;

    @Resource
    private NumberCubeTaskSegmentService numberCubeTaskSegmentService;

    @Resource
    private NumberCubeProperties numberCubeProperties;


    public PagerWithOption<List<NumberCubeTaskResponse>> page(NumberCubeTaskPageRequest request, String organizationId) {

        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());

        List<NumberCubeTask> tasks = extNumberCubeTaskMapper.page(organizationId, request);

        List<NumberCubeTaskResponse> list = tasks.stream().map(this::toListResponse).collect(Collectors.toList());

        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);

    }

    @Transactional(rollbackFor = Exception.class)
    public NumberCubeTaskResponse create(NumberCubeTaskCreateRequest request, String userId, String organizationId) {

        String province = StringUtils.trimToEmpty(request.getProvince());

        String city = StringUtils.trimToEmpty(request.getCity());

        if (StringUtils.isBlank(province) || StringUtils.isBlank(city)) {

            throw new GenericException(Translator.get("number_cube_province_city_required"));

        }

        String selectionMode = NumberCubeSelectionMode.normalize(request.getSelectionMode());

        int segmentCount;

        List<PlatformPhoneSegmentResponse> partialSegments = List.of();


        if (NumberCubeSelectionMode.ALL.equals(selectionMode)) {

            segmentCount = Math.toIntExact(platformPhoneSegmentService.countByProvinceCity(province, city));

            if (segmentCount <= 0) {

                throw new GenericException(Translator.get("number_cube_segment_required"));

            }

        } else {

            List<String> segmentIds = request.getSegmentIds() == null ? List.of() : request.getSegmentIds().stream()

                    .filter(StringUtils::isNotBlank)

                    .map(String::trim)

                    .distinct()

                    .toList();

            if (segmentIds.isEmpty()) {

                throw new GenericException(Translator.get("number_cube_segment_required"));

            }

            partialSegments = platformPhoneSegmentService.listByIds(segmentIds);

            if (partialSegments.size() != segmentIds.size()) {

                throw new GenericException(Translator.get("number_cube_segment_invalid"));

            }

            for (PlatformPhoneSegmentResponse segment : partialSegments) {

                if (!Objects.equals(province, segment.getProvince()) || !Objects.equals(city, segment.getCity())) {

                    throw new GenericException(Translator.get("number_cube_segment_invalid"));

                }

            }

            segmentCount = partialSegments.size();

        }


        validateSegmentCount(segmentCount);


        long now = System.currentTimeMillis();

        NumberCubeTask task = new NumberCubeTask();

        task.setId(IDGenerator.nextStr());

        task.setOrganizationId(organizationId);

        task.setProvince(province);

        task.setCity(city);

        task.setSelectionMode(selectionMode);

        task.setSegmentCount(segmentCount);

        task.setStatus(NumberCubeTaskStatus.PENDING);

        task.setCreateUser(userId);

        task.setUpdateUser(userId);

        task.setCreateTime(now);

        task.setUpdateTime(now);

        numberCubeTaskMapper.insert(task);


        if (NumberCubeSelectionMode.PARTIAL.equals(selectionMode)) {

            numberCubeTaskSegmentService.savePartialSegments(task.getId(), partialSegments);

        }


        scheduleGenerateAfterCommit(task);

        return toListResponse(task);

    }


    public NumberCubeTaskDetailResponse detail(String taskId, String organizationId) {

        NumberCubeTask task = requireTask(taskId, organizationId);

        NumberCubeTaskDetailResponse response = new NumberCubeTaskDetailResponse();

        response.setId(task.getId());

        response.setProvince(task.getProvince());

        response.setCity(task.getCity());

        response.setSelectionMode(task.getSelectionMode());

        response.setSegmentCount(resolveSegmentCount(task));

        response.setSegmentGroups(numberCubeTaskSegmentService.listPrefixGroups(task));

        response.setStatus(task.getStatus());

        response.setErrorMessage(task.getErrorMessage());

        response.setCreateTime(task.getCreateTime());

        response.setUpdateTime(task.getUpdateTime());

        return response;

    }


    public NumberCubeTaskSegmentDetailResponse segmentDetail(NumberCubeTaskSegmentDetailRequest request,

                                                             String organizationId) {

        NumberCubeTask task = requireTask(request.getTaskId(), organizationId);

        NumberCubeTaskSegmentDetailResponse response = new NumberCubeTaskSegmentDetailResponse();

        response.setTaskId(task.getId());

        response.setPrefix(StringUtils.trimToEmpty(request.getPrefix()));

        response.setSegments(numberCubeTaskSegmentService.listSegmentsByPrefix(task, response.getPrefix()));

        return response;

    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String taskId, String organizationId) {

        NumberCubeTask task = requireTask(taskId, organizationId);

        if (NumberCubeTaskStatus.RUNNING.equals(task.getStatus())) {

            throw new GenericException(Translator.get("number_cube_delete_running"));

        }

        numberCubeTaskSegmentService.deleteByTaskId(taskId);

        numberCubeDownloadService.deletePackDirs(task);

        numberCubeTaskMapper.deleteByPrimaryKey(taskId);

        deleteJobArtifacts(taskId);
    }


    public List<PhoneSegmentRegionNodeResponse> listRegions() {

        return platformPhoneSegmentService.listRegions();

    }


    public List<PlatformPhoneSegmentResponse> listSegments(String province, String city, String prefix) {

        return platformPhoneSegmentService.listByProvinceCity(province, city, prefix);

    }


    public List<PlatformPhoneSegmentGroupResponse> listSegmentGroups(String province, String city) {

        return platformPhoneSegmentService.listSegmentGroups(province, city);

    }


    public NumberCubeTaskProgressResponse getProgress(String taskId, String organizationId) {

        return numberCubeGenerateCallbackService.getProgress(taskId, organizationId);

    }


    public List<NumberCubeTaskProgressResponse> getProgressBatch(NumberCubeTaskProgressBatchRequest request,

                                                                 String organizationId) {

        return numberCubeGenerateCallbackService.getProgressBatch(request.getTaskIds(), organizationId);

    }


    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public NumberCubeDownloadStartResponse downloadStart(String taskId, String organizationId, String maskMode, String userId) {
        return numberCubeDownloadService.start(taskId, organizationId, maskMode, userId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public NumberCubeDownloadProgressResponse downloadProgress(String taskId, String exportJobId, String organizationId, String userId) {
        return numberCubeDownloadService.progress(taskId, exportJobId, organizationId, userId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void downloadFile(String taskId, String exportJobId, String organizationId, String userId, HttpServletResponse response) {
        numberCubeDownloadService.streamFile(taskId, exportJobId, organizationId, userId, response);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void downloadCached(String taskId, String organizationId, String maskMode, HttpServletResponse response) {
        numberCubeDownloadService.streamCached(taskId, organizationId, maskMode, response);
    }

    private void validateSegmentCount(int segmentCount) {

        if (segmentCount <= 0) {

            throw new GenericException(Translator.get("number_cube_segment_required"));

        }

        if (segmentCount > numberCubeProperties.getMaxSegmentsPerTask()) {

            throw new GenericException(Translator.get("number_cube_segment_limit_exceeded"));

        }

    }


    private void scheduleGenerateAfterCommit(NumberCubeTask task) {

        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override

                public void afterCommit() {

                    numberCubeGenerateJobService.submit(task);

                }

            });

        } else {

            numberCubeGenerateJobService.submit(task);

        }

    }


    private NumberCubeTask requireTask(String taskId, String organizationId) {

        NumberCubeTask task = numberCubeTaskMapper.selectByPrimaryKey(taskId);

        if (task == null || !StringUtils.equals(organizationId, task.getOrganizationId())) {

            throw new GenericException(Translator.get("number_cube_task_not_found"));

        }

        return task;

    }


    private void deleteJobArtifacts(String taskId) {

        try {

            Files.deleteIfExists(NumberCubePathUtils.getJobManifestPath(taskId));

            Files.deleteIfExists(NumberCubePathUtils.getJobSegmentsPath(taskId));

            Files.deleteIfExists(NumberCubePathUtils.getJobLogPath(taskId));

            Files.deleteIfExists(NumberCubePathUtils.getJobStatusPath(taskId));

        } catch (IOException e) {

            log.warn("failed to delete number cube job artifacts taskId={}", taskId, e);

        }

    }


    private NumberCubeTaskResponse toListResponse(NumberCubeTask task) {

        NumberCubeTaskResponse response = new NumberCubeTaskResponse();

        response.setId(task.getId());

        response.setProvince(task.getProvince());

        response.setCity(task.getCity());

        response.setSegmentCount(resolveSegmentCount(task));

        response.setStatus(task.getStatus());

        response.setErrorMessage(task.getErrorMessage());

        response.setPlainPackFileId(task.getPlainPackFileId());

        response.setMaskedPackFileId(task.getMaskedPackFileId());

        String plainName = NumberCubeDownloadService.buildPackFileName(task, NumberCubeMaskMode.PLAIN);

        String maskedName = NumberCubeDownloadService.buildPackFileName(task, NumberCubeMaskMode.MASKED);

        response.setPlainPackReady(StringUtils.isNotBlank(task.getPlainPackFileId())
                && numberCubeDownloadService.isPackZipReady(task.getId(), task.getPlainPackFileId(), plainName));
        response.setMaskedPackReady(StringUtils.isNotBlank(task.getMaskedPackFileId())
                && numberCubeDownloadService.isPackZipReady(task.getId(), task.getMaskedPackFileId(), maskedName));

        response.setCreateTime(task.getCreateTime());

        response.setUpdateTime(task.getUpdateTime());

        return response;

    }


    private int resolveSegmentCount(NumberCubeTask task) {

        if (task.getSegmentCount() != null && task.getSegmentCount() > 0) {

            return task.getSegmentCount();

        }

        return 0;

    }

}

