package cn.cordys.crm.system.notice.sse;

import cn.cordys.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/sse")
@Tag(name = "消息通知-SSE")
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    /**
     * 客户端订阅 SSE 事件流
     *
     * @param kind     TENANT（默认，须带 tenantId）、PLATFORM、DATA_SPECIALIST（后两者不依赖租户）
     * @param tenantId 租户内用户必填；平台 / 数据专员可不传
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "客户端订阅 SSE 事件流")
    @CrossOrigin
    public Flux<?> subscribe(@RequestParam(defaultValue = "TENANT") String kind,
                             @RequestParam(required = false) String tenantId,
                             @RequestParam String userId,
                             @RequestParam String clientId,
                             HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        SsePrincipalKind k = SsePrincipalKind.fromQuery(kind);
        String tid = StringUtils.trimToNull(tenantId);
        if (k == SsePrincipalKind.TENANT && tid == null) {
            tid = StringUtils.trimToNull(TenantContext.getTenantId());
        }
        return sseService.addClient(k, tid, userId, clientId);
    }

    @GetMapping("/broadcast")
    @Operation(summary = "模拟向所有客户端广播事件-(测试使用)")
    public String broadcast(@RequestParam(defaultValue = "TENANT") String kind,
                            @RequestParam(required = false) String tenantId,
                            @RequestParam String userId,
                            @RequestParam String clientId,
                            @RequestParam String message) {
        SsePrincipalKind k = SsePrincipalKind.fromQuery(kind);
        String tid = StringUtils.trimToNull(tenantId);
        if (k == SsePrincipalKind.TENANT && tid == null) {
            tid = StringUtils.trimToNull(TenantContext.getTenantId());
        }
        sseService.sendToClient(k, tid, userId, clientId, "SYSTEM_HEARTBEAT: " + message);
        return "Broadcast: " + message;
    }

    @GetMapping("/close")
    @Operation(summary = "主动断开客户端连接")
    public void close(@RequestParam(defaultValue = "TENANT") String kind,
                      @RequestParam(required = false) String tenantId,
                      @RequestParam String userId,
                      @RequestParam String clientId) {
        SsePrincipalKind k = SsePrincipalKind.fromQuery(kind);
        String tid = StringUtils.trimToNull(tenantId);
        if (k == SsePrincipalKind.TENANT && tid == null) {
            tid = StringUtils.trimToNull(TenantContext.getTenantId());
        }
        sseService.removeClient(k, tid, userId, clientId);
    }
}
