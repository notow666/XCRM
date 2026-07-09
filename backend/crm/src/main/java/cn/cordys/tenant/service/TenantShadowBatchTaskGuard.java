package cn.cordys.tenant.service;

import cn.cordys.context.RoutingContext;
import cn.cordys.context.RoutingPurpose;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.system.constants.ExportConstants;
import cn.cordys.crm.system.domain.ExportTask;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.platform.mapper.ExtTenantOpsTaskMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 切换前校验租户是否存在进行中的大批量任务 / 导出。
 */
@Service
public class TenantShadowBatchTaskGuard {

    @Resource
    private BaseMapper<ExportTask> exportTaskMapper;

    @Resource
    private ExtTenantOpsTaskMapper extTenantOpsTaskMapper;

    public boolean hasRunningBatchTasks(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return false;
        }
        if (hasRunningExportTasks(tenantId)) {
            return true;
        }
        Long runningOps = extTenantOpsTaskMapper.countRunningByTenantId(tenantId.trim());
        return runningOps != null && runningOps > 0;
    }

    private boolean hasRunningExportTasks(String tenantId) {
        String previousTenant = TenantContext.getTenantId();
        RoutingPurpose previousPurpose = RoutingContext.getPurpose();
        try {
            TenantContext.setTenantId(tenantId);
            RoutingContext.setPurpose(RoutingPurpose.PRODUCTION_MAINTAIN);
            LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExportTask::getStatus, ExportConstants.ExportStatus.PREPARED.name());
            var tasks = exportTaskMapper.selectListByLambda(wrapper);
            return tasks != null && !tasks.isEmpty();
        } finally {
            if (StringUtils.isBlank(previousTenant)) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
            if (previousPurpose == null) {
                RoutingContext.clear();
            } else {
                RoutingContext.setPurpose(previousPurpose);
            }
        }
    }
}
