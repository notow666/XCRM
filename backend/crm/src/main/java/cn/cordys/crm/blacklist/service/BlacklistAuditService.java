package cn.cordys.crm.blacklist.service;

import cn.cordys.crm.blacklist.domain.Blacklist;
import java.util.ArrayList;
import java.util.List;
import cn.cordys.context.OrganizationContext;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.dto.MessageDetailDTO;
import cn.cordys.crm.system.notice.common.NoticeModel;
import cn.cordys.crm.system.notice.common.Receiver;
import cn.cordys.crm.system.notice.sender.insite.InSiteNoticeSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
public class BlacklistAuditService {
    @Resource private LogService logService;
    @Resource private BaseMapper<User> users;
    @Resource private InSiteNoticeSender noticeSender;
    @Resource private PlatformTransactionManager transactionManager;

    /** 日志随业务提交；提交成功后调用现有站内通知发送器，仅通知操作人。 */
    public void record(String operationId, String userId, String type, String verb, Map<String, Object> result) {
        long now = System.currentTimeMillis();
        TenantContext.requireTenantId();
        User user = users.selectByPrimaryKey(userId);
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
                .format(Instant.ofEpochMilli(now));
        String text = time + "，" + (user == null ? userId : user.getName()) + "成功" + verb + "黑名单数据 "
                + result.get("successCount") + " 条。";
        int failCount = ((Number) result.getOrDefault("failCount", 0)).intValue();
        if (((Number) result.get("successCount")).intValue() == 0 && failCount > 0) {
            text = time + "，" + (user == null ? userId : user.getName()) + "导入黑名单数据失败，成功 0 条。";
        }
        if (failCount > 0) {
            text += "失败 " + failCount + " 行，可在本次导入结果中下载错误文件。";
        }
        List<LogDTO> entries = new ArrayList<>();
        if ("DELETE".equals(type)) {
            if (result.get("deletedRecords") instanceof List<?> records) {
                for (Object record : records) {
                    if (record instanceof Blacklist row) {
                        entries.add(createLog(userId, type, row.getId(), row.getMobile(),
                                "黑名单号码：" + row.getMobile()));
                    }
                }
            }
        } else if ("ADD".equals(type)) {
            String mobile = (String) result.get("mobile");
            entries.add(createLog(userId, type, (String) result.get("resourceId"), mobile,
                    "黑名单号码：" + mobile));
        } else {
            String fileName = "EXPORT".equals(type) ? "黑名单.xlsx" : (String) result.get("fileName");
            entries.add(createLog(userId, type, operationId, fileName,
                    "文件：" + fileName + "，成功 " + result.get("successCount") + " 条，失败 " + failCount + " 条。"));
        }
        logService.batchAddSync(entries);

        MessageDetailDTO detail = new MessageDetailDTO();
        detail.setId(operationId);
        detail.setOrganizationId(OrganizationContext.getOrganizationId());
        detail.setTaskType(NotificationConstants.Module.CUSTOMER);
        NoticeModel model = NoticeModel.builder()
                .operator(userId)
                .event("BLACKLIST_" + type)
                .paramMap(Map.of("resourceId", operationId, "name", "黑名单"))
                .receivers(java.util.List.of(new Receiver(userId, NotificationConstants.Type.SYSTEM_NOTICE.name())))
                .excludeSelf(false)
                .build();
        String content = text;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 提交回调不能继续使用已提交的业务事务写通知。
                    TransactionTemplate notificationTransaction = new TransactionTemplate(transactionManager);
                    notificationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    notificationTransaction.executeWithoutResult(status ->
                            noticeSender.sendAnnouncement(detail, model, content, "黑名单" + verb + "结果"));
                } catch (Exception e) {
                    log.warn("黑名单操作已完成，站内通知发送失败 operationId={}", operationId, e);
                }
            }
        });
    }
    private LogDTO createLog(String userId, String type, String resourceId, String resourceName, String detail) {
        LogDTO entry = new LogDTO();
        entry.setOrganizationId(OrganizationContext.getOrganizationId());
        entry.setType(type);
        entry.setModule("CUSTOMER_BLACKLIST");
        entry.setResourceId(resourceId);
        entry.setResourceName(resourceName);
        entry.setDetail(detail);
        entry.setCreateUser(userId);
        entry.setMethod("POST");
        return entry;
    }
}
