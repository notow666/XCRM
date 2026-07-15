package cn.cordys.crm.tools.mapper;

import cn.cordys.crm.tools.domain.NumberCubeTaskSegment;
import cn.cordys.platform.dto.response.PlatformPhoneSegmentGroupResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtNumberCubeTaskSegmentMapper {

    int batchInsert(@Param("list") List<NumberCubeTaskSegment> segments);

    int deleteByTaskId(@Param("taskId") String taskId);

    List<String> listSegmentsByTaskId(@Param("taskId") String taskId);

    List<String> listSegmentsByTaskIdAndPrefix(@Param("taskId") String taskId, @Param("prefix") String prefix);

    List<PlatformPhoneSegmentGroupResponse> listPrefixGroupsByTaskId(@Param("taskId") String taskId);
}
