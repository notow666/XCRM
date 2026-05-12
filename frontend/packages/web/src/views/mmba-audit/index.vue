<template>
  <n-scrollbar x-scrollable :content-style="{ 'min-width': '1000px', 'width': '100%', 'height': '100%' }">
    <CrmCard no-content-padding hide-footer auto-height class="mb-[16px]">
      <CrmTab v-model:active-tab="listActiveTab" no-content :tab-list="listTabList" type="line" />
    </CrmCard>

    <CrmCard hide-footer auto-height class="form-card mb-[16px] min-w-[1000px]">
      <div class="flex flex-wrap items-end justify-between gap-[16px]">
        <n-form
          label-placement="left"
          label-width="auto"
          :model="form"
          class="grid flex-1 grid-cols-1 gap-x-[24px] md:grid-cols-3"
        >
          <n-form-item :label="t('mmbaAudit.filter.date')" path="dateRange">
            <n-date-picker v-model:value="form.dateRange" type="daterange" clearable class="w-full" />
          </n-form-item>
          <n-form-item :label="t('mmbaAudit.filter.keyword')" path="keyword">
            <n-input v-model:value="form.keyword" :placeholder="t('mmbaAudit.filter.keywordPlaceholder')" clearable />
          </n-form-item>
          <n-form-item>
            <n-button ghost class="mr-[12px]" type="primary" @click="handleSearch">
              {{ t('mmbaAudit.filter.search') }}
            </n-button>
            <n-button type="default" class="outline--secondary" @click="handleReset">
              {{ t('mmbaAudit.filter.reset') }}
            </n-button>
          </n-form-item>
        </n-form>
        <n-button type="primary" secondary @click="handleExportClick">
          {{ t('mmbaAudit.export') }}
        </n-button>
      </div>
    </CrmCard>

    <CrmCard no-content-bottom-padding hide-footer class="min-w-[1000px]">
      <n-data-table :columns="columns" :data="tableData" :bordered="false" />
    </CrmCard>
  </n-scrollbar>
</template>

<script lang="ts" setup>
  import { useRoute } from 'vue-router';
  import {
    type DataTableColumns,
    NButton,
    NDataTable,
    NDatePicker,
    NForm,
    NFormItem,
    NInput,
    NScrollbar,
    useMessage,
  } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';

  import { MMBAAuditRouteEnum } from '@/enums/routeEnum';

  const { t } = useI18n();
  const message = useMessage();
  const route = useRoute();

  const listActiveTab = ref('wxAccountStat');

  const listTabList = computed(() => {
    switch (route.name) {
      case MMBAAuditRouteEnum.MMBA_AUDIT_WECHAT:
        return [
          { name: 'wxAccountStat', tab: t('mmbaAudit.wechatTab.accountStat') },
          { name: 'singleChat', tab: t('mmbaAudit.wechatTab.singleChat') },
          { name: 'groupChat', tab: t('mmbaAudit.wechatTab.groupChat') },
          // { name: 'rejectedFriend', tab: t('mmbaAudit.wechatTab.rejectedFriend') },
          // { name: 'verifyMessage', tab: t('mmbaAudit.wechatTab.verifyMessage') },
          { name: 'loggedWx', tab: t('mmbaAudit.wechatTab.loggedWx') },
          { name: 'loginLogout', tab: t('mmbaAudit.wechatTab.loginLogout') },
          // { name: 'wxTeamMsg', tab: t('mmbaAudit.wechatTab.wxTeamMsg') },
        ];
      case MMBAAuditRouteEnum.MMBA_AUDIT_CALL:
        return [
          { name: 'callStat', tab: t('mmbaAudit.callTab.stat') },
          { name: 'callDetail', tab: t('mmbaAudit.callTab.detail') },
        ];
      case MMBAAuditRouteEnum.MMBA_AUDIT_SMS:
        return [
          { name: 'smsStat', tab: t('mmbaAudit.smsTab.stat') },
          { name: 'smsDetail', tab: t('mmbaAudit.smsTab.detail') },
        ];
      default:
        return [];
    }
  });

  watch(
    () => route.name,
    () => {
      const first = listTabList.value[0]?.name;
      if (first) {
        listActiveTab.value = first;
      }
    },
    { immediate: true }
  );

  const form = reactive({
    dateRange: null as [number, number] | null,
    keyword: '',
  });

  const tableData = ref<Record<string, string>[]>([]);

  const columns = computed<DataTableColumns<Record<string, string>>>(() => [
    { title: t('mmbaAudit.table.employee'), key: 'employee', ellipsis: { tooltip: true } },
    { title: t('mmbaAudit.table.department'), key: 'department', ellipsis: { tooltip: true } },
    { title: t('mmbaAudit.table.device'), key: 'device', ellipsis: { tooltip: true } },
    { title: t('mmbaAudit.table.action'), key: 'action', width: 120 },
  ]);

  function handleSearch() {
    // Shell: listActiveTab / route.name identify view; wire API later
  }

  function handleReset() {
    form.dateRange = null;
    form.keyword = '';
  }

  function handleExportClick() {
    message.info(t('mmbaAudit.exportSoon'));
  }
</script>
