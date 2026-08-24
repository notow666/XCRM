<template>
  <CrmDrawer v-model:show="visible" resizable no-padding :width="800" :title="title" :footer="false">
    <template v-if="!props.readonly" #titleRight>
      <n-button
        v-if="detailInfo?.approvalStatus === 'APPROVING'"
        v-permission="['CONTRACT_PAYMENT_RECORD:APPROVAL']"
        type="primary"
        ghost
        class="n-btn-outline-primary"
        @click="handleApproval(true)"
      >
        {{ t('common.pass') }}
      </n-button>
      <n-button
        v-if="detailInfo?.approvalStatus === 'APPROVING'"
        v-permission="['CONTRACT_PAYMENT_RECORD:APPROVAL']"
        type="error"
        ghost
        class="n-btn-outline-error ml-[12px]"
        @click="handleApproval(false)"
      >
        {{ t('common.unPass') }}
      </n-button>
      <n-button
        v-if="detailInfo?.approvalStatus !== 'APPROVING'"
        v-permission="['CONTRACT_PAYMENT_RECORD:UPDATE']"
        type="primary"
        ghost
        class="n-btn-outline-primary"
        @click="handleEdit(props.sourceId)"
      >
        {{ t('common.edit') }}
      </n-button>
      <n-button
        v-permission="['CONTRACT_PAYMENT_RECORD:DELETE']"
        type="error"
        ghost
        class="n-btn-outline-error ml-[12px]"
        @click="handleDelete(detailInfo)"
      >
        {{ t('common.delete') }}
      </n-button>
    </template>
    <div class="h-full bg-[var(--text-n9)] p-[16px]">
      <CrmCard no-content-padding hide-footer auto-height class="mb-[16px]">
        <CrmTab v-model:active-tab="activeTab" no-content :tab-list="tabList" type="line" />
      </CrmCard>
      <CrmCard hide-footer :special-height="64" noContentBottomPadding>
        <div v-show="activeTab === 'detail'" class="flex-1">
          <CrmFormDescription
            :form-key="FormDesignKeyEnum.CONTRACT_PAYMENT_RECORD"
            :source-id="props.sourceId"
            :column="2"
            :refresh-key="refreshKey"
            label-width="auto"
            value-align="start"
            tooltip-position="top-start"
            @init="handleInit"
            @open-contract-detail="emit('openContractDrawer', $event)"
          />
        </div>
        <ContractVersionHistory
          v-if="activeTab === 'history'"
          mode="paymentRecord"
          :versions="detailInfo?.versionHistory"
          :user-name-map="detailInfo?.versionUserNameMap"
        />
      </CrmCard>
    </div>

    <CrmFormCreateDrawer
      v-model:visible="formCreateDrawerVisible"
      :form-key="FormDesignKeyEnum.CONTRACT_PAYMENT_RECORD"
      :source-id="props.sourceId"
      need-init-detail
      :link-form-key="FormDesignKeyEnum.CONTRACT_PAYMENT_RECORD"
      @saved="() => handleSaved()"
    />
  </CrmDrawer>
</template>

<script lang="ts" setup>
  import { NButton, useMessage } from 'naive-ui';

  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { characterLimit } from '@lib/shared/method';
  import { CollaborationType } from '@lib/shared/models/customer';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';
  import CrmFormCreateDrawer from '@/components/business/crm-form-create-drawer/index.vue';
  import CrmFormDescription from '@/components/business/crm-form-description/index.vue';
  import ContractVersionHistory from '@/views/contract/components/versionHistory.vue';

  import { approvalPaymentRecord, deletePaymentRecord } from '@/api/modules';
  import useModal from '@/hooks/useModal';

  const props = defineProps<{
    sourceId: string;
    readonly?: boolean;
  }>();
  const emit = defineEmits<{
    (e: 'refresh'): void;
    (e: 'delete'): void;
    (e: 'openContractDrawer', params: { id: string }): void;
  }>();

  const visible = defineModel<boolean>('visible', {
    required: true,
  });

  const Message = useMessage();
  const { openModal } = useModal();
  const { t } = useI18n();
  const detailInfo = ref();
  const title = ref('');
  const activeTab = ref('detail');
  const tabList = [
    { name: 'detail', tab: '基本信息' },
    { name: 'history', tab: '历史记录' },
  ];

  function handleInit(type?: CollaborationType, name?: string, detail?: Record<string, any>) {
    detailInfo.value = detail;
    title.value = name || '';
  }

  const refreshKey = ref(0);
  function handleSaved() {
    refreshKey.value += 1;
    emit('refresh');
  }

  function handleDelete(row: any) {
    openModal({
      type: 'error',
      title: t('common.deleteConfirmTitle', { name: characterLimit(row.name) }),
      content: t('contract.paymentRecord.deleteConfirmContent'),
      positiveText: t('common.confirmDelete'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await deletePaymentRecord(row.id);
          Message.success(t('common.deleteSuccess'));
          visible.value = false;
          emit('delete');
        } catch (error) {
          // eslint-disable-next-line no-console
          console.error(error);
        }
      },
    });
  }

  async function handleApproval(approved: boolean) {
    await approvalPaymentRecord({
      id: props.sourceId,
      approvalStatus: approved ? 'APPROVED' : 'UNAPPROVED',
    });
    Message.success(approved ? t('common.approvedSuccess') : t('common.unApprovedSuccess'));
    handleSaved();
  }

  const formCreateDrawerVisible = ref(false);
  function handleEdit(id: string) {
    formCreateDrawerVisible.value = true;
  }
</script>
