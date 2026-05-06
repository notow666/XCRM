<template>
  <CrmCard no-content-padding hide-footer>
    <CrmSplitPanel class="h-full" :max="0.35" :min="0.2" :default-size="0.22">
      <template #1>
        <div class="content-audit-left h-full">
          <div class="content-audit-left__title">{{ t('contentAudit.channelTitle') }}</div>
          <button
            type="button"
            class="content-audit-left__item"
            :class="{ 'content-audit-left__item--active': activeChannel === 'wechat' }"
            @click="activeChannel = 'wechat'"
          >
            {{ t('contentAudit.channel.wechat') }}
          </button>
        </div>
      </template>
      <template #2>
        <div class="content-audit-right h-full">
          <CrmCard no-content-padding hide-footer auto-height class="mb-[16px]">
            <CrmTab v-model:active-tab="activeTab" no-content :tab-list="tabList" type="line" />
          </CrmCard>

          <WechatAccountStatList v-if="activeTab === 'accountStat'" />
          <CrmCard v-else hide-footer>
            <n-empty :description="t('contentAudit.placeholder')" />
          </CrmCard>
        </div>
      </template>
    </CrmSplitPanel>
  </CrmCard>
</template>

<script lang="ts" setup>
  import { NEmpty } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmSplitPanel from '@/components/pure/crm-split-panel/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';
  import WechatAccountStatList from './components/wechatAccountStatList.vue';

  const { t } = useI18n();

  const activeChannel = ref('wechat');
  const activeTab = ref('accountStat');
  const tabList = computed(() => [
    { name: 'accountStat', tab: t('contentAudit.tab.accountStat') },
    { name: 'pendingFriend', tab: t('contentAudit.tab.pendingFriend') },
    { name: 'verifyMessage', tab: t('contentAudit.tab.verifyMessage') },
    { name: 'loginLogout', tab: t('contentAudit.tab.loginLogout') },
  ]);
</script>

<style lang="less" scoped>
  .content-audit-left {
    padding: 24px 20px;
    background: var(--bg-3);
  }

  .content-audit-left__title {
    margin-bottom: 16px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-n2);
  }

  .content-audit-left__item {
    display: flex;
    width: 100%;
    align-items: center;
    border: none;
    border-radius: 12px;
    background: transparent;
    padding: 12px 14px;
    color: var(--text-n2);
    text-align: left;
    transition: all 0.2s ease;
  }

  .content-audit-left__item--active {
    background: var(--primary-0);
    color: var(--primary-6);
    font-weight: 600;
  }

  .content-audit-right {
    padding: 16px;
  }
</style>
