package cn.cordys.crm.follow.service;

import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.context.TenantTaskExecutor;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.util.Translator;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.follow.constants.FollowUpPlanRemindStatus;
import cn.cordys.crm.follow.domain.FollowUpPlan;
import cn.cordys.crm.follow.dto.FollowUpPlanReminderMessage;
import cn.cordys.crm.follow.mapper.ExtFollowUpPlanMapper;
import cn.cordys.crm.system.constants.NotificationConstants;
import cn.cordys.crm.system.notice.CommonNoticeSendService;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FollowUpPlanReminderService implements SmartLifecycle {

    private static final String READY_QUEUE = "follow_plan_remind:ready";
    private static final int COMPENSATION_BATCH_SIZE = 500;

    @Resource
    private Redisson redisson;
    @Resource
    private BaseMapper<FollowUpPlan> followUpPlanMapper;
    @Resource
    private ExtFollowUpPlanMapper extFollowUpPlanMapper;
    @Resource
    private CommonNoticeSendService commonNoticeSendService;
    @Resource
    private BaseService baseService;
    @Resource
    private TenantTaskExecutor tenantTaskExecutor;

    private final Object lifecycleLock = new Object();
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "follow-plan-reminder-consumer");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = false;
    private Future<?> consumerFuture;

    public void validateRemindTime(Long remindTime, Long estimatedTime) {
        if (remindTime != null && remindTime <= System.currentTimeMillis()) {
            throw new GenericException(Translator.get("follow_plan.remind_time.future"));
        }
        if (remindTime != null && estimatedTime != null && remindTime >= estimatedTime) {
            throw new GenericException(Translator.get("follow_plan.remind_time.before_estimated_time"));
        }
    }

    public void initReminder(FollowUpPlan followUpPlan) {
        if (followUpPlan.getRemindTime() == null) {
            return;
        }
        followUpPlan.setRemindStatus(FollowUpPlanRemindStatus.PENDING.name());
    }

    public void enqueueAfterCommit(FollowUpPlan followUpPlan) {
        if (followUpPlan.getRemindTime() == null) {
            return;
        }
        String tenantId = TenantContext.requireTenantId();
        FollowUpPlanReminderMessage message = new FollowUpPlanReminderMessage(
                tenantId,
                followUpPlan.getOrganizationId(),
                followUpPlan.getId(),
                followUpPlan.getRemindTime()
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(message);
                }
            });
        } else {
            enqueue(message);
        }
    }

    public void cancelPendingReminder(String planId) {
        if (StringUtils.isBlank(planId)) {
            return;
        }
        extFollowUpPlanMapper.cancelPendingReminder(planId, System.currentTimeMillis());
    }

    public void compensateCurrentTenant() {
        long now = System.currentTimeMillis();
        List<FollowUpPlan> plans = extFollowUpPlanMapper.selectPendingRemindPlans(now, COMPENSATION_BATCH_SIZE);
        for (FollowUpPlan plan : plans) {
            FollowUpPlanReminderMessage message = new FollowUpPlanReminderMessage(
                    TenantContext.requireTenantId(),
                    plan.getOrganizationId(),
                    plan.getId(),
                    plan.getRemindTime()
            );
            processCurrentTenantReminder(message);
        }
    }

    public void compensateAllTenants() {
        tenantTaskExecutor.runForEachEnabledTenant("FollowUpPlanReminderService.compensateAllTenants",
                tenantId -> compensateCurrentTenant());
    }

    private void enqueue(FollowUpPlanReminderMessage message) {
        try {
            long delay = Math.max(0, message.getRemindTime() - System.currentTimeMillis());
            delayedQueue().offer(message, delay, TimeUnit.MILLISECONDS);
            log.info("跟进计划提醒已入队 tenantId={}, organizationId={}, planId={}, remindTime={}",
                    message.getTenantId(), message.getOrganizationId(), message.getPlanId(), message.getRemindTime());
        } catch (Exception e) {
            log.error("跟进计划提醒入队失败 tenantId={}, organizationId={}, planId={}",
                    message.getTenantId(), message.getOrganizationId(), message.getPlanId(), e);
        }
    }

    private RBlockingQueue<FollowUpPlanReminderMessage> readyQueue() {
        return redisson.getBlockingQueue(READY_QUEUE);
    }

    private RDelayedQueue<FollowUpPlanReminderMessage> delayedQueue() {
        return redisson.getDelayedQueue(readyQueue());
    }

    private void consumeLoop() {
        log.info("跟进计划提醒消费启动");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                FollowUpPlanReminderMessage message = readyQueue().take();
                processReminder(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("跟进计划提醒消费异常", e);
            }
        }
        log.info("跟进计划提醒消费停止");
    }

    private void processReminder(FollowUpPlanReminderMessage message) {
        if (message == null || StringUtils.isBlank(message.getTenantId())) {
            return;
        }
        try {
            TenantContext.setTenantId(message.getTenantId());
            processCurrentTenantReminder(message);
        } finally {
            TenantContext.clear();
        }
    }

    private synchronized void processCurrentTenantReminder(FollowUpPlanReminderMessage message) {
        FollowUpPlan plan = followUpPlanMapper.selectByPrimaryKey(message.getPlanId());
        if (plan == null) {
            log.info("跟进计划提醒丢弃，计划不存在 tenantId={}, planId={}", message.getTenantId(), message.getPlanId());
            return;
        }
        if (!isPendingReminder(plan)) {
            return;
        }
        if (StringUtils.isBlank(plan.getProcessor())) {
            cancelPendingReminder(plan.getId());
            return;
        }
        if (plan.getRemindTime() == null || plan.getRemindTime() > System.currentTimeMillis()) {
            return;
        }

        if (!sendReminderNotice(plan)) {
            cancelPendingReminder(plan.getId());
            return;
        }
        int updated = extFollowUpPlanMapper.markReminderSent(plan.getId(), plan.getOrganizationId(), System.currentTimeMillis());
        if (updated > 0) {
            log.info("跟进计划提醒已发送 tenantId={}, organizationId={}, planId={}, processor={}",
                    TenantContext.getTenantId(), plan.getOrganizationId(), plan.getId(), plan.getProcessor());
        }
    }

    private boolean isPendingReminder(FollowUpPlan plan) {
        return FollowUpPlanRemindStatus.PENDING.name().equals(plan.getRemindStatus());
    }

    private boolean sendReminderNotice(FollowUpPlan plan) {
        String module = resolveModule(plan);
        String event = resolveEvent(plan);
        String resourceName = resolveResourceName(plan);
        if (StringUtils.isBlank(module) || StringUtils.isBlank(event) || StringUtils.isBlank(resourceName)) {
            log.warn("跟进计划提醒缺少通知上下文 planId={}, module={}, event={}, resourceName={}",
                    plan.getId(), module, event, resourceName);
            return false;
        }
        String operatorId = StringUtils.defaultIfBlank(plan.getCreateUser(), InternalUser.ADMIN.getValue());
        commonNoticeSendService.sendNotice(module, event, resourceName, operatorId, plan.getOrganizationId(),
                List.of(plan.getProcessor()), false);
        return true;
    }

    private String resolveModule(FollowUpPlan plan) {
        if (StringUtils.isNotBlank(plan.getOpportunityId())) {
            return NotificationConstants.Module.OPPORTUNITY;
        }
        if (StringUtils.isNotBlank(plan.getClueId())) {
            return NotificationConstants.Module.CLUE;
        }
        if (StringUtils.isNotBlank(plan.getCustomerId())) {
            return NotificationConstants.Module.CUSTOMER;
        }
        return null;
    }

    private String resolveEvent(FollowUpPlan plan) {
        if (StringUtils.isNotBlank(plan.getOpportunityId())) {
            return NotificationConstants.Event.BUSINESS_FOLLOW_UP_PLAN_REMIND;
        }
        if (StringUtils.isNotBlank(plan.getClueId())) {
            return NotificationConstants.Event.CLUE_FOLLOW_UP_PLAN_REMIND;
        }
        if (StringUtils.isNotBlank(plan.getCustomerId())) {
            return NotificationConstants.Event.CUSTOMER_FOLLOW_UP_PLAN_REMIND;
        }
        return null;
    }

    private String resolveResourceName(FollowUpPlan plan) {
        if (StringUtils.isNotBlank(plan.getOpportunityId())) {
            return baseService.getOpportunityMap(List.of(plan.getOpportunityId())).get(plan.getOpportunityId());
        }
        if (StringUtils.isNotBlank(plan.getClueId())) {
            return baseService.getClueMap(List.of(plan.getClueId())).get(plan.getClueId());
        }
        if (StringUtils.isNotBlank(plan.getCustomerName())) {
            return plan.getCustomerName();
        }
        if (StringUtils.isNotBlank(plan.getCustomerId())) {
            return baseService.getCustomerMap(List.of(plan.getCustomerId())).get(plan.getCustomerId());
        }
        return null;
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            running = true;
            consumerFuture = consumerExecutor.submit(this::consumeLoop);
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        synchronized (lifecycleLock) {
            running = false;
            if (consumerFuture != null) {
                consumerFuture.cancel(true);
            }
            consumerExecutor.shutdownNow();
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
