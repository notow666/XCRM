package cn.cordys.crm.customer.constants;

import cn.cordys.crm.customer.job.CustomerDataCleanupExecuteJob;
import cn.cordys.crm.system.constants.ScheduleResourceType;

/**
 * 客户数据清理在 {@code schedule} 表中的固定标识（与 Flyway V1.8.3_2 种子数据一致）。
 */
public final class BuiltinCustomerDataCleanupSchedule {

    public static final String SCHEDULE_ID = "builtin_customer_data_cleanup";
    public static final String SCHEDULE_KEY = "customer_data_cleanup";
    public static final String DEFAULT_CRON = "0 30 12 * * ?";
    public static final String JOB_CLASS_NAME = CustomerDataCleanupExecuteJob.class.getName();
    public static final String RESOURCE_TYPE = ScheduleResourceType.BUILTIN_CUSTOMER_DATA_CLEANUP;
    public static final String SCHEDULE_NAME = "客户数据清理任务";

    private BuiltinCustomerDataCleanupSchedule() {
    }
}
