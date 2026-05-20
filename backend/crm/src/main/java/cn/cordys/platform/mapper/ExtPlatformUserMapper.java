package cn.cordys.platform.mapper;

import cn.cordys.platform.domain.PlatformUser;
import cn.cordys.platform.dto.PlatformUserAdminItemResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtPlatformUserMapper {

    PlatformUser selectByUsername(@Param("username") String username);

    PlatformUser selectById(@Param("id") String id);

    int insert(PlatformUser row);

    int update(PlatformUser row);

    Long countByKeyword(@Param("keyword") String keyword, @Param("like") String like);

    List<PlatformUserAdminItemResponse> pageByKeyword(@Param("keyword") String keyword,
                                                        @Param("like") String like,
                                                        @Param("pageSize") int pageSize,
                                                        @Param("offset") int offset);

    Long countByUsernameExcludeId(@Param("username") String username, @Param("excludeId") String excludeId);
}
