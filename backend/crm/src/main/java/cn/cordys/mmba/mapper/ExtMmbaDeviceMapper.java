package cn.cordys.mmba.mapper;

import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.dto.request.MmbaDevicePageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MMBA 设备主表查询扩展 Mapper。
 */
public interface ExtMmbaDeviceMapper {

    List<MmbaDevice> list();
    List<MmbaDevice> page(@Param("request") MmbaDevicePageRequest request);
}
