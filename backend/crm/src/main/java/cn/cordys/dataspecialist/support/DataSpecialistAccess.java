package cn.cordys.dataspecialist.support;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.context.TenantContext;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class DataSpecialistAccess {

    @Resource
    private ExtDataSpecialistMapper extDataSpecialistMapper;

    public SessionUser requireDataSpecialist() {
        SessionUser user = SessionUtils.getUser();
        if (user == null || !DataSpecialistConstants.SESSION_SOURCE.equalsIgnoreCase(StringUtils.defaultString(user.getSource()))) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        return user;
    }

    /**
     * 校验当前请求 {@link TenantContext} 中的租户是否在专员授权范围内。
     */
    public void assertCurrentTenantAllowedForSpecialist() {
        SessionUser specialist = requireDataSpecialist();
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, "缺少租户标识，请设置请求头 X-Tenant-ID");
        }
        if (extDataSpecialistMapper.existsTenantBinding(specialist.getId(), tenantId) <= 0) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
    }
}
