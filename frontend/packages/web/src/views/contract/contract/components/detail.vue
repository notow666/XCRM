<template>
  <CrmDrawer v-model:show="visible" resizable no-padding :width="800" :footer="false" :title="title">
    <template #titleLeft>
      <div class="text-[14px] font-normal">
        {{ stageName }}
      </div>
    </template>
    <template #titleRight>
      <CrmButtonGroup class="gap-[12px]" :list="buttonList" not-show-divider @select="handleButtonClick" />
    </template>
    <div class="h-full bg-[var(--text-n9)] p-[16px]">
      <CrmCard no-content-padding hide-footer auto-height class="mb-[16px]">
        <CrmTab v-model:active-tab="activeTab" no-content :tab-list="tabList" type="line" />
      </CrmCard>

      <CrmCard hide-footer :special-height="64" noContentBottomPadding>
        <!-- 需要用到 detailInfo 所以这里不用 v-if -->
        <div v-show="activeTab === 'contract'">
          <CrmFormDescription
            :form-key="FormDesignKeyEnum.CONTRACT_SNAPSHOT"
            :source-id="props.sourceId"
            :column="2"
            :refresh-key="refreshKey"
            label-width="auto"
            value-align="start"
            tooltip-position="top-start"
            readonly
            :isContractTableDetail="props.isContractTableDetail"
            @openCustomerDetail="emit('showCustomerDrawer', $event)"
            @openOpportunityDetail="openOpportunityDetail"
            @openQuotationDetail="openQuotationDetail"
            @init="handleInit"
          />
        </div>
        <ContractVersionHistory
          v-if="activeTab === 'history'"
          mode="contract"
          :versions="detailInfo?.versionHistory"
          :user-name-map="detailInfo?.versionUserNameMap"
        />
        <!-- 回款计划本期仅隐藏入口，原页面、组件和接口继续保留。 -->
        <template v-if="activeTab === 'paymentRecord'">
          <PaymentRecordTable
            :form-key="FormDesignKeyEnum.CONTRACT_PAYMENT_RECORD"
            :sourceId="props.sourceId"
            :sourceName="title"
            isContractTab
            :readonly="getReadonlyPayment"
            @refresh="handleSaved()"
          />
        </template>
        <!-- 发票、订单本期仅隐藏入口，原页面、组件和接口继续保留。 -->
      </CrmCard>
    </div>
    <CrmFormCreateDrawer
      v-model:visible="formCreateDrawerVisible"
      :source-id="activeSourceId"
      :form-key="activeFormKey"
      :need-init-detail="needInitDetail"
      :initial-source-name="initialSourceName"
      :link-form-key="FormDesignKeyEnum.CONTRACT"
      :link-form-info="linkFormInfo"
      @saved="() => handleSaved()"
    />
    <QuotationDetailDrawer
      v-model:visible="showQuotationDetailDrawer"
      :source-id="activeQuotationSourceId"
      @edit="handleEditQuotation"
      @refresh="handleSaved()"
    />
    <OptOverviewDrawer
      v-model:show="showOptOverviewDrawer"
      :detail="activeOpportunity"
      @refresh="handleSaved()"
      @open-customer-drawer="emit('showCustomerDrawer', $event)"
    />
  </CrmDrawer>
</template>

<script lang="ts" setup>
  import { useMessage } from 'naive-ui';

  import { ContractStatusEnum } from '@lib/shared/enums/contractEnum';
  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { QuotationStatusEnum } from '@lib/shared/enums/opportunityEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { characterLimit } from '@lib/shared/method';
  import type { ContractItem } from '@lib/shared/models/contract';
  import { CollaborationType } from '@lib/shared/models/customer';

  import CrmButtonGroup from '@/components/pure/crm-button-group/index.vue';
  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';
  import CrmFormCreateDrawer from '@/components/business/crm-form-create-drawer/index.vue';
  import CrmFormDescription from '@/components/business/crm-form-description/index.vue';
  import ContractVersionHistory from '@/views/contract/components/versionHistory.vue';
  // 回款计划、发票、订单本期只隐藏入口，保留原组件文件以便后续恢复。
  // import PaymentTable from '@/views/contract/contractPaymentPlan/components/paymentTable.vue';
  import PaymentRecordTable from '@/views/contract/contractPaymentRecord/components/paymentTable.vue';
  // import InvoiceTable from '@/views/contract/invoice/components/invoiceTable.vue';
  import OptOverviewDrawer from '@/views/opportunity/components/optOverviewDrawer.vue';
  import QuotationDetailDrawer from '@/views/opportunity/components/quotation/detail.vue';

  // import OrderTable from '@/views/order/order/components/orderTable.vue';
  import { approvalContract, changeContractStatus, deleteContract } from '@/api/modules';
  import { contractStatusOptions } from '@/config/contract';
  import useApprovalConfig from '@/hooks/useApprovalConfig';
  import useFormCreateApi from '@/hooks/useFormCreateApi';
  import useModal from '@/hooks/useModal';
  import { hasAnyPermission } from '@/utils/permission';

  const props = defineProps<{
    sourceId: string;
    isContractTableDetail?: boolean;
  }>();
  const emit = defineEmits<{
    (e: 'refresh'): void;
    (e: 'delete'): void;
    (e: 'showCustomerDrawer', params: { customerId: string; inCustomerPool: boolean; poolId: string }): void;
    (e: 'openBusinessTitleDrawer', params: { id: string }): void;
  }>();

  const visible = defineModel<boolean>('visible', {
    required: true,
  });

  const Message = useMessage();
  const { openModal } = useModal();
  const { t } = useI18n();
  const title = ref('');
  const detailInfo = ref();

  const stageName = computed(() => {
    return contractStatusOptions.find(
      (item) => item.value === (detailInfo.value?.displayStage || detailInfo.value?.stage)
    )?.label;
  });

  const activeTab = ref('contract');

  const tabList = computed(() =>
    [
      {
        name: 'contract',
        tab: t('module.contract'),
        permission: ['CONTRACT:READ'],
      },
      // 回款计划、发票、订单本期仅隐藏合同详情入口。
      {
        name: 'paymentRecord',
        tab: t('module.paymentRecord'),
        permission: ['CONTRACT_PAYMENT_RECORD:READ'],
      },
      {
        name: 'history',
        tab: '历史记录',
        permission: ['CONTRACT:READ'],
      },
    ].filter((item) => hasAnyPermission(item.permission))
  );

  function getApprovalEnableBtnList() {
    if (detailInfo.value?.approvalStatus === QuotationStatusEnum.APPROVING) {
      return [
        {
          label: t('common.pass'),
          key: 'pass',
          text: false,
          ghost: true,
          class: 'n-btn-outline-primary',
          permission: ['CONTRACT:APPROVAL'],
        },
        {
          label: t('common.unPass'),
          key: 'unPass',
          danger: true,
          text: false,
          ghost: true,
          class: 'n-btn-outline-primary',
          permission: ['CONTRACT:APPROVAL'],
        },
        // 新审批版本不提供撤销，审批不通过后通过编辑重新提审。
        {
          label: t('common.delete'),
          key: 'delete',
          text: false,
          ghost: true,
          danger: true,
          class: 'n-btn-outline-primary',
          permission: ['CONTRACT:DELETE'],
        },
      ];
    }
    if (detailInfo.value?.approvalStatus === QuotationStatusEnum.APPROVED) {
      const terminal = [ContractStatusEnum.COMPLETED_PERFORMANCE, ContractStatusEnum.VOID].includes(
        detailInfo.value?.stage
      );
      return [
        ...(!terminal
          ? [
              {
                label: t('common.edit'),
                key: 'edit',
                permission: ['CONTRACT:UPDATE'],
                text: false,
                ghost: true,
                class: 'n-btn-outline-primary',
              },
              {
                label: t('contract.payment'),
                key: 'paymentRecord',
                permission: ['CONTRACT_PAYMENT_RECORD:ADD'],
                text: false,
                ghost: true,
                class: 'n-btn-outline-primary',
              },
              {
                label: t('contract.completedPerformance'),
                key: 'complete',
                permission: ['CONTRACT:STAGE'],
                text: false,
                ghost: true,
                class: 'n-btn-outline-primary',
              },
              {
                label: t('common.voided'),
                key: 'void',
                permission: ['CONTRACT:STAGE'],
                text: false,
                danger: true,
                ghost: true,
                class: 'n-btn-outline-primary',
              },
            ]
          : []),
        {
          label: t('common.delete'),
          key: 'delete',
          text: false,
          ghost: true,
          danger: true,
          class: 'n-btn-outline-primary',
          permission: ['CONTRACT:DELETE'],
        },
      ];
    }
    return [
      {
        key: 'edit',
        label: t('common.edit'),
        permission: ['CONTRACT:UPDATE'],
        text: false,
        ghost: true,
        class: 'n-btn-outline-primary',
      },
      {
        label: t('common.delete'),
        key: 'delete',
        text: false,
        ghost: true,
        danger: true,
        class: 'n-btn-outline-primary',
        permission: ['CONTRACT:DELETE'],
      },
    ];
  }

  const { initApprovalConfig, dicApprovalEnable } = useApprovalConfig(FormDesignKeyEnum.CONTRACT);

  const buttonList = computed(() =>
    dicApprovalEnable.value
      ? getApprovalEnableBtnList()
      : [
          {
            key: 'edit',
            label: t('common.edit'),
            permission: ['CONTRACT:UPDATE'],
            text: false,
            ghost: true,
            class: 'n-btn-outline-primary',
          },
          {
            label: t('contract.payment'),
            key: 'paymentRecord',
            permission: ['CONTRACT:PAYMENT'],
            text: false,
            ghost: true,
            class: 'n-btn-outline-primary',
            disabled: !detailInfo.value?.amount || detailInfo.value?.alreadyPayAmount >= detailInfo.value?.amount,
            tooltipContent:
              detailInfo.value?.alreadyPayAmount >= detailInfo.value?.amount ? t('contract.noPaymentRequired') : '',
          },
          {
            label: t('common.delete'),
            key: 'delete',
            text: false,
            ghost: true,
            danger: true,
            class: 'n-btn-outline-primary',
            permission: ['CONTRACT:DELETE'],
          },
        ]
  );

  function handleInit(type?: CollaborationType, name?: string, detail?: Record<string, any>) {
    title.value = name || '';
    detailInfo.value = detail ?? {};
  }

  const formCreateDrawerVisible = ref(false);
  const needInitDetail = ref(true);
  const initialSourceName = ref('');
  const activeFormKey = ref(FormDesignKeyEnum.CONTRACT);
  const activeSourceId = ref('');

  function handleEdit() {
    needInitDetail.value = true;
    initialSourceName.value = '';
    activeFormKey.value = FormDesignKeyEnum.CONTRACT;
    activeSourceId.value = props.sourceId;
    formCreateDrawerVisible.value = true;
  }

  const refreshKey = ref(0);
  function handleSaved() {
    refreshKey.value += 1;
    emit('refresh');
  }

  function handleDelete(row: ContractItem) {
    openModal({
      type: 'error',
      title: t('common.deleteConfirmTitle', { name: characterLimit(row.name) }),
      content: t('common.deleteConfirmContent'),
      positiveText: t('common.confirmDelete'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await deleteContract(row.id);
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

  async function handleApproval(approval = false) {
    const approvalStatus = approval ? QuotationStatusEnum.APPROVED : QuotationStatusEnum.UNAPPROVED;
    try {
      await approvalContract({
        id: props.sourceId,
        approvalStatus,
      });
      Message.success(approval ? t('common.approvedSuccess') : t('common.unApprovedSuccess'));
      handleSaved();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error(error);
    }
  }

  // 回款
  const linkFormInfo = ref();
  const { initFormDetail, initFormConfig, linkFormFieldMap } = useFormCreateApi({
    formKey: ref(FormDesignKeyEnum.CONTRACT),
    sourceId: activeSourceId,
  });
  async function handlePaymentRecord(row: ContractItem) {
    activeSourceId.value = row.id;
    initialSourceName.value = row.name;
    needInitDetail.value = false;
    activeFormKey.value = FormDesignKeyEnum.CONTRACT_PAYMENT_RECORD;
    await initFormConfig();
    await initFormDetail(false, true);
    linkFormInfo.value = linkFormFieldMap.value;
    formCreateDrawerVisible.value = true;
  }

  const showQuotationDetailDrawer = ref(false);
  const activeQuotationSourceId = ref('');
  function openQuotationDetail(params: { id: string }) {
    showQuotationDetailDrawer.value = true;
    activeQuotationSourceId.value = params.id;
  }

  function handleEditQuotation(id: string) {
    activeFormKey.value = FormDesignKeyEnum.OPPORTUNITY_QUOTATION;
    activeSourceId.value = id;
    needInitDetail.value = true;
    linkFormInfo.value = undefined;
    formCreateDrawerVisible.value = true;
  }

  const showOptOverviewDrawer = ref<boolean>(false);
  const activeOpportunity = ref();
  function openOpportunityDetail(params: { id: string }) {
    showOptOverviewDrawer.value = true;
    activeOpportunity.value = {
      id: params.id,
    };
  }

  const getReadonlyPayment = computed(() => {
    if (dicApprovalEnable.value) {
      return (
        [ContractStatusEnum.VOID, ContractStatusEnum.COMPLETED_PERFORMANCE].includes(detailInfo.value?.stage) ||
        detailInfo.value?.approvalStatus === QuotationStatusEnum.APPROVING
      );
    }
    return [ContractStatusEnum.VOID, ContractStatusEnum.COMPLETED_PERFORMANCE].includes(detailInfo.value?.stage);
  });

  function handleStageChange(stage: ContractStatusEnum) {
    openModal({
      type: 'warning',
      title: stage === ContractStatusEnum.VOID ? t('common.voided') : t('contract.completedPerformance'),
      content: t('common.confirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await changeContractStatus(props.sourceId, stage);
        Message.success(t('common.updateSuccess'));
        handleSaved();
      },
    });
  }

  async function handleButtonClick(actionKey: string) {
    switch (actionKey) {
      case 'pass':
        handleApproval(true);
        break;
      case 'unPass':
        handleApproval();
        break;
      case 'edit':
        handleEdit();
        break;
      case 'paymentRecord':
        handlePaymentRecord(detailInfo.value);
        break;
      case 'complete':
        handleStageChange(ContractStatusEnum.COMPLETED_PERFORMANCE);
        break;
      case 'void':
        handleStageChange(ContractStatusEnum.VOID);
        break;
      case 'delete':
        handleDelete(detailInfo.value);
        break;
      default:
        break;
    }
  }

  watch(
    () => visible.value,
    (val) => {
      if (val) {
        initApprovalConfig();
      }
    }
  );
</script>
