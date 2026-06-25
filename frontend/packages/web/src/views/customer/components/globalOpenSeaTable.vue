<template>
  <CrmTable
    v-bind="tableProps"
    class="crm-global-open-sea-table"
    :columns="columns"
    @page-change="propsEvent.pageChange"
    @page-size-change="propsEvent.pageSizeChange"
    @refresh="searchData"
  >
    <template #tableTop>
      <div class="flex w-full justify-end">
        <div class="flex items-center gap-[8px]">
          <n-input
            v-model:value="mobile"
            class="w-[280px]"
            clearable
            :maxlength="30"
            :placeholder="t('customer.globalPoolSearchPlaceholder')"
            @clear="clearData"
            @keydown.enter="searchData"
          />
          <n-button type="primary" :disabled="!mobile.trim()" @click="searchData">
            {{ t('common.search') }}
          </n-button>
        </div>
      </div>
    </template>
  </CrmTable>
</template>

<script setup lang="ts">
  import { NButton, NInput, useMessage } from 'naive-ui';

  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { validatePhone } from '@lib/shared/method/validate';

  import CrmTable from '@/components/pure/crm-table/index.vue';
  import { CrmDataTableColumn } from '@/components/pure/crm-table/type';

  import { getGlobalPoolCustomerPage } from '@/api/modules';
  import useFormCreateTable from '@/hooks/useFormCreateTable';

  const { t } = useI18n();
  const Message = useMessage();
  const mobile = ref('');

  const { useTableRes } = await useFormCreateTable({
    formKey: FormDesignKeyEnum.CUSTOMER_OPEN_SEA,
    containerClass: '.crm-global-open-sea-table',
    permission: [],
    readonly: true,
    hiddenAllScreen: true,
    hiddenRefresh: true,
    showSetting: false,
    listApi: getGlobalPoolCustomerPage,
  });
  const { propsRes, propsEvent, loadList, setLoadListParams } = useTableRes;
  const tableProps = computed(() => {
    const restProps = { ...(propsRes.value as Record<string, unknown>) };
    delete restProps.onSorterChange;
    return restProps;
  });

  const columns = computed<CrmDataTableColumn[]>(() => {
    const removedColumnKeys = new Set(['follower', 'followTime']);
    const baseColumns = propsRes.value.columns
      .filter((item) => item.key && !removedColumnKeys.has(String(item.key)))
      .map((item) => ({ ...item, filter: false, sorter: false })) as CrmDataTableColumn[];
    const poolColumn: CrmDataTableColumn = {
      title: t('customer.poolName'),
      key: 'poolName',
      width: 160,
      ellipsis: {
        tooltip: true,
      },
      render: (row: any) => row.poolName || '-',
    };
    const nameIndex = baseColumns.findIndex((item) => item.key === 'name');
    if (nameIndex < 0) {
      return [...baseColumns, poolColumn];
    }
    return [...baseColumns.slice(0, nameIndex + 1), poolColumn, ...baseColumns.slice(nameIndex + 1)];
  });

  function clearData() {
    propsRes.value.data = [];
    if (propsRes.value.crmPagination) {
      propsRes.value.crmPagination.page = 1;
      propsRes.value.crmPagination.itemCount = 0;
    }
  }

  function searchData() {
    const exactMobile = mobile.value.trim();
    if (!exactMobile) {
      clearData();
      return;
    }
    if (!validatePhone(exactMobile)) {
      clearData();
      Message.warning(t('customer.globalPoolSearchMobileInvalid'));
      return;
    }
    setLoadListParams({ mobile: exactMobile });
    loadList();
  }
</script>
