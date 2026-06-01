<template>
  <CrmModal
    v-model:show="showModal"
    size="medium"
    :title="t('customer.assignByCondition')"
    :ok-loading="loading"
    :positive-text="t('common.distribute')"
    @confirm="confirmHandler"
    @cancel="closeHandler"
  >
    <div class="w-full space-y-[16px]">
      <div class="text-[14px] text-[var(--text-n2)]">
        {{ t('customer.assignByConditionTotal', { total }) }}
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.assignCount') }}</div>
        <n-input-number
          v-model:value="assignCount"
          class="w-full"
          :min="1"
          :max="maxAssignCount"
          :precision="0"
          :show-button="true"
        />
        <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
          {{ t('customer.assignCountHint', { limit: MAX_ASSIGN_COUNT }) }}
        </div>
      </div>
      <CrmPoolAssignUserSelect
        v-model:selected-ids="selectedUserIds"
        :pool-id="poolId"
        multiple
        show-selected-count
      />
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NInputNumber, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { OpenSeaCustomerTableParams } from '@lib/shared/models/customer';

  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import CrmPoolAssignUserSelect from '@/components/business/crm-pool-assign-user-select/index.vue';

  import { batchAssignOpenSeaCustomerByCondition } from '@/api/modules';

  const MAX_ASSIGN_COUNT = 2000;

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    total: number;
    poolId: string;
    queryParams: OpenSeaCustomerTableParams;
  }>();

  const emit = defineEmits<{
    (e: 'success'): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const maxAssignCount = computed(() => Math.min(props.total, MAX_ASSIGN_COUNT));

  const assignCount = ref<number>(1);
  const selectedUserIds = ref<string[]>([]);
  const loading = ref(false);

  function closeHandler() {
    selectedUserIds.value = [];
  }

  function clampAssignCount() {
    const max = maxAssignCount.value;
    if (max < 1) {
      assignCount.value = 1;
      return;
    }
    assignCount.value = Math.min(Math.max(assignCount.value || 1, 1), max);
  }

  watch(showModal, (val) => {
    if (val) {
      assignCount.value = maxAssignCount.value > 0 ? maxAssignCount.value : 1;
      selectedUserIds.value = [];
    }
  });

  watch(
    () => props.total,
    () => {
      if (showModal.value) {
        clampAssignCount();
      }
    }
  );

  function confirmHandler() {
    const count = assignCount.value;
    const max = maxAssignCount.value;
    if (!count || count < 1 || count > max) {
      Message.warning(t('customer.assignCountInvalid', { max }));
      return;
    }
    if (!selectedUserIds.value.length) {
      Message.warning(t('opportunity.selectReceiverPlaceholder'));
      return;
    }
    loading.value = true;
    batchAssignOpenSeaCustomerByCondition({
      ...props.queryParams,
      assignCount: count,
      assignUserId: selectedUserIds.value[0] || '',
      assignUserIds: selectedUserIds.value,
    })
      .then((data) => {
        if (!data?.accepted) {
          Message.warning(data?.message || t('common.operationFailed'));
          showModal.value = false;
          emit('success');
          return;
        }
        showModal.value = false;
        Message.success(data.message || '批量分配任务已提交');
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
