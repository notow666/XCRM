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
  </div>
</template>

<script setup lang="ts">
  import { h } from 'vue';
  import { NButton, NDataTable, useMessage } from 'naive-ui';
  import dayjs from 'dayjs';

  import { CustomerFollowPlanStatusEnum } from '@lib/shared/enums/customerEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerFollowPlanListItem } from '@lib/shared/models/customer';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import { completeTaskFollowPlan, getTaskFollowPlanPage } from '@/api/modules';
  import useModal from '@/hooks/useModal';
  import useOpenNewPage from '@/hooks/useOpenNewPage';
  import { hasAnyPermission } from '@/utils/permission';

  import { CustomerRouteEnum } from '@/enums/routeEnum';

  import type { DataTableColumns, PaginationProps } from 'naive-ui';

  const { t } = useI18n();
  const Message = useMessage();
  const { openModal } = useModal();
  const { openNewPage } = useOpenNewPage();

  const loading = ref(false);
  const dataSource = ref<CustomerFollowPlanListItem[]>([]);
  const page = ref(1);
  const pageSize = ref(30);
  const total = ref(0);

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

  function handleComplete(row: CustomerFollowPlanListItem) {
    openModal({
      type: 'warning',
      title: t('common.tip'),
      content: t('task.completeConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await completeTaskFollowPlan({ id: row.id });
        Message.success(t('common.operationSuccess'));
        await loadList();
      },
    });
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
            onClick: () =>
              openNewPage(CustomerRouteEnum.CUSTOMER_INDEX, {
                id: row.customerId,
              }),
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
      title: t('task.column.status'),
      key: 'status',
      width: 100,
      render(row) {
        return statusLabel(row.status);
      },
    },
    {
      title: t('task.column.owner'),
      key: 'ownerName',
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
