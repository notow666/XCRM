package cn.cordys.mmba.dto;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.common.util.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
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
    private transient String rawPayload;

    public MmbaAuditRequest withSingleData(ZzyData singleData) {
        return copyWithData(Collections.singletonList(singleData));
    }

    public MmbaAuditRequest copyWithData(List<ZzyData> newData) {
        MmbaAuditRequest request = new MmbaAuditRequest(behaviorType, tenancyName, newData, null);
        request.setRawPayload(buildPayloadRaw(newData));
        return request;
    }

    public static MmbaAuditRequest generate(JsonNode json, Map<String, String> tenant) {
        MmbaAuditRequest mmbaAuditRequest = JSON.parseObject(JSON.toJSONString(json), MmbaAuditRequest.class);
        mmbaAuditRequest.setRawPayload(json == null ? null : JSON.toJSONString(json));
        List<ZzyData> sourceData = mmbaAuditRequest.getData() == null ? Collections.emptyList() : mmbaAuditRequest.getData();
        JsonNode rawDataNode = json == null ? null : json.get("data");
        List<ZzyData> _new = new ArrayList<>(sourceData.size());
        List<ZzyData> invalid = new ArrayList<>();
        for (int i = 0; i < sourceData.size(); i++) {
            ZzyData zzy = sourceData.get(i);
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
                zzy.setRawPayload(extractRawData(rawDataNode, i, zzy));

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

    public String buildPayloadRaw(List<ZzyData> selectedData) {
        if (!StringUtils.hasText(rawPayload)) {
            return JSON.toJSONString(new MmbaAuditRequest(behaviorType, tenancyName, selectedData, null));
        }
        JsonNode root = JSON.parseObject(rawPayload, JsonNode.class);
        if (!(root instanceof ObjectNode)) {
            return rawPayload;
        }
        ObjectNode objectNode = (ObjectNode) root;
        ArrayNode arrayNode = objectNode.putArray("data");
        if (selectedData != null) {
            for (ZzyData zzyData : selectedData) {
                if (StringUtils.hasText(zzyData.getRawPayload())) {
                    arrayNode.add(JSON.parseObject(zzyData.getRawPayload(), JsonNode.class));
                } else {
                    arrayNode.add(JSON.parseObject(JSON.toJSONString(zzyData), JsonNode.class));
                }
            }
        }
        return JSON.toJSONString(objectNode);
    }

    public void hydrateDataRawPayload() {
        JsonNode root = StringUtils.hasText(rawPayload) ? JSON.parseObject(rawPayload, JsonNode.class) : null;
        JsonNode rawDataNode = root == null ? null : root.get("data");
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            data.get(i).setRawPayload(extractRawData(rawDataNode, i, data.get(i)));
        }
    }

    private static String extractRawData(JsonNode rawDataNode, int index, ZzyData zzy) {
        if (rawDataNode != null && rawDataNode.isArray() && rawDataNode.size() > index) {
            JsonNode item = rawDataNode.get(index);
            if (item != null) {
                return JSON.toJSONString(item);
            }
        }
        return JSON.toJSONString(zzy);
    }
}
