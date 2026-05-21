package cn.cordys.platform.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.CodingUtils;
import cn.cordys.platform.domain.PlatformUser;
import cn.cordys.platform.dto.PlatformUserAdminDetailResponse;
import cn.cordys.platform.dto.PlatformUserAdminItemResponse;
import cn.cordys.platform.dto.PlatformUserAdminPageRequest;
import cn.cordys.platform.dto.PlatformUserCreateRequest;
import cn.cordys.platform.dto.PlatformUserUpdateRequest;
import cn.cordys.platform.mapper.ExtPlatformUserMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlatformUserAdminService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    @Resource
    private ExtPlatformUserMapper extPlatformUserMapper;

    public Pager<List<PlatformUserAdminItemResponse>> page(PlatformUserAdminPageRequest request) {
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.max(1, request.getPageSize());
        int offset = (current - 1) * pageSize;
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String like = "%" + keyword + "%";

        Long total = extPlatformUserMapper.countByKeyword(keyword, like);
        List<PlatformUserAdminItemResponse> list = extPlatformUserMapper.pageByKeyword(keyword, like, pageSize, offset);
        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    public PlatformUserAdminDetailResponse getDetail(String id) {
        PlatformUser row = extPlatformUserMapper.selectById(id);
        if (row == null) {
            throw new GenericException("平台管理员不存在");
        }
        return toDetailResponse(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public String create(PlatformUserCreateRequest request, String operatorId) {
        String username = StringUtils.trimToEmpty(request.getUsername());
        if (extPlatformUserMapper.selectByUsername(username) != null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "用户名已存在");
        }

        long now = System.currentTimeMillis();
        PlatformUser row = new PlatformUser();
        row.setId(IDGenerator.nextStr());
        row.setUsername(username);
        row.setNickname(StringUtils.trimToNull(request.getNickname()));
        row.setPasswordHash(CodingUtils.md5(request.getPassword()));
        row.setStatus(STATUS_ACTIVE);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setCreateUser(operatorId);
        row.setUpdateUser(operatorId);
        extPlatformUserMapper.insert(row);
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(String id, PlatformUserUpdateRequest request, String operatorId) {
        PlatformUser row = extPlatformUserMapper.selectById(id);
        if (row == null) {
            throw new GenericException("平台管理员不存在");
        }
        if (request.getNickname() != null) {
            row.setNickname(StringUtils.trimToNull(request.getNickname()));
        }
        if (StringUtils.isNotBlank(request.getPassword())) {
            row.setPasswordHash(CodingUtils.md5(request.getPassword()));
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            String status = StringUtils.trimToEmpty(request.getStatus()).toUpperCase();
            if (!STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "状态无效");
            }
            row.setStatus(status);
        }
        row.setUpdateTime(System.currentTimeMillis());
        row.setUpdateUser(operatorId);
        extPlatformUserMapper.update(row);
    }

    private static PlatformUserAdminDetailResponse toDetailResponse(PlatformUser row) {
        PlatformUserAdminDetailResponse resp = new PlatformUserAdminDetailResponse();
        resp.setId(row.getId());
        resp.setUsername(row.getUsername());
        resp.setNickname(row.getNickname());
        resp.setStatus(row.getStatus());
        resp.setCreateTime(row.getCreateTime());
        resp.setUpdateTime(row.getUpdateTime());
        return resp;
    }
}
