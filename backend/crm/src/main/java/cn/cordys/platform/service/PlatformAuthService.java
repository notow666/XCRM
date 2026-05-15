package cn.cordys.platform.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.security.ShiroSessionAttributes;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.security.SessionUser;
import cn.cordys.security.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import cn.cordys.common.constants.LoginAuthenticateConstants.LoginAuthenticateType;

@Service
public class PlatformAuthService {

    @Resource
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;

    public SessionUser login(String username, String password) {
        String trimmedUsername = StringUtils.trim(username);
        Subject subject = SecurityUtils.getSubject();
        subject.getSession().setAttribute(ShiroSessionAttributes.AUTHENTICATE, LoginAuthenticateType.PLATFORM.name());
        try {
            subject.login(new UsernamePasswordToken(trimmedUsername, password));
            if (!subject.isAuthenticated()) {
                recordLogin(trimmedUsername, "FAILED", "not authenticated");
                throw new GenericException("账号或密码错误");
            }
            SessionUser sessionUser = SessionUtils.getUser();
            recordLogin(trimmedUsername, "SUCCESS", "");
            return sessionUser;
        } catch (UnknownAccountException | IncorrectCredentialsException e) {
            recordLogin(trimmedUsername, "FAILED", "password error");
            throw new GenericException("账号或密码错误");
        } catch (DisabledAccountException e) {
            recordLogin(trimmedUsername, "FAILED", "user disabled");
            throw new GenericException("账号已禁用");
        } catch (AuthenticationException e) {
            recordLogin(trimmedUsername, "FAILED", StringUtils.defaultString(e.getMessage()));
            throw new GenericException("账号或密码错误");
        }
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
        masterJdbcTemplate.update(
                "INSERT INTO platform_login_log (id, username, result, detail, create_time) VALUES (?, ?, ?, ?, ?)",
                IDGenerator.nextStr(), username, result, detail, System.currentTimeMillis());
    }
}
