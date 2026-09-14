<template>
  <n-scrollbar x-scrollable :content-style="{ minWidth: '1000px', width: '100%', height: '100%' }">
    <CrmCard hide-footer auto-height class="mb-[16px] min-w-[1000px]">
      <div class="flex flex-wrap items-end justify-between gap-[16px]">
        <n-form label-placement="left" label-width="auto" :model="form" class="flex flex-wrap gap-[16px]">
          <n-form-item label="统计时间">
            <n-select v-model:value="form.timePreset" class="w-[180px]" :options="timeOptions" />
          </n-form-item>
          <n-form-item v-if="form.timePreset === 'CUSTOM'" label="自定义时间">
            <n-date-picker v-model:value="form.customRange" type="daterange" class="w-[280px]" />
          </n-form-item>
          <n-form-item label="统计维度">
            <n-select v-model:value="form.dimensionType" class="w-[180px]" :options="dimensionOptions" />
          </n-form-item>
          <n-form-item label="部门">
            <n-tree-select
              v-model:value="form.departmentId"
              :options="departmentOptions"
              label-field="name"
              key-field="id"
              children-field="children"
              filterable
              clearable
              class="w-[220px]"
            />
          </n-form-item>
          <n-form-item :show-label="false">
            <n-button type="primary" ghost @click="fetchSummary">查询</n-button>
            <n-button class="ml-[12px]" @click="handleReset">重置</n-button>
          </n-form-item>
        </n-form>
        <n-button
          type="primary"
          secondary
          :loading="exportLoading"
          @click="handleExport"
        >
          导出
        </n-button>
      </div>
    </CrmCard>
    <CrmCard hide-footer class="min-w-[1000px]">
      <n-data-table :columns="columns" :data="rows" :loading="loading" :bordered="false" :scroll-x="1100" />
    </CrmCard>
  </n-scrollbar>
  <DrilldownModal
    v-model:show="detail.visible"
    :title="detail.title"
    :loading="detail.loading"
    :data="detail.list"
    :columns="detailColumns"
    :total="detail.total"
    :current="detail.current"
    :page-size="detail.pageSize"
    :scroll-x="1200"
    @page-change="handlePageChange"
    @page-size-change="handlePageSizeChange"
  />
  <!-- 合同成交分析明细暂不开放导出；恢复时重新配置 show-export、export-loading 和 export 事件。 -->
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
    NTreeSelect,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import { downloadByteFile } from '@lib/shared/method';
  import type {
    ContractAnalysisDetailItem,
    ContractAnalysisDetailParams,
    ContractAnalysisSummaryItem,
    ReportRangeParams,
  } from '@lib/shared/models/report/contractAnalysis';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import type { CrmTreeNodeData } from '@/components/pure/crm-tree/type';
  import DrilldownModal from '@/views/report/employee-follow-up/components/drilldownModal.vue';

  import {
    exportContractAnalysis,
    // 合同成交分析明细暂不开放导出，保留接口代码便于后续恢复。
    // exportContractAnalysisDetail,
    getContractAnalysisDetail,
    getContractAnalysisSummary,
    getHomeDepartmentTree,
  } from '@/api/modules';

  type MetricType = ContractAnalysisDetailParams['metricType'];

  const message = useMessage();
  const loading = ref(false);
  const exportLoading = ref(false);
  const rows = ref<ContractAnalysisSummaryItem[]>([]);
  const form = reactive({
    timePreset: 'MONTH',
    customRange: null as [number, number] | null,
    dimensionType: 'EMPLOYEE_NAME' as ReportRangeParams['dimensionType'],
    departmentId: null as string | null,
  });
  const departmentOptions = ref<CrmTreeNodeData[]>([]);
  const timeOptions = [
    { label: '今天', value: 'TODAY' },
    { label: '昨天', value: 'YESTERDAY' },
    { label: '本周', value: 'WEEK' },
    { label: '本月', value: 'MONTH' },
    { label: '自定义', value: 'CUSTOM' },
  ];
  const dimensionOptions = [
    { label: '员工名称', value: 'EMPLOYEE_NAME' },
    { label: '员工部门', value: 'EMPLOYEE_DEPT' },
    { label: '客户来源', value: 'CUSTOMER_SOURCE' },
    { label: '统计时间-天', value: 'STAT_DAY' },
    { label: '统计时间-月', value: 'STAT_MONTH' },
  ];

  function buildRange(): [number, number] {
    const now = dayjs();
    switch (form.timePreset) {
      case 'TODAY':
        return [now.startOf('day').valueOf(), now.endOf('day').valueOf()];
      case 'YESTERDAY': {
        const yesterday = now.subtract(1, 'day');
        return [yesterday.startOf('day').valueOf(), yesterday.endOf('day').valueOf()];
      }
      case 'WEEK':
        return [now.startOf('week').valueOf(), now.endOf('day').valueOf()];
      case 'CUSTOM':
        return form.customRange || [now.startOf('month').valueOf(), now.endOf('day').valueOf()];
      default:
        return [now.startOf('month').valueOf(), now.endOf('day').valueOf()];
    }
  }

  function buildQueryParams(): ReportRangeParams {
    const [startTime, endTime] = buildRange();
    return {
      startTime,
      endTime,
      dimensionType: form.dimensionType,
      departmentId: form.departmentId || undefined,
    };
  }

  async function fetchSummary() {
    loading.value = true;
    try {
      rows.value = await getContractAnalysisSummary(buildQueryParams());
    } finally {
      loading.value = false;
    }
  }

  const metricLabels: Record<MetricType, string> = {
    CONTRACT: '合同',
    LOAN: '放款',
    REPAYMENT: '回款',
    REVENUE: '创收',
  };
  const customerSourceLabels: Record<string, string> = {
    MANUAL_CREATE: '自主创建',
    PRIVATE_IMPORT: '客户导入',
    POOL_IMPORT: '公海导入',
    CLUE_CREATE: '线索池生成',
  };
  const dimensionTitleLabels: Record<ReportRangeParams['dimensionType'], string> = {
    EMPLOYEE_NAME: '签约人',
    EMPLOYEE_DEPT: '部门',
    CUSTOMER_SOURCE: '客户来源',
    STAT_DAY: '统计日期',
    STAT_MONTH: '统计月份',
  };

  function formatDimensionLabel(row: ContractAnalysisSummaryItem) {
    if (form.dimensionType === 'CUSTOMER_SOURCE') {
      return customerSourceLabels[row.dimensionKey] || customerSourceLabels[row.dimensionLabel] || row.dimensionLabel;
    }
    return row.dimensionLabel;
  }
  const detail = reactive({
    visible: false,
    loading: false,
    // exportLoading: false,
    title: '',
    metricType: 'CONTRACT' as MetricType,
    dimensionKey: '',
    current: 1,
    pageSize: 20,
    total: 0,
    list: [] as ContractAnalysisDetailItem[],
  });

  async function fetchDetail() {
    detail.loading = true;
    try {
      const result = await getContractAnalysisDetail({
        ...buildQueryParams(),
        dimensionKey: detail.dimensionKey,
        metricType: detail.metricType,
        current: detail.current,
        pageSize: detail.pageSize,
      });
      detail.list = result.list || [];
      detail.total = result.total || 0;
    } finally {
      detail.loading = false;
    }
  }

  function openDetail(metricType: MetricType, row: ContractAnalysisSummaryItem, metricLabel = metricLabels[metricType]) {
    detail.metricType = metricType;
    detail.dimensionKey = row.dimensionKey;
    detail.current = 1;
    detail.title = `${formatDimensionLabel(row)} - ${metricLabel}明细`;
    detail.visible = true;
    fetchDetail();
  }

  function metricButton(metricType: MetricType, value: number, row: ContractAnalysisSummaryItem, metricLabel?: string) {
    return h(NButton, { text: true, type: 'primary', onClick: () => openDetail(metricType, row, metricLabel) }, () =>
      metricType === 'CONTRACT' ? value : `¥${Number(value || 0).toFixed(2)}`
    );
  }

  const columns = computed<DataTableColumns<ContractAnalysisSummaryItem>>(() => [
    {
      title: dimensionTitleLabels[form.dimensionType],
      key: 'dimensionLabel',
      width: 180,
      render: (row) => formatDimensionLabel(row),
    },
    {
      title: '合同签约数',
      key: 'contractCount',
      render: (row) => metricButton('CONTRACT', row.contractCount, row, '合同签约数'),
    },
    {
      title: '合同签约金额',
      key: 'contractAmount',
      render: (row) => metricButton('CONTRACT', row.contractAmount, row, '合同签约金额'),
    },
    { title: '放款金额', key: 'loanAmount', render: (row) => metricButton('LOAN', row.loanAmount, row) },
    { title: '回款金额', key: 'repaymentAmount', render: (row) => metricButton('REPAYMENT', row.repaymentAmount, row) },
    { title: '创收金额', key: 'revenueAmount', render: (row) => metricButton('REVENUE', row.revenueAmount, row) },
  ]);

  const detailColumns: DataTableColumns<ContractAnalysisDetailItem> = [
    { title: '合同', key: 'contractName', width: 180 },
    { title: '客户', key: 'customerName', width: 150 },
    { title: '手机号', key: 'customerMobile', width: 130 },
    { title: '签约人', key: 'signerName', width: 120 },
    { title: '部门', key: 'departmentName', width: 150 },
    {
      title: '客户来源',
      key: 'customerSource',
      width: 120,
      render: (row) =>
        row.customerSource ? customerSourceLabels[row.customerSource] || row.customerSource : '-',
    },
    {
      title: '业务日期',
      key: 'businessTime',
      width: 130,
      render: (row) => dayjs(row.businessTime).format('YYYY-MM-DD'),
    },
    { title: '金额', key: 'amount', width: 130, render: (row) => `¥${Number(row.amount || 0).toFixed(2)}` },
  ];

  function handleReset() {
    form.timePreset = 'MONTH';
    form.customRange = null;
    form.dimensionType = 'EMPLOYEE_NAME';
    form.departmentId = null;
    fetchSummary();
  }

  function parseFileName(value?: string) {
    const matched = value && /filename\*=UTF-8''([^;]+)/i.exec(value);
    return matched?.[1] ? decodeURIComponent(matched[1]) : '合同成交分析.xlsx';
  }

  async function handleExport() {
    exportLoading.value = true;
    try {
      const response = await exportContractAnalysis(buildQueryParams());
      downloadByteFile(response.data, parseFileName(response.headers?.['content-disposition']));
    } catch {
      message.error('导出失败');
    } finally {
      exportLoading.value = false;
    }
  }

  // 合同成交分析明细暂不开放导出，保留实现便于后续恢复。
  // async function handleDetailExport() {
  //   detail.exportLoading = true;
  //   try {
  //     const response = await exportContractAnalysisDetail({
  //       ...buildQueryParams(),
  //       dimensionKey: detail.dimensionKey,
  //       metricType: detail.metricType,
  //       current: detail.current,
  //       pageSize: detail.pageSize,
  //     });
  //     downloadByteFile(response.data, `${detail.title}.xlsx`);
  //   } catch {
  //     message.error('明细导出失败');
  //   } finally {
  //     detail.exportLoading = false;
  //   }
  // }

  function handlePageChange(current: number) {
    detail.current = current;
    fetchDetail();
  }

  function handlePageSizeChange(pageSize: number) {
    detail.pageSize = pageSize;
    detail.current = 1;
    fetchDetail();
  }

  onMounted(() => {
    getHomeDepartmentTree().then((result) => {
      departmentOptions.value = result;
    });
    fetchSummary();
  });
</script>
