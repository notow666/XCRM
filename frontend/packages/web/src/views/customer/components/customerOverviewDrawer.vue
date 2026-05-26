<template>
  <CrmOverviewDrawer
    ref="crmOverviewDrawerRef"
    v-model:show="show"
    v-model:active-tab="activeTab"
    :tab-list="tabList"
    :button-list="buttonList"
    :title="sourceName"
    :form-key="FormDesignKeyEnum.CUSTOMER"
    :source-id="props.sourceId"
    show-tab-setting
    @button-select="handleButtonSelect"
    @saved="handleSaved"
  >
    <template #titleRightPrefix>
      <div v-if="showListNavigation || canReachOperate" class="mr-[12px] flex items-center gap-[12px]">
        <CrmCustomerListReach
          v-if="canReachOperate"
          direction="row"
          :call-status="readCallStatus(reachRow)"
          :wechat-friend-status="readWechatFriendStatus(reachRow)"
          :can-operate="true"
          @dial="(slot) => handleDialCustomer(reachRow, slot)"
          @sms="(slot) => handleSmsCustomer(reachRow, slot)"
          @wechat="() => handleWechatCustomer(reachRow)"
          @add-wechat="() => handleWechatFriendCustomer(reachRow)"
        />
        <template v-if="showListNavigation">
          <n-button type="primary" ghost class="n-btn-outline-primary" :disabled="!canGoPrev" @click="goPrevCustomer">
            {{ t('customer.detail.prev') }}
          </n-button>
          <n-button type="primary" ghost class="n-btn-outline-primary" :disabled="!canGoNext" @click="goNextCustomer">
            {{ t('customer.detail.next') }}
          </n-button>
        </template>
      </div>
    </template>
    <template #transferPopContent>
      <CrmPoolAssignUserSelect v-model:selected-ids="transferSelectedUserIds" class="mt-[16px] min-w-[480px]" />
    </template>
    <template #left>
      <div class="h-full overflow-hidden">
        <CrmFormDescription
          ref="descriptionRef"
          :form-key="FormDesignKeyEnum.CUSTOMER"
          :source-id="props.sourceId"
          :refresh-key="refreshKey"
          :hidden-fields="customerHiddenLeftFields"
          class="p-[16px_24px]"
          :column="layout === 'vertical' ? 3 : undefined"
          :label-width="layout === 'vertical' ? 'auto' : undefined"
          :value-align="layout === 'vertical' ? 'start' : undefined"
          @init="handleDescriptionInit"
        />
      </div>
    </template>
    <template #rightTop>
      <CrmWorkflowCard
        v-model:stage="currentStatus"
        class="mb-[16px]"
        :stage-config-list="stageConfig?.stageConfigList || []"
        :source-id="props.sourceId"
        readonly
        @load-detail="handleSaved"
      />
    </template>
    <template #right>
      <div class="h-full pt-[16px]">
        <ContactTable
          v-if="activeTab === 'contact'"
          :refresh-key="refreshKey"
          :source-id="props.sourceId"
          :initial-source-name="sourceName"
          :readonly="collaborationType === 'READ_ONLY' || props.readonly"
          :form-key="FormDesignKeyEnum.CUSTOMER_CONTACT"
        />
        <FollowDetail
          v-else-if="['followRecord', 'followPlan'].includes(activeTab) && show"
          :active-type="(activeTab as 'followRecord'| 'followPlan')"
          wrapper-class="h-[calc(100vh-162px)]"
          virtual-scroll-height="calc(100vh - 254px)"
          :follow-api-key="FormDesignKeyEnum.CUSTOMER"
          :source-id="props.sourceId"
          :refresh-key="refreshKey"
          :initial-source-name="sourceName"
          :show-add="
            collaborationType !== 'READ_ONLY' &&
            hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']) &&
            !props.readonly &&
            !isCustomerCompleted
          "
          :show-action="
            collaborationType !== 'READ_ONLY' &&
            hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']) &&
            !props.readonly &&
            !(activeTab === 'followPlan' && isCustomerCompleted)
          "
          :parentFormKey="FormDesignKeyEnum.CUSTOMER"
          :customer-stage-status="customerStageStatus"
          :customer-stage="currentStatus"
          @saved="handleSaved"
        />
        <CrmHeaderTable
          v-else-if="activeTab === 'headRecord'"
          :form-key="FormDesignKeyEnum.CUSTOMER_OPEN_SEA"
          :source-id="props.sourceId"
          :load-list-api="getCustomerHeaderList"
        />
        <customerRelation
          v-else-if="activeTab === 'relation'"
          :source-id="props.sourceId"
          :readonly="collaborationType === 'READ_ONLY' || props.readonly"
        />
        <CrmCard v-else-if="activeTab === 'opportunityInfo'" no-content-bottom-padding hide-footer>
          <opportunityTable
            :source-id="props.sourceId"
            :customer-name="sourceName"
            is-customer-tab
            :form-key="FormDesignKeyEnum.CUSTOMER_OPPORTUNITY"
            :readonly="collaborationType === 'READ_ONLY' || props.readonly"
          />
        </CrmCard>
        <collaborator
          v-else-if="activeTab === 'collaborator'"
          :source-id="props.sourceId"
          :readonly="collaborationType === 'READ_ONLY' || props.readonly"
        />
        <ContractTimeline
          v-else-if="activeTab === 'contract'"
          :form-key="FormDesignKeyEnum.CONTRACT"
          :source-id="props.sourceId"
        />
        <ContractTimeline
          v-else-if="activeTab === 'contractPayment'"
          :form-key="FormDesignKeyEnum.CONTRACT_PAYMENT"
          :source-id="props.sourceId"
        />
        <ContractTimeline
          v-else-if="activeTab === 'contractPaymentRecord'"
          :form-key="FormDesignKeyEnum.CONTRACT_PAYMENT_RECORD"
          :source-id="props.sourceId"
        />
        <CustomerCallRecordTable v-else-if="activeTab === 'callRecord'" :source-id="props.sourceId" />
        <ContractTimeline
          v-else-if="activeTab === 'invoice'"
          :form-key="FormDesignKeyEnum.INVOICE"
          :source-id="props.sourceId"
        />
        <CrmCard v-else-if="activeTab === 'order'" hide-footer no-content-bottom-padding>
          <OrderTable
            :formKey="FormDesignKeyEnum.CUSTOMER_ORDER"
            :sourceId="props.sourceId"
            isCustomerTab
            @open-contract-drawer="handleOpenContractDrawer"
          />
        </CrmCard>
      </div>
      <CrmMoveModal
        v-model:show="showMoveModal"
        :reason-key="ReasonTypeEnum.CUSTOMER_POOL_RS"
        :source-id="props.sourceId"
        :name="sourceName"
        :pool-id="selectedPoolId"
        type="warning"
        @refresh="emit('deleted')"
      />
      <CrmSelectPoolModal v-model:show="showSelectPoolModal" :name="sourceName" @confirm="handlePoolSelected" />
      <ContractDetailDrawer
        v-model:visible="showContractDetailDrawer"
        :sourceId="activeSourceId"
        @showCustomerDrawer="handleOpenCustomerDrawer"
      />
    </template>
  </CrmOverviewDrawer>
  <customerSmsModal
    v-model:show="showReachModal"
    :mode="reachModal.mode"
    :source-id="reachModal.sourceId"
    :name="reachModal.name"
    :mobile="reachModal.mobile"
    :wechat-options="activeWechatOptions"
    @submit="handleReachModalSubmit"
  />
</template>

<script setup lang="ts">
  import { onMounted, watch } from 'vue';
  import { NButton, useMessage } from 'naive-ui';

  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { ModuleConfigEnum, ReasonTypeEnum } from '@lib/shared/enums/moduleEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { CollaborationType } from '@lib/shared/models/customer';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import type { ActionsItem } from '@/components/pure/crm-more-action/type';
  import CrmCustomerListReach from '@/components/business/crm-customer-list-reach/index.vue';
  import ContactTable from '@/components/business/crm-form-create-table/contactTable.vue';
  import CrmFormDescription from '@/components/business/crm-form-description/index.vue';
  import CrmHeaderTable from '@/components/business/crm-header-table/index.vue';
  import CrmMoveModal from '@/components/business/crm-move-modal/index.vue';
  import CrmOverviewDrawer from '@/components/business/crm-overview-drawer/index.vue';
  import CrmPoolAssignUserSelect from '@/components/business/crm-pool-assign-user-select/index.vue';
  import CrmSelectPoolModal from '@/components/business/crm-select-pool-modal/index.vue';
  import type { TabContentItem } from '@/components/business/crm-tab-setting/type';
  import CrmWorkflowCard from '@/components/business/crm-workflow-card/index.vue';
  import collaborator from './collaborator.vue';
  import CustomerCallRecordTable from './customerCallRecordTable.vue';
  import customerRelation from './customerRelation.vue';
  import customerSmsModal from './customerSmsModal.vue';
  import ContractTimeline from '@/views/contract/contract/components/contractTimeline.vue';
  import ContractDetailDrawer from '@/views/contract/contract/components/detail.vue';
  import opportunityTable from '@/views/opportunity/components/opportunityTable.vue';
  import OrderTable from '@/views/order/order/components/orderTable.vue';

  import {
    batchTransferCustomer,
    deleteCustomer,
    getCustomer,
    getCustomerHeaderList,
    getCustomerStageConfig,
  } from '@/api/modules';
  import useModal from '@/hooks/useModal';
  import { hasAnyPermission } from '@/utils/permission';

  import { type CustomerReachRow, useCustomerReach } from '../hooks/useCustomerReach';

  const FollowDetail = defineAsyncComponent(() => import('@/components/business/crm-follow-detail/index.vue'));

  export interface CustomerNavigationItem {
    id: string;
    name?: string;
    owner?: string;
    callStatus?: number;
    wechatFriendStatus?: number;
    mobile?: string;
    inSharedPool?: boolean;
  }

  const props = defineProps<{
    sourceId: string;
    readonly?: boolean;
    /** 从客户列表打开时传入当前页客户，用于详情页上一页/下一页 */
    navigationRows?: CustomerNavigationItem[];
  }>();
  const emit = defineEmits<{
    (e: 'saved'): void;
    (e: 'deleted'): void;
    (e: 'transfer'): void;
    (e: 'button-select', key: string): void;
    (e: 'update:sourceId', id: string): void;
  }>();

  const { t } = useI18n();
  const Message = useMessage();
  const { openModal } = useModal();

  const show = defineModel<boolean>('show', {
    required: true,
  });

  const crmOverviewDrawerRef = ref<InstanceType<typeof CrmOverviewDrawer>>();
  const layout = computed(() => crmOverviewDrawerRef.value?.layout);
  // 客户详情左侧暂时隐藏“回收公海”“剩余归属”，仅隐藏展示，不删除字段能力。
  const customerHiddenLeftFields = ['recyclePoolName', 'reservedDays'];

  const refreshKey = ref(0);
  const reachRow = ref<CustomerReachRow>({ id: '' });
  const transferLoading = ref(false);
  const collaborationType = ref<CollaborationType>();
  const sourceName = ref('');
  const createSource = ref('MANUAL_CREATE');
  const descriptionRef = ref<InstanceType<typeof CrmFormDescription>>();

  const {
    showReachModal,
    reachModal,
    activeWechatOptions,
    readCallStatus,
    readWechatFriendStatus,
    isReachOwner,
    handleDialCustomer,
    handleSmsCustomer,
    handleWechatCustomer,
    handleWechatFriendCustomer,
    handleReachModalSubmit,
  } = useCustomerReach({
    onStatusUpdated: () => {
      emit('saved');
    },
  });

  const showListNavigation = computed(() => (props.navigationRows?.length ?? 0) > 0);
  const navigationIndex = computed(() => props.navigationRows?.findIndex((row) => row.id === props.sourceId) ?? -1);
  const canGoPrev = computed(() => navigationIndex.value > 0);
  const canGoNext = computed(
    () => navigationIndex.value >= 0 && navigationIndex.value < (props.navigationRows?.length ?? 0) - 1
  );
  const canReachOperate = computed(
    () =>
      !props.readonly &&
      collaborationType.value !== 'READ_ONLY' &&
      Boolean(reachRow.value.id) &&
      isReachOwner(reachRow.value)
  );

  function syncReachRowFromNavigation() {
    const item = props.navigationRows?.find((row) => row.id === props.sourceId);
    if (item) {
      reachRow.value = { ...item };
      return;
    }
    reachRow.value = { id: props.sourceId };
  }

  function goPrevCustomer() {
    const rows = props.navigationRows;
    const index = navigationIndex.value;
    if (!rows || index <= 0) {
      return;
    }
    emit('update:sourceId', rows[index - 1].id);
  }

  function goNextCustomer() {
    const rows = props.navigationRows;
    const index = navigationIndex.value;
    if (!rows || index < 0 || index >= rows.length - 1) {
      return;
    }
    emit('update:sourceId', rows[index + 1].id);
  }

  const stageConfig = ref<Awaited<ReturnType<typeof getCustomerStageConfig>>>();
  const currentStatus = ref<string>('');
  const customerStageStatus = ref<string>('');
  const customerFailReason = ref<string>('');

  const isCustomerCompleted = computed(
    () => currentStatus.value === 'stage_fail' || currentStatus.value === 'stage_payment'
  );

  async function getCustomerDetailStage() {
    try {
      const detail = await getCustomer(props.sourceId);
      if (detail?.stage) {
        currentStatus.value = detail.stage;
      }
      if (detail?.stageStatus) {
        customerStageStatus.value = detail.stageStatus;
      }
      if (detail?.failReason) {
        customerFailReason.value = detail.failReason;
      }
      createSource.value = detail?.createSource || '';
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('getCustomerDetailStage error:', error);
    }
  }

  async function initStageConfig() {
    try {
      stageConfig.value = await getCustomerStageConfig();
      if (stageConfig.value?.stageConfigList?.length && !currentStatus.value) {
        currentStatus.value = stageConfig.value.stageConfigList[0].id;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('initStageConfig error:', error);
    }
  }

  onMounted(() => {
    initStageConfig();
  });

  watch(
    () => show.value,
    (val) => {
      if (val) {
        initStageConfig();
        syncReachRowFromNavigation();
      }
    }
  );

  watch(
    () => props.sourceId,
    () => {
      if (!show.value) {
        return;
      }
      syncReachRowFromNavigation();
      refreshKey.value += 1;
      getCustomerDetailStage();
    }
  );

  watch(
    () => props.navigationRows,
    () => {
      syncReachRowFromNavigation();
    },
    { deep: true }
  );

  const buttonList = computed<ActionsItem[]>(() => {
    if (collaborationType.value || props.readonly) {
      return [];
    }
    const actions: ActionsItem[] = [
      {
        label: t('common.edit'),
        key: 'edit',
        text: false,
        ghost: true,
        class: 'n-btn-outline-primary',
        permission: ['CUSTOMER_MANAGEMENT:UPDATE'],
      },
      {
        label: t('common.transfer'),
        key: 'transfer',
        text: false,
        ghost: true,
        class: 'n-btn-outline-primary',
        permission: ['CUSTOMER_MANAGEMENT:TRANSFER'],
        popConfirmProps: {
          loading: transferLoading.value,
          title: t('common.transfer'),
          positiveText: t('common.confirm'),
          iconType: 'primary',
        },
        popSlotContent: 'transferPopContent',
      },
      {
        label: t('common.delete'),
        key: 'delete',
        text: false,
        ghost: true,
        danger: true,
        class: 'n-btn-outline-primary',
        permission: ['CUSTOMER_MANAGEMENT:DELETE'],
      },
    ];
    if (!['MANUAL_CREATE', 'PRIVATE_IMPORT'].includes(createSource.value)) {
      actions.splice(2, 0, {
        label: t('customer.moveToOpenSea'),
        key: 'moveToOpenSea',
        text: false,
        ghost: true,
        class: 'n-btn-outline-primary',
        permission: ['CUSTOMER_MANAGEMENT:RECYCLE'],
      });
    }
    return actions;
  });

  const activeTab = ref('contact');
  const tabList = computed<TabContentItem[]>(() => {
    // 客户详情隐藏无用标签
    const fullList = [
      {
        name: 'followRecord',
        tab: t('crmFollowRecord.followRecord'),
        enable: true,
      },
      // {
      //   name: 'contact',
      //   tab: t('opportunity.contactInfo'),
      //   enable: true,
      //   permission: ['CUSTOMER_MANAGEMENT_CONTACT:READ'],
      // },
      {
        name: 'followPlan',
        tab: t('common.plan'),
        enable: true,
      },
      {
        name: 'headRecord',
        tab: t('common.headRecord'),
        enable: true,
      },
      // {
      //   name: 'relation',
      //   tab: t('customer.relation'),
      //   enable: true,
      // },
      // {
      //   name: 'opportunityInfo',
      //   tab: t('customer.opportunityInfo'),
      //   enable: true,
      //   permission: ['OPPORTUNITY_MANAGEMENT:READ'],
      // },
      // {
      //   name: 'collaborator',
      //   tab: t('customer.collaborator'),
      //   enable: true,
      // },
      {
        name: 'contract',
        tab: t('module.contract'),
        enable: true,
        permission: ['CONTRACT:READ'],
      },
      {
        name: 'contractPayment',
        tab: t('module.paymentPlan'),
        enable: true,
        permission: ['CONTRACT_PAYMENT_PLAN:READ'],
      },
      {
        name: 'contractPaymentRecord',
        tab: t('module.paymentRecord'),
        enable: true,
        permission: ['CONTRACT_PAYMENT_RECORD:READ'],
      },
      {
        name: 'callRecord',
        tab: t('customer.callRecord'),
        enable: true,
        permission: ['CUSTOMER_MANAGEMENT:READ'],
      },
      // {
      //   name: 'invoice',
      //   tab: t('module.invoice'),
      //   enable: true,
      //   permission: ['CONTRACT_INVOICE:READ'],
      // },
      // {
      //   name: 'order',
      //   tab: t('module.order'),
      //   enable: true,
      //   permission: ['ORDER:READ'],
      // },
    ];
    if (collaborationType.value) {
      return fullList.filter((item) => item.name !== 'collaborator');
    }
    return fullList;
  });

  const transferSelectedUserIds = ref<string[]>([]);

  // 转移
  async function transfer() {
    if (!transferSelectedUserIds.value[0]) {
      Message.warning(t('opportunity.selectReceiverPlaceholder'));
      return;
    }
    try {
      transferLoading.value = true;
      await batchTransferCustomer({
        ids: [props.sourceId],
        owner: transferSelectedUserIds.value[0],
        ownerUserIds: transferSelectedUserIds.value,
      });
      Message.success(t('common.transferSuccess'));
      descriptionRef.value?.initFormDescription();
      emit('transfer');
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error(error);
    } finally {
      transferLoading.value = false;
    }
  }

  // 删除
  function handleDelete() {
    openModal({
      type: 'error',
      title: t('customer.deleteTitleTip'),
      content: t('customer.batchDeleteContentTip'),
      positiveText: t('common.confirmDelete'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await deleteCustomer(props.sourceId);
          Message.success(t('common.deleteSuccess'));
          emit('deleted');
          show.value = false;
        } catch (error) {
          // eslint-disable-next-line no-console
          console.error(error);
        }
      },
    });
  }

  // 移入公海
  const showMoveModal = ref(false);
  const showSelectPoolModal = ref(false);
  const selectedPoolId = ref<string>('');
  function handleMoveToPublicPool() {
    showSelectPoolModal.value = true;
  }
  function handlePoolSelected(poolId: string) {
    selectedPoolId.value = poolId;
    showSelectPoolModal.value = false;
    showMoveModal.value = true;
  }

  function handleButtonSelect(key: string) {
    if (key === 'delete') {
      handleDelete();
    } else if (key === 'pop-transfer') {
      transfer();
    } else if (key === 'moveToOpenSea') {
      handleMoveToPublicPool();
    }
  }

  function handleSaved() {
    refreshKey.value += 1;
    emit('saved');
    // 延迟获取最新阶段，避免被取消
    setTimeout(() => {
      getCustomerDetailStage();
    }, 500);
  }

  function handleDescriptionInit(
    _collaborationType?: CollaborationType,
    _sourceName?: string,
    detail?: Record<string, any>
  ) {
    collaborationType.value = _collaborationType;
    sourceName.value = _sourceName || '';
    createSource.value = detail?.createSource || '';
    if (detail?.stage) {
      currentStatus.value = detail.stage;
    }
    if (detail?.stageStatus) {
      customerStageStatus.value = detail.stageStatus;
    }
    reachRow.value = {
      id: props.sourceId,
      name: _sourceName || reachRow.value.name,
      owner: detail?.owner ?? reachRow.value.owner,
      callStatus: detail?.callStatus ?? reachRow.value.callStatus,
      wechatFriendStatus: detail?.wechatFriendStatus ?? reachRow.value.wechatFriendStatus,
      mobile: detail?.mobile ?? reachRow.value.mobile,
      inSharedPool: detail?.inSharedPool ?? reachRow.value.inSharedPool,
    };
  }

  const showContractDetailDrawer = ref(false);
  const activeSourceId = ref<string>('');
  function handleOpenContractDrawer(params: { id: string }) {
    activeSourceId.value = params.id;
    showContractDetailDrawer.value = true;
  }

  function handleOpenCustomerDrawer() {
    showContractDetailDrawer.value = false;
  }
</script>

<style lang="less" scoped></style>
