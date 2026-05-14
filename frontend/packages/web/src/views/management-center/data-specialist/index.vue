<template>
  <CrmCard no-content-padding hide-footer>
    <div class="p-4">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <NInput
          v-model:value="keyword"
          clearable
          :placeholder="t('managementCenter.dataSpecialist.keywordPlaceholder')"
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
    :title="t('managementCenter.dataSpecialist.createTitle')"
    :positive-text="t('common.confirm')"
    :negative-text="t('common.cancel')"
    class="data-specialist-form-modal"
    @positive-click="handleCreate"
  >
    <NSpace vertical class="max-w-[90vw]">
      <NInput v-model:value="createForm.username" :placeholder="t('managementCenter.dataSpecialist.username')" />
      <NInput
        v-model:value="createForm.password"
        type="password"
        show-password-on="click"
        :placeholder="t('managementCenter.dataSpecialist.initialPassword')"
      />
      <div>
        <div class="mb-1 flex flex-col gap-0.5">
          <span class="text-sm font-medium text-[var(--text-n1)]">{{
            t('managementCenter.dataSpecialist.tenantIds')
          }}</span>
          <span class="text-xs leading-snug text-[var(--text-n4)]">{{
            t('managementCenter.dataSpecialist.tenantMultiHint')
          }}</span>
        </div>
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <NInput
            v-model:value="tenantFilterCreate"
            clearable
            class="min-w-[200px] flex-1"
            :placeholder="t('managementCenter.dataSpecialist.tenantFilterPlaceholder')"
          />
          <NButton text type="primary" size="small" @click="createForm.tenantIds = []">
            {{ t('managementCenter.dataSpecialist.clearTenantSelection') }}
          </NButton>
        </div>
        <template v-if="createTenantPickerOptions.length > 0">
          <NScrollbar class="tenant-checkbox-scroll">
            <NCheckboxGroup v-model:value="createForm.tenantIds" class="block py-1">
              <NSpace vertical :size="6">
                <NCheckbox v-for="opt in createTenantPickerOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </NCheckbox>
              </NSpace>
            </NCheckboxGroup>
          </NScrollbar>
        </template>
        <div v-else class="py-6 text-center text-sm text-[var(--text-n4)]">
          {{ t('managementCenter.dataSpecialist.tenantFilterEmpty') }}
        </div>
        <div class="mt-2 text-xs text-[var(--text-n3)]">
          {{ t('managementCenter.dataSpecialist.selectedTenantCount', { count: createForm.tenantIds.length }) }}
        </div>
      </div>
    </NSpace>
  </NModal>

  <NModal
    v-model:show="showEdit"
    preset="dialog"
    :title="t('managementCenter.dataSpecialist.editTitle')"
    :positive-text="t('common.confirm')"
    :negative-text="t('common.cancel')"
    class="data-specialist-form-modal"
    @positive-click="handleEditSave"
  >
    <NSpace vertical class="max-w-[90vw]">
      <NInput v-model:value="editForm.username" disabled :placeholder="t('managementCenter.dataSpecialist.username')" />
      <NInput
        v-model:value="editForm.password"
        type="password"
        show-password-on="click"
        :placeholder="t('managementCenter.dataSpecialist.passwordOptional')"
      />
      <div class="flex items-center gap-2">
        <span class="text-sm text-[var(--text-n2)]">{{ t('managementCenter.dataSpecialist.enabled') }}</span>
        <NSwitch v-model:value="editForm.enabled" />
      </div>
      <div>
        <div class="mb-1 flex flex-col gap-0.5">
          <span class="text-sm font-medium text-[var(--text-n1)]">{{
            t('managementCenter.dataSpecialist.tenantIds')
          }}</span>
          <span class="text-xs leading-snug text-[var(--text-n4)]">{{
            t('managementCenter.dataSpecialist.tenantMultiHint')
          }}</span>
        </div>
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <NInput
            v-model:value="tenantFilterEdit"
            clearable
            class="min-w-[200px] flex-1"
            :placeholder="t('managementCenter.dataSpecialist.tenantFilterPlaceholder')"
          />
          <NButton text type="primary" size="small" @click="editForm.tenantIds = []">
            {{ t('managementCenter.dataSpecialist.clearTenantSelection') }}
          </NButton>
        </div>
        <template v-if="editTenantPickerOptions.length > 0">
          <NScrollbar class="tenant-checkbox-scroll">
            <NCheckboxGroup v-model:value="editForm.tenantIds" class="block py-1">
              <NSpace vertical :size="6">
                <NCheckbox v-for="opt in editTenantPickerOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </NCheckbox>
              </NSpace>
            </NCheckboxGroup>
          </NScrollbar>
        </template>
        <div v-else class="py-6 text-center text-sm text-[var(--text-n4)]">
          {{ t('managementCenter.dataSpecialist.tenantFilterEmpty') }}
        </div>
        <div class="mt-2 text-xs text-[var(--text-n3)]">
          {{ t('managementCenter.dataSpecialist.selectedTenantCount', { count: editForm.tenantIds.length }) }}
        </div>
      </div>
    </NSpace>
  </NModal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import {
    NButton,
    NCheckbox,
    NCheckboxGroup,
    NDataTable,
    NInput,
    NModal,
    NPagination,
    NScrollbar,
    NSpace,
    NSwitch,
    useMessage,
  } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    createDataSpecialist,
    type DataSpecialistAdminItem,
    getDataSpecialist,
    pageDataSpecialists,
    pagePlatformTenants,
    type PlatformTenantItem,
    updateDataSpecialist,
  } from '@/api/modules';

  const { t } = useI18n();
  const message = useMessage();

  type TenantOption = { label: string; value: string };

  const tenantFilterCreate = ref('');
  const tenantFilterEdit = ref('');

  const loading = ref(false);
  const rows = ref<DataSpecialistAdminItem[]>([]);
  const keyword = ref('');
  const pagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
  });

  const tenantOptions = ref<TenantOption[]>([]);

  const showCreate = ref(false);
  const createForm = reactive({
    username: '',
    password: '',
    tenantIds: [] as string[],
  });

  const showEdit = ref(false);
  const editForm = reactive({
    id: '',
    username: '',
    password: '',
    enabled: true,
    tenantIds: [] as string[],
  });

  function filterTenantsByKeyword(options: TenantOption[], kw: string): TenantOption[] {
    const q = kw.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q));
  }

  function mergeTenantsWithSelected(base: TenantOption[], selectedIds: string[]): TenantOption[] {
    const map = new Map<string, TenantOption>();
    base.forEach((o) => map.set(o.value, o));
    selectedIds.forEach((id) => {
      if (id && !map.has(id)) {
        map.set(id, {
          value: id,
          label: `${id} (${t('managementCenter.dataSpecialist.tenantNotInList')})`,
        });
      }
    });
    return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label, undefined, { sensitivity: 'base' }));
  }

  const createTenantPickerOptions = computed(() =>
    filterTenantsByKeyword(tenantOptions.value, tenantFilterCreate.value).sort((a, b) =>
      a.label.localeCompare(b.label, undefined, { sensitivity: 'base' })
    )
  );

  const editTenantMergedOptions = computed(() => mergeTenantsWithSelected(tenantOptions.value, editForm.tenantIds));

  const editTenantPickerOptions = computed(() =>
    filterTenantsByKeyword(editTenantMergedOptions.value, tenantFilterEdit.value).sort((a, b) =>
      a.label.localeCompare(b.label, undefined, { sensitivity: 'base' })
    )
  );

  function formatTime(ms?: number) {
    if (ms == null) return '-';
    return new Date(ms).toLocaleString();
  }

  async function loadTenantOptions() {
    const res = await pagePlatformTenants({ current: 1, pageSize: 200, keyword: '' });
    const list = Array.isArray(res?.list) ? (res.list as PlatformTenantItem[]) : [];
    tenantOptions.value = list
      .filter((row) => String(row.status || '').toUpperCase() === 'ACTIVE')
      .map((row) => ({
        label: `${row.name} (${row.tenantId})`,
        value: row.tenantId,
      }));
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await pageDataSpecialists({
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

  async function openCreate() {
    try {
      await loadTenantOptions();
    } catch {
      message.error(t('managementCenter.dataSpecialist.loadTenantsFailed'));
      return;
    }
    tenantFilterCreate.value = '';
    createForm.username = '';
    createForm.password = '';
    createForm.tenantIds = [];
    showCreate.value = true;
  }

  async function handleCreate() {
    const username = createForm.username.trim();
    const password = createForm.password.trim();
    if (!username) {
      message.warning(t('managementCenter.dataSpecialist.usernameRequired'));
      return false;
    }
    if (!password) {
      message.warning(t('managementCenter.dataSpecialist.passwordRequired'));
      return false;
    }
    if (!createForm.tenantIds.length) {
      message.warning(t('managementCenter.dataSpecialist.tenantRequired'));
      return false;
    }
    try {
      await createDataSpecialist({
        username,
        password,
        tenantIds: createForm.tenantIds,
      });
      message.success(t('managementCenter.dataSpecialist.createSuccess'));
      showCreate.value = false;
      pagination.current = 1;
      await loadData();
    } catch {
      return false;
    }
    return true;
  }

  async function openEdit(row: DataSpecialistAdminItem) {
    try {
      await loadTenantOptions();
    } catch {
      message.error(t('managementCenter.dataSpecialist.loadTenantsFailed'));
      return;
    }
    try {
      const detail = await getDataSpecialist(row.id);
      tenantFilterEdit.value = '';
      editForm.id = detail.id;
      editForm.username = detail.username;
      editForm.password = '';
      editForm.enabled = !!detail.enabled;
      editForm.tenantIds = [...(detail.tenantIds || [])];
      showEdit.value = true;
    } catch {
      // http layer shows error
    }
  }

  async function handleEditSave() {
    if (!editForm.tenantIds.length) {
      message.warning(t('managementCenter.dataSpecialist.tenantRequired'));
      return false;
    }
    const payload: {
      enabled: boolean;
      tenantIds: string[];
      password?: string;
    } = {
      enabled: editForm.enabled,
      tenantIds: editForm.tenantIds,
    };
    const pwd = editForm.password.trim();
    if (pwd) {
      payload.password = pwd;
    }
    try {
      await updateDataSpecialist(editForm.id, payload);
      message.success(t('managementCenter.dataSpecialist.updateSuccess'));
      showEdit.value = false;
      await loadData();
    } catch {
      return false;
    }
    return true;
  }

  async function setEnabled(row: DataSpecialistAdminItem, enabled: boolean) {
    try {
      await updateDataSpecialist(row.id, { enabled });
      message.success(
        enabled ? t('managementCenter.dataSpecialist.enabledOn') : t('managementCenter.dataSpecialist.enabledOff')
      );
      await loadData();
    } catch {
      await loadData();
    }
  }

  const columns = computed(() => [
    { title: t('managementCenter.dataSpecialist.username'), key: 'username' },
    {
      title: t('managementCenter.dataSpecialist.enabled'),
      key: 'enabled',
      width: 100,
      render: (row: DataSpecialistAdminItem) =>
        h(NSwitch, {
          value: !!row.enabled,
          onUpdateValue: (v: boolean) => setEnabled(row, v),
        }),
    },
    {
      title: t('managementCenter.dataSpecialist.createTime'),
      key: 'createTime',
      width: 180,
      render: (row: DataSpecialistAdminItem) => formatTime(row.createTime),
    },
    {
      title: t('managementCenter.dataSpecialist.id'),
      key: 'id',
      ellipsis: { tooltip: true },
    },
    {
      title: t('common.operation'),
      key: 'action',
      width: 100,
      render: (row: DataSpecialistAdminItem) =>
        h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
    },
  ]);

  onMounted(() => {
    loadData();
  });
</script>

<style scoped>
  .tenant-checkbox-scroll {
    max-height: 280px;
  }
</style>
