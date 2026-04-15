<template>
  <CrmModal
    v-model:show="showModal"
    size="medium"
    :title="t('customer.selectTargetPool')"
    :ok-loading="loading"
    :ok-button-props="{ disabled: !selectedPoolId }"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  >
    <div class="mb-[16px] text-[var(--text-n3)]">{{ t('customer.selectPoolTip') }}</div>
    <n-select
      v-model:value="selectedPoolId"
      :options="poolOptions"
      :value-field="'id'"
      :label-field="'name'"
      :loading="loading"
      :placeholder="t('common.pleaseSelect')"
      class="w-full"
    />
  </CrmModal>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { NSelect, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  import { getOpenSeaOptions } from '@/api/modules';

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    name?: string;
  }>();

  const emit = defineEmits<{
    (e: 'confirm', poolId: string): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const selectedPoolId = ref<string>('');
  const poolOptions = ref<Array<{ id: string; name: string }>>([]);
  const loading = ref(false);

  async function loadPoolOptions() {
    try {
      loading.value = true;
      const res = await getOpenSeaOptions();
      poolOptions.value = res ?? [];
    } catch (_error) {
      Message.error(t('common.loadFailed'));
    } finally {
      loading.value = false;
    }
  }

  watch(showModal, (val) => {
    if (val) {
      selectedPoolId.value = '';
      loadPoolOptions();
    }
  });

  function handleCancel() {
    showModal.value = false;
    selectedPoolId.value = '';
  }

  function handleConfirm() {
    if (!selectedPoolId.value) {
      Message.warning(t('common.pleaseSelect'));
      return;
    }
    emit('confirm', selectedPoolId.value);
    handleCancel();
  }
</script>

<style scoped></style>
