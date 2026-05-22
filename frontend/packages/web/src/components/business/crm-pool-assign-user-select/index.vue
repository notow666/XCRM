<template>
  <div class="w-full">
    <div v-if="showLabel" class="mb-[8px] flex items-center justify-between">
      <div class="text-[14px] text-[var(--text-n1)]">{{ t('customer.assignSelectUsers') }}</div>
      <NCheckbox
        v-if="multiple && showSelectAll && selectableUserIds.length > 0"
        :checked="isAllSelected"
        :indeterminate="isIndeterminate"
        @update:checked="handleSelectAll"
      >
        {{ t('common.allSelect') }}
      </NCheckbox>
    </div>
    <NSpin :show="loadingUserList">
      <div class="overflow-y-auto rounded border border-[var(--border-1)] p-[12px]" :style="{ maxHeight }">
        <div v-if="userCapacityList.length > 0" class="grid grid-cols-4 gap-[8px]">
          <div
            v-for="item in userCapacityList"
            :key="item.userId"
            class="flex min-h-[36px] cursor-pointer items-center gap-[6px] rounded-[4px] border px-[8px] py-[6px] transition-colors"
            :class="getUserItemClass(item)"
            :title="formatUserLabel(item)"
            @click="handleUserItemClick(item)"
          >
            <NRadio
              v-if="!multiple"
              :checked="selectedIds.includes(item.userId)"
              :disabled="!isUserSelectable(item)"
              class="shrink-0"
              @click.stop
              @update:checked="() => handleUserItemClick(item)"
            />
            <NCheckbox
              v-else
              :checked="selectedIds.includes(item.userId)"
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
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { NCheckbox, NRadio, NSpin } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { UserCapacityItem } from '@lib/shared/models/system/module';

  import { batchUserCapacity } from '@/api/modules';

  const { t } = useI18n();

  const props = withDefaults(
    defineProps<{
      /** 公海池 ID；不传则按客户转移权限拉取用户 */
      poolId?: string;
      /** 多选；false 为单选 */
      multiple?: boolean;
      showLabel?: boolean;
      showSelectAll?: boolean;
      maxHeight?: string;
    }>(),
    {
      multiple: false,
      showLabel: true,
      showSelectAll: true,
      maxHeight: '280px',
    }
  );

  const selectedIds = defineModel<string[]>('selectedIds', {
    required: true,
    default: () => [],
  });

  const userCapacityList = ref<UserCapacityItem[]>([]);
  const loadingUserList = ref(false);

  function isUserSelectable(item: UserCapacityItem) {
    return (item.remainingCapacity ?? Infinity) > 0;
  }

  const selectableUserIds = computed(() => userCapacityList.value.filter(isUserSelectable).map((item) => item.userId));

  const isAllSelected = computed(
    () => selectableUserIds.value.length > 0 && selectableUserIds.value.every((id) => selectedIds.value.includes(id))
  );

  const isIndeterminate = computed(
    () => !isAllSelected.value && selectableUserIds.value.some((id) => selectedIds.value.includes(id))
  );

  function handleSelectAll(checked: boolean) {
    if (checked) {
      selectedIds.value = [...selectableUserIds.value];
    } else {
      const selectableSet = new Set(selectableUserIds.value);
      selectedIds.value = selectedIds.value.filter((id) => !selectableSet.has(id));
    }
  }

  function formatUserLabel(item: UserCapacityItem) {
    const capacityText =
      item.capacity != null ? String(item.remainingCapacity ?? 0) : t('customer.assignUserCapacityUnlimited');
    return t('customer.assignUserCapacityLabel', { name: item.userName, capacity: capacityText });
  }

  function toggleUser(userId: string) {
    const index = selectedIds.value.indexOf(userId);
    if (index > -1) {
      selectedIds.value = selectedIds.value.filter((id) => id !== userId);
    } else {
      selectedIds.value = [...selectedIds.value, userId];
    }
  }

  function getUserItemClass(item: UserCapacityItem) {
    if (!isUserSelectable(item)) {
      return 'cursor-not-allowed border-[var(--border-1)] opacity-50';
    }
    if (selectedIds.value.includes(item.userId)) {
      return 'border-[var(--primary-8)] bg-[var(--primary-1)]';
    }
    return 'border-[var(--border-1)] hover:border-[var(--primary-6)] hover:bg-[var(--fill-1)]';
  }

  function handleUserItemClick(item: UserCapacityItem) {
    if (!isUserSelectable(item)) return;
    if (props.multiple) {
      toggleUser(item.userId);
    } else {
      selectedIds.value = selectedIds.value.includes(item.userId) ? [] : [item.userId];
    }
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

  watch(
    () => props.poolId,
    () => {
      loadUserCapacity();
    },
    { immediate: true }
  );

  defineExpose({
    loadUserCapacity,
  });
</script>
