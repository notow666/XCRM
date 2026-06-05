<template>
  <div>
    <div class="notify-title">
      <div class="one-line-text flex items-center">
        <CrmIcon class="mr-[8px] text-[var(--warning-yellow)]" type="iconicon_info_circle_filled" :size="24" />
        <n-tooltip :delay="300">
          <template #trigger>
            <div class="one-line-text">{{ subject }}</div>
          </template>
          {{ subject }}
        </n-tooltip>
      </div>
    </div>
    <div class="notify-content py-[16px]">
      <div v-html="(content || '').replace(/\n/g, '<br />')"></div>
    </div>
    <div class="flex justify-end">
      <n-button class="!bg-[var(--primary-8)]" type="primary" @click="emit('close')">
        {{ t('managementCenter.systemMaintenance.platformAnnouncementAck') }}
      </n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { NButton, NTooltip } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';

  defineProps<{
    subject: string;
    content: string;
  }>();

  const emit = defineEmits<{
    close: [];
  }>();

  const { t } = useI18n();
</script>

<style scoped lang="less">
  .notify-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-n1);
  }

  .notify-content {
    color: var(--text-n2);
    font-size: 14px;
    line-height: 1.6;
  }
</style>
