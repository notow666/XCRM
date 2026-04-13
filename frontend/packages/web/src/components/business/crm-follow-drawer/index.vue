<template>
  <CrmDrawer
    v-model:show="visible"
    resizable
    no-padding
    width="1200"
    :footer="false"
    class="min-w-[1200px]"
    :title="t('settings.navbar.event')"
  >
    <div class="flex h-full flex-col bg-[var(--text-n9)] px-[16px] pt-[16px]">
      <CrmCard no-content-padding hide-footer auto-height class="mb-[16px]">
        <CrmTab v-model:active-tab="activeTab" no-content :tab-list="tabList" type="line" />
      </CrmCard>
      <div v-if="visible" class="flex-1">
        <Suspense>
          <template v-if="activeTab === 'followRecord'">
            <RecordTable ref="recordTableRef" @open-plan="handleOpenPlan" />
          </template>
          <template v-else>
            <PlanTable ref="planTableRef" />
          </template>
        </Suspense>
      </div>
    </div>
  </CrmDrawer>
  <CrmFormCreateDrawer
    v-model:visible="planFormDrawerVisible"
    :form-key="FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER"
    :other-save-params="planFormSaveParams"
    @saved="handlePlanSaved"
  />
</template>

<script lang="ts" setup>
  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';
  import CrmFormCreateDrawer from '@/components/business/crm-form-create-drawer/index.vue';

  import { getCustomerNextStage } from '@/api/modules';

  import type PlanTableType from './components/planTable.vue';
  import type RecordTableType from './components/recordTable.vue';

  const RecordTable = defineAsyncComponent(() => import('./components/recordTable.vue'));
  const PlanTable = defineAsyncComponent(() => import('./components/planTable.vue'));

  const visible = defineModel<boolean>('visible', {
    required: true,
  });

  const { t } = useI18n();

  const activeTab = ref('followRecord');
  const recordTableRef = ref<InstanceType<typeof RecordTableType>>();
  const planTableRef = ref<InstanceType<typeof PlanTableType>>();

  const tabList = [
    {
      name: 'followRecord',
      tab: t('crmFollowRecord.followRecord'),
    },
    {
      name: 'followPlan',
      tab: t('common.plan'),
    },
  ];

  const planFormDrawerVisible = ref(false);
  const planFormSaveParams = ref<Record<string, any>>({ converted: false });

  async function handleOpenPlan(data: Record<string, any>) {
    let nextStageId = '';
    let nextStageName = '';
    let owner = '';
    let ownerName = '';
    // eslint-disable-next-line prefer-destructuring
    let customerName = data.customerName;

    if (data.resourceType === 'CUSTOMER' && data.customerId) {
      try {
        const res = await getCustomerNextStage(data.customerId);
        if (res) {
          nextStageId = res.nextStageId;
          nextStageName = res.nextStageName;
          owner = res.owner || '';
          ownerName = res.ownerName || '';
          customerName = res.customerName || data.customerName;
        }
      } catch (error) {
        // eslint-disable-next-line no-console
        console.error('获取客户下一阶段信息失败', error);
      }
    }

    planFormSaveParams.value = {
      converted: false,
      customerId: data.customerId,
      customerName,
      opportunityId: data.opportunityId,
      contactId: data.contactId,
      type: data.resourceType,
      nextStage: nextStageId,
      _nextStage: nextStageId,
      nextStageName,
      owner,
      ownerName,
    };
    planFormDrawerVisible.value = true;
  }

  function handlePlanSaved() {
    planFormSaveParams.value = { converted: false };
    recordTableRef.value?.refresh();
  }
</script>
