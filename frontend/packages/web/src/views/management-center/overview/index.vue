<template>
  <CrmCard no-content-padding hide-footer>
    <div class="grid gap-4 md:grid-cols-3">
      <div class="rounded border p-4">
        <div class="text-sm text-[var(--text-n4)]">{{ t('menu.managementCenter.tenant') }}</div>
        <div class="mt-2 text-2xl font-semibold">{{ tenantStats.total }}</div>
      </div>
      <div class="rounded border p-4">
        <div class="text-sm text-[var(--text-n4)]">ACTIVE</div>
        <div class="mt-2 text-2xl font-semibold text-green-600">{{ tenantStats.active }}</div>
      </div>
      <div class="rounded border p-4">
        <div class="text-sm text-[var(--text-n4)]">DISABLED</div>
        <div class="mt-2 text-2xl font-semibold text-orange-500">{{ tenantStats.disabled }}</div>
      </div>
    </div>
  </CrmCard>
</template>

<script setup lang="ts">
  import { onMounted, reactive } from 'vue';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import { pagePlatformTenants } from '@/api/modules';

  const { t } = useI18n();

  const tenantStats = reactive({
    total: 0,
    active: 0,
    disabled: 0,
  });

  async function loadStats() {
    const res = await pagePlatformTenants({ current: 1, pageSize: 200 });
    tenantStats.total = res.total || 0;
    tenantStats.active = (res.list || []).filter((item) => item.status === 'ACTIVE').length;
    tenantStats.disabled = (res.list || []).filter((item) => item.status === 'DISABLED').length;
  }

  onMounted(() => {
    loadStats();
  });
</script>
