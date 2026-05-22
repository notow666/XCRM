<template>
  <div class="flex h-full min-h-0 flex-1 flex-col gap-[12px]">
    <n-spin v-if="loading" class="flex flex-1 items-center justify-center py-[48px]" />
    <n-alert v-else-if="errMsg" type="error">{{ errMsg }}</n-alert>
    <n-alert v-else-if="openedInNewTab" type="success">{{ t('mmbaMgmtSso.openedNewTab') }}</n-alert>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import { NAlert, NSpin } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import { getMmbaMgmtSsoRedirectUrl } from '@/api/modules';

  const { t } = useI18n();
  const loading = ref(true);
  const errMsg = ref('');
  const openedInNewTab = ref(false);

  /** 指掌易页禁止 iframe 嵌入，在新标签页打开 SSO URL。 */
  onMounted(async () => {
    try {
      const data = await getMmbaMgmtSsoRedirectUrl();
      const url = data?.url;
      if (typeof url === 'string' && url.length > 0) {
        const win = window.open(url, '_blank', 'noopener,noreferrer');
        if (win) {
          openedInNewTab.value = true;
        } else {
          errMsg.value = t('mmbaMgmtSso.popupBlocked');
        }
      } else {
        errMsg.value = t('mmbaMgmtSso.emptyUrl');
      }
    } catch {
      errMsg.value = t('mmbaMgmtSso.loadFailed');
    }
    loading.value = false;
  });
</script>
