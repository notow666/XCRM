package cn.cordys.platform.controller;

import cn.cordys.platform.dto.request.PlatformLoginRequest;
import cn.cordys.platform.service.PlatformAuthService;
import cn.cordys.security.SessionUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/auth")
@Tag(name = "管理中心-平台认证")
public class PlatformAuthController {

    @Resource
    private PlatformAuthService platformAuthService;

    @PostMapping("/login")
    @Operation(summary = "平台登录")
    public SessionUser login(@Valid @RequestBody PlatformLoginRequest request) {
        return platformAuthService.login(request.getUsername(), request.getPassword());
    }

    @GetMapping("/logout")
    @Operation(summary = "平台登出")
    public String logout() {
        platformAuthService.logout();
        return "logout success";
    }

    @GetMapping("/is-login")
    @Operation(summary = "平台是否登录")
    public SessionUser isLogin() {
        return platformAuthService.isLogin();
    }
}
