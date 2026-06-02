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
            <n-button ghost class="mr-[12px]" type="primary" :loading="loading" @click="handleQuery">
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
            <n-switch v-model:value="showEmptyItems" @update:value="handleShowEmptyItemsChange" />
          </div>
          <n-button
            type="primary"
            secondary
            :loading="exportLoading"
            :disabled="!lastQueryParams"
            @click="handleExport"
          >
            {{ t('common.export') }}
          </n-button>
        </div>
      </div>
    </CrmCard>

    <CrmCard no-content-bottom-padding hide-footer class="min-w-[1000px]">
      <n-data-table
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :bordered="false"
        :scroll-x="tableScrollX"
        :max-height="tableMaxHeight"
      />
    </CrmCard>
  </n-scrollbar>

  <drilldown-modal
    v-model:show="drilldownState.visible"
    :title="drilldownTitle"
    :loading="drilldownState.loading"
    :data="drilldownState.list"
    :columns="drilldownColumns"
    :total="drilldownState.total"
    :current="drilldownState.current"
    :page-size="drilldownState.pageSize"
    :scroll-x="drilldownScrollX"
    @page-change="handleDrilldownPageChange"
    @page-size-change="handleDrilldownPageSizeChange"
  />
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
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
    type SelectOption,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { downloadByteFile } from '@lib/shared/method';
  import type {
    EmployeeFollowAnalysisDrilldownItem,
    EmployeeFollowAnalysisDrilldownParams,
    EmployeeFollowAnalysisSummaryItem,
    EmployeeFollowAnalysisSummaryParams,
  } from '@lib/shared/models/report/employeeFollowAnalysis';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import DrilldownModal from './components/drilldownModal.vue';

  import {
    exportEmployeeFollowAnalysisSummary,
    getEmployeeFollowAnalysisDrilldown,
    getEmployeeFollowAnalysisSummary,
  } from '@/api/modules';

  const { t } = useI18n();
  const message = useMessage();

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

  const MetricType = {
    INBOUND_CUSTOMER: 'inboundCustomer',
    CONTACTED_CUSTOMER: 'contactedCustomer',
    NEW_WECHAT_FRIENDS: 'newWechatFriends',
    DIAL_COUNT: 'dialCount',
    CONNECTED_COUNT: 'connectedCount',
    CALL_OVER_1MIN: 'callOver1Min',
    CALL_OVER_3MIN: 'callOver3Min',
  } as const;

  const EMPTY_CUSTOMER_SOURCE_DIMENSION_KEY = '__EMPTY__';

  type MetricTypeValue = (typeof MetricType)[keyof typeof MetricType];

  type FollowUpRow = EmployeeFollowAnalysisSummaryItem;

  const loading = ref(false);
  const exportLoading = ref(false);
  const showEmptyItems = ref(true);
  const tableData = ref<FollowUpRow[]>([]);
  const lastQueryParams = ref<EmployeeFollowAnalysisSummaryParams | null>(null);

  const form = reactive({
    timePreset: TimePreset.TODAY as TimePresetValue,
    customRange: null as [number, number] | null,
    dimension: Dimension.EMPLOYEE_NAME as DimensionValue,
  });

  const drilldownState = reactive({
    visible: false,
    loading: false,
    metricType: '' as MetricTypeValue | '',
    title: '',
    dimensionKey: '',
    list: [] as EmployeeFollowAnalysisDrilldownItem[],
    total: 0,
    current: 1,
    pageSize: 10,
  });

  // 页面筛选项直接映射后端枚举值，便于和汇总/下钻接口保持一套口径。
  const timePresetOptions = computed<SelectOption[]>(() => [
    { label: t('report.filter.statTime.today'), value: TimePreset.TODAY },
    { label: t('report.filter.statTime.yesterday'), value: TimePreset.YESTERDAY },
    { label: t('report.filter.statTime.week'), value: TimePreset.WEEK },
    { label: t('report.filter.statTime.month'), value: TimePreset.MONTH },
    { label: t('report.filter.statTime.custom'), value: TimePreset.CUSTOM },
  ]);

  const dimensionOptions = computed<SelectOption[]>(() => [
    { label: t('report.dimension.employeeName'), value: Dimension.EMPLOYEE_NAME },
    { label: t('report.dimension.employeeDept'), value: Dimension.EMPLOYEE_DEPT },
    { label: t('report.dimension.customerSource'), value: Dimension.CUSTOMER_SOURCE },
    { label: t('report.dimension.statDay'), value: Dimension.STAT_DAY },
    { label: t('report.dimension.statMonth'), value: Dimension.STAT_MONTH },
  ]);

  // 给表格设置视口内最大高度，让数据区域内部滚动，表头保持固定可见。
  const tableMaxHeight = 'calc(100vh - 320px)';

  function formatDuration(sec: number) {
    const value = Math.max(0, Math.floor(sec));
    const hours = Math.floor(value / 3600);
    const minutes = Math.floor((value % 3600) / 60);
    const seconds = value % 60;
    const pad = (num: number) => String(num).padStart(2, '0');
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }

  function formatDateTime(value?: number) {
    if (!value) {
      return '-';
    }
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }

  function isCallMetric(metricType: string) {
    return (
      metricType === MetricType.DIAL_COUNT ||
      metricType === MetricType.CONNECTED_COUNT ||
      metricType === MetricType.CALL_OVER_1MIN ||
      metricType === MetricType.CALL_OVER_3MIN
    );
  }

  function validateRange() {
    if (form.timePreset === TimePreset.CUSTOM && (!form.customRange || form.customRange.length !== 2)) {
      message.warning(t('common.notNull', { value: t('report.filter.customRange') }));
      return false;
    }
    return true;
  }

  function buildDrilldownParams(): EmployeeFollowAnalysisDrilldownParams {
    return {
      timePreset: form.timePreset,
      startTime: form.customRange?.[0],
      endTime: form.customRange?.[1],
      dimensionType: form.dimension,
      dimensionKey: drilldownState.dimensionKey,
      metricType: drilldownState.metricType,
      current: drilldownState.current,
      pageSize: drilldownState.pageSize,
    };
  }

  function resolveDrilldownTitle(metricType: MetricTypeValue) {
    const titleMap: Record<MetricTypeValue, string> = {
      [MetricType.INBOUND_CUSTOMER]: t('report.drilldown.inboundCustomer'),
      [MetricType.CONTACTED_CUSTOMER]: t('report.drilldown.contactedCustomer'),
      [MetricType.NEW_WECHAT_FRIENDS]: t('report.drilldown.newWechatFriends'),
      [MetricType.DIAL_COUNT]: t('report.drilldown.dialCount'),
      [MetricType.CONNECTED_COUNT]: t('report.drilldown.connectedCount'),
      [MetricType.CALL_OVER_1MIN]: t('report.drilldown.callOver1Min'),
      [MetricType.CALL_OVER_3MIN]: t('report.drilldown.callOver3Min'),
    };
    return titleMap[metricType];
  }

  async function fetchDrilldown() {
    drilldownState.loading = true;
    try {
      const result = await getEmployeeFollowAnalysisDrilldown(buildDrilldownParams());
      drilldownState.list = result.list;
      drilldownState.total = result.total;
    } finally {
      drilldownState.loading = false;
    }
  }

  async function openDrilldown(metricType: MetricTypeValue, row: FollowUpRow) {
    if (!validateRange()) {
      return;
    }
    drilldownState.metricType = metricType;
    // 客户来源汇总里显示为 "-" 的行，真实维度键是空串；下钻时改传约定值，避免被后端 @NotBlank 拦截。
    drilldownState.dimensionKey =
      form.dimension === Dimension.CUSTOMER_SOURCE && !row.dimensionKey
        ? EMPTY_CUSTOMER_SOURCE_DIMENSION_KEY
        : row.dimensionKey;
    drilldownState.title = resolveDrilldownTitle(metricType);
    drilldownState.current = 1;
    drilldownState.pageSize = 10;
    drilldownState.visible = true;
    await fetchDrilldown();
  }

  function renderMetricCell(row: FollowUpRow, value: number, metricType: MetricTypeValue) {
    const canClick = value > 0;
    if (!canClick) {
      return value;
    }
    // 只有 7 个支持下钻的指标会渲染成可点击按钮，时长类保持纯展示。
    return h(
      NButton,
      {
        type: 'primary',
        text: true,
        onClick: () => openDrilldown(metricType, row),
      },
      { default: () => String(value) }
    );
  }

  function buildMetricColumn(
    title: string,
    key: keyof FollowUpRow,
    metricType: MetricTypeValue
  ): DataTableColumns<FollowUpRow>[number] {
    return {
      title,
      key,
      width: 140,
      render: (row) => renderMetricCell(row, Number(row[key] ?? 0), metricType),
    };
  }

  const dimensionColumnTitle = computed(() => {
    const titleMap: Record<DimensionValue, string> = {
      [Dimension.EMPLOYEE_NAME]: t('report.dimension.employeeName'),
      [Dimension.EMPLOYEE_DEPT]: t('report.dimension.employeeDept'),
      [Dimension.CUSTOMER_SOURCE]: t('report.dimension.customerSource'),
      [Dimension.STAT_DAY]: t('report.dimension.statDay'),
      [Dimension.STAT_MONTH]: t('report.dimension.statMonth'),
    };
    return titleMap[form.dimension];
  });

  const tableScrollX = 1480;
  const drilldownTitle = computed(() => drilldownState.title);

  const columns = computed<DataTableColumns<FollowUpRow>>(() => [
    {
      title: dimensionColumnTitle.value,
      key: 'dimensionLabel',
      width: 180,
      fixed: 'left',
      ellipsis: { tooltip: true },
    },
    buildMetricColumn(t('report.followUp.col.inboundCustomer'), 'inboundCustomerCount', MetricType.INBOUND_CUSTOMER),
    buildMetricColumn(
      t('report.followUp.col.contactedCustomer'),
      'contactedCustomerCount',
      MetricType.CONTACTED_CUSTOMER
    ),
    buildMetricColumn(t('report.followUp.col.newWechatFriends'), 'newWechatFriendCount', MetricType.NEW_WECHAT_FRIENDS),
    buildMetricColumn(t('report.followUp.col.dialCount'), 'dialCount', MetricType.DIAL_COUNT),
    buildMetricColumn(t('report.followUp.col.connectedCount'), 'connectedCount', MetricType.CONNECTED_COUNT),
    buildMetricColumn(t('report.followUp.col.callOver1Min'), 'callOver1MinCount', MetricType.CALL_OVER_1MIN),
    buildMetricColumn(t('report.followUp.col.callOver3Min'), 'callOver3MinCount', MetricType.CALL_OVER_3MIN),
    {
      title: t('report.followUp.col.callDuration'),
      key: 'callDurationSec',
      width: 140,
      render: (row) => formatDuration(row.callDurationSec),
    },
    {
      title: t('report.followUp.col.avgCallDuration'),
      key: 'avgCallDurationSec',
      width: 140,
      render: (row) => formatDuration(row.avgCallDurationSec),
    },
  ]);

  const drilldownColumns = computed<DataTableColumns<EmployeeFollowAnalysisDrilldownItem>>(() => {
    // 下钻弹窗按指标类型切三套列：客户类、微信好友审计类、通话审计类。
    if (drilldownState.metricType === MetricType.NEW_WECHAT_FRIENDS) {
      return [
        { title: t('report.drilldown.col.customerName'), key: 'customerName', width: 140 },
        { title: t('report.drilldown.col.mobile'), key: 'mobile', width: 130 },
        { title: t('report.drilldown.col.ownerName'), key: 'ownerName', width: 120 },
        { title: t('report.drilldown.col.departmentName'), key: 'departmentName', width: 140 },
        { title: t('report.drilldown.col.customerSource'), key: 'customerSource', width: 140 },
        {
          title: t('report.drilldown.col.eventTime'),
          key: 'eventTime',
          width: 180,
          render: (row) => formatDateTime(row.eventTime),
        },
      ];
    }
    if (isCallMetric(drilldownState.metricType)) {
      return [
        { title: t('report.drilldown.col.customerName'), key: 'customerName', width: 140 },
        { title: t('report.drilldown.col.mobile'), key: 'mobile', width: 130 },
        { title: t('report.drilldown.col.ownerName'), key: 'ownerName', width: 120 },
        { title: t('report.drilldown.col.departmentName'), key: 'departmentName', width: 140 },
        { title: t('report.drilldown.col.customerSource'), key: 'customerSource', width: 140 },
        { title: t('report.drilldown.col.beginTime'), key: 'beginTime', width: 160 },
        { title: t('report.drilldown.col.endTime'), key: 'endTime', width: 160 },
        {
          title: t('report.drilldown.col.duration'),
          key: 'duration',
          width: 110,
          render: (row) => formatDuration(row.duration ?? 0),
        },
        {
          title: t('report.drilldown.col.isConnected'),
          key: 'isConnected',
          width: 110,
          render: (row) => (row.isConnected === 1 ? t('common.yes') : t('common.no')),
        },
      ];
    }
    return [
      { title: t('report.drilldown.col.customerName'), key: 'customerName', width: 160 },
      { title: t('report.drilldown.col.mobile'), key: 'mobile', width: 130 },
      { title: t('report.drilldown.col.ownerName'), key: 'ownerName', width: 120 },
      { title: t('report.drilldown.col.departmentName'), key: 'departmentName', width: 140 },
      { title: t('report.drilldown.col.customerSource'), key: 'customerSource', width: 140 },
      {
        title: t('report.drilldown.col.eventTime'),
        key: 'eventTime',
        width: 180,
        render: (row) => formatDateTime(row.eventTime),
      },
    ];
  });

  const drilldownScrollX = computed(() => {
    if (drilldownState.metricType === MetricType.NEW_WECHAT_FRIENDS) {
      return 900;
    }
    if (isCallMetric(drilldownState.metricType)) {
      return 1360;
    }
    return 980;
  });

  function buildSummaryParams(): EmployeeFollowAnalysisSummaryParams {
    return {
      timePreset: form.timePreset,
      startTime: form.customRange?.[0],
      endTime: form.customRange?.[1],
      dimensionType: form.dimension,
      showEmptyItems: showEmptyItems.value,
    };
  }

  async function fetchSummary() {
    if (!validateRange()) {
      return;
    }
    loading.value = true;
    try {
      // 汇总接口返回的 dimensionKey 是后续下钻时回传给后端的唯一维度键。
      const data = buildSummaryParams();
      tableData.value = await getEmployeeFollowAnalysisSummary(data);
      lastQueryParams.value = { ...data };
    } finally {
      loading.value = false;
    }
  }

  function onTimePresetChange(val: TimePresetValue) {
    if (val !== TimePreset.CUSTOM) {
      form.customRange = null;
    }
  }

  function defaultForm() {
    form.timePreset = TimePreset.TODAY;
    form.customRange = null;
    form.dimension = Dimension.EMPLOYEE_NAME;
    showEmptyItems.value = true;
  }

  async function handleQuery() {
    await fetchSummary();
  }

  async function handleReset() {
    defaultForm();
    await fetchSummary();
  }

  async function handleShowEmptyItemsChange() {
    await fetchSummary();
  }

  function parseDownloadFileName(contentDisposition?: string) {
    if (!contentDisposition) {
      return '';
    }
    const utf8FileName = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
    if (utf8FileName?.[1]) {
      return decodeURIComponent(utf8FileName[1]);
    }
    const fileName = /filename="?([^";]+)"?/i.exec(contentDisposition);
    return fileName?.[1] ? decodeURIComponent(fileName[1]) : '';
  }

  async function handleExport() {
    if (!lastQueryParams.value) {
      message.warning(t('report.action.query'));
      return;
    }
    exportLoading.value = true;
    try {
      const response = await exportEmployeeFollowAnalysisSummary(lastQueryParams.value);
      const fileName = parseDownloadFileName(response.headers?.['content-disposition']);
      if (!fileName) {
        message.error(t('common.exportFailed'));
        return;
      }
      downloadByteFile(response.data, fileName);
    } finally {
      exportLoading.value = false;
    }
  }

  async function handleDrilldownPageChange(page: number) {
    drilldownState.current = page;
    await fetchDrilldown();
  }

  async function handleDrilldownPageSizeChange(pageSize: number) {
    drilldownState.pageSize = pageSize;
    drilldownState.current = 1;
    await fetchDrilldown();
  }

  onMounted(() => {
    fetchSummary();
  });
</script>
