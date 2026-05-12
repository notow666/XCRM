<template>
  <n-scrollbar x-scrollable :content-style="{ 'min-width': '1000px', 'width': '100%', 'height': '100%' }">
    <CrmCard hide-footer auto-height class="form-card mb-[16px] min-w-[1000px]">
      <div class="flex flex-wrap items-end justify-between gap-[16px]">
        <n-form
          label-placement="left"
          label-width="auto"
          :model="form"
          class="grid flex-1 grid-cols-1 gap-x-[24px] gap-y-[8px] md:grid-cols-2 xl:grid-cols-4"
        >
          <n-form-item :label="t('report.filter.statTime')" path="timePreset">
            <n-select
              v-model:value="form.timePreset"
              class="w-full min-w-[200px]"
              :options="timePresetOptions"
              @update:value="onTimePresetChange"
            />
          </n-form-item>
          <n-form-item
            v-if="form.timePreset === TimePreset.CUSTOM"
            :label="t('report.filter.customRange')"
            path="customRange"
          >
            <n-date-picker v-model:value="form.customRange" type="daterange" clearable class="w-full min-w-[280px]" />
          </n-form-item>
          <n-form-item :label="t('report.filter.dimension')" path="dimension">
            <n-select v-model:value="form.dimension" class="w-full min-w-[200px]" :options="dimensionOptions" />
          </n-form-item>
          <n-form-item :show-label="false">
            <n-button ghost class="mr-[12px]" type="primary" @click="handleQuery">
              {{ t('report.action.query') }}
            </n-button>
            <n-button type="default" class="outline--secondary" @click="handleReset">
              {{ t('common.reset') }}
            </n-button>
          </n-form-item>
        </n-form>
        <div class="flex shrink-0 flex-wrap items-center gap-[16px] pb-[2px]">
          <div class="flex items-center gap-[8px]">
            <span class="whitespace-nowrap text-[14px] leading-none text-[var(--text-n1)]">
              {{ t('report.toolbar.showEmptyItems') }}
            </span>
            <n-switch v-model:value="showEmptyItems" />
          </div>
          <n-button type="primary" secondary @click="handleExport">
            {{ t('common.export') }}
          </n-button>
        </div>
      </div>
    </CrmCard>

    <CrmCard no-content-bottom-padding hide-footer class="min-w-[1000px]">
      <n-data-table :columns="columns" :data="displayTableData" :bordered="false" :scroll-x="tableScrollX" />
    </CrmCard>
  </n-scrollbar>
</template>

<script lang="ts" setup>
  import {
    type DataTableColumns,
    NButton,
    NDataTable,
    NDatePicker,
    NForm,
    NFormItem,
    NScrollbar,
    NSelect,
    NSwitch,
    useMessage,
  } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  const { t } = useI18n();
  const message = useMessage();

  /** 默认打开：展示指标全为 0 的维度行 */
  const showEmptyItems = ref(true);

  const TimePreset = {
    TODAY: 'today',
    YESTERDAY: 'yesterday',
    WEEK: 'week',
    MONTH: 'month',
    CUSTOM: 'custom',
  } as const;

  type TimePresetValue = (typeof TimePreset)[keyof typeof TimePreset];

  const Dimension = {
    EMPLOYEE_NAME: 'employeeName',
    EMPLOYEE_DEPT: 'employeeDept',
    CUSTOMER_SOURCE: 'customerSource',
    STAT_DAY: 'statDay',
    STAT_MONTH: 'statMonth',
  } as const;

  type DimensionValue = (typeof Dimension)[keyof typeof Dimension];

  interface FollowUpRow {
    dimensionValue: string;
    inboundCustomerCount: number;
    contactedCustomerCount: number;
    newWechatFriends: number;
    dialCount: number;
    connectedCount: number;
    callOver1Min: number;
    callOver3Min: number;
    callDurationSec: number;
    avgCallDurationSec: number;
  }

  const form = reactive({
    timePreset: TimePreset.TODAY as TimePresetValue,
    customRange: null as [number, number] | null,
    dimension: Dimension.EMPLOYEE_NAME as DimensionValue,
  });

  const timePresetOptions = computed(() => [
    { label: t('report.filter.statTime.today'), value: TimePreset.TODAY },
    { label: t('report.filter.statTime.yesterday'), value: TimePreset.YESTERDAY },
    { label: t('report.filter.statTime.week'), value: TimePreset.WEEK },
    { label: t('report.filter.statTime.month'), value: TimePreset.MONTH },
    { label: t('report.filter.statTime.custom'), value: TimePreset.CUSTOM },
  ]);

  const dimensionOptions = computed(() => [
    { label: t('report.dimension.employeeName'), value: Dimension.EMPLOYEE_NAME },
    { label: t('report.dimension.employeeDept'), value: Dimension.EMPLOYEE_DEPT },
    { label: t('report.dimension.customerSource'), value: Dimension.CUSTOMER_SOURCE },
    { label: t('report.dimension.statDay'), value: Dimension.STAT_DAY },
    { label: t('report.dimension.statMonth'), value: Dimension.STAT_MONTH },
  ]);

  function onTimePresetChange(val: TimePresetValue) {
    if (val !== TimePreset.CUSTOM) {
      form.customRange = null;
    }
  }

  function defaultForm() {
    form.timePreset = TimePreset.TODAY;
    form.customRange = null;
    form.dimension = Dimension.EMPLOYEE_NAME;
  }

  /** Demo formatting: HH:MM:SS */
  function formatDuration(sec: number) {
    const s = Math.max(0, Math.floor(sec));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const r = s % 60;
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(h)}:${pad(m)}:${pad(r)}`;
  }

  const dimensionColumnTitle = computed(() => {
    const map: Record<DimensionValue, string> = {
      [Dimension.EMPLOYEE_NAME]: t('report.dimension.employeeName'),
      [Dimension.EMPLOYEE_DEPT]: t('report.dimension.employeeDept'),
      [Dimension.CUSTOMER_SOURCE]: t('report.dimension.customerSource'),
      [Dimension.STAT_DAY]: t('report.dimension.statDay'),
      [Dimension.STAT_MONTH]: t('report.dimension.statMonth'),
    };
    return map[form.dimension];
  });

  const dimensionValueSamples: Record<DimensionValue, string[]> = {
    [Dimension.EMPLOYEE_NAME]: ['张三', '李四', '王五', '赵六', '钱七'],
    [Dimension.EMPLOYEE_DEPT]: ['销售一部', '销售二部', '市场部', '客服组', '运营组'],
    [Dimension.CUSTOMER_SOURCE]: ['线上推广', '老客户介绍', '展会', '地推', '电销'],
    [Dimension.STAT_DAY]: ['2026-05-10', '2026-05-11', '2026-05-12', '2026-05-13', '2026-05-14'],
    [Dimension.STAT_MONTH]: ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05'],
  };

  const emptyMetricRow: Omit<FollowUpRow, 'dimensionValue'> = {
    inboundCustomerCount: 0,
    contactedCustomerCount: 0,
    newWechatFriends: 0,
    dialCount: 0,
    connectedCount: 0,
    callOver1Min: 0,
    callOver3Min: 0,
    callDurationSec: 0,
    avgCallDurationSec: 0,
  };

  const metricTemplates: FollowUpRow[] = [
    {
      dimensionValue: '张三',
      inboundCustomerCount: 12,
      contactedCustomerCount: 9,
      newWechatFriends: 4,
      dialCount: 28,
      connectedCount: 19,
      callOver1Min: 11,
      callOver3Min: 5,
      callDurationSec: 3720,
      avgCallDurationSec: 196,
    },
    {
      dimensionValue: '李四',
      inboundCustomerCount: 8,
      contactedCustomerCount: 6,
      newWechatFriends: 2,
      dialCount: 15,
      connectedCount: 10,
      callOver1Min: 6,
      callOver3Min: 2,
      callDurationSec: 2100,
      avgCallDurationSec: 210,
    },
    {
      dimensionValue: '王五',
      inboundCustomerCount: 5,
      contactedCustomerCount: 4,
      newWechatFriends: 1,
      dialCount: 9,
      connectedCount: 5,
      callOver1Min: 3,
      callOver3Min: 1,
      callDurationSec: 960,
      avgCallDurationSec: 192,
    },
    {
      dimensionValue: '赵六',
      ...emptyMetricRow,
    },
    {
      dimensionValue: '钱七',
      ...emptyMetricRow,
    },
  ];

  const tableData = ref<FollowUpRow[]>([]);

  function rebuildDemoTable() {
    const samples = dimensionValueSamples[form.dimension];
    tableData.value = metricTemplates.map((row, i) => ({
      ...row,
      dimensionValue: samples[i] ?? row.dimensionValue,
    }));
  }

  function handleReset() {
    defaultForm();
    rebuildDemoTable();
  }

  onMounted(() => {
    rebuildDemoTable();
  });

  function rowHasMetricData(row: FollowUpRow) {
    return (
      row.inboundCustomerCount > 0 ||
      row.contactedCustomerCount > 0 ||
      row.newWechatFriends > 0 ||
      row.dialCount > 0 ||
      row.connectedCount > 0 ||
      row.callOver1Min > 0 ||
      row.callOver3Min > 0 ||
      row.callDurationSec > 0 ||
      row.avgCallDurationSec > 0
    );
  }

  const displayTableData = computed(() => {
    if (showEmptyItems.value) {
      return tableData.value;
    }
    return tableData.value.filter((row) => rowHasMetricData(row));
  });

  const tableScrollX = 1400;

  const columns = computed<DataTableColumns<FollowUpRow>>(() => [
    {
      title: dimensionColumnTitle.value,
      key: 'dimensionValue',
      width: 160,
      align: 'left',
      titleAlign: 'left',
      ellipsis: { tooltip: true },
      fixed: 'left',
    },
    {
      title: t('report.followUp.col.inboundCustomer'),
      key: 'inboundCustomerCount',
      width: 120,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.contactedCustomer'),
      key: 'contactedCustomerCount',
      width: 120,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.newWechatFriends'),
      key: 'newWechatFriends',
      width: 140,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.dialCount'),
      key: 'dialCount',
      width: 120,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.connectedCount'),
      key: 'connectedCount',
      width: 120,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.callOver1Min'),
      key: 'callOver1Min',
      width: 160,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.callOver3Min'),
      key: 'callOver3Min',
      width: 160,
      align: 'left',
      titleAlign: 'left',
    },
    {
      title: t('report.followUp.col.callDuration'),
      key: 'callDurationSec',
      width: 140,
      align: 'left',
      titleAlign: 'left',
      render: (row) => formatDuration(row.callDurationSec),
    },
    {
      title: t('report.followUp.col.avgCallDuration'),
      key: 'avgCallDurationSec',
      width: 140,
      align: 'left',
      titleAlign: 'left',
      render: (row) => formatDuration(row.avgCallDurationSec),
    },
  ]);

  function handleExport() {
    message.info(t('report.exportSoon'));
  }

  function handleQuery() {
    // v1: demo refresh; replace with API using timePreset, customRange, dimension
    rebuildDemoTable();
  }

  watch(() => form.dimension, rebuildDemoTable);
</script>
