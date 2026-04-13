<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-3 flex items-center gap-2">
        <n-input v-model:value="tenantId" clearable placeholder="tenantId" class="w-[240px]" />
        <n-button type="primary" @click="loadData">{{ t('common.search') }}</n-button>
      </div>
      <n-data-table :columns="columns" :data="rows" :loading="loading" :pagination="false" />
    </div>
  </CrmCard>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import { pagePlatformAudits, type PlatformAuditItem } from '@/api/modules';

  const { t } = useI18n();
  const loading = ref(false);
  const tenantId = ref('');
  const rows = ref<PlatformAuditItem[]>([]);

  async function loadData() {
    loading.value = true;
    try {
      const res = await pagePlatformAudits({ current: 1, pageSize: 100, tenantId: tenantId.value });
      rows.value = res.list || [];
    } finally {
      loading.value = false;
    }
  }

  const columns = computed(() => [
    { title: 'time', key: 'createTime' },
    { title: 'operator', key: 'operatorId' },
    { title: 'action', key: 'action' },
    { title: 'tenant', key: 'tenantId' },
    { title: 'result', key: 'result' },
    { title: 'detail', key: 'detail' },
  ]);

  onMounted(() => {
    loadData();
  });
</script>
