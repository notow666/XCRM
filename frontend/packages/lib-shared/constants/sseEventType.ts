/** 与后端 {@code MmbaConstants.SSE_EVENT_DEVICE_SYNC} 一致 */
export const SSE_EVENT_MMBA_DEVICE_SYNC = 'MMBA_DEVICE_SYNC';

/** 设备同步 SSE 到达后，通知设备管理页切换「同步」为「刷新」 */
export const MMBA_DEVICE_SYNC_DOM_EVENT = 'mmba-device-sync-sse';

/** 与后端 {@code PoolCustomerBatchConstants.SSE_POOL_BATCH_BY_CONDITION_DONE} 一致 */
export const SSE_EVENT_POOL_BATCH_BY_CONDITION_DONE = 'POOL_BATCH_BY_CONDITION_DONE';

/** 公海按筛选批量任务完成 SSE 到达后，通知公海池列表刷新 */
export const POOL_BATCH_BY_CONDITION_DOM_EVENT = 'pool-batch-by-condition-sse';

/** 与后端 {@code CustomerBatchConstants.SSE_CUSTOMER_BATCH_BY_CONDITION_DONE} 一致 */
export const SSE_EVENT_CUSTOMER_BATCH_BY_CONDITION_DONE = 'CUSTOMER_BATCH_BY_CONDITION_DONE';

/** 客户列表按筛选批量任务完成 SSE 到达后，通知客户列表刷新 */
export const CUSTOMER_BATCH_BY_CONDITION_DOM_EVENT = 'customer-batch-by-condition-sse';

/** 平台系统公告 SSE */
export const SSE_EVENT_PLATFORM_SYSTEM_ANNOUNCEMENT = 'PLATFORM_SYSTEM_ANNOUNCEMENT';

/** 平台强制全员下线 SSE */
export const SSE_EVENT_PLATFORM_FORCE_LOGOUT = 'PLATFORM_FORCE_LOGOUT';

/** 平台强制全员下线完成 SSE（仅管理中心） */
export const SSE_EVENT_PLATFORM_FORCE_LOGOUT_DONE = 'PLATFORM_FORCE_LOGOUT_DONE';

/** 强制全员下线完成后通知系统维护页刷新在线人数 */
export const PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT = 'platform-force-logout-done';

/** 影子库切换预告 SSE（租户侧） */
export const SSE_EVENT_SHADOW_PRE_NOTICE = 'SHADOW_PRE_NOTICE';

/** 影子库切换完成 SSE（租户侧） */
export const SSE_EVENT_SHADOW_SWITCH_COMPLETE = 'SHADOW_SWITCH_COMPLETE';

export type PoolBatchByConditionSseOperation = 'PICK' | 'ASSIGN' | 'TRANSFER';

export interface PoolBatchByConditionSseDetail {
  poolId: string;
  operation: PoolBatchByConditionSseOperation;
  successCount: number;
  failCount: number;
  submittedCount: number;
}

export type CustomerBatchByConditionSseOperation = 'DELETE' | 'TRANSFER' | 'TO_POOL' | 'UPDATE';

export interface CustomerBatchByConditionSseDetail {
  viewId: string;
  operation: CustomerBatchByConditionSseOperation;
  successCount: number;
  failCount: number;
  submittedCount: number;
}
