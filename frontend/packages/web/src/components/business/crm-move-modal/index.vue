<template>
  <CrmModal
    v-model:show="showModal"
    size="small"
    :title="title"
    show-icon
    :mask-closable="false"
    :type="props.type"
    :ok-button-props="{ disabled: enableReason ? !form.reason : false, type: props.type || 'primary' }"
    :positive-text="t('common.confirmMoveIn')"
    :ok-loading="loading"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  >
    <div class="mb-[16px]">{{ contentTip }}</div>
    <div v-if="props.byCondition" class="mb-[16px] space-y-[16px]">
      <div class="text-[14px] text-[var(--text-n2)]">
        {{ t('customer.moveToOpenSeaByConditionTotal', { total: props.total ?? 0 }) }}
      </div>
      <div>
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('customer.moveCount') }}</div>
        <n-input-number
          v-model:value="moveCount"
          class="w-full"
          :min="1"
          :max="maxMoveCount"
          :precision="0"
          :show-button="true"
        />
        <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
          {{ t('customer.moveCountHint', { limit: MAX_MOVE_COUNT }) }}
        </div>
      </div>
    </div>
    <n-form v-if="enableReason" ref="formRef" :model="form" label-placement="left" require-mark-placement="left">
      <n-form-item path="reason" :label="t('common.moveInReason')">
        <n-select v-model:value="form.reason" :placeholder="t('common.pleaseSelect')" clearable :options="reasonList" />
      </n-form-item>
    </n-form>
  </CrmModal>
  <toPublicPoolResultModal
    v-model:show="showToPoolResultModel"
    :fail-count="failCount"
    :success-count="successCount"
    :title="resultTitle"
    :reason-key="props.reasonKey"
    @cancel="handleResultCancel"
  />
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { DataTableRowKey, FormInst, NForm, NFormItem, NInputNumber, NSelect, useMessage } from 'naive-ui';

  import { ReasonTypeEnum } from '@lib/shared/enums/moduleEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { characterLimit } from '@lib/shared/method';
  import {
    BatchMoveToPublicPoolParams,
    CustomerTableParams,
    MoveToPublicPoolParams,
  } from '@lib/shared/models/customer';

  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import type { Option } from '@/components/business/crm-select-user-drawer/type';
  import toPublicPoolResultModal from './toPublicPoolResultModal.vue';

  import {
    batchMoveCustomer,
    batchMoveCustomerByCondition,
    batchToCluePool,
    getReasonConfig,
    moveCustomerToPool,
    moveToLeadPool,
  } from '@/api/modules';

  const MAX_MOVE_COUNT = 2000;
  const { t } = useI18n();
  const Message = useMessage();

  export type ReasonKey = ReasonTypeEnum.CLUE_POOL_RS | ReasonTypeEnum.CUSTOMER_POOL_RS;

  const props = defineProps<{
    reasonKey: ReasonKey;
    name?: string;
    sourceId?: DataTableRowKey[] | DataTableRowKey;
    queryParams?: CustomerTableParams;
    byCondition?: boolean;
    total?: number;
    poolId?: string;
    type?: 'error' | 'success' | 'warning' | 'info';
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const emit = defineEmits<{
    (e: 'refresh'): void;
  }>();

  const batchMoveApiMap: Record<ReasonKey, (params: BatchMoveToPublicPoolParams) => Promise<any>> = {
    [ReasonTypeEnum.CLUE_POOL_RS]: batchToCluePool,
    [ReasonTypeEnum.CUSTOMER_POOL_RS]: batchMoveCustomer,
  };

  const moveApiMap: Record<ReasonKey, (params: MoveToPublicPoolParams) => Promise<any>> = {
    [ReasonTypeEnum.CLUE_POOL_RS]: moveToLeadPool,
    [ReasonTypeEnum.CUSTOMER_POOL_RS]: moveCustomerToPool,
  };

  const form = ref({
    reason: null,
  });
  const successCount = ref<number>(0);
  const failCount = ref<number>(0);

  const reasonList = ref<Option[]>([]);
  const maxMoveCount = computed(() => Math.min(props.total ?? 0, MAX_MOVE_COUNT));
  const moveCount = ref<number>(1);

  const title = computed(() => {
    if (props.byCondition) {
      return t('customer.moveToOpenSeaByCondition');
    }
    const isArraySourceIds = Array.isArray(props.sourceId);
    switch (props.reasonKey) {
      case ReasonTypeEnum.CLUE_POOL_RS:
        return isArraySourceIds
          ? t('clue.batchMoveIntoCluePoolTitleTip', { number: props.sourceId.length })
          : t('clue.moveIntoCluePoolTitle', { name: characterLimit(props.name ?? '') });
      case ReasonTypeEnum.CUSTOMER_POOL_RS:
        return isArraySourceIds
          ? t('customer.batchMoveTitleTip', { number: props.sourceId.length })
          : t('customer.moveCustomerToOpenSeaTitleTip', { name: characterLimit(props.name ?? '') });
      default:
        break;
    }
  });

  const resultTitle = computed(() =>
    props.reasonKey === ReasonTypeEnum.CUSTOMER_POOL_RS ? t('customer.moveToOpenSea') : t('clue.moveIntoCluePool')
  );

  const contentTip = computed(() =>
    props.reasonKey === ReasonTypeEnum.CLUE_POOL_RS ? t('clue.moveToLeadPoolTip') : t('customer.batchMoveContentTip')
  );

  function handleCancel() {
    showModal.value = false;
    form.value.reason = null;
  }

  const showToPoolResultModel = ref(false);
  const formRef = ref<FormInst | null>(null);
  const loading = ref(false);

  async function handleSave() {
    try {
      loading.value = true;
      const isBatch = Array.isArray(props.sourceId);

      if (props.byCondition) {
        if (!props.queryParams) {
          return;
        }
        const count = moveCount.value;
        const max = maxMoveCount.value;
        if (!count || count < 1 || count > max) {
          Message.warning(t('customer.moveCountInvalid', { max }));
          return;
        }
        const { success, fail } = await batchMoveCustomerByCondition({
          ...props.queryParams,
          targetPoolId: props.poolId,
          reasonId: form.value.reason,
          moveCount: count,
        });
        successCount.value = success;
        failCount.value = fail;
        showToPoolResultModel.value = true;
      } else if (isBatch) {
        const { success, fail } = await batchMoveApiMap[props.reasonKey]({
          ids: props.sourceId,
          poolId: props.poolId,
          reasonId: form.value.reason,
        });
        successCount.value = success;
        failCount.value = fail;
        showToPoolResultModel.value = true;
      } else {
        const { success, fail } = await moveApiMap[props.reasonKey]({
          id: props.sourceId as DataTableRowKey,
          poolId: props.poolId,
          reasonId: form.value.reason,
        });
        successCount.value = success;
        failCount.value = fail;
        showToPoolResultModel.value = true;
      }
      if (failCount.value === 0) {
        setTimeout(() => {
          showModal.value = false;
          showToPoolResultModel.value = false;
          emit('refresh');
        }, 3000);
      } else if (successCount.value > 0) {
        showModal.value = false;
        showToPoolResultModel.value = false;
        emit('refresh');
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.log(e);
    } finally {
      loading.value = false;
    }
  }

  const enableReason = ref(false);
  function handleConfirm() {
    if (enableReason.value) {
      formRef.value?.validate(async (error) => {
        if (!error) {
          handleSave();
        }
      });
    } else {
      handleSave();
    }
  }

  async function initReasonConfig() {
    try {
      const { dictList, enable } = await getReasonConfig(props.reasonKey);
      enableReason.value = enable;
      reasonList.value = dictList.filter((e) => e.id !== 'system').map((e) => ({ label: e.name, value: e.id }));
    } catch (e) {
      // eslint-disable-next-line no-console
      console.log(e);
    }
  }

  function handleResultCancel() {
    showModal.value = false;
    showToPoolResultModel.value = false;
  }

  function clampMoveCount() {
    const max = maxMoveCount.value;
    if (max < 1) {
      moveCount.value = 1;
      return;
    }
    moveCount.value = Math.min(Math.max(moveCount.value || 1, 1), max);
  }

  watch(
    () => showModal.value,
    (val) => {
      if (val) {
        if (props.byCondition) {
          moveCount.value = maxMoveCount.value > 0 ? maxMoveCount.value : 1;
        }
        initReasonConfig();
      }
    }
  );

  watch(
    () => props.total,
    () => {
      if (showModal.value && props.byCondition) {
        clampMoveCount();
      }
    }
  );
</script>

<style scoped></style>
