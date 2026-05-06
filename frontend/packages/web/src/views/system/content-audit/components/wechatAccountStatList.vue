<template>
  <CrmCard hide-footer no-content-bottom-padding>
    <CrmTable
      v-bind="propsRes"
      class="wechat-account-stat-table"
      :scroll-x="1200"
      @page-change="propsEvent.pageChange"
      @page-size-change="propsEvent.pageSizeChange"
      @sorter-change="propsEvent.sorterChange"
      @filter-change="propsEvent.filterChange"
      @refresh="initData"
    />
  </CrmCard>
</template>

<script lang="ts" setup>
  import { h, onBeforeMount } from 'vue';
  import { NAvatar } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { WechatAccountStatItem } from '@lib/shared/models/system/contentAudit';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmNameTooltip from '@/components/pure/crm-name-tooltip/index.vue';
  import CrmTable from '@/components/pure/crm-table/index.vue';
  import { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import useTable from '@/components/pure/crm-table/useTable';

  import { getWechatAccountStatPage } from '@/api/modules';

  const { t } = useI18n();

  function getAvatarFallbackText(name?: string) {
    if (!name) {
      return 'W';
    }
    return name.slice(0, 1).toUpperCase();
  }

  function renderWechatAccount(row: WechatAccountStatItem) {
    return h('div', { class: 'flex items-center gap-[12px] min-w-0' }, [
      h(
        NAvatar,
        {
          round: true,
          size: 40,
          src: row.wxHeaderPic || undefined,
        },
        {
          default: () => getAvatarFallbackText(row.wxNickName),
        }
      ),
      h('div', { class: 'min-w-0 flex-1' }, [
        h('div', { class: 'truncate text-[14px] text-[var(--text-n1)] font-medium' }, row.wxNickName || '-'),
        h('div', { class: 'truncate text-[12px] text-[var(--text-n3)] mt-[4px]' }, row.wxAccount || '-'),
      ]),
    ]);
  }

  const columns = computed<CrmDataTableColumn[]>(() => [
    {
      title: t('contentAudit.accountStat.employee'),
      key: 'employeeName',
      width: 140,
      render: (row: WechatAccountStatItem) => h(CrmNameTooltip, { text: row.employeeName || '-' }),
    },
    {
      title: t('contentAudit.accountStat.department'),
      key: 'departmentName',
      width: 180,
      render: (row: WechatAccountStatItem) => h(CrmNameTooltip, { text: row.departmentName || '-' }),
    },
    {
      title: t('contentAudit.accountStat.device'),
      key: 'deviceDisplay',
      width: 220,
      render: (row: WechatAccountStatItem) => h(CrmNameTooltip, { text: row.deviceDisplay || '-' }),
    },
    {
      title: t('contentAudit.accountStat.wechatAccount'),
      key: 'wxAccount',
      width: 280,
      render: (row: WechatAccountStatItem) => renderWechatAccount(row),
    },
    {
      title: t('contentAudit.accountStat.friend'),
      key: 'friendCount',
      width: 120,
    },
    {
      title: t('contentAudit.accountStat.chatRecord'),
      key: 'chatRecordCount',
      width: 120,
    },
  ]);

  const { propsRes, propsEvent, loadList } = useTable<WechatAccountStatItem>(
    (params) => getWechatAccountStatPage(params || { current: 1, pageSize: 10 }),
    {
      showSetting: false,
      columns: columns.value,
      containerClass: '.wechat-account-stat-table',
    }
  );

  function initData() {
    loadList();
  }

  onBeforeMount(() => {
    initData();
  });
</script>

<style lang="less" scoped></style>
