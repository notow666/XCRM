<template>
  <div class="h-full">
    <CrmCard no-content-padding hide-footer>
      <div class="p-[16px]">
        <n-data-table
          :columns="columns"
          :data="dataSource"
          :loading="loading"
          :pagination="pagination"
          :scroll-x="1100"
          :row-key="(row) => row.id"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </CrmCard>
    <customerOverviewDrawer
      v-model:show="showCustomerDrawer"
      :source-id="activeCustomerId"
      @saved="loadList"
    />
    <CrmFormCreateDrawer
      v-model:visible="planFormDrawerVisible"
      :form-key="FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER"
      :source-id="selectedPlan?.customerId"
      :initial-source-name="selectedPlan?.customerName"
      :need-init-detail="false"
      :link-form-key="FormDesignKeyEnum.CUSTOMER"
      :other-save-params="planFormSaveParams"
      @saved="handlePlanSaved"
    />
  </div>
</template>

<script setup lang="ts">
  import { h } from 'vue';
  import { NButton, NDataTable, useMessage } from 'naive-ui';
  import dayjs from 'dayjs';

  import { CustomerFollowPlanStatusEnum } from '@lib/shared/enums/customerEnum';
  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerFollowPlanListItem } from '@lib/shared/models/customer';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmFormCreateDrawer from '@/components/business/crm-form-create-drawer/index.vue';
  import customerOverviewDrawer from '@/views/customer/components/customerOverviewDrawer.vue';

  import { getCustomerNextStage, getTaskFollowPlanPage } from '@/api/modules';
  import useModal from '@/hooks/useModal';
  import { hasAnyPermission } from '@/utils/permission';

  import type { DataTableColumns, PaginationProps } from 'naive-ui';

  const { t } = useI18n();
  const Message = useMessage();
  const { openModal } = useModal();

  const loading = ref(false);
  const dataSource = ref<CustomerFollowPlanListItem[]>([]);
  const page = ref(1);
  const pageSize = ref(30);
  const total = ref(0);

  const showCustomerDrawer = ref(false);
  const activeCustomerId = ref('');

  const planFormDrawerVisible = ref(false);
  const selectedPlan = ref<CustomerFollowPlanListItem | null>(null);
  const planFormSaveParams = ref<Record<string, any>>({ converted: false });

  const pagination = computed<PaginationProps>(() => ({
    page: page.value,
    pageSize: pageSize.value,
    itemCount: total.value,
    showSizePicker: true,
    pageSizes: [10, 20, 30, 50],
  }));

  function statusLabel(s: string) {
    const map: Record<string, string> = {
      PREPARED: t('task.status.prepared'),
      UNDERWAY: t('task.status.underway'),
      COMPLETED: t('task.status.completed'),
      CANCELLED: t('task.status.cancelled'),
    };
    return map[s] ?? s;
  }

  function completionStatusLabel(s?: string) {
    const map: Record<string, string> = {
      UNCOMPLETED: t('task.completionStatus.uncompleted'),
      COMPLETED: t('task.completionStatus.completed'),
    };
    return map[s ?? ''] ?? '-';
  }

  function formatTime(ts?: number) {
    if (!ts) return '-';
    return dayjs(ts).format('YYYY-MM-DD HH:mm');
  }

  async function loadList() {
    loading.value = true;
    try {
      const res = await getTaskFollowPlanPage({
        sourceId: '',
        status: CustomerFollowPlanStatusEnum.ALL,
        current: page.value,
        pageSize: pageSize.value,
      });
      dataSource.value = (res.list ?? []) as CustomerFollowPlanListItem[];
      total.value = res.total ?? 0;
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error(e);
    } finally {
      loading.value = false;
    }
  }

  function onPageChange(p: number) {
    page.value = p;
    loadList();
  }

  function onPageSizeChange(s: number) {
    pageSize.value = s;
    page.value = 1;
    loadList();
  }

  function handleCustomerClick(customerId: string) {
    activeCustomerId.value = customerId;
    showCustomerDrawer.value = true;
  }

  async function handleComplete(row: CustomerFollowPlanListItem) {
    selectedPlan.value = row;
    const { customerId, customerName } = row;

    const params: Record<string, any> = { converted: false, type: 'CUSTOMER' };
    try {
      const res = await getCustomerNextStage(customerId);
      if (res) {
        params.nextStage = res.nextStageId || '';
        params._nextStage = res.nextStageId || '';
        params.nextStageName = res.nextStageName || '';
        params.owner = res.owner || '';
        params.ownerName = res.ownerName || '';
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('获取客户下一阶段信息失败', error);
    }

    planFormSaveParams.value = params;
    planFormDrawerVisible.value = true;
  }

  async function handlePlanSaved() {
    Message.success(t('common.operationSuccess'));
    planFormSaveParams.value = { converted: false };
    planFormDrawerVisible.value = false;
    selectedPlan.value = null;
    await loadList();
  }

  const columns = computed<DataTableColumns<CustomerFollowPlanListItem>>(() => [
    {
      title: t('task.column.customer'),
      key: 'customerName',
      width: 180,
      ellipsis: { tooltip: true },
      render(row) {
        return h(
          NButton,
          {
            text: true,
            type: 'primary',
            onClick: () => handleCustomerClick(row.customerId),
          },
          { default: () => row.customerName ?? '-' }
        );
      },
    },
    {
      title: t('task.column.stage'),
      key: 'customerStageName',
      width: 140,
      ellipsis: { tooltip: true },
      render(row) {
        return row.customerStageName ?? '-';
      },
    },
    {
      title: t('task.column.planTime'),
      key: 'estimatedTime',
      width: 170,
      render(row) {
        return formatTime(row.estimatedTime);
      },
    },
    {
      title: t('task.column.completionStatus'),
      key: 'completionStatus',
      width: 100,
      render(row) {
        return completionStatusLabel(row.completionStatus);
      },
    },
    {
      title: t('task.column.completionTime'),
      key: 'completionTime',
      width: 170,
      render(row) {
        return formatTime(row.completionTime);
      },
    },
    {
      title: t('task.column.owner'),
      key: 'customerOwnerName',
      width: 120,
      ellipsis: { tooltip: true },
    },
    {
      title: t('common.operation'),
      key: 'actions',
      width: 100,
      fixed: 'right',
      render(row) {
        if (!hasAnyPermission(['TASK:COMPLETE'])) {
          return '-';
        }
        return h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            secondary: true,
            onClick: () => handleComplete(row),
          },
          { default: () => t('task.complete') }
        );
      },
    },
  ]);

  onMounted(() => {
    loadList();
  });
</script>
