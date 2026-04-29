package cn.cordys.platform.mapper;

import cn.cordys.platform.domain.PlatformUser;
import org.apache.ibatis.annotations.Param;

public interface ExtPlatformUserMapper {
    PlatformUser selectByUsername(@Param("username") String username);
}
