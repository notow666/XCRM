package cn.cordys.mmba.dto;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.common.util.JSON;
import cn.cordys.mmba.MmbaBehaviorTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
public class MmbaAuditRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -8678428122164917085L;

    private int behaviorType;
    private String tenancyName;
    private List<ZzyData> data;
    private transient String rawPayload;
    private transient String streamId;
    private transient String streamConsumer;

    public MmbaAuditRequest copyWithData(List<ZzyData> newData) {
        MmbaAuditRequest request = new MmbaAuditRequest(behaviorType, tenancyName, newData, null, streamId, streamConsumer);
        request.setRawPayload(buildPayloadRaw(newData));
        return request;
    }

    public static MmbaAuditRequest generate(JsonNode json, Map<String, String> tenant) {
        String jsonString = JSON.toJSONString(json);
        MmbaAuditRequest mmbaAuditRequest = JSON.parseObject(jsonString, MmbaAuditRequest.class);
        if (!MmbaBehaviorTypes.isSupported(mmbaAuditRequest.getBehaviorType())){
            log.warn("[mmba-callback-queue] 忽略不支持的行为类型: {}", mmbaAuditRequest.getBehaviorType());
            return null;
        }
        mmbaAuditRequest.setRawPayload(json == null ? null : jsonString);
        List<ZzyData> sourceData = mmbaAuditRequest.getData() == null ? Collections.emptyList() : mmbaAuditRequest.getData();
        List<ZzyData> _new = new ArrayList<>(sourceData.size());
        List<ZzyData> invalid = new ArrayList<>();
        for (int i = 0; i < sourceData.size(); i++) {
            ZzyData zzy = sourceData.get(i);
            // 2026-07-13：过滤无法匹配客户的微信聊天审计数据，避免无效记录进入后续处理链路并增加数据库压力。
            if (MmbaBehaviorTypes.WX_CHAT_AUDIT == mmbaAuditRequest.getBehaviorType()
                    && !StringUtils.hasText(zzy.getFriendPhone())) {
                continue;
            }
            if (!StringUtils.hasText(zzy.getDeptIdPath())) {
                invalid.add(zzy);
                continue;
            }
            String[] orgPath = zzy.getDeptIdPath().split("/");
            if (orgPath.length <= 1) {
                invalid.add(zzy);
                continue;
            }
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
        if (CollectionUtils.isEmpty(_new)) {
            log.warn("[mmba-callback-queue] 忽略无效回调（无有效 data） behaviorType={} tenancyName={}",
                    mmbaAuditRequest.getBehaviorType(), mmbaAuditRequest.getTenancyName());
            return null;
        }else{
            mmbaAuditRequest.setData(_new);
        }
        if(!CollectionUtils.isEmpty(invalid)){
            log.debug("[mmba-callback-queue] 无效审计数据(无TenantId) count={}", invalid.size());
        }
        return mmbaAuditRequest;
    }

    public String buildPayloadRaw(List<ZzyData> selectedData) {
        if (!StringUtils.hasText(rawPayload)) {
            return JSON.toJSONString(new MmbaAuditRequest(behaviorType, tenancyName, selectedData, null, null, null));
        }
        JsonNode root = JSON.parseObject(rawPayload, JsonNode.class);
        if (!(root instanceof ObjectNode)) {
            return rawPayload;
        }
        ObjectNode objectNode = (ObjectNode) root;
        ArrayNode arrayNode = objectNode.putArray("data");
        if (selectedData != null) {
            for (ZzyData zzyData : selectedData) {
                arrayNode.add(JSON.parseObject(JSON.toJSONString(zzyData), JsonNode.class));
            }
        }
        return JSON.toJSONString(objectNode);
    }
}
