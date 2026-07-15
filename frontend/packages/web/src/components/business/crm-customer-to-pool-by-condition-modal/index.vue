<template>
  <CrmModal
    v-model:show="showModal"
    size="medium"
    :title="t('customer.moveToOpenSeaByCondition')"
    :ok-loading="loading"
    :positive-text="t('common.confirmMoveIn')"
    @confirm="confirmHandler"
    @cancel="closeHandler"
  >
    <div class="w-full space-y-[16px]">
      <div class="text-[14px] text-[var(--text-n2)]">
        {{ t('customer.moveToOpenSeaByConditionTotal', { total }) }}
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.toPoolCount') }}</div>
        <n-input-number
          v-model:value="toPoolCount"
          class="w-full"
          :min="1"
          :max="maxToPoolCount"
          :precision="0"
          :show-button="true"
        />
        <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
          {{ t('customer.toPoolCountHint', { limit: MAX_TO_POOL_COUNT }) }}
        </div>
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.selectTargetPool') }}</div>
        <n-select
          v-model:value="selectedPoolId"
          :options="poolOptions"
          value-field="id"
          label-field="name"
          :loading="poolLoading"
          :placeholder="t('common.pleaseSelect')"
          class="w-full"
          clearable
        />
      </div>
      <n-form v-if="enableReason" :model="form" label-placement="left">
        <n-form-item path="reason" :label="t('common.moveInReason')">
          <n-select
            v-model:value="form.reason"
            :placeholder="t('common.pleaseSelect')"
            clearable
            :options="reasonList"
          />
        </n-form-item>
      </n-form>
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NForm, NFormItem, NInputNumber, NSelect, useMessage } from 'naive-ui';

  import { ReasonTypeEnum } from '@lib/shared/enums/moduleEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerTableParams } from '@lib/shared/models/customer';

  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import type { Option } from '@/components/business/crm-select-user-drawer/type';

  import { batchToPoolCustomerByCondition, getOpenSeaOptions, getReasonConfig } from '@/api/modules';

  const MAX_TO_POOL_COUNT = 2000;

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

  const maxToPoolCount = computed(() => Math.min(props.total, MAX_TO_POOL_COUNT));
  const toPoolCount = ref<number>(1);
  const selectedPoolId = ref<string>('');
  const poolOptions = ref<Array<{ id: string; name: string }>>([]);
  const poolLoading = ref(false);
  const loading = ref(false);
  const form = ref<{ reason: string | null }>({ reason: null });
  const reasonList = ref<Option[]>([]);
  const enableReason = ref(false);

  async function loadPoolOptions() {
    try {
      poolLoading.value = true;
      const res = await getOpenSeaOptions();
      poolOptions.value = res ?? [];
    } catch (_error) {
      Message.error(t('common.loadFailed'));
    } finally {
      poolLoading.value = false;
    }
  }

  async function loadReasonList() {
    try {
      const { dictList, enable } = await getReasonConfig(ReasonTypeEnum.CUSTOMER_POOL_RS);
      enableReason.value = enable;
      reasonList.value = (dictList ?? []).filter((e) => e.id !== 'system').map((e) => ({ label: e.name, value: e.id }));
    } catch (_error) {
      // ignore
    }
  }

  function closeHandler() {
    selectedPoolId.value = '';
    form.value.reason = null;
  }

  function clampToPoolCount() {
    const max = maxToPoolCount.value;
    if (max < 1) {
      toPoolCount.value = 1;
      return;
    }
    toPoolCount.value = Math.min(Math.max(toPoolCount.value || 1, 1), max);
  }

  watch(showModal, (val) => {
    if (val) {
      toPoolCount.value = maxToPoolCount.value > 0 ? maxToPoolCount.value : 1;
      selectedPoolId.value = '';
      form.value.reason = null;
      loadPoolOptions();
      loadReasonList();
    }
  });

  watch(
    () => props.total,
    () => {
      if (showModal.value) {
        clampToPoolCount();
      }
    }
  );

  function confirmHandler() {
    const count = toPoolCount.value;
    const max = maxToPoolCount.value;
    if (!count || count < 1 || count > max) {
      Message.warning(t('customer.toPoolCountInvalid', { max }));
      return;
    }
    loading.value = true;
    batchToPoolCustomerByCondition({
      ...props.queryParams,
      toPoolCount: count,
      targetPoolId: selectedPoolId.value || undefined,
      reasonId: form.value.reason || undefined,
    })
      .then((data) => {
        if (!data?.accepted) {
          Message.warning(data?.message || t('common.operationFailed'));
          return;
        }
        showModal.value = false;
        Message.success(data.message || t('customer.batchToPoolTaskSubmitted'));
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
