<template>
  <div
    class="customer-list-dynamic-th flex shrink-0 items-center"
    :class="{ 'customer-list-dynamic-th--sorting': !!sortOrder }"
    :style="{ width: `${width}px`, minWidth: `${width}px` }"
  >
    <div
      class="customer-list-dynamic-th__inner flex min-w-0 max-w-full items-center"
      :class="{ 'customer-list-dynamic-th__inner--sortable': !!column.sorter }"
      @click="handleClick"
    >
      <div class="customer-list-dynamic-th__title-wrapper flex min-w-0 items-center">
        <span class="customer-list-dynamic-th__title one-line-text">{{ title }}</span>
        <div v-if="column.sorter" class="customer-list-dynamic-sorter ml-[8px] flex shrink-0 flex-col">
          <CrmIcon
            type="iconicon_chevron_up"
            class="sort-up-icon h-[8px]"
            :color="sortOrder === 'ascend' ? 'var(--primary-8)' : 'var(--text-n2)'"
          />
          <CrmIcon
            type="iconicon_chevron_down"
            class="sort-down-icon h-[8px]"
            :color="sortOrder === 'descend' ? 'var(--primary-8)' : 'var(--text-n2)'"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';
  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';

  const props = defineProps<{
    column: CrmDataTableColumn;
    title: string;
    width: number;
  }>();

  const emit = defineEmits<{
    (e: 'sort', column: CrmDataTableColumn): void;
  }>();

  const sortOrder = computed(() => props.column.sortOrder ?? false);

  function handleClick() {
    if (!props.column.sorter) return;
    emit('sort', props.column);
  }
</script>

<style lang="less" scoped>
  .customer-list-dynamic-th {
    padding: 10px 16px;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-n4);
    white-space: nowrap;
    background-color: var(--text-n10);
  }
  .customer-list-dynamic-th__inner--sortable {
    cursor: pointer;
  }
  .customer-list-dynamic-th__title-wrapper {
    width: fit-content;
    max-width: 100%;
    height: 22px;
  }
  .customer-list-dynamic-th--sorting .customer-list-dynamic-th__title-wrapper {
    border-radius: 2px;
    background-color: var(--primary-7);
  }
  .customer-list-dynamic-th--sorting .customer-list-dynamic-th__title {
    padding-left: 4px;
    color: var(--primary-8);
  }
  .sort-up-icon {
    transform: translateY(4px);
  }
  .sort-down-icon {
    transform: translateY(-11px);
  }
</style>
