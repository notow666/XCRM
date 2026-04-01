package cn.cordys.platform.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.CodingUtils;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import cn.cordys.security.UserDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class PlatformAuthService {

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    public SessionUser login(String username, String password) {
        String sql = "SELECT id, username, password_hash, status FROM platform_user WHERE username = ? LIMIT 1";
        List<PlatformUserRow> rows = masterJdbcTemplate.query(sql, (rs, rowNum) -> {
            PlatformUserRow row = new PlatformUserRow();
            row.id = rs.getString("id");
            row.username = rs.getString("username");
            row.passwordHash = rs.getString("password_hash");
            row.status = rs.getString("status");
            return row;
        }, username);
        if (rows.isEmpty()) {
            recordLogin(username, "FAILED", "user not exists");
            throw new GenericException("账号或密码错误");
        }
        PlatformUserRow row = rows.get(0);
        if (!StringUtils.equalsIgnoreCase("ACTIVE", row.status)) {
            recordLogin(username, "FAILED", "user disabled");
            throw new GenericException("账号已禁用");
        }
        String encryptedPwd = CodingUtils.md5(password);
        if (!StringUtils.equals(row.passwordHash, encryptedPwd)) {
            recordLogin(username, "FAILED", "password error");
            throw new GenericException("账号或密码错误");
        }

        UserDTO user = new UserDTO();
        user.setId(row.id);
        user.setName(row.username);
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
        if (user == null || !StringUtils.equalsIgnoreCase("PLATFORM", user.getSource())) {
            return null;
        }
        return user;
    }

    private void recordLogin(String username, String result, String detail) {
        masterJdbcTemplate.update("INSERT INTO platform_login_log (id, username, result, detail, create_time) VALUES (?, ?, ?, ?, ?)",
                IDGenerator.nextStr(), username, result, detail, System.currentTimeMillis());
    }

    private static class PlatformUserRow {
        private String id;
        private String username;
        private String passwordHash;
        private String status;
    }
}
