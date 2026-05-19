<template>
  <div class="notify-toast">
    <div class="notify-toast__summary one-line-text">{{ summary }}</div>
    <n-button class="!mt-[8px]" text type="primary" @click.stop="handleView">
      {{ t('system.message.viewMessage') }}
    </n-button>
  </div>
</template>

<script lang="ts" setup>
  import { NButton } from 'naive-ui';

  import { SystemMessageTypeEnum } from '@lib/shared/enums/systemEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { MessageCenterItem } from '@lib/shared/models/system/message';

  import { dispatchOpenMessageDrawer } from '@/constants/messageNotify';

  const props = defineProps<{
    item: MessageCenterItem;
  }>();

  const emit = defineEmits<{
    (e: 'close'): void;
  }>();

  const { t } = useI18n();

  const summary = computed(() => {
    const { item } = props;
    if (item.type === SystemMessageTypeEnum.SYSTEM_NOTICE) {
      return item.contentText || item.subject || t('system.message.systemNotification');
    }
    try {
      const parsed = JSON.parse(item.contentText || '{}');
      return parsed.content || item.subject || '-';
    } catch {
      return item.subject || item.contentText || '-';
    }
  });

  function handleView() {
    dispatchOpenMessageDrawer();
    emit('close');
  }
</script>

<style lang="less" scoped>
  .notify-toast {
    &__summary {
      max-width: 280px;
      color: var(--text-n2);
      font-size: 13px;
      line-height: 20px;
    }
  }
</style>
