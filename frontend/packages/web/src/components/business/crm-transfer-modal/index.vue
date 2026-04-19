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
    <div v-if="!props.showCapacity">
      <CrmUserSelect
        v-model:value="ownerValue"
        :placeholder="t('opportunity.selectReceiverPlaceholder')"
        value-field="id"
        label-field="name"
        mode="remote"
        :fetch-api="getUserOptions"
        max-tag-count="responsive"
      />
    </div>
    <div v-else class="w-full min-w-[350px]">
      <NSpin :show="loadingUserList">
        <div class="max-h-[200px] space-y-1 overflow-y-auto rounded border p-2">
          <div
            v-for="item in userCapacityList"
            :key="item.userId"
            class="flex cursor-pointer items-center justify-between rounded p-2"
            :class="[
              (item.remainingCapacity ?? Infinity) <= 0 ? '!cursor-not-allowed opacity-50' : '',
              ownerValues.includes(item.userId) ? 'bg-blue-100 ring-1 ring-blue-300' : 'hover:bg-gray-50',
            ]"
            @click="(item.remainingCapacity ?? Infinity) > 0 && toggleUser(item.userId)"
          >
            <div class="flex items-center">
              <NCheckbox
                :checked="ownerValues.includes(item.userId)"
                :disabled="(item.remainingCapacity ?? Infinity) <= 0"
                @update:checked="toggleUser(item.userId)"
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
  import { computed, onMounted, ref } from 'vue';
  import { DataTableRowKey, NCheckbox, NSpin, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { TransferParams } from '@lib/shared/models/customer/index';
  import type { UserCapacityItem } from '@lib/shared/models/system/module';

  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import CrmUserSelect from '@/components/business/crm-user-select/index.vue';

  import { batchUserCapacity, getUserOptions } from '@/api/modules';

  const { t } = useI18n();
  const Message = useMessage();

  interface TransferModalProps {
    title?: string;
    sourceIds: DataTableRowKey[];
    isBatch?: boolean;
    positiveText?: string;
    showCapacity?: boolean;
    saveApi?: (params: TransferParams) => Promise<any>;
  }

  const props = withDefaults(defineProps<TransferModalProps>(), {
    isBatch: true,
    showCapacity: true,
  });

  const emit = defineEmits<{
    (e: 'loadList'): void;
    (e: 'confirm', owner: string | null): void;
  }>();

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const title = computed(() => {
    if (props.title) {
      return props.title;
    }
    return props.isBatch ? t('common.batchTransfer') : t('common.transfer');
  });

  const ownerValue = ref<string | null>(null);
  const ownerValues = ref<string[]>([]);
  const loading = ref<boolean>(false);
  const userCapacityList = ref<UserCapacityItem[]>([]);
  const loadingUserList = ref(false);

  function closeHandler() {
    ownerValue.value = null;
    ownerValues.value = [];
  }

  function toggleUser(userId: string) {
    const index = ownerValues.value.indexOf(userId);
    if (index > -1) {
      ownerValues.value.splice(index, 1);
    } else {
      ownerValues.value.push(userId);
    }
  }

  async function loadUserCapacity() {
    if (!props.showCapacity) return;
    try {
      loadingUserList.value = true;
      const res = await getUserOptions({ keyword: '', pageSize: 100 });
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

  onMounted(() => {
    loadUserCapacity();
  });

  function confirmHandler() {
    if (props.showCapacity) {
      if (!ownerValues.value || ownerValues.value.length === 0) {
        Message.warning(t('opportunity.selectReceiverPlaceholder'));
        return;
      }
      if (props.saveApi) {
        loading.value = true;
        props
          .saveApi({
            batchIds: props.sourceIds,
            assignUserId: ownerValues.value[0] || '',
            assignUserIds: ownerValues.value,
          })
          .then((data: any) => {
            showModal.value = false;
            if (typeof data === 'number' && data > 0) {
              Message.warning(t('module.customer.capacityOver', { count: data }));
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
      } else {
        emit('confirm', ownerValues.value);
      }
    } else {
      if (!ownerValue.value) {
        Message.warning(t('opportunity.selectReceiverPlaceholder'));
        return;
      }
      if (props.saveApi) {
        loading.value = true;
        props
          .saveApi({
            batchIds: props.sourceIds,
            assignUserId: ownerValue.value,
            assignUserIds: [ownerValue.value],
          })
          .then(() => {
            showModal.value = false;
            Message.success(t('common.transferSuccess'));
            emit('loadList');
          })
          .catch(() => {
            // ignore
          })
          .finally(() => {
            loading.value = false;
          });
      } else {
        emit('confirm', ownerValue.value);
      }
    }
  }
</script>

<style lang="less" scoped></style>
