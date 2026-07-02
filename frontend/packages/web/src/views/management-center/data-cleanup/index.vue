<template>
  <CrmCard no-content-padding hide-footer>
    <div class="space-y-5 p-4">
      <section>
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <div class="text-base font-semibold text-[var(--text-n1)]">
            {{ t('managementCenter.dataCleanup.tenantList') }}
          </div>
          <NInput
            v-model:value="tenantKeyword"
            clearable
            :placeholder="t('managementCenter.dataCleanup.tenantKeywordPlaceholder')"
            class="w-[280px]"
            @keyup.enter="handleTenantSearch"
          />
          <NButton type="primary" @click="handleTenantSearch">{{ t('common.search') }}</NButton>
          <NButton @click="handleTenantReset">{{ t('common.reset') }}</NButton>
          <NDatePicker
            v-model:value="cleanupStartDate"
            type="date"
            clearable
            class="w-[160px]"
            :placeholder="t('managementCenter.dataCleanup.startDatePlaceholder')"
          />
          <NDatePicker
            v-model:value="cleanupEndDate"
            type="date"
            clearable
            class="w-[160px]"
            :placeholder="t('managementCenter.dataCleanup.endDatePlaceholder')"
          />
          <NPopconfirm :disabled="submitDisabled" @positive-click="handleSubmitCleanup">
            <template #trigger>
              <NButton type="error" :disabled="submitDisabled" :loading="submitting">
                {{ t('managementCenter.dataCleanup.submit') }}
              </NButton>
            </template>
            {{ t('managementCenter.dataCleanup.confirm') }}
          </NPopconfirm>
          <span class="text-sm text-[var(--text-n4)]">
            {{ t('managementCenter.dataCleanup.selectedTenantCount', { count: selectedTenantIds.length }) }}
          </span>
        </div>

        <NDataTable
          v-model:checked-row-keys="selectedTenantIds"
          :columns="tenantColumns"
          :data="tenantRows"
          :loading="tenantLoading"
          :pagination="false"
          :row-key="(row) => row.tenantId"
        />
        <div class="mt-4 flex justify-end">
          <NPagination
            v-model:page="tenantPagination.current"
            v-model:page-size="tenantPagination.pageSize"
            :item-count="tenantPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            show-size-picker
            @update:page="loadTenants"
            @update:page-size="handleTenantPageSizeChange"
          />
        </div>
      </section>

      <section>
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <div class="text-base font-semibold text-[var(--text-n1)]">
            {{ t('managementCenter.dataCleanup.taskList') }}
          </div>
          <NInput
            v-model:value="taskTenantId"
            clearable
            :placeholder="t('managementCenter.dataCleanup.taskTenantPlaceholder')"
            class="w-[220px]"
            @keyup.enter="handleTaskSearch"
          />
          <NSelect
            v-model:value="taskStatus"
            :options="statusOptions"
            clearable
            class="w-[160px]"
            :placeholder="t('managementCenter.dataCleanup.statusAll')"
          />
          <NButton type="primary" @click="handleTaskSearch">{{ t('common.search') }}</NButton>
          <NButton @click="handleTaskReset">{{ t('common.reset') }}</NButton>
        </div>

        <NDataTable :columns="taskColumns" :data="taskRows" :loading="taskLoading" :pagination="false" />
        <div class="mt-4 flex justify-end">
          <NPagination
            v-model:page="taskPagination.current"
            v-model:page-size="taskPagination.pageSize"
            :item-count="taskPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            show-size-picker
            @update:page="loadTasks"
            @update:page-size="handleTaskPageSizeChange"
          />
        </div>
      </section>
    </div>
  </CrmCard>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import {
    NButton,
    NDataTable,
    NDatePicker,
    NInput,
    NPagination,
    NPopconfirm,
    NSelect,
    NTag,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    pagePlatformTenantDataCleanupTasks,
    pagePlatformTenants,
    type PlatformTenantDataCleanupTask,
    type PlatformTenantItem,
    submitPlatformTenantDataCleanup,
  } from '@/api/modules';

  import type { DataTableColumns, DataTableRowKey } from 'naive-ui';

  const { t } = useI18n();
  const message = useMessage();

  const tenantLoading = ref(false);
  const submitting = ref(false);
  const tenantRows = ref<PlatformTenantItem[]>([]);
  const tenantKeyword = ref('');
  const selectedTenantIds = ref<DataTableRowKey[]>([]);
  const cleanupStartDate = ref<number | null>(null);
  const cleanupEndDate = ref<number | null>(null);
  const tenantPagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
  });

  const taskLoading = ref(false);
  const taskRows = ref<PlatformTenantDataCleanupTask[]>([]);
  const taskTenantId = ref('');
  const taskStatus = ref<string | null>(null);
  const taskPagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
  });

  const statusOptions = computed(() => [
    { label: t('managementCenter.dataCleanup.statusPending'), value: 'PENDING' },
    { label: t('managementCenter.dataCleanup.statusRunning'), value: 'RUNNING' },
    { label: t('managementCenter.dataCleanup.statusSuccess'), value: 'SUCCESS' },
    { label: t('managementCenter.dataCleanup.statusFailed'), value: 'FAILED' },
  ]);
  const submitDisabled = computed(
    () =>
      selectedTenantIds.value.length === 0 ||
      submitting.value ||
      cleanupStartDate.value === null ||
      cleanupEndDate.value === null
  );

  function formatDateTime(ms?: number) {
    return ms ? dayjs(ms).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  function statusType(status?: string) {
    if (status === 'SUCCESS') return 'success';
    if (status === 'FAILED') return 'error';
    if (status === 'RUNNING') return 'info';
    return 'warning';
  }

  function statusLabel(status?: string) {
    const keyMap: Record<string, string> = {
      PENDING: 'managementCenter.dataCleanup.statusPending',
      RUNNING: 'managementCenter.dataCleanup.statusRunning',
      SUCCESS: 'managementCenter.dataCleanup.statusSuccess',
      FAILED: 'managementCenter.dataCleanup.statusFailed',
    };
    return status ? t(keyMap[status] || status) : '-';
  }

  function formatTaskDetail(detail?: string) {
    if (!detail) {
      return '-';
    }
    try {
      const parsed = JSON.parse(detail);
      const messageText = parsed.message || '';
      const progress = t('managementCenter.dataCleanup.detailProgress', {
        success: parsed.successCount ?? 0,
        total: parsed.totalTables ?? 0,
      });
      const dateRange =
        parsed.startDate && parsed.endDate
          ? `，${t('managementCenter.dataCleanup.detailDateRange', {
              startDate: parsed.startDate,
              endDate: parsed.endDate,
            })}`
          : '';
      const deletedRows = `，${t('managementCenter.dataCleanup.detailDeletedRows', {
        count: parsed.deletedRows ?? 0,
      })}`;
      const current = parsed.currentTable ? `，${parsed.currentTable}` : '';
      const skippedReasons = parsed.skippedTableReasons || {};
      const skippedTables = Array.isArray(parsed.skippedTables)
        ? parsed.skippedTables
            .map((table: string) => {
              const reason = skippedReasons[table];
              return reason ? `${table}(${reason})` : table;
            })
            .join(', ')
        : '';
      const skipped =
        parsed.skippedCount > 0 ? `，跳过 ${parsed.skippedCount} 张${skippedTables ? `：${skippedTables}` : ''}` : '';
      const redundantProgress =
        parsed.redundantTotalTables === undefined
          ? ''
          : `，冗余进度 ${parsed.redundantSuccessCount ?? 0}/${parsed.redundantTotalTables ?? 0}`;
      const redundantDeletedRows =
        parsed.redundantTotalTables === undefined ? '' : `，冗余删除 ${parsed.redundantDeletedRows ?? 0} 行`;
      const redundantFailedTableErrors = parsed.redundantFailedTableErrors || {};
      const redundantFailedTables = Object.keys(redundantFailedTableErrors).join(', ');
      const redundantFailed =
        parsed.redundantFailedCount > 0
          ? `，冗余失败 ${parsed.redundantFailedCount} 张${redundantFailedTables ? `：${redundantFailedTables}` : ''}`
          : '';
      const error = parsed.error ? `，${parsed.error}` : '';
      return [
        messageText,
        dateRange,
        `，${progress}`,
        deletedRows,
        skipped,
        redundantProgress,
        redundantDeletedRows,
        redundantFailed,
        current,
        error,
      ].join('');
    } catch {
      return detail;
    }
  }

  const tenantColumns = computed<DataTableColumns<PlatformTenantItem>>(() => [
    { type: 'selection' },
    { title: '租户名称', key: 'name' },
    { title: '唯一标识', key: 'tenantId' },
    { title: 'dbName', key: 'dbName' },
    {
      title: 'status',
      key: 'status',
      render: (row) =>
        h(
          NTag,
          { type: row.status === 'ACTIVE' ? 'success' : 'warning' },
          { default: () => row.status || (row.enabled ? 'ACTIVE' : 'DISABLED') }
        ),
    },
  ]);

  const taskColumns = computed<DataTableColumns<PlatformTenantDataCleanupTask>>(() => [
    { title: t('managementCenter.dataCleanup.columnTenant'), key: 'tenantId', width: 150 },
    {
      title: t('managementCenter.dataCleanup.columnStatus'),
      key: 'status',
      width: 110,
      render: (row) => h(NTag, { type: statusType(row.status) }, { default: () => statusLabel(row.status) }),
    },
    { title: t('managementCenter.dataCleanup.columnOperator'), key: 'operatorId', width: 140 },
    {
      title: t('managementCenter.dataCleanup.columnCreateTime'),
      key: 'createTime',
      width: 170,
      render: (row) => h('span', formatDateTime(row.createTime)),
    },
    {
      title: t('managementCenter.dataCleanup.columnUpdateTime'),
      key: 'updateTime',
      width: 170,
      render: (row) => h('span', formatDateTime(row.updateTime)),
    },
    {
      title: t('managementCenter.dataCleanup.columnDetail'),
      key: 'detail',
      ellipsis: { tooltip: true },
      render: (row) => h('span', formatTaskDetail(row.detail)),
    },
  ]);

  async function loadTenants() {
    tenantLoading.value = true;
    try {
      const res = await pagePlatformTenants({
        current: tenantPagination.current,
        pageSize: tenantPagination.pageSize,
        keyword: tenantKeyword.value,
      });
      tenantRows.value = res.list || [];
      tenantPagination.total = res.total || 0;
      tenantPagination.current = res.current || tenantPagination.current;
      tenantPagination.pageSize = res.pageSize || tenantPagination.pageSize;
    } finally {
      tenantLoading.value = false;
    }
  }

  function handleTenantSearch() {
    tenantPagination.current = 1;
    loadTenants();
  }

  function handleTenantReset() {
    tenantKeyword.value = '';
    selectedTenantIds.value = [];
    cleanupStartDate.value = null;
    cleanupEndDate.value = null;
    tenantPagination.current = 1;
    loadTenants();
  }

  function handleTenantPageSizeChange() {
    tenantPagination.current = 1;
    loadTenants();
  }

  async function loadTasks() {
    taskLoading.value = true;
    try {
      const res = await pagePlatformTenantDataCleanupTasks({
        current: taskPagination.current,
        pageSize: taskPagination.pageSize,
        tenantId: taskTenantId.value,
        status: taskStatus.value || undefined,
      });
      taskRows.value = res.list || [];
      taskPagination.total = res.total || 0;
      taskPagination.current = res.current || taskPagination.current;
      taskPagination.pageSize = res.pageSize || taskPagination.pageSize;
    } finally {
      taskLoading.value = false;
    }
  }

  async function handleSubmitCleanup() {
    if (selectedTenantIds.value.length === 0) {
      message.warning(t('managementCenter.dataCleanup.selectTenantRequired'));
      return false;
    }
    if (cleanupStartDate.value === null || cleanupEndDate.value === null) {
      message.warning(t('managementCenter.dataCleanup.dateRangeRequired'));
      return false;
    }
    if (cleanupStartDate.value > cleanupEndDate.value) {
      message.warning(t('managementCenter.dataCleanup.dateRangeInvalid'));
      return false;
    }
    submitting.value = true;
    try {
      await submitPlatformTenantDataCleanup({
        tenantIds: selectedTenantIds.value.map(String),
        startDate: dayjs(cleanupStartDate.value).format('YYYY-MM-DD'),
        endDate: dayjs(cleanupEndDate.value).format('YYYY-MM-DD'),
      });
      message.success(t('managementCenter.dataCleanup.submitSuccess'));
      selectedTenantIds.value = [];
      taskPagination.current = 1;
      await loadTasks();
    } finally {
      submitting.value = false;
    }
    return true;
  }

  function handleTaskSearch() {
    taskPagination.current = 1;
    loadTasks();
  }

  function handleTaskReset() {
    taskTenantId.value = '';
    taskStatus.value = null;
    taskPagination.current = 1;
    loadTasks();
  }

  function handleTaskPageSizeChange() {
    taskPagination.current = 1;
    loadTasks();
  }

  onMounted(() => {
    loadTenants();
    loadTasks();
  });
</script>
