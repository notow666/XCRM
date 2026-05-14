package cn.cordys.dataspecialist.controller;

import cn.cordys.dataspecialist.dto.DataSpecialistLoginRequest;
import cn.cordys.dataspecialist.service.DataSpecialistAuthService;
import cn.cordys.security.SessionUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/data-specialist/auth")
@Tag(name = "数据专员-认证")
public class DataSpecialistAuthController {

    @Resource
    private DataSpecialistAuthService dataSpecialistAuthService;

    @PostMapping("/login")
    @Operation(summary = "数据专员登录")
    public SessionUser login(@Valid @RequestBody DataSpecialistLoginRequest request) {
        return dataSpecialistAuthService.login(request);
    }

    @GetMapping("/logout")
    @Operation(summary = "数据专员登出")
    public String logout() {
        dataSpecialistAuthService.logout();
        return "logout success";
    }

    @GetMapping("/is-login")
    @Operation(summary = "数据专员是否已登录")
    public SessionUser isLogin() {
        return dataSpecialistAuthService.isLogin();
    }
}
