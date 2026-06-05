<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div class="text-base font-semibold text-[var(--text-n1)]">
          <span>{{ t('menu.managementCenter.overview') }}</span>
          <span class="font-mono tabular-nums ml-4 text-[var(--text-n4)]">{{ clockText }}</span>
        </div>
        <div class="flex flex-wrap items-center gap-3 text-xs text-[var(--text-n4)]">
          <span>{{ t('managementCenter.overview.autoRefreshHint') }}</span>
        </div>
      </div>

      <n-spin :show="loading">
        <div class="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <div
            v-for="card in kpiCards"
            :key="card.key"
            class="rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-4"
          >
            <div class="text-sm text-[var(--text-n4)]">{{ card.label }}</div>
            <div class="mt-2 text-2xl font-semibold text-[var(--text-n1)]">{{ card.value }}</div>
            <div v-if="card.note" class="mt-1 text-xs text-[var(--text-n4)]">{{ card.note }}</div>
          </div>
        </div>

        <div class="grid gap-4 lg:grid-cols-4">
          <div
            class="flex min-h-[320px] flex-col overflow-visible rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-3 lg:col-span-1"
          >
            <div class="mb-2 shrink-0 text-sm font-medium text-[var(--text-n2)]">
              {{ t('managementCenter.overview.chartTenantStatus') }}
            </div>
            <div class="relative min-h-[280px] flex-1">
              <CrmChart
                v-if="tenantStatusPie.length > 0"
                :type="ChartTypeEnum.DONUT"
                layout="compact"
                :group-name="t('managementCenter.overview.chartTenantStatus')"
                :data-indicator-name="t('crmViewSelect.counts')"
                :aggregation-method-name="t('crmViewSelect.count')"
                :data="tenantStatusPie"
                :is-full-screen="false"
              />
              <n-empty v-else class="flex h-full items-center justify-center" />
            </div>
          </div>

          <div
            class="flex min-h-[300px] flex-col overflow-hidden rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-3 lg:col-span-2"
          >
            <div class="mb-2 shrink-0 text-sm font-medium text-[var(--text-n2)]">
              {{ t('managementCenter.overview.chartOnlineByTenant') }}
            </div>
            <div class="relative min-h-[260px] flex-1">
              <CrmChart
                v-if="tenantBar.hasData"
                :type="ChartTypeEnum.BAR"
                :group-name="t('managementCenter.overview.chartOnlineByTenant')"
                :data-indicator-name="t('crmViewSelect.counts')"
                :aggregation-method-name="t('crmViewSelect.count')"
                :x-data="tenantBar.xData"
                :data="tenantBar.data"
                :is-full-screen="false"
              />
              <n-empty v-else class="flex h-full items-center justify-center" />
            </div>
          </div>

          <div
            class="flex min-h-[320px] flex-col overflow-visible rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-3 lg:col-span-1"
          >
            <div class="mb-2 shrink-0 text-sm font-medium text-[var(--text-n2)]">
              {{ t('managementCenter.overview.chartActiveCoverage') }}
            </div>
            <div class="relative min-h-[280px] flex-1">
              <CrmChart
                v-if="coveragePie.length > 0"
                :type="ChartTypeEnum.DONUT"
                layout="compact"
                :group-name="t('managementCenter.overview.chartActiveCoverage')"
                :data-indicator-name="t('crmViewSelect.counts')"
                :aggregation-method-name="t('crmViewSelect.count')"
                :data="coveragePie"
                :is-full-screen="false"
              />
              <n-empty v-else class="flex h-full items-center justify-center" />
            </div>
          </div>
        </div>
      </n-spin>
    </div>
  </CrmCard>
</template>

<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref } from 'vue';
  import { NEmpty, NSpin } from 'naive-ui';
  import dayjs from 'dayjs';

  import { PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT } from '@lib/shared/constants/sseEventType';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmChart from '@/components/pure/crm-chart/index.vue';
  import { ChartTypeEnum } from '@/components/pure/crm-chart/type';

  import { getPlatformOverview, type PlatformOverview, type PlatformOverviewSeriesItem } from '@/api/modules';

  const REFRESH_MS = 60_000;

  const { t } = useI18n();

  const loading = ref(false);
  const overview = ref<PlatformOverview | null>(null);
  const now = ref(dayjs());

  let refreshTimer: ReturnType<typeof setInterval> | null = null;
  let clockTimer: ReturnType<typeof setInterval> | null = null;

  const clockText = computed(() => now.value.format('YYYY-MM-DD HH:mm:ss'));

  function tickClock() {
    now.value = dayjs();
  }

  function mapSeriesLabel(name: string) {
    const key = String(name || '').toUpperCase();
    if (key === 'ACTIVE') return t('managementCenter.overview.statusActive');
    if (key === 'FROZEN') return t('managementCenter.overview.statusFrozen');
    if (key === 'WITH_ONLINE') return t('managementCenter.overview.coverageWithOnline');
    if (key === 'WITHOUT_ONLINE') return t('managementCenter.overview.coverageWithoutOnline');
    if (key === 'OTHER') return t('managementCenter.overview.seriesOther');
    return name;
  }

  function toPieData(series?: PlatformOverviewSeriesItem[]) {
    return (series || []).map((item) => ({
      name: mapSeriesLabel(item.name),
      value: Number(item.value) || 0,
    }));
  }

  function toBarData(series?: PlatformOverviewSeriesItem[]) {
    const items = (series || []).map((item) => ({
      name: item.name === 'OTHER' ? t('managementCenter.overview.seriesOther') : item.name,
      value: Number(item.value) || 0,
    }));
    return {
      xData: items.map((i) => i.name),
      data: items.map((i) => ({ value: i.value })),
      hasData: items.length > 0,
    };
  }

  const kpiCards = computed(() => {
    const o = overview.value;
    const extraNote =
      o && (o.onlinePlatformUserCount > 0 || o.onlineDataSpecialistUserCount > 0)
        ? t('managementCenter.overview.kpiExtraNote', {
            platform: o.onlinePlatformUserCount,
            specialist: o.onlineDataSpecialistUserCount,
          })
        : undefined;
    return [
      { key: 'tenantTotal', label: t('managementCenter.overview.kpiTenantTotal'), value: o?.tenantTotal ?? 0 },
      { key: 'tenantActive', label: t('managementCenter.overview.kpiTenantActive'), value: o?.tenantActive ?? 0 },
      {
        key: 'onlineTotal',
        label: t('managementCenter.overview.kpiOnlineTotal'),
        value: o?.onlineUserTotal ?? 0,
        note: extraNote,
      },
      {
        key: 'onlineTenant',
        label: t('managementCenter.overview.kpiOnlineTenant'),
        value: o?.onlineTenantUserTotal ?? 0,
      },
      {
        key: 'multiDevice',
        label: t('managementCenter.overview.kpiOnlineMultiDevice'),
        value: o?.onlineMultiDeviceUserCount ?? 0,
      },
    ];
  });

  const tenantStatusPie = computed(() => toPieData(overview.value?.tenantStatusSeries));
  const coveragePie = computed(() => toPieData(overview.value?.activeTenantOnlineCoverageSeries));
  const tenantBar = computed(() => toBarData(overview.value?.onlineByTenantSeries));

  async function loadOverview() {
    loading.value = true;
    try {
      overview.value = await getPlatformOverview();
    } finally {
      loading.value = false;
    }
  }

  onMounted(() => {
    tickClock();
    clockTimer = setInterval(tickClock, 1000);
    loadOverview();
    refreshTimer = setInterval(loadOverview, REFRESH_MS);
    window.addEventListener(PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT, loadOverview);
  });

  onUnmounted(() => {
    if (clockTimer) {
      clearInterval(clockTimer);
      clockTimer = null;
    }
    if (refreshTimer) {
      clearInterval(refreshTimer);
      refreshTimer = null;
    }
    window.removeEventListener(PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT, loadOverview);
  });
</script>
