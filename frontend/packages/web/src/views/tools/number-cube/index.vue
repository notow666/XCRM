<template>
  <CrmCard hide-footer no-content-padding :special-height="licenseStore.expiredDuring ? 64 : 0">
    <div class="mb-[16px] flex flex-wrap items-center justify-between gap-[12px] px-[16px] pt-[16px]">
      <div class="flex shrink-0 flex-wrap items-center gap-[12px]">
        <NButton v-permission="['NUMBER_CUBE:ADD']" type="primary" @click="openCreateModal">
          {{ t('numberCube.create') }}
        </NButton>
      </div>
      <div class="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-[12px]">
        <NSelect
          v-model:value="filters.province"
          clearable
          filterable
          class="!w-[160px]"
          :placeholder="t('numberCube.provincePlaceholder')"
          :options="provinceOptions"
          @update:value="handleFilterProvinceChange"
        />
        <NSelect
          v-model:value="filters.city"
          clearable
          filterable
          class="!w-[160px]"
          :placeholder="t('numberCube.cityPlaceholder')"
          :options="filterCityOptions"
          :disabled="!filters.province"
        />
        <NDatePicker
          v-model:value="filters.createTimeRange"
          type="daterange"
          clearable
          class="!w-[280px]"
          :placeholder="t('numberCube.createTimePlaceholder')"
        />
        <NButton type="primary" @click="handleSearch">{{ t('common.search') }}</NButton>
        <NButton @click="handleReset">{{ t('common.reset') }}</NButton>
      </div>
    </div>
    <NDataTable :columns="columns" :data="tableData" :loading="loading" :bordered="false" class="px-[16px]" />
    <div class="flex justify-end px-[16px] py-[16px]">
      <NPagination
        v-model:page="page.current"
        v-model:page-size="page.pageSize"
        :item-count="total"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="load"
        @update:page-size="onPageSizeChange"
      />
    </div>
  </CrmCard>

  <NModal
    v-model:show="showCreateModal"
    preset="dialog"
    :title="t('numberCube.createTitle')"
    :positive-text="t('common.confirm')"
    :negative-text="t('common.cancel')"
    style="width: 720px"
    @positive-click="handleCreate"
  >
    <div class="mt-[12px] space-y-[16px]">
      <div>
        <div class="mb-[8px] text-[var(--text-n2)]">{{ t('numberCube.regionLabel') }}</div>
        <NCascader
          v-model:value="cascaderValue"
          :options="regionOptions"
          check-strategy="child"
          filterable
          clearable
          show-path
          class="w-full"
          :placeholder="t('numberCube.regionPlaceholder')"
          @update:value="handleCreateRegionChange"
        />
      </div>
      <div v-if="selectedRegion">
        <div class="mb-[8px] flex flex-wrap items-center justify-between gap-[8px] text-[13px] text-[var(--text-n3)]">
          <span>{{
            t('numberCube.segmentStatsTotal', {
              segments: formatCount(totalSegmentCount),
              numbers: formatCount(totalNumberCount),
            })
          }}</span>
          <div class="flex items-center gap-[8px]">
            <span class="text-[var(--primary-8)]">{{
              t('numberCube.segmentStatsSelected', {
                segments: formatCount(selectedSegmentCount),
                numbers: formatCount(selectedNumberCount),
              })
            }}</span>
          </div>
        </div>
        <div class="mb-[8px] flex flex-wrap gap-[8px] text-[var(--text-n2)]">
          <span>{{ t('numberCube.segmentLabel') }}</span>
          <NButton text type="primary" size="small" class="text-[14px]" :disabled="!totalSegmentCount" @click="handleToggleSelectAll">
            {{ isAllSegmentsSelected ? t('numberCube.deselectAll') : t('numberCube.selectAll') }}
          </NButton>
        </div>
        <NSpin :show="segmentLoading">
          <div class="max-h-[360px] overflow-auto rounded border border-[var(--text-n8)] p-[8px]">
            <NCollapse
              v-if="segmentGroups.length"
              v-model:expanded-names="expandedPrefixes"
              @update:expanded-names="handleExpandGroups"
            >
              <NCollapseItem v-for="group in segmentGroups" :key="group.prefix" :name="group.prefix">
                <template #header>
                  <div class="flex items-center gap-[8px]">
                    <NCheckbox
                      :checked="isGroupAllSelected(group.prefix)"
                      :indeterminate="isGroupIndeterminate(group.prefix)"
                      :disabled="createForm.selectionMode === 'ALL'"
                      @click.stop
                      @update:checked="(checked: boolean) => toggleGroup(group.prefix, checked)"
                    />
                    <span>{{
                      t('numberCube.segmentGroupTitle', { prefix: group.prefix, count: formatCount(group.count) })
                    }}</span>
                  </div>
                </template>
                <NSpin :show="loadingPrefixSet.has(group.prefix)">
                  <NCheckboxGroup
                    v-if="segmentCache[group.prefix]?.length"
                    v-model:value="createForm.segmentIds"
                    @update:value="handleSegmentIdsChange"
                  >
                    <div class="segment-grid py-[4px] pl-[24px]">
                      <NCheckbox
                        v-for="item in segmentCache[group.prefix]"
                        :key="item.id"
                        :value="item.id"
                        :label="item.segment"
                        :disabled="createForm.selectionMode === 'ALL'"
                      />
                    </div>
                  </NCheckboxGroup>
                  <div v-else-if="!loadingPrefixSet.has(group.prefix)" class="pl-[24px] text-[var(--text-n4)]">
                    {{ t('numberCube.noSegments') }}
                  </div>
                </NSpin>
              </NCollapseItem>
            </NCollapse>
            <div v-if="!segmentLoading && segmentGroups.length === 0" class="p-[12px] text-[var(--text-n4)]">
              {{ t('numberCube.noSegments') }}
            </div>
          </div>
        </NSpin>
      </div>
    </div>
  </NModal>

  <NModal
    v-model:show="showDownloadModal"
    preset="dialog"
    :title="t('numberCube.downloadTitle')"
    :positive-text="downloading ? undefined : t('common.confirm')"
    :negative-text="t('common.cancel')"
    :show-positive-button="!downloading"
    :closable="true"
    :mask-closable="!downloading"
    style="width: 420px"
    @positive-click="confirmDownload"
    @after-leave="onDownloadModalLeave"
  >
    <div class="mt-[12px] space-y-[12px]">
      <NRadioGroup v-model:value="downloadMaskMode" :disabled="downloading">
        <NSpace vertical>
          <NRadio value="PLAIN">
            {{ t('numberCube.downloadPlain') }}
            <span v-if="isPackReady(downloadTarget, 'PLAIN')" class="ml-[6px] text-[12px] text-[var(--text-n3)]">
              ({{ t('numberCube.packCached') }})
            </span>
          </NRadio>
          <NRadio value="MASKED">
            {{ t('numberCube.downloadMasked') }}
            <span v-if="isPackReady(downloadTarget, 'MASKED')" class="ml-[6px] text-[12px] text-[var(--text-n3)]">
              ({{ t('numberCube.packCached') }})
            </span>
          </NRadio>
        </NSpace>
      </NRadioGroup>
      <div v-if="downloading" class="text-[13px] text-[var(--text-n3)]">
        <div>{{ t('numberCube.packing') }}</div>
        <div class="mt-[4px]">{{ t('numberCube.packingLeaveHint') }}</div>
        <div v-if="downloadProgressTotal != null" class="mt-[4px]">
          {{
            t('numberCube.packProgress', {
              processed: formatCount(downloadProgressProcessed || 0),
              total: formatCount(downloadProgressTotal || 0),
            })
          }}
        </div>
      </div>
    </div>
  </NModal>

  <NModal
    v-model:show="showSegmentDetailModal"
    preset="dialog"
    :title="t('numberCube.segmentDetailTitle')"
    :show-positive-button="false"
    :negative-text="t('common.close')"
    style="width: 720px"
  >
    <NSpin :show="segmentDetailLoading">
      <div v-if="segmentDetail" class="mt-[12px] space-y-[12px]">
        <div class="text-[13px] text-[var(--text-n3)]">
          {{ segmentDetail.province }} / {{ segmentDetail.city }} ·
          {{ t('numberCube.segmentCount', { count: formatCount(segmentDetail.segmentCount || 0) }) }}
        </div>
        <div class="max-h-[420px] overflow-auto rounded border border-[var(--text-n8)] p-[8px]">
          <NCollapse
            v-if="segmentDetailGroups.length"
            v-model:expanded-names="detailExpandedPrefixes"
            @update:expanded-names="handleDetailExpandGroups"
          >
            <NCollapseItem v-for="group in segmentDetailGroups" :key="group.prefix" :name="group.prefix">
              <template #header>
                <span>{{
                  t('numberCube.segmentGroupTitle', { prefix: group.prefix, count: formatCount(group.count) })
                }}</span>
              </template>
              <NSpin :show="detailLoadingPrefixSet.has(group.prefix)">
                <div class="segment-grid py-[4px] pl-[8px]">
                  <span v-for="segment in group.segments" :key="segment" class="text-[13px] text-[var(--text-n2)]">
                    {{ segment }}
                  </span>
                </div>
              </NSpin>
            </NCollapseItem>
          </NCollapse>
        </div>
      </div>
    </NSpin>
  </NModal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue';
  import {
    type CascaderOption,
    type DataTableColumns,
    NButton,
    NCascader,
    NCheckbox,
    NCheckboxGroup,
    NCollapse,
    NCollapseItem,
    NDataTable,
    NDatePicker,
    NModal,
    NPagination,
    NPopconfirm,
    NRadio,
    NRadioGroup,
    NSelect,
    NSpace,
    NSpin,
    NTooltip,
    type SelectOption,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import {
    NUMBER_CUBE_TASK_UPDATED_DOM_EVENT,
    type NumberCubeTaskUpdatedSseDetail,
  } from '@lib/shared/constants/sseEventType';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { downloadByteFile } from '@lib/shared/method';
  import type {
    NumberCubeMaskMode,
    NumberCubeSegmentGroup,
    NumberCubeSegmentItem,
    NumberCubeTask,
    NumberCubeTaskDetail,
    PhoneSegmentRegionNode,
  } from '@lib/shared/models/tools/numberCube';
  import { NUMBER_CUBE_NUMBERS_PER_SEGMENT } from '@lib/shared/models/tools/numberCube';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    addNumberCubeTask,
    deleteNumberCubeTask,
    downloadNumberCubeCachedFile,
    downloadNumberCubeFile,
    getNumberCubeDownloadProgress,
    getNumberCubeRegions,
    getNumberCubeSegmentGroups,
    getNumberCubeSegments,
    getNumberCubeTaskDetail,
    getNumberCubeTaskDetailSegments,
    getNumberCubeTaskPage,
    getNumberCubeTaskProgressBatch,
    startNumberCubeDownload,
  } from '@/api/modules';
  import useLicenseStore from '@/store/modules/setting/license';
  import { hasAnyPermission } from '@/utils/permission';

  const PROGRESS_POLL_INTERVAL_MS = 10_000;
  const DOWNLOAD_POLL_INTERVAL_MS = 10_000;
  const TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED']);
  const DOWNLOAD_SUCCESS_STATUS = 'SUCCESS';
  const DOWNLOAD_ERROR_STATUS = 'ERROR';

  const { t } = useI18n();
  const message = useMessage();
  const licenseStore = useLicenseStore();

  const loading = ref(false);
  const segmentLoading = ref(false);
  type NumberCubeTaskRow = NumberCubeTask & { progressText?: string };

  const tableData = ref<NumberCubeTaskRow[]>([]);
  const total = ref(0);
  const page = reactive({ current: 1, pageSize: 20 });
  const regionTree = ref<PhoneSegmentRegionNode[]>([]);
  const segmentGroups = ref<NumberCubeSegmentGroup[]>([]);
  const segmentCache = reactive<Record<string, NumberCubeSegmentItem[]>>({});
  const expandedPrefixes = ref<string[]>([]);
  const prevExpandedPrefixes = ref<string[]>([]);
  const loadingPrefixSet = reactive(new Set<string>());
  const showCreateModal = ref(false);
  const showDownloadModal = ref(false);
  const showSegmentDetailModal = ref(false);
  const segmentDetailLoading = ref(false);
  const segmentDetail = ref<NumberCubeTaskDetail | null>(null);
  const detailExpandedPrefixes = ref<string[]>([]);
  const prevDetailExpandedPrefixes = ref<string[]>([]);
  const detailSegmentCache = reactive<Record<string, string[]>>({});
  const detailLoadingPrefixSet = reactive(new Set<string>());
  const downloadMaskMode = ref<NumberCubeMaskMode>('PLAIN');
  const downloadTarget = ref<NumberCubeTask | null>(null);
  const downloading = ref(false);
  const downloadProgressProcessed = ref<number | null>(null);
  const downloadProgressTotal = ref<number | null>(null);
  const downloadPollTimer = ref<number | null>(null);
  const pollTimer = ref<number | null>(null);
  const cascaderValue = ref<string | null>(null);
  const selectedRegion = ref<[string, string] | null>(null);

  const REGION_VALUE_SEP = '::';

  const filters = reactive({
    province: null as string | null,
    city: null as string | null,
    createTimeRange: null as [number, number] | null,
  });

  const createForm = reactive({
    selectionMode: 'PARTIAL' as 'ALL' | 'PARTIAL',
    segmentIds: [] as string[],
  });

  const totalSegmentCount = computed(() => segmentGroups.value.reduce((sum, group) => sum + group.count, 0));
  const totalNumberCount = computed(() => totalSegmentCount.value * NUMBER_CUBE_NUMBERS_PER_SEGMENT);
  const selectedSegmentCount = computed(() =>
    createForm.selectionMode === 'ALL' ? totalSegmentCount.value : createForm.segmentIds.length
  );
  const selectedNumberCount = computed(() => selectedSegmentCount.value * NUMBER_CUBE_NUMBERS_PER_SEGMENT);
  const isAllSegmentsSelected = computed(() => createForm.selectionMode === 'ALL');

  const segmentDetailGroups = computed(() =>
    (segmentDetail.value?.segmentGroups || []).map((group) => ({
      prefix: group.prefix,
      count: group.count,
      segments: detailSegmentCache[group.prefix] || [],
    }))
  );

  function formatCount(value: number) {
    return value.toLocaleString();
  }

  function getGroupSegmentIds(prefix: string) {
    return (segmentCache[prefix] || []).map((item) => item.id);
  }

  function isGroupAllSelected(prefix: string) {
    if (createForm.selectionMode === 'ALL') {
      return true;
    }
    const ids = getGroupSegmentIds(prefix);
    if (!ids.length) {
      return false;
    }
    return ids.every((id) => createForm.segmentIds.includes(id));
  }

  function isGroupIndeterminate(prefix: string) {
    if (createForm.selectionMode === 'ALL') {
      return false;
    }
    const ids = getGroupSegmentIds(prefix);
    if (!ids.length) {
      return false;
    }
    const selectedInGroup = ids.filter((id) => createForm.segmentIds.includes(id)).length;
    return selectedInGroup > 0 && selectedInGroup < ids.length;
  }

  async function ensureGroupSegments(prefix: string) {
    if (!selectedRegion.value) {
      return [];
    }
    if (segmentCache[prefix]?.length) {
      return segmentCache[prefix];
    }
    if (loadingPrefixSet.has(prefix)) {
      return segmentCache[prefix] || [];
    }
    loadingPrefixSet.add(prefix);
    try {
      const list =
        (await getNumberCubeSegments({
          province: selectedRegion.value[0],
          city: selectedRegion.value[1],
          prefix,
        })) || [];
      segmentCache[prefix] = list;
      return list;
    } finally {
      loadingPrefixSet.delete(prefix);
    }
  }

  async function handleToggleSelectAll() {
    if (!selectedRegion.value) {
      return;
    }
    if (isAllSegmentsSelected.value) {
      createForm.selectionMode = 'PARTIAL';
      createForm.segmentIds = [];
      return;
    }
    createForm.selectionMode = 'ALL';
    createForm.segmentIds = [];
  }

  function handleSegmentIdsChange() {
    createForm.selectionMode = 'PARTIAL';
  }

  async function toggleGroup(prefix: string, checked: boolean) {
    if (createForm.selectionMode === 'ALL') {
      return;
    }
    const segments = await ensureGroupSegments(prefix);
    const ids = segments.map((item) => item.id);
    if (checked) {
      createForm.segmentIds = [...new Set([...createForm.segmentIds, ...ids])];
    } else {
      const idSet = new Set(ids);
      createForm.segmentIds = createForm.segmentIds.filter((id) => !idSet.has(id));
    }
  }

  async function handleExpandGroups(names: string[]) {
    const previous = new Set(prevExpandedPrefixes.value);
    prevExpandedPrefixes.value = [...names];
    const newPrefixes = names.filter((prefix) => !previous.has(prefix));
    await Promise.all(newPrefixes.map((prefix) => ensureGroupSegments(prefix)));
  }

  function resetSegmentState() {
    segmentGroups.value = [];
    Object.keys(segmentCache).forEach((key) => {
      delete segmentCache[key];
    });
    expandedPrefixes.value = [];
    prevExpandedPrefixes.value = [];
    loadingPrefixSet.clear();
    createForm.selectionMode = 'PARTIAL';
    createForm.segmentIds = [];
  }

  const provinceOptions = computed<SelectOption[]>(() =>
    regionTree.value.map((item) => ({ label: item.label, value: item.value }))
  );

  const filterCityOptions = computed<SelectOption[]>(() => {
    const province = regionTree.value.find((item) => item.value === filters.province);
    return (province?.children || []).map((item) => ({ label: item.label, value: item.value }));
  });

  const regionOptions = computed<CascaderOption[]>(() =>
    regionTree.value.map((province) => ({
      label: province.label,
      value: province.value,
      children: (province.children || []).map((city) => ({
        label: city.label,
        value: `${province.value}${REGION_VALUE_SEP}${city.value}`,
      })),
    }))
  );

  function parseRegionComposite(value: string | number | Array<string | number> | null): [string, string] | null {
    const raw = Array.isArray(value) ? value[value.length - 1] : value;
    if (raw == null) {
      return null;
    }
    const text = String(raw);
    const sepIndex = text.indexOf(REGION_VALUE_SEP);
    if (sepIndex <= 0) {
      return null;
    }
    const province = text.slice(0, sepIndex);
    const city = text.slice(sepIndex + REGION_VALUE_SEP.length);
    return province && city ? [province, city] : null;
  }

  function resolveRegionFromCascader(
    value: string | number | Array<string | number> | null,
    pathValues: Array<CascaderOption | null> | Array<CascaderOption[] | null> | null
  ): [string, string] | null {
    const fromComposite = parseRegionComposite(value);
    if (fromComposite) {
      return fromComposite;
    }

    if (Array.isArray(pathValues) && pathValues.length >= 2) {
      const path = pathValues.filter((item): item is CascaderOption => !!item && !Array.isArray(item));
      if (path.length >= 2) {
        const province = String(path[path.length - 2].value ?? '');
        const cityValue = String(path[path.length - 1].value ?? '');
        const fromPath = parseRegionComposite(cityValue) ?? (province && cityValue ? [province, cityValue] : null);
        if (fromPath) {
          return fromPath;
        }
      }
    }

    if (Array.isArray(value) && value.length >= 2) {
      const province = String(value[value.length - 2]);
      const city = String(value[value.length - 1]);
      return parseRegionComposite(city) ?? (province && city ? [province, city] : null);
    }

    return null;
  }

  function formatStatus(status?: string) {
    switch (status) {
      case 'PENDING':
        return t('numberCube.status.pending');
      case 'RUNNING':
        return t('numberCube.status.running');
      case 'SUCCESS':
        return t('numberCube.status.success');
      case 'FAILED':
        return t('numberCube.status.failed');
      default:
        return status || '-';
    }
  }

  function applyProgressToRow(row: NumberCubeTaskRow, processed?: number, progressTotal?: number, status?: string) {
    if (status) {
      row.status = status;
    }
    if (progressTotal != null && processed != null) {
      row.progressText = t('numberCube.progressDetail', {
        processed: formatCount(processed),
        total: formatCount(progressTotal),
      });
    } else if (TERMINAL_STATUSES.has(row.status)) {
      row.progressText = undefined;
    }
  }

  function getActiveTaskIds() {
    return tableData.value
      .filter((item) => item.status === 'PENDING' || item.status === 'RUNNING')
      .map((item) => item.id);
  }

  function updatePollTimerState(onTick: () => void) {
    const needPoll = getActiveTaskIds().length > 0;
    if (needPoll && pollTimer.value == null) {
      pollTimer.value = window.setInterval(onTick, PROGRESS_POLL_INTERVAL_MS);
    } else if (!needPoll && pollTimer.value != null) {
      window.clearInterval(pollTimer.value);
      pollTimer.value = null;
    }
  }

  async function refreshActiveTaskProgress() {
    const taskIds = getActiveTaskIds();
    if (!taskIds.length) {
      updatePollTimerState(refreshActiveTaskProgress);
      return;
    }
    try {
      const progressList = (await getNumberCubeTaskProgressBatch({ taskIds })) || [];
      const progressMap = new Map(progressList.map((item) => [item.jobId, item]));
      tableData.value.forEach((row) => {
        const progress = progressMap.get(row.id);
        if (!progress) {
          return;
        }
        applyProgressToRow(row, progress.processed, progress.total, progress.status);
      });
    } catch {
      // ignore polling errors
    } finally {
      updatePollTimerState(refreshActiveTaskProgress);
    }
  }

  async function load() {
    loading.value = true;
    try {
      const res = await getNumberCubeTaskPage({
        current: page.current,
        pageSize: page.pageSize,
        province: filters.province || undefined,
        city: filters.city || undefined,
        createTimeStart: filters.createTimeRange?.[0],
        createTimeEnd: filters.createTimeRange?.[1]
          ? dayjs(filters.createTimeRange[1]).endOf('day').valueOf()
          : undefined,
      });
      tableData.value = res.list || [];
      total.value = res.total || 0;
      page.current = res.current || page.current;
      page.pageSize = res.pageSize || page.pageSize;
      await refreshActiveTaskProgress();
    } finally {
      loading.value = false;
    }
  }

  function handleTaskUpdatedSse(event: Event) {
    const { detail } = event as CustomEvent<NumberCubeTaskUpdatedSseDetail>;
    if (!detail?.taskId) {
      return;
    }
    const row = tableData.value.find((item) => item.id === detail.taskId);
    if (row) {
      applyProgressToRow(row, detail.processed, detail.total, detail.status);
      if (detail.errorMessage) {
        row.errorMessage = detail.errorMessage;
      }
      if (detail.status && TERMINAL_STATUSES.has(detail.status)) {
        load();
      } else {
        updatePollTimerState(refreshActiveTaskProgress);
      }
      return;
    }
    if (detail.status && TERMINAL_STATUSES.has(detail.status)) {
      load();
    }
  }

  async function openSegmentDetail(row: NumberCubeTask) {
    showSegmentDetailModal.value = true;
    segmentDetailLoading.value = true;
    segmentDetail.value = null;
    detailExpandedPrefixes.value = [];
    prevDetailExpandedPrefixes.value = [];
    Object.keys(detailSegmentCache).forEach((key) => {
      delete detailSegmentCache[key];
    });
    detailLoadingPrefixSet.clear();
    try {
      segmentDetail.value = await getNumberCubeTaskDetail(row.id);
    } finally {
      segmentDetailLoading.value = false;
    }
  }

  async function loadDetailPrefixSegments(prefix: string) {
    if (!segmentDetail.value?.id || detailSegmentCache[prefix]?.length) {
      return;
    }
    detailLoadingPrefixSet.add(prefix);
    try {
      const res = await getNumberCubeTaskDetailSegments({
        taskId: segmentDetail.value.id,
        prefix,
      });
      detailSegmentCache[prefix] = res?.segments || [];
    } finally {
      detailLoadingPrefixSet.delete(prefix);
    }
  }

  async function handleDetailExpandGroups(names: string[]) {
    const previous = new Set(prevDetailExpandedPrefixes.value);
    prevDetailExpandedPrefixes.value = [...names];
    const newPrefixes = names.filter((prefix) => !previous.has(prefix));
    await Promise.all(newPrefixes.map((prefix) => loadDetailPrefixSegments(prefix)));
  }

  function clearDownloadPollTimer() {
    if (downloadPollTimer.value != null) {
      window.clearInterval(downloadPollTimer.value);
      downloadPollTimer.value = null;
    }
  }

  function resetDownloadState() {
    clearDownloadPollTimer();
    downloading.value = false;
    downloadProgressProcessed.value = null;
    downloadProgressTotal.value = null;
  }

  function onDownloadModalLeave() {
    // Pack continues on server; clear local poll only.
    resetDownloadState();
  }

  function handleDownload(row: NumberCubeTask) {
    downloadTarget.value = row;
    downloadMaskMode.value = 'PLAIN';
    resetDownloadState();
    showDownloadModal.value = true;
  }

  async function handleDelete(row: NumberCubeTask) {
    try {
      await deleteNumberCubeTask(row.id);
      message.success(t('numberCube.deleteSuccess'));
      await load();
    } catch {
      // request layer handles error toast
    }
  }

  async function downloadCachedPack(task: NumberCubeTask, maskMode: NumberCubeMaskMode) {
    const zipName = `${task.province || ''}${task.city || ''}_${maskMode}.zip`;
    const res = await downloadNumberCubeCachedFile(task.id, maskMode);
    downloadByteFile(res as Blob, zipName);
    message.success(t('numberCube.packSuccess'));
    showDownloadModal.value = false;
    resetDownloadState();
    await load();
  }

  function isPackReady(task: NumberCubeTask | null, maskMode: NumberCubeMaskMode) {
    if (!task) {
      return false;
    }
    return maskMode === 'MASKED' ? !!task.maskedPackReady : !!task.plainPackReady;
  }

  async function pollDownloadProgress(taskId: string, exportJobId: string, zipName: string) {
    try {
      const progress = await getNumberCubeDownloadProgress(exportJobId, taskId);
      if (progress?.processed != null) {
        downloadProgressProcessed.value = progress.processed;
      }
      if (progress?.total != null) {
        downloadProgressTotal.value = progress.total;
      }
      if (progress?.status === DOWNLOAD_SUCCESS_STATUS) {
        clearDownloadPollTimer();
        const res = await downloadNumberCubeFile(exportJobId, taskId);
        downloadByteFile(res as Blob, zipName);
        message.success(t('numberCube.packSuccess'));
        showDownloadModal.value = false;
        resetDownloadState();
        await load();
        return;
      }
      if (progress?.status === DOWNLOAD_ERROR_STATUS) {
        clearDownloadPollTimer();
        message.error(progress.errorMessage || t('numberCube.packFailed'));
        resetDownloadState();
      }
    } catch {
      // Transient poll failures should not abort a long-running pack.
    }
  }

  async function confirmDownload() {
    if (!downloadTarget.value || downloading.value) {
      return false;
    }
    downloading.value = true;
    downloadProgressProcessed.value = 0;
    downloadProgressTotal.value = downloadTarget.value.segmentCount || null;
    clearDownloadPollTimer();
    try {
      if (isPackReady(downloadTarget.value, downloadMaskMode.value)) {
        await downloadCachedPack(downloadTarget.value, downloadMaskMode.value);
        return false;
      }
      const startRes = await startNumberCubeDownload(downloadTarget.value.id, downloadMaskMode.value);
      if (startRes?.cached || startRes?.status === DOWNLOAD_SUCCESS_STATUS) {
        await downloadCachedPack(downloadTarget.value, downloadMaskMode.value);
        return false;
      }
      if (!startRes?.exportJobId) {
        throw new Error('missing exportJobId');
      }
      const zipName = `${downloadTarget.value.province || ''}${downloadTarget.value.city || ''}_${downloadMaskMode.value}.zip`;
      const taskId = downloadTarget.value.id;
      await pollDownloadProgress(taskId, startRes.exportJobId, zipName);
      if (downloading.value) {
        downloadPollTimer.value = window.setInterval(() => {
          pollDownloadProgress(taskId, startRes.exportJobId as string, zipName).catch(() => {
            // ignore polling errors; next tick will retry
          });
        }, DOWNLOAD_POLL_INTERVAL_MS);
      }
      return false;
    } catch {
      message.error(t('numberCube.downloadFailed'));
      resetDownloadState();
      return false;
    }
  }

  const columns = computed<DataTableColumns<NumberCubeTaskRow>>(() => [
    { title: t('numberCube.province'), key: 'province', width: 160 },
    { title: t('numberCube.city'), key: 'city', width: 160 },
    {
      title: t('numberCube.segments'),
      key: 'segmentCount',
      minWidth: 160,
      render: (row) =>
        h('div', { class: 'flex items-center gap-[8px]' }, [
          h('span', t('numberCube.segmentCount', { count: formatCount(row.segmentCount || 0) })),
          h(
            NButton,
            {
              text: true,
              type: 'primary',
              onClick: () => openSegmentDetail(row),
            },
            { default: () => t('numberCube.viewSegments') }
          ),
        ]),
    },
    {
      title: t('numberCube.statusLabel'),
      key: 'status',
      width: 240,
      render: (row) => {
        const statusText = formatStatus(row.status);
        if (row.status === 'RUNNING' || row.status === 'PENDING') {
          return h('div', { class: 'text-[13px]' }, [
            h('div', statusText),
            row.progressText ? h('div', { class: 'text-[var(--text-n4)]' }, row.progressText) : null,
          ]);
        }
        return statusText;
      },
    },
    {
      title: t('numberCube.createTime'),
      key: 'createTime',
      width: 180,
      render: (row) => (row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: t('common.operation'),
      key: 'operation',
      width: 160,
      render: (row) => {
        const actions: ReturnType<typeof h>[] = [];
        if (hasAnyPermission(['NUMBER_CUBE:DOWNLOAD'])) {
          actions.push(
            h(
              NButton,
              {
                text: true,
                type: 'primary',
                disabled: row.status !== 'SUCCESS' || downloading.value,
                onClick: () => handleDownload(row),
              },
              { default: () => t('common.download') }
            )
          );
        }
        if (hasAnyPermission(['NUMBER_CUBE:DELETE'])) {
          const deleteBtn = h(
            NButton,
            {
              text: true,
              type: 'error',
              disabled: row.status === 'RUNNING',
            },
            { default: () => t('numberCube.delete') }
          );
          if (row.status === 'RUNNING') {
            actions.push(
              h(NTooltip, null, {
                trigger: () => deleteBtn,
                default: () => t('numberCube.deleteRunningTip'),
              })
            );
          } else {
            actions.push(
              h(
                NPopconfirm,
                { onPositiveClick: () => handleDelete(row) },
                {
                  trigger: () => deleteBtn,
                  default: () => t('numberCube.deleteConfirm'),
                }
              )
            );
          }
        }
        return actions.length ? h(NSpace, { size: 8 }, { default: () => actions }) : null;
      },
    },
  ]);

  async function loadRegions() {
    regionTree.value = (await getNumberCubeRegions()) || [];
  }

  async function loadSegmentGroups(province: string, city: string) {
    segmentLoading.value = true;
    resetSegmentState();
    try {
      segmentGroups.value = (await getNumberCubeSegmentGroups({ province, city })) || [];
    } finally {
      segmentLoading.value = false;
    }
  }

  function handleFilterProvinceChange() {
    filters.city = null;
  }

  function handleSearch() {
    page.current = 1;
    load();
  }

  function handleReset() {
    filters.province = null;
    filters.city = null;
    filters.createTimeRange = null;
    page.current = 1;
    load();
  }

  function onPageSizeChange(pageSize: number) {
    page.pageSize = pageSize;
    page.current = 1;
    load();
  }

  function openCreateModal() {
    cascaderValue.value = null;
    selectedRegion.value = null;
    resetSegmentState();
    showCreateModal.value = true;
  }

  async function handleCreateRegionChange(
    value: string | number | Array<string | number> | null,
    _option: CascaderOption | Array<CascaderOption | null> | null,
    pathValues: Array<CascaderOption | null> | Array<CascaderOption[] | null> | null
  ) {
    const region = resolveRegionFromCascader(value, pathValues);
    selectedRegion.value = region;
    if (region) {
      await loadSegmentGroups(region[0], region[1]);
    } else {
      resetSegmentState();
    }
  }

  async function handleCreate() {
    if (!selectedRegion.value) {
      message.error(t('numberCube.regionRequired'));
      return false;
    }
    if (createForm.selectionMode === 'PARTIAL' && createForm.segmentIds.length === 0) {
      message.error(t('numberCube.segmentRequired'));
      return false;
    }
    try {
      await addNumberCubeTask({
        province: selectedRegion.value[0],
        city: selectedRegion.value[1],
        selectionMode: createForm.selectionMode,
        segmentIds: createForm.selectionMode === 'ALL' ? undefined : createForm.segmentIds,
      });
      message.success(t('numberCube.createSuccess'));
      showCreateModal.value = false;
      await load();
      return true;
    } catch {
      return false;
    }
  }

  onMounted(async () => {
    window.addEventListener(NUMBER_CUBE_TASK_UPDATED_DOM_EVENT, handleTaskUpdatedSse);
    await loadRegions();
    await load();
  });

  onUnmounted(() => {
    window.removeEventListener(NUMBER_CUBE_TASK_UPDATED_DOM_EVENT, handleTaskUpdatedSse);
    if (pollTimer.value != null) {
      window.clearInterval(pollTimer.value);
    }
    clearDownloadPollTimer();
  });
</script>

<style scoped lang="less">
  .segment-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px 12px;
  }
</style>
