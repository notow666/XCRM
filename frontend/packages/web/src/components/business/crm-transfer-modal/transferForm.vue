<template>
  <div class="min-w-[350px]">
    <div v-if="!showCapacity">
      <CrmUserSelect
        v-model:value="form.owner"
        :placeholder="t('opportunity.selectReceiverPlaceholder')"
        value-field="id"
        label-field="name"
        mode="remote"
        :fetch-api="getUserOptions"
        max-tag-count="responsive"
      />
    </div>
    <div v-else class="w-full">
      <NSpin :show="loadingUserList">
        <div class="max-h-[200px] space-y-1 overflow-y-auto rounded border p-2">
          <div
            v-for="item in userCapacityList"
            :key="item.userId"
            class="flex cursor-pointer items-center justify-between rounded p-2"
            :class="[
              (item.remainingCapacity ?? Infinity) <= 0 ? '!cursor-not-allowed opacity-50' : '',
              form.owners?.includes(item.userId) ? 'bg-blue-100 ring-1 ring-blue-300' : 'hover:bg-gray-50',
            ]"
            @click="(item.remainingCapacity ?? Infinity) > 0 && toggleUser(item.userId)"
          >
            <div class="flex items-center">
              <NCheckbox
                :checked="form.owners?.includes(item.userId)"
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
  </div>
</template>

<script lang="ts" setup>
  import { onMounted, ref } from 'vue';
  import { NCheckbox, NSpin } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { TransferParams } from '@lib/shared/models/customer/index';
  import type { UserCapacityItem } from '@lib/shared/models/system/module';

  import CrmUserSelect from '@/components/business/crm-user-select/index.vue';

  import { batchUserCapacity, getUserOptions } from '@/api/modules';
  import { defaultTransferForm } from '@/config/opportunity';

  const { t } = useI18n();

  const props = defineProps<{
    showCapacity?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:form', value: TransferParams): void;
  }>();

  const form = defineModel<TransferParams>({
    required: true,
    default: {
      ...defaultTransferForm,
      owners: [],
    },
  });

  const userCapacityList = ref<UserCapacityItem[]>([]);
  const loadingUserList = ref(false);

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

  function toggleUser(userId: string) {
    if (props.showCapacity) {
      const owners = [...(form.value.owners || [])];
      const index = owners.indexOf(userId);
      if (index > -1) {
        owners.splice(index, 1);
      } else {
        owners.push(userId);
      }
      const newForm = { ...form.value, owners };
      form.value = newForm;
      emit('update:form', newForm);
    }
  }

  defineExpose({
    loadUserCapacity,
  });
</script>

<style lang="less" scoped></style>
