<template>
  <CrmModal
    v-model:show="showModal"
    size="small"
    :title="t('poolTransfer.title')"
    :ok-loading="transferring"
    :ok-button-props="{ disabled: !targetPoolId }"
    @cancel="cancel"
    @confirm="confirm"
  >
    <div>
      <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('poolTransfer.selectPool') }}</div>
      <n-select
        v-model:value="targetPoolId"
        :options="poolOptions"
        value-field="id"
        label-field="name"
        :placeholder="t('poolTransfer.selectPoolPlaceholder')"
        class="w-full"
      />
    </div>
  </CrmModal>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { NSelect, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  import { batchTransferPoolCustomer, getOpenSeaOptions, transferPoolCustomer } from '@/api/modules';

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    customerId?: string;
    batchIds?: string[];
  }>();

  const emit = defineEmits<{
    (e: 'success'): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const targetPoolId = ref('');
  const poolOptions = ref<Array<{ id: string; name: string }>>([]);
  const transferring = ref(false);

  async function loadPoolOptions() {
    try {
      const res = await getOpenSeaOptions();
      poolOptions.value = res ?? [];
    } catch (_error) {
      Message.error(t('common.loadFailed'));
    }
  }

  watch(showModal, (val) => {
    if (val) {
      targetPoolId.value = '';
      loadPoolOptions();
    }
  });

  function cancel() {
    showModal.value = false;
    targetPoolId.value = '';
  }

  async function confirm() {
    if (!targetPoolId.value) {
      Message.warning(t('poolTransfer.selectPoolPlaceholder'));
      return;
    }
    transferring.value = true;
    try {
      if (props.customerId) {
        await transferPoolCustomer(props.customerId, targetPoolId.value);
      } else if (props.batchIds && props.batchIds.length > 0) {
        await batchTransferPoolCustomer(props.batchIds, targetPoolId.value);
      }
      Message.success(t('poolTransfer.transferSuccess'));
      emit('success');
      cancel();
    } catch (_error) {
      Message.error(t('poolTransfer.transferFailed'));
    } finally {
      transferring.value = false;
    }
  }
</script>

<style scoped></style>
