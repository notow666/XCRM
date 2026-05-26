<template>
  <CrmModal
    v-model:show="showModal"
    size="medium"
    :title="title"
    :ok-loading="loading"
    :positive-text="props.positiveText || t('common.transfer')"
    @confirm="confirmHandler"
    @cancel="closeHandler"
  >
    <div class="w-full">
      <CrmPoolAssignUserSelect v-model:selected-ids="selectedUserIds" />
    </div>
  </CrmModal>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { DataTableRowKey, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { TransferParams } from '@lib/shared/models/customer/index';

  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import CrmPoolAssignUserSelect from '@/components/business/crm-pool-assign-user-select/index.vue';

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

  const title = computed(() => props.title || t('common.transfer'));

  const selectedUserIds = ref<string[]>([]);
  const loading = ref<boolean>(false);

  watch(showModal, (val) => {
    if (val) {
      selectedUserIds.value = [];
    }
  });

  function closeHandler() {
    selectedUserIds.value = [];
  }

  function confirmHandler() {
    if (!selectedUserIds.value[0]) {
      Message.warning(t('opportunity.selectReceiverPlaceholder'));
      return;
    }
    if (props.saveApi) {
      loading.value = true;
      props
        .saveApi({
          ids: props.sourceIds,
          owner: selectedUserIds.value[0],
        })
        .then((data: any) => {
          showModal.value = false;
          const failCount = typeof data === 'number' ? data : 0;
          if (failCount > 0) {
            Message.warning(t('customer.transferPartial', { count: failCount }));
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
