import { type Ref, ref } from 'vue';

import { SpecialColumnEnum, TableKeyEnum } from '@lib/shared/enums/tableEnum';

import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';

import useTableStore from '@/hooks/useTableStore';

/** 固定在「客户信息」列展示，不参与中间动态列（客户阶段名称 stage 由表头显示设置控制） */
const LIST_CUSTOMER_INFO_KEYS = new Set(['name', 'callStatus', 'wechatFriendStatus', 'mobile']);

/** 表格模式专用或已从列表剔除的列 */
const LIST_EXCLUDED_MIDDLE_KEYS = new Set([
  'recyclePoolName',
  'reasonId',
  'reservedDays',
  'customerDial',
  'customerReach',
  SpecialColumnEnum.ORDER,
  SpecialColumnEnum.OPERATION,
  SpecialColumnEnum.DRAG,
]);

function isSpecialColumn(col: CrmDataTableColumn) {
  const key = String(col.key ?? '');
  return (
    col.type === SpecialColumnEnum.SELECTION ||
    key === SpecialColumnEnum.ORDER ||
    key === SpecialColumnEnum.OPERATION ||
    key === SpecialColumnEnum.DRAG
  );
}

export default function useCustomerListColumns(
  sourceColumns: Ref<CrmDataTableColumn[]>,
  options?: { enabled?: Ref<boolean> }
) {
  const tableStore = useTableStore();
  const listMiddleColumns = ref<CrmDataTableColumn[]>([]);

  async function refreshListMiddleColumns() {
    if (options?.enabled?.value === false) {
      listMiddleColumns.value = [];
      return;
    }
    const visible = await tableStore.getShowInTableColumns(TableKeyEnum.CUSTOMER);
    const sourceMap = new Map(sourceColumns.value.map((col) => [String(col.key), col] as const));

    listMiddleColumns.value = visible
      .filter((col) => {
        const key = String(col.key ?? '');
        if (!key || !col.showInTable) return false;
        if (isSpecialColumn(col)) return false;
        if (LIST_CUSTOMER_INFO_KEYS.has(key) || LIST_EXCLUDED_MIDDLE_KEYS.has(key)) {
          return false;
        }
        return true;
      })
      .map((col) => {
        const source = sourceMap.get(String(col.key));
        return {
          ...col,
          render: source?.render,
          isTag: source?.isTag ?? col.isTag,
          tagGroupProps: source?.tagGroupProps ?? col.tagGroupProps,
          sorter: source?.sorter ?? col.sorter,
          sortOrder: source?.sortOrder ?? col.sortOrder ?? false,
          ellipsis: source?.ellipsis ?? col.ellipsis,
          filedType: source?.filedType ?? col.filedType,
          dateType: source?.dateType ?? col.dateType,
          minWidth: col.minWidth ?? source?.minWidth ?? 100,
          width: col.width ?? source?.width ?? 120,
        } as CrmDataTableColumn;
      });
  }

  return {
    listMiddleColumns,
    refreshListMiddleColumns,
  };
}
