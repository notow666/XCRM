package cn.cordys.crm.tools.service;

import cn.cordys.crm.tools.constants.NumberCubeSelectionMode;
import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.domain.NumberCubeTaskSegment;
import cn.cordys.crm.tools.mapper.ExtNumberCubeTaskSegmentMapper;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentGroupResponse;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentResponse;
import cn.cordys.platform.service.PlatformPhoneSegmentService;
import cn.cordys.platform.util.NumberCubePathUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class NumberCubeTaskSegmentService {

    private static final int BATCH_SIZE = 1000;

    @Resource
    private ExtNumberCubeTaskSegmentMapper extNumberCubeTaskSegmentMapper;
    @Resource
    private PlatformPhoneSegmentService platformPhoneSegmentService;

    public void savePartialSegments(String taskId, List<PlatformPhoneSegmentResponse> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        List<NumberCubeTaskSegment> rows = new ArrayList<>(segments.size());
        for (PlatformPhoneSegmentResponse segment : segments) {
            NumberCubeTaskSegment row = new NumberCubeTaskSegment();
            row.setTaskId(taskId);
            row.setSegment(segment.getSegment());
            row.setPrefix(resolvePrefix(segment));
            rows.add(row);
        }
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            extNumberCubeTaskSegmentMapper.batchInsert(rows.subList(i, end));
        }
    }

    public void deleteByTaskId(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        extNumberCubeTaskSegmentMapper.deleteByTaskId(taskId);
    }

    public List<String> resolveSegments(NumberCubeTask task) {
        if (task == null) {
            return List.of();
        }
        if (NumberCubeSelectionMode.ALL.equals(task.getSelectionMode())) {
            return platformPhoneSegmentService.listByProvinceCity(task.getProvince(), task.getCity()).stream()
                    .map(PlatformPhoneSegmentResponse::getSegment)
                    .toList();
        }
        return extNumberCubeTaskSegmentMapper.listSegmentsByTaskId(task.getId());
    }

    public int writeSegmentsFile(String taskId, List<String> segments) throws IOException {
        if (segments == null || segments.isEmpty()) {
            throw new IOException("segments is empty");
        }
        Path segmentsPath = NumberCubePathUtils.getJobSegmentsPath(taskId);
        Files.createDirectories(segmentsPath.getParent());
        Path tmp = segmentsPath.resolveSibling(segmentsPath.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String segment : segments) {
                if (StringUtils.isBlank(segment)) {
                    continue;
                }
                writer.write(segment.trim());
                writer.newLine();
            }
        }
        Files.move(tmp, segmentsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return segments.size();
    }

    public List<PlatformPhoneSegmentGroupResponse> listPrefixGroups(NumberCubeTask task) {
        if (task == null) {
            return List.of();
        }
        if (NumberCubeSelectionMode.ALL.equals(task.getSelectionMode())) {
            return platformPhoneSegmentService.listSegmentGroups(task.getProvince(), task.getCity());
        }
        return extNumberCubeTaskSegmentMapper.listPrefixGroupsByTaskId(task.getId());
    }

    public List<String> listSegmentsByPrefix(NumberCubeTask task, String prefix) {
        if (task == null || StringUtils.isBlank(prefix)) {
            return List.of();
        }
        if (NumberCubeSelectionMode.ALL.equals(task.getSelectionMode())) {
            return platformPhoneSegmentService.listByProvinceCity(task.getProvince(), task.getCity(), prefix).stream()
                    .map(PlatformPhoneSegmentResponse::getSegment)
                    .toList();
        }
        return extNumberCubeTaskSegmentMapper.listSegmentsByTaskIdAndPrefix(task.getId(), prefix);
    }

    private String resolvePrefix(PlatformPhoneSegmentResponse segment) {
        if (StringUtils.isNotBlank(segment.getPrefix())) {
            return segment.getPrefix();
        }
        String value = StringUtils.defaultString(segment.getSegment());
        return value.length() >= 3 ? value.substring(0, 3) : value;
    }
}
