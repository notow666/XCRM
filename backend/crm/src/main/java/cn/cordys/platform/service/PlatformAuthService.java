package cn.cordys.platform.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.CodingUtils;
import cn.cordys.platform.domain.PlatformUser;
import cn.cordys.platform.mapper.ExtPlatformUserMapper;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.UserDTO;
import jakarta.annotation.Resource;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;

@Service
public class PlatformAuthService {

    @Resource
    private ExtPlatformUserMapper extPlatformUserMapper;

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    public SessionUser login(String username, String password) {
        PlatformUser userRow = extPlatformUserMapper.selectByUsername(username);
        if (userRow == null) {
            recordLogin(username, "FAILED", "user not exists");
            throw new GenericException("账号或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(userRow.getStatus())) {
            recordLogin(username, "FAILED", "user disabled");
            throw new GenericException("账号已禁用");
        }
        String encryptedPwd = CodingUtils.md5(password);
        if (!Objects.equals(userRow.getPasswordHash(), encryptedPwd)) {
            recordLogin(username, "FAILED", "password error");
            throw new GenericException("账号或密码错误");
        }

        UserDTO user = new UserDTO();
        user.setId(userRow.getId());
        user.setName(userRow.getUsername());
        user.setSource("PLATFORM");
        user.setEnable(true);
        user.setTenantId("---");
        user.setPermissionIds(Collections.singleton("PLATFORM_ADMIN:READ"));
        user.setOrganizationIds(Collections.emptySet());
        user.setTenantIds(Collections.emptySet());

        SessionUser sessionUser = SessionUser.fromUser(user, SessionUtils.getSessionId());
        SessionUtils.putUser(sessionUser);
        recordLogin(username, "SUCCESS", "");
        return sessionUser;
    }

    public void logout() {
        SecurityUtils.getSubject().logout();
    }

    public SessionUser isLogin() {
        SessionUser user = SessionUtils.getUser();
        if (user == null || !"PLATFORM".equalsIgnoreCase(user.getSource())) {
            return null;
        }
        return user;
    }

    private void recordLogin(String username, String result, String detail) {
        masterJdbcTemplate.update("INSERT INTO platform_login_log (id, username, result, detail, create_time) VALUES (?, ?, ?, ?, ?)",
                IDGenerator.nextStr(), username, result, detail, System.currentTimeMillis());
    }

}
