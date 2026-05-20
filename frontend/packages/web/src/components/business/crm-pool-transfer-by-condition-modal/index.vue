<template>
  <CrmModal
    v-model:show="showModal"
    size="small"
    :title="t('customer.transferByCondition')"
    :ok-loading="loading"
    :positive-text="t('common.confirm')"
    @confirm="confirmHandler"
    @cancel="closeHandler"
  >
    <div class="w-full space-y-[16px]">
      <div class="text-[14px] text-[var(--text-n2)]">
        {{ t('customer.transferByConditionTotal', { total }) }}
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.transferCount') }}</div>
        <n-input-number
          v-model:value="transferCount"
          class="w-full"
          :min="1"
          :max="maxTransferCount"
          :precision="0"
          :show-button="true"
        />
        <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
          {{ t('customer.transferCountHint', { limit: MAX_TRANSFER_COUNT }) }}
        </div>
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.transferSelectPool') }}</div>
        <n-select
          v-model:value="targetPoolId"
          :options="poolOptions"
          value-field="id"
          label-field="name"
          :placeholder="t('customer.transferSelectPoolPlaceholder')"
          class="w-full"
        />
      </div>
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NInputNumber, NSelect, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { OpenSeaCustomerTableParams } from '@lib/shared/models/customer';
  import type { CluePoolItem } from '@lib/shared/models/system/module';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  import { batchTransferOpenSeaCustomerByCondition, getOpenSeaOptions } from '@/api/modules';

  const MAX_TRANSFER_COUNT = 2000;

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    total: number;
    queryParams: OpenSeaCustomerTableParams;
    currentPoolId: string;
  }>();

  const emit = defineEmits<{
    (e: 'success'): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const maxTransferCount = computed(() => Math.min(props.total, MAX_TRANSFER_COUNT));

  const transferCount = ref<number>(1);
  const targetPoolId = ref('');
  const poolOptions = ref<CluePoolItem[]>([]);
  const loading = ref(false);

  function clampTransferCount() {
    const max = maxTransferCount.value;
    if (max < 1) {
      transferCount.value = 1;
      return;
    }
    transferCount.value = Math.min(Math.max(transferCount.value || 1, 1), max);
  }

  async function loadPoolOptions() {
    try {
      const res = await getOpenSeaOptions();
      poolOptions.value = (res ?? []).filter((item) => item.id !== props.currentPoolId);
    } catch {
      poolOptions.value = [];
      Message.error(t('common.loadFailed'));
    }
  }

  function closeHandler() {
    targetPoolId.value = '';
  }

  watch(showModal, (val) => {
    if (val) {
      transferCount.value = maxTransferCount.value > 0 ? maxTransferCount.value : 1;
      targetPoolId.value = '';
      loadPoolOptions();
    }
  });

  watch(
    () => props.total,
    () => {
      if (showModal.value) {
        clampTransferCount();
      }
    }
  );

  function confirmHandler() {
    const count = transferCount.value;
    const max = maxTransferCount.value;
    if (!count || count < 1 || count > max) {
      Message.warning(t('customer.transferCountInvalid', { max }));
      return;
    }
    if (!targetPoolId.value) {
      Message.warning(t('customer.transferSelectPoolPlaceholder'));
      return;
    }
    loading.value = true;
    batchTransferOpenSeaCustomerByCondition({
      ...props.queryParams,
      targetPoolId: targetPoolId.value,
      transferCount: count,
    })
      .then((data) => {
        if (!data?.accepted) {
          Message.warning(data?.message || t('common.operationFailed'));
          showModal.value = false;
          emit('success');
          return;
        }
        showModal.value = false;
        Message.success(data.message || '批量转移任务已提交');
        emit('success');
      })
      .catch(() => {
        // ignore
      })
      .finally(() => {
        loading.value = false;
      });
  }
</script>
