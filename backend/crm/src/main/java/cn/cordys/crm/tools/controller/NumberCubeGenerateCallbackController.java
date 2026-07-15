package cn.cordys.crm.tools.controller;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.tools.dto.request.NumberCubeGenerateCallbackRequest;
import cn.cordys.crm.tools.dto.request.NumberCubePackCallbackRequest;
import cn.cordys.crm.tools.service.NumberCubeGenerateCallbackService;
import cn.cordys.crm.tools.service.NumberCubePackCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/number-cube")
@Tag(name = "内部-号码魔方")
public class NumberCubeGenerateCallbackController {

    @Resource
    private NumberCubeGenerateCallbackService numberCubeGenerateCallbackService;
    @Resource
    private NumberCubePackCallbackService numberCubePackCallbackService;

    @PostMapping("/generate/callback")
    @Operation(summary = "号码魔方生成任务回调")
    public void callback(@RequestBody NumberCubeGenerateCallbackRequest request, HttpServletRequest httpRequest) {
        validateLocalRequest(httpRequest);
        numberCubeGenerateCallbackService.handleCallback(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/pack/callback")
    @Operation(summary = "号码魔方打包任务回调")
    public void packCallback(@RequestBody NumberCubePackCallbackRequest request, HttpServletRequest httpRequest) {
        validateLocalRequest(httpRequest);
        numberCubePackCallbackService.handleCallback(request, httpRequest.getRemoteAddr());
    }

    private void validateLocalRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null) {
            return;
        }
        if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return;
        }
        throw new GenericException(Translator.get("number_cube_callback_invalid"));
    }
}
