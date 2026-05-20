<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <NInput
          v-model:value="keyword"
          clearable
          :placeholder="t('managementCenter.platformAdmin.keywordPlaceholder')"
          class="min-w-[200px] flex-1 sm:max-w-[280px]"
          @keyup.enter="handleSearch"
        />
        <NButton type="primary" @click="handleSearch">{{ t('common.search') }}</NButton>
        <NButton @click="handleReset">{{ t('common.reset') }}</NButton>
        <NButton type="primary" @click="openCreate">{{ t('common.add') }}</NButton>
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
    :title="t('managementCenter.platformAdmin.createTitle')"
    :positive-text="t('common.confirm')"
    :negative-text="t('common.cancel')"
    @positive-click="handleCreate"
  >
    <NSpace vertical class="max-w-[90vw]">
      <NInput v-model:value="createForm.username" :placeholder="t('managementCenter.platformAdmin.username')" />
      <NInput v-model:value="createForm.nickname" :placeholder="t('managementCenter.platformAdmin.nickname')" />
      <NInput
        v-model:value="createForm.password"
        type="password"
        show-password-on="click"
        :placeholder="t('managementCenter.platformAdmin.initialPassword')"
      />
    </NSpace>
  </NModal>

  <NModal
    v-model:show="showEdit"
    preset="dialog"
    :title="t('managementCenter.platformAdmin.editTitle')"
    :positive-text="t('common.confirm')"
    :negative-text="t('common.cancel')"
    @positive-click="handleEditSave"
  >
    <NSpace vertical class="max-w-[90vw]">
      <NInput v-model:value="editForm.username" disabled :placeholder="t('managementCenter.platformAdmin.username')" />
      <NInput v-model:value="editForm.nickname" :placeholder="t('managementCenter.platformAdmin.nickname')" />
      <NInput
        v-model:value="editForm.password"
        type="password"
        show-password-on="click"
        :placeholder="t('managementCenter.platformAdmin.passwordOptional')"
      />
    </NSpace>
  </NModal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { NButton, NDataTable, NInput, NModal, NPagination, NSpace, NSwitch, NTag, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    createPlatformUser,
    getPlatformUser,
    pagePlatformUsers,
    type PlatformUserAdminItem,
    updatePlatformUser,
  } from '@/api/modules';

  const { t } = useI18n();
  const message = useMessage();

  const STATUS_ACTIVE = 'ACTIVE';

  const loading = ref(false);
  const rows = ref<PlatformUserAdminItem[]>([]);
  const keyword = ref('');
  const pagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
  });

  const showCreate = ref(false);
  const createForm = reactive({
    username: '',
    nickname: '',
    password: '',
  });

  const showEdit = ref(false);
  const editForm = reactive({
    id: '',
    username: '',
    nickname: '',
    password: '',
    status: STATUS_ACTIVE,
  });

  function formatDateTime(ms?: number) {
    if (ms == null) return '-';
    const d = new Date(ms);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
      d.getMinutes()
    )}:${pad(d.getSeconds())}`;
  }

  function isActive(row: PlatformUserAdminItem) {
    return String(row.status || '').toUpperCase() === STATUS_ACTIVE;
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await pagePlatformUsers({
        current: pagination.current,
        pageSize: pagination.pageSize,
        keyword: keyword.value,
      });
      rows.value = Array.isArray(res?.list) ? res.list : [];
      pagination.total = typeof res?.total === 'number' ? res.total : 0;
      pagination.current = typeof res?.current === 'number' ? res.current : pagination.current;
      pagination.pageSize = typeof res?.pageSize === 'number' ? res.pageSize : pagination.pageSize;
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
    createForm.username = '';
    createForm.nickname = '';
    createForm.password = '';
    showCreate.value = true;
  }

  async function handleCreate() {
    const username = createForm.username.trim();
    const password = createForm.password.trim();
    if (!username) {
      message.warning(t('managementCenter.platformAdmin.usernameRequired'));
      return false;
    }
    if (!password) {
      message.warning(t('managementCenter.platformAdmin.passwordRequired'));
      return false;
    }
    try {
      await createPlatformUser({
        username,
        password,
        nickname: createForm.nickname.trim() || undefined,
      });
      message.success(t('managementCenter.platformAdmin.createSuccess'));
      showCreate.value = false;
      pagination.current = 1;
      await loadData();
    } catch {
      return false;
    }
    return true;
  }

  async function openEdit(row: PlatformUserAdminItem) {
    try {
      const detail = await getPlatformUser(row.id);
      editForm.id = detail.id;
      editForm.username = detail.username;
      editForm.nickname = detail.nickname ?? '';
      editForm.password = '';
      editForm.status = detail.status || STATUS_ACTIVE;
      showEdit.value = true;
    } catch {
      // http layer shows error
    }
  }

  async function handleEditSave() {
    const payload: { nickname: string; password?: string } = {
      nickname: editForm.nickname.trim(),
    };
    const pwd = editForm.password.trim();
    if (pwd) {
      payload.password = pwd;
    }
    try {
      await updatePlatformUser(editForm.id, payload);
      message.success(t('managementCenter.platformAdmin.updateSuccess'));
      showEdit.value = false;
      await loadData();
    } catch {
      return false;
    }
    return true;
  }

  async function setStatus(row: PlatformUserAdminItem, active: boolean) {
    const status = active ? STATUS_ACTIVE : 'DISABLED';
    try {
      await updatePlatformUser(row.id, { status });
      message.success(
        active ? t('managementCenter.platformAdmin.enabledOn') : t('managementCenter.platformAdmin.enabledOff')
      );
      await loadData();
    } catch {
      await loadData();
    }
  }

  const columns = computed(() => [
    { title: t('managementCenter.platformAdmin.username'), key: 'username', width: 120, ellipsis: { tooltip: true } },
    {
      title: t('managementCenter.platformAdmin.nickname'),
      key: 'nickname',
      width: 120,
      ellipsis: { tooltip: true },
      render: (row: PlatformUserAdminItem) => row.nickname || '-',
    },
    {
      title: t('managementCenter.platformAdmin.status'),
      key: 'status',
      width: 100,
      render: (row: PlatformUserAdminItem) =>
        h(
          NTag,
          { type: isActive(row) ? 'success' : 'default', size: 'small' },
          {
            default: () =>
              isActive(row)
                ? t('managementCenter.platformAdmin.statusActive')
                : t('managementCenter.platformAdmin.statusDisabled'),
          }
        ),
    },
    {
      title: t('managementCenter.platformAdmin.enabled'),
      key: 'enabled',
      width: 100,
      render: (row: PlatformUserAdminItem) =>
        h(NSwitch, {
          value: isActive(row),
          onUpdateValue: (v: boolean) => setStatus(row, v),
        }),
    },
    {
      title: t('managementCenter.platformAdmin.createTime'),
      key: 'createTime',
      width: 178,
      render: (row: PlatformUserAdminItem) => formatDateTime(row.createTime),
    },
    {
      title: t('common.operation'),
      key: 'action',
      width: 100,
      render: (row: PlatformUserAdminItem) =>
        h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
    },
  ]);

  onMounted(() => {
    loadData();
  });
</script>
