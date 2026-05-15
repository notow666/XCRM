package cn.cordys.common.security.realm;

import cn.cordys.common.constants.LoginAuthenticateConstants;
import cn.cordys.common.security.ShiroSessionAttributes;
import cn.cordys.common.util.CodingUtils;
import cn.cordys.dataspecialist.DataSpecialistConstants;
import cn.cordys.dataspecialist.domain.DataSpecialist;
import cn.cordys.dataspecialist.mapper.ExtDataSpecialistMapper;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.UserDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.Collections;
import java.util.Objects;

import cn.cordys.common.constants.LoginAuthenticateConstants.LoginAuthenticateType;

/**
 * 数据专员 Shiro Realm。
 */
public class DataSpecialistRealm extends AuthorizingRealm {

    @Resource
    private ExtDataSpecialistMapper extDataSpecialistMapper;

    @Override
    public String getName() {
        return LoginAuthenticateType.DATA_SPECIALIST.name();
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        if (!(token instanceof UsernamePasswordToken)) {
            return false;
        }
        return LoginAuthenticateType.DATA_SPECIALIST.name().equals(getAuthenticateFromSession());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return null;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        UsernamePasswordToken token = (UsernamePasswordToken) authenticationToken;
        String username = StringUtils.trimToEmpty(token.getUsername());
        String password = String.valueOf(token.getPassword());

        DataSpecialist row = extDataSpecialistMapper.selectByUsername(username);
        if (row == null) {
            throw new UnknownAccountException("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(row.getEnabled())) {
            throw new DisabledAccountException("账号已禁用");
        }
        String encryptedPwd = CodingUtils.md5(password);
        if (!Objects.equals(row.getPasswordHash(), encryptedPwd)) {
            throw new IncorrectCredentialsException("账号或密码错误");
        }

        UserDTO user = new UserDTO();
        user.setId(row.getId());
        user.setName(StringUtils.isNotBlank(row.getSpecialistName()) ? row.getSpecialistName() : row.getUsername());
        user.setEmail(row.getUsername() + "@data-specialist.local");
        user.setSource(LoginAuthenticateType.DATA_SPECIALIST.name());
        user.setEnable(true);
        user.setTenantId("---");
        user.setPermissionIds(Collections.singleton(DataSpecialistConstants.PERMISSION_POOL_IMPORT));
        user.setOrganizationIds(Collections.emptySet());
        user.setTenantIds(Collections.emptySet());

        Session session = SecurityUtils.getSubject().getSession();
        SessionUser sessionUser = SessionUser.fromUser(user, (String) session.getId());
        SessionUtils.putUser(sessionUser);
        return new SimpleAuthenticationInfo(user.getId(), password, getName());
    }

    private static String getAuthenticateFromSession() {
        Session session = SecurityUtils.getSubject().getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ShiroSessionAttributes.AUTHENTICATE);
        return value == null ? null : String.valueOf(value);
    }
}
