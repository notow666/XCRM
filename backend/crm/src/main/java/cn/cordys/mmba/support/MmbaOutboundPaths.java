package cn.cordys.mmba.support;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * MMBA 出站类接口路径（影子库 active 时禁止）。
 */
public final class MmbaOutboundPaths {

    private static final Set<String> OUTBOUND_PATHS = Set.of(
            "/mmba/phone/dial",
            "/mmba/wx/friend/add",
            "/mmba/wx/message/send",
            "/mmba/sms/send",
            "/mmba/wx/moment/send",
            "/mmba/wx/friend/remark/modify",
            "/mmba/device/message/push",
            "/mmba/device/sync"
    );

    private MmbaOutboundPaths() {
        throw new AssertionError("工具类不应该被实例化");
    }

    public static boolean isOutboundRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = StringUtils.defaultString(request.getRequestURI());
        for (String path : OUTBOUND_PATHS) {
            if (uri.contains(path)) {
                return true;
            }
        }
        return false;
    }
}
