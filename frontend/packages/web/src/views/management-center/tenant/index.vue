<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-3 flex items-center gap-2">
        <NInput
          v-model:value="keyword"
          clearable
          :placeholder="t('managementCenter.tenant.keywordPlaceholder')"
          class="min-w-[200px] flex-1 sm:max-w-[280px]"
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
      <NInput v-model:value="createForm.orgId" placeholder="org_id（MMBA部门ID，可选）" />
      <NInput v-model:value="createForm.initialUsers" placeholder="initialUserIds，逗号分隔（可选）" />
    </NSpace>
  </NModal>

  <NModal
    v-model:show="showOrgSync"
    preset="dialog"
    title="MMBA部门同步"
    positive-text="保存"
    @positive-click="handleSaveOrgId"
  >
    <NSpace vertical>
      <div class="text-sm text-[#999]">填写租户对应 MMBA 平台部门ID（org_id）后保存</div>
      <NInput v-model:value="orgSyncForm.orgId" placeholder="org_id" />
    </NSpace>
  </NModal>

  <NModal
    v-model:show="showEditName"
    preset="dialog"
    title="编辑租户名称"
    positive-text="保存"
    @positive-click="handleSaveName"
  >
    <NSpace vertical>
      <div class="text-sm text-[#999]">租户唯一标识：{{ nameEditForm.tenantId }}</div>
      <NInput v-model:value="nameEditForm.name" placeholder="租户名称" />
    </NSpace>
  </NModal>

  <NModal
    v-model:show="showShadow"
    preset="card"
    :title="t('managementCenter.tenant.shadowModalTitle')"
    class="w-[520px]"
    @after-leave="stopShadowPolling"
  >
    <NSpin :show="shadowLoading">
      <div class="space-y-4">
        <div class="text-sm leading-relaxed text-[var(--text-n4)]">
          {{ t('managementCenter.tenant.shadowModalHint') }}
        </div>
        <div class="rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-3 text-sm">
          <div class="mb-2 font-medium text-[var(--text-n2)]"
            >{{ shadowForm.tenantName }} ({{ shadowForm.tenantId }})</div
          >
          <div class="grid gap-2">
            <div class="flex justify-between gap-4">
              <span class="text-[var(--text-n4)]">{{ t('managementCenter.tenant.shadowStatusEnabled') }}</span>
              <NTag :type="shadowStatus?.shadowEnabled ? 'success' : 'default'" size="small">
                {{
                  shadowStatus?.shadowEnabled
                    ? t('managementCenter.tenant.shadowStatusEnabled')
                    : t('managementCenter.tenant.shadowStatusDisabled')
                }}
              </NTag>
            </div>
            <div v-if="shadowStatus?.shadowEnabled" class="flex justify-between gap-4">
              <span class="text-[var(--text-n4)]">{{ t('managementCenter.tenant.shadowActiveRole') }}</span>
              <NTag :type="shadowStatus.activeDbRole === 'SHADOW' ? 'warning' : 'info'" size="small">
                {{
                  shadowStatus.activeDbRole === 'SHADOW'
                    ? t('managementCenter.tenant.shadowActive')
                    : t('managementCenter.tenant.shadowPrimary')
                }}
              </NTag>
            </div>
            <div v-if="shadowStatus?.maintenanceState" class="flex justify-between gap-4">
              <span class="text-[var(--text-n4)]">{{ t('managementCenter.tenant.shadowMaintenance') }}</span>
              <span>{{ formatMaintenanceState(shadowStatus.maintenanceState) }}</span>
            </div>
            <div v-if="shadowStatus?.maintenanceUntil" class="flex justify-between gap-4">
              <span class="text-[var(--text-n4)]">{{ t('managementCenter.tenant.shadowMaintenanceUntil') }}</span>
              <span>{{ formatDateTime(shadowStatus.maintenanceUntil) }}</span>
            </div>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <NButton :loading="shadowLoading" @click="refreshShadowStatus">
            {{ t('managementCenter.tenant.shadowRefreshStatus') }}
          </NButton>
          <NPopconfirm v-if="shadowForm.active && !shadowStatus?.shadowEnabled" @positive-click="handleEnableShadow">
            <template #trigger>
              <NButton type="primary" :loading="shadowActionLoading">
                {{ t('managementCenter.tenant.shadowEnable') }}
              </NButton>
            </template>
            {{ t('managementCenter.tenant.shadowEnableConfirm') }}
          </NPopconfirm>
          <NPopconfirm
            v-if="
              shadowStatus?.shadowEnabled && shadowStatus.activeDbRole !== 'SHADOW' && !shadowStatus.maintenanceState
            "
            @positive-click="handleSwitchToShadow"
          >
            <template #trigger>
              <NButton type="warning" :loading="shadowActionLoading">
                {{ t('managementCenter.tenant.shadowSwitchToShadow') }}
              </NButton>
            </template>
            {{ t('managementCenter.tenant.shadowSwitchToShadowConfirm') }}
          </NPopconfirm>
          <NPopconfirm
            v-if="
              shadowStatus?.shadowEnabled && shadowStatus.activeDbRole === 'SHADOW' && !shadowStatus.maintenanceState
            "
            @positive-click="handleSwitchToPrimary"
          >
            <template #trigger>
              <NButton type="info" :loading="shadowActionLoading">
                {{ t('managementCenter.tenant.shadowSwitchToPrimary') }}
              </NButton>
            </template>
            {{ t('managementCenter.tenant.shadowSwitchToPrimaryConfirm') }}
          </NPopconfirm>
        </div>
        <div v-if="!shadowForm.active" class="text-xs text-[var(--warning-color)]">
          {{ t('managementCenter.tenant.shadowTenantFrozenHint') }}
        </div>
      </div>
    </NSpin>
  </NModal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue';
  import {
    NButton,
    NDataTable,
    NInput,
    NModal,
    NPagination,
    NPopconfirm,
    NSpace,
    NSpin,
    NTag,
    useMessage,
  } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    enablePlatformTenantShadow,
    getPlatformTenantHealth,
    getPlatformTenantShadowStatus,
    pagePlatformTenants,
    type PlatformTenantItem,
    type PlatformTenantShadowMeta,
    provisionPlatformTenant,
    switchPlatformTenantToPrimary,
    switchPlatformTenantToShadow,
    updatePlatformTenantName,
    updatePlatformTenantOrgId,
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
    orgId: '',
    initialUsers: '',
  });
  const showOrgSync = ref(false);
  const orgSyncForm = reactive({
    tenantId: '',
    orgId: '',
  });
  const showEditName = ref(false);
  const nameEditForm = reactive({
    tenantId: '',
    name: '',
  });
  const showShadow = ref(false);
  const shadowLoading = ref(false);
  const shadowActionLoading = ref(false);
  const shadowStatus = ref<PlatformTenantShadowMeta | null>(null);
  const shadowForm = reactive({
    tenantId: '',
    tenantName: '',
    active: true,
  });
  let shadowPollTimer: ReturnType<typeof setInterval> | null = null;

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
    createForm.orgId = '';
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
      orgId: createForm.orgId || undefined,
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

  function openOrgSync(row: PlatformTenantItem) {
    orgSyncForm.tenantId = row.tenantId;
    orgSyncForm.orgId = row.orgId || '';
    showOrgSync.value = true;
  }

  function formatDateTime(ms?: number | null) {
    if (ms == null) return '-';
    const d = new Date(ms);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
      d.getMinutes()
    )}:${pad(d.getSeconds())}`;
  }

  function formatMaintenanceState(state?: string | null) {
    if (state === 'PRE_NOTICE') return t('managementCenter.tenant.shadowMaintenancePreNotice');
    if (state === 'BLOCKING') return t('managementCenter.tenant.shadowMaintenanceBlocking');
    return t('managementCenter.tenant.shadowMaintenanceNone');
  }

  function renderShadowTag(row: PlatformTenantItem) {
    if (!row.shadowEnabled) {
      return h(
        NTag,
        { size: 'small', bordered: false },
        { default: () => t('managementCenter.tenant.shadowNotEnabled') }
      );
    }
    const isShadow = row.activeDbRole === 'SHADOW';
    return h(
      NTag,
      { size: 'small', type: isShadow ? 'warning' : 'info' },
      {
        default: () =>
          isShadow ? t('managementCenter.tenant.shadowActive') : t('managementCenter.tenant.shadowPrimary'),
      }
    );
  }

  async function handleSaveOrgId() {
    const orgId = orgSyncForm.orgId.trim();
    if (!orgId) {
      message.warning('请先填写org_id');
      return false;
    }
    await updatePlatformTenantOrgId(orgSyncForm.tenantId, orgId);
    message.success('org_id已保存');
    await loadData();
    return true;
  }

  function openEditName(row: PlatformTenantItem) {
    nameEditForm.tenantId = row.tenantId;
    nameEditForm.name = row.name || '';
    showEditName.value = true;
  }

  async function handleSaveName() {
    const name = nameEditForm.name.trim();
    if (!name) {
      message.warning('请先填写租户名称');
      return false;
    }
    try {
      await updatePlatformTenantName(nameEditForm.tenantId, name);
      message.success('租户名称已保存');
      await loadData();
    } catch {
      return false;
    }
    return true;
  }

  function stopShadowPolling() {
    if (shadowPollTimer) {
      clearInterval(shadowPollTimer);
      shadowPollTimer = null;
    }
  }

  async function fetchShadowStatus() {
    if (!shadowForm.tenantId) return;
    shadowLoading.value = true;
    try {
      shadowStatus.value = await getPlatformTenantShadowStatus(shadowForm.tenantId);
    } finally {
      shadowLoading.value = false;
    }
  }

  function startShadowPolling() {
    stopShadowPolling();
    shadowPollTimer = setInterval(async () => {
      await fetchShadowStatus();
      if (!shadowStatus.value?.maintenanceState) {
        stopShadowPolling();
      }
      loadData();
    }, 5000);
  }

  async function refreshShadowStatus() {
    await fetchShadowStatus();
    if (shadowStatus.value?.maintenanceState) {
      startShadowPolling();
    } else {
      stopShadowPolling();
    }
  }

  async function openShadow(row: PlatformTenantItem) {
    shadowForm.tenantId = row.tenantId;
    shadowForm.tenantName = row.name;
    shadowForm.active = row.status === 'ACTIVE';
    shadowStatus.value = null;
    showShadow.value = true;
    await refreshShadowStatus();
  }

  async function doEnableShadow() {
    shadowActionLoading.value = true;
    try {
      await enablePlatformTenantShadow(shadowForm.tenantId);
      message.success(t('managementCenter.tenant.shadowEnableSuccess'));
      await refreshShadowStatus();
      await loadData();
    } finally {
      shadowActionLoading.value = false;
    }
  }

  function handleEnableShadow() {
    doEnableShadow();
  }

  async function doSwitchToShadow() {
    shadowActionLoading.value = true;
    try {
      await switchPlatformTenantToShadow(shadowForm.tenantId);
      message.success(t('managementCenter.tenant.shadowSwitchToShadowSuccess'));
      startShadowPolling();
      await refreshShadowStatus();
      await loadData();
    } finally {
      shadowActionLoading.value = false;
    }
  }

  function handleSwitchToShadow() {
    doSwitchToShadow();
  }

  async function doSwitchToPrimary() {
    shadowActionLoading.value = true;
    try {
      await switchPlatformTenantToPrimary(shadowForm.tenantId);
      message.success(t('managementCenter.tenant.shadowSwitchToPrimarySuccess'));
      stopShadowPolling();
      await refreshShadowStatus();
      await loadData();
    } finally {
      shadowActionLoading.value = false;
    }
  }

  function handleSwitchToPrimary() {
    doSwitchToPrimary();
  }

  const columns = computed(() => [
    { title: '租户名称', key: 'name' },
    { title: '唯一标识', key: 'tenantId' },
    {
      title: '创建时间',
      key: 'createTime',
      render: (row: PlatformTenantItem) => formatDateTime(row.createTime),
    },
    { title: 'org_id', key: 'orgId' },
    { title: 'dbName', key: 'dbName' },
    {
      title: t('managementCenter.tenant.shadowColumn'),
      key: 'activeDbRole',
      render: (row: PlatformTenantItem) => renderShadowTag(row),
    },
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
        h('div', { class: 'flex flex-wrap gap-2' }, [
          h(NButton, { size: 'small', onClick: () => openEditName(row) }, { default: () => '编辑名称' }),
          h(NButton, { size: 'small', onClick: () => openOrgSync(row) }, { default: () => 'MMBA部门同步' }),
          h(
            NButton,
            { size: 'small', onClick: () => openShadow(row) },
            { default: () => t('managementCenter.tenant.shadowManage') }
          ),
          h(NButton, { size: 'small', onClick: () => showHealth(row) }, { default: () => '健康' }),
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

  onUnmounted(() => {
    stopShadowPolling();
  });
</script>
