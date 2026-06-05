package cn.cordys.crm.system.notice.dto;

import cn.cordys.common.dto.RedisMessage;
import lombok.Data;

@Data
public class NoticeRedisMessage extends RedisMessage {
    /**
     * redis 发布订阅消息补充
     */
    private String noticeType;

    /**
     * 事件所属租户ID
     */
    private String tenantId;

    /**
     * 平台广播范围；非空时表示 {@link cn.cordys.common.constants.PlatformSseEventType#BROADCAST_SCOPE_TENANT_AND_DS} 等平台级广播。
     */
    private String broadcastScope;

}
