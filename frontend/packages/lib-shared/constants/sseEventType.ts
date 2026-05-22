/** 与后端 {@code MmbaConstants.SSE_EVENT_DEVICE_SYNC} 一致 */
export const SSE_EVENT_MMBA_DEVICE_SYNC = 'MMBA_DEVICE_SYNC';

/** 设备同步 SSE 到达后，通知设备管理页切换「同步」为「刷新」 */
export const MMBA_DEVICE_SYNC_DOM_EVENT = 'mmba-device-sync-sse';

/** 与后端 {@code PoolCustomerBatchConstants.SSE_POOL_BATCH_BY_CONDITION_DONE} 一致 */
export const SSE_EVENT_POOL_BATCH_BY_CONDITION_DONE = 'POOL_BATCH_BY_CONDITION_DONE';

/** 公海按筛选批量任务完成 SSE 到达后，通知公海池列表刷新 */
export const POOL_BATCH_BY_CONDITION_DOM_EVENT = 'pool-batch-by-condition-sse';

export type PoolBatchByConditionSseOperation = 'PICK' | 'ASSIGN' | 'TRANSFER';

export interface PoolBatchByConditionSseDetail {
  poolId: string;
  operation: PoolBatchByConditionSseOperation;
  successCount: number;
  failCount: number;
  submittedCount: number;
}
