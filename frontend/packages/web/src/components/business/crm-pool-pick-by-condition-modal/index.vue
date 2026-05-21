<template>
  <CrmModal
    v-model:show="showModal"
    size="small"
    :title="t('customer.pickByCondition')"
    :ok-loading="loading"
    :positive-text="t('common.claim')"
    @confirm="confirmHandler"
    @cancel="closeHandler"
  >
    <div class="w-full space-y-[16px]">
      <div class="text-[14px] text-[var(--text-n2)]">
        {{ t('customer.pickByConditionTotal', { total }) }}
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.pickCount') }}</div>
        <n-input-number
          v-model:value="pickCount"
          class="w-full"
          :min="1"
          :max="maxPickCount"
          :precision="0"
          :show-button="true"
        />
        <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
          {{ t('customer.pickCountHint', { limit: MAX_PICK_COUNT }) }}
        </div>
      </div>
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NInputNumber, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { BatchPickOpenSeaCustomerSubmitResult, OpenSeaCustomerTableParams } from '@lib/shared/models/customer';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  import { batchPickOpenSeaCustomerByCondition } from '@/api/modules';

  const MAX_PICK_COUNT = 2000;

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    total: number;
    queryParams: OpenSeaCustomerTableParams;
  }>();

  const emit = defineEmits<{
    (e: 'success', result: BatchPickOpenSeaCustomerSubmitResult): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const maxPickCount = computed(() => Math.min(props.total, MAX_PICK_COUNT));
  const pickCount = ref<number>(1);
  const loading = ref(false);

  function clampPickCount() {
    const max = maxPickCount.value;
    if (max < 1) {
      pickCount.value = 1;
      return;
    }
    pickCount.value = Math.min(Math.max(pickCount.value || 1, 1), max);
  }

  function closeHandler() {
    pickCount.value = 1;
  }

  watch(showModal, (val) => {
    if (val) {
      pickCount.value = maxPickCount.value > 0 ? maxPickCount.value : 1;
    }
  });

  watch(
    () => props.total,
    () => {
      if (showModal.value) {
        clampPickCount();
      }
    }
  );

  function confirmHandler() {
    const count = pickCount.value;
    const max = maxPickCount.value;
    if (!count || count < 1 || count > max) {
      Message.warning(t('customer.pickCountInvalid', { max }));
      return;
    }
    loading.value = true;
    batchPickOpenSeaCustomerByCondition({
      ...props.queryParams,
      pickCount: count,
    })
      .then((data) => {
        if (!data?.accepted) {
          Message.warning(data?.message || t('common.operationFailed'));
          showModal.value = false;
          emit('success', data);
          return;
        }
        showModal.value = false;
        Message.success(data.message || '批量领取任务已提交');
        emit('success', data);
      })
      .catch(() => {
        // ignore
      })
      .finally(() => {
        loading.value = false;
      });
  }
</script>
