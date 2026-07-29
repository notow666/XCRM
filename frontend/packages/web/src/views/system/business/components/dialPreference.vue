<template>
  <CrmCard hide-footer :special-height="64">
    <n-spin :show="loading">
      <div class="flex max-w-[640px] flex-col gap-[20px]">
        <div>
          <div class="font-medium text-[var(--text-n1)]">{{ t('system.personal.dialSetting.defaultCard') }}</div>
          <div class="mt-[4px] text-[var(--text-n4)]">
            {{ t('system.personal.dialSetting.description') }}
          </div>
        </div>

        <n-alert v-if="!preference.umConfigured" type="warning">
          {{ t('system.personal.dialSetting.umMissing') }}
        </n-alert>
        <n-alert v-else-if="!preference.deviceConfigured" type="warning">
          {{ t('system.personal.dialSetting.deviceMissing') }}
        </n-alert>

        <n-radio-group v-model:value="selectedCardSlotNum" class="flex flex-col gap-[12px]">
          <n-radio :value="0">
            {{ t('system.personal.dialSetting.notSet') }}
          </n-radio>
          <n-radio
            v-for="cardSlot in preference.cardSlots"
            :key="cardSlot.cardSlotNum"
            :value="cardSlot.cardSlotNum"
            :disabled="!cardSlot.available"
          >
            <span>{{ t(`system.personal.dialSetting.cardSlot${cardSlot.cardSlotNum}`) }}</span>
            <span class="ml-[8px] text-[var(--text-n4)]">
              {{ cardSlot.phone || t('system.personal.dialSetting.unavailable') }}
            </span>
          </n-radio>
        </n-radio-group>

        <div>
          <n-button type="primary" :loading="saving" :disabled="loading" @click="handleSave">
            {{ t('common.save') }}
          </n-button>
        </div>
      </div>
    </n-spin>
  </CrmCard>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { storeToRefs } from 'pinia';
  import { NAlert, NButton, NRadio, NRadioGroup, NSpin, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { MmbaPhoneCardSlotNum } from '@lib/shared/models/mmba/phone';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import useMmbaPhoneStore from '@/store/modules/mmbaPhone';

  const { t } = useI18n();
  const Message = useMessage();
  const mmbaPhoneStore = useMmbaPhoneStore();
  const { preference, loading, saving } = storeToRefs(mmbaPhoneStore);
  const selectedCardSlotNum = ref<number>(0);

  watch(
    () => preference.value.defaultCardSlotNum,
    (value) => {
      selectedCardSlotNum.value = value || 0;
    },
    { immediate: true }
  );

  onMounted(() => {
    mmbaPhoneStore.loadPreference();
  });

  async function handleSave() {
    await mmbaPhoneStore.savePreference({
      defaultCardSlotNum: selectedCardSlotNum.value ? (selectedCardSlotNum.value as MmbaPhoneCardSlotNum) : null,
    });
    Message.success(t('system.personal.dialSetting.saveSuccess'));
  }
</script>
