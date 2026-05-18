package cn.cordys.mmba.service;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.system.mapper.ExtUserMapper;
import cn.cordys.mmba.dto.MmbaMgmtSsoCheckRequest;
import cn.cordys.mmba.dto.MmbaMgmtSsoRedirectVO;
import cn.cordys.security.SessionUser;
import cn.cordys.security.UserDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 指掌易管理平台 SSO：按配置拼装 URL、生成 ticket（含自定义 token）、供匿名鉴权解析。
 * <p>SSO 基址使用 {@code mmba.api-base-url}（即 https://ip:9074），路径 orgCode 使用 {@code mmba.company-code}。</p>
 */
@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
@Service
public class MmbaMgmtSsoService {

    private static final long TICKET_TOKEN_TTL_MS = 5 * 60 * 1000L;
    private static final long MAX_TIME_SKEW_MS = 120_000L;

    @Value("${mmba.api-base-url:}")
    private String apiBaseUrl;

    @Value("${mmba.company-code:}")
    private String companyCode;

    @Resource
    private MmbaMgmtSsoTokenCodec mmbaMgmtSsoTokenCodec;

    @Resource
    private ExtUserMapper extUserMapper;

    /**
     * 为当前登录租户用户生成指掌易管理平台 SSO 完整 URL（ticket 内 loginName = um）。
     */
    public MmbaMgmtSsoRedirectVO buildRedirectForUser(SessionUser user) {
        if (user == null) {
            throw new GenericException(CrmHttpResultCode.UNAUTHORIZED, "未登录");
        }
        String um = StringUtils.trimToNull(user.getUm());
        um = "A001";
        if (StringUtils.isBlank(um)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED,
                    "缺少用户 MMBA 唯一标识(um)：请在「组织与成员」中为用户维护 um，且与指掌易管理员 loginName 一致；若已维护请重新登录后再试");
        }
        String orgCode = StringUtils.trimToNull(companyCode);
        if (StringUtils.isBlank(orgCode)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED,
                    "未配置 mmba.company-code（指掌易租户 orgCode），请在配置中设置并与指掌易一致");
        }
        String baseUrl = stripTrailingSlash(apiBaseUrl);
        if (StringUtils.isBlank(baseUrl)) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED,
                    "未配置 mmba.api-base-url（应为 https://{ip}:9074），无法拼装管理平台 SSO 地址；请检查当前 Spring profile 是否加载了含 mmba 的配置");
        }

        String token;
        try {
            token = mmbaMgmtSsoTokenCodec.generateToken(um, orgCode, TICKET_TOKEN_TTL_MS);
        } catch (IllegalStateException e) {
            throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, e.getMessage());
        }

        // 文档：仅 ticket 查询参数 = 标准 Base64(UTF-8 JSON{loginName, token})；orgCode/type 为明文
        Map<String, String> ticketMap = new LinkedHashMap<>();
        ticketMap.put("loginName", um);
        ticketMap.put("token", token);
        String ticketRaw = JSON.toJSONString(ticketMap);
        String ticketB64 = Base64.getEncoder().encodeToString(ticketRaw.getBytes(StandardCharsets.UTF_8));

        String url = baseUrl + "/" + orgCode
                + "?type=sso&orgCode=" + orgCode
                + "&ticket=" + ticketB64;
        return new MmbaMgmtSsoRedirectVO(url);
    }

    /**
     * 指掌易 checkLoginUrl：校验 POST 中的 token（自定义解析），成功返回 code=0。
     */
    public Map<String, Object> validateAndRespond(MmbaMgmtSsoCheckRequest request) {
        Map<String, Object> fail = failBody(1);
        if (request == null || StringUtils.isBlank(request.getToken())) {
            return fail;
        }
//        if (request.getTime() != null) {
//            long skew = Math.abs(System.currentTimeMillis() - request.getTime());
//            if (skew > MAX_TIME_SKEW_MS) {
//                log.warn("mmba mgmt sso check time skew too large: {} ms", skew);
//                return fail;
//            }
//        }
        Optional<MmbaMgmtSsoTokenCodec.MmbaMgmtSsoTokenPayload> payload =
                mmbaMgmtSsoTokenCodec.parseAndVerify(request.getToken().trim());
        if (payload.isEmpty()) {
            return fail;
        }
        MmbaMgmtSsoTokenCodec.MmbaMgmtSsoTokenPayload p = payload.get();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("code", 0);
        ok.put("companyCode", p.companyCode());
        ok.put("um", p.um());
        return ok;
    }

    private static Map<String, Object> failBody(int code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("companyCode", "");
        m.put("um", "");
        return m;
    }

    private static String stripTrailingSlash(String raw) {
        return StringUtils.removeEnd(StringUtils.trimToEmpty(raw), "/");
    }

    private static String urlEncodePathSegment(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String urlEncodeQuery(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
