package cn.cordys.dataspecialist.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.Pager;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.CodingUtils;
import cn.cordys.dataspecialist.domain.DataSpecialist;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminDetailResponse;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminItemResponse;
import cn.cordys.dataspecialist.dto.DataSpecialistAdminPageRequest;
import cn.cordys.dataspecialist.dto.DataSpecialistCreateRequest;
import cn.cordys.dataspecialist.dto.DataSpecialistUpdateRequest;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.tenant.mapper.ExtTenantMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class DataSpecialistAdminService {

    @Resource
    private ExtDataSpecialistMapper extDataSpecialistMapper;

    @Resource
    private ExtTenantMapper extTenantMapper;

    public Pager<List<DataSpecialistAdminItemResponse>> page(DataSpecialistAdminPageRequest request) {
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.max(1, request.getPageSize());
        int offset = (current - 1) * pageSize;
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String like = "%" + keyword + "%";

        Long total = extDataSpecialistMapper.countByKeyword(keyword, like);
        List<DataSpecialistAdminItemResponse> list = extDataSpecialistMapper.pageByKeyword(keyword, like, pageSize, offset);
        return new Pager<>(list, total == null ? 0L : total, pageSize, current);
    }

    public DataSpecialistAdminDetailResponse getDetail(String id) {
        DataSpecialist row = extDataSpecialistMapper.selectById(id);
        if (row == null) {
            throw new GenericException("数据专员不存在");
        }
        List<String> tenantIds = extDataSpecialistMapper.listTenantIds(id);
        DataSpecialistAdminDetailResponse resp = new DataSpecialistAdminDetailResponse();
        resp.setId(row.getId());
        resp.setUsername(row.getUsername());
        resp.setName(row.getSpecialistName());
        resp.setRemark(row.getRemark());
        resp.setEnabled(row.getEnabled());
        resp.setCreateTime(row.getCreateTime());
        resp.setUpdateTime(row.getUpdateTime());
        resp.setTenantIds(tenantIds);
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    public String create(DataSpecialistCreateRequest request, String operatorId) {
        String username = StringUtils.trimToEmpty(request.getUsername());
        if (extDataSpecialistMapper.selectByUsername(username) != null) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "用户名已存在");
        }
        validateTenantIds(request.getTenantIds());

        long now = System.currentTimeMillis();
        DataSpecialist row = new DataSpecialist();
        row.setId(IDGenerator.nextStr());
        row.setUsername(username);
        row.setSpecialistName(StringUtils.trimToNull(request.getName()));
        row.setRemark(StringUtils.trimToNull(request.getRemark()));
        row.setPasswordHash(CodingUtils.md5(request.getPassword()));
        row.setEnabled(true);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setCreateUser(operatorId);
        row.setUpdateUser(operatorId);
        extDataSpecialistMapper.insert(row);
        replaceTenantLinks(row.getId(), request.getTenantIds());
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(String id, DataSpecialistUpdateRequest request, String operatorId) {
        DataSpecialist row = extDataSpecialistMapper.selectById(id);
        if (row == null) {
            throw new GenericException("数据专员不存在");
        }
        if (request.getEnabled() != null) {
            row.setEnabled(request.getEnabled());
        }
        if (request.getName() != null) {
            row.setSpecialistName(StringUtils.trimToNull(request.getName()));
        }
        if (request.getRemark() != null) {
            row.setRemark(StringUtils.trimToNull(request.getRemark()));
        }
        if (StringUtils.isNotBlank(request.getPassword())) {
            row.setPasswordHash(CodingUtils.md5(request.getPassword()));
        }
        if (request.getTenantIds() != null) {
            validateTenantIds(request.getTenantIds());
            replaceTenantLinks(id, request.getTenantIds());
        }
        row.setUpdateTime(System.currentTimeMillis());
        row.setUpdateUser(operatorId);
        extDataSpecialistMapper.update(row);
    }

    private void validateTenantIds(List<String> tenantIds) {
        if (CollectionUtils.isEmpty(tenantIds)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "至少选择一个可导入租户");
        }
        for (String tenantId : tenantIds) {
            if (StringUtils.isBlank(tenantId)) {
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "租户ID无效");
            }
            Long cnt = extTenantMapper.countByTenantId(StringUtils.trim(tenantId));
            if (cnt == null || cnt <= 0) {
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "租户不存在: " + tenantId);
            }
            String status = extTenantMapper.selectStatusByTenantId(StringUtils.trim(tenantId));
            if (!"ACTIVE".equalsIgnoreCase(StringUtils.trimToEmpty(status))) {
                throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "租户未启用: " + tenantId);
            }
        }
    }

    private void replaceTenantLinks(String specialistId, List<String> tenantIds) {
        extDataSpecialistMapper.deleteTenantLinks(specialistId);
        for (String tenantId : tenantIds) {
            extDataSpecialistMapper.insertTenantLink(specialistId, StringUtils.trim(tenantId));
        }
    }
}
