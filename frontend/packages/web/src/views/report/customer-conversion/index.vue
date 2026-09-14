<template>
  <n-scrollbar x-scrollable :content-style="{ minWidth: '1000px', width: '100%', height: '100%' }">
    <CrmCard hide-footer auto-height class="mb-[16px] min-w-[1000px]">
      <div class="flex flex-wrap items-end justify-between gap-[16px]">
        <n-form label-placement="left" label-width="auto" :model="form" class="flex flex-wrap gap-[16px]">
          <n-form-item label="统计时间">
            <n-date-picker v-model:value="form.range" type="daterange" class="w-[280px]" />
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
        <n-button type="primary" secondary :loading="exportLoading" @click="handleExport">导出</n-button>
      </div>
    </CrmCard>
    <CrmCard hide-footer class="min-w-[1000px]">
      <n-data-table :columns="columns" :data="rows" :loading="loading" :bordered="false" :scroll-x="1000" />
    </CrmCard>
  </n-scrollbar>
  <!-- 明细导出功能暂不开放，仅隐藏页面入口，保留后端接口及前端处理逻辑。 -->
  <DrilldownModal
    v-model:show="detail.visible"
    :title="detail.title"
    :loading="detail.loading"
    :data="detail.list"
    :columns="detailColumns"
    :total="detail.total"
    :current="detail.current"
    :page-size="detail.pageSize"
    :scroll-x="950"
    :show-export="false"
    :export-loading="detail.exportLoading"
    @page-change="handlePageChange"
    @page-size-change="handlePageSizeChange"
    @export="handleDetailExport"
  />
</template>

<script lang="ts" setup>
  import { h, onMounted, reactive, ref } from 'vue';
  import {
    type DataTableColumns,
    NButton,
    NDataTable,
    NDatePicker,
    NForm,
    NFormItem,
    NScrollbar,
    NSelect,
    NTooltip,
    NTreeSelect,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import { downloadByteFile } from '@lib/shared/method';
  import type { ReportRangeParams } from '@lib/shared/models/report/contractAnalysis';
  import type {
    CustomerConversionDetailItem,
    CustomerConversionDetailParams,
    CustomerConversionEventType,
    CustomerConversionSummaryItem,
  } from '@lib/shared/models/report/customerConversion';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import type { CrmTreeNodeData } from '@/components/pure/crm-tree/type';
  import DrilldownModal from '@/views/report/employee-follow-up/components/drilldownModal.vue';

  import {
    exportCustomerConversion,
    exportCustomerConversionDetail,
    getCustomerConversionDetail,
    getCustomerConversionSummary,
    getHomeDepartmentTree,
  } from '@/api/modules';

  const message = useMessage();
  const defaultRange = (): [number, number] => [dayjs().startOf('month').valueOf(), dayjs().endOf('day').valueOf()];
  const form = reactive({
    range: defaultRange(),
    dimensionType: 'EMPLOYEE_NAME' as CustomerConversionDetailParams['dimensionType'],
    departmentId: null as string | null,
  });
  const departmentOptions = ref<CrmTreeNodeData[]>([]);
  const dimensionOptions = [
    { label: '员工名称', value: 'EMPLOYEE_NAME' },
    { label: '员工部门', value: 'EMPLOYEE_DEPT' },
    { label: '统计时间-天', value: 'STAT_DAY' },
    { label: '统计时间-月', value: 'STAT_MONTH' },
  ];
  const loading = ref(false);
  const exportLoading = ref(false);
  const rows = ref<CustomerConversionSummaryItem[]>([]);

  function buildQuery(): ReportRangeParams {
    return {
      startTime: form.range?.[0],
      endTime: form.range?.[1],
      dimensionType: form.dimensionType,
      departmentId: form.departmentId || undefined,
    };
  }

  async function fetchSummary() {
    loading.value = true;
    try {
      rows.value = await getCustomerConversionSummary(buildQuery());
    } finally {
      loading.value = false;
    }
  }

  const labels: Record<CustomerConversionEventType, string> = {
    VISIT: '上门',
    CONTRACT_SIGNED: '签约',
    PAYMENT_APPROVED: '回款',
  };
  const detail = reactive({
    visible: false,
    loading: false,
    exportLoading: false,
    title: '',
    eventType: 'VISIT' as CustomerConversionEventType,
    dimensionKey: '',
    current: 1,
    pageSize: 20,
    total: 0,
    list: [] as CustomerConversionDetailItem[],
  });

  async function fetchDetail() {
    detail.loading = true;
    try {
      const result = await getCustomerConversionDetail({
        ...buildQuery(),
        dimensionType: form.dimensionType,
        dimensionKey: detail.dimensionKey,
        eventType: detail.eventType,
        current: detail.current,
        pageSize: detail.pageSize,
      });
      detail.list = result.list || [];
      detail.total = result.total || 0;
    } finally {
      detail.loading = false;
    }
  }

  function openDetail(eventType: CustomerConversionEventType, row: CustomerConversionSummaryItem) {
    detail.eventType = eventType;
    detail.dimensionKey = row.dimensionKey;
    detail.current = 1;
    detail.title = `${row.dimensionLabel} - ${labels[eventType]}明细`;
    detail.visible = true;
    fetchDetail();
  }

  function countButton(eventType: CustomerConversionEventType, value: number, row: CustomerConversionSummaryItem) {
    return h(NButton, { text: true, type: 'primary', onClick: () => openDetail(eventType, row) }, () => value);
  }

  function rateColumnTitle(title: string, description: string) {
    return () =>
      h(NTooltip, null, {
        trigger: () =>
          h(
            'span',
            {
              class: 'cursor-help border-b border-dashed border-[var(--text-n5)]',
              tabindex: 0,
              'aria-label': `${title}：${description}`,
            },
            title
          ),
        default: () => description,
      });
  }

  const columns: DataTableColumns<CustomerConversionSummaryItem> = [
    { title: '统计维度', key: 'dimensionLabel', width: 180 },
    {
      title: '上门客户数',
      key: 'visitCustomerCount',
      render: (row) => countButton('VISIT', row.visitCustomerCount, row),
    },
    {
      title: '签约客户数',
      key: 'signedCustomerCount',
      render: (row) => countButton('CONTRACT_SIGNED', row.signedCustomerCount, row),
    },
    {
      title: '回款客户数',
      key: 'paymentCustomerCount',
      render: (row) => countButton('PAYMENT_APPROVED', row.paymentCustomerCount, row),
    },
    { title: rateColumnTitle('到店签单率', '签约客户数/上门客户数'), key: 'signRate' },
    { title: rateColumnTitle('到店转化率', '回款客户数/上门客户数'), key: 'paymentRate' },
  ];

  const detailColumns: DataTableColumns<CustomerConversionDetailItem> = [
    { title: '客户', key: 'customerName', width: 160 },
    { title: '手机号', key: 'customerMobile', width: 140 },
    { title: '负责人/签约人', key: 'employeeName', width: 140 },
    { title: '部门', key: 'departmentName', width: 180 },
    { title: '业务日期', key: 'eventTime', width: 140, render: (row) => dayjs(row.eventTime).format('YYYY-MM-DD') },
  ];

  function handleReset() {
    form.range = defaultRange();
    form.dimensionType = 'EMPLOYEE_NAME';
    form.departmentId = null;
    fetchSummary();
  }

  async function handleExport() {
    exportLoading.value = true;
    try {
      const response = await exportCustomerConversion(buildQuery());
      const matched = /filename\*=UTF-8''([^;]+)/i.exec(response.headers?.['content-disposition'] || '');
      downloadByteFile(response.data, matched?.[1] ? decodeURIComponent(matched[1]) : '客户转化报表.xlsx');
    } catch {
      message.error('导出失败');
    } finally {
      exportLoading.value = false;
    }
  }

  async function handleDetailExport() {
    detail.exportLoading = true;
    try {
      const response = await exportCustomerConversionDetail({
        ...buildQuery(),
        dimensionType: form.dimensionType,
        dimensionKey: detail.dimensionKey,
        eventType: detail.eventType,
        current: detail.current,
        pageSize: detail.pageSize,
      });
      const matched = /filename\*=UTF-8''([^;]+)/i.exec(response.headers?.['content-disposition'] || '');
      downloadByteFile(response.data, matched?.[1] ? decodeURIComponent(matched[1]) : `客户转化-${labels[detail.eventType]}明细.xlsx`);
    } catch {
      message.error('明细导出失败');
    } finally {
      detail.exportLoading = false;
    }
  }

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
