<template>
  <div class="customer-list-datetime-cell">
    <div class="customer-list-datetime-cell__date">{{ display.dateLine }}</div>
    <div v-if="display.timeLine" class="customer-list-datetime-cell__time">{{ display.timeLine }}</div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';

  import { parseCustomerListDateTimeDisplay } from './customerListDateTime';

  const props = defineProps<{
    value: string | number | null | undefined;
    column?: CrmDataTableColumn;
  }>();

  const display = computed(() =>
    parseCustomerListDateTimeDisplay(props.value, props.column?.dateType ?? props.column?.fieldConfig?.dateType)
  );
</script>

<style lang="less" scoped>
  .customer-list-datetime-cell {
    font-size: 14px;
    line-height: 18px;
    color: var(--text-n1);
    white-space: normal;
    word-break: break-word;
  }
  .customer-list-datetime-cell__date,
  .customer-list-datetime-cell__time {
    white-space: nowrap;
  }
  .customer-list-datetime-cell__time {
    margin-top: 2px;
  }
</style>
