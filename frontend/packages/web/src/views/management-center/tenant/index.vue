<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-3 flex items-center gap-2">
        <NInput
          v-model:value="keyword"
          clearable
          placeholder="tenantId/code/name"
          class="w-[280px]"
          @keyup.enter="handleSearch"
        />
        <NButton type="primary" @click="handleSearch">{{ t('common.search') }}</NButton>
        <NButton @click="handleReset">{{ t('common.reset') }}</NButton>
        <NButton @click="openCreate">{{ t('common.add') }}</NButton>
      </div>
      <NDataTable :columns="columns" :data="rows" :loading="loading" :pagination="false" />
      <div class="mt-4 flex justify-end">
        <NPagination
          v-model:page="pagination.current"
          v-model:page-size="pagination.pageSize"
          :item-count="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          show-size-picker
          @update:page="loadData"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
  </CrmCard>

  <NModal
    v-model:show="showCreate"
    preset="dialog"
    title="创建租户"
    positive-text="提交"
    @positive-click="handleCreate"
  >
    <NSpace vertical>
      <NInput v-model:value="createForm.code" placeholder="code" />
      <NInput v-model:value="createForm.name" placeholder="name" />
      <NInput v-model:value="createForm.initialUsers" placeholder="initialUserIds，逗号分隔（可选）" />
    </NSpace>
  </NModal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { NButton, NDataTable, NInput, NModal, NPagination, NSpace, NTag, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    getPlatformTenantHealth,
    pagePlatformTenants,
    type PlatformTenantItem,
    provisionPlatformTenant,
    rerunPlatformTenantMigrate,
    updatePlatformTenantStatus,
  } from '@/api/modules';

  const { t } = useI18n();
  const message = useMessage();

  const loading = ref(false);
  const rows = ref<PlatformTenantItem[]>([]);
  const keyword = ref('');
  const pagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
  });
  const showCreate = ref(false);
  const createForm = reactive({
    code: '',
    name: '',
    initialUsers: '',
  });

  async function loadData() {
    loading.value = true;
    try {
      const res = await pagePlatformTenants({
        current: pagination.current,
        pageSize: pagination.pageSize,
        keyword: keyword.value,
      });
      rows.value = res.list || [];
      pagination.total = res.total || 0;
      pagination.current = res.current || pagination.current;
      pagination.pageSize = res.pageSize || pagination.pageSize;
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    pagination.current = 1;
    loadData();
  }

  function handleReset() {
    keyword.value = '';
    pagination.current = 1;
    loadData();
  }

  function handlePageSizeChange() {
    pagination.current = 1;
    loadData();
  }

  function openCreate() {
    createForm.code = '';
    createForm.name = '';
    createForm.initialUsers = '';
    showCreate.value = true;
  }

  async function handleCreate() {
    const initialUserIds = createForm.initialUsers
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const task = await provisionPlatformTenant({
      code: createForm.code,
      name: createForm.name,
      initialUserIds,
    });
    message.success(`创建任务已提交，任务ID: ${task.taskId}`);
    pagination.current = 1;
    loadData();
  }

  async function switchStatus(row: PlatformTenantItem, enabled: boolean) {
    await updatePlatformTenantStatus(row.tenantId, enabled);
    message.success(enabled ? '已启用' : '已停用');
    loadData();
  }

  async function showHealth(row: PlatformTenantItem) {
    const health = await getPlatformTenantHealth(row.tenantId);
    message.info(
      `metadata=${health.metadataExists}, ds=${health.datasourceRegistered}, jdbc=${health.jdbcReachable}, version=${
        health.migrationVersion || '-'
      }`
    );
  }

  async function rerunMigrate(row: PlatformTenantItem) {
    await rerunPlatformTenantMigrate(row.tenantId);
    message.success('迁移完成');
  }

  const columns = computed(() => [
    { title: '租户名称', key: 'name' },
    { title: '唯一标识', key: 'tenantId' },
    { title: 'dbName', key: 'dbName' },
    {
      title: 'status',
      key: 'status',
      render: (row: PlatformTenantItem) =>
        h(
          NTag,
          { type: row.status === 'ACTIVE' ? 'success' : 'warning' },
          { default: () => row.status || (row.enabled ? 'ACTIVE' : 'DISABLED') }
        ),
    },
    {
      title: 'action',
      key: 'action',
      render: (row: PlatformTenantItem) =>
        h('div', { class: 'flex gap-2' }, [
          h(NButton, { size: 'small', onClick: () => showHealth(row) }, { default: () => '健康' }),
          // h(NButton, { size: 'small', onClick: () => rerunMigrate(row) }, { default: () => '重跑迁移' }),
          h(
            NButton,
            {
              size: 'small',
              type: row.status === 'ACTIVE' ? 'warning' : 'primary',
              onClick: () => switchStatus(row, row.status !== 'ACTIVE'),
            },
            { default: () => (row.status === 'ACTIVE' ? '停用' : '启用') }
          ),
        ]),
    },
  ]);

  onMounted(() => {
    loadData();
  });
</script>
