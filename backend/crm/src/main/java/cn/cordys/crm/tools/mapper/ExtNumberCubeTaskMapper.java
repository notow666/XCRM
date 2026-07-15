package cn.cordys.crm.tools.mapper;

import cn.cordys.crm.tools.domain.NumberCubeTask;
import cn.cordys.crm.tools.dto.request.NumberCubeTaskPageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtNumberCubeTaskMapper {

    List<NumberCubeTask> page(@Param("organizationId") String organizationId,
                              @Param("request") NumberCubeTaskPageRequest request);
}
