<template>
  <CrmCard hide-footer no-content-bottom-padding>
    <CrmTable
      v-bind="propsRes"
      class="customer-call-record-table"
      :scroll-x="1100"
      @page-change="propsEvent.pageChange"
      @page-size-change="propsEvent.pageSizeChange"
      @sorter-change="propsEvent.sorterChange"
      @filter-change="propsEvent.filterChange"
      @refresh="initData"
    />
  </CrmCard>
</template>

<script lang="ts" setup>
  import { h, onBeforeMount, onBeforeUnmount, ref, watch } from 'vue';
  import { NButton } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerCallRecordListItem, CustomerCallRecordTableParams } from '@lib/shared/models/customer';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmNameTooltip from '@/components/pure/crm-name-tooltip/index.vue';
  import CrmTable from '@/components/pure/crm-table/index.vue';
  import { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import useTable from '@/components/pure/crm-table/useTable';

  import { getAccountCallRecord, previewAccountCallRecordAudio } from '@/api/modules';

  const props = defineProps<{
    sourceId: string;
  }>();

  const { t } = useI18n();
  const activeAudioRowId = ref('');
  const loadingAudioRowId = ref('');
  const audioUrlMap = ref<Record<string, string>>({});

  function formatDuration(duration?: number | null) {
    if (!duration || duration <= 0) {
      return '0秒';
    }
    const minutes = Math.floor(duration / 60);
    const seconds = duration % 60;
    if (minutes <= 0) {
      return `${seconds}秒`;
    }
    return `${minutes}分${String(seconds).padStart(2, '0')}秒`;
  }

  function resetAudioCache() {
    Object.values(audioUrlMap.value).forEach((url) => {
      URL.revokeObjectURL(url);
    });
    audioUrlMap.value = {};
    activeAudioRowId.value = '';
    loadingAudioRowId.value = '';
  }

  async function toggleAudio(row: CustomerCallRecordListItem) {
    if (!row.mediaFileId) {
      return;
    }
    if (activeAudioRowId.value === row.id) {
      activeAudioRowId.value = '';
      return;
    }
    if (audioUrlMap.value[row.id]) {
      activeAudioRowId.value = row.id;
      return;
    }
    loadingAudioRowId.value = row.id;
    try {
      const response = await previewAccountCallRecordAudio(row.mediaFileId);
      const blob = response.data instanceof Blob ? response.data : new Blob([response.data]);
      audioUrlMap.value[row.id] = URL.createObjectURL(blob);
      activeAudioRowId.value = row.id;
    } finally {
      loadingAudioRowId.value = '';
    }
  }

  function renderRecordingCell(row: CustomerCallRecordListItem) {
    if (!row.mediaFileId) {
      return t('customer.callRecord.noRecording');
    }
    const children = [
      h(
        NButton,
        {
          type: 'primary',
          text: true,
          loading: loadingAudioRowId.value === row.id,
          onClick: async () => {
            await toggleAudio(row);
          },
        },
        {
          default: () =>
            activeAudioRowId.value === row.id ? t('customer.callRecord.hide') : t('customer.callRecord.play'),
        }
      ),
    ];
    if (activeAudioRowId.value === row.id && audioUrlMap.value[row.id]) {
      children.push(
        h('audio', {
          src: audioUrlMap.value[row.id],
          controls: true,
          preload: 'none',
          style: 'width: 220px; vertical-align: middle;',
        })
      );
    }
    return h('div', { class: 'flex items-center gap-[8px] min-h-[32px]' }, children);
  }

  const columns = computed<CrmDataTableColumn[]>(() => [
    {
      title: t('customer.callRecord.employee'),
      key: 'employeeName',
      width: 140,
      render: (row: CustomerCallRecordListItem) => h(CrmNameTooltip, { text: row.employeeName || '-' }),
    },
    {
      title: t('customer.callRecord.beginTime'),
      key: 'beginTime',
      width: 180,
    },
    {
      title: t('customer.callRecord.endTime'),
      key: 'endTime',
      width: 180,
    },
    {
      title: t('customer.callRecord.connectStatus'),
      key: 'isConnected',
      width: 120,
      render: (row: CustomerCallRecordListItem) =>
        row.isConnected === 1 ? t('customer.callRecord.connected') : t('customer.callRecord.notConnected'),
    },
    {
      title: t('customer.callRecord.duration'),
      key: 'duration',
      width: 120,
      render: (row: CustomerCallRecordListItem) => formatDuration(row.duration),
    },
    {
      title: t('customer.callRecord.recording'),
      key: 'mediaFileId',
      width: 320,
      render: (row: CustomerCallRecordListItem) => renderRecordingCell(row),
    },
  ]);

  const { propsRes, propsEvent, loadList, setLoadListParams } = useTable<CustomerCallRecordListItem>(
    (params?: CustomerCallRecordTableParams) => getAccountCallRecord(params as CustomerCallRecordTableParams),
    {
      showSetting: false,
      columns: columns.value,
      containerClass: '.customer-call-record-table',
    }
  );

  function initData() {
    resetAudioCache();
    setLoadListParams({
      sourceId: props.sourceId,
    });
    loadList();
  }

  watch(
    () => props.sourceId,
    () => {
      initData();
    }
  );

  onBeforeMount(() => {
    initData();
  });

  onBeforeUnmount(() => {
    resetAudioCache();
  });
</script>

<style lang="less" scoped></style>
