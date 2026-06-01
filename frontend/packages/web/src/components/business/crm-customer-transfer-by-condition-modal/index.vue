<template>
  <CrmModal
    v-model:show="showModal"
    size="medium"
    :title="t('customer.transferByCondition')"
    :ok-loading="loading"
    :positive-text="t('common.transfer')"
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
      <CrmPoolAssignUserSelect v-model:selected-ids="selectedUserIds" multiple show-selected-count />
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NInputNumber, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerTableParams } from '@lib/shared/models/customer';

  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import CrmPoolAssignUserSelect from '@/components/business/crm-pool-assign-user-select/index.vue';

  import { batchTransferCustomerByCondition } from '@/api/modules';

  const MAX_TRANSFER_COUNT = 2000;

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    total: number;
    queryParams: CustomerTableParams;
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
  const selectedUserIds = ref<string[]>([]);
  const loading = ref(false);

  function closeHandler() {
    selectedUserIds.value = [];
  }

  function clampTransferCount() {
    const max = maxTransferCount.value;
    if (max < 1) {
      transferCount.value = 1;
      return;
    }
    transferCount.value = Math.min(Math.max(transferCount.value || 1, 1), max);
  }

  watch(showModal, (val) => {
    if (val) {
      transferCount.value = maxTransferCount.value > 0 ? maxTransferCount.value : 1;
      selectedUserIds.value = [];
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
    if (!selectedUserIds.value.length) {
      Message.warning(t('opportunity.selectReceiverPlaceholder'));
      return;
    }
    loading.value = true;
    batchTransferCustomerByCondition({
      ...props.queryParams,
      transferCount: count,
      ownerUserIds: selectedUserIds.value,
    })
      .then((data) => {
        showModal.value = false;
        const failCount = typeof data === 'number' ? data : 0;
        if (failCount > 0) {
          Message.warning(t('customer.transferByConditionPartial', { count: failCount }));
        } else {
          Message.success(t('common.transferSuccess'));
        }
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
