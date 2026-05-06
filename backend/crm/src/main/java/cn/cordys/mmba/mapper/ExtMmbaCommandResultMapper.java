package cn.cordys.mmba.mapper;

import cn.cordys.mmba.domain.MmbaCommandResult;
import cn.cordys.mmba.dto.request.MmbaCommandResultPageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MMBA 指令结果查询扩展 Mapper。
 */
public interface ExtMmbaCommandResultMapper {

    List<MmbaCommandResult> list(@Param("request") MmbaCommandResultPageRequest request, @Param("orgId") String orgId);
}
