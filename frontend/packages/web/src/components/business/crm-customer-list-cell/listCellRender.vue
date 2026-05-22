<script setup lang="ts">
  import { computed, h, isVNode, type VNodeChild } from 'vue';
  import { NEllipsis } from 'naive-ui';

  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import CustomerListDateTimeCell from './customerListDateTimeCell.vue';

  import { isCustomerListDateTimeColumn } from './customerListDateTime';

  const props = defineProps<{
    render: (rowData: Record<string, any>, rowIndex: number) => VNodeChild;
    row: Record<string, any>;
    rowIndex: number;
    column: CrmDataTableColumn;
  }>();

  const showEllipsisTooltip = computed(() => {
    const { ellipsis } = props.column;
    if (ellipsis === true) return true;
    if (ellipsis && typeof ellipsis === 'object') {
      return ellipsis.tooltip !== false;
    }
    return true;
  });

  function wrapEllipsis(content: string) {
    return h(
      NEllipsis,
      { class: 'customer-list-dynamic-cell', tooltip: showEllipsisTooltip.value },
      { default: () => content }
    );
  }

  function renderCell() {
    const result = props.render(props.row, props.rowIndex);
    if (result === undefined || result === null || result === '') {
      return wrapEllipsis('-');
    }
    if (typeof result === 'string' || typeof result === 'number') {
      if (isCustomerListDateTimeColumn(props.column)) {
        return h(CustomerListDateTimeCell, { value: result, column: props.column });
      }
      return wrapEllipsis(String(result));
    }
    if (isVNode(result)) {
      return result;
    }
    return result;
  }
</script>

<template>
  <component :is="{ render: renderCell }" />
</template>

<style lang="less" scoped>
  :deep(.customer-list-dynamic-cell) {
    font-size: 14px;
    color: var(--text-n1);
  }
</style>
