<template>
  <div class="flex items-center gap-[8px]">
    <div v-if="props.status === PersonalExportStatusEnum.PREPARED" class="ring-loader"></div>
    <CrmIcon v-else :type="statusMap[props.status].icon" :size="20" :class="`${statusMap[props.status].color}`" />
    <div class="font-semibold text-[var(--text-n1)]">
      {{ isImportTask ? statusMap[props.status].importLabel : statusMap[props.status].label }}
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  import { PersonalExportStatusEnum, SystemResourceMessageTypeEnum } from '@lib/shared/enums/systemEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  const { t } = useI18n();

  const props = defineProps<{
    status: PersonalExportStatusEnum;
    resourceType?: string;
  }>();

  const isImportTask = computed(() => {
    return props.resourceType === SystemResourceMessageTypeEnum.CUSTOMER_POOL_IMPORT;
  });

  const statusMap: Record<
    PersonalExportStatusEnum,
    {
      value: PersonalExportStatusEnum;
      label: string;
      importLabel: string;
      icon: string;
      color: string;
    }
  > = {
    [PersonalExportStatusEnum.STOP]: {
      value: PersonalExportStatusEnum.STOP,
      label: t('system.personal.cancelledExport'),
      importLabel: t('system.personal.cancelledImport'),
      icon: 'iconicon_block_filled',
      color: 'text-[var(--text-n4)]',
    },
    [PersonalExportStatusEnum.PREPARED]: {
      value: PersonalExportStatusEnum.PREPARED,
      label: t('system.personal.exporting'),
      importLabel: t('system.personal.importing'),
      icon: 'iconicon_loading',
      color: '',
    },
    [PersonalExportStatusEnum.ERROR]: {
      value: PersonalExportStatusEnum.ERROR,
      label: t('common.exportFailed'),
      importLabel: t('common.importFailed'),
      icon: 'iconicon_close_circle_filled',
      color: 'text-[var(--error-red)]',
    },
    [PersonalExportStatusEnum.SUCCESS]: {
      value: PersonalExportStatusEnum.SUCCESS,
      label: t('common.exportSuccessful'),
      importLabel: t('common.importSuccessful'),
      icon: 'iconicon_check_circle_filled',
      color: 'text-[var(--success-green)]',
    },
  };
</script>

<style scoped lang="less">
  .ring-loader {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: conic-gradient(
      from 0deg,
      transparent 0%,
      var(--primary-6) 10%,
      var(--primary-7) 30%,
      var(--primary-8) 50%,
      transparent 100%
    );
    mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000000 calc(100% - 2px));
    animation: spin 0.6s linear infinite;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
