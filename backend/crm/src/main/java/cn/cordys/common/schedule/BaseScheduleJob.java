package cn.cordys.common.schedule;

import cn.cordys.common.constants.MdcConstants;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

/**
 * 基础调度任务类，所有租户自定义 Quartz Job 应继承此类。
 *
 * <p>从 JobDataMap 或 JobKey 前缀恢复 tenantId，再执行业务逻辑。</p>
 */
@Slf4j
public abstract class BaseScheduleJob implements Job {

    protected String resourceId;

    protected String userId;

    protected String expression;

    @Override
    public void execute(JobExecutionContext context) {
        String previousTenantId = TenantContext.getTenantId();
        String previousMdcTraceId = MDC.get(MdcConstants.TRACE_ID_KEY);
        String previousMdcTenantId = MDC.get(MdcConstants.TENANT_ID_KEY);
        try {
            JobKey jobKey = context.getTrigger().getJobKey();
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            this.resourceId = jobDataMap.getString("resourceId");
            this.userId = jobDataMap.getString("userId");
            this.expression = jobDataMap.getString("expression");

            String tenantId = resolveTenantId(jobDataMap, jobKey);
            if (StringUtils.isNotBlank(tenantId)) {
                TenantContext.setTenantId(tenantId);
                MDC.put(MdcConstants.TRACE_ID_KEY, IDGenerator.nextStr());
                MDC.put(MdcConstants.TENANT_ID_KEY, tenantId);
            } else {
                TenantContext.clear();
                MDC.remove(MdcConstants.TRACE_ID_KEY);
                MDC.remove(MdcConstants.TENANT_ID_KEY);
            }
            businessExecute(context);
        } catch (Exception e) {
            log.error("执行多租户定时任务失败：{}", e.getMessage());
        } finally {
            if (StringUtils.isBlank(previousTenantId)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
            if (StringUtils.isBlank(previousMdcTraceId)) {
                MDC.remove(MdcConstants.TRACE_ID_KEY);
            } else {
                MDC.put(MdcConstants.TRACE_ID_KEY, previousMdcTraceId);
            }
            if (StringUtils.isBlank(previousMdcTenantId)) {
                MDC.remove(MdcConstants.TENANT_ID_KEY);
            } else {
                MDC.put(MdcConstants.TENANT_ID_KEY, previousMdcTenantId);
            }
        }
    }


    private String resolveTenantId(JobDataMap jobDataMap, JobKey jobKey) {
        String tenantId = jobDataMap.getString(ScheduleManager.TENANT_ID_KEY);
        if (StringUtils.isNotBlank(tenantId)) {
            return tenantId;
        }
        return TenantQuartzKeys.parseTenantId(jobKey);
    }

    protected abstract void businessExecute(JobExecutionContext context);
}


