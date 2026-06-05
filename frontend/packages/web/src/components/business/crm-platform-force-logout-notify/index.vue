<template>
  <div class="py-[8px]">
    <div class="mb-3 text-base font-semibold text-[var(--text-n1)]">
      {{ t('managementCenter.systemMaintenance.forceLogoutModalTitle') }}
    </div>
    <div class="mb-2 text-sm text-[var(--text-n2)]">{{ messageText }}</div>
    <div class="text-sm text-[var(--warning-yellow)]">
      {{ t('managementCenter.systemMaintenance.forceLogoutCountdown', { seconds: remaining }) }}
    </div>
  </div>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, onMounted, ref } from 'vue';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  const props = defineProps<{
    messageText: string;
    countdownSeconds: number;
  }>();

  const emit = defineEmits<{
    expired: [];
  }>();

  const { t } = useI18n();
  const remaining = ref(props.countdownSeconds);
  let timer: ReturnType<typeof setInterval> | null = null;

  onMounted(() => {
    timer = setInterval(() => {
      if (remaining.value <= 1) {
        if (timer) {
          clearInterval(timer);
          timer = null;
        }
        emit('expired');
        return;
      }
      remaining.value -= 1;
    }, 1000);
  });

  onBeforeUnmount(() => {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  });
</script>
