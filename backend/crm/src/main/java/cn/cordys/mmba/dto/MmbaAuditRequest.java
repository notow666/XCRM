package cn.cordys.mmba.dto;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.common.util.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MmbaAuditRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -8678428122164917085L;

    private static final Logger logger = LoggerFactory.getLogger(LogModule.PLATFORM_TENANT_CENTER);

    private int behaviorType;
    private String tenancyName;
    private List<ZzyData> data;

    public static MmbaAuditRequest generate(JsonNode json, Map<String, String> tenant) {
        MmbaAuditRequest mmbaAuditRequest = JSON.parseObject(JSON.toJSONString(json), MmbaAuditRequest.class);
        List<ZzyData> _new = new ArrayList<>(mmbaAuditRequest.getData().size());
        List<ZzyData> invalid = new ArrayList<>();
        for (ZzyData zzy : mmbaAuditRequest.getData()) {
            String[] orgPath = zzy.getDeptIdPath().split("/");
            if(tenant.containsKey(orgPath[1])) {
                zzy.setTenantId(tenant.get(orgPath[1]));

                zzy.setBehaviorType(mmbaAuditRequest.getBehaviorType());
                zzy.setTenancyName(mmbaAuditRequest.getTenancyName());
                // 通过@分割字符串
                String[] split = zzy.getOrgNames().split("@");
                zzy.setCompany(split.length >= 2 ? split[1] : "");
                zzy.setRegion(split.length >= 3 ? split[2] : "");
                zzy.setDepartment(split.length >= 4 ? split[3] : "");

                _new.add(zzy);
            }
            else {
                invalid.add(zzy);
            }
        }
        mmbaAuditRequest.setData(_new);
        if(!CollectionUtils.isEmpty(invalid)){
            logger.error(LogModule.TENANT_MARKER, "[TENANT_CENTER] 无效mmba审计数据(无TenantId) => [{}]", JSON.toJSONString(invalid));
        }
        return mmbaAuditRequest;
    }
}
