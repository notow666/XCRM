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
      <div>
        <div class="mb-[8px] flex items-center justify-between">
          <div class="text-[14px] text-[var(--text-n1)]">{{ t('customer.assignSelectUsers') }}</div>
          <NCheckbox
            v-if="selectableUserIds.length > 0"
            :checked="isAllSelected"
            :indeterminate="isIndeterminate"
            @update:checked="handleSelectAll"
          >
            {{ t('common.allSelect') }}
          </NCheckbox>
        </div>
        <NSpin :show="loadingUserList">
          <div class="max-h-[280px] overflow-y-auto rounded border border-[var(--border-1)] p-[12px]">
            <div v-if="userCapacityList.length > 0" class="grid grid-cols-4 gap-[8px]">
              <div
                v-for="item in userCapacityList"
                :key="item.userId"
                class="flex min-h-[36px] cursor-pointer items-center gap-[6px] rounded-[4px] border px-[8px] py-[6px] transition-colors"
                :class="getUserItemClass(item)"
                :title="formatUserLabel(item)"
                @click="handleUserItemClick(item)"
              >
                <NCheckbox
                  :checked="ownerValues.includes(item.userId)"
                  :disabled="!isUserSelectable(item)"
                  class="shrink-0"
                  @click.stop
                  @update:checked="() => handleUserItemClick(item)"
                />
                <span class="line-clamp-2 flex-1 text-[12px] leading-[18px] text-[var(--text-n1)]">
                  {{ formatUserLabel(item) }}
                </span>
              </div>
            </div>
            <div v-else-if="!loadingUserList" class="py-[24px] text-center text-[14px] text-[var(--text-n4)]">
              {{ t('common.noData') }}
            </div>
          </div>
        </NSpin>
      </div>
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NCheckbox, NInputNumber, NSpin, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { OpenSeaCustomerTableParams } from '@lib/shared/models/customer';
  import type { UserCapacityItem } from '@lib/shared/models/system/module';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  import { batchAssignOpenSeaCustomerByCondition, batchUserCapacity } from '@/api/modules';

  const MAX_ASSIGN_COUNT = 500;

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
  const ownerValues = ref<string[]>([]);
  const loading = ref(false);
  const userCapacityList = ref<UserCapacityItem[]>([]);
  const loadingUserList = ref(false);

  function isUserSelectable(item: UserCapacityItem) {
    return (item.remainingCapacity ?? Infinity) > 0;
  }

  const selectableUserIds = computed(() =>
    userCapacityList.value.filter(isUserSelectable).map((item) => item.userId)
  );

  const isAllSelected = computed(
    () =>
      selectableUserIds.value.length > 0 &&
      selectableUserIds.value.every((id) => ownerValues.value.includes(id))
  );

  const isIndeterminate = computed(
    () =>
      !isAllSelected.value && selectableUserIds.value.some((id) => ownerValues.value.includes(id))
  );

  function handleSelectAll(checked: boolean) {
    if (checked) {
      ownerValues.value = [...selectableUserIds.value];
    } else {
      const selectableSet = new Set(selectableUserIds.value);
      ownerValues.value = ownerValues.value.filter((id) => !selectableSet.has(id));
    }
  }

  function formatUserLabel(item: UserCapacityItem) {
    const capacityText =
      item.capacity != null ? String(item.remainingCapacity ?? 0) : t('customer.assignUserCapacityUnlimited');
    return t('customer.assignUserCapacityLabel', { name: item.userName, capacity: capacityText });
  }

  function toggleUser(userId: string) {
    const index = ownerValues.value.indexOf(userId);
    if (index > -1) {
      ownerValues.value.splice(index, 1);
    } else {
      ownerValues.value.push(userId);
    }
  }

  function getUserItemClass(item: UserCapacityItem) {
    if (!isUserSelectable(item)) {
      return 'cursor-not-allowed border-[var(--border-1)] opacity-50';
    }
    if (ownerValues.value.includes(item.userId)) {
      return 'border-[var(--primary-8)] bg-[var(--primary-1)]';
    }
    return 'border-[var(--border-1)] hover:border-[var(--primary-6)] hover:bg-[var(--fill-1)]';
  }

  function handleUserItemClick(item: UserCapacityItem) {
    if (!isUserSelectable(item)) return;
    toggleUser(item.userId);
  }

  function closeHandler() {
    ownerValues.value = [];
  }

  function clampAssignCount() {
    const max = maxAssignCount.value;
    if (max < 1) {
      assignCount.value = 1;
      return;
    }
    assignCount.value = Math.min(Math.max(assignCount.value || 1, 1), max);
  }

  async function loadUserCapacity() {
    try {
      loadingUserList.value = true;
      userCapacityList.value = (await batchUserCapacity(props.poolId)) ?? [];
    } catch {
      userCapacityList.value = [];
    } finally {
      loadingUserList.value = false;
    }
  }

  watch(showModal, (val) => {
    if (val) {
      assignCount.value = maxAssignCount.value > 0 ? maxAssignCount.value : 1;
      ownerValues.value = [];
      loadUserCapacity();
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
    if (!ownerValues.value.length) {
      Message.warning(t('opportunity.selectReceiverPlaceholder'));
      return;
    }
    loading.value = true;
    batchAssignOpenSeaCustomerByCondition({
      ...props.queryParams,
      assignCount: count,
      assignUserId: ownerValues.value[0] || '',
      assignUserIds: ownerValues.value,
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
