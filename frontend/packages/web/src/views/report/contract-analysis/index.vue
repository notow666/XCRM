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

    <CrmCard hide-footer class="min-w-[1000px]">
      <div class="flex min-h-[320px] items-center justify-center px-[16px] py-[24px]">
        <n-empty :description="t('report.contract.placeholder')" />
      </div>
    </CrmCard>
  </n-scrollbar>
</template>

<script lang="ts" setup>
  import { NButton, NDatePicker, NEmpty, NForm, NFormItem, NScrollbar, NSelect, NSwitch, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  const { t } = useI18n();
  const message = useMessage();

  /** 与员工跟进分析页一致，后续接合同报表无数据行过滤 */
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

  function handleReset() {
    defaultForm();
  }

  function handleQuery() {
    // Contract report API later
  }

  function handleExport() {
    message.info(t('report.exportSoon'));
  }
</script>
