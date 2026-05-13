<template>
  <n-modal :show="show" style="width: 1200px" preset="card" :title="title" @update:show="handleVisibleChange">
    <div class="flex flex-col gap-[12px]">
      <n-data-table
        :columns="columns"
        :data="data"
        :loading="loading"
        :bordered="false"
        :scroll-x="scrollX"
        :max-height="560"
      />
      <div class="flex justify-end">
        <n-pagination
          :page="current"
          :page-size="pageSize"
          :item-count="total"
          :page-sizes="[10, 20, 50]"
          show-size-picker
          @update:page="(page) => emit('pageChange', page)"
          @update:page-size="(size) => emit('pageSizeChange', size)"
        />
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
  import { NDataTable, NModal, NPagination } from 'naive-ui';

  import type { EmployeeFollowAnalysisDrilldownItem } from '@lib/shared/models/report/employeeFollowAnalysis';

  import type { DataTableColumns } from 'naive-ui';

  const props = defineProps<{
    show: boolean;
    title: string;
    loading: boolean;
    data: EmployeeFollowAnalysisDrilldownItem[];
    columns: DataTableColumns<EmployeeFollowAnalysisDrilldownItem>;
    total: number;
    current: number;
    pageSize: number;
    scrollX?: number;
  }>();

  const emit = defineEmits<{
    (e: 'update:show', value: boolean): void;
    (e: 'pageChange', page: number): void;
    (e: 'pageSizeChange', pageSize: number): void;
  }>();

  function handleVisibleChange(value: boolean) {
    emit('update:show', value);
  }
</script>
