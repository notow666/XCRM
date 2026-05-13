package cn.cordys.dataspecialist.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.CodingUtils;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.dataspecialist.dto.DataSpecialistLoginRequest;
import cn.cordys.dataspecialist.domain.DataSpecialist;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.UserDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;

@Service
public class DataSpecialistAuthService {

    @Resource
    private ExtDataSpecialistMapper extDataSpecialistMapper;

    public SessionUser login(DataSpecialistLoginRequest request) {
        String username = StringUtils.trimToEmpty(request.getUsername());
        DataSpecialist row = extDataSpecialistMapper.selectByUsername(username);
        if (row == null) {
            throw new GenericException("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(row.getEnabled())) {
            throw new GenericException("账号已禁用");
        }
        String encryptedPwd = CodingUtils.md5(request.getPassword());
        if (!Objects.equals(row.getPasswordHash(), encryptedPwd)) {
            throw new GenericException("账号或密码错误");
        }

        UserDTO user = new UserDTO();
        user.setId(row.getId());
        user.setName(row.getUsername());
        user.setEmail(row.getUsername() + "@data-specialist.local");
        user.setSource(DataSpecialistConstants.SESSION_SOURCE);
        user.setEnable(true);
        user.setTenantId("---");
        user.setPermissionIds(Collections.singleton(DataSpecialistConstants.PERMISSION_POOL_IMPORT));
        user.setOrganizationIds(Collections.emptySet());
        user.setTenantIds(Collections.emptySet());

        SessionUser sessionUser = SessionUser.fromUser(user, SessionUtils.getSessionId());
        SessionUtils.putUser(sessionUser);
        return sessionUser;
    }

    public void logout() {
        SecurityUtils.getSubject().logout();
    }

    public SessionUser isLogin() {
        SessionUser user = SessionUtils.getUser();
        if (user == null || !DataSpecialistConstants.SESSION_SOURCE.equalsIgnoreCase(StringUtils.defaultString(user.getSource()))) {
            return null;
        }
        return user;
    }
}
