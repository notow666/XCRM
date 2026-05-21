package cn.cordys.crm.system.service;

import cn.cordys.context.TenantContext;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.system.domain.GlobalPhoneMaskConfig;
import cn.cordys.crm.system.dto.response.GlobalPhoneMaskConfigResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(rollbackFor = Exception.class)
public class GlobalPhoneMaskConfigService {

    @Resource
    private BaseMapper<GlobalPhoneMaskConfig> globalPhoneMaskConfigMapper;

    private final Map<String, Boolean> enabledCache = new ConcurrentHashMap<>();

    public GlobalPhoneMaskConfigResponse getConfig(String orgId) {
        GlobalPhoneMaskConfig config = getByOrgId(orgId);
        GlobalPhoneMaskConfigResponse response = new GlobalPhoneMaskConfigResponse();
        response.setEnabled(config != null && Boolean.TRUE.equals(config.getEnabled()));
        return response;
    }

    public boolean isEnabled(String orgId) {
        if (orgId == null) {
            return false;
        }
        String cacheKey = buildCacheKey(orgId);
        if (cacheKey == null) {
            GlobalPhoneMaskConfig config = getByOrgId(orgId);
            return config != null && Boolean.TRUE.equals(config.getEnabled());
        }
        Boolean cached = enabledCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        GlobalPhoneMaskConfig config = getByOrgId(orgId);
        boolean enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
        enabledCache.put(cacheKey, enabled);
        return enabled;
    }

    public void save(Boolean enabled, String orgId, String userId) {
        long currentTime = System.currentTimeMillis();
        GlobalPhoneMaskConfig config = getByOrgId(orgId);
        boolean isNew = config == null;
        if (config == null) {
            config = new GlobalPhoneMaskConfig();
            config.setId(IDGenerator.nextStr());
            config.setOrganizationId(orgId);
            config.setCreateTime(currentTime);
            config.setCreateUser(userId);
        }
        config.setEnabled(Boolean.TRUE.equals(enabled));
        config.setUpdateTime(currentTime);
        config.setUpdateUser(userId);
        if (config.getCreateTime() == null) {
            config.setCreateTime(currentTime);
        }
        if (config.getCreateUser() == null) {
            config.setCreateUser(userId);
        }
        if (isNew) {
            globalPhoneMaskConfigMapper.insert(config);
        } else {
            globalPhoneMaskConfigMapper.updateById(config);
        }
        String cacheKey = buildCacheKey(orgId);
        if (cacheKey != null) {
            enabledCache.put(cacheKey, Boolean.TRUE.equals(enabled));
        }
    }

    public GlobalPhoneMaskConfig getByOrgId(String orgId) {
        if (orgId == null) {
            return null;
        }
        LambdaQueryWrapper<GlobalPhoneMaskConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GlobalPhoneMaskConfig::getOrganizationId, orgId);
        return globalPhoneMaskConfigMapper.selectListByLambda(queryWrapper).stream().findFirst().orElse(null);
    }

    private String buildCacheKey(String orgId) {
        String tenantId = StringUtils.trimToNull(TenantContext.getTenantId());
        if (tenantId == null) {
            return null;
        }
        return tenantId + ":" + orgId;
    }
}
