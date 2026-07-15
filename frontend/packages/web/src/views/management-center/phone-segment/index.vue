<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <NSelect
          v-model:value="filters.province"
          clearable
          filterable
          class="min-w-[200px] flex-1 sm:max-w-[280px]"
          :placeholder="t('managementCenter.phoneSegment.provincePlaceholder')"
          :options="provinceOptions"
          @update:value="handleProvinceChange"
        />
        <NSelect
          v-model:value="filters.city"
          clearable
          filterable
          class="min-w-[200px] flex-1 sm:max-w-[280px]"
          :placeholder="t('managementCenter.phoneSegment.cityPlaceholder')"
          :options="cityOptions"
          :disabled="!filters.province"
        />
        <NButton type="primary" @click="handleSearch">{{ t('common.search') }}</NButton>
        <NButton @click="handleReset">{{ t('common.reset') }}</NButton>
      </div>
      <NDataTable :columns="columns" :data="rows" :loading="loading" :pagination="false" />
      <div class="mt-4 flex justify-end">
        <NPagination
          v-model:page="pagination.current"
          v-model:page-size="pagination.pageSize"
          :item-count="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          show-size-picker
          @update:page="loadData"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
  </CrmCard>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { NButton, NDataTable, NPagination, NSelect, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    getPlatformPhoneSegmentRegions,
    pagePlatformPhoneSegments,
    type PhoneSegmentRegionNode,
    type PlatformPhoneSegmentItem,
  } from '@/api/modules';

  import type { DataTableColumns, SelectOption } from 'naive-ui';

  const { t } = useI18n();
  const message = useMessage();

  const loading = ref(false);
  const rows = ref<PlatformPhoneSegmentItem[]>([]);
  const regionTree = ref<PhoneSegmentRegionNode[]>([]);
  const filters = reactive({
    province: null as string | null,
    city: null as string | null,
  });
  const pagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
  });

  const provinceOptions = computed<SelectOption[]>(() =>
    regionTree.value.map((item) => ({ label: item.label, value: item.value }))
  );

  const cityOptions = computed<SelectOption[]>(() => {
    const province = regionTree.value.find((item) => item.value === filters.province);
    return (province?.children || []).map((item) => ({ label: item.label, value: item.value }));
  });

  const columns = computed<DataTableColumns<PlatformPhoneSegmentItem>>(() => [
    { title: t('managementCenter.phoneSegment.province'), key: 'province', width: 120 },
    { title: t('managementCenter.phoneSegment.city'), key: 'city', width: 120 },
    { title: t('managementCenter.phoneSegment.segment'), key: 'segment', width: 120 },
    { title: t('managementCenter.phoneSegment.operator'), key: 'isp', width: 120 },
    { title: t('managementCenter.phoneSegment.areaCode'), key: 'areaCode', width: 100 },
  ]);

  async function loadRegions() {
    try {
      regionTree.value = (await getPlatformPhoneSegmentRegions()) || [];
    } catch {
      message.error(t('managementCenter.phoneSegment.loadRegionsFailed'));
    }
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await pagePlatformPhoneSegments({
        current: pagination.current,
        pageSize: pagination.pageSize,
        province: filters.province || undefined,
        city: filters.city || undefined,
      });
      rows.value = res.list || [];
      pagination.total = res.total || 0;
      pagination.current = res.current || pagination.current;
      pagination.pageSize = res.pageSize || pagination.pageSize;
    } finally {
      loading.value = false;
    }
  }

  function handleProvinceChange() {
    filters.city = null;
  }

  function handleSearch() {
    pagination.current = 1;
    loadData();
  }

  function handleReset() {
    filters.province = null;
    filters.city = null;
    pagination.current = 1;
    loadData();
  }

  function handlePageSizeChange(pageSize: number) {
    pagination.pageSize = pageSize;
    pagination.current = 1;
    loadData();
  }

  onMounted(async () => {
    await loadRegions();
    await loadData();
  });
</script>
