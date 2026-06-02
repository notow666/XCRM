import { type Ref, ref } from 'vue';

export interface CustomerNavigationItem {
  id: string;
  name?: string;
  owner?: string;
  callStatus?: number;
  wechatFriendStatus?: number;
  mobile?: string;
  inSharedPool?: boolean;
}

/** 客户列表默认每页条数（生产按 2000 条上限、约 40 次请求拉满） */
export const CUSTOMER_LIST_PAGE_SIZE = 50;

/** 列表展示滑动窗口：保留最近 N 页 */
export const CUSTOMER_LIST_WINDOW_PAGES = 5;

/** 导航缓冲上限（与生产搜索结果上限一致） */
export const CUSTOMER_LIST_NAV_BUFFER_MAX = 2000;

export function getCustomerListWindowMaxRows(pageSize = CUSTOMER_LIST_PAGE_SIZE) {
  return CUSTOMER_LIST_WINDOW_PAGES * pageSize;
}

function toNavigationItem(
  row: Record<string, any>,
  readCallStatus: (row: Record<string, any>) => number,
  readWechatFriendStatus: (row: Record<string, any>) => number
): CustomerNavigationItem {
  return {
    id: String(row.id),
    name: row.name,
    owner: row.owner,
    callStatus: readCallStatus(row),
    wechatFriendStatus: readWechatFriendStatus(row),
    mobile: row.mobile,
    inSharedPool: row.inSharedPool,
  };
}

export default function useCustomerListWindow(
  readCallStatus: (row: Record<string, any>) => number,
  readWechatFriendStatus: (row: Record<string, any>) => number
) {
  const navigationBuffer = ref<CustomerNavigationItem[]>([]);
  const enterRowIds = ref<Set<string>>(new Set());

  function resetNavigationBuffer() {
    navigationBuffer.value = [];
    enterRowIds.value = new Set();
  }

  function mergeNavigationBuffer(rows: Record<string, any>[]) {
    if (!rows.length) return;
    const idIndex = new Map(navigationBuffer.value.map((item, i) => [item.id, i]));
    rows.forEach((row) => {
      const id = String(row.id);
      const item = toNavigationItem(row, readCallStatus, readWechatFriendStatus);
      const existing = idIndex.get(id);
      if (existing !== undefined) {
        navigationBuffer.value[existing] = item;
      } else {
        idIndex.set(id, navigationBuffer.value.length);
        navigationBuffer.value.push(item);
      }
    });
    if (navigationBuffer.value.length > CUSTOMER_LIST_NAV_BUFFER_MAX) {
      navigationBuffer.value = navigationBuffer.value.slice(-CUSTOMER_LIST_NAV_BUFFER_MAX);
    }
  }

  function markEnterBatch(ids: string[]) {
    if (!ids.length) return;
    const next = new Set(enterRowIds.value);
    ids.forEach((id) => next.add(id));
    enterRowIds.value = next;
  }

  function clearEnterRowId(id: string) {
    if (!enterRowIds.value.has(id)) return;
    const next = new Set(enterRowIds.value);
    next.delete(id);
    enterRowIds.value = next;
  }

  /**
   * 裁剪列表展示数据，仅保留最近 window 条；返回被移除条数（用于校正虚拟列表 scrollTop）
   */
  function trimDisplayWindow(data: Record<string, any>[], pageSize: number): { removedCount: number } {
    const maxRows = getCustomerListWindowMaxRows(pageSize);
    if (data.length <= maxRows) {
      return { removedCount: 0 };
    }
    const removedCount = data.length - maxRows;
    data.splice(0, removedCount);
    return { removedCount };
  }

  function patchNavigationBufferRow(id: string, patch: Partial<CustomerNavigationItem>) {
    const item = navigationBuffer.value.find((row) => row.id === id);
    if (item) {
      Object.assign(item, patch);
    }
  }

  function removeFromNavigationBuffer(id: string) {
    navigationBuffer.value = navigationBuffer.value.filter((row) => row.id !== id);
  }

  return {
    navigationBuffer,
    enterRowIds,
    resetNavigationBuffer,
    mergeNavigationBuffer,
    markEnterBatch,
    clearEnterRowId,
    trimDisplayWindow,
    patchNavigationBufferRow,
    removeFromNavigationBuffer,
    toNavigationItem,
  };
}

export function adjustVirtualListScrollAfterTrim(
  listRef: Ref<{ $el?: HTMLElement } | null>,
  removedCount: number,
  itemHeight: number
) {
  if (removedCount <= 0 || !listRef.value) return;
  const scrollEl = listRef.value.$el?.querySelector('.v-vl') as HTMLElement | null;
  if (!scrollEl) return;
  const delta = removedCount * itemHeight;
  scrollEl.scrollTop = Math.max(0, scrollEl.scrollTop - delta);
}
