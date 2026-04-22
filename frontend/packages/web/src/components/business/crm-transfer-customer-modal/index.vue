<template>
  <CrmModal
    v-model:show="showModal"
    size="small"
    :title="title"
    :ok-loading="loading"
    :positive-text="props.positiveText || t('common.transfer')"
    @confirm="confirmHandler"
    @cancel="closeHandler"
  >
    <div class="w-full min-w-[350px]">
      <NSpin :show="loadingUserList">
        <div class="max-h-[200px] space-y-1 overflow-y-auto rounded border p-2">
          <div
            v-for="item in userCapacityList"
            :key="item.userId"
            class="flex cursor-pointer items-center justify-between rounded p-2"
            :class="[
              (item.remainingCapacity ?? Infinity) <= 0 ? '!cursor-not-allowed opacity-50' : '',
              selectedUserId === item.userId ? 'bg-blue-100 ring-1 ring-blue-300' : 'hover:bg-gray-50',
            ]"
            @click="(item.remainingCapacity ?? Infinity) > 0 && selectUser(item.userId)"
          >
            <div class="flex items-center">
              <NRadio
                :checked="selectedUserId === item.userId"
                :disabled="(item.remainingCapacity ?? Infinity) <= 0"
                @update:checked="selectUser(item.userId)"
              />
              <span class="ml-2 text-sm font-medium">{{ item.userName }}</span>
            </div>
            <span
              v-if="item.capacity"
              class="text-xs"
              :class="(item.remainingCapacity ?? Infinity) > 0 ? 'text-gray-500' : 'text-red-500'"
              >{{ t('module.capacitySet.remaining') }}: {{ item.remainingCapacity ?? '-' }}
              {{ t('module.capacitySet.count') }}</span
            >
            <span v-else class="text-xs text-gray-400">{{ t('module.capacitySet.notConfigured') }}</span>
          </div>
          <div v-if="userCapacityList.length === 0 && !loadingUserList" class="py-4 text-center text-gray-400">
            {{ t('common.noData') }}
          </div>
        </div>
      </NSpin>
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { DataTableRowKey, NRadio, NSpin, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { TransferParams } from '@lib/shared/models/customer/index';
  import type { UserCapacityItem } from '@lib/shared/models/system/module';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  import { batchUserCapacity, getAuthUserOptions } from '@/api/modules';

  const { t } = useI18n();
  const Message = useMessage();

  interface TransferModalProps {
    title?: string;
    sourceIds: DataTableRowKey[];
    positiveText?: string;
    saveApi?: (params: TransferParams) => Promise<any>;
  }

  const props = defineProps<TransferModalProps>();

  const emit = defineEmits<{
    (e: 'loadList'): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const title = computed(() => {
    if (props.title) {
      return props.title;
    }
    return t('common.batchTransfer');
  });

  const selectedUserId = ref<string | null>(null);
  const loading = ref<boolean>(false);
  const userCapacityList = ref<UserCapacityItem[]>([]);
  const loadingUserList = ref(false);

  function closeHandler() {
    selectedUserId.value = null;
  }

  function selectUser(userId: string) {
    selectedUserId.value = userId;
  }

  async function loadUserCapacity() {
    try {
      loadingUserList.value = true;
      const res = await getAuthUserOptions();
      if (res && res.length > 0) {
        const userIds = res.map((u: any) => u.value || u.id);
        const capacityRes = await batchUserCapacity(userIds);
        const capacityMap = new Map(capacityRes.map((c: UserCapacityItem) => [c.userId, c]));
        userCapacityList.value = res.map((u: any) => {
          const userId = u.value || u.id;
          const cap = capacityMap.get(userId);
          return {
            userId,
            userName: u.label || u.name || '',
            capacity: cap?.capacity,
            ownedCount: cap?.ownedCount,
            remainingCapacity: cap?.remainingCapacity,
          };
        });
      }
    } catch {
      userCapacityList.value = [];
    } finally {
      loadingUserList.value = false;
    }
  }

  watch(showModal, (val) => {
    if (val) {
      loadUserCapacity();
    }
  });

  function confirmHandler() {
    if (!selectedUserId.value) {
      Message.warning(t('opportunity.selectReceiverPlaceholder'));
      return;
    }
    if (props.saveApi) {
      loading.value = true;
      props
        .saveApi({
          ids: props.sourceIds,
          owner: selectedUserId.value,
        })
        .then((data: any) => {
          showModal.value = false;
          const transferredCount = props.sourceIds.length - (data || 0);
          if (data && data > 0) {
            Message.warning(`成功转移 ${transferredCount} 个客户，由于库容不足，${data} 个未转移`);
          } else {
            Message.success(t('common.transferSuccess'));
          }
          emit('loadList');
        })
        .catch(() => {
          // ignore
        })
        .finally(() => {
          loading.value = false;
        });
    }
  }
</script>

<style lang="less" scoped></style>
