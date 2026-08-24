<template>
  <n-scrollbar x-scrollable :content-style="{ 'min-width': '1180px', 'width': '100%', 'height': '100%' }">
    <CrmCard hide-footer auto-height class="form-card mb-[16px] min-w-[1180px]">
      <n-form
        label-placement="left"
        label-width="auto"
        :model="form"
        class="grid grid-cols-1 gap-x-[24px] gap-y-[8px] md:grid-cols-2 xl:grid-cols-4"
      >
        <n-form-item :label="t('customerRecording.filter.callTime')" path="dateRange">
          <n-date-picker
            v-model:value="form.dateRange"
            type="daterange"
            :clearable="false"
            class="w-full min-w-[280px]"
          />
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.department')" path="departmentId">
          <n-tree-select
            v-model:value="form.departmentId"
            :options="departmentOptions"
            label-field="name"
            key-field="id"
            children-field="children"
            filterable
            clearable
            class="w-full min-w-[200px]"
            @update:value="handleDepartmentChange"
          />
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.employee')" path="employeeId">
          <n-select
            v-model:value="form.employeeId"
            :options="employeeOptions"
            label-field="name"
            value-field="id"
            filterable
            clearable
            class="w-full min-w-[200px]"
          />
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.customerName')" path="customerName">
          <n-input
            v-model:value="form.customerName"
            clearable
            :placeholder="t('customerRecording.placeholder.customerName')"
          />
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.customerTel')" path="customerTel">
          <n-input
            v-model:value="form.customerTel"
            clearable
            :placeholder="t('customerRecording.placeholder.customerTel')"
          />
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.quickDuration')" path="quickDuration">
          <n-select
            v-model:value="form.quickDuration"
            :options="durationOptions"
            clearable
            class="w-full min-w-[180px]"
            @update:value="handleQuickDurationChange"
          />
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.minDuration')" path="minDuration">
          <n-input-number v-model:value="form.minDuration" :min="0" :show-button="false" clearable class="w-full">
            <template #suffix>{{ t('customerRecording.unit.second') }}</template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('customerRecording.filter.maxDuration')" path="maxDuration">
          <n-input-number v-model:value="form.maxDuration" :min="0" :show-button="false" clearable class="w-full">
            <template #suffix>{{ t('customerRecording.unit.second') }}</template>
          </n-input-number>
        </n-form-item>
        <n-form-item :show-label="false">
          <n-button ghost class="mr-[12px]" type="primary" :loading="propsRes.loading" @click="handleQuery">
            {{ t('common.search') }}
          </n-button>
          <n-button type="default" class="outline--secondary" @click="handleReset">
            {{ t('common.reset') }}
          </n-button>
        </n-form-item>
      </n-form>
    </CrmCard>

    <CrmCard no-content-bottom-padding hide-footer class="min-w-[1180px]">
      <CrmTable
        :key="tableRenderKey"
        v-bind="propsRes"
        class="customer-recording-table"
        :scroll-x="1520"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
        @sorter-change="propsEvent.sorterChange"
        @refresh="handleQuery"
      />
    </CrmCard>
  </n-scrollbar>
</template>

<script lang="ts" setup>
  import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import {
    NButton,
    NDatePicker,
    NForm,
    NFormItem,
    NInput,
    NInputNumber,
    NScrollbar,
    NSelect,
    NTreeSelect,
    type SelectOption,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type {
    CustomerRecordingEmployeeOption,
    CustomerRecordingListItem,
    CustomerRecordingPageParams,
  } from '@lib/shared/models/report/customerRecording';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmNameTooltip from '@/components/pure/crm-name-tooltip/index.vue';
  import CrmTable from '@/components/pure/crm-table/index.vue';
  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import useTable from '@/components/pure/crm-table/useTable';
  import type { CrmTreeNodeData } from '@/components/pure/crm-tree/type';

  import {
    getCustomerRecordingEmployeeOptions,
    getCustomerRecordingPage,
    getHomeDepartmentTree,
    previewCustomerRecordingAudio,
  } from '@/api/modules';

  const { t } = useI18n();
  const message = useMessage();
  const departmentOptions = ref<CrmTreeNodeData[]>([]);
  const employeeOptions = ref<CustomerRecordingEmployeeOption[]>([]);
  const activeAudioRowId = ref('');
  const loadingAudioRowId = ref('');
  const audioUrlMap = ref<Record<string, string>>({});

  function getDefaultDateRange(): [number, number] {
    return [dayjs().subtract(6, 'day').startOf('day').valueOf(), dayjs().endOf('day').valueOf()];
  }

  const form = reactive({
    dateRange: getDefaultDateRange(),
    departmentId: null as string | null,
    employeeId: null as string | null,
    customerName: '',
    customerTel: '',
    quickDuration: null as number | null,
    minDuration: null as number | null,
    maxDuration: null as number | null,
  });

  const durationOptions = computed<SelectOption[]>(() => [
    { label: t('customerRecording.duration.over30Seconds'), value: 30 },
    { label: t('customerRecording.duration.over1Minute'), value: 60 },
    { label: t('customerRecording.duration.over3Minutes'), value: 180 },
    { label: t('customerRecording.duration.over5Minutes'), value: 300 },
  ]);

  function formatDuration(duration?: number | null) {
    const value = Math.max(0, Math.floor(duration ?? 0));
    const hours = Math.floor(value / 3600);
    const minutes = Math.floor((value % 3600) / 60);
    const seconds = value % 60;
    const pad = (num: number) => String(num).padStart(2, '0');
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }

  function revokeAudioUrl(url?: string) {
    if (url) {
      URL.revokeObjectURL(url);
    }
  }

  function resetAudioCache() {
    Object.values(audioUrlMap.value).forEach(revokeAudioUrl);
    audioUrlMap.value = {};
    activeAudioRowId.value = '';
    loadingAudioRowId.value = '';
  }

  async function toggleAudio(row: CustomerRecordingListItem) {
    if (activeAudioRowId.value === row.auditId) {
      activeAudioRowId.value = '';
      return;
    }
    if (!audioUrlMap.value[row.auditId]) {
      loadingAudioRowId.value = row.auditId;
      try {
        const response = await previewCustomerRecordingAudio(row.auditId);
        const audioUrl = URL.createObjectURL(
          new Blob([response.data], { type: response.headers['content-type'] || 'audio/mpeg' })
        );
        audioUrlMap.value = {
          ...audioUrlMap.value,
          [row.auditId]: audioUrl,
        };
      } finally {
        loadingAudioRowId.value = '';
      }
    }
    activeAudioRowId.value = row.auditId;
  }

  function renderAudio(row: CustomerRecordingListItem) {
    const children = [
      h(
        NButton,
        {
          type: 'primary',
          text: true,
          loading: loadingAudioRowId.value === row.auditId,
          onClick: () => {
            toggleAudio(row).catch(() => undefined);
          },
        },
        {
          default: () =>
            activeAudioRowId.value === row.auditId
              ? t('customerRecording.action.hide')
              : t('customerRecording.action.play'),
        }
      ),
    ];
    if (activeAudioRowId.value === row.auditId) {
      children.push(
        h('audio', {
          src: audioUrlMap.value[row.auditId],
          controls: true,
          preload: 'none',
          style: 'width: 240px; vertical-align: middle;',
        })
      );
    }
    return h('div', { class: 'flex items-center gap-[8px] min-h-[32px]' }, children);
  }

  const columns = computed<CrmDataTableColumn[]>(() => [
    {
      title: t('customerRecording.column.employee'),
      key: 'employeeName',
      width: 130,
      fixed: 'left',
      render: (row: CustomerRecordingListItem) => h(CrmNameTooltip, { text: row.employeeName || '-' }),
    },
    {
      title: t('customerRecording.column.department'),
      key: 'departmentName',
      width: 150,
      render: (row: CustomerRecordingListItem) => h(CrmNameTooltip, { text: row.departmentName || '-' }),
    },
    {
      title: t('customerRecording.column.customerName'),
      key: 'customerName',
      width: 150,
      render: (row: CustomerRecordingListItem) => h(CrmNameTooltip, { text: row.customerName || '-' }),
    },
    {
      title: t('customerRecording.column.customerTel'),
      key: 'customerTel',
      width: 140,
    },
    {
      title: t('customerRecording.column.beginTime'),
      key: 'beginTime',
      width: 180,
    },
    {
      title: t('customerRecording.column.answerTime'),
      key: 'answerTime',
      width: 180,
    },
    {
      title: t('customerRecording.column.endTime'),
      key: 'endTime',
      width: 180,
    },
    {
      title: t('customerRecording.column.duration'),
      key: 'duration',
      width: 120,
      render: (row: CustomerRecordingListItem) => formatDuration(row.duration),
    },
    {
      title: t('customerRecording.column.recording'),
      key: 'audio',
      width: 340,
      render: (row: CustomerRecordingListItem) => renderAudio(row),
    },
  ]);

  const tableRenderKey = ref(0);
  let queryVersion = 0;
  let getLoadedAuditIds = () => new Set<string>();

  async function loadCustomerRecordingPage(params?: CustomerRecordingPageParams) {
    const requestVersion = queryVersion;
    const response = await getCustomerRecordingPage(params as CustomerRecordingPageParams);
    if (requestVersion !== queryVersion) {
      return {
        ...response,
        list: [],
        total: 0,
        current: 1,
      };
    }

    const loadedAuditIds = (params?.current ?? 1) > 1 ? getLoadedAuditIds() : new Set<string>();
    const uniqueList = (response.list ?? []).filter((item) => {
      if (loadedAuditIds.has(item.auditId)) {
        return false;
      }
      loadedAuditIds.add(item.auditId);
      return true;
    });
    return {
      ...response,
      list: uniqueList,
    };
  }

  const { propsRes, propsEvent, loadList, setLoadListParams } = useTable<CustomerRecordingListItem>(
    loadCustomerRecordingPage,
    {
      showSetting: false,
      columns: columns.value,
      tableRowKey: 'auditId',
      containerClass: '.customer-recording-table',
    }
  );
  getLoadedAuditIds = () => new Set(propsRes.value.data.map((item) => item.auditId));

  function validateForm() {
    if (!form.dateRange || form.dateRange.length !== 2) {
      message.warning(t('customerRecording.message.timeRequired'));
      return false;
    }
    if (form.minDuration !== null && form.maxDuration !== null && form.maxDuration < form.minDuration) {
      message.warning(t('customerRecording.message.durationRangeInvalid'));
      return false;
    }
    return true;
  }

  function buildQueryParams(): Omit<CustomerRecordingPageParams, 'current' | 'pageSize'> {
    return {
      startTime: dayjs(form.dateRange[0]).startOf('day').valueOf(),
      endTime: dayjs(form.dateRange[1]).endOf('day').valueOf(),
      departmentId: form.departmentId || undefined,
      employeeId: form.employeeId || undefined,
      customerName: form.customerName.trim() || undefined,
      customerTel: form.customerTel.trim() || undefined,
      minDuration: form.minDuration ?? undefined,
      maxDuration: form.maxDuration ?? undefined,
    };
  }

  async function handleQuery() {
    if (!validateForm()) {
      return;
    }
    queryVersion += 1;
    resetAudioCache();
    propsRes.value.data = [];
    tableRenderKey.value += 1;
    setLoadListParams(buildQueryParams());
    await loadList();
  }

  function handleQuickDurationChange(value: number | null) {
    form.minDuration = value;
    form.maxDuration = null;
  }

  async function loadEmployeeOptions() {
    employeeOptions.value = await getCustomerRecordingEmployeeOptions(form.departmentId || undefined);
  }

  async function handleDepartmentChange() {
    form.employeeId = null;
    await loadEmployeeOptions();
  }

  async function handleReset() {
    form.dateRange = getDefaultDateRange();
    form.departmentId = null;
    form.employeeId = null;
    form.customerName = '';
    form.customerTel = '';
    form.quickDuration = null;
    form.minDuration = null;
    form.maxDuration = null;
    await loadEmployeeOptions();
    await handleQuery();
  }

  async function handlePageChange(page: number) {
    resetAudioCache();
    await propsEvent.value.pageChange(page);
  }

  async function handlePageSizeChange(pageSize: number) {
    resetAudioCache();
    await propsEvent.value.pageSizeChange(pageSize);
  }

  onMounted(async () => {
    departmentOptions.value = await getHomeDepartmentTree();
    await loadEmployeeOptions();
    await handleQuery();
  });

  onBeforeUnmount(() => {
    resetAudioCache();
  });
</script>

<style lang="less" scoped>
  .form-card {
    :deep(.n-card__content) {
      padding-bottom: 8px;
    }
  }
</style>
