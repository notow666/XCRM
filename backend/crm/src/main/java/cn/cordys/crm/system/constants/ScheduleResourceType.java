package cn.cordys.crm.system.constants;

/**
 * {@link cn.cordys.crm.system.domain.Schedule#resourceType} 取值。
 */
public final class ScheduleResourceType {

    /** 内置：客户数据清理（{@link cn.cordys.crm.customer.job.CustomerDataCleanupExecuteJob}） */
    public static final String BUILTIN_CUSTOMER_DATA_CLEANUP = "BUILTIN_CUSTOMER_DATA_CLEANUP";

    /** 内置：客户定时删除（{@link cn.cordys.crm.customer.job.CustomerAutoDeleteExecuteJob}） */
    public static final String BUILTIN_CUSTOMER_AUTO_DELETE = "BUILTIN_CUSTOMER_AUTO_DELETE";

    /** 租户自定义定时任务 */
    public static final String CUSTOM = "CUSTOM";

    private ScheduleResourceType() {
    }
}
