package cn.cordys.crm.customer.constants;

/**
 * 客户列表按筛选批量任务相关常量。
 */
public final class CustomerBatchConstants {

    private CustomerBatchConstants() {
    }

    /** SSE 载荷 type，与前端 {@code SSE_EVENT_CUSTOMER_BATCH_BY_CONDITION_DONE} 一致 */
    public static final String SSE_CUSTOMER_BATCH_BY_CONDITION_DONE = "CUSTOMER_BATCH_BY_CONDITION_DONE";
}
